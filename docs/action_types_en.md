# Action Types
All action types available for use in enchantment YAML files. Each action is defined inside the `actions` section with a unique key and a `type` field.
```yaml
actions:
  my_key:
    type: action_type_name
    # ... parameters
```
#### Universal `exclude` parameter
Any action type supports an optional `exclude` list. If the player's current state matches any entry, that action is skipped entirely for that trigger.
```yaml
actions:
  my_key:
    type: multi_break
    exclude:
      - sneaking
      - sprinting
    # ... other parameters
```
| Value       | Condition skipped when... |
| ----------- | ------------------------- |
| `sneaking`  | `player.isSneaking`       |
| `sprinting` | `player.isSprinting`      |
| `swimming`  | `player.isSwimming`       |
| `flying`    | `player.isFlying`         |
| `gliding`   | `player.isGliding`        |
***
### potion\_effect
Applies a potion effect to the target.
| Parameter   | Type            | Required | Default | Description                                                           |
| ----------- | --------------- | -------- | ------- | --------------------------------------------------------------------- |
| `effect`    | string          | yes      | --      | Potion effect type (lowercase, e.g. `poison`, `strength`, `slowness`) |
| `duration`  | ScalingFunction | yes      | --      | Duration in ticks (20 ticks = 1 second)                               |
| `amplifier` | ScalingFunction | yes      | --      | Effect amplifier (0 = level I, 1 = level II, etc.)                    |
| `particles` | boolean         | no       | `true`  | Show potion particles                                                 |
| `icon`      | boolean         | no       | `true`  | Show effect icon on HUD                                               |
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
    particles: true
    icon: true
```
***
### bonus\_damage
Deals additional damage or multiplies existing damage.
| Parameter                 | Type            | Required | Default | Description                                           |
| ------------------------- | --------------- | -------- | ------- | ----------------------------------------------------- |
| `amount`                  | ScalingFunction | yes      | --      | Damage amount (or multiplier if `multiplier` is true) |
| `multiplier`              | boolean         | no       | `false` | If true, `amount` multiplies the existing damage      |
| `conditions.sneaking`     | boolean         | no       | `false` | Only activate while sneaking                          |
| `conditions.night_time`   | boolean         | no       | `false` | Only activate at night                                |
| `conditions.low_health`   | double          | no       | `0.0`   | Only activate below this health                       |
| `conditions.dimension`    | string          | no       | --      | Only activate in this dimension (e.g. `the_nether`)   |
| `conditions.target_types` | list            | no       | --      | Only activate against these entity types              |
| `conditions.passive_mobs` | boolean         | no       | `false` | Only activate against passive mobs                    |
```yaml
actions:
  extra_dmg:
    type: bonus_damage
    amount:
      base: 2.0
      per_level: 1.0
    multiplier: false
    conditions:
      night_time: true
```
***
### healthsteal
Steals health from the target and heals the player.
| Parameter | Type            | Required | Default | Description            |
| --------- | --------------- | -------- | ------- | ---------------------- |
| `amount`  | ScalingFunction | yes      | --      | Fraction of damage to steal as health (e.g. 0.10 = 10%) |
```yaml
actions:
  steal:
    type: healthsteal
    amount:
      base: 0.1
      per_level: 0.1
```
***
### teleport
Teleports the player forward in the direction they're looking.
The action searches backward from the maximum distance for a safe landing spot with
standable ground and passable space for the player's feet and head.
| Parameter | Type            | Required | Default | Description                         |
| --------- | --------------- | -------- | ------- | ----------------------------------- |
| `range`   | ScalingFunction | yes      | --      | Maximum teleport distance in blocks |
```yaml
actions:
  blink:
    type: teleport
    range:
      base: 5.0
      per_level: 3.0
```
***
### explosion
Creates an explosion at the target's location.
| Parameter      | Type            | Required | Default | Description     |
| -------------- | --------------- | -------- | ------- | --------------- |
| `power`        | ScalingFunction | yes      | --      | Explosion power |
| `fire`         | boolean         | no       | `false` | Create fire     |
| `break_blocks` | boolean         | no       | `false` | Destroy blocks  |
```yaml
actions:
  boom:
    type: explosion
    power:
      base: 2.0
      per_level: 1.0
    fire: false
    break_blocks: false
