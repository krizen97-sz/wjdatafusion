package com.hm.manage.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import com.hm.common.core.domain.AjaxResult;
import com.hm.manage.domain.bo.IpamWorkbookCommitBo;
import com.hm.manage.service.IIpamWorkbookService;
import com.hm.manage.domain.vo.IpamWorkbookCatalogVo;

class IpamWorkbookControllerTest
{
    @Test
    void catalogMustReturnWorkbookCatalog()
    {
        IIpamWorkbookService service = mock(IIpamWorkbookService.class);
        IpamWorkbookCatalogVo catalog = new IpamWorkbookCatalogVo();
        when(service.getCatalog()).thenReturn(catalog);
        IpamWorkbookController controller = new IpamWorkbookController();
        ReflectionTestUtils.setField(controller, "ipamWorkbookService", service);

        AjaxResult result = controller.catalog();

        assertEquals(catalog, result.get("data"));
    }

    @Test
    void commitMustReturnWorkbookServiceResult()
    {
        IIpamWorkbookService service = mock(IIpamWorkbookService.class);
        IpamWorkbookCommitBo workbook = new IpamWorkbookCommitBo();
        when(service.commitWorkbook(workbook)).thenReturn(3);
        IpamWorkbookController controller = new IpamWorkbookController();
        ReflectionTestUtils.setField(controller, "ipamWorkbookService", service);

        AjaxResult result = controller.commit(workbook);

        assertEquals(200, result.get("code"));
    }
}
