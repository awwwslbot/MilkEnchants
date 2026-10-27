# Action Types

All action types are used inside the `actions` section of an enchantment YAML file. Each action has a key of your choice (description placeholders refer to it, e.g. `{actions.head.amount}`) and a `type` field; the remaining fields are the action's parameters.

```yaml
actions:
  my_key:
    type: action_type_name
    chance: 0.5        # optional, common to every action, see below
    # ... parameters
```

- `actions` must be a section (key → action); a list is treated as no actions. Actions run in YAML order.
- If any action fails (a condition is not met, a probability misses, `chance` misses, or it throws), the **remaining actions are skipped**, the trigger does not start the cooldown, and the enchantment's sound and particles are not played. `cooldown` therefore means "cooldown after a successful trigger".
- An action with a missing parameter, an invalid value form or an unknown type is skipped when it triggers and logged; the other actions still run. The load-time check on startup and on `/enchants reload` reports these problems up front.
- The parameter tables below follow the parameter declarations in the source (`editor/enchant-schema.json` is exported from the same declarations). "Default" is the value the plugin uses when the parameter is omitted. Every optional parameter added in milk.13 defaults to the value that was hard-coded in milk.12, so leaving it out keeps the old behaviour.
- The config editor exposes 23 actions; it does not expose `command`.

## Per-level values (ScalingFunction)

Parameters typed ScalingFunction take a value per enchantment level, in one of four forms:

| Form | Meaning |
| --- | --- |
| `amount: 0.5` | Fixed value |
| `amount: {scaling: fixed, value: 0.5}` | Fixed value (legacy form, still supported) |
| `amount: {base: 0.1, per_level: 0.1}` | `base + per_level × (level − 1)` |
| `amount: [8, 16, 32, 64, 128]` | Per-level list (new in milk.13): level L uses item `min(L, length)` |

- When a list is shorter than the maximum level, higher levels use the last item and the plugin logs one warning per list. An empty list, or a list containing a non-number, is an error (the action is skipped).
- Parameters marked "integer" are truncated with `(int)` (towards zero) after evaluation; e.g. `{base: 10, per_level: 2.5}` is 12.5 at level 2 and 12 is used.
- Exception: tracking's `tracking.radius`, written as a list, overlays the default list `[1.5, 2.5, 3.5]` item by item (missing items keep the default); see [enchants/tracking.md](enchants/tracking.md).

## Common parameter `chance`

Every action accepts `chance` (ScalingFunction, 0–1, default `1.0`): a roll is made before the action runs, and a miss counts as the action failing — the remaining actions are skipped, no cooldown, no sound or particles — exactly like a probability action such as `drop_head` missing.

```yaml
actions:
  poison:
    type: potion_effect
    effect: poison
    duration: {base: 60, per_level: 40}
    amplifier: 0
    chance: [0.25, 0.5, 0.75]   # 25% / 50% / 75% at levels I / II / III
```

- Actions without `chance` are not rolled and behave exactly as in milk.12.
- A probability of 0 or less never passes; 1 or more always passes.
- An invalid `chance` (e.g. an empty list) skips that action.

## Action list

`potion_effect`, `bonus_damage`, `healthsteal`, `teleport`, `explosion`, `velocity`, `shockwave`, `break_armor`, `drop_head`, `attribute_modifier`, `multi_break`, `vacuum_drops`, `replant`, `multi_arrow`, `draw_accelerate`, `auto_bow`, `repair`, `hook`, `feast`, `deflect`, `till`, `reflect_damage`, `block_break`, `command` (not exposed in the editor).

---

### potion_effect

Applies a potion effect to the targets.

