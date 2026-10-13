package com.hm.manage.service.governance.hdi;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

class HdiCoreCatalogTest {
    @TempDir Path temp;
    HdiCoreAssetsTest.Fixtures fixture;
    HdiCoreCatalog catalog;
    @BeforeEach void setup() throws Exception {
        fixture = new HdiCoreAssetsTest.Fixtures(temp);
        for (String[] plugin : new String[][]{{"dataGeneratorInput", "input"}, {"fieldMapping", "preproc"}, {"fileOutput", "output"}}) {
            String folder = "instance/etl-" + plugin[0] + "-2.0.0/";
            String json = "{\"adapterName\":\"" + plugin[0] + "\",\"adapterDisplayName\":\"原插件\",\"adapterType\":\"" + plugin[1]
                + "\",\"version\":\"2.0.0\",\"className\":\"vendor.Original\",\"adapterLanguge\":\"java\",\"adapterParams\":["
                + "{\"name\":\"size\",\"displayName\":\"数据量\",\"defaultValue\":100000,\"required\":1,\"paramType\":\"el-input\",\"allowableValues\":[]},"
                + "{\"name\":\"mode\",\"displayName\":\"模式\",\"defaultValue\":\"false\",\"required\":0,\"paramType\":\"el-select\",\"allowableValues\":[{\"name\":\"关闭\",\"value\":\"false\"}]}]}";
            fixture.put(folder + "etl_plugin.json", json.getBytes(StandardCharsets.UTF_8));
            fixture.put(folder + "adapter.properties", ("adapterName=" + plugin[0] + "\nadapterDisplayName=\\u539f\\u63d2\\u4ef6\nadapterType=" + plugin[1]
                + "\nversion=2.0.0\nclassName=vendor.Original\nadapterLanguage=java\npluginResourceType=external\n").getBytes(StandardCharsets.ISO_8859_1));
        }
        fixture.put("instance/conf/task_config.json", "{\"configParam\":[{\"name\":\"retry\",\"displayName\":\"重试\",\"defaultValue\":\"true\",\"required\":1,\"type\":\"el-switch\",\"allowableValues\":[]}]}".getBytes(StandardCharsets.UTF_8));
        for (String icon : List.of("defaultinput.e71f57a7.svg", "defaultpreproc.26462e9c.svg", "defaultoutput.bfb8531e.svg"))
            fixture.put("public/static/img/" + icon, "<svg xmlns=\"http://www.w3.org/2000/svg\"/>".getBytes(StandardCharsets.UTF_8));
        fixture.manifest(); catalog = new HdiCoreCatalog(fixture.properties, new HdiCoreAssets(fixture.properties));
    }

    @Test void parsesFactoryDefaultsControlTypesRequiredFlagsAndNamedChoices() {
        assertEquals(List.of("dataGeneratorInput", "fieldMapping", "fileOutput"), catalog.getAdapters().stream().map(row -> row.get("adapterName")).toList());
        assertEquals(List.of(1L, 2L, 3L), catalog.getAdapters().stream().map(row -> row.get("id")).toList());
        assertEquals(List.of("/static/img/defaultinput.e71f57a7.svg", "/static/img/defaultpreproc.26462e9c.svg", "/static/img/defaultoutput.bfb8531e.svg"),
            catalog.getAdapters().stream().map(row -> row.get("iconStr")).toList());
        assertEquals("other", catalog.getAdapters().get(1).get("processType"));
        var rows = catalog.listParams(9, "dataGeneratorInput", "input");
        assertEquals("100000", rows.get(0).get("paramDefaultValue")); assertEquals(1, rows.get(0).get("loadFlag"));
        assertEquals("el-input", rows.get(0).get("valueType")); assertEquals("false", rows.get(1).get("paramDefaultValue"));
        assertEquals(0, rows.get(1).get("loadFlag"));
        var choice = (Map<?, ?>)((List<?>)rows.get(1).get("values")).get(0);
        assertEquals("关闭", choice.get("configName")); assertEquals("false", choice.get("configValue"));
        assertEquals("true", catalog.listParams(2, null, null).get(0).get("paramDefaultValue"));
    }

    @Test void missingDefinitionOrUnsupportedPluginIsNeverAnEmptySuccess() throws Exception {
        assertEquals(400, assertThrows(HdiTaskFault.class, () -> catalog.listParams(9, "jdbcInput", "input")).status());
        assertEquals(400, assertThrows(HdiTaskFault.class, () -> catalog.listParams(9, "fileOutput", "input")).status());
        assertThrows(HdiTaskFault.class, () -> catalog.listParams(42, null, null));
        Files.delete(fixture.bundle.resolve("instance/conf/task_config.json"));
        assertEquals(503, assertThrows(HdiTaskFault.class, () -> catalog.listParams(2, null, null)).status());
        Files.writeString(fixture.bundle.resolve("instance/etl-dataGeneratorInput-2.0.0/adapter.properties"), "adapterName=changed");
        assertEquals(503, assertThrows(HdiTaskFault.class, () -> catalog.getAdapters()).status());
    }

