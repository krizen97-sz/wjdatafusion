package com.hm.manage.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import com.hm.common.core.domain.AjaxResult;
import com.hm.manage.domain.IpamNetwork;
import com.hm.manage.service.IIpamService;

class IpamControllerTest
{
    @Test
    void networkTreeMustReturnTheCompleteFilteredListWithoutControllerPagination()
    {
        IIpamService service = mock(IIpamService.class);
        IpamNetwork query = new IpamNetwork();
        query.setKeyword("湖塘");
        List<IpamNetwork> networks = List.of(new IpamNetwork(), new IpamNetwork());
        when(service.selectNetworkList(query)).thenReturn(networks);
        IpamController controller = new IpamController();
        ReflectionTestUtils.setField(controller, "ipamService", service);

        AjaxResult result = controller.networkTree(query);

        assertSame(networks, result.get("data"));
    }

    @Test
    void credentialResponseMustNeverBeCached()
    {
        IIpamService service = mock(IIpamService.class);
        when(service.getAddressCredential(1L)).thenReturn("secret");
        IpamController controller = new IpamController();
        ReflectionTestUtils.setField(controller, "ipamService", service);
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.getAddressCredential(1L, response);

        assertEquals("no-store, no-cache, must-revalidate", response.getHeader(HttpHeaders.CACHE_CONTROL));
        assertEquals("no-cache", response.getHeader(HttpHeaders.PRAGMA));
        assertEquals(0L, response.getDateHeader(HttpHeaders.EXPIRES));
    }
}