| Parameter | Type | Required | Default | Description |
| --- | --- | --- | --- | --- |
| `effect` | string | yes | -- | Potion effect name, e.g. `poison`, `strength` (case-insensitive) |
| `duration` | ScalingFunction (integer) | yes | -- | Duration in ticks (20 ticks = 1 second); `-1` = infinite |
| `amplifier` | ScalingFunction (integer) | yes | -- | Amplifier, 0 = level I, 1 = level II |
| `particles` | boolean | no | `true` | `false` applies the effect as ambient: fainter, fewer particles — it does **not** hide them |
| `icon` | boolean | no | `true` | `false` hides both the particles and the HUD icon |

> The real meaning of `particles` / `icon` comes from the Bukkit five-argument constructor `PotionEffect(type, duration, amplifier, ambient, particles)`: `particles` goes into the ambient slot and `icon` into the particles slot (the icon follows it). milk.13 keeps this behaviour and only documents it correctly.

```yaml
actions:
  poison:
    type: potion_effect
    effect: poison
    duration:
      base: 40
      per_level: 20
    amplifier:
      scaling: fixed
      value: 0
```

---

### bonus_damage

Adds damage to, or multiplies, the current damage event. Needs a damage event (`ATTACK_ENTITY` etc.).

| Parameter | Type | Required | Default | Description |
| --- | --- | --- | --- | --- |
| `amount` | ScalingFunction | yes | -- | Added to the damage; multiplies it when `multiplier` is true |
| `multiplier` | boolean | no | `false` | Multiply instead of add |
| `conditions.sneaking` | boolean | no | `false` | Only while sneaking |
| `conditions.night_time` | boolean | no | `false` | Only while the world time is between `night_start` and `night_end` |
| `conditions.night_start` | int | no | `13000` | Start of night (tick of day, 0–24000) |
| `conditions.night_end` | int | no | `23000` | End of night |
| `conditions.low_health` | double | no | `0.0` | When above 0, only while your health is at or below this value |
| `conditions.dimension` | string | no | -- | Bukkit world name (case-insensitive), e.g. `world_nether` |
| `conditions.passive_mobs` | boolean | no | `false` | Only animals, ambient mobs, fish, squids, snow golems, iron golems and dolphins |
| `conditions.target_types` | list | no | `[]` | Entity type enum names (upper case, e.g. `ZOMBIE`); empty = any |

```yaml
actions:
  extra_dmg:
    type: bonus_damage
    amount:
      base: 2.0
      per_level: 1.0
    conditions:
      night_time: true
```

---

### healthsteal

Heals you by a share of the current hit; the damage itself is unchanged. Needs a damage event.

| Parameter | Type | Required | Default | Description |
| --- | --- | --- | --- | --- |
| `amount` | ScalingFunction | yes | -- | Share of the damage (0.10 = 10%), before damage reduction |
| `max_fraction` | double | no | `1.0` | Cap: larger `amount` values are treated as this |

```yaml
actions:
  steal:
    type: healthsteal
    amount:
      base: 0.1
      per_level: 0.1
```

---

### teleport

Teleports the player along the look direction. It walks back from the maximum distance to find a safe spot: a standable block within a few blocks below, and passable feet and head space. With Skyllia compatibility on it never leaves the current island.

| Parameter | Type | Required | Default | Description |
| --- | --- | --- | --- | --- |
| `range` | ScalingFunction | yes | -- | Maximum distance in blocks |
| `step` | double | no | `1.0` | How far to step back when no spot is found |
| `min_distance` | double | no | `0.5` | Give up when stepping back below this distance |
| `ground_search_depth` | int | no | `4` | How many blocks below the spot may hold the standable block |

```yaml
actions:
  blink:
    type: teleport
    range:
      base: 5.0
      per_level: 3.0
```

---

### explosion

Creates an explosion. On `PROJECTILE_HIT` it uses the projectile's hit point (block centre for block hits), **ignores** the `target` filter, and the projectile must come from a bow/crossbow carrying an enchantment with this action (tagged when shot); other triggers use the first target's location.

