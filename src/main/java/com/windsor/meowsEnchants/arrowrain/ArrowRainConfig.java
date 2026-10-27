package com.windsor.meowsEnchants.arrowrain;

import com.windsor.meowsEnchants.schema.EnchantSchema;
import com.windsor.meowsEnchants.schema.Param;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.yaml.snakeyaml.Yaml;

/** Installs only missing defaults; existing volley/custom configurations remain an explicit opt-in. */
public final class ArrowRainConfig {
    public static final String FILE = "arrow_rain.yml";
    public static final String SECTION = "arrow_rain";
    public static final String MECHANIC = "landing_arrow_rain_v1";
    /** {@code arrow_rain:} 段的全部参数。 */
    public static final List<Param<?>> PARAMS = ArrowRainSettings.PARAMS;

    private ArrowRainConfig() {}

    public static void installMissing(Path directory) throws IOException {
        try (InputStream stream = ArrowRainConfig.class.getResourceAsStream("/enchants/" + FILE)) {
            if (stream == null) throw new IOException("Missing arrow_rain.yml resource");
            copyMissing(directory.resolve(FILE), stream);
        }
    }

    static void copyMissing(Path path, InputStream stream) throws IOException {
        try { Files.copy(stream, path); }
        catch (FileAlreadyExistsException ignored) { /* Never overwrite server configuration. */ }
    }

    /**
     * 由 arrow_rain.yml 的原始内容得到箭雨参数；文件不存在或没有 {@code mechanic: landing_arrow_rain_v1}
     * 时返回 null，此时落地箭雨停用（旧的 volley 或自定义配置保持原行为）。
     */
    public static ArrowRainSettings settings(Map<String, Object> yaml) {
        if (yaml == null || !MECHANIC.equals(EnchantSchema.MECHANIC.read(yaml))) return null;
        return ArrowRainSettings.fromYaml(section(yaml.get(SECTION)));
    }

    /** jar 里内置的 arrow_rain.yml。 */
    public static Map<String, Object> bundledYaml() throws IOException {
        try (InputStream stream = ArrowRainConfig.class.getResourceAsStream("/enchants/" + FILE)) {
            if (stream == null) throw new IOException("Missing arrow_rain.yml resource");
            return new Yaml().load(stream);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> section(Object raw) {
        return raw instanceof Map<?, ?> map ? (Map<String, Object>) map : null;
    }
}
