package com.hm.manage.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.DigestUtils;
import com.hm.common.exception.ServiceException;
import com.hm.manage.domain.bo.AutoInspectionMetricQuery;
import com.hm.manage.domain.vo.AutoInspectionMetricSampleVo;
import com.hm.manage.domain.vo.AutoInspectionMetricsVo;
import com.hm.manage.domain.vo.AutoInspectionMetricsVo.ExecutionPoint;
import com.hm.manage.domain.vo.AutoInspectionMetricsVo.MetricPoint;
import com.hm.manage.domain.vo.AutoInspectionMetricsVo.MetricSeries;
import com.hm.manage.domain.vo.AutoInspectionMetricsVo.PlanOption;
import com.hm.manage.mapper.SupportAutoInspectionMetricsMapper;
import com.hm.manage.service.ISupportAutoInspectionMetricsService;

@Service
public class SupportAutoInspectionMetricsServiceImpl implements ISupportAutoInspectionMetricsService
{
    static final int SAMPLE_LIMIT = 5000;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final SupportAutoInspectionMetricsMapper mapper;
    private final Clock clock;

    @Autowired
    public SupportAutoInspectionMetricsServiceImpl(SupportAutoInspectionMetricsMapper mapper)
    {
        this(mapper, Clock.systemDefaultZone());
    }