| Parameter | Type | Required | Default | Description |
| --- | --- | --- | --- | --- |
| `power` | ScalingFunction | yes | -- | Vanilla explosion power (TNT is 4, a charged creeper 6) |
| `fire` | boolean | no | `false` | Set fire |
| `break_blocks` | boolean | no | `false` | Break blocks |

```yaml
actions:
  boom:
    type: explosion
    power:
      base: 2.0
      per_level: 1.0
```

---

### velocity

Applies a velocity (knockback, launch) to you or the target.

| Parameter | Type | Required | Default | Description |
| --- | --- | --- | --- | --- |
| `direction` | string | yes | -- | `UP`, `DOWN` (absolute vertical); `FORWARD`/`LOOK` (look direction); `BACKWARD` (opposite of look); `AWAY` (horizontally away from the target, needs a target) |
| `power` | ScalingFunction | yes | -- | Speed; the vertical component is then multiplied by `vertical_scale` |
| `apply_to` | string | no | `SELF` | `SELF`; any other value means `TARGET` (the event target) |
| `no_fall_damage` | boolean | no | `false` | For players, cancels the next fall damage within `no_fall_damage_ticks` |
| `on_ground` | boolean | no | `true` | The triggering player must be on the ground or within `ground_distance` blocks of a supporting block (checked for both `SELF` and `TARGET`). The old key `onGround` still works (the bundled leap.yml uses it); if both are present, `onGround` wins (as in milk.12) and the load-time check warns |
| `vertical_scale` | double | no | `0.25` | Multiplier for the vertical component (so `UP` with `power: 5` actually launches at 1.25) |
| `ground_distance` | int | no | `1` | Blocks checked below the feet for `on_ground` |
| `no_fall_damage_ticks` | int | no | `100` | Ticks the fall protection lasts (100 ticks = 5 s) |

```yaml
actions:
  launch:
    type: velocity
    direction: UP
    power:
      base: 0.8
      per_level: 0.3
    apply_to: SELF
    no_fall_damage: true
```

---

### shockwave

Pushes every living entity within the radius away from you at a launch angle, **ignoring** `target`. Closer entities are hit harder; the target's final `minecraft:knockback_resistance` reduces the impulse proportionally.

| Parameter | Type | Required | Default | Description |
| --- | --- | --- | --- | --- |
| `radius` | ScalingFunction | yes | -- | Radius in blocks; the editor warns above 10 (cross-region behaviour on Folia is unverified) |
| `power` | ScalingFunction | yes | -- | Impulse within `falloff_start` |
| `max_entities` | int | no | `64` | Only the nearest this many entities are affected |
| `max_power` | double | no | `16.0` | Impulse cap |
| `knockback_resistance_reduction` | double | no | `0.5` | Share removed at knockback resistance 1, linear in resistance |
| `falloff_start` | double | no | `1.0` | Further away the impulse is power ÷ (distance − start)^exponent |
| `falloff_exponent` | double | no | `2.0` | Falloff exponent |
| `launch_angle_degrees` | double | no | `45.0` | Launch angle |

```yaml
actions:
  push:
    type: shockwave
    radius:
      base: 5.0
      per_level: 2.0
    power:
      base: 1.0
      per_level: 0.5
```

---

### break_armor

Damages one random armour piece of the target player (Unbreaking applies). The target must be a player.

| Parameter | Type | Required | Default | Description |
| --- | --- | --- | --- | --- |
| `damage` | ScalingFunction (integer) | yes | -- | Durability damage |

```yaml
actions:
  shatter:
    type: break_armor
    damage:
      base: 10
      per_level: 10
```

---

### drop_head

Drops the first target's head with a probability.

| Parameter | Type | Required | Default | Description |
| --- | --- | --- | --- | --- |
| `amount` | ScalingFunction | yes | -- | Drop probability (0–1) |
| `heads` | map | no | see below | Entity type (upper-case enum name, optionally `minecraft:`) → dropped item; mobs not in the table drop nothing. Writing it replaces the default table |
| `player_heads` | boolean | no | `true` | Drop the victim's head when killing a player (same probability) |
| `player_head_success` | boolean | no | `false` | Whether dropping a player head counts as success. With `false` (milk.12 behaviour) it counts as a failure: no cooldown, no sound or particles, no further actions |

