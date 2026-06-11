package com.hm.manage.service.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.hm.common.exception.ServiceException;
import com.hm.common.utils.DateUtils;
import com.hm.common.utils.SecurityUtils;
import com.hm.common.utils.StringUtils;
import com.hm.manage.domain.SupportTimInspectionItemConfig;
import com.hm.manage.domain.SupportTimInspectionPlan;
import com.hm.manage.domain.SupportTimInspectionPlanItem;
import com.hm.manage.domain.SupportTimInspectionPlanTarget;
import com.hm.manage.domain.SupportTimInspectionTarget;
import com.hm.manage.mapper.SupportTimInspectionMapper;
import com.hm.manage.mapper.SupportTimInspectionPlanMapper;
import com.hm.manage.service.ISupportTimInspectionPlanService;

@Service
public class SupportTimInspectionPlanServiceImpl implements ISupportTimInspectionPlanService
{
    private static final String ENABLED = "Y";
    private static final String DISABLED = "N";
    private static final String STATUS_NORMAL = "0";
    private static final String REPORT_STANDARD = "STANDARD";
    private static final String RULE_MIN = "MIN";
    private static final String RULE_MAX = "MAX";

    @Autowired
    private SupportTimInspectionPlanMapper planMapper;

    @Autowired
    private SupportTimInspectionMapper timInspectionMapper;

    @Override
    public List<SupportTimInspectionPlan> selectPlanList(SupportTimInspectionPlan plan)
    {
        return planMapper.selectPlanList(plan);
    }

    @Override
    public SupportTimInspectionPlan selectPlanById(Long planId)
    {
        SupportTimInspectionPlan plan = planMapper.selectPlanById(planId);
        if (plan == null)
        {
            throw new ServiceException("巡检计划不存在");
        }
        fillPlanItems(plan);
        return plan;
    }

