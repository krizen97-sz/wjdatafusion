package com.hm.manage.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.slf4j.LoggerFactory;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.hm.common.core.domain.entity.SysUser;
import com.hm.common.core.domain.model.LoginUser;
import com.hm.common.exception.ServiceException;
import com.hm.manage.config.SupportCredentialProperties;
import com.hm.manage.mapper.SupportChangeLogMapper;
import com.hm.manage.domain.SupportPlatform;
import com.hm.manage.domain.bo.SupportServerScanBo;
import com.hm.manage.domain.vo.SupportServerLoginCheckVo;
import com.hm.manage.service.ISupportPlatformService;
import com.hm.manage.service.support.*;

class SupportServerScanServiceTest
{
    private SupportServerScanProbe probe;
    private SupportServerLoginVerifier verifier;
    private SupportServerScanCredentialService credentials;
    private SupportEquipmentWorkbookService workbook;
    private SupportServerScanSyncService sync;
    private SupportServerScanService service;
    private CredentialCryptoService crypto;
    private List<Map<String, Object>> pool;
    private String loginMode = "ROOT_DIRECT";
    private final String fingerprint = "SHA256:" + "A".repeat(43);
    @BeforeEach void setup()
    {
        probe = mock(SupportServerScanProbe.class); verifier = mock(SupportServerLoginVerifier.class);
        credentials = mock(SupportServerScanCredentialService.class); workbook = mock(SupportEquipmentWorkbookService.class);
        sync = mock(SupportServerScanSyncService.class);
        crypto = mock(CredentialCryptoService.class); var platforms = mock(ISupportPlatformService.class);
        SupportPlatform platform = new SupportPlatform(); platform.setSiteId(9L); platform.setPlatformId(3L); platform.setPlatformLevel("SUB");
        when(platforms.selectSupportPlatformByPlatformId(3L)).thenReturn(platform);
        pool = List.of(candidate(1L, "root"));
        when(credentials.selected(eq(9L), any())).thenAnswer(call -> pool);
        when(workbook.snapshot(9L)).thenReturn(Map.of("rows", List.of()));
        when(crypto.decrypt(anyString())).thenAnswer(call -> "fixture-" + call.getArgument(0));
        when(probe.inspect(eq("192.0.2.1"), anyInt())).thenAnswer(call -> new SupportServerScanProbe.Port(call.getArgument(1), true, "READY", fingerprint, "ready"));
        when(verifier.verify(anyString(), anyInt(), anyString(), anyString(), anyString(), anyBoolean(), anyString())).thenReturn(result("SUCCESS"));
        when(probe.collect(anyString(), anyInt(), anyString(), anyString(), anyString())).thenReturn(new SupportServerScanProbe.Facts("server-a", "CentOS", "7", "3.10", ""));
        when(sync.sync(anyLong(), anyLong(), anyString(), any(), any(), anyInt(), anyBoolean(), anyString(), anyString(), any(), anyString(), anyString())).thenReturn(10L);
        service = new SupportServerScanService(probe, verifier, new SupportServerLoginGuard(), credentials, crypto, workbook, sync, platforms);
    }
    @AfterEach void stop() { service.close(); RequestContextHolder.resetRequestAttributes(); SecurityContextHolder.clearContext(); }
    @Test void probesEveryPortWithoutAuthenticatingThenSynchronizesConfirmedRoot() throws Exception
    {
        String id = begin(); await(id, "REVIEW");
        for (int port : List.of(22,2343,55555)) verify(probe).inspect("192.0.2.1", port);
        verifyNoInteractions(verifier, sync);
        service.confirm(id, 1L, confirmation(2343));
        Map<String,Object> done = await(id, "DONE");
        verify(verifier).verify("192.0.2.1",2343,"root","fixture-root","fixture-root",true,fingerprint);
        verify(sync).sync(eq(9L),eq(3L),eq("192.0.2.1"),isNull(),isNull(),eq(2343),eq(true),eq(""),eq("fixture-root"),any(),eq("22,2343,55555"),eq("operator"));
        assertEquals("SYNCED", rows(done).get(0).get("state"));
        assertFalse(done.toString().contains("fixture-root")); assertFalse(done.toString().contains("rootCipher"));
    }
    @Test void hikPathUsesLowercaseHikAndBothValidatedSecrets() throws Exception
    {
        loginMode = "HIK_SU_ROOT";
        pool = List.of(candidate(1L, "hik"), candidate(2L, "root"));
        when(verifier.verify(anyString(),anyInt(),anyString(),anyString(),anyString(),anyBoolean(),anyString()))
            .thenReturn(result("SU_REJECTED"), result("SUCCESS"));
        String id = start(2); await(id,"REVIEW"); service.confirm(id,1L,confirmation(22)); await(id,"DONE");
        verify(verifier).verify("192.0.2.1",22,"hik","fixture-hik","fixture-hik",false,fingerprint);
        verify(verifier).verify("192.0.2.1",22,"hik","fixture-hik","fixture-root",false,fingerprint);
        verify(sync).sync(anyLong(),anyLong(),anyString(),isNull(),isNull(),eq(22),eq(false),eq("fixture-hik"),eq("fixture-root"),any(),anyString(),anyString());
    }
    @Test void neverOverwritesOnFailedAuthenticationAndHonorsCandidateLimit() throws Exception
    {
        pool = List.of(candidate(1L,"first"),candidate(2L,"second"));
        when(verifier.verify(anyString(),anyInt(),anyString(),anyString(),anyString(),anyBoolean(),anyString())).thenReturn(result("AUTH_REJECTED"));
        String id = begin(); await(id,"REVIEW"); service.confirm(id,1L,confirmation(22)); var done = await(id,"DONE");
        verify(verifier,times(1)).verify(anyString(),anyInt(),anyString(),anyString(),anyString(),anyBoolean(),anyString());
        verifyNoInteractions(sync); assertEquals("FAILED",rows(done).get(0).get("state"));
    }
    @Test void changedFingerprintNeverFallsBackToAnotherPassword() throws Exception
    {
        pool = List.of(candidate(1L,"first"),candidate(2L,"second"));
        when(verifier.verify(anyString(),anyInt(),anyString(),anyString(),anyString(),anyBoolean(),anyString())).thenReturn(result("HOST_KEY_CHANGED"));
        String id = start(2); await(id,"REVIEW"); service.confirm(id,1L,confirmation(22)); await(id,"DONE");
        verify(verifier,times(1)).verify(anyString(),anyInt(),anyString(),anyString(),anyString(),anyBoolean(),anyString()); verifyNoInteractions(sync);
    }
    @Test void automaticModeFallsBackToRootOnlyWhenHikAuthenticationIsRejected() throws Exception
    {
        loginMode = "AUTO";
        pool=List.of(candidate(1L,"root"));
        when(verifier.verify(anyString(),anyInt(),anyString(),anyString(),anyString(),anyBoolean(),anyString()))
            .thenReturn(result("AUTH_REJECTED"),result("SUCCESS"));
        String id=start(1);await(id,"REVIEW");service.confirm(id,1L,confirmation(22));var done=await(id,"DONE");
        assertEquals("SYNCED",rows(done).get(0).get("state"));assertEquals(2,rows(done).get(0).get("attempts"));
        verify(verifier).verify("192.0.2.1",22,"root","fixture-root","fixture-root",true,fingerprint);
        verify(verifier).verify("192.0.2.1",22,"hik","fixture-root","fixture-root",false,fingerprint);
    }
    @Test void registeredHardwareCannotBeRecreatedAsAServerOrConfirmedByForgedInput() throws Exception
    {
        when(workbook.snapshot(9L)).thenReturn(Map.of("rows",List.of(Map.of("sourceType","HARDWARE","ipAddress","192.0.2.1","sourceId",7L,"canEdit",true,"status","0"))));
        String id=begin();var report=await(id,"REVIEW");assertEquals("SKIPPED",rows(report).get(0).get("state"));
        assertThrows(Exception.class,()->service.confirm(id,1L,confirmation(22)));verifyNoInteractions(verifier,sync);
    }
    @Test void ownerTargetAndConsentAreRequired() throws Exception
    {
        String id = begin(); await(id,"REVIEW");
        assertThrows(Exception.class,()->service.status(id,2L)); assertThrows(Exception.class,()->service.cancel(id,2L));
        assertThrows(Exception.class,()->service.confirm(id,1L,new SupportServerScanBo.Confirm(List.of(new SupportServerScanBo.Selection("192.0.2.1",22)),false)));
        assertThrows(Exception.class,()->service.confirm(id,1L,new SupportServerScanBo.Confirm(List.of(new SupportServerScanBo.Selection("192.0.2.2",22)),true)));
        assertThrows(Exception.class,()->service.confirm(id,1L,confirmation(2222))); verifyNoInteractions(verifier,sync);
    }
    @Test void changedPoolAndCancelledTasksCannotBeConfirmed() throws Exception
    {
        String id = begin(); await(id,"REVIEW"); pool = List.of(candidate(2L,"root"));
        assertThrows(Exception.class,()->service.confirm(id,1L,confirmation(22)));
        service.cancel(id,1L); assertThrows(Exception.class,()->service.confirm(id,1L,confirmation(22))); verifyNoInteractions(verifier,sync);
    }
    @Test void existingIdentityAndRevisionReachTheTransactionalSynchronizer() throws Exception
    {
        when(workbook.snapshot(9L)).thenReturn(Map.of("rows",List.of(Map.of("sourceType","SERVER","ipAddress","192.0.2.1","sourceId",7L,"revision","original-revision","canEdit",true,"status","0"))));
        String id=begin(); await(id,"REVIEW"); service.confirm(id,1L,confirmation(55555)); await(id,"DONE");
        verify(sync).sync(eq(9L),eq(3L),eq("192.0.2.1"),eq(7L),eq("original-revision"),eq(55555),eq(true),anyString(),anyString(),any(),anyString(),anyString());
    }
    @Test void cancellationStopsBeforeQueuingTheRestOfASubnet() throws Exception
    {
        var started=new java.util.concurrent.CountDownLatch(1);
        when(probe.inspect(anyString(),anyInt())).thenAnswer(call -> { started.countDown(); Thread.sleep(80); return new SupportServerScanProbe.Port(call.getArgument(1),true,"READY",fingerprint,"ready"); });
        String id=String.valueOf(service.start(new SupportServerScanBo.Start(9L,3L,"192.0.2.1-192.0.2.10",List.of(22,2343,55555),List.of(1L),1,loginMode),1L,"operator").get("taskId"));
        assertTrue(started.await(2,java.util.concurrent.TimeUnit.SECONDS));service.cancel(id,1L);
        var stopped=await(id,"CANCELLED");assertTrue(((Number)stopped.get("done")).intValue()<10);
        verifyNoInteractions(verifier,sync);
    }
    @Test void poolChangedDuringLoginPreventsFurtherCollectionAndSynchronization() throws Exception
    {
        when(verifier.verify(anyString(),anyInt(),anyString(),anyString(),anyString(),anyBoolean(),anyString()))
            .thenAnswer(call -> { pool = List.of(candidate(1L,"changed")); return result("SUCCESS"); });
        String id=begin();await(id,"REVIEW");service.confirm(id,1L,confirmation(22));
        assertEquals("FAILED",rows(await(id,"DONE")).get(0).get("state"));
        verify(probe,never()).collect(anyString(),anyInt(),anyString(),anyString(),anyString());
        verifyNoInteractions(sync);
    }
    @Test void autoKeepsAnExistingRootDirectPolicy() throws Exception
    {
        loginMode="AUTO";
        when(workbook.snapshot(9L)).thenReturn(Map.of("rows",List.of(Map.of("sourceType","SERVER","ipAddress","192.0.2.1",
            "sourceId",7L,"revision","r","canEdit",true,"status","0","rootDirectLogin",true))));
        String id=begin();await(id,"REVIEW");service.confirm(id,1L,confirmation(22));await(id,"DONE");
        verify(verifier).verify("192.0.2.1",22,"root","fixture-root","fixture-root",true,fingerprint);
        verify(verifier,never()).verify(anyString(),anyInt(),eq("hik"),anyString(),anyString(),anyBoolean(),anyString());
    }
    @Test void realAesCipherIsDecryptedBeforeSshAndCollection() throws Exception
    {
        var properties = new SupportCredentialProperties(); properties.setKey("isolated-unit-test-key");
        var realCrypto = new CredentialCryptoService(); ReflectionTestUtils.setField(realCrypto,"properties",properties);
        String plain = " fixture-password! ", cipher = realCrypto.encrypt(plain);
        assertNotEquals(plain, cipher);
        pool = List.of(candidate(1L,cipher));
        when(crypto.decrypt(anyString())).thenAnswer(call -> realCrypto.decrypt(call.getArgument(0)));
        String id=begin();await(id,"REVIEW");service.confirm(id,1L,confirmation(22));var done=await(id,"DONE");
        verify(verifier).verify("192.0.2.1",22,"root",plain,plain,true,fingerprint);
        verify(probe).collect("192.0.2.1",22,"root",plain,fingerprint);
        assertEquals("SYNCED",rows(done).get(0).get("state"));
        assertFalse(done.toString().contains(plain)); assertFalse(done.toString().contains(cipher));
    }
    @Test void decryptFailureNeverSendsCiphertextToSsh() throws Exception
    {
        when(crypto.decrypt(anyString())).thenThrow(new ServiceException("fixture-secret-in-exception"));
        String id=begin();await(id,"REVIEW");service.confirm(id,1L,confirmation(22));var row=rows(await(id,"DONE")).get(0);
        assertEquals("PASSWORD_DECRYPT_FAILED",row.get("errorCode"));assertEquals(0,row.get("attempts"));
        assertEquals(false,row.get("rootPasswordVerified"));assertFalse(row.toString().contains("fixture-secret-in-exception"));
        verifyNoInteractions(verifier,sync);
    }
    @Test void collectionFailureDoesNotMisreportAnAlreadyVerifiedPassword() throws Exception
    {
        when(probe.collect(anyString(),anyInt(),anyString(),anyString(),anyString())).thenThrow(new ServiceException("fixture-private-detail"));
        String id=begin();await(id,"REVIEW");service.confirm(id,1L,confirmation(22));var row=rows(await(id,"DONE")).get(0);
        assertEquals("SYSTEM_INFO_FAILED",row.get("errorCode"));assertEquals(true,row.get("rootPasswordVerified"));
        assertTrue(row.get("message").toString().contains("已核验通过"));assertFalse(row.toString().contains("fixture-private-detail"));
        verifyNoInteractions(sync);
    }
    @Test void syncFailureHasAStageAndSafeDiagnosticWithoutSqlOrPasswordDetails() throws Exception
    {
        Logger logger=(Logger)LoggerFactory.getLogger(SupportServerScanService.class);
        ListAppender<ILoggingEvent> events=new ListAppender<>();events.start();logger.addAppender(events);
        try
        {
            when(sync.sync(anyLong(),anyLong(),anyString(),any(),any(),anyInt(),anyBoolean(),anyString(),anyString(),any(),anyString(),anyString()))
                .thenThrow(new IllegalStateException("SQL password=fixture-private-detail",new RuntimeException("fixture-cipher")));
            String id=begin();await(id,"REVIEW");service.confirm(id,1L,confirmation(22));var row=rows(await(id,"DONE")).get(0);
            assertEquals("SERVER_SYNC_FAILED",row.get("errorCode"));assertEquals(true,row.get("rootPasswordVerified"));
            assertFalse(row.get("diagnosticId").toString().isEmpty());
            assertTrue(row.get("message").toString().contains("数据库同步失败"));
            assertFalse(row.toString().contains("fixture-private-detail"));
            assertFalse(events.list.isEmpty());
            for(var event:events.list)
            {
                assertFalse(event.getFormattedMessage().contains("fixture-private-detail"));
                assertFalse(event.getFormattedMessage().contains("fixture-cipher"));
                assertNull(event.getThrowableProxy());
            }
        }
        finally { logger.detachAppender(events);events.stop(); }
    }
    @Test void actualAuditServiceWorksInScanWorkerWithoutAnHttpRequest() throws Exception
    {
        var auditMapper=mock(SupportChangeLogMapper.class);var audit=new SupportChangeLogServiceImpl();
        ReflectionTestUtils.setField(audit,"changeLogMapper",auditMapper);
        SysUser user=new SysUser();user.setUserName("operator");user.setNickName("Scan nickname");
        LoginUser principal=new LoginUser(1L,1L,user,Set.of("*:*:*"));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal,null));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        when(sync.sync(anyLong(),anyLong(),anyString(),any(),any(),anyInt(),anyBoolean(),anyString(),anyString(),any(),anyString(),anyString()))
            .thenAnswer(call -> {
                assertNull(RequestContextHolder.getRequestAttributes());
                audit.record(9L,"UPDATE","SERVER_SCAN",10L,"fixture","scan synchronized");
                return 10L;
            });
        String id=begin();await(id,"REVIEW");service.confirm(id,1L,confirmation(22));
        assertEquals("SYNCED",rows(await(id,"DONE")).get(0).get("state"));
        verify(auditMapper).insertSupportChangeLog(argThat(row -> "Scan nickname".equals(row.getOperatorName()) && "unknown".equals(row.getOperatorIp())));
    }
    private String begin() { return start(1); }
    private String start(int limit) { return String.valueOf(service.start(new SupportServerScanBo.Start(9L,3L,"192.0.2.1",List.of(22,2343,55555),pool.stream().map(row->(Long)row.get("credentialId")).toList(),limit,loginMode),1L,"operator").get("taskId")); }
    private SupportServerScanBo.Confirm confirmation(int port) { return new SupportServerScanBo.Confirm(List.of(new SupportServerScanBo.Selection("192.0.2.1",port)),true); }
    private Map<String,Object> await(String id,String state) throws Exception
    {
        long deadline=System.currentTimeMillis()+15000;
        while(System.currentTimeMillis()<deadline) { var value=service.status(id,1L); if(state.equals(value.get("state"))) return value; Thread.sleep(10); }
        fail("Task did not reach "+state); return null;
    }
    @SuppressWarnings("unchecked") private List<Map<String,Object>> rows(Map<String,Object> value) { return (List<Map<String,Object>>)value.get("rows"); }
    private Map<String,Object> candidate(Long id,String value) { return Map.of("credentialId",id,"name","candidate-"+id,"passwordCipher",value); }
    private SupportServerLoginCheckVo result(String status) { return new SupportServerLoginCheckVo(status,status,"ROOT_DIRECT","root","192.0.2.1",22,fingerprint,"SUCCESS".equals(status),"now",List.of()); }
}
