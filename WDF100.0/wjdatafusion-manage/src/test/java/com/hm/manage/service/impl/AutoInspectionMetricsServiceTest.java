package com.hm.manage.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hm.common.exception.ServiceException;
import com.hm.manage.domain.bo.AutoInspectionMetricQuery;
import com.hm.manage.domain.vo.AutoInspectionMetricSampleVo;
import com.hm.manage.domain.vo.AutoInspectionMetricsVo;
import com.hm.manage.domain.vo.AutoInspectionMetricsVo.PlanOption;
import com.hm.manage.mapper.SupportAutoInspectionMetricsMapper;

class AutoInspectionMetricsServiceTest
{
    private final SupportAutoInspectionMetricsMapper mapper = mock(SupportAutoInspectionMetricsMapper.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-03T04:00:00Z"), ZoneId.of("Asia/Shanghai"));
    private final SupportAutoInspectionMetricsServiceImpl service = new SupportAutoInspectionMetricsServiceImpl(mapper, clock);

    @BeforeEach
    void plans()
    {
        PlanOption plan = new PlanOption();
        plan.setPlanId(7L);
        plan.setPlanName("过车数据统计");
        when(mapper.selectPlans(any(), any())).thenReturn(List.of(plan));
        when(mapper.selectSamples(anyLong(), any(), any(), anyInt())).thenReturn(List.of());
        when(mapper.selectExecutions(anyLong(), any(), any(), anyInt())).thenReturn(List.of());
    }

    @Test
    void validatesQueryRangeAndPlanBeforeReadingSamples()
    {
        AutoInspectionMetricQuery query = new AutoInspectionMetricQuery();
        query.setDays(31);
        assertThrows(ServiceException.class, () -> service.selectMetrics(query));
        query.setDays(0);
        assertThrows(ServiceException.class, () -> service.selectMetrics(query));
        query.setDays(7);
        query.setPlanId(-1L);
        assertThrows(ServiceException.class, () -> service.selectMetrics(query));
        verify(mapper, never()).selectPlans(any(), any());
    }

    @Test
    void emptyCatalogAndUnknownPlanAreExplicit()
    {
        when(mapper.selectPlans(any(), any())).thenReturn(List.of());
        assertTrue(service.selectMetrics(null).getMetrics().isEmpty());
        AutoInspectionMetricQuery query = new AutoInspectionMetricQuery();
        query.setPlanId(99L);
        assertThrows(ServiceException.class, () -> service.selectMetrics(query));
        verify(mapper, never()).selectSamples(anyLong(), any(), any(), anyInt());
    }

    @Test
    void preservesDecimalPrecisionAndIncludesFailedSamplesWithoutInventingValues() throws Exception
    {
        AutoInspectionMetricSampleVo baseline = sample(1, "9007199254740993.12", "条", "1");
        baseline.setBaselineFlag("Y");
        baseline.setPreviousValue(BigDecimal.ZERO);
        baseline.setChangeValue(BigDecimal.ZERO);
        AutoInspectionMetricSampleVo normal = sample(2, "9007199254740995.12", "条", "1");
        normal.setPreviousValue(baseline.getActualValue());
        normal.setChangeValue(new BigDecimal("2"));
        AutoInspectionMetricSampleVo failed = sample(3, null, null, "2");
        when(mapper.selectSamples(anyLong(), any(), any(), anyInt())).thenReturn(List.of(failed, normal, baseline));

        AutoInspectionMetricsVo result = service.selectMetrics(null);
        assertEquals(7L, result.getPlanId());
        assertEquals("2026-08-28 00:00:00", result.getBeginTime());
        assertEquals("2026-09-03 12:00:00", result.getEndTime());
        assertEquals(1, result.getMetrics().size());
        var metric = result.getMetrics().get(0);
        assertEquals(3, metric.getSampleCount());
        assertEquals(2, metric.getNumericCount());
        assertEquals("9007199254740994.12", metric.getAverage());
        assertEquals("9007199254740993.12", metric.getMinimum());
        assertEquals("9007199254740995.12", metric.getMaximum());
        assertNull(metric.getLatest().getValue());
        assertEquals("2", metric.getLatest().getResultStatus());
        assertEquals(1, metric.getStatusCounts().get("2"));
        assertNull(metric.getPoints().get(0).getPreviousValue());
        assertNull(metric.getPoints().get(0).getChangeValue());
        assertEquals("2", metric.getPoints().get(1).getChangeValue());
        String json = new ObjectMapper().writeValueAsString(result);
        assertTrue(json.contains("\"value\":\"9007199254740993.12\""));
        assertFalse(json.contains("resultDetail"));
        assertFalse(json.contains("stepParams"));
    }

    @Test
    void separatesDifferentUnitsAndComparisonWindows()
    {
        AutoInspectionMetricSampleVo percent = sample(1, "50", "%%", "1");
        AutoInspectionMetricSampleVo time = sample(2, "180", "ms", "1");
        AutoInspectionMetricSampleVo daily = sample(3, "55", "%", "1");
        daily.setComparisonScope("DAY");
        AutoInspectionMetricSampleVo unknown = sample(4, null, null, "2");
        when(mapper.selectSamples(anyLong(), any(), any(), anyInt())).thenReturn(List.of(unknown, daily, time, percent));
        var metrics = service.selectMetrics(null).getMetrics();
        assertEquals(4, metrics.size());
        assertEquals(4, metrics.stream().map(AutoInspectionMetricsVo.MetricSeries::getMetricKey).distinct().count());
        assertTrue(metrics.stream().anyMatch(metric -> metric.getUnit().isEmpty() && metric.getNumericCount() == 0));
        assertTrue(metrics.stream().anyMatch(metric -> "%".equals(metric.getUnit()) && "DAY".equals(metric.getComparisonScope())));
    }

    @Test
    void limitsSamplesAndKeepsTheMostRecentBoundedWindow()
    {
        List<AutoInspectionMetricSampleVo> rows = new ArrayList<>();
        for (int id = 5001; id >= 1; id--) rows.add(sample(id, String.valueOf(id), "条", "1"));
        when(mapper.selectSamples(anyLong(), any(), any(), anyInt())).thenReturn(rows);
        AutoInspectionMetricsVo result = service.selectMetrics(null);
        assertTrue(result.isTruncated());
        assertEquals(5000, result.getSampleCount());
        assertEquals("2", result.getMetrics().get(0).getMinimum());
        assertEquals("5001", result.getMetrics().get(0).getLatest().getValue());
        assertEquals(5001, rows.size());
        verify(mapper).selectSamples(eq(7L), any(), any(), eq(5001));
    }

    @Test
    void queryIsReadOnlyAndDoesNotStartInspectionExecution() throws Exception
    {
        var method = SupportAutoInspectionMetricsServiceImpl.class.getMethod("selectMetrics", AutoInspectionMetricQuery.class);
        assertTrue(method.getAnnotation(Transactional.class).readOnly());
    }

    private AutoInspectionMetricSampleVo sample(long id, String value, String unit, String status)
    {
        AutoInspectionMetricSampleVo sample = new AutoInspectionMetricSampleVo();
        sample.setResultId(id);
        sample.setRecordId(id);
        sample.setStepId(11L);
        sample.setStepName("数据库数量");
        sample.setTargetId(21L);
        sample.setTargetName("过车记录");
        sample.setToolCode("DATABASE_QUERY");
        sample.setActualValue(value == null ? null : new BigDecimal(value));
        sample.setActualUnit(unit);
        sample.setResultStatus(status);
        sample.setComparisonScope("CONTINUOUS");
        sample.setSampleTime(Date.from(clock.instant().minusSeconds(6000 - id)));
        return sample;
    }
}
