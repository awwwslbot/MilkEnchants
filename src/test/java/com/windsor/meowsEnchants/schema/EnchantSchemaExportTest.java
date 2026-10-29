package com.windsor.meowsEnchants.schema;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;

/**
 * 仓库里提交的 editor/enchant-schema.json 必须等于由代码声明导出的结果。
 * 更新：{@code mvn -B test -Dtest=EnchantSchemaExportTest -Dmeowsenchants.updateSchema=true}
 */
class EnchantSchemaExportTest {
    @Test void committedSchemaEqualsExport() throws Exception {
        if (Boolean.getBoolean("meowsenchants.updateSchema")) {
            SchemaExport.write(SchemaExport.SCHEMA_FILE);
        }
        assertTrue(Files.isRegularFile(SchemaExport.SCHEMA_FILE), "缺少 " + SchemaExport.SCHEMA_FILE);
        String expected = SchemaExport.render(SchemaExport.pluginVersion());
        String committed = Files.readString(SchemaExport.SCHEMA_FILE, StandardCharsets.UTF_8);
        assertEquals(expected, committed, SchemaExport.SCHEMA_FILE + " 与代码声明不一致，运行 "
                + "mvn -B test -Dtest=EnchantSchemaExportTest -Dmeowsenchants.updateSchema=true 重新导出");
    }

    @Test void versionComesFromTheBuild() {
        assertEquals("0.2-milk.14", SchemaExport.pluginVersion());
    }
}
