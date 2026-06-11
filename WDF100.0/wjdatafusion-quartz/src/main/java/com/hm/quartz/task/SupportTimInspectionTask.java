package com.hm.quartz.task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.hm.manage.domain.vo.SupportTimInspectionDetailVo;
import com.hm.manage.service.ISupportTimInspectionService;

@Component("supportTimInspectionTask")
public class SupportTimInspectionTask
{
    private static final Logger log = LoggerFactory.getLogger(SupportTimInspectionTask.class);

    @Autowired
    private ISupportTimInspectionService timInspectionService;

    public void run()
    {
        run("自动巡检");
    }

    public void run(String executorName)
    {
        SupportTimInspectionDetailVo result = timInspectionService.runScheduledInspection(executorName);
        log.info("TIM系统巡检定时任务执行完成，巡检ID：{}，结果：{}",
                result.getInspection().getInspectionId(), result.getInspection().getResultStatus());
    }

    public void runPlan(Long planId)
    {
        SupportTimInspectionDetailVo result = timInspectionService.runScheduledInspectionPlan(planId, "计划巡检");
        log.info("TIM系统巡检计划执行完成，计划ID：{}，巡检ID：{}，结果：{}",
                planId, result.getInspection().getInspectionId(), result.getInspection().getResultStatus());
    }
}
