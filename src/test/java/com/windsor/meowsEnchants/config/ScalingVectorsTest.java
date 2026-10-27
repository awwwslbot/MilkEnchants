package com.windsor.meowsEnchants.config;

import static org.junit.jupiter.api.Assertions.*;
import com.windsor.meowsEnchants.actions.ScalingFunction;
import com.windsor.meowsEnchants.actions.ScalingFunctionLoader;
import com.windsor.meowsEnchants.tracking.TrackingSettings;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** editor/scaling-vectors.json：插件和编辑器都必须得到同样的每级取值（契约 §4）。 */
class ScalingVectorsTest {
    @Test void everyVectorMatchesThePluginScaling() throws Exception {
        List<Map<String, Object>> vectors = VectorFiles.load("scaling-vectors.json");
        assertTrue(vectors.size() >= 20);
        for (Map<String, Object> vector : vectors) {
            Object value = vector.get("value");
            boolean overlay = "overlay".equals(vector.get("listSemantics"));
            if (overlay) assertEquals(TrackingSettings.RADIUS.defaultJson(), vector.get("default"), "overlay default");
            ScalingFunction scaling;
            try {
                scaling = overlay ? TrackingSettings.RADIUS.convert(value) : ScalingFunctionLoader.load(value);
            } catch (IllegalArgumentException | ClassCastException e) {
                scaling = null;
            }
            if (Boolean.TRUE.equals(vector.get("expectError"))) {
                assertNull(scaling, "expected an error for " + vector);
                continue;
            }
            assertNotNull(scaling, "unexpected error for " + vector);
            double actual = scaling.getValue(((Number) vector.get("level")).intValue());
            if ("int".equals(vector.get("type"))) actual = (int) actual;
            assertEquals(((Number) vector.get("expect")).doubleValue(), actual, 1e-9, vector.toString());
        }
    }
}
