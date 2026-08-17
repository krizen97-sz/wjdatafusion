package com.hm.quartz.service;

import org.quartz.SchedulerException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.hm.common.constant.ScheduleConstants;
import com.hm.common.exception.ServiceException;
import com.hm.common.exception.job.TaskException;
import com.hm.common.utils.StringUtils;
import com.hm.manage.domain.SupportAutoInspectionPlan;
import com.hm.manage.domain.bo.AutoInspectionPlanSaveBo;
import com.hm.manage.domain.vo.AutoInspectionRunResultVo;
import com.hm.manage.service.ISupportAutoInspectionService;
import com.hm.quartz.domain.SysJob;
import com.hm.quartz.util.CronUtils;

@Service
public class SupportAutoInspectionPlanScheduleService
{
    private static final String JOB_GROUP = "DEFAULT";
    private static final String STATUS_NORMAL = ScheduleConstants.Status.NORMAL.getValue();
    private static final String STATUS_PAUSE = ScheduleConstants.Status.PAUSE.getValue();

    @Autowired
    private ISupportAutoInspectionService autoInspectionService;

    @Autowired
    private ISysJobService jobService;

    @Transactional(rollbackFor = Exception.class)
    public SupportAutoInspectionPlan savePlan(AutoInspectionPlanSaveBo plan, String username) throws SchedulerException, TaskException
    {
        validateCron(plan);
        Long planId = autoInspectionService.savePlan(plan);
        SupportAutoInspectionPlan saved = autoInspectionService.selectPlanById(planId);
        syncQuartzJob(saved, username);
        return autoInspectionService.selectPlanById(planId);
    }

    @Transactional(rollbackFor = Exception.class)
    public int changeStatus(Long planId, String status, String username) throws SchedulerException, TaskException
    {
        SupportAutoInspectionPlan plan = autoInspectionService.selectPlanById(planId);
        String normalizedStatus = STATUS_NORMAL.equals(status) ? STATUS_NORMAL : STATUS_PAUSE;
        autoInspectionService.updatePlanStatus(planId, normalizedStatus);
        plan.setStatus(normalizedStatus);
        Long jobId = plan.getJobId();
        if (jobId == null || jobService.selectJobById(jobId) == null)
        {
            syncQuartzJob(plan, username);
            return 1;
        }
        SysJob job = jobService.selectJobById(jobId);
        job.setStatus(normalizedStatus);
        job.setUpdateBy(username);
        return jobService.changeStatus(job);
    }

    @Transactional(rollbackFor = Exception.class)
    public int deletePlan(Long planId) throws SchedulerException
    {
        SupportAutoInspectionPlan plan = autoInspectionService.selectPlanById(planId);
        Long jobId = plan.getJobId();
        if (jobId != null)
        {
            SysJob job = jobService.selectJobById(jobId);
            if (job != null)
            {
                jobService.deleteJob(job);
            }
        }
        return autoInspectionService.deletePlanById(planId);
    }

    public AutoInspectionRunResultVo runPlanOnce(Long planId)
    {
        return autoInspectionService.runManualPlan(planId);
    }

    private void syncQuartzJob(SupportAutoInspectionPlan plan, String username) throws SchedulerException, TaskException
    {
        SysJob job = buildJob(plan);
        Long planId = plan.getPlanId();
        Long jobId = plan.getJobId();
        if (jobId == null || jobService.selectJobById(jobId) == null)
        {
            job.setCreateBy(username);
            jobService.insertJob(job);
            autoInspectionService.updatePlanJobId(planId, job.getJobId());
            if (STATUS_NORMAL.equals(plan.getStatus()))
            {
                SysJob savedJob = jobService.selectJobById(job.getJobId());
                savedJob.setStatus(STATUS_NORMAL);
                savedJob.setUpdateBy(username);
                jobService.changeStatus(savedJob);
            }
            return;
        }

        job.setJobId(jobId);
        job.setUpdateBy(username);
        jobService.updateJob(job);
    }

    private SysJob buildJob(SupportAutoInspectionPlan plan)
    {
        SysJob job = new SysJob();
        Long planId = plan.getPlanId();
        job.setJobName(buildJobCode(planId));
        job.setJobGroup(JOB_GROUP);
        job.setInvokeTarget("supportAutoInspectionTask.runPlan(" + planId + "L)");
        job.setCronExpression(plan.getCronExpression());
        job.setMisfirePolicy(ScheduleConstants.MISFIRE_DO_NOTHING);
        job.setConcurrent("1");
        job.setStatus(STATUS_NORMAL.equals(plan.getStatus()) ? STATUS_NORMAL : STATUS_PAUSE);
        job.setRemark("由自动化巡检计划自动生成，请在自动化巡检页面维护。");
        return job;
    }

    private String buildJobCode(Long planId)
    {
        return "AUTO_INSPECTION_PLAN_" + planId;
    }

    private void validateCron(SupportAutoInspectionPlan plan)
    {
        String cron = plan == null ? null : plan.getCronExpression();
        if (StringUtils.isBlank(cron) || "null".equals(cron))
        {
            throw new ServiceException("执行周期不能为空");
        }
        if (!CronUtils.isValid(cron))
        {
            throw new ServiceException("执行周期不正确：" + CronUtils.getInvalidMessage(cron));
        }
    }

}
