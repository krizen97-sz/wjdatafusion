package com.hm.quartz.task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.hm.manage.domain.vo.AutoInspectionRunResultVo;
import com.hm.manage.service.ISupportAutoInspectionService;

@Component("supportAutoInspectionTask")
public class SupportAutoInspectionTask
{
    private static final Logger log = LoggerFactory.getLogger(SupportAutoInspectionTask.class);

    @Autowired
    private ISupportAutoInspectionService autoInspectionService;

    public void runPlan(Long planId)
    {
        AutoInspectionRunResultVo result = autoInspectionService.runScheduledPlan(planId, "计划巡检");
        log.info("自动化巡检计划执行完成，计划ID：{}，记录ID：{}，结果：{}",
                planId, result.getRecordId(), result.getResultStatus());
    }
}
