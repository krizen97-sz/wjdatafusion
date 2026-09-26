package com.hm.manage.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.SimpleTransactionStatus;
import com.hm.common.exception.ServiceException;
import com.hm.framework.web.service.PermissionService;
import com.hm.manage.domain.*;
import com.hm.manage.domain.bo.SupportEquipmentWorkbookBo;
import com.hm.manage.mapper.*;
import com.hm.manage.service.ISupportChangeLogService;
import com.hm.manage.service.support.CredentialCryptoService;

class SupportEquipmentWorkbookServiceTest
{
    private SupportEquipmentWorkbookService service;
    private SupportEquipmentWorkbookMapper mapper;
    private SupportPlatformServerRelMapper serverRelations;
    private SupportPlatformAssetRelMapper hardwareRelations;
    private ISupportChangeLogService logs;
    private PermissionService permissions;
    private CredentialCryptoService crypto;
    private List<Map<String, Object>> servers;
    private List<Map<String, Object>> hardware;

    @BeforeEach
    void setUp()
    {
        service = new SupportEquipmentWorkbookService();
        mapper = mock(SupportEquipmentWorkbookMapper.class);
        SupportEquipmentLocationMapper locations = mock(SupportEquipmentLocationMapper.class);
        SupportSiteMapper sites = mock(SupportSiteMapper.class);
        serverRelations = mock(SupportPlatformServerRelMapper.class);
        hardwareRelations = mock(SupportPlatformAssetRelMapper.class);
        permissions = mock(PermissionService.class);
        crypto = mock(CredentialCryptoService.class);
        logs = mock(ISupportChangeLogService.class);
        Map<String, Object> dependencies = new LinkedHashMap<>();
        dependencies.put("mapper", mapper); dependencies.put("locations", locations);
        dependencies.put("sites", sites); dependencies.put("serverRelations", serverRelations);
        dependencies.put("hardwareRelations", hardwareRelations); dependencies.put("permissions", permissions);
        dependencies.put("crypto", crypto); dependencies.put("logs", logs);
        dependencies.put("credentials", mock(SupportServerCredentialMapper.class));
        dependencies.forEach((key, value) -> ReflectionTestUtils.setField(service, key, value));
        when(permissions.hasPermi(anyString())).thenReturn(true);
        when(sites.selectSiteIdForUpdate(9L)).thenReturn(9L);
        when(crypto.encrypt(any())).thenAnswer(call -> call.getArgument(0) == null ? "" : "encrypted-test-value");
        servers = new ArrayList<>(List.of(device("SERVER", 1L, "A", "192.0.2.1", 1L, 2L),
            device("SERVER", 2L, "B", "192.0.2.2", 3L, 4L)));
        hardware = new ArrayList<>(List.of(device("HARDWARE", 3L, "交换机", "192.0.2.3", 5L, 6L)));
        servers.get(0).put("_legacyUsername", "root");
        servers.get(0).put("_legacyCipher", "stored-cipher");
        when(mapper.selectServers(eq(9L), anyBoolean())).thenAnswer(call -> servers);
        when(mapper.selectHardware(eq(9L), anyBoolean())).thenAnswer(call -> hardware);
        when(mapper.selectServerCredentials(eq(9L), anyBoolean())).thenReturn(List.of());
        when(mapper.selectPlatforms(eq(9L), anyBoolean())).thenReturn(List.of(
            new LinkedHashMap<>(Map.of("platformId", 10L, "platformLevel", "SUB", "platformName", "子平台")),
            new LinkedHashMap<>(Map.of("platformId", 11L, "platformLevel", "MAIN", "platformName", "主平台"))));
        SupportEquipmentRoom room = new SupportEquipmentRoom();
        room.setSiteId(9L); room.setRoomId(100L); room.setRoomName("机房");
        SupportEquipmentCabinet cabinet = new SupportEquipmentCabinet();
        cabinet.setSiteId(9L); cabinet.setRoomId(100L); cabinet.setCabinetId(200L); cabinet.setCabinetNo("A01"); cabinet.setUCapacity(45);
        when(locations.selectRoomsBySiteId(9L)).thenReturn(List.of(room));
        when(locations.selectCabinetsBySiteId(9L)).thenReturn(List.of(cabinet));
        when(mapper.updateServer(eq(9L), anyLong(), anyMap(), anyString(), any())).thenReturn(1);
        when(mapper.updateHardware(eq(9L), anyLong(), anyMap(), anyString(), any())).thenReturn(1);
    }

