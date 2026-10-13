package com.hm.manage.service.governance.hdi;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.springframework.stereotype.Component;
import org.springframework.asm.ClassReader;
import org.springframework.asm.AnnotationVisitor;
import org.springframework.asm.FieldVisitor;
import org.springframework.asm.ClassVisitor;
import org.springframework.asm.MethodVisitor;
import org.springframework.asm.Opcodes;

/** Platform registry backed by the selected original plugin definitions, never fabricated defaults. */
@Component
public final class HdiCoreCatalog {
    private static final ObjectMapper JSON = new ObjectMapper().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    // These are new platform registry identities, not IDs claimed to exist in a vendor database.
    private static final List<String> SUPPORTED = List.of("dataGeneratorInput", "fieldMapping", "fileOutput");
    private static final Map<String, String> TYPES = Map.of("dataGeneratorInput", "input", "fieldMapping", "preproc", "fileOutput", "output");
    private static final Map<String, String> ORIGINAL_ICONS = Map.of("input", "/static/img/defaultinput.e71f57a7.svg",
        "preproc", "/static/img/defaultpreproc.26462e9c.svg", "output", "/static/img/defaultoutput.bfb8531e.svg");
    private static final String DATA_JAR = "task-service/dEtlv2.jar";
    private static final String SQL_ROOT = "db/migration/V200/";
    private static final List<String> TYPE_SQL = List.of("V1.0.1__INIT_DATA.sql", "V1.0.10__ETL2_0511.sql",
        "V1.0.13__ETL2_2.1.0_b3.sql", "V1.0.18__ETL2_2.1.1_b1.sql", "V1.0.19__ETL2_2.1.1_b1_0812.sql",
        "V1.0.20__ETL2_2.1.1_b2.sql", "V1.0.23__ETL2_2.1.1_r2.sql", "V1.0.26__ETL2_2.1.2_b3.sql");
    private static final Pattern INSERT = Pattern.compile("(?is)insert\\s+into\\s+(?:public\\.)?sys_code\\s*\\(([^)]*)\\)\\s*values\\s*(.*?);");
    private static final Pattern DESCRIPTION = Pattern.compile("(?is)update\\s+sys_code\\s+set\\s+description\\s*=\\s*'([^']*)'\\s+where\\s+pid\\s*=\\s*200\\s+and\\s+(?:lower\\(name\\)|name)\\s+in\\s*\\(([^)]*)\\)");
    private final HdiCoreAssets assets;

    public HdiCoreCatalog(HdiCoreProperties properties, HdiCoreAssets assets) { this.assets = Objects.requireNonNull(assets); }
    private record Plugin(long id, String folder, JsonNode definition, Properties properties) { }

