package com.hm.manage.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;
import com.hm.common.exception.ServiceException;

class AutoInspectionToolContractTest
{
    @Test
    void activityToolsResolveToQueueSpecificTargets()
    {
        assertEquals("KAFKA", AutoInspectionToolContract.requireTargetType("KAFKA_TOPIC_ACTIVITY"));
        assertEquals("KAFKA", AutoInspectionToolContract.requireTargetType("KAFKA_CONSUMER_PROGRESS"));
        assertEquals("MQTT", AutoInspectionToolContract.requireTargetType("MQTT_TOPIC_ACTIVITY"));
    }

    @Test
    void serverToolsRemainExplicitInsteadOfBeingTheFallback()
    {
        assertEquals("SERVER", AutoInspectionToolContract.requireTargetType("SERVER_FILE_COUNT"));
        assertEquals("SERVER", AutoInspectionToolContract.requireTargetType("SERVER_DISK"));
        assertEquals("BIG_DATA_SERVER", AutoInspectionToolContract.requireTargetType("BIG_DATA_SERVER_DISK"));
    }

    @Test
    void unknownToolIsRejected()
    {
        assertEquals("", AutoInspectionToolContract.findTargetType("UNKNOWN_TOOL"));
        assertThrows(ServiceException.class, () -> AutoInspectionToolContract.requireTargetType("UNKNOWN_TOOL"));
    }
}
