package com.hm.manage.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;
import com.hm.framework.web.service.PermissionService;
import com.hm.manage.controller.SupportEquipmentController;
import com.hm.manage.domain.bo.SupportEquipmentBatchBo;
import com.hm.manage.domain.bo.SupportEquipmentDeviceRefBo;
import com.hm.manage.service.ISupportEquipmentService;

class SupportEquipmentPermissionTest
{
    private final SupportEquipmentController controller = new SupportEquipmentController();
    private PermissionService permissions;
    private ISupportEquipmentService equipment;

    @BeforeEach
    void setup()
    {
        permissions = mock(PermissionService.class);
        equipment = mock(ISupportEquipmentService.class);
        ReflectionTestUtils.setField(controller, "permissions", permissions);
        ReflectionTestUtils.setField(controller, "equipmentService", equipment);
    }

    private SupportEquipmentBatchBo command(String... types)
    {
        var command = new SupportEquipmentBatchBo();
        command.setSiteId(1L);
        command.setDevices(java.util.Arrays.stream(types).map(type -> {
            var ref = new SupportEquipmentDeviceRefBo(); ref.setSourceType(type); ref.setSourceId(1L); return ref;
        }).toList());
        return command;
    }

    @Test
    void serverOnlyPermissionCannotDeleteHardwareOrMixedBatches()
    {
        when(permissions.hasPermi("support:server:remove")).thenReturn(true);
        assertThrows(AccessDeniedException.class, () -> controller.remove(command("HARDWARE")));
        assertThrows(AccessDeniedException.class, () -> controller.remove(command("SERVER", "HARDWARE")));
        verifyNoInteractions(equipment);
        var serverOnly = command("SERVER");
        controller.remove(serverOnly);
        verify(equipment).deleteEquipmentAssets(serverOnly);
    }

    @Test
    void unifiedPermissionAllowsBothKnownDeviceTypesButNotUnknownSources()
    {
        when(permissions.hasPermi("support:equipment:remove")).thenReturn(true);
        var both = command("SERVER", "HARDWARE");
        controller.remove(both);
        verify(equipment).deleteEquipmentAssets(both);
        assertThrows(AccessDeniedException.class, () -> controller.remove(command("unknown")));
    }

    @Test
    void hardwareExportPermissionDoesNotAuthorizeServerExport()
    {
        when(permissions.hasPermi("support:hardwareAsset:export")).thenReturn(true);
        assertEquals(Boolean.TRUE, ReflectionTestUtils.invokeMethod(controller, "canOperate", "HARDWARE", "export"));
        assertEquals(Boolean.FALSE, ReflectionTestUtils.invokeMethod(controller, "canOperate", "SERVER", "export"));
    }

    @Test
    void combiningBothTypedPermissionsAllowsMixedDeletion()
    {
        when(permissions.hasPermi("support:server:remove")).thenReturn(true);
        when(permissions.hasPermi("support:hardwareAsset:remove")).thenReturn(true);
        var both = command("server", " hardware ");
        controller.remove(both);
        verify(equipment).deleteEquipmentAssets(both);
    }
}