```
***
### velocity
Applies velocity (knockback/launch) to the player or target.
`onGround` is enabled by default. When enabled, the triggering player must be on
the ground or within roughly one block of a supporting block. Disable it to allow
airborne execution. This check applies to both `SELF` and `TARGET`.
| Parameter        | Type            | Required | Default | Description                                                    |
| ---------------- | --------------- | -------- | ------- | -------------------------------------------------------------- |
| `direction`      | string          | yes      | --      | Direction: `UP`, `DOWN`, `FORWARD`, `BACKWARD`, `LOOK`, `AWAY` |
| `power`          | ScalingFunction | yes      | --      | Velocity magnitude                                             |
| `apply_to`       | string          | no       | `SELF`  | Who to apply to: `SELF` or `TARGET`                            |
| `onGround`       | boolean         | no       | `true`  | Require the triggering player to be near the ground            |
| `no_fall_damage` | boolean         | no       | `false` | Cancel the next fall damage event caused by this launch        |
**Directions:**
* `UP` / `DOWN` — absolute vertical
* `FORWARD` / `BACKWARD` — along the player's look direction
* `LOOK` — same as `FORWARD`
* `AWAY` — away from the target (requires a resolved target)
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
***
### shockwave
Pushes nearby entities away from the player. The target's final
`minecraft:knockback_resistance` attribute value linearly reduces the impulse from
0% at resistance 0 to 50% at resistance 1.
| Parameter | Type            | Required | Default | Description             |
| --------- | --------------- | -------- | ------- | ----------------------- |
| `radius`  | ScalingFunction | yes      | --      | Effect radius in blocks |
| `power`   | ScalingFunction | yes      | --      | Knockback power         |
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
***
### break\_armor
Damages a random piece of the target player's armor.
| Parameter | Type            | Required | Default | Description                |
| --------- | --------------- | -------- | ------- | -------------------------- |
| `damage`  | ScalingFunction | yes      | --      | Durability damage to apply |
```yaml
actions:
  shatter:
    type: break_armor
    damage:
      base: 10
      per_level: 10
```

***
### drop\_head
Drops the killed entity's head as an item.
| Parameter | Type            | Required | Default | Description            |
| --------- | --------------- | -------- | ------- | ---------------------- |
| `amount`  | ScalingFunction | yes      | --      | Possibility to drop head |
```yaml
actions:
  head:
    type: drop_head
    amount:
      base: 0.01
      per_level: 0.01
```
***
### attribute\_modifier
Applies a persistent attribute modifier while the item is equipped. Used with PASSIVE trigger.
| Parameter   | Type            | Required | Default | Description                                                                                                                 |
| ----------- | --------------- | -------- | ------- | --------------------------------------------------------------------------------------------------------------------------- |
| `attribute` | string          | yes      | --      | Attribute name: `MAX_HEALTH`, `MOVEMENT_SPEED`, `ATTACK_DAMAGE`, `JUMP_STRENGTH`, `KNOCKBACK_RESISTANCE`, `ARMOR_TOUGHNESS` |
| `amount`    | ScalingFunction | yes      | --      | Modifier amount                                                                                                             |
| `operation` | string          | yes      | --      | `ADD_NUMBER`, `ADD_SCALAR`, or `MULTIPLY_SCALAR_1`                                                                          |
| `key`       | string          | yes      | --      | Unique modifier key                                                                                                         |
```yaml
actions:
  speed_boost:
    type: attribute_modifier
    keep_old_attribute: true
    attribute: "MOVEMENT_SPEED"
    amount:
      base: 0.02
      per_level: 0.01
    operation: "ADD_NUMBER"
    key: "speed"
```
**Note:** Attribute modifiers are automatically removed when the item is unequipped.
***
### multi\_break
Breaks blocks in a radius around the mined block (3x3, 5x5 pattern).
| Parameter | Type            | Required | Default | Description                                                             |
| --------- | --------------- | -------- | ------- | ----------------------------------------------------------------------- |
| `radius`  | ScalingFunction | yes      | --      | Break radius                                                            |
| `exclude` | list            | no       | `[]`    | Player states that skip this action (see Universal `exclude` parameter) |
| `conditions.sneaking` | boolean | no   | `false` | Only activate while sneaking                          |
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
***
### vacuum\_drops
Cancels natural block drops and delivers them directly to the player's inventory. Any items that don't fit in the inventory are dropped at the block's location. Only works with `BLOCK_BREAK` trigger.
No parameters.
```yaml
actions:
  collect:
    type: vacuum_drops
```
***
### replant
Automatically replants crops after harvesting.
Works with wheat, carrots, potatoes, beetroots, and nether wart.

| Parameter    | Type    | Required | Default | Description                                                        |
| ------------ | ------- | -------- | ------- | ------------------------------------------------------------------ |
| `fertilizer` | boolean | no       | `false` | If true, consumes 5 bone meal when available and replants as mature |

```yaml
actions:
  plant:
    type: replant
    fertilizer: true