    @Test
    void snapshotNeverReturnsCipherOrPlaintextAndPreservesExistingSshPort()
    {
        Map<String, Object> row = snapshot("SERVER:1");
        assertFalse(row.keySet().stream().anyMatch(key -> key.startsWith("_")));
        assertFalse(row.toString().contains("stored-cipher"));
        assertEquals(22, row.get("sshPort"));
        assertEquals(true, row.get("rootPasswordConfigured"));
        assertEquals(64, row.get("revision").toString().length());
        verifyNoInteractions(crypto);
    }

    @Test
    void revisionSupportsJdbcLocalDateTimeWithoutExposingStoredValues()
    {
        servers.get(0).put("_updateTime", java.time.LocalDateTime.of(2026, 9, 26, 20, 0, 0));
        String first = snapshot("SERVER:1").get("revision").toString();
        assertEquals(first, snapshot("SERVER:1").get("revision"));
        assertEquals(64, first.length());
    }

    @Test
    void rootPolicyIsBooleanSparseAndServerOnly()
    {
        assertEquals(false, snapshot("SERVER:1").get("rootDirectLogin"));
        service.save(command(change("SERVER", 1L, Map.of("rootDirectLogin", true))), "tester");
        verify(mapper).updateServer(eq(9L), eq(1L), argThat(fields -> Boolean.TRUE.equals(fields.get("rootDirectLogin"))
            && !fields.containsKey("equipmentRoom") && !fields.containsKey("_legacyCipher")), eq("tester"), any());
        assertThrows(ServiceException.class, () -> service.save(command(change("SERVER", 1L, Map.of("rootDirectLogin", "false"))), "tester"));
        assertThrows(ServiceException.class, () -> service.save(command(change("HARDWARE", 3L, Map.of("rootDirectLogin", true))), "tester"));
    }

    @Test
    void currentLockingReadRejectsOwnershipChangedAfterTheClientSnapshot()
    {
        var command = command(change("SERVER", 1L, Map.of("assetName", "pending")));
        when(mapper.selectServerBindings(9L, true)).thenReturn(List.of(Map.of("sourceId", 1L, "platformId", 10L)));
        assertThrows(ServiceException.class, () -> service.save(command, "tester"));
        verify(mapper, never()).updateServer(anyLong(), anyLong(), anyMap(), anyString(), any());
    }

    @Test
    void basicPatchDoesNotResetPlacementCredentialsOrPlatformRelations()
    {
        var command = command(change("SERVER", 1L, Map.of("assetName", "新名称")));
        assertEquals(1, service.save(command, "tester").get("updated"));
        ArgumentCaptor<Map<String, Object>> fields = ArgumentCaptor.forClass(Map.class);
        verify(mapper).updateServer(eq(9L), eq(1L), fields.capture(), eq("tester"), any());
        assertEquals(Map.of("assetName", "新名称"), fields.getValue());
        verifyNoInteractions(serverRelations, hardwareRelations, crypto);
    }

    @Test
    void allRowsValidateBeforeAnyWriteAndPositionsCanSwapAsOneBatch()
    {
        var first = change("SERVER", 1L, Map.of("rackUStart", 3L, "rackUEnd", 4L));
        var second = change("SERVER", 2L, Map.of("rackUStart", 1L, "rackUEnd", 2L));
        assertEquals(2, service.save(command(first, second), "tester").get("updated"));
        verify(mapper, times(2)).updateServer(eq(9L), anyLong(), anyMap(), anyString(), any());
    }

    @Test
    void conflictsOutOfBoundsAndCrossSitePlatformAreRejectedWithoutWrites()
    {
        for (Map<String, Object> patch : List.<Map<String, Object>>of(
            Map.of("rackUStart", 5L, "rackUEnd", 6L), Map.of("rackUEnd", 46L),
            Map.of("platformIds", List.of(999L)), Map.of("platformIds", List.of(11L)),
            Map.of("sshPort", 0), Map.of("ipAddress", "192.0.2.2"), Map.of("ipAddress", "192.0.2.999"), Map.of("_legacyCipher", "input-cipher")))
            assertThrows(ServiceException.class, () -> service.save(command(change("SERVER", 1L, patch)), "tester"));
        verify(mapper, never()).updateServer(anyLong(), anyLong(), anyMap(), anyString(), any());
    }

    @Test
    void staleRevisionAndDeletedOrCrossSiteDeviceCannotBeOverwritten()
    {
        var stale = change("SERVER", 1L, Map.of("assetName", "new"));
        servers.get(0).put("assetName", "peer-edit");
        assertThrows(ServiceException.class, () -> service.save(command(stale), "tester"));
        var missing = change("SERVER", 2L, Map.of("assetName", "new"));
        missing.setSourceId(999L);
        assertThrows(ServiceException.class, () -> service.save(command(missing), "tester"));
        verify(mapper, never()).updateServer(anyLong(), anyLong(), anyMap(), anyString(), any());
    }

