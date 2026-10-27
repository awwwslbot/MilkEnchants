package com.windsor.meowsEnchants.schema;

import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * 导出 {@code editor/enchant-schema.json}。
 * <p>
 * 日常用单元测试导出：{@code mvn -B test -Dtest=EnchantSchemaExportTest -Dmeowsenchants.updateSchema=true}；
 * 不带该属性时同一个测试比对仓库里提交的文件，不一致就失败。
 */
public final class SchemaExport {
    public static final Path SCHEMA_FILE = Path.of("editor", "enchant-schema.json");

    private SchemaExport() {}

    public static String render(String pluginVersion) {
        return Json.pretty(EnchantSchema.toJson(pluginVersion));
    }

    /** 构建时过滤进 paper-plugin.yml 的版本号。 */
    public static String pluginVersion() {
        try (InputStream input = SchemaExport.class.getResourceAsStream("/paper-plugin.yml")) {
            if (input == null) throw new IllegalStateException("paper-plugin.yml not on classpath");
            Map<String, Object> yaml = new Yaml().load(input);
            return String.valueOf(yaml.get("version"));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public static void write(Path file) throws IOException {
        if (file.getParent() != null) Files.createDirectories(file.getParent());
        Files.writeString(file, render(pluginVersion()), StandardCharsets.UTF_8);
    }

    public static void main(String[] args) throws IOException {
        Path file = args.length > 0 ? Path.of(args[0]) : SCHEMA_FILE;
        write(file);
        System.out.println("Wrote " + file.toAbsolutePath());
    }
}
