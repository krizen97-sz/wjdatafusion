package com.hm.manage.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.SimpleTransactionStatus;
import com.hm.manage.domain.SupportServer;
import com.hm.manage.domain.bo.*;
import com.hm.manage.mapper.*;
import com.hm.manage.service.*;
import com.hm.manage.service.support.CredentialCryptoService;
import com.hm.manage.service.support.SupportServerScanProbe;

class SupportServerScanPersistenceTest
{
    @Test void malformedStoredCandidateCannotStartAuthentication()
    {
        var mapper=mock(SupportServerScanMapper.class);
        var service=new SupportServerScanCredentialService(mapper,mock(SupportSiteMapper.class),mock(CredentialCryptoService.class),mock(ISupportChangeLogService.class));
        when(mapper.selectCredential(9L,1L)).thenReturn(Map.of("passwordCipher",""));
        assertThrows(Exception.class,()->service.selected(9L,List.of(1L)));
        when(mapper.selectCredential(9L,1L)).thenReturn(Map.of("mode","HIK_SU_ROOT","rootCipher","saved","hikCipher","saved"));
        assertThrows(Exception.class,()->service.selected(9L,List.of(1L)));
    }
    @Test void candidatePasswordsAreEncryptedAndNeverReturnedOrAuditedAsPlaintext()
    {
        var mapper = mock(SupportServerScanMapper.class); var sites = mock(SupportSiteMapper.class);
        var crypto = mock(CredentialCryptoService.class); var logs = mock(ISupportChangeLogService.class);
        when(sites.selectSiteIdForUpdate(9L)).thenReturn(9L);
        when(mapper.selectCredentials(9L)).thenReturn(List.of());
        when(crypto.encrypt(anyString())).thenAnswer(call -> "encrypted-" + call.getArgument(0));
        doAnswer(call -> { ((Map<String,Object>)call.getArgument(0)).put("credentialId", 1L); return 1; }).when(mapper).insertCredential(any());
        var service = new SupportServerScanCredentialService(mapper,sites,crypto,logs);
        service.save(new SupportServerScanBo.Credential(null,9L,"授权密码","fixture-password"),"actor");
        var saved = ArgumentCaptor.forClass(Map.class); verify(mapper).insertCredential(saved.capture());
        assertEquals("encrypted-fixture-password",saved.getValue().get("passwordCipher"));
        assertFalse(saved.getValue().containsKey("mode"));
        when(mapper.selectCredentials(9L)).thenReturn(List.of(saved.getValue()));
        assertFalse(service.list(9L).toString().contains("fixture-"));
        verify(logs).record(eq(9L),eq("INSERT"),eq("SERVER_SCAN_CREDENTIAL"),eq(1L),eq("授权密码"),anyString(),isNull(),argThat(value -> !String.valueOf(value).contains("fixture-")));
    }
    @Test void blankCandidateEditKeepsTheSavedCipherAndCrossSiteIdsFail()
    {
        var mapper=mock(SupportServerScanMapper.class); var sites=mock(SupportSiteMapper.class);
        var crypto=mock(CredentialCryptoService.class);
        when(sites.selectSiteIdForUpdate(9L)).thenReturn(9L);
        when(mapper.selectCredential(9L,1L)).thenReturn(Map.of("credentialId",1L,"name","old","passwordCipher","saved-cipher"));
        var service=new SupportServerScanCredentialService(mapper,sites,crypto,mock(ISupportChangeLogService.class));
        service.save(new SupportServerScanBo.Credential(1L,9L,"renamed",""),"actor");
        verify(mapper).updateCredential(argThat(row -> "saved-cipher".equals(row.get("passwordCipher"))));
        verifyNoInteractions(crypto);
        assertThrows(Exception.class,()->service.save(new SupportServerScanBo.Credential(2L,9L,"x","secret"),"actor"));
        assertThrows(Exception.class,()->service.save(new SupportServerScanBo.Credential(1L,9L,"old-page",null),"actor"));
        verify(mapper,times(1)).updateCredential(any());
    }
    @Test void controlCharactersAreRejectedBeforePersistence()
    {
        var mapper=mock(SupportServerScanMapper.class); var sites=mock(SupportSiteMapper.class);
        when(sites.selectSiteIdForUpdate(9L)).thenReturn(9L);
        var service=new SupportServerScanCredentialService(mapper,sites,mock(CredentialCryptoService.class),mock(ISupportChangeLogService.class));
        assertThrows(Exception.class,()->service.save(new SupportServerScanBo.Credential(null,9L,"x","bad\ncommand"),"actor"));
        verify(mapper,never()).insertCredential(any());
    }
    @Test void existingSyncIsSparseAndCannotOverwriteAChangedIdentity()
    {
        var fixture=new SyncFixture();
        SupportServer server=new SupportServer();server.setServerId(7L);server.setSiteId(9L);server.setServerAddress("192.0.2.1");
        when(fixture.servers.selectSupportServerByServerIdForUpdate(7L)).thenReturn(server);
        fixture.run(7L, fixture.complete());
        var update=ArgumentCaptor.forClass(SupportEquipmentWorkbookBo.class);
        verify(fixture.workbook).save(update.capture(),eq("actor"));
        var row=update.getValue().getRows().get(0);
        assertEquals("revision",row.getRevision());
        assertEquals(Set.of("sshPort","rootDirectLogin","rootPassword","osType"),row.getChanges().keySet());
        assertFalse(row.getChanges().containsKey("platformIds"));
        server.setServerAddress("192.0.2.2");
        assertThrows(Exception.class,()->fixture.run(7L,fixture.complete()));
        verify(fixture.workbook,times(1)).save(any(),anyString());
    }
    @Test void newServerUsesUnifiedIntakeAndDoesNotDuplicateAConcurrentHardwareAsset()
    {
        var fixture=new SyncFixture();
        var ref=new SupportEquipmentDeviceRefBo();ref.setSourceType("SERVER");ref.setSourceId(10L);
        when(fixture.equipment.createEquipment(any())).thenReturn(ref);
        assertEquals(10L,fixture.run(null,fixture.complete()));
        verify(fixture.equipment).createEquipment(argThat(row -> row.getPlatformId()==3L && row.getServer().getRootPassword().equals("fixture-root")));
        when(fixture.profiles.countAddressOwners(9L,"192.0.2.1")).thenReturn(1);
        assertThrows(Exception.class,()->fixture.run(null,fixture.complete()));
        verify(fixture.equipment,times(1)).createEquipment(any());
    }
    @Test void incompleteMetadataDoesNotRefreshOrOverwriteThePreviousProfile()
    {
        var fixture=new SyncFixture();
        var ref=new SupportEquipmentDeviceRefBo();ref.setSourceId(10L);when(fixture.equipment.createEquipment(any())).thenReturn(ref);
        fixture.run(null,new SupportServerScanProbe.Facts("","","","","collection failed"));
        verify(fixture.profiles,never()).saveProfile(any());
    }
    @Test void profileFailureRollsBackTheSameSpringTransaction()
    {
        var fixture=new SyncFixture();
        var ref=new SupportEquipmentDeviceRefBo();ref.setSourceId(10L);when(fixture.equipment.createEquipment(any())).thenReturn(ref);
        when(fixture.profiles.saveProfile(any())).thenThrow(new IllegalStateException("fixture SQL failure"));
        var transactions=mock(PlatformTransactionManager.class);var status=new SimpleTransactionStatus();
        when(transactions.getTransaction(any())).thenReturn(status);
        ProxyFactory factory=new ProxyFactory(fixture.service);factory.setProxyTargetClass(true);
        factory.addAdvice(new TransactionInterceptor(transactions,new AnnotationTransactionAttributeSource()));
        fixture.service=(SupportServerScanSyncService)factory.getProxy();
        assertThrows(Exception.class,()->fixture.run(null,fixture.complete()));
        verify(transactions).rollback(status);verify(transactions,never()).commit(any());
    }
    private static class SyncFixture
    {
        final SupportSiteMapper sites=mock(SupportSiteMapper.class);
        final SupportServerMapper servers=mock(SupportServerMapper.class);
        final ISupportEquipmentService equipment=mock(ISupportEquipmentService.class);
        final SupportEquipmentWorkbookService workbook=mock(SupportEquipmentWorkbookService.class);
        final SupportServerScanMapper profiles=mock(SupportServerScanMapper.class);
        SupportServerScanSyncService service=new SupportServerScanSyncService(sites,servers,equipment,workbook,profiles,mock(ISupportChangeLogService.class));
        SyncFixture(){when(sites.selectSiteIdForUpdate(9L)).thenReturn(9L);}
        SupportServerScanProbe.Facts complete(){return new SupportServerScanProbe.Facts("server","CentOS","7","3.10","");}
        Long run(Long id,SupportServerScanProbe.Facts facts){return service.sync(9L,3L,"192.0.2.1",id,"revision",22,true,"","fixture-root",facts,"22,2343,55555","actor");}
    }
}