Default `heads`: `CREEPER: CREEPER_HEAD`, `ZOMBIE: ZOMBIE_HEAD`, `SKELETON: SKELETON_SKULL`, `WITHER_SKELETON: WITHER_SKELETON_SKULL`, `PIGLIN: PIGLIN_HEAD`, `ENDER_DRAGON: DRAGON_HEAD`.

```yaml
actions:
  head:
    type: drop_head
    amount:
      base: 0.1
      per_level: 0.1
```

---

### attribute_modifier

Writes an attribute modifier onto items carrying this enchantment. Usually used with `PASSIVE`; recalculated on equipment changes, hotbar switches, join, every PASSIVE period, and for online players after `/enchants reload`.

| Parameter | Type | Required | Default | Description |
| --- | --- | --- | --- | --- |
| `attribute` | string | yes | -- | Vanilla attribute registry name (minecraft namespace, case-insensitive), e.g. `movement_speed`, `max_health`, `scale` |
| `amount` | ScalingFunction | yes | -- | Modifier value; 0 adds nothing |
| `operation` | string | yes | -- | `ADD_NUMBER` adds; `ADD_SCALAR` adds a share of the base value; `MULTIPLY_SCALAR_1` multiplies the total by (1 + value) |
| `key` | string | yes | -- | Modifier key, stored as `attr_<enchant>_<key>`; unique within the enchantment |
| `conditions.equipped` | boolean | no | `false` | `true` limits the modifier to armour slots (ARMOR); `false` uses any slot (ANY), so it also works in hand |

> **Never rename `key` on a live enchantment**: old modifiers are removed by the current key only, so a renamed key leaves the old modifier on existing items, stacking with the new one. The editor only lets you set it when creating the action.

```yaml
actions:
  speed_boost:
    type: attribute_modifier
    attribute: movement_speed
    amount:
      base: 0.02
      per_level: 0.01
    operation: ADD_NUMBER
    key: speed
    conditions:
      equipped: true
```

---

### multi_break

Breaks a cube (including above and below) around the mined block. Each block fires a `BlockBreakEvent` first so protection plugins can deny it; container contents drop too. Needs the `BLOCK_BREAK` trigger.

| Parameter | Type | Required | Default | Description |
| --- | --- | --- | --- | --- |
| `radius` | ScalingFunction (integer) | yes | -- | Radius; 0 or negative does nothing; the editor warns above 2 (cross-region behaviour on Folia is unverified) |
| `match_tool` | boolean | no | `false` | Only break blocks the held tool is meant for (pickaxe/shovel/axe/hoe mineable tags) |
| `exclude` | list | no | `[]` | Skip entirely while the player is in any of these states, see below |
| `durability_per_block` | int | no | `2` | Tool durability charged per extra block (Unbreaking applies) |
| `conditions.sneaking` | boolean | no | `false` | Only while sneaking |

`exclude` is read by `multi_break` only (it is not a common parameter); values are case-insensitive:

| Value | Skipped while |
| --- | --- |
| `sneaking` | sneaking |
| `sprinting` | sprinting |
| `swimming` | swimming |
| `flying` | flying |
| `gliding` | gliding |

```yaml
actions:
  excavate:
    type: multi_break
    exclude:
      - sneaking
    radius:
      base: 1
      per_level: 1
```

---

### vacuum_drops

Cancels natural block drops and puts them straight into the player's inventory; what does not fit drops in place. The actual collection is done by a listener: it applies whenever the main hand, off hand or any armour piece carries an enchantment with this action, regardless of trigger and cooldown.

No parameters.

```yaml
actions:
  collect:
    type: vacuum_drops
```

---

### replant

