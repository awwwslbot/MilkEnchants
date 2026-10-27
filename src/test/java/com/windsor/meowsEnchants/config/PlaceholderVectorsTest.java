package com.windsor.meowsEnchants.config;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** editor/placeholder-vectors.json：/enchants 说明占位符，插件和编辑器预览必须一致（契约 §6）。 */
class PlaceholderVectorsTest {
    @Test @SuppressWarnings("unchecked")
    void everyVectorRendersExactly() throws Exception {
        List<Map<String, Object>> vectors = VectorFiles.load("placeholder-vectors.json");
        assertTrue(vectors.size() >= 30, "契约要求至少 30 条");
        for (Map<String, Object> vector : vectors) {
            Map<String, Object> enchant = (Map<String, Object>) vector.get("enchant");
            String line = (String) vector.get("line");
            Placeholders.Result result = Placeholders.render(enchant, line);
            assertEquals(vector.get("expect"), result.text(), line);
            assertEquals(vector.get("unresolved"), result.unresolved(), line);
        }
    }

    @Test void linesWithoutPlaceholdersAreUnchanged() {
        String line = "<gradient:#CFCFCF:#00D4FF>箭雨</gradient> &f箭矢首次命中后，从天降下箭雨";
        assertEquals(line, Placeholders.render(Map.of(), line).text());
        assertEquals(List.of(), Placeholders.render(Map.of(), line).unresolved());
    }
}
