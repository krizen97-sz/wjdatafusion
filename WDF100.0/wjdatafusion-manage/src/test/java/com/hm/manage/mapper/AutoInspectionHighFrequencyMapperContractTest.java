package com.hm.manage.mapper;

import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class AutoInspectionHighFrequencyMapperContractTest
{
    @Test
    void mapperKeepsPlanModeSamplesStateAndDailyHealthContracts() throws Exception
    {
        try (InputStream input = getClass().getClassLoader()
                .getResourceAsStream("mapper/support/SupportAutoInspectionMapper.xml"))
        {
            String source = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(source.contains("p.plan_mode as planMode"));
            assertTrue(source.contains("p.health_config as healthConfig"));
            assertTrue(source.contains("run_mode as runMode"));
            assertTrue(source.contains("schedule_slot_time as scheduleSlotTime"));
            assertTrue(source.contains("id=\"upsertProbeState\""));
            assertTrue(source.contains("id=\"upsertDailyHealth\""));
            assertTrue(source.contains("id=\"selectDailyHealthList\""));
            assertTrue(source.contains("run_mode = 'FREQUENT'"));
            assertTrue(source.contains("tr.evaluation_mode as evaluationMode"));
            assertTrue(source.contains("tr.previous_value as previousValue"));
            assertTrue(source.contains("tr.change_value as changeValue"));
            assertTrue(source.contains("tr.baseline_flag as baselineFlag"));
        }
    }
}