Replants a harvested crop (taking one seed from the inventory) and can use bone meal to grow it instantly. Needs the `BLOCK_BREAK` trigger.

| Parameter | Type | Required | Default | Description |
| --- | --- | --- | --- | --- |
| `fertilizer` | boolean | no | `false` | Grow instantly when the inventory holds at least `bone_meal` bone meal |
| `bone_meal` | int | no | `5` | Bone meal consumed (off hand first) |
| `delay_ticks` | int | no | `3` | Ticks to wait after harvesting before replanting |
| `crops` | map | no | see below | Crop block → `{seed: seed item, soil: [soil blocks]}`; only listed crops with growth stages are handled. Writing it replaces the default table |

Default `crops`:

```yaml
crops:
  WHEAT: {seed: WHEAT_SEEDS, soil: [FARMLAND]}
  CARROTS: {seed: CARROT, soil: [FARMLAND]}
  POTATOES: {seed: POTATO, soil: [FARMLAND]}
  BEETROOTS: {seed: BEETROOT_SEEDS, soil: [FARMLAND]}
  NETHER_WART: {seed: NETHER_WART, soil: [SOUL_SAND, SOUL_SOIL]}
```

```yaml
actions:
  plant:
    type: replant
    fertilizer: true
```

---

### multi_arrow

Fires extra arrows after the main one. Use with `BOW_SHOOT`; works for bows and crossbows. Extra arrows inherit the original arrow's direction, initial speed, critical flag, piercing, fire, potion effects and the shooting weapon (keeping Power, Flame and similar bonuses), and cannot be picked up.

| Parameter | Type | Required | Default | Description |
| --- | --- | --- | --- | --- |
| `extra_arrows` | ScalingFunction (integer) | yes | -- | Number of extra arrows |
| `spread` | ScalingFunction | yes | -- | Maximum angle from the main arrow (degrees), capped at `max_spread_degrees` |
| `max_spread_degrees` | double | no | `60.0` | Spread cap |
| `default_speed` | double | no | `3.0` | Arrow speed when the original arrow is unavailable (not triggered by a shoot event) |
| `interval_ticks` | int | no | `1` | The i-th extra arrow fires after i × interval ticks |

```yaml
actions:
  volley:
    type: multi_arrow
    extra_arrows:
      base: 1
      per_level: 1
    spread:
      base: 5
      per_level: 2
```

---

### draw_accelerate

Advances the draw progress by extra ticks every tick while drawing, so the bow reaches vanilla full power earlier.

| Parameter | Type | Required | Default | Description |
| --- | --- | --- | --- | --- |
| `extra_ticks` | ScalingFunction | no | `1.0` | Extra draw ticks per tick, rounded down; below 1 does nothing |

```yaml
actions:
  accelerate:
    type: draw_accelerate
    extra_ticks:
      base: 1
      per_level: 1
```

**Notes:**
- Bows only (not crossbows). Use it with the `RIGHT_CLICK` trigger.
- The client draw animation is unchanged; only the server-side remaining use time is reduced. Full power is reached in about `20 / (1 + extra_ticks)` ticks (20 ticks is the vanilla full draw and is not configurable here).
- Used alone, releasing early still produces the speed of the accelerated progress.
- Combined with `auto_bow`, the latter sees full draw earlier and fires automatically.

---

### auto_bow

After drawing starts, reads the actual draw progress every tick and releases automatically **only at full draw**, optionally starting the next draw. It does not change draw speed.

| Parameter | Type | Required | Default | Description |
| --- | --- | --- | --- | --- |
| `refire` | boolean | no | `true` | Start the next draw automatically |
| `refire_delay` | int | no | `0` | Ticks between shots |
| `max_shots` | int | no | `0` | Maximum shots, 0 = unlimited (until right click is released) |
| `full_draw_ticks` | int | no | `20` | Fire once the actual draw reaches this many ticks; the vanilla bow is fully drawn at 20, a smaller value fires early and the arrow speed still follows the actual draw |