    @Test void missingDefaultFailsEvenWhenAnUpdatedManifestMatchesIt() throws Exception {
        String relative = "instance/conf/task_config.json";
        fixture.entries.removeIf(row -> relative.equals(row.get("path")));
        fixture.put(relative, "{\"configParam\":[{\"name\":\"retry\",\"displayName\":\"重试\",\"required\":1,\"type\":\"el-switch\",\"allowableValues\":[]}]}".getBytes(StandardCharsets.UTF_8)); fixture.manifest();
        assertThrows(HdiTaskFault.class, () -> catalog.listParams(2, null, null));
    }

    @Test void readsOriginalDictionaryRowsAndAppliesOnlyRecordedMigrations() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        String[] names = {"V1.0.1__INIT_DATA.sql", "V1.0.10__ETL2_0511.sql", "V1.0.13__ETL2_2.1.0_b3.sql", "V1.0.18__ETL2_2.1.1_b1.sql", "V1.0.19__ETL2_2.1.1_b1_0812.sql", "V1.0.20__ETL2_2.1.1_b2.sql", "V1.0.23__ETL2_2.1.1_r2.sql", "V1.0.26__ETL2_2.1.2_b3.sql"};
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (int i = 0; i < names.length; i++) {
                String sql = "-- original migration fixture\n";
                if (i == 0) sql += "INSERT INTO public.sys_code (id,name,value,code,pid,sort) VALUES(204,'STRING','3','STRING',200,4);\n"
                    + "INSERT INTO sys_code(name,value,code,pid,sort) VALUES('输入插件','input','input',1,1),('处理插件','preproc','preproc',1,2),('输出插件','output','output',1,3);";
                if (i == 1) sql += "INSERT INTO sys_code(id,name,value,code,pid,sort) VALUES(230,'STRL','28','STRL',200,29);";
                if (i == 2) sql += "update sys_code set description = 'js' where pid = 200 and name in ('STRING');";
                if (i == 3) sql += "update sys_code set name = 'EXTERNAL_FILE', code = 'EXTERNAL_FILE' where pid = 200 and name = 'STRL';";
                if (i == 4) sql += "update sys_code set name = lower(name) , code = lower(code) where pid in (select id from sys_code where code = 'DATA_TYPE');";
                if (i == 7) sql += "INSERT INTO sys_code(name,value,code,pid,sort) VALUES('float4','34','float4',200,35);";
                zip.putNextEntry(new ZipEntry("db/migration/V200/" + names[i])); zip.write(sql.getBytes(StandardCharsets.UTF_8)); zip.closeEntry();
            }
        }
        fixture.put("task-service/dEtlv2.jar", bytes.toByteArray()); fixture.manifest();
        var types = catalog.getDataTypes();
        assertEquals(List.of("string", "external_file", "float4"), types.stream().map(row -> row.get("name")).toList());
        assertEquals("js", types.get(0).get("description"));
        assertEquals(3, catalog.getAdapterTypeOptions().size());
    }

    @Test @EnabledIfSystemProperty(named = "hdi.core.bundle", matches = ".+")
    void verifiesTheActualPackagedVendorCatalogWithoutRunningVendorCode() {
        HdiCoreProperties properties = new HdiCoreProperties(); properties.setBundleDir(System.getProperty("hdi.core.bundle"));
        HdiCoreCatalog real = new HdiCoreCatalog(properties, new HdiCoreAssets(properties));
        assertEquals(3, real.getAdapters().size());
        assertTrue(real.getAdapterUiInfo("dataGeneratorInput").isEmpty());
        assertTrue(real.getAdapterUiInfo("fieldMapping").isEmpty());
        assertEquals(Map.of("adapterExtField", "false", "adapterTableUse", "false", "adapterTableTruncate", "false"), real.getAdapterUiInfo("fileOutput"));
        for (String[] plugin : new String[][]{{"dataGeneratorInput", "input"}, {"fieldMapping", "preproc"}, {"fileOutput", "output"}}) {
            var adapterParams = real.listParams(6, plugin[0], plugin[1]);
            assertFalse(adapterParams.isEmpty());
            assertTrue(adapterParams.stream().allMatch(row -> Integer.valueOf(6).equals(row.get("paramTypeId"))));
        }
        var generator = real.listParams(9, "dataGeneratorInput", "input");
        assertEquals("100000", generator.stream().filter(row -> row.get("paramName").equals("etl.plugin.input.generator.data.size")).findFirst().orElseThrow().get("paramDefaultValue"));
        assertEquals(20, real.listParams(2, null, null).size());
        assertEquals(3, real.getAdapterTypeOptions().size());
        assertEquals(18, real.getAdapters().get(2).get("dbTypeId"));
        assertTrue(real.getFileColumnTypes().stream().anyMatch(row -> "string".equals(row.get("columnType"))));
        assertTrue(real.getFileColumnTypes().stream().allMatch(row -> row.get("actualFieldTypeId") == null && row.get("fieldType").equals(row.get("columnType"))));
        assertEquals(24, real.getJdbcDbTypes().size());
        assertEquals(List.of(1, 2, 3, 7, 16, 17, 19, 22, 25, 26, 83, 101, 109, 121, 124, 140, 144, 147, 149, 160, 180, 192, 193, 195), real.getJdbcDbTypes());
        assertTrue(real.getDataTypes().stream().anyMatch(row -> "external_file".equals(row.get("name"))));
        assertTrue(real.getDataTypes().stream().anyMatch(row -> "float4".equals(row.get("name"))));
    }
    @Test void originalIconMustMatchThePublicManifest() throws Exception {
        Files.writeString(fixture.bundle.resolve("public/static/img/defaultinput.e71f57a7.svg"), "tampered");
        assertEquals(503, assertThrows(HdiTaskFault.class, () -> catalog.getAdapters()).status());
    }

    @Test void adapterUiInfoComesFromVerifiedOptionalPluginMetadata() throws Exception {
        assertTrue(catalog.getAdapterUiInfo("dataGeneratorInput").isEmpty());
        assertTrue(catalog.getAdapterUiInfo("fieldMapping").isEmpty());
        String path = "instance/etl-fileOutput-2.0.0/etl_plugin.json";
        var definition = HdiProtocol.JSON.readTree(Files.readAllBytes(fixture.bundle.resolve(path)));
        ((com.fasterxml.jackson.databind.node.ObjectNode)definition).putObject("adapterUIParams")
            .put("adapterExtField", "false").put("adapterTableUse", "false").put("adapterTableTruncate", "false");
        fixture.entries.removeIf(row -> path.equals(row.get("path")));
        fixture.put(path, HdiProtocol.JSON.writeValueAsBytes(definition)); fixture.manifest();
        assertEquals(Map.of("adapterExtField", "false", "adapterTableUse", "false", "adapterTableTruncate", "false"), catalog.getAdapterUiInfo("fileOutput"));
        assertEquals(400, assertThrows(HdiTaskFault.class, () -> catalog.getAdapterUiInfo("jdbcOutput")).status());
        Files.writeString(fixture.bundle.resolve(path), "{}");
        assertEquals(503, assertThrows(HdiTaskFault.class, () -> catalog.getAdapterUiInfo("fileOutput")).status());
    }

    @Test void jdbcDictionaryReadsLiteralClassMetadataWithoutLoadingVendorCode() throws Exception {
        String owner = "com/hikvision/idatafusion/detl/common/DataSourceConstants";
        var code = new org.springframework.asm.ClassWriter(0);
        code.visit(org.springframework.asm.Opcodes.V1_8, org.springframework.asm.Opcodes.ACC_PUBLIC, owner, null, "java/lang/Object", null);
        var init = code.visitMethod(org.springframework.asm.Opcodes.ACC_STATIC, "<clinit>", "()V", null, null); init.visitCode();
        for (int value : new int[]{17, 2}) {
            init.visitIntInsn(org.springframework.asm.Opcodes.BIPUSH, value);
            init.visitMethodInsn(org.springframework.asm.Opcodes.INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false);
            init.visitFieldInsn(org.springframework.asm.Opcodes.PUTSTATIC, owner, "TYPE_" + value, "Ljava/lang/Integer;");
        }
        init.visitInsn(org.springframework.asm.Opcodes.ICONST_2); init.visitTypeInsn(org.springframework.asm.Opcodes.ANEWARRAY, "java/lang/Integer");
        int index = 0;
        for (int value : new int[]{17, 2}) {
            init.visitInsn(org.springframework.asm.Opcodes.DUP); init.visitIntInsn(org.springframework.asm.Opcodes.BIPUSH, index++);
            init.visitFieldInsn(org.springframework.asm.Opcodes.GETSTATIC, owner, "TYPE_" + value, "Ljava/lang/Integer;"); init.visitInsn(org.springframework.asm.Opcodes.AASTORE);
        }
        init.visitMethodInsn(org.springframework.asm.Opcodes.INVOKESTATIC, "com/google/common/collect/Sets", "newHashSet", "([Ljava/lang/Object;)Ljava/util/HashSet;", false);
        init.visitFieldInsn(org.springframework.asm.Opcodes.PUTSTATIC, owner, "JDBC_DATASOURCE_TYPE_LIST", "Ljava/util/Set;");
        init.visitInsn(org.springframework.asm.Opcodes.RETURN); init.visitMaxs(4, 0); init.visitEnd(); code.visitEnd();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) { zip.putNextEntry(new ZipEntry(owner + ".class")); zip.write(code.toByteArray()); zip.closeEntry(); }
        fixture.put("task-service/lib/dEtlv2-core-3.3.0.jar", bytes.toByteArray()); fixture.manifest();
        assertEquals(List.of(2, 17), catalog.getJdbcDbTypes());
        Files.writeString(fixture.bundle.resolve("task-service/lib/dEtlv2-core-3.3.0.jar"), "changed");
        assertEquals(503, assertThrows(HdiTaskFault.class, () -> catalog.getJdbcDbTypes()).status());
    }

}
