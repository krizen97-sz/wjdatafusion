package com.hm.manage.service.governance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hm.common.exception.ServiceException;
import com.hm.manage.service.governance.DataGovernanceModels.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** NiFi process groups are the authoritative project and flow definitions. */
@Service
public class DataGovernanceEngine
{
    static final String PROJECT = "RYNEW_GOV_PROJECT\n";
    static final String FLOW = "RYNEW_GOV_FLOW:";
    static final String TEST = "RYNEW_GOV_TEST:";
    static final String CAPTURE = "RYNEW_GOV_CAPTURE";
    static final String STANDARD = "org.apache.nifi.processors.standard.";
    static final String UPDATE = "org.apache.nifi.processors.attributes.UpdateAttribute";
    static final String JOLT = "org.apache.nifi.processors.jolt.JoltTransformJSON";
    static final String SAMPLE = "sample-safe-v1";
    static final String DELIMITED = "delimited-safe-v1";
    static final String WRITER = "com.hm.governance.nifi.DelimitedTextWriter";
    static final String LOOKUP = "com.hm.governance.nifi.JsonLookupSnapshot";
    static final String LOOKUP_TEMPLATE = "lookup-safe-v1";
    // NiFi processor cards are about 350 px wide; leave room for connection labels.
    private static final double TEMPLATE_COLUMN = 520;
    final DataGovernanceNifiClient client;
    final DataGovernanceProperties properties;
    final ObjectMapper mapper = new ObjectMapper();

    public DataGovernanceEngine(DataGovernanceNifiClient client, DataGovernanceProperties properties)
    { this.client = client; this.properties = properties; }

    public Map<String, Object> overview()
    {
        boolean configured = client.configured();
        Map<String, Object> engine = map("configured", configured, "reachable", false, "version", "",
            "message", configured ? "独立引擎尚未连接" : "请由管理员配置独立 NiFi 与治理根目录",
            "designerPath", properties.getNifi().getDesignerPath());
        Map<String, Object> counts = map("projects", 0, "flows", 0);
        if (configured)
        {
            try
            {
                JsonNode about = client.json("GET", "/flow/about", null);
                List<Project> projects = projects();
                int flowCount = 0;
                for (Project project : projects) flowCount += flows(project.id()).size();
                engine.put("reachable", true);
                engine.put("version", about.path("about").path("version").asText(""));
                engine.put("message", "NiFi 已连接；测试报告使用单实例文件存储");
                counts.put("projects", projects.size()); counts.put("flows", flowCount);
            }
            catch (ServiceException e) { engine.put("message", e.getMessage()); }
        }
        return map("engine", engine, "counts", counts);
    }

    public List<Project> projects()
    {
        List<Project> result = new ArrayList<>();
        for (JsonNode entity : groups(root()))
        {
            JsonNode c = entity.path("component"); String comments = c.path("comments").asText("");
            if (comments.startsWith(PROJECT)) result.add(new Project(c.path("id").asText(),
                c.path("name").asText(), comments.substring(PROJECT.length()), c.path("id").asText()));
        }
        return result;
    }

    public Project createProject(CreateProject request)
    {
        String name = text(request.name(), 80, "项目名称");
        String description = request.description() == null ? "" : request.description().trim();
        if (description.length() > 500) throw new ServiceException("项目说明最多 500 字");
        JsonNode c = createGroup(root(), name, PROJECT + description).path("component");
        return new Project(c.path("id").asText(), name, description, c.path("id").asText());
    }

    public List<Flow> flows(String projectId)
    {
        project(projectId); List<Flow> result = new ArrayList<>();
        for (JsonNode entity : groups(projectId))
        {
            JsonNode c = entity.path("component");
            if (c.path("comments").asText("").startsWith(FLOW)) result.add(flowDto(c));
        }
        return result;
    }

    public Flow flow(String id)
    {
        JsonNode component = client.json("GET", "/process-groups/" + id(id), null).path("component");
        project(component.path("parentGroupId").asText());
        if (!component.path("comments").asText("").startsWith(FLOW)) throw new ServiceException("流程不属于数据治理模块");
        return flowDto(component);
    }

