package com.hm.manage.service.governance;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DataGovernanceModels
{
    private DataGovernanceModels() { }
    public record CreateProject(String name, String description) { }
    public record CreateFlow(String projectId, String name, String templateId) { }
    public record TestInput(String inputJson, Map<String, Object> parameters) { }
    public record Project(String id, String name, String description, String engineId) { }
    public record Flow(String id, String projectId, String name, String description, String engineId,
                       String templateId, String designerPath) { }
    public record CatalogItem(String id, String name, String category, String availability, String description,
                              List<String> inputKinds, List<String> outputKinds) { }
    public record Template(String id, String name, String description, String availability,
                           Map<String, Object> parametersSchema) { }
    public record Samples(List<String> input, List<String> output, List<Map<String, String>> attributes)
    {
        public Samples(List<String> input, List<String> output) { this(input, output, List.of()); }
    }
    public record StepResult(String id, String name, String type, String status, long inputCount,
                             long outputCount, List<String> messages, Samples samples) { }

    /** Public result contains only observed engine evidence, never transport credentials. */
    public static class TestRun
    {
        public String id;
        public String status;
        public String flowId;
        public String projectId;
        public String createdAt;
        public String updatedAt;
        public List<StepResult> steps = new ArrayList<>();
        public List<String> output = new ArrayList<>();
        public String error;
        public boolean cleanupConfirmed;
        public String engineTestGroupId;
    }

    /** Private persistence envelope; controller returns only run. */
    public static class StoredRun
    {
        public long ownerId;
        public String inputJson;
        public Map<String, Object> parameters = new LinkedHashMap<>();
        public TestRun run;
    }
}
