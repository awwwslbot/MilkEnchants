package com.windsor.meowsEnchants.schema;

import static org.junit.jupiter.api.Assertions.*;
import com.windsor.meowsEnchants.actions.FixedScaling;
import com.windsor.meowsEnchants.actions.LinearScaling;
import com.windsor.meowsEnchants.actions.ListScaling;
import com.windsor.meowsEnchants.actions.ScalingFunction;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 声明即读取：缺省取默认、必填缺失和不合法的按等级写法抛 IllegalArgumentException（动作被跳过）。 */
class ParamTest {
    static final Param<ScalingFunction> REQUIRED = Param.scalableNumber("amount", "数值").required().build();
    static final Param<ScalingFunction> OPTIONAL = Param.scalableInt("extra", "额外").def(Map.of("base", 1, "per_level", 2)).build();
    static final Param<Boolean> FLAG = Param.bool("flag", "开关").alias("oldFlag").def(true).build();
    static final Param<Integer> COUNT = Param.integer("count", "数量").def(5).build();

    @Test void scalableReadsAllFourForms() {
        assertEquals(new FixedScaling(0.5), REQUIRED.read(Map.of("amount", 0.5)));
        assertEquals(new FixedScaling(2.0), REQUIRED.read(Map.of("amount", Map.of("scaling", "fixed", "value", 2))));
        assertEquals(new LinearScaling(0.1, 0.2), REQUIRED.read(Map.of("amount", Map.of("base", 0.1, "per_level", 0.2))));
        assertEquals(new ListScaling(List.of(1.0, 2.0)), REQUIRED.read(Map.of("amount", List.of(1, 2))));
        assertEquals(new LinearScaling(1, 2), OPTIONAL.read(Map.of()));
    }

    @Test void requiredAndInvalidScalingThrowLikeMilk12() {
        assertThrows(IllegalArgumentException.class, () -> REQUIRED.read(Map.of()));
        assertThrows(IllegalArgumentException.class, () -> REQUIRED.read(Map.of("amount", "x")));
        assertThrows(IllegalArgumentException.class, () -> OPTIONAL.read(Map.of("extra", List.of())));
    }

    @Test void plainValuesFallBackToTheDefaultOnWrongTypes() {
        assertEquals(5, COUNT.read(Map.of()));
        assertEquals(5, COUNT.read(Map.of("count", "seven")));
        assertEquals(7, COUNT.read(Map.of("count", 7.9)), "ints truncate like Number#intValue");
        assertEquals(true, FLAG.read(Map.of()));
        assertEquals(false, FLAG.read(Map.of("oldFlag", false)), "aliases are read too");
        assertEquals(true, FLAG.read(Map.of("flag", false, "oldFlag", true)), "the legacy alias wins, like milk.12's onGround");
    }

    @Test void jsonCarriesDefaultsInTheirYamlForm() {
        Map<String, Object> json = OPTIONAL.toJson();
        assertEquals(Map.of("base", 1, "per_level", 2), json.get("default"));
        assertEquals("int", json.get("type"));
        assertEquals(true, json.get("scalable"));
        assertFalse(REQUIRED.toJson().containsKey("default"));
        assertEquals(List.of("oldFlag"), FLAG.toJson().get("aliases"));
    }

    @Test void aDefaultThatDoesNotMatchItsTypeFailsFast() {
        assertThrows(IllegalStateException.class, () -> Param.integer("x", "x").def("oops").build());
        assertThrows(IllegalStateException.class, () -> Param.scalableNumber("x", "x").build());
    }
}
