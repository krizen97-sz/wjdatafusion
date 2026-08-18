package com.hm.manage.controller;

import java.util.List;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.hm.common.annotation.Log;
import com.hm.common.core.controller.BaseController;
import com.hm.common.core.domain.AjaxResult;
import com.hm.common.core.page.TableDataInfo;
import com.hm.common.enums.BusinessType;
import com.hm.manage.domain.IpamAddress;
import com.hm.manage.domain.bo.IpamWorkbookCommitBo;
import com.hm.manage.service.IIpamWorkbookService;

@RestController
@RequestMapping("/ipam/workbook")
public class IpamWorkbookController extends BaseController
{
    @Autowired
    private IIpamWorkbookService ipamWorkbookService;

    @PreAuthorize("@ss.hasPermi('ipam:address:list')")
    @GetMapping("/catalog")
    public AjaxResult catalog()
    {
        return success(ipamWorkbookService.getCatalog());
    }

    @PreAuthorize("@ss.hasPermi('ipam:address:list')")
    @GetMapping("/community/list")
    public TableDataInfo communityList(@RequestParam String communityName)
    {
        startPage();
        List<IpamAddress> list = ipamWorkbookService.selectCommunityAddressList(communityName);
        return getDataTable(list);
    }

    @PreAuthorize("@ss.hasPermi('ipam:address:allocate') and (!#workbook.containsIssued() or @ss.hasPermi('ipam:address:issue'))")
    @Log(title = "IP分配管控-在线工作表", businessType = BusinessType.UPDATE,
        isSaveRequestData = false, isSaveResponseData = false)
    @PostMapping("/commit")
    public AjaxResult commit(@Valid @RequestBody IpamWorkbookCommitBo workbook)
    {
        return toAjax(ipamWorkbookService.commitWorkbook(workbook));
    }
}