    @Test
    void passwordClearAndReplaceNeverReachSqlOrAuditAsPlaintext()
    {
        hardware.get(0).put("_passwordCipher", "previous-hardware-cipher");
        service.save(command(change("HARDWARE", 3L, Map.of("loginPassword", "unit-test-secret"))), "tester");
        ArgumentCaptor<Map<String, Object>> fields = ArgumentCaptor.forClass(Map.class);
        verify(mapper).updateHardware(eq(9L), eq(3L), fields.capture(), eq("tester"), any());
        assertFalse(fields.getValue().containsKey("loginPassword"));
        assertEquals("encrypted-test-value", fields.getValue().get("_passwordCipher"));
        ArgumentCaptor<Object> before = ArgumentCaptor.forClass(Object.class), after = ArgumentCaptor.forClass(Object.class);
        verify(logs).record(eq(9L), eq("UPDATE"), eq("HARDWARE_ASSET"), eq(3L), anyString(), anyString(), before.capture(), after.capture());
        assertFalse(after.getValue().toString().contains("unit-test-secret"));
        reset(mapper);
        setUp();
        Map<String, Object> clear = new LinkedHashMap<>(); clear.put("rootPassword", null);
        service.save(command(change("SERVER", 1L, clear)), "tester");
        verify(mapper).updateServer(eq(9L), eq(1L), fields.capture(), anyString(), any());
        assertEquals("", fields.getValue().get("_legacyCipher"));
    }

    @Test
    void mixedPermissionsAreCheckedBeforeWrites()
    {
        when(permissions.hasPermi("support:equipment:edit")).thenReturn(false);
        when(permissions.hasPermi("support:hardwareAsset:edit")).thenReturn(false);
        assertThrows(org.springframework.security.access.AccessDeniedException.class,
            () -> service.save(command(change("SERVER", 1L, Map.of("assetName", "x")),
                change("HARDWARE", 3L, Map.of("assetName", "y"))), "tester"));
        verify(mapper, never()).updateServer(anyLong(), anyLong(), anyMap(), anyString(), any());
    }

    @Test
    void databaseFailureRollsBackTheOuterSpringTransaction()
    {
        var command = command(change("SERVER", 1L, Map.of("assetName", "first")),
            change("SERVER", 2L, Map.of("assetName", "second")));
        when(mapper.updateServer(eq(9L), eq(2L), anyMap(), anyString(), any())).thenReturn(0);
        PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
        var status = new SimpleTransactionStatus();
        when(transactions.getTransaction(any())).thenReturn(status);
        ProxyFactory proxy = new ProxyFactory(service);
        proxy.addAdvice(new TransactionInterceptor(transactions, new AnnotationTransactionAttributeSource()));
        var transactionalService = (SupportEquipmentWorkbookService) proxy.getProxy();
        assertThrows(ServiceException.class, () -> transactionalService.save(command, "tester"));
        verify(transactions).rollback(status);
        verify(transactions, never()).commit(any());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> snapshot(String key)
    {
        return ((List<Map<String, Object>>) service.snapshot(9L).get("rows")).stream()
            .filter(row -> key.equals(row.get("deviceKey"))).findFirst().orElseThrow();
    }
    private SupportEquipmentWorkbookBo.Row change(String type, Long id, Map<String, Object> fields)
    {
        var row = new SupportEquipmentWorkbookBo.Row();
        row.setSourceType(type); row.setSourceId(id); row.setChanges(fields);
        row.setRevision(snapshot(type + ":" + id).get("revision").toString());
        return row;
    }
    private SupportEquipmentWorkbookBo command(SupportEquipmentWorkbookBo.Row... rows)
    {
        var command = new SupportEquipmentWorkbookBo(); command.setSiteId(9L); command.setRows(Arrays.asList(rows)); return command;
    }
    private Map<String, Object> device(String type, Long id, String name, String ip, Long start, Long end)
    {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("siteId", 9L); row.put("sourceType", type); row.put("sourceId", id); row.put("assetName", name);
        row.put("ipAddress", ip); row.put("status", "0"); row.put("assetType", "SERVER".equals(type) ? "SERVER" : "SWITCH");
        row.put("sshPort", 22); row.put("equipmentRoom", "机房"); row.put("cabinetNo", "A01");
        row.put("rackUStart", start); row.put("rackUEnd", end);
        return row;
    }
}
