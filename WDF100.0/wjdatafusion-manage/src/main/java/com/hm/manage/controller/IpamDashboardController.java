package com.hm.manage.controller;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.hm.common.core.controller.BaseController;
import com.hm.common.core.domain.AjaxResult;
import com.hm.common.core.page.TableDataInfo;
import com.hm.manage.domain.vo.IpamCommunityAddressVo;
import com.hm.manage.service.IIpamDashboardService;

@RestController
@RequestMapping("/ipam/dashboard")
public class IpamDashboardController extends BaseController
{
    @Autowired
    private IIpamDashboardService ipamDashboardService;

    @PreAuthorize("@ss.hasPermi('ipam:network:list')")
    @GetMapping
    public AjaxResult dashboard(@RequestParam(required = false) String policeStationName)
    {
        return success(ipamDashboardService.getDashboard(policeStationName));
    }

    @PreAuthorize("@ss.hasPermi('ipam:address:list')")
    @GetMapping("/community/detail")
    public TableDataInfo communityDetail(@RequestParam String communityName)
    {
        startPage();
        List<IpamCommunityAddressVo> list = ipamDashboardService.selectCommunityAddressList(communityName);
        return getDataTable(list);
    }
}