```yaml
actions:
  auto:
    type: auto_bow
    refire: true
    refire_delay: 0
    max_shots: 0
```

**Notes:**
- Bows only (not crossbows). Use it with the `RIGHT_CLICK` trigger.
- Power uses the vanilla formula `power = (t² + 2t) / 3` with `t = used ticks / full_draw_ticks`; it fires only at `power = 1.0`.
- Firing calls the NMS `releaseUsingItem()`, so vanilla ammo consumption, Infinity, Power/Punch/Flame and `EntityShootBowEvent` are preserved (so `BOW_SHOOT` actions such as `multi_arrow` still apply).
- The task stops on release, item or hand switch, death or quit. With `refire_delay > 0` the server may miss a release during the delay; keep it at 0.

---

### repair

Rolls once per damaged item carrying this enchantment in the main hand, off hand and armour, and repairs the items that hit. Usually used with `PASSIVE`.

| Parameter | Type | Required | Default | Description |
| --- | --- | --- | --- | --- |
| `amount` | ScalingFunction | yes | -- | Repair probability per item and trigger (0–1) |
| `exp_mode` | boolean | no | `false` | Charge experience per repair; stop when there is not enough |
| `repair_amount` | int | no | `1` | Durability restored per successful roll (never more than the damage) |
| `exp_cost` | int | no | `1` | Experience points charged per repair when `exp_mode` is on |

```yaml
actions:
  mending:
    type: repair
    amount:
      base: 0.4
      per_level: 0.2
    exp_mode: true
```

---

### hook

Pulls the first target towards you at a launch angle.

| Parameter | Type | Required | Default | Description |
| --- | --- | --- | --- | --- |
| `power` | ScalingFunction | yes | -- | Pull strength |
| `min_distance` | double | no | `1.0` | No pull when the target is this close or closer |
| `full_power_distance` | double | no | `5.0` | Full power at or beyond this distance; closer it is power ÷ (full distance − distance)² |
| `launch_angle_degrees` | double | no | `45.0` | Launch angle |

```yaml
actions:
  pull:
    type: hook
    power:
      base: 1.0
      per_level: 0.5
```

---

### feast

When food is not full, restores food and saturation with a probability.

| Parameter | Type | Required | Default | Description |
| --- | --- | --- | --- | --- |
| `amount` | ScalingFunction | yes | -- | Trigger probability (0–1) |
| `food` | int | no | `1` | Food restored (capped at 20) |
| `saturation` | double | no | `1.0` | Saturation restored, never above the new food level |

```yaml
actions:
  feed:
    type: feast
    amount:
      base: 0.2
      per_level: 0.2
```

---

### deflect

When a projectile hits you, cancels the damage with a probability and bounces the projectile away at its original speed (rotated randomly around the reverse direction). Use with `TAKE_DAMAGE`; only projectile damage to players counts.

| Parameter | Type | Required | Default | Description |
| --- | --- | --- | --- | --- |
| `amount` | ScalingFunction | no | `1.0` | Deflection probability (0–1) |
| `yaw_spread_degrees` | double | no | `360.0` | Random horizontal rotation from 0 to this angle |
| `pitch_spread_degrees` | double | no | `30.0` | Random vertical rotation within ± this angle |

> Up to milk.12 the plugin ignored `amount` and always deflected. From milk.13 `amount` applies: when omitted it is still 1.0 (always deflect); the bundled `deflection.yml` uses `0.2 + 0.2/level`, i.e. 20% / 40% / 60%. This is a balance change; deflection is not live on the production server.

```yaml
actions:
  block:
    type: deflect
    amount:
      base: 0.2
      per_level: 0.2
```

---

### till

On right click, converts blocks within the radius into other blocks, on the clicked block's layer only. The default is "hoe tills the ground"; each enchantment can declare its own table with `transforms`. On Folia only blocks owned by the current region are changed.