    SupportAutoInspectionMetricsServiceImpl(SupportAutoInspectionMetricsMapper mapper, Clock clock)
    {
        this.mapper = mapper;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public AutoInspectionMetricsVo selectMetrics(AutoInspectionMetricQuery query)
    {
        AutoInspectionMetricQuery request = query == null ? new AutoInspectionMetricQuery() : query;
        int days = request.getDays() == null ? 7 : request.getDays();
        if (days < 1 || days > 30) throw new ServiceException("统计时间范围必须在1到30天之间");
        if (request.getPlanId() != null && request.getPlanId() <= 0) throw new ServiceException("巡检计划ID无效");
        Date begin = Date.from(LocalDate.now(clock).minusDays(days - 1L).atStartOfDay(clock.getZone()).toInstant());
        Date end = Date.from(clock.instant());
        List<PlanOption> plans = mapper.selectPlans(begin, end);

        AutoInspectionMetricsVo result = new AutoInspectionMetricsVo();
        result.setPlans(plans);
        result.setBeginTime(formatTime(begin));
        result.setEndTime(formatTime(end));
        result.setGeneratedTime(formatTime(end));
        result.setSampleLimit(SAMPLE_LIMIT);
        if (plans.isEmpty() && request.getPlanId() == null) return result;

        Long planId = request.getPlanId() == null ? plans.get(0).getPlanId() : request.getPlanId();
        if (plans.stream().noneMatch(plan -> planId.equals(plan.getPlanId())))
        {
            throw new ServiceException("巡检计划不存在或已删除");
        }
        result.setPlanId(planId);
        List<AutoInspectionMetricSampleVo> raw = mapper.selectSamples(planId, begin, end, SAMPLE_LIMIT + 1);
        List<ExecutionPoint> rawExecutions = mapper.selectExecutions(planId, begin, end, SAMPLE_LIMIT + 1);
        result.setTruncated(raw.size() > SAMPLE_LIMIT || rawExecutions.size() > SAMPLE_LIMIT);
        List<AutoInspectionMetricSampleVo> samples = new ArrayList<>(raw.subList(0, Math.min(raw.size(), SAMPLE_LIMIT)));
        samples.sort(Comparator.comparing(AutoInspectionMetricSampleVo::getSampleTime)
                .thenComparing(AutoInspectionMetricSampleVo::getResultId));
        List<ExecutionPoint> executions = new ArrayList<>(rawExecutions.subList(0, Math.min(rawExecutions.size(), SAMPLE_LIMIT)));
        executions.sort(Comparator.comparing(ExecutionPoint::getSampleTime).thenComparing(ExecutionPoint::getRecordId));
        result.setExecutions(executions);
        result.setSampleCount(samples.size());
        result.setMetrics(buildSeries(samples));
        return result;
    }

    private List<MetricSeries> buildSeries(List<AutoInspectionMetricSampleVo> samples)
    {
        Map<MetricIdentity, List<AutoInspectionMetricSampleVo>> identities = new LinkedHashMap<>();
        for (AutoInspectionMetricSampleVo sample : samples)
        {
            MetricIdentity identity = new MetricIdentity(
                    sample.getStepId() == null ? "name:" + text(sample.getStepName()) : "id:" + sample.getStepId(),
                    text(sample.getToolCode()),
                    sample.getTargetId() == null ? text(sample.getTargetType()) + ":" + text(sample.getTargetName()) : "id:" + sample.getTargetId(),
                    fallback(sample.getComparisonScope(), "CONTINUOUS"));
            identities.computeIfAbsent(identity, key -> new ArrayList<>()).add(sample);
        }
        List<MetricSeries> result = new ArrayList<>();
        identities.forEach((identity, group) -> {
            Set<String> knownUnits = new LinkedHashSet<>();
            group.forEach(sample -> {
                String unit = normalizeUnit(sample.getActualUnit());
                if (!unit.isEmpty()) knownUnits.add(unit);
            });
            Map<String, List<AutoInspectionMetricSampleVo>> units = new LinkedHashMap<>();
            for (AutoInspectionMetricSampleVo sample : group)
            {
                String unit = normalizeUnit(sample.getActualUnit());
                // Failed samples may omit their unit. Only a unique known unit is safe to reuse.
                if (unit.isEmpty() && sample.getActualValue() == null && knownUnits.size() == 1)
                {
                    unit = knownUnits.iterator().next();
                }
                units.computeIfAbsent(unit, key -> new ArrayList<>()).add(sample);
            }
            units.forEach((unit, rows) -> result.add(aggregate(identity, unit, rows)));
        });
        result.sort(Comparator.comparing((MetricSeries metric) -> metric.getNumericCount() == 0)
                .thenComparing(MetricSeries::getMetricName));
        return result;
    }

    private MetricSeries aggregate(MetricIdentity identity, String unit, List<AutoInspectionMetricSampleVo> rows)
    {
        AutoInspectionMetricSampleVo last = rows.get(rows.size() - 1);
        MetricSeries metric = new MetricSeries();
        metric.setMetricKey(DigestUtils.md5DigestAsHex((identity + "\u0000" + unit).getBytes(StandardCharsets.UTF_8)));
        metric.setStepName(fallback(last.getStepName(), fallback(last.getToolName(), "未命名步骤")));
        metric.setTargetName(fallback(last.getTargetName(), "未命名目标"));
        metric.setToolName(fallback(last.getToolName(), last.getToolCode()));
        metric.setMetricName(metric.getStepName().equals(metric.getTargetName()) ? metric.getStepName()
                : metric.getStepName() + " · " + metric.getTargetName());
        metric.setUnit(unit);
        metric.setComparisonScope(identity.scope());
        metric.setSampleCount(rows.size());
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String status : List.of("1", "4", "2", "3")) counts.put(status, 0);
        List<MetricPoint> points = new ArrayList<>();
        List<BigDecimal> values = new ArrayList<>();
        for (AutoInspectionMetricSampleVo row : rows)
        {
            MetricPoint point = new MetricPoint();
            point.setResultId(row.getResultId());
            point.setRecordId(row.getRecordId());
            point.setSampleTime(formatTime(row.getSampleTime()));
            point.setValue(decimalText(row.getActualValue()));
            boolean baseline = "Y".equals(row.getBaselineFlag());
            point.setPreviousValue(baseline ? null : decimalText(row.getPreviousValue()));
            point.setChangeValue(baseline ? null : decimalText(row.getChangeValue()));
            point.setBaselineFlag(baseline ? "Y" : "N");
            point.setEvaluationMode(row.getEvaluationMode());
            point.setEvaluationRule(row.getEvaluationRule());
            point.setWindowKey(row.getWindowKey());
            String status = counts.containsKey(row.getResultStatus()) ? row.getResultStatus() : "3";
            point.setResultStatus(status);
            counts.computeIfPresent(status, (key, count) -> count + 1);
            points.add(point);
            if (row.getActualValue() != null) values.add(row.getActualValue());
        }
        metric.setPoints(points);
        metric.setLatest(points.get(points.size() - 1));
        metric.setStatusCounts(counts);
        metric.setNumericCount(values.size());
        if (!values.isEmpty())
        {
            metric.setMinimum(decimalText(values.stream().min(BigDecimal::compareTo).orElseThrow()));
            metric.setMaximum(decimalText(values.stream().max(BigDecimal::compareTo).orElseThrow()));
            BigDecimal sum = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            metric.setAverage(decimalText(sum.divide(BigDecimal.valueOf(values.size()), 4, RoundingMode.HALF_UP)));
        }
        return metric;
    }

    private String formatTime(Date value)
    {
        return value.toInstant().atZone(clock.getZone()).format(TIME_FORMAT);
    }

    private static String decimalText(BigDecimal value)
    {
        return value == null ? null : value.stripTrailingZeros().toPlainString();
    }

    private static String normalizeUnit(String unit)
    {
        String value = text(unit);
        return "%%".equals(value) ? "%" : value;
    }

    private static String text(String value) { return value == null ? "" : value.trim(); }
    private static String fallback(String value, String fallback) { return text(value).isEmpty() ? fallback : text(value); }

    private record MetricIdentity(String step, String tool, String target, String scope) { }
}