```
***
### multi\_arrow
Shoots additional arrows alongside the main arrow.
Requires the `BOW_SHOOT` trigger and works with both bows and crossbows; the extra
arrows inherit critical hits, piercing, fire, potion effects and the firing weapon
(keeping Power, Flame and similar enchantment bonuses).
| Parameter      | Type            | Required | Default | Description                  |
| -------------- | --------------- | -------- | ------- | ---------------------------- |
| `extra_arrows` | ScalingFunction | yes      | --      | Number of extra arrows       |
| `spread`       | ScalingFunction | yes      | --      | Spread angle of extra arrows |
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
***
### draw\_accelerate

Advances the draw progress by extra ticks every tick while drawing, so the bow reaches vanilla full power earlier.

| Parameter | Type | Required | Default | Description |
| --- | --- | --- | --- | --- |
| `extra_ticks` | ScalingFunction | No | `1.0` | Extra draw ticks applied every tick |

```yaml
actions:
  accelerate:
    type: draw_accelerate
    extra_ticks:
      base: 1
      per_level: 1
```

**Notes:**
- Bows only. Use it with the `RIGHT_CLICK` trigger.
- The client draw animation is unchanged; only the server-side remaining use time is reduced. Full power is reached in about `20 / (1 + extra_ticks)` ticks.
- Combined with `auto_bow`, the latter sees `power = 1.0` earlier and fires automatically.
- If used alone, releasing early still produces full-power speed based on the accelerated progress.

---

### auto\_bow

After the bow starts drawing, checks the actual draw progress every tick and releases automatically **only when the bow is fully drawn (20 vanilla ticks)**. Optionally starts the next draw. This action does not change draw speed.

| Parameter | Type | Required | Default | Description |
| --- | --- | --- | --- | --- |
| `refire` | boolean | No | `true` | Start the next draw automatically |
| `refire_delay` | int | No | `0` | Ticks between shots |
| `max_shots` | int | No | `0` | Maximum shots, 0 = unlimited |

```yaml
actions:
  auto:
    type: auto_bow
    refire: true
```

**Notes:**
- Bows only. Use it with the `RIGHT_CLICK` trigger.
- Power uses the vanilla formula: `power = ((t² + 2t) / 3)` with `t = used ticks / 20`; it fires only at `power = 1.0`.
- It does not accelerate drawing or modify the remaining use time.
- Firing calls `releaseUsingItem()` (NMS), so vanilla ammo consumption, Infinity, Power/Punch/Flame and `EntityShootBowEvent` are preserved.
- Tasks stop on release, item switch, death or quit.

---

### repair
Restore the item's durability.
| Parameter | Type            | Required | Default | Description                        |
| --------- | --------------- | -------- | ------- | ---------------------------------- |
| `amount`  | ScalingFunction | yes      | --      | Posibility to restore 1 Durability per activation |
```yaml
actions:
  repair:
    type: repair
    amount:
      base: 0.4
      per_level: 0.2
  exp_mode: true
```
***
### hook
Pulls the target entity toward the player (grapple).
| Parameter | Type            | Required | Default | Description   |
| --------- | --------------- | -------- | ------- | ------------- |
| `power`   | ScalingFunction | yes      | --      | Pull strength |
```yaml
actions:
  pull:
    type: hook
    power:
      base: 1.0
      per_level: 0.5
```
***
### feast
Restores hunger/saturation to the player.
| Parameter | Type            | Required | Default | Description            |
| --------- | --------------- | -------- | ------- | ---------------------- |
| `amount`  | ScalingFunction | yes      | --      | Possibility to restore 1 Hunger value and Saturation |
```yaml
actions:
  feed:
    type: feast
    amount:
      base: 0.2
      per_level: 0.2
```
***
### deflect
Deflects incoming projectiles (arrows, etc.) away from the player.
No parameters. Use with TAKE\_DAMAGE trigger.
```yaml
actions:
  block:
    type: deflect
    amount:
      base: 0.2
      per_level: 0.2
```
***
### gravity
Pulls nearby entities toward the player.
| Parameter | Type            | Required | Default | Description   |
| --------- | --------------- | -------- | ------- | ------------- |
| `radius`  | ScalingFunction | yes      | --      | Pull radius   |
| `power`   | ScalingFunction | yes      | --      | Pull strength |
```yaml
actions:
  pull:
    type: gravity
    radius:
      base: 5
      per_level: 2
    power:
      base: 0.5
      per_level: 0.2
