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
        fixture.manifest(); catalog = new HdiCoreCatalog(fixture.properties, new HdiCoreAssets(fixture.properties));
    }

    @Test void parsesFactoryDefaultsControlTypesRequiredFlagsAndNamedChoices() {
        assertEquals(List.of("dataGeneratorInput", "fieldMapping", "fileOutput"), catalog.getAdapters().stream().map(row -> row.get("adapterName")).toList());
        assertEquals(List.of(1L, 2L, 3L), catalog.getAdapters().stream().map(row -> row.get("id")).toList());
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
        var generator = real.listParams(9, "dataGeneratorInput", "input");
        assertEquals("100000", generator.stream().filter(row -> row.get("paramName").equals("etl.plugin.input.generator.data.size")).findFirst().orElseThrow().get("paramDefaultValue"));
        assertEquals(20, real.listParams(2, null, null).size());
        assertEquals(3, real.getAdapterTypeOptions().size());
        assertTrue(real.getDataTypes().stream().anyMatch(row -> "external_file".equals(row.get("name"))));
        assertTrue(real.getDataTypes().stream().anyMatch(row -> "float4".equals(row.get("name"))));
    }
}
