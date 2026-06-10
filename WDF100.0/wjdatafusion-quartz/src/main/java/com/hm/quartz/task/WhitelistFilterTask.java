package com.hm.quartz.task;

import com.hm.manage.domain.vo.WhitelistKafkaPullResultVo;
import com.hm.manage.service.IWhitelistFilterDataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 白名单过滤数据拉取任务
 * 按若依定时任务约定，将任务处理类放在 quartz 模块统一管理。
 */
@Component("whitelistFilterTask")
public class WhitelistFilterTask
{
    private static final Logger log = LoggerFactory.getLogger(WhitelistFilterTask.class);

    @Autowired
    private IWhitelistFilterDataService whitelistFilterDataService;

    public void pullKafkaData()
    {
        WhitelistKafkaPullResultVo result = whitelistFilterDataService.pullKafkaData();
        log.info("白名单过滤数据任务执行完成，本次消费 {} 条，解析 {} 条，入库 {} 条，跳过 {} 条",
                result.getPolledMessages(), result.getParsedMessages(), result.getInsertedRows(), result.getSkippedMessages());
    }
}