    public Flow createFlow(CreateFlow request)
    {
        project(request.projectId());
        String template = request.templateId() == null || request.templateId().isBlank() ? "blank" : request.templateId();
        if (!SAMPLE.equals(template) && !DELIMITED.equals(template) && !LOOKUP_TEMPLATE.equals(template) && !template.equals("blank")) throw new ServiceException("该模板尚未实现，不能创建为可执行流程");
        if (DELIMITED.equals(template) && !supports(WRITER)) throw new ServiceException("独立 NiFi 尚未安装协议文本组件");
        if (LOOKUP_TEMPLATE.equals(template) && !supports(LOOKUP)) throw new ServiceException("独立 NiFi 尚未安装快照查表组件");
        String name = text(request.name(), 80, "流程名称");
        JsonNode group = createGroup(request.projectId(), name, FLOW + template);
        String groupId = group.path("component").path("id").asText();
        if (template.equals("blank")) return flow(groupId);
        try
        {
            String source = createProcessor(groupId, "样本输入", STANDARD + "GenerateFlowFile", "", map(
                "Custom Text", "{\"message\":\"sample\"}", "Batch Size", "1", "Data Format", "Text", "Unique FlowFiles", "false"), List.of(), 0).path("component").path("id").asText();
            if (LOOKUP_TEMPLATE.equals(template))
            {
                String lookup = createProcessor(groupId, "批次快照查表", LOOKUP, "", map(
                    "Lookup Rows", "[{\"camera_code\":\"CAM-001\",\"platform_code\":\"DEMO\",\"external_code\":\"EXT-001\",\"active\":\"Y\"}]",
                    "Match Fields", "[{\"input\":\"/camera\",\"lookup\":\"camera_code\",\"type\":\"STRING\",\"operator\":\"EQ\"},{\"input\":\"/platform\",\"lookup\":\"platform_code\",\"type\":\"STRING\",\"operator\":\"EQ\"},{\"lookup\":\"active\",\"type\":\"STRING\",\"operator\":\"IS_NOT_NULL\"}]",
                    "Return Fields", "[{\"lookup\":\"external_code\",\"output\":\"external_camera\",\"default\":\"0\"}]",
                    "Missing Match", "KEEP", "Multiple Matches", "FAIL"), List.of(), TEMPLATE_COLUMN).path("component").path("id").asText();
                String capture = createProcessor(groupId, "查看测试结果", UPDATE, CAPTURE, map(), List.of("success"), 2 * TEMPLATE_COLUMN).path("component").path("id").asText();
                connect(groupId, source, lookup, List.of("success"));
                connect(groupId, lookup, capture, List.of("success"));
                connect(groupId, lookup, capture, List.of("empty"), templateBends(1, 2, -80));
                connect(groupId, lookup, capture, List.of("failure"), templateBends(1, 2, 400));
                return flow(groupId);
            }
            if (DELIMITED.equals(template))
            {
                String writer = createProcessor(groupId, "协议文本输出", WRITER, "", map("Field Order", "message,picture", "Delimiter Hex", "7C 1F",
                    "Include Header", "true", "Split Limit", "75", "Count Basis", "KETTLE_HEADER_INCLUSIVE", "Filename Prefix", "sample"), List.of(), TEMPLATE_COLUMN)
                    .path("component").path("id").asText();
                String capture = createProcessor(groupId, "查看测试结果", UPDATE, CAPTURE, map(), List.of("success"), 2 * TEMPLATE_COLUMN).path("component").path("id").asText();
                connect(groupId, source, writer, List.of("success"));
                connect(groupId, writer, capture, List.of("success"));
                connect(groupId, writer, capture, List.of("empty"), templateBends(1, 2, -80));
                connect(groupId, writer, capture, List.of("failure"), templateBends(1, 2, 400));
                return flow(groupId);
            }
            String extract = createProcessor(groupId, "提取字段", STANDARD + "EvaluateJsonPath", "", map(
                "Destination", "flowfile-attribute", "Return Type", "scalar", "sample.value", "$.message"), List.of(), TEMPLATE_COLUMN).path("component").path("id").asText();
            String route = createProcessor(groupId, "条件路由", STANDARD + "RouteOnAttribute", "", map(
                "Routing Strategy", "Route to Property name", "accepted", "${sample.value:isEmpty():not()}"), List.of(), 2 * TEMPLATE_COLUMN).path("component").path("id").asText();
            String capture = createProcessor(groupId, "查看测试结果", UPDATE, CAPTURE, map(), List.of("success"), 3 * TEMPLATE_COLUMN).path("component").path("id").asText();
            connect(groupId, source, extract, List.of("success"));
            connect(groupId, extract, route, List.of("matched"));
            connect(groupId, extract, capture, List.of("unmatched"), templateBends(1, 3, -80));
            connect(groupId, extract, capture, List.of("failure"), templateBends(1, 3, -220));
            connect(groupId, route, capture, List.of("accepted"));
            connect(groupId, route, capture, List.of("unmatched"), templateBends(2, 3, 400));
            return flow(groupId);
        }
        catch (RuntimeException e)
        {
            // A partially created definition remains stopped and visible for repair; never report success.
            throw new ServiceException("流程已建立但模板初始化失败，请检查引擎组件；流程标识 " + groupId);
        }
    }