| Parameter | Type | Required | Default | Description |
| --- | --- | --- | --- | --- |
| `radius` | ScalingFunction (integer) | yes | -- | Square radius, 0 = clicked block only; the editor warns above 4 |
| `transforms` | map | no | -- | Source spec → target spec, see the DSL below |
| `inherit_defaults` | boolean | no | `true` | Also apply the vanilla tilling table (dirt/grass/podzol/mycelium → farmland, coarse/rooted dirt → dirt) |
| `require_item_types` | list | no | -- | Allowed main hand materials, e.g. `[IRON_HOE, DIAMOND_HOE]`; a single value also works |
| `require_item_tags` | list | no | -- | Allowed item tags, e.g. `[minecraft:hoes]`; a single value also works |
| `consume_durability` | boolean | no | `true` | Charge durability per converted block |
| `durability_per_block` | int | no | `1` | Durability charged per converted block (Unbreaking applies) |
| `protection_check` | boolean | no | `true` | Fire a `PlayerInteractEvent` per block and skip blocks a protection plugin denies |
| `cancel_vanilla` | boolean | no | `true` | Cancel the vanilla right click when the clicked block is in the table or something was converted |
| `drop_roots` | boolean | no | `true` | Drop hanging roots when rooted dirt is converted (as vanilla does) |
| `apply_physics` | boolean | no | `true` | Trigger block/redstone updates when writing the block |

> Without `require_item_types` / `require_item_tags` the legacy behaviour is kept: the main hand item name must contain `HOE`.

#### `transforms` DSL

Key = **source spec**, value = **target spec**; both accept an optional block state section:

```text
source := (material | "#tag") [ "[" states "]" ]
target := material [ "[" states "]" ]
states := entry ("," entry)*
entry  := property=value      # both sides
        | same                # target side only, the `same` generator
```

**Source side: subset matching**

| Form | Meaning |
| --- | --- |
| `ICE` | Any state of that material (legacy behaviour) |
| `WATER[level=0]` | Only `level=0`; other properties are not compared |
| `OAK_STAIRS[facing=east, half=top]` | Multiple properties are ANDed |
| `#minecraft:wooden_stairs[facing=east]` | Tag expanded, every material matched with that constraint |

Unlisted properties do not participate in matching. Property names/values are validated by the server parser, so typos fail at config load time.

**Target side: default state / explicit state / `same` generator**

| Form | Resulting BlockData |
| --- | --- |
| `EXPOSED_COPPER` | Target default state (same as the old `setType`) |
| `OAK_SLAB[type=top]` | Explicit state, unlisted properties keep the target default |
| `EXPOSED_CUT_COPPER_STAIRS[same]` | Copy every property name the target also has |
| `COPPER_BULB[same, lit=false]` | `same` first, then literal overrides win |

The `same` generator is the Bukkit-side equivalent of `Block#withPropertiesOf`:

- properties are matched by **name** (source `facing/half/shape/waterlogged` are all copied when the target has them);
- source properties the target does not have are silently dropped; source defaults are replaced by target defaults;
- if the generated BlockData is rejected, it degrades to literal-only overrides, then to the target default state, warning once per `(from -> to)` pair.

`same` is only allowed on the target side; using it as a source errors at load time.

**Priority (within one material)**

1. rules with more state constraints win;
2. on equal constraint count, explicit `transforms` entries win over the `inherit_defaults` table;
3. otherwise YAML declaration order wins.

The first matching rule is applied; there is no chaining. So this is valid: still water freezes, other water levels stay untouched.

```yaml
transforms:
  "WATER[level=0]": ICE
  WATER: WATER
```

> Multiple enchants may each keep their own `transforms` table. Keep tables disjoint: enchants are executed one
> after another and their order is not guaranteed; the plugin warns on startup and on `/enchants reload` about overlapping entries whose
> state constraints are compatible.

```yaml
actions:
  farm:
    type: till
    radius:
      base: 1
      per_level: 1
```

