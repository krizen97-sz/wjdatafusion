package com.hm.manage.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class AutoInspectionActivityStateMachineTest
{
    private static final AutoInspectionActivityStateMachine.Rule RULE =
            new AutoInspectionActivityStateMachine.Rule(3, 5, 2);

    @Test
    void topicActivityMovesFromBaselineToWarningAndAbnormal()
    {
        LocalDateTime start = LocalDateTime.of(2026, 8, 24, 10, 0);
        AutoInspectionActivityStateMachine.Evaluation baseline = AutoInspectionActivityStateMachine.evaluate(
                AutoInspectionActivityStateMachine.Mode.TOPIC_ACTIVITY, RULE, null,
                observation(100, null, start));
        assertEquals(AutoInspectionActivityStateMachine.STATUS_BASELINE, baseline.status);

        AutoInspectionActivityStateMachine.State state = state(baseline);
        AutoInspectionActivityStateMachine.Evaluation warning = AutoInspectionActivityStateMachine.evaluate(
                AutoInspectionActivityStateMachine.Mode.TOPIC_ACTIVITY, RULE, state,
                observation(100, null, start.plusMinutes(3)));
        assertEquals(AutoInspectionActivityStateMachine.STATUS_WARNING, warning.status);

        AutoInspectionActivityStateMachine.Evaluation abnormal = AutoInspectionActivityStateMachine.evaluate(
                AutoInspectionActivityStateMachine.Mode.TOPIC_ACTIVITY, RULE, state(warning),
                observation(100, null, start.plusMinutes(5)));
        assertEquals(AutoInspectionActivityStateMachine.STATUS_ABNORMAL, abnormal.status);
    }

    @Test
    void recoveredActivityRequiresConfiguredConfirmationCount()
    {
        LocalDateTime start = LocalDateTime.of(2026, 8, 24, 10, 0);
        AutoInspectionActivityStateMachine.State abnormal = new AutoInspectionActivityStateMachine.State(
                BigDecimal.valueOf(100), null, start.plusMinutes(5), start, 1, 0,
                AutoInspectionActivityStateMachine.STATUS_ABNORMAL);

        AutoInspectionActivityStateMachine.Evaluation confirming = AutoInspectionActivityStateMachine.evaluate(
                AutoInspectionActivityStateMachine.Mode.TOPIC_ACTIVITY, RULE, abnormal,
                observation(101, null, start.plusMinutes(6)));
        assertEquals(AutoInspectionActivityStateMachine.STATUS_WARNING, confirming.status);

        AutoInspectionActivityStateMachine.Evaluation recovered = AutoInspectionActivityStateMachine.evaluate(
                AutoInspectionActivityStateMachine.Mode.TOPIC_ACTIVITY, RULE, state(confirming),
                observation(102, null, start.plusMinutes(7)));
        assertEquals(AutoInspectionActivityStateMachine.STATUS_NORMAL, recovered.status);
    }

    @Test
    void consumerDoesNotReportStallWhenUpstreamIsIdle()
    {
        LocalDateTime start = LocalDateTime.of(2026, 8, 24, 10, 0);
        AutoInspectionActivityStateMachine.State previous = new AutoInspectionActivityStateMachine.State(
                BigDecimal.valueOf(80), BigDecimal.valueOf(100), start, start, 0, 0,
                AutoInspectionActivityStateMachine.STATUS_NORMAL);
        AutoInspectionActivityStateMachine.Evaluation evaluation = AutoInspectionActivityStateMachine.evaluate(
                AutoInspectionActivityStateMachine.Mode.CONSUMER_PROGRESS, RULE, previous,
                observation(80, 100L, start.plusMinutes(5)));
        assertEquals(AutoInspectionActivityStateMachine.STATUS_BASELINE, evaluation.status);
    }

    private AutoInspectionActivityStateMachine.Observation observation(long primary, Long secondary,
                                                                        LocalDateTime time)
    {
        return new AutoInspectionActivityStateMachine.Observation(BigDecimal.valueOf(primary),
                secondary == null ? null : BigDecimal.valueOf(secondary), time, null);
    }

    private AutoInspectionActivityStateMachine.State state(AutoInspectionActivityStateMachine.Evaluation value)
    {
        return new AutoInspectionActivityStateMachine.State(value.primaryValue, value.secondaryValue,
                value.observedAt, value.lastActivityAt, value.abnormalStreak, value.normalStreak, value.status);
    }
}
