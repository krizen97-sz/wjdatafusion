package com.hm.manage.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.hm.common.annotation.Log;
import com.hm.common.core.controller.BaseController;
import com.hm.common.core.domain.AjaxResult;
import com.hm.common.enums.BusinessType;
import com.hm.manage.service.IIpamScanService;

@RestController
@RequestMapping("/ipam/scan")
public class IpamScanController extends BaseController
{
    @Autowired
    private IIpamScanService ipamScanService;

    @PreAuthorize("@ss.hasPermi('ipam:network:scan')")
    @Log(title = "IP分配管控-网段扫描", businessType = BusinessType.OTHER)
    @PostMapping("/network/{networkId}")
    public AjaxResult scanNetwork(@PathVariable Long networkId)
    {
        return success(ipamScanService.startNetworkScan(networkId));
    }

    @PreAuthorize("@ss.hasPermi('ipam:network:list')")
    @GetMapping("/job/{scanId}")
    public AjaxResult scanJob(@PathVariable Long scanId)
    {
        return success(ipamScanService.selectScanJobById(scanId));
    }

    @PreAuthorize("@ss.hasPermi('ipam:network:list')")
    @GetMapping("/network/{networkId}/latest")
    public AjaxResult latestNetworkScan(@PathVariable Long networkId)
    {
        return success(ipamScanService.selectLatestNetworkScanJob(networkId));
    }
}
