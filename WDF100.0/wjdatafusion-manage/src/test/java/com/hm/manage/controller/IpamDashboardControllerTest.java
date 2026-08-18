package com.hm.manage.controller;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import com.hm.common.core.domain.AjaxResult;
import com.hm.manage.domain.vo.IpamDashboardVo;
import com.hm.manage.service.IIpamDashboardService;

class IpamDashboardControllerTest
{
    @Test
    void dashboardMustReturnTheAggregatedView()
    {
        IIpamDashboardService service = mock(IIpamDashboardService.class);
        IpamDashboardVo dashboard = new IpamDashboardVo();
        when(service.getDashboard(null)).thenReturn(dashboard);
        IpamDashboardController controller = new IpamDashboardController();
        ReflectionTestUtils.setField(controller, "ipamDashboardService", service);

        AjaxResult result = controller.dashboard(null);

        assertSame(dashboard, result.get("data"));
    }
}
