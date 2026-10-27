# MeowsEnchants

[中文](README.md)

MeowsEnchants is a Paper/Folia custom enchantment plugin for declaring enchantments in YAML and registering them into the vanilla enchantment registry during the Paper plugin bootstrap phase.

## 1. Feature Overview

MeowsEnchants registers custom enchantments into the server like vanilla enchantments, instead of imitating enchantments through NBT, PersistentDataContainer data, or item lore like many traditional custom enchantment plugins.

Compared with common NBT- or lore-based custom enchantment plugins, this plugin is different in several ways:

- Custom enchantments are registered into the vanilla registry. They behave like real vanilla enchantments and can be accessed directly through the Bukkit API, so this plugin does not need to provide a separate API.
- Registered enchantments can be obtained directly from the creative inventory, vanilla `/minecraft:enchant`, or other plugins' `/enchant` commands, and their properties can be read correctly.
- Registered enchantments support vanilla-style conflicts, applicable items, anvil costs, and other registry properties, making them naturally compatible with other enchantment-based plugins.
- Enchantment effects are composed declaratively with triggers, targets, and actions instead of hardcoding a separate Java implementation for each enchantment.
- Plugin listeners independently control the logic and probability for each enchantment to appear in villager trades and enchanting table rolls.

## 2. Server Compatibility

- Target server: Paper/Folia `1.21.11 - 26.2+`
- Java: `25`
- Plugin type: Paper plugin using the `bootstrapper` field in `paper-plugin.yml`
- Folia: full support
- Optional dependency: Residence (the tracking enchantment skips targets the shooter may not hurt inside a claim; skipped when Residence is absent)

| Server / Version                               | Compatibility  | Result                                                                                                           |
|------------------------------------------------|----------------|------------------------------------------------------------------------------------------------------------------|
| Folia `1.21.11 - 26.2+`                        | ✅Supported     | Primary target environment; adapted for the Folia scheduler model.                                               |
| Paper `1.21.11 - 26.2+`                        | ✅Supported     | Falls back to the normal Paper scheduler, but still requires Paper plugin bootstrapper and Registry API support. |
| Paper/Folia `1.21.10` and lower                | ❌Not supported | Missing experimental APIs used by the plugin: `RegistryComposeEvent` and required `ItemTypeTagKeys`.             |
| Spigot / CraftBukkit                           | ❌Not supported | They do not provide the Paper plugin bootstrapper or Paper Registry Mutation API.                                |
| Paper downstreams such as Purpur, Leaf, Leaves | ✅Supported     | Supported if the fork keeps the corresponding experimental Paper API intact.                                     |

Paper's Registry Mutation API is experimental. Future Paper/Folia updates may change the API shape; please report issues if they occur.

## 3. How It Works

### Enchantment Registration

During the bootstrap phase, the plugin reads `plugins/MeowsEnchants/enchants/*.yml`.
Each enchantment has its own YAML file and declares:

- Enchantment ID, display name, maximum level, anvil cost, applicable item tag, and conflicting enchantments
- Trigger, such as attack, mining, right-click, passive, sneaking, respawn, and more
- Target, such as self, players, mobs, or hit entities
- Actions, such as bonus damage, potion effects, explosions, multi-block mining, replanting, velocity, commands, and more
- Independent enchanting table and librarian villager trade rules

Each valid YAML file is parsed into an `EnchantConfig`, then registered into the vanilla enchantment registry through Paper's `RegistryEvents.ENCHANTMENT.compose()`. Registration sets:

- Enchantment description text (the enchantment name shown on items, from `name`)
- Applicable item tag
- Active equipment slots (`registry.active_slots`, default `ANY`)
- Maximum enchantment level
- Anvil cost
- Vanilla weight and vanilla enchanting table minimum/maximum cost (the `registry` section; default weight 1, both costs 30)
- Conflicting enchantment set

The registration fields can be set in the `registry` section of an enchantment YAML (since milk.13; when omitted they equal the previously hard-coded values):

```yaml
registry:
  weight: 1               # vanilla enchantment weight, 1-1024; the plugin's own enchanting table logic ignores it
  min_cost: 30            # vanilla table minimum cost = min_cost + min_cost_per_level × (level − 1)
  min_cost_per_level: 0
  max_cost: 30            # vanilla table maximum cost = max_cost + max_cost_per_level × (level − 1)
  max_cost_per_level: 0
  active_slots: ANY       # vanilla active slot group; the plugin's own triggers always check main hand, off hand and all armour
```

The registry is frozen after startup, so changes to `name`, `max_level`, `anvil_cost`, `applicable_items`, `conflicts` and the `registry` section need a full restart; everything else can be hot-applied with `/enchants reload`, see "Reloading Config" below.

### Triggers

The plugin listens to Bukkit/Paper events and executes actions according to the configured trigger. Common trigger timings include:

- Combat: attacking entities, taking damage, blocking with a shield, killing entities
- Blocks: breaking blocks, left-click mining blocks, placing blocks, right-click interaction
- Projectiles: projectile hitting an entity or block
- Shooting: shooting a bow or crossbow (BOW_SHOOT)
- Player states: starting sneaking, holding sneak, starting sprinting, holding sprint, periodic passive trigger
- Player lifecycle and items: death, respawn, swapping hands, consuming items
- Fishing: catching fish or entities, fish bite

### Targets

Before actions execute, the plugin resolves the enchantment target according to the configured target. Supported target meanings include:

- The trigger player
- Target player
- Target mob
- All entities
- No target

### Actions

Actions are declared in the `actions` section (key → action) and executed in order when the enchantment triggers. Built-in actions include:

`potion_effect`, `bonus_damage`, `healthsteal`, `teleport`, `explosion`, `velocity`, `shockwave`, `break_armor`, `drop_head`, `attribute_modifier`, `multi_break`, `vacuum_drops`, `replant`, `multi_arrow`, `auto_bow`, `draw_accelerate`, `repair`, `hook`, `feast`, `deflect`, `till`, `reflect_damage`, `command`, `block_break`

Numeric parameters can take a value per level: a fixed value, `{base, per_level}`, or since milk.13 a per-level list (e.g. `[8, 16, 32]`; level L uses item L, levels past the end use the last item). Since milk.13 every action also accepts a common `chance` parameter (0-1, also per level); a miss counts as the action failing.

Detailed action parameters are not documented in this README. See:

- [English: docs/action_types_en.md](docs/action_types_en.md)
- [中文版：docs/action_types_zh.md](docs/action_types_zh.md)

## 4. Usage

1. Download the plugin jar.
2. Put the plugin jar into the server `plugins/` directory.
3. Start the server once and let the plugin generate `plugins/MeowsEnchants/config.yml`.
4. Create one YAML file for each custom enchantment under `plugins/MeowsEnchants/enchants/`.
5. Restart the server. Custom enchantments can only be registered during bootstrap and cannot be dynamically added to the server registry through reload: adding or deleting enchantment files and changing registration fields need a full restart; other config changes can be hot-applied with `/enchants reload`.
6. Successfully registered custom enchantments can be viewed directly in the creative inventory, JEI/REI enchantment lists, and recipe lookup views.

Global config path: `plugins/MeowsEnchants/config.yml`. It currently controls:

- Skyllia compatibility logic, used to make enchantments respect island protection mechanics, plus the island size `skyllia_region_size` (default 512) and the actions disabled in the spawn area `skyllia_protected_actions`
- Whether enchanting table probability considers player level
- Book probability balancing, making custom enchantments harder to obtain on books than on weapons or equipment
- The enchanting table per-level chance base `enchanting_table.level_chance_divisor` (default 2) and the book divisor `enchanting_table.book_divisor` (default 4)
- Villager trades `villager_trades`: book price formula, treasure multiplier, price floor and cap, default max uses, and the polluted-trade message `polluted_message`
- Periodic triggers `triggers`: minimum periods for PASSIVE and for holding sneak/sprint (`passive_min_period_ticks` / `hold_min_period_ticks`, default 20 ticks)

The last four are new in milk.13 and default to the previously hard-coded values. The plugin does not add new keys to an existing `config.yml`; missing keys use their defaults. To change one, copy it from the `config.yml` bundled in the jar.

Enchantment config file path:

```text
plugins/MeowsEnchants/enchants/<enchant_id>.yml
```

### Reloading Config

`/enchants reload` re-reads `config.yml` and every enchantment YAML, swaps in the new config as a whole, then restarts the PASSIVE and hold-sneak/sprint tasks and recalculates attribute modifiers for online players. Players, the console and RCON can run it; it needs the permission `meowsenchants.reload` (OP only by default).

- Applied immediately: all of `config.yml`; an enchantment's trigger, target, cooldown, actions, description, sound and particles, enchanting table and villager trade rules; the arrow rain `arrow_rain` and tracking `tracking` sections (including the `mechanic` switch). Arrow rain and tracking arrows already fired keep the parameters they were fired with; only later shots use the new ones.
- Needs a restart: the registration fields above. On reload they keep their registered values and are listed as "restart required"; when only `name` changes, the browser shows the new name right away while the name on items changes after the restart. New enchantment files are registered after the restart; deleted files keep their old config until then.
- When an enchantment file is broken (YAML syntax error, empty file, missing required fields), that enchantment keeps its old config and the report records an error; a broken `config.yml` keeps the previous global config.

The command prints a Chinese summary (applied, restart required, warnings and errors) and always ends with a single JSON line, which the config editor parses over RCON:

```text
MEOWSENCHANTS_RELOAD {"ok":true,"pluginVersion":"0.2-milk.13","applied":["night_vision"],"restartRequired":[{"enchant":"night_vision","fields":["max_level"],"reason":"changed"}],"warnings":[],"errors":[]}
```

`applied` lists only enchantments where at least one hot-applied value actually changed; an enchantment whose only changes are restart fields (including a `name`-only change) appears only in `restartRequired`. In the example, night_vision changed both its cooldown and `max_level`, so it is in both lists. `ok` is `false` when there are errors; `reason` is `changed` (registration fields changed), `added` (new file) or `removed` (file deleted); warnings and errors carry `enchant`, `path` and `message`. When a player or RCON runs it, the summary is also written to the server log.

Load-time validation runs at startup and on every reload. It only reports and never blocks: type mismatches, missing required parameters and empty per-level lists are errors (the affected actions are skipped when triggered); unknown keys, unknown values, names missing from the registries, out-of-range values and unresolved description placeholders are warnings. The startup report goes to the server log.

### Enchantment Browser

Run `/enchants` in game to open the enchantment browser (a 6-row chest GUI):

- The middle slot of the first row accepts the item to inspect and lists the custom enchantments that item supports; an enchanted book or an empty slot lists every enchantment.
- Rows 2-5 show enchantment books sorted by supported item tag and enchantment name, using the `name` and `description` fields from the config.
- Enchantments the inspected item already has are shown as knowledge books; the border colour indicates the item category: yellow for tools, red for weapons, blue for armour, black otherwise.
- When more than 36 enchantments match, the last row shows previous/next page buttons with the arrow amount set to the target page number.

Every enchantment config supports a `description` string list rendered as lore in the browser, supporting both Bukkit colour codes and MiniMessage tags:

```yaml
description:
  - "&fGrants additional knockback resistance"
  - "&bCooldown: 5s"
  - "&7Applies to: &eleggings"
```

Since milk.13 `description` lines can contain placeholders that are replaced with the configured values when shown, so the text no longer has to be edited by hand after a parameter change:

```yaml
description:
  - "&bCooldown: {cooldown|s}s"
  - "&7Head drop chance: {actions.head.amount|%}, {actions.head.amount@max|%} at max level"
```

- References: `cooldown`, `max_level`, `anvil_cost`, `enchanting_table.chance`, `villager_trades.chance`, `actions.<key>.<parameter>` (including `chance`; nested sections joined with dots); arrow rain and tracking can also reference `arrow_rain.<parameter>` / `tracking.<parameter>`. Omitted parameters use their defaults.
- `@level`: `@2` takes the level 2 value, `@max` the max level value, `@step` the per-level increment (`per_level` of `{base, per_level}`, 0 for a fixed value, not supported for lists); without it level 1 is used. Integer parameters are truncated as the plugin does.
- `|format`, stackable: `%` multiplies by 100 and appends a percent sign, `s` converts milliseconds or ticks to seconds, `0`-`3` sets fixed decimals; without fixed decimals whole numbers print without decimals and others with up to two.
- `{{` / `}}` print literal braces. Unresolved placeholders are shown verbatim and load-time validation reports a warning.
- The plugin and the editor preview run the same test vectors, [editor/placeholder-vectors.json](editor/placeholder-vectors.json), and must agree.

## 5. Building

Parameters, config migration and verification notes for Milk custom enchantments live per enchantment in [docs/enchants/](docs/enchants/) (in Chinese), e.g. arrow rain in [docs/enchants/arrow_rain.md](docs/enchants/arrow_rain.md) and tracking (arrows and crossbow fireworks steer towards nearby targets, optionally respecting Residence claims) in [docs/enchants/tracking.md](docs/enchants/tracking.md).

This project uses Maven. Dependencies are resolved from the PaperMC Maven repository and the PlaceholderAPI repository.  
The project currently uses Java `25`. Before building, make sure your local JDK matches the Java version used by Maven.

With a normal Maven installation:

```bash
mvn clean package
```

Build output:

```text
target/meowsenchants-0.2-milk.13.jar
```

### Editor Contract Files

The three files under `editor/` are for the config editor:

- `enchant-schema.json`: every configurable item (enchantment fields, action parameters, the arrow rain and tracking sections, `config.yml`) with its type, default, range and how it applies (`apply: reload` or `apply: restart`), exported from the parameter declarations in the source.
- `scaling-vectors.json`: test vectors for per-level values.
- `placeholder-vectors.json`: test vectors for description placeholders.

Re-export the schema after changing parameter declarations:

```bash
JAVA_HOME=/usr/lib/jvm/zulu-25 mvn -B test -Dtest=EnchantSchemaExportTest -Dmeowsenchants.updateSchema=true
```

In a normal test run `EnchantSchemaExportTest` only compares, and fails when the committed schema differs from the code declarations.

## License

This project is licensed under the MIT License. See [LICENSE](LICENSE).