    private Flow flowDto(JsonNode c)
    {
        String id = c.path("id").asText();
        return new Flow(id, c.path("parentGroupId").asText(), c.path("name").asText(), "NiFi 持久流程定义", id,
            c.path("comments").asText().substring(FLOW.length()),
            properties.getNifi().getDesignerPath().replaceAll("/+$", "") + "/#/process-groups/" + id);
    }

    private Project project(String id)
    {
        id(id);
        return projects().stream().filter(p -> p.id().equals(id)).findFirst()
            .orElseThrow(() -> new ServiceException("项目不属于配置的治理根目录"));
    }

    private Iterable<JsonNode> groups(String parent)
    { return client.json("GET", "/process-groups/" + id(parent) + "/process-groups", null).path("processGroups"); }
    String root()
    {
        if (!client.configured()) throw new ServiceException("请先配置独立 NiFi 和治理根目录");
        return id(properties.getNifi().getRootGroupId());
    }
    JsonNode createGroup(String parent, String name, String comments)
    { return client.json("POST", "/process-groups/" + id(parent) + "/process-groups",
        map("revision", map("version", 0), "component", map("name", name, "comments", comments, "position", map("x", 0, "y", 0)))); }
    JsonNode groupContents(String group)
    { return client.json("GET", "/flow/process-groups/" + id(group), null).path("processGroupFlow").path("flow"); }

    JsonNode createProcessor(String group, String name, String type, String comments, Map<String, Object> props,
                             List<String> terminated, double x)
    { return createProcessor(group, name, type, comments, props, terminated, x, null); }

    JsonNode createProcessor(String group, String name, String type, String comments, Map<String, Object> props,
                             List<String> terminated, double x, JsonNode requestedBundle)
    {
        JsonNode bundle = null;
        for (JsonNode processor : client.json("GET", "/flow/processor-types", null).path("processorTypes"))
            if (processor.path("type").asText().equals(type) && (requestedBundle == null || requestedBundle.equals(processor.path("bundle"))))
            { bundle = processor.path("bundle"); break; }
        if (bundle == null) throw new ServiceException("NiFi 未安装快照所需组件版本 " + type.substring(type.lastIndexOf('.') + 1));
        return client.json("POST", "/process-groups/" + id(group) + "/processors", map("revision", map("version", 0),
            "component", map("name", name, "type", type, "bundle", bundle, "comments", comments,
                "position", map("x", x, "y", 100), "config", map("comments", comments, "properties", props, "schedulingStrategy", "TIMER_DRIVEN",
                    "schedulingPeriod", "0 sec", "concurrentlySchedulableTaskCount", 1, "autoTerminatedRelationships", terminated))));
    }
    public boolean supports(String type)
    {
        if (!client.configured()) return false;
        try
        {
            for (JsonNode processor : client.json("GET", "/flow/processor-types", null).path("processorTypes"))
                if (type.equals(processor.path("type").asText())) return true;
        }
        catch (ServiceException ignored) { }
        return false;
    }
    JsonNode connect(String group, String source, String target, List<String> relations)
    { return connect(group, source, target, relations, List.of()); }

    private static List<Map<String, Object>> templateBends(int sourceColumn, int targetColumn, double y)
    { return List.of(map("x", sourceColumn * TEMPLATE_COLUMN + 175, "y", y),
        map("x", targetColumn * TEMPLATE_COLUMN + 175, "y", y)); }

    private JsonNode connect(String group, String source, String target, List<String> relations, List<Map<String, Object>> bends)
    {
        return client.json("POST", "/process-groups/" + id(group) + "/connections", map("revision", map("version", 0),
            "component", map("source", map("id", source, "groupId", group, "type", "PROCESSOR"),
                "destination", map("id", target, "groupId", group, "type", "PROCESSOR"),
                "selectedRelationships", relations, "bends", bends, "backPressureObjectThreshold", 200, "backPressureDataSizeThreshold", "2 MB")));
    }
    static String id(String value)
    {
        try { if (!UUID.fromString(value).toString().equals(value)) throw new IllegalArgumentException(); return value; }
        catch (Exception e) { throw new ServiceException("引擎资源标识无效"); }
    }
    static String processorComment(JsonNode processor)
    { return processor.path("config").has("comments") ? processor.path("config").path("comments").asText("") : processor.path("comments").asText(""); }
    static String text(String value, int max, String name)
    { if (value == null || value.isBlank() || value.length() > max) throw new ServiceException(name + "不能为空且最多 " + max + " 字"); return value.trim(); }
    static Map<String, Object> map(Object... pairs)
    {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) map.put((String) pairs[i], pairs[i + 1]);
        return map;
    }
}
