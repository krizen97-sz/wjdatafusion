package com.hm.quartz.service;

import org.quartz.SchedulerException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.hm.common.constant.ScheduleConstants;
import com.hm.common.exception.ServiceException;
import com.hm.common.exception.job.TaskException;
import com.hm.common.utils.StringUtils;
import com.hm.manage.domain.SupportTimInspectionPlan;
import com.hm.manage.domain.vo.SupportTimInspectionDetailVo;
import com.hm.manage.service.ISupportTimInspectionPlanService;
import com.hm.manage.service.ISupportTimInspectionService;
import com.hm.quartz.domain.SysJob;
import com.hm.quartz.util.CronUtils;

@Service
public class SupportTimInspectionPlanScheduleService
{
    private static final String JOB_GROUP = "DEFAULT";
    private static final String STATUS_NORMAL = ScheduleConstants.Status.NORMAL.getValue();
    private static final String STATUS_PAUSE = ScheduleConstants.Status.PAUSE.getValue();

    @Autowired
    private ISupportTimInspectionPlanService planService;

    @Autowired
    private ISupportTimInspectionService timInspectionService;

    @Autowired
    private ISysJobService jobService;

    @Transactional(rollbackFor = Exception.class)
    public SupportTimInspectionPlan savePlan(SupportTimInspectionPlan plan, String username)
            throws SchedulerException, TaskException
    {
        validateCron(plan);
        Long planId = planService.savePlan(plan);
        SupportTimInspectionPlan saved = planService.selectPlanById(planId);
        syncQuartzJob(saved, username);
        return planService.selectPlanById(planId);
    }

    @Transactional(rollbackFor = Exception.class)
    public int changeStatus(Long planId, String status, String username) throws SchedulerException, TaskException
    {
        SupportTimInspectionPlan plan = planService.selectPlanById(planId);
        String normalizedStatus = STATUS_NORMAL.equals(status) ? STATUS_NORMAL : STATUS_PAUSE;
        planService.updatePlanStatus(planId, normalizedStatus);
        plan.setStatus(normalizedStatus);
        if (plan.getJobId() == null || jobService.selectJobById(plan.getJobId()) == null)
        {
            syncQuartzJob(plan, username);
            return 1;
        }
        SysJob job = jobService.selectJobById(plan.getJobId());
        job.setStatus(normalizedStatus);
        job.setUpdateBy(username);
        return jobService.changeStatus(job);
    }

    @Transactional(rollbackFor = Exception.class)
    public int deletePlan(Long planId) throws SchedulerException
    {
        SupportTimInspectionPlan plan = planService.selectPlanById(planId);
        if (plan.getJobId() != null)
        {
            SysJob job = jobService.selectJobById(plan.getJobId());
            if (job != null)
            {
                jobService.deleteJob(job);
            }
        }
        return planService.deletePlanById(planId);
    }

    public SupportTimInspectionDetailVo runPlanOnce(Long planId)
    {
        return timInspectionService.runManualInspectionPlan(planId);
    }

    private void syncQuartzJob(SupportTimInspectionPlan plan, String username) throws SchedulerException, TaskException
    {
        SysJob job = buildJob(plan);
        if (plan.getJobId() == null || jobService.selectJobById(plan.getJobId()) == null)
        {
            job.setCreateBy(username);
            jobService.insertJob(job);
            planService.updatePlanJobId(plan.getPlanId(), job.getJobId());
            if (STATUS_NORMAL.equals(plan.getStatus()))
            {
                SysJob savedJob = jobService.selectJobById(job.getJobId());
                savedJob.setStatus(STATUS_NORMAL);
                savedJob.setUpdateBy(username);
                jobService.changeStatus(savedJob);
            }
            return;
        }

        job.setJobId(plan.getJobId());
        job.setUpdateBy(username);
        jobService.updateJob(job);
    }

    private SysJob buildJob(SupportTimInspectionPlan plan)
    {
        SysJob job = new SysJob();
        job.setJobName(buildJobName(plan.getPlanName()));
        job.setJobGroup(JOB_GROUP);
        job.setInvokeTarget("supportTimInspectionTask.runPlan(" + plan.getPlanId() + "L)");
        job.setCronExpression(plan.getCronExpression());
        job.setMisfirePolicy(ScheduleConstants.MISFIRE_DO_NOTHING);
        job.setConcurrent("1");
        job.setStatus(STATUS_NORMAL.equals(plan.getStatus()) ? STATUS_NORMAL : STATUS_PAUSE);
        job.setRemark("由TIM巡检计划自动生成，请在TIM系统巡检页面维护。");
        return job;
    }

    private String buildJobName(String planName)
    {
        String name = "TIM巡检计划-" + StringUtils.defaultIfBlank(planName, "未命名计划");
        return name.length() > 64 ? name.substring(0, 64) : name;
    }

    private void validateCron(SupportTimInspectionPlan plan)
    {
        if (plan == null || StringUtils.isBlank(plan.getCronExpression()))
        {
            throw new ServiceException("Cron表达式不能为空");
        }
        if (!CronUtils.isValid(plan.getCronExpression()))
        {
            throw new ServiceException("Cron表达式不正确：" + CronUtils.getInvalidMessage(plan.getCronExpression()));
        }
    }
}
