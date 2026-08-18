package com.hm.quartz.task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.hm.manage.domain.IpamScanJob;
import com.hm.manage.service.IIpamScanService;

@Component("ipamScanTask")
public class IpamScanTask
{
    private static final Logger log = LoggerFactory.getLogger(IpamScanTask.class);

    @Autowired
    private IIpamScanService ipamScanService;

    public void scanAllNetworks()
    {
        IpamScanJob scanJob = ipamScanService.scanAllNetworks();
        log.info("IPAM定时全域扫描完成，任务ID：{}，状态：{}，在线：{}，离线：{}，异常：{}",
            scanJob.getScanId(), scanJob.getScanStatus(), scanJob.getOnlineCount(),
            scanJob.getOfflineCount(), scanJob.getErrorCount());
    }
}