    public List<Map<String, Object>> getAdapters() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Plugin plugin : plugins()) {
            JsonNode def = plugin.definition; Properties props = plugin.properties;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", plugin.id); item.put("groupAdapterId", plugin.id);
            item.put("adapterName", text(def, "adapterName")); item.put("adapterDisplayName", text(def, "adapterDisplayName"));
            item.put("adapterType", text(def, "adapterType")); item.put("adapterVersion", text(def, "version"));
            String icon = ORIGINAL_ICONS.get(text(def, "adapterType"));
            HdiCoreAssets.Asset image = assets.read("/detl" + icon);
            if (image == null || !"image/svg+xml".equals(image.contentType())) throw unavailable();
            item.put("iconStr", icon); item.put("iconUrI", icon);
            // Original getOrderedGroupAdapters uses PROCESS_ADAPTER_TYPE.getOrDefault(adapterName, "other").
            // The bundled seed has no fieldMapping override; a missing processType hides it from the original panel.
            if ("fieldMapping".equals(text(def, "adapterName"))) item.put("processType", "other");
            item.put("adapterClassName", text(def, "className")); item.put("languageType", 1); // Original LanguageType.JAVA=1.
            item.put("languageTypeDesc", "java"); item.put("dbTypeId", "FTP".equals(scalar(def.get("adapterDatabaseType"))) ? 18 : null);
            item.put("addComment", scalar(def.get("adapterComment")));
            item.put("packageName", plugin.folder.substring(plugin.folder.lastIndexOf('/') + 1));
            for (String name : List.of("buildVersion", "pluginResourceType", "pluginFieldSyncMode", "referenceConfigAdapter", "adapterSubType"))
                if (props.containsKey(name)) item.put(name, props.getProperty(name));
            if (def.has("adapterUIParams")) item.put("adapterUIParams", JSON.convertValue(def.get("adapterUIParams"), Map.class));
            result.add(Collections.unmodifiableMap(item));
        }
        return List.copyOf(result);
    }

    /** Original getAdapterUiInfo exposes parsed adapterUIParams; an absent optional section is an empty map. */
    public Map<String, Object> getAdapterUiInfo(String adapterName) {
        if (adapterName == null || !SUPPORTED.contains(adapterName)) throw badQuery();
        Plugin plugin = plugins().stream().filter(p -> adapterName.equals(text(p.definition, "adapterName"))).findFirst().orElseThrow(HdiCoreCatalog::unavailable);
        JsonNode info = plugin.definition.get("adapterUIParams");
        if (info == null || info.isNull()) return Map.of();
        if (!info.isObject()) throw unavailable();
        @SuppressWarnings("unchecked") Map<String, Object> result = JSON.convertValue(info, Map.class);
        return Collections.unmodifiableMap(result);
    }

    public List<Map<String, Object>> listParams(Integer typeId, String adapterName, String stepTypeName) {
        return listParams(typeId, adapterName, stepTypeName, Set.of());
    }
    public List<Map<String, Object>> listParams(Integer typeId, String adapterName, String stepTypeName, Set<String> selectedNames) {
        if (Integer.valueOf(2).equals(typeId)) {
            if ((adapterName != null && !adapterName.isBlank()) || (stepTypeName != null && !stepTypeName.isBlank())) throw badQuery();
            return params(json(assets.readOriginal("instance/conf/task_config.json")).get("configParam"), 2, 0);
        }
        if ((!Integer.valueOf(6).equals(typeId) && !Integer.valueOf(9).equals(typeId)) || adapterName == null || !SUPPORTED.contains(adapterName)) throw badQuery();
        Plugin plugin = plugins().stream().filter(p -> adapterName.equals(text(p.definition, "adapterName"))).findFirst().orElseThrow(HdiCoreCatalog::unavailable);
        if (stepTypeName != null && !stepTypeName.isBlank() && !stepTypeName.equals(TYPES.get(adapterName))) throw badQuery();
        Set<String> hidden = "fileOutput".equals(adapterName) ? fileOutputFormManagedParams() : Set.of();
        return params(plugin.definition.get("adapterParams"), typeId, plugin.id).stream()
            .filter(row -> selectedNames.contains(row.get("paramName")) || !hidden.contains(row.get("paramName"))).toList();
    }

    /** Match AbstractAdapterService.getIgnoreDisplayParamName: annotated form-owned parameters stay out of new advanced defaults. */
    private Set<String> fileOutputFormManagedParams() {
        String annotation = "com/hikvision/idatafusion/detl/common/annotation/AdapterParam";
        Boolean[] displayDefault = {null};
        try {
            new ClassReader(metadataClass(DATA_JAR, annotation)).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                    if (!name.equals("display") || !desc.equals("()Z")) return null;
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override public AnnotationVisitor visitAnnotationDefault() {
                            return new AnnotationVisitor(Opcodes.ASM9) { @Override public void visit(String name, Object value) { if (value instanceof Boolean) displayDefault[0] = (Boolean)value; } };
                        }
                    };
                }
            }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            if (displayDefault[0] == null) throw unavailable();
            Set<String> names = new java.util.LinkedHashSet<>();
            ClassReader config = new ClassReader(metadataClass(DATA_JAR, "com/hikvision/idatafusion/detl/dto/task/node/FileOutputNodeConfigVo"));
            if (!"java/lang/Object".equals(config.getSuperName())) throw unavailable();
            config.accept(new ClassVisitor(Opcodes.ASM9) {
                @Override public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
                    return new FieldVisitor(Opcodes.ASM9) {
                        @Override public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
                            if (!desc.equals("L" + annotation + ";")) return null;
                            return new AnnotationVisitor(Opcodes.ASM9) {
                                String param; boolean display = displayDefault[0];
                                @Override public void visit(String key, Object data) { if (key.equals("value") && data instanceof String) param = (String)data; else if (key.equals("display") && data instanceof Boolean) display = (Boolean)data; }
                                @Override public void visitEnd() { if (!display && param != null && !param.isEmpty()) names.add(param); }
                            };
                        }
                    };
                }
            }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return Set.copyOf(names);
        } catch (RuntimeException invalid) { throw unavailable(); }
    }

    /** Read the vendor's literal type dictionary without loading its classes or opening a data source. */
    public List<Integer> getJdbcDbTypes() {
        String owner = "com/hikvision/idatafusion/detl/common/DataSourceConstants";
        byte[] bytes = metadataClass("task-service/lib/dEtlv2-core-3.3.0.jar", owner);
        Map<String, Integer> constants = new LinkedHashMap<>(); List<Integer> result = new ArrayList<>();
        boolean[] valid = {true}, found = {false};
        try {
            new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    if (!name.equals("<clinit>")) return null;
                    return new MethodVisitor(Opcodes.ASM9) {
                        Integer literal; List<Integer> array; boolean constructed;
                        @Override public void visitInsn(int opcode) {
                            literal = opcode >= Opcodes.ICONST_M1 && opcode <= Opcodes.ICONST_5 ? opcode - Opcodes.ICONST_0 : null;
                        }
                        @Override public void visitIntInsn(int opcode, int operand) {
                            literal = opcode == Opcodes.BIPUSH || opcode == Opcodes.SIPUSH ? operand : null;
                        }
                        @Override public void visitLdcInsn(Object value) { literal = value instanceof Integer ? (Integer)value : null; }
                        @Override public void visitTypeInsn(int opcode, String type) {
                            if (opcode == Opcodes.ANEWARRAY) { array = type.equals("java/lang/Integer") ? new ArrayList<>() : null; constructed = false; }
                            literal = null;
                        }
                        @Override public void visitFieldInsn(int opcode, String type, String field, String desc) {
                            if (!owner.equals(type)) { literal = null; return; }
                            if (opcode == Opcodes.PUTSTATIC && desc.equals("Ljava/lang/Integer;")) {
                                if (literal == null) valid[0] = false; else constants.put(field, literal);
                            }
                            if (opcode == Opcodes.GETSTATIC && array != null && desc.equals("Ljava/lang/Integer;")) {
                                Integer value = constants.get(field); if (value == null) valid[0] = false; else array.add(value);
                            }
                            if (opcode == Opcodes.PUTSTATIC && field.equals("JDBC_DATASOURCE_TYPE_LIST")) {
                                if (found[0] || !constructed || array == null) valid[0] = false;
                                else { found[0] = true; result.addAll(array); }
                            }
                            literal = null;
                        }
                        @Override public void visitMethodInsn(int opcode, String type, String method, String desc, boolean isInterface) {
                            if (opcode == Opcodes.INVOKESTATIC && type.equals("java/lang/Integer") && method.equals("valueOf") && desc.equals("(I)Ljava/lang/Integer;")) return;
                            constructed = opcode == Opcodes.INVOKESTATIC && type.equals("com/google/common/collect/Sets") && method.equals("newHashSet")
                                && desc.equals("([Ljava/lang/Object;)Ljava/util/HashSet;") && array != null;
                            literal = null;
                        }
                        @Override public void visitJumpInsn(int opcode, org.springframework.asm.Label label) { valid[0] = false; }
                        @Override public void visitTableSwitchInsn(int min, int max, org.springframework.asm.Label dflt, org.springframework.asm.Label... labels) { valid[0] = false; }
                        @Override public void visitLookupSwitchInsn(org.springframework.asm.Label dflt, int[] keys, org.springframework.asm.Label[] labels) { valid[0] = false; }
                    };
                }
            }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        } catch (RuntimeException invalid) { throw unavailable(); }
        if (!valid[0] || !found[0] || result.isEmpty() || result.size() > 256 || result.stream().anyMatch(value -> value <= 0)
            || result.stream().distinct().count() != result.size()) throw unavailable();
        return result.stream().sorted().toList();
    }

    /** Original non-database/file-field fallback from DataTypeUtils.DATA_TYPE_MAP keys. */
    public List<Map<String, Object>> getFileColumnTypes() {
        String owner = "com/hikvision/idatafusion/detl/utils/datasource/DataTypeUtils";
        Set<String> names = new java.util.TreeSet<>(); boolean[] valid = {true};
        try {
            new ClassReader(metadataClass(DATA_JAR, owner)).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    if (!name.equals("<clinit>")) return null;
                    return new MethodVisitor(Opcodes.ASM9) {
                        List<String> values;
                        @Override public void visitFieldInsn(int opcode, String type, String field, String desc) {
                            values = opcode == Opcodes.GETSTATIC && owner.equals(type) && field.equals("DATA_TYPE_MAP") ? new ArrayList<>() : null;
                        }
                        @Override public void visitLdcInsn(Object value) { if (values != null) { if (value instanceof String) values.add((String)value); else valid[0] = false; } }
                        @Override public void visitMethodInsn(int opcode, String type, String method, String desc, boolean isInterface) {
                            if (values != null) {
                                if (opcode != Opcodes.INVOKEINTERFACE || !type.equals("java/util/Map") || !method.equals("put") || values.size() != 2
                                    || !values.get(0).matches("[a-z][a-z0-9_]*") || !names.add(values.get(0))) valid[0] = false;
                                values = null;
                            }
                        }
                        @Override public void visitJumpInsn(int opcode, org.springframework.asm.Label label) { valid[0] = false; }
                    };
                }
            }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        } catch (RuntimeException invalid) { throw unavailable(); }
        if (!valid[0] || names.isEmpty() || names.size() > 256) throw unavailable();
        return names.stream().map(name -> { Map<String, Object> row = new LinkedHashMap<>(); row.put("actualFieldTypeId", null); row.put("fieldType", name); row.put("columnType", name); return Collections.unmodifiableMap(row); }).toList();
    }
    private byte[] metadataClass(String jar, String owner) {
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(assets.readOriginal(jar)))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) if (entry.getName().equals(owner + ".class")) {
                byte[] bytes = zip.readNBytes(128 * 1024 + 1);
                if (bytes.length > 128 * 1024 || !new ClassReader(bytes).getClassName().equals(owner)) throw unavailable();
                return bytes;
            }
        } catch (IOException | IllegalArgumentException invalid) { throw unavailable(); }
        throw unavailable();
    }

    /** Original SelectOption projection of DATASOURCE_TYPE, limited to types declared by this platform profile's plugins. */
    public List<Map<String, Object>> getDatasourceTypeOptions() {
        Set<String> required = new java.util.LinkedHashSet<>();
        for (Plugin plugin : plugins()) {
            String type = scalar(plugin.definition.get("adapterDatabaseType"));
            if (type != null && !type.isEmpty()) required.add(type);
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : seedRows(sqlResources().get(TYPE_SQL.get(0)), 54)) {
            if (!required.remove(row.get("code"))) continue;
            Map<String, Object> option = new LinkedHashMap<>();
            option.put("name", row.get("name")); option.put("value", row.get("value")); option.put("code", row.get("code")); option.put("comment", null);
            result.add(Collections.unmodifiableMap(option));
        }
        if (!required.isEmpty()) throw unavailable();
        return List.copyOf(result);
    }

    public List<Map<String, Object>> getAdapterTypeOptions() {
        String sql = sqlResources().get(TYPE_SQL.get(0));
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : seedRows(sql, 1)) {
            if (!TYPES.containsValue(row.get("value"))) continue;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", row.get("name")); item.put("value", row.get("value")); item.put("code", row.get("code"));
            item.put("comment", row.get("description")); result.add(Collections.unmodifiableMap(item));
        }
        if (result.size() != 3) throw unavailable();
        return List.copyOf(result);
    }

    /** Reads the original DATA_TYPE seeds and their finite vendor migrations; no SQL is executed. */
    public List<Map<String, Object>> getDataTypes() {
        Map<String, String> scripts = sqlResources();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (String name : TYPE_SQL) {
            String sql = scripts.get(name);
            rows.addAll(seedRows(sql, 200));
            if (name.equals("V1.0.18__ETL2_2.1.1_b1.sql")) {
                if (!sql.contains("set name = 'EXTERNAL_FILE', code = 'EXTERNAL_FILE'")) throw unavailable();
                for (Map<String, Object> row : rows) if ("STRL".equals(row.get("name"))) { row.put("name", "EXTERNAL_FILE"); row.put("code", "EXTERNAL_FILE"); }
            }
            if (name.equals("V1.0.19__ETL2_2.1.1_b1_0812.sql")) {
                if (!sql.contains("name = lower(name) , code = lower(code)")) throw unavailable();
                for (Map<String, Object> row : rows) for (String field : List.of("name", "code")) row.put(field, row.get(field).toString().toLowerCase(java.util.Locale.ROOT));
            }
            Matcher update = DESCRIPTION.matcher(sql);
            while (update.find()) {
                Set<String> names = Set.copyOf(splitValues(update.group(2)).stream().map(HdiCoreCatalog::literal).map(Object::toString)
                    .map(s -> s.toLowerCase(java.util.Locale.ROOT)).toList());
                for (Map<String, Object> row : rows) if (names.contains(row.get("name").toString().toLowerCase(java.util.Locale.ROOT))) row.put("description", update.group(1));
            }
        }
        if (rows.isEmpty() || rows.stream().map(row -> row.get("value")).distinct().count() != rows.size()) throw unavailable();
        rows.sort(Comparator.comparingLong(row -> ((Number)row.get("sort")).longValue()));
        return rows.stream().map(Collections::unmodifiableMap).toList();
    }

    private List<Plugin> plugins() {
        Map<String, Plugin> found = new LinkedHashMap<>();
        for (String path : assets.originalPaths()) {
            if (!path.matches("instance/[^/]+/etl_plugin\\.json")) continue;
            JsonNode def = json(assets.readOriginal(path)); String name = text(def, "adapterName");
            if (!SUPPORTED.contains(name)) continue;
            String folder = path.substring(0, path.lastIndexOf('/'));
            Properties props = new Properties();
            try { props.load(new ByteArrayInputStream(assets.readOriginal(folder + "/adapter.properties"))); }
            catch (IOException error) { throw unavailable(); }
            for (String field : List.of("adapterName", "adapterType", "adapterDisplayName", "version", "className"))
                if (!text(def, field).equals(props.getProperty(field))) throw unavailable();
            if (!TYPES.get(name).equals(text(def, "adapterType")) || !"java".equals(props.getProperty("adapterLanguage"))
                    || !"java".equals(text(def, "adapterLanguge")) || found.containsKey(name)) throw unavailable();
            found.put(name, new Plugin(SUPPORTED.indexOf(name) + 1L, folder, def, props));
        }
        if (found.size() != SUPPORTED.size()) throw unavailable();
        return SUPPORTED.stream().map(found::get).toList();
    }

    private static List<Map<String, Object>> params(JsonNode definitions, int type, long adapter) {
        if (definitions == null || !definitions.isArray() || definitions.size() > 2048) throw unavailable();
        List<Map<String, Object>> result = new ArrayList<>(); Set<String> names = new java.util.HashSet<>();
        for (JsonNode def : definitions) {
            String name = text(def, "name");
            if (!names.add(name) || !def.has("defaultValue") || !def.path("required").isIntegralNumber()
                    || !Set.of(0, 1).contains(def.path("required").intValue())) throw unavailable();
            Map<String, Object> row = new LinkedHashMap<>(); long id = type * 1_000_000L + adapter * 10_000L + result.size() + 1;
            row.put("id", id); row.put("paramName", name); row.put("paramDisplayName", text(def, "displayName"));
            row.put("paramTypeId", type); row.put("paramDefaultValue", scalar(def.get("defaultValue")));
            row.put("loadFlag", def.get("required").intValue()); row.put("required", def.get("required").intValue());
            row.put("valueType", text(def, type == 2 ? "type" : "paramType"));
            row.put("addComment", scalar(def.get("description"))); row.put("minValue", scalar(def.get("minValue"))); row.put("maxValue", scalar(def.get("maxValue")));
            row.put("paramCategory", scalar(def.get("category"))); row.put("paramSubCategory", scalar(def.get("subCategory")));
            row.put("paramOrder", def.has("order") ? def.get("order").intValue() : result.size() + 1);
            JsonNode values = def.get("allowableValues");
            if (values == null || !values.isArray() || values.size() > 1024) throw unavailable();
            List<Map<String, Object>> choices = new ArrayList<>();
            for (JsonNode value : values) {
                if (!value.has("value")) throw unavailable();
                Map<String, Object> choice = new LinkedHashMap<>(); choice.put("id", id * 1000 + choices.size() + 1);
                choice.put("configName", text(value, "name")); choice.put("configValue", scalar(value.get("value")));
                choices.add(Collections.unmodifiableMap(choice));
            }
            row.put("values", List.copyOf(choices)); result.add(Collections.unmodifiableMap(row));
        }
        return List.copyOf(result);
    }

    private Map<String, String> sqlResources() {
        Map<String, String> result = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(assets.readOriginal(DATA_JAR)))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (!entry.getName().startsWith(SQL_ROOT)) continue;
                String name = entry.getName().substring(SQL_ROOT.length());
                if (!TYPE_SQL.contains(name)) continue;
                byte[] bytes = zip.readNBytes(1024 * 1024 + 1);
                if (bytes.length > 1024 * 1024 || result.containsKey(name)) throw unavailable();
                result.put(name, new String(bytes, StandardCharsets.UTF_8));
            }
        } catch (IOException error) { throw unavailable(); }
        if (result.size() != TYPE_SQL.size()) throw unavailable();
        return result;
    }

    private static List<Map<String, Object>> seedRows(String sql, int parent) {
        List<Map<String, Object>> rows = new ArrayList<>(); Matcher insert = INSERT.matcher(sql);
        while (insert.find()) {
            List<String> columns = splitValues(insert.group(1)).stream().map(v -> v.replace("\"", "").trim()).toList();
            int pid = columns.indexOf("pid"); if (pid < 0) continue;
            for (String tuple : splitValues(insert.group(2))) {
                if (!tuple.startsWith("(") || !tuple.endsWith(")")) throw unavailable();
                List<String> values = splitValues(tuple.substring(1, tuple.length() - 1));
                if (values.size() != columns.size() || !Integer.toString(parent).equals(values.get(pid))) continue;
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 0; i < columns.size(); i++) {
                    String column = columns.get(i);
                    if (Set.of("id", "name", "value", "code", "pid", "sort", "description").contains(column)) row.put(column, literal(values.get(i)));
                    if (column.equals("has_child")) row.put("hasChild", literal(values.get(i)));
                    if (column.equals("has_detailed")) row.put("hasDetailed", literal(values.get(i)));
                }
                if (row.get("name") == null || row.get("value") == null || row.get("code") == null || !(row.get("sort") instanceof Number)) throw unavailable();
                row.put("value", row.get("value").toString()); rows.add(row);
            }
        }
        return rows;
    }

    private static List<String> splitValues(String value) {
        List<String> result = new ArrayList<>(); boolean quoted = false; int depth = 0, start = 0;
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch == '\'') {
                if (quoted && i + 1 < value.length() && value.charAt(i + 1) == '\'') { i++; continue; }
                quoted = !quoted;
            } else if (!quoted) {
                if (ch == '(') depth++; else if (ch == ')') depth--;
                else if (ch == ',' && depth == 0) { result.add(value.substring(start, i).trim()); start = i + 1; }
            }
        }
        if (quoted || depth != 0) throw unavailable(); result.add(value.substring(start).trim()); return result;
    }
    private static Object literal(String value) {
        if (value.equalsIgnoreCase("null")) return null;
        if (value.startsWith("'") && value.endsWith("'")) return value.substring(1, value.length() - 1).replace("''", "'");
        try { return Long.parseLong(value); } catch (NumberFormatException error) { throw unavailable(); }
    }
    private static JsonNode json(byte[] bytes) {
        try { JsonNode value = JSON.readTree(bytes); if (value == null || !value.isObject()) throw unavailable(); return value; }
        catch (IOException error) { throw unavailable(); }
    }
    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || value.textValue().isBlank() || value.textValue().length() > 8192) throw unavailable();
        return value.textValue();
    }
    private static String scalar(JsonNode value) {
        if (value == null || value.isNull()) return null;
        if (!value.isValueNode()) throw unavailable();
        return value.asText();
    }
    private static HdiTaskFault badQuery() { return new HdiTaskFault(400, "当前任务范围不支持该参数类型或插件"); }
    private static HdiTaskFault unavailable() { return new HdiTaskFault(503, "原插件定义或字段类型目录不可用"); }
}