```yaml
# State aware: still water only; keep stair orientation when swapping wood types
actions:
  work:
    type: till
    radius: 0
    inherit_defaults: false
    transforms:
      "WATER[level=0]": ICE
      "#minecraft:wooden_stairs": "OAK_STAIRS[same]"
      CUT_COPPER_STAIRS: "EXPOSED_CUT_COPPER_STAIRS[same]"
      COPPER_BULB: "EXPOSED_COPPER_BULB[same, lit=false]"
    require_item_tags: [minecraft:axes]
```

---

### reflect_damage

When melee damage hits you, cancels it with a probability and damages the attacker. Melee only (the attacker must be a living entity; projectiles do not count).

| Parameter | Type | Required | Default | Description |
| --- | --- | --- | --- | --- |
| `percentage` | ScalingFunction | yes | -- | Reflection probability (0–1) |
| `damage_ratio` | double | no | `1.0` | Damage dealt to the attacker = the hit × ratio |

```yaml
actions:
  reflect:
    type: reflect_damage
    percentage:
      base: 0.10
      per_level: 0.05
```

---

### block_break

Breaks the left-clicked block instantly (use with `BLOCK_DIG`), firing a `BlockBreakEvent` first so protection plugins can deny it. Drops honour Fortune and Silk Touch and go straight to the inventory when `vacuum_drops` is equipped. Blocks with hardness below 0 are ignored.

| Parameter | Type | Required | Default | Description |
| --- | --- | --- | --- | --- |
| `target_types` | list | no | `[]` | Breakable blocks: material names (`GLASS`), block tags (with a colon, `minecraft:impermeable`) or `includes:fragment` (material name contains the fragment); empty = any |
| `durability` | int | no | `1` | Tool durability charged per block (Unbreaking applies) |
| `conditions.sneaking` | boolean | no | `false` | Only while sneaking |

```yaml
actions:
  shatter:
    type: block_break
    target_types:
      - "includes:glass"
```

---

### command

Runs a command as the player or the console. **The editor does not expose this action** (`run_as: CONSOLE` amounts to console access); the plugin still runs `command` actions found in existing configs.

| Parameter | Type | Required | Default | Description |
| --- | --- | --- | --- | --- |
| `command` | string | yes | -- | Command without the leading `/`; supports the placeholders below and PlaceholderAPI |
| `run_as` | string | no | `PLAYER` | `PLAYER` or `CONSOLE` |

| Placeholder | Replaced with |
| --- | --- |
| `{player}` | Player name |
| `{target}` | Target name (player name, otherwise the lower-case entity type; empty without a target) |
| `{level}` | Enchantment level |

```yaml
actions:
  reward:
    type: command
    run_as: CONSOLE
    command: "give {player} diamond 1"
```

`PLAYER` runs with the player's own permissions; a command that reports failure makes the action fail.

---

## Enchantment-level sound and particles

`sound` / `particle` sit next to `trigger` and `target`, not inside `actions`:

```yaml
sound:
  type: block.lava.extinguish   # vanilla sound registry key
  volume: 1.0                   # default 1.0
  pitch: 1.2                    # default 1.0
  location: BLOCK               # SELF (default) / TARGET / BLOCK

particle:
  type: cloud                   # vanilla particle key or Bukkit enum name
  count: 12                     # default 10
  spread: 0.4                   # default 0.5
  speed: 0.05                   # default 0.1
  location: BLOCK               # default SELF
  data:                         # for particles that need data: material / color / to_color / size (default 1.0) / value
    material: "minecraft:nether_wart_block"
```

- `location`: `SELF` = player position, `TARGET` = first target's position, `BLOCK` = centre of the interacted/broken block; falls back to the player. Particles spawn one block higher.
- The sound uses `Player#playSound(Location, ...)`: **only the triggering player receives it**, positioned at the `location` world coordinate.
- Sound and particles play only after every action succeeded. Unknown sound or particle names are reported at load time and not played.
