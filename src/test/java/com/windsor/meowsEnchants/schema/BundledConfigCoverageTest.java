package com.windsor.meowsEnchants.schema;

import static org.junit.jupiter.api.Assertions.*;
import com.windsor.meowsEnchants.actions.ActionRegistry;
import com.windsor.meowsEnchants.arrowrain.ArrowRainConfig;
import com.windsor.meowsEnchants.config.EnchantConfigLoader;
import com.windsor.meowsEnchants.config.GlobalSettings;
import com.windsor.meowsEnchants.tracking.TrackingConfig;
import com.windsor.meowsEnchants.tracking.TrackingSettings;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/** 每份内置 yml 和 config.yml 里出现的键都在 schema 里有声明，且内置附魔都能解析。 */
class BundledConfigCoverageTest {
    static final Path ENCHANTS = Path.of("src/main/resources/enchants");

    static List<Path> bundled() throws Exception {
        try (Stream<Path> files = Files.list(ENCHANTS)) {
            return files.filter(p -> p.toString().endsWith(".yml")).sorted().toList();
        }
    }

    static Map<String, Object> load(Path file) throws Exception {
        try (InputStream input = Files.newInputStream(file)) {
            return new Yaml().load(input);
        }
    }

    @Test void allThirtyNineBundledEnchantsParse() throws Exception {
        List<Path> files = bundled();
        assertEquals(39, files.size());
        for (Path file : files) {
            String id = file.getFileName().toString().replace(".yml", "");
            assertNotNull(EnchantConfigLoader.parse(id, load(file)), id);
        }
    }

    @Test void everyKeyInTheBundledEnchantsIsDeclared() throws Exception {
        List<String> unknown = new ArrayList<>();
        for (Path file : bundled()) {
            String id = file.getFileName().toString();
            Map<String, Object> data = load(file);
            for (Map.Entry<String, Object> entry : data.entrySet()) {
                String key = entry.getKey();
                if (id.equals(ArrowRainConfig.FILE) && key.equals(ArrowRainConfig.SECTION)) {
                    walk(id + ":" + key, ArrowRainConfig.PARAMS, entry.getValue(), unknown);
                    continue;
                }
                if (id.equals(TrackingConfig.FILE) && key.equals(TrackingConfig.SECTION)) {
                    walk(id + ":" + key, TrackingSettings.PARAMS, entry.getValue(), unknown);
                    continue;
                }
                Param<?> param = find(EnchantSchema.PARAMS, key);
                if (param == null) {
                    unknown.add(id + ":" + key);
                } else if (param == EnchantSchema.ACTIONS) {
                    for (Map.Entry<?, ?> action : ((Map<?, ?>) entry.getValue()).entrySet()) {
                        Map<?, ?> params = (Map<?, ?>) action.getValue();
                        var type = ActionRegistry.type(String.valueOf(params.get("type")));
                        assertNotNull(type, id + ": action type " + params.get("type"));
                        for (Map.Entry<?, ?> p : params.entrySet()) {
                            if (p.getKey().equals("type")) continue;
                            String path = id + ":actions." + action.getKey() + "." + p.getKey();
                            Param<?> declared = find(type.params(), String.valueOf(p.getKey()));
                            if (declared == null) unknown.add(path);
                            else if (declared.type() == Param.Type.SECTION) walk(path, declared.children(), p.getValue(), unknown);
                        }
                    }
                } else if (param.type() == Param.Type.SECTION) {
                    walk(id + ":" + key, param.children(), entry.getValue(), unknown);
                }
            }
        }
        assertEquals(List.of(), unknown);
    }

    @Test void everyKeyInTheBundledConfigYmlIsDeclared() throws Exception {
        List<String> unknown = new ArrayList<>();
        Map<String, Object> data = load(Path.of("src/main/resources/config.yml"));
        walk("config.yml", GlobalSettings.PARAMS, data, unknown);
        assertEquals(List.of(), unknown);
        assertEquals(GlobalSettings.DEFAULTS, GlobalSettings.from(data), "bundled config.yml equals the built-in defaults");
    }

    static void walk(String path, List<Param<?>> params, Object value, List<String> unknown) {
        if (!(value instanceof Map<?, ?> map)) return;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            Param<?> param = find(params, String.valueOf(entry.getKey()));
            String child = path + "." + entry.getKey();
            if (param == null) unknown.add(child);
            else if (param.type() == Param.Type.SECTION) walk(child, param.children(), entry.getValue(), unknown);
        }
    }

    static Param<?> find(List<Param<?>> params, String key) {
        for (Param<?> param : params) {
            if (param.key().equals(key) || param.aliases().contains(key)) return param;
        }
        return null;
    }
}
