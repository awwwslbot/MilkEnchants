package com.windsor.meowsEnchants.config;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.yaml.snakeyaml.Yaml;

/** editor/*-vectors.json 用插件同款的 SnakeYAML 读（JSON 是 YAML 的子集，整数保持整数）。 */
final class VectorFiles {
    private VectorFiles() {}

    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> load(String name) throws Exception {
        try (InputStream input = Files.newInputStream(Path.of("editor", name))) {
            return (List<Map<String, Object>>) new Yaml().load(input);
        }
    }
}