```
***
### till
Tills dirt/grass blocks in a radius when using a hoe. Each enchant can ship its own conversion table via `transforms`.

| Parameter | Type | Required | Default | Description |
| --- | --- | --- | --- | --- |
| `radius` | ScalingFunction | yes | -- | Radius, 0 = clicked block only |
| `transforms` | map | no | -- | Source spec -> target spec, see the DSL below |
| `inherit_defaults` | boolean | no | `true` | Also apply the vanilla farmland table (dirt/grass/podzol/mycelium -> farmland, coarse/rooted dirt -> dirt) |
| `require_item_types` | list | no | -- | Allowed item materials, e.g. `[IRON_HOE, DIAMOND_HOE]` |
| `require_item_tags` | list | no | -- | Allowed item tags, e.g. `[minecraft:hoes]` |
| `consume_durability` | boolean | no | `true` | Consume 1 durability per converted block |
| `protection_check` | boolean | no | `true` | Simulate `PlayerInteractEvent` per block so protection plugins can deny it |
| `cancel_vanilla` | boolean | no | `true` | Cancel the vanilla right click once at least one block changed |
| `drop_roots` | boolean | no | `true` | Drop hanging roots when rooted dirt -> dirt |
| `apply_physics` | boolean | no | `true` | Trigger block/redstone updates when writing the block (same as `setType`) |

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
> after another and their order is not guaranteed; the plugin warns at `onEnable` about overlapping entries whose
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
***
### reflect\_damage
Reflects a incoming melee damage fully back to the attacker.
| Parameter    | Type            | Required | Default | Description                                     |
| ------------ | --------------- | -------- | ------- | ----------------------------------------------- |
| `percentage` | ScalingFunction | yes      | --      | Possibility to reflect damage (e.g. 0.10 = 10%) |
```yaml
actions:
  reflect:
    type: reflect_damage
    percentage:
      base: 0.10
      per_level: 0.05
```
**Note:** Only works with melee damage (requires `EntityDamageByEntityEvent`).
***
### command
Runs a server command as the player or as the console.
| Parameter | Type   | Required | Default  | Description                                                  |
| --------- | ------ | -------- | -------- | ------------------------------------------------------------ |
| `command` | string | yes      | --       | Command to run (without leading `/`). Supports placeholders. |
| `run_as`  | string | no       | `PLAYER` | Who runs the command: `PLAYER` or `CONSOLE`                  |
**Placeholders:**
| Placeholder | Replaced with                                   |
| ----------- | ----------------------------------------------- |
| `{player}`  | The player's name                               |
| `{target}`  | The target entity's name (empty string if none) |
| `{level}`   | The enchantment level                           |
```yaml
actions:
  announce:
    type: command
    run_as: CONSOLE
    command: "say {player} triggered the enchant at level {level}!"
```
```yaml
actions:
  reward:
    type: command
    run_as: CONSOLE
    command: "give {player} diamond 1"
```
**Note:** `PLAYER` runs the command with the player's own permissions. Use `CONSOLE` for commands that require operator-level access.
***
### send\_message
Sends a MiniMessage-formatted message to the player or to nearby players.
| Parameter | Type   | Required | Default | Description                                                               |
| --------- | ------ | -------- | ------- | ------------------------------------------------------------------------- |
| `message` | string | yes      | --      | MiniMessage text. Supports `{player}`, `{target}`, `{level}` placeholders |
| `radius`  | double | no       | `0.0`   | If > 0, broadcasts to all players within this radius                      |
```yaml
actions:
  notify:
    type: send_message
    message: "<gold>{player}</gold> <gray>hit</gray> <red>{target}</red> <gray>with a level {level} enchant!"
    radius: 0.0
```
**Notes:**
* `{player}` is replaced with the enchant-user's name.
* `{target}` is replaced with the target entity's name, or empty string if no target.
* `{level}` is replaced with the enchantment level.
* When `radius > 0`, all online players within that radius receive the message.
***
### durability
Damages a specific equipment slot's item by a configurable amount. Respects Unbreaking enchantment via the server's standard damage calculation.
| Parameter | Type            | Required | Default | Description                                                                      |
| --------- | --------------- | -------- | ------- | -------------------------------------------------------------------------------- |
| `amount`  | ScalingFunction | yes      | --      | Durability damage to apply (minimum 1)                                           |
| `slot`    | string          | no       | `HAND`  | Equipment slot: `HAND`, `MAIN_HAND`, `OFF_HAND`, `HEAD`, `CHEST`, `LEGS`, `FEET` |
| `exclude` | list            | no       | `[]`    | Player states that skip this action (see Universal `exclude` parameter)          |
```yaml
actions:
  durability_cost:
    type: durability
    exclude:
      - sneaking
    amount:
      base: 1
      per_level: 1
    slot: HAND
```
**Notes:**
* `HAND` and `MAIN_HAND` both refer to the main hand slot.
* The damage respects Unbreaking — the actual durability loss may be lower depending on the item's Unbreaking level.
* If the slot is empty or the item is indestructible, nothing happens.