    @Override
    public SupportTimInspectionPlan buildPlanTemplate()
    {
        SupportTimInspectionPlan plan = new SupportTimInspectionPlan();
        plan.setStatus(STATUS_NORMAL);
        plan.setReportStyle(REPORT_STANDARD);
        plan.setItems(buildDefaultItems());
        return plan;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long savePlan(SupportTimInspectionPlan plan)
    {
        normalizePlan(plan);
        if (plan.getPlanId() == null)
        {
            plan.setCreateBy(getCurrentUsername());
            plan.setCreateTime(DateUtils.getNowDate());
            planMapper.insertPlan(plan);
        }
        else
        {
            if (planMapper.selectPlanById(plan.getPlanId()) == null)
            {
                throw new ServiceException("巡检计划不存在");
            }
            plan.setUpdateBy(getCurrentUsername());
            plan.setUpdateTime(DateUtils.getNowDate());
            planMapper.updatePlan(plan);
            planMapper.deleteItemsByPlanId(plan.getPlanId());
            planMapper.deleteTargetsByPlanId(plan.getPlanId());
        }
        savePlanItems(plan);
        return plan.getPlanId();
    }

    @Override
    public int updatePlanJobId(Long planId, Long jobId)
    {
        return planMapper.updatePlanJobId(planId, jobId);
    }

    @Override
    public int updatePlanStatus(Long planId, String status)
    {
        if (!STATUS_NORMAL.equals(status))
        {
            status = "1";
        }
        return planMapper.updatePlanStatus(planId, status, getCurrentUsername());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deletePlanById(Long planId)
    {
        planMapper.deleteItemsByPlanId(planId);
        planMapper.deleteTargetsByPlanId(planId);
        return planMapper.deletePlanById(planId);
    }

    @Override
    public List<SupportTimInspectionPlanItem> selectPlanItems(Long planId)
    {
        SupportTimInspectionPlan plan = selectPlanById(planId);
        return plan.getItems();
    }

    private void fillPlanItems(SupportTimInspectionPlan plan)
    {
        List<SupportTimInspectionPlanItem> items = planMapper.selectItemsByPlanId(plan.getPlanId());
        Map<String, List<Long>> targetMap = new HashMap<>();
        for (SupportTimInspectionPlanTarget target : planMapper.selectPlanTargetsByPlanId(plan.getPlanId()))
        {
            targetMap.computeIfAbsent(target.getItemCode(), key -> new ArrayList<>()).add(target.getTargetId());
        }
        for (SupportTimInspectionPlanItem item : items)
        {
            item.setTargetIds(targetMap.getOrDefault(item.getItemCode(), new ArrayList<>()));
        }
        plan.setItems(items);
    }

    private List<SupportTimInspectionPlanItem> buildDefaultItems()
    {
        List<SupportTimInspectionPlanItem> items = new ArrayList<>();
        for (SupportTimInspectionItemConfig config : timInspectionMapper.selectItemConfigList())
        {
            SupportTimInspectionPlanItem item = new SupportTimInspectionPlanItem();
            item.setItemCode(config.getItemCode());
            item.setItemName(config.getItemName());
            item.setItemType(config.getItemType());
            item.setEnabledFlag(config.getEnabledFlag());
            item.setSortOrder(config.getSortOrder());
            item.setThresholdValue(config.getThresholdValue());
            item.setThresholdUnit(config.getThresholdUnit());
            item.setCompareRule(config.getCompareRule());
            item.setTimeWindowMinutes(config.getTimeWindowMinutes());
            item.setTimeoutSeconds(config.getTimeoutSeconds());
            List<Long> targetIds = new ArrayList<>();
            for (SupportTimInspectionTarget target : timInspectionMapper.selectEnabledTargetsByItemCode(config.getItemCode()))
            {
                targetIds.add(target.getTargetId());
            }
            item.setTargetIds(targetIds);
            items.add(item);
        }
        return items;
    }

    private void normalizePlan(SupportTimInspectionPlan plan)
    {
        if (plan == null)
        {
            throw new ServiceException("巡检计划不能为空");
        }
        plan.setPlanName(StringUtils.trimToEmpty(plan.getPlanName()));
        if (StringUtils.isBlank(plan.getPlanName()))
        {
            throw new ServiceException("巡检计划名称不能为空");
        }
        if (StringUtils.isBlank(plan.getCronExpression()))
        {
            throw new ServiceException("Cron表达式不能为空");
        }
        plan.setStatus(STATUS_NORMAL.equals(plan.getStatus()) ? STATUS_NORMAL : "1");
        plan.setReportStyle(StringUtils.defaultIfBlank(plan.getReportStyle(), REPORT_STANDARD));
        if (plan.getItems() == null || plan.getItems().isEmpty())
        {
            plan.setItems(buildDefaultItems());
        }
    }

    private void savePlanItems(SupportTimInspectionPlan plan)
    {
        for (SupportTimInspectionPlanItem item : plan.getItems())
        {
            normalizePlanItem(item);
            item.setPlanId(plan.getPlanId());
            item.setCreateBy(getCurrentUsername());
            item.setCreateTime(DateUtils.getNowDate());
            planMapper.insertPlanItem(item);
            if (item.getTargetIds() == null)
            {
                continue;
            }
            for (Long targetId : item.getTargetIds())
            {
                if (targetId == null)
                {
                    continue;
                }
                SupportTimInspectionPlanTarget target = new SupportTimInspectionPlanTarget();
                target.setPlanId(plan.getPlanId());
                target.setItemCode(item.getItemCode());
                target.setTargetId(targetId);
                target.setCreateBy(getCurrentUsername());
                target.setCreateTime(DateUtils.getNowDate());
                planMapper.insertPlanTarget(target);
            }
        }
    }

    private void normalizePlanItem(SupportTimInspectionPlanItem item)
    {
        if (StringUtils.isBlank(item.getItemCode()))
        {
            throw new ServiceException("巡检项编码不能为空");
        }
        SupportTimInspectionItemConfig config = timInspectionMapper.selectItemConfigByCode(item.getItemCode());
        if (config == null)
        {
            throw new ServiceException("巡检项配置不存在：" + item.getItemCode());
        }
        item.setItemName(StringUtils.defaultIfBlank(item.getItemName(), config.getItemName()));
        item.setItemType(config.getItemType());
        item.setEnabledFlag(ENABLED.equals(item.getEnabledFlag()) ? ENABLED : DISABLED);
        item.setSortOrder(item.getSortOrder() == null ? config.getSortOrder() : item.getSortOrder());
        item.setThresholdValue(item.getThresholdValue() == null ? config.getThresholdValue() : item.getThresholdValue());
        item.setThresholdUnit(StringUtils.defaultIfBlank(item.getThresholdUnit(), config.getThresholdUnit()));
        item.setCompareRule(RULE_MIN.equals(item.getCompareRule()) ? RULE_MIN : RULE_MAX);
        item.setTimeWindowMinutes(item.getTimeWindowMinutes() == null ? config.getTimeWindowMinutes() : item.getTimeWindowMinutes());
        item.setTimeoutSeconds(item.getTimeoutSeconds() == null ? config.getTimeoutSeconds() : item.getTimeoutSeconds());
    }

    private String getCurrentUsername()
    {
        try
        {
            return SecurityUtils.getUsername();
        }
        catch (Exception e)
        {
            return "system";
        }
    }
}
