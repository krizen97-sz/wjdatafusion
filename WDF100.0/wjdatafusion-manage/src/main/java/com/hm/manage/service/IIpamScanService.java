package com.hm.manage.service;

import com.hm.manage.domain.IpamScanJob;

public interface IIpamScanService
{
    IpamScanJob startNetworkScan(Long networkId);

    IpamScanJob selectScanJobById(Long scanId);

    IpamScanJob selectLatestNetworkScanJob(Long networkId);

    IpamScanJob scanAllNetworks();
}
