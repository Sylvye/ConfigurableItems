# ConfigurableItems

ConfigurableItems is a Paper plugin for Minecraft Java `1.21.11` that lets admins build custom items in game and attach trigger-driven command/action behavior to those items.

## Requirements

- Java 21
- Paper `1.21.11`

## Build

```sh
./gradlew build
```

On Windows:

```bat
gradlew.bat build
```

The plugin jar is written to `build/libs/`.

## Commands

| Command | Description |
| --- | --- |
| `/ci` | Opens the item editor GUI. |
| `/citems` | Alias for `/ci`. |
| `/configurableitems` | Alias for `/ci`. |
| `/ci give <id> [player] [amount]` | Gives a saved configured item. |
| `/ci reload` | Reloads item YAML files from disk. |

Permission: `configurableitems.admin`, default `op`.

Players without permission receive:

```text
You do not have permission to use ConfigurableItems
```

## Trigger And Action Wiki

Custom item behavior is built from three pieces:

1. A configured item saved in YAML.
2. One or more triggers on that item.
3. Ordered command/action lines inside each trigger.

Generated items are tagged with a persistent ConfigurableItems item id. Trigger listeners return early unless the involved stack has that tag, so the system does not scan display names, lore, or every inventory slot during normal play.

### Trigger Folders

Each trigger behaves like a folder. The folder contains ordered lines that run when that event happens for the tagged item.

Example `RIGHT_CLICK` folder:

```yaml
triggers:
  RIGHT_CLICK:
    - "SEND_MESSAGE &aActivated {ITEM_NAME}"
    - "PARTICLE FLAME 20 0.2 0.01"
    - "minecraft:give {SELF} minecraft:diamond 1"
  RIGHT_CLICK_BLOCK:
    - "SEND_MESSAGE &eClicked {BLOCK}"
```

Lines run from top to bottom. If a line starts with a known CI action name, ConfigurableItems executes it through the action engine. If not, the line is dispatched synchronously by the server console as a normal command.

Trigger lines can optionally have per-player cooldowns. Existing plain string lines are still valid. In the GUI trigger command list, left-click removes a row and right-click prompts for cooldown input:

```text
[TICKS] [optional message]
```

Example YAML with one cooled line:

```yaml
triggers:
  RIGHT_CLICK:
    - command: "DASH 1.8"
      cooldown-ticks: 100
      cooldown-message: "&cThat ability is on cooldown for {COOLDOWN}s."
    - "PARTICLE CLOUD 30 0.2 0.05"
```

Cooldowns are disabled by default. Use `clear` or `0` in the GUI cooldown prompt to disable one. If no message is set, the command is skipped silently while cooling down. In cooldown messages, `{COOLDOWN}` is the whole seconds remaining, rounded up.

Known CI action:

```text
DAMAGE 5
```

Console-command fallback:

```text
minecraft:give {SELF} minecraft:diamond 1
```

Do not include a leading `/` in console commands.

### Variables

Variables are written with curly braces. They are replaced before commands or actions run.

Use explicit scoped variables for locations. `{SELF_X}`, `{SELF_Y}`, `{SELF_Z}`, and `{SELF_WORLD}` describe the triggering player. `{TARGET_*}`, `{BLOCK_*}`, `{HIT_*}`, and `{PROJECTILE_*}` describe nested or event-specific context. Generic `{X}`, `{Y}`, `{Z}`, and `{WORLD}` are not built-in variables.

Numeric expressions can be written inside placeholders when the referenced variables are numeric:

```text
fill {HIT_X-1} {HIT_Y-1} {HIT_Z-1} {HIT_X+1} {HIT_Y+1} {HIT_Z+1} minecraft:cobweb replace #air
```

Base variables are available on every trigger:

| Variable | Value |
| --- | --- |
| `{SELF}` | Player holding or using the item. |
| `{SELF_UUID}` | UUID of `{SELF}`. |
| `{SELF_WORLD}` | `{SELF}` world. |
| `{SELF_X}` | `{SELF}` block X coordinate. |
| `{SELF_Y}` | `{SELF}` block Y coordinate. |
| `{SELF_Z}` | `{SELF}` block Z coordinate. |
| `{ITEM_ID}` | ConfigurableItems YAML id. |
| `{ITEM_NAME}` | Configured custom item name text. |

Target variables are available when the trigger starts with an entity target, or inside selector actions after they choose a target:

| Variable | Value |
| --- | --- |
| `{TARGET}` | Target player name, or target entity registry id for non-player entities. |
| `{TARGET_UUID}` | UUID of the target entity. |
| `{ENTITY}` | Target entity registry id. |
| `{ENTITY_UUID}` | UUID of the target entity. |
| `{TARGET_WORLD}` | Target world. |
| `{TARGET_X}` / `{TARGET_Y}` / `{TARGET_Z}` | Target block coordinates. |

Block variables are available on block-specific triggers, or inside `HITSCAN`/block actions after a block is selected:

| Variable | Value |
| --- | --- |
| `{BLOCK}` | Block registry id, for example `minecraft:stone`. |
| `{BLOCK_WORLD}` | Block world. |
| `{BLOCK_X}` / `{BLOCK_Y}` / `{BLOCK_Z}` | Block coordinates. |

Hitscan variables are available inside `HITSCAN` bodies:

| Variable | Value |
| --- | --- |
| `{HIT_TYPE}` | `ENTITY` or `BLOCK`. |
| `{HIT_WORLD}` | Hit world. |
| `{HIT_X}` / `{HIT_Y}` / `{HIT_Z}` | Hit coordinates. |

Projectile variables are available for projectile triggers:

| Variable | Value |
| --- | --- |
| `{PROJECTILE}` | Projectile entity registry id. |
| `{PROJECTILE_WORLD}` | Projectile world. |
| `{PROJECTILE_X}` / `{PROJECTILE_Y}` / `{PROJECTILE_Z}` | Projectile coordinates. |

`FOR` loops can create dynamic variables. Use lowercase or mixed-case variable names such as `{tier}` or `{for1}`. Uppercase unknown variables are rejected during GUI save validation because uppercase names are reserved for trigger context variables.

```text
FOR [red,green,blue] > color
SEND_MESSAGE &eSelected {color}
END_FOR color
```

#### Which Variable Should I Use?

- Use `RIGHT_CLICK_BLOCK` or `LEFT_CLICK_BLOCK` when you need `{BLOCK}` from a click. `RIGHT_CLICK` and `LEFT_CLICK` are broad click triggers and do not validate `{BLOCK}`.
- Use `AROUND`, `NEAREST`, `MOB_AROUND`, `MOB_NEAREST`, or `HITSCAN` when a trigger does not start with a target but nested actions need `{TARGET}`.
- Use `{PROJECTILE}` only in `LAUNCH_PROJECTILE`, `PROJECTILE_HIT_BLOCK`, `PROJECTILE_HIT_ENTITY`, and `PROJECTILE_HIT_PLAYER`.
- Use `{SELF_WORLD}`, `{SELF_X}`, `{SELF_Y}`, and `{SELF_Z}` for `{SELF}`. Use scoped coordinate variables for selected targets, blocks, hits, and projectiles.

### Trigger Reference

| Trigger | Fires When | Main Context |
| --- | --- | --- |
| `RIGHT_CLICK` | Player right-clicks air or block with the item in main hand. | Self. |
| `RIGHT_CLICK_BLOCK` | Player right-clicks a block with the item in main hand. | Self, block. |
| `LEFT_CLICK` | Player left-clicks air or block with the item in main hand. | Self. |
| `LEFT_CLICK_BLOCK` | Player left-clicks a block with the item in main hand. | Self, block. |
| `ALL_CLICK` | Any left or right click handled by the click listener. | Self. |
| `CONSUME` | Player consumes the item. | Self. |
| `BLOCK_BREAK` | Player breaks a block with the item in main hand. | Self, block. |
| `BLOCK_PLACE` | Player places the item as a block. | Self, block. |
| `CLICK_ENTITY` | Player right-clicks a non-player entity with the item in main hand. | Self, target. |
| `CLICK_PLAYER` | Player right-clicks another player with the item in main hand. | Self, target. |
| `HIT_ENTITY` | Player damages a non-player entity with the item. | Self, target. |
| `HIT_PLAYER` | Player damages another player with the item. | Self, target. |
| `HIT_BY_ENTITY` | Player holding the item is damaged by a non-player entity. | Self, target damager. |
| `HIT_BY_PLAYER` | Player holding the item is damaged by another player. | Self, target damager. |
| `HIT_GLOBAL` | Player holding the item is damaged by any entity. | Self, target damager. |
| `KILL_ENTITY` | Player kills a non-player entity with the item. | Self, target victim. |
| `KILL_PLAYER` | Player kills another player with the item. | Self, target victim. |
| `LAUNCH_PROJECTILE` | Player launches a projectile while holding a tagged item. | Self, projectile. |
| `PROJECTILE_HIT_BLOCK` | Tracked projectile from the item hits a block. | Self, projectile, block. |
| `PROJECTILE_HIT_ENTITY` | Tracked projectile from the item hits a non-player entity. | Self, projectile, target. |
| `PROJECTILE_HIT_PLAYER` | Tracked projectile from the item hits a player. | Self, projectile, target. |
| `DROP_SELF` | Player drops the tagged item. | Self. |
| `SELECT_SELF` | Player switches hotbar slot onto the tagged item. | Self. |
| `DESELECT_SELF` | Player switches hotbar slot away from the tagged item. | Self. |
| `EQUIP_SELF` | Paper equipment event equips the tagged item. | Self. |
| `UNEQUIP_SELF` | Paper equipment event unequips the tagged item. | Self. |
| `ITEM_BREAK` | The tagged item breaks. | Self. |
| `DEATH` | Player dies while the tagged item is in main hand. | Self. |

Notes:

- Click triggers only listen to the main hand and deduplicate likely double fires within a short window. They handle Paper's cancelled air/no-op interact events so air clicks can fire; denied block interactions are still skipped. `RIGHT_CLICK`, `LEFT_CLICK`, and `ALL_CLICK` always use self coordinates; use `RIGHT_CLICK_BLOCK` and `LEFT_CLICK_BLOCK` when `{BLOCK}` or `{BLOCK_X}` is needed.
- Projectile hit triggers are linked to the item id recorded when the projectile launched.
- Most non-click trigger listeners use `ignoreCancelled = true`. If a configured restriction cancels drop, placement, consumption, crafting, enchant/anvil, or tool interaction, matching triggers do not fire for that cancelled event.
- `HIT_BY_*` and `DEATH` currently check the player's main hand item.

## Action Syntax

Action lines are whitespace-tokenized. Text actions such as `SEND_MESSAGE` and `ACTIONBAR` treat the rest of the line as text.

### Positional Args

```text
DAMAGE 5
PARTICLE FLAME 20 0.2 0.01
PARTICLE_LINE FLAME 8 24 0 0
```

### Key Args

Some actions also accept key arguments:

```text
DAMAGE amount:5
BREAK_BLOCK drop:false
VEINMINE 64 drop:true
```

### Inline Chaining

Use `<+>` to run several actions from one line.

```text
SEND_MESSAGE &aDash <+> DASH 1.5 <+> PARTICLE CLOUD 30 0.2 0.05
```

### Random Inline Chaining

Inside `RANDOM_RUN`, use `+++` to group several actions as one random option.

```text
RANDOM_RUN selectionCount:1
SEND_MESSAGE &aGreen +++ PARTICLE HAPPY_VILLAGER 20
SEND_MESSAGE &cRed +++ PARTICLE FLAME 20
RANDOM_END
```

One random line is selected, then all actions on that selected line are run.

### Console Command Fallback

Any line that does not start with a known CI action name runs as a console command after variable replacement.

```text
minecraft:effect give {SELF} minecraft:speed 5 1 true
lp user {SELF} permission set temp.example true
```

If a server command has the same first word as a CI action, use a namespaced command where available, or choose a different command wrapper.

## Flow And Control Actions

### `DELAY_TICK <ticks>`

Pauses the remaining queue for the given number of server ticks.

```text
SEND_MESSAGE &eCharging...
DELAY_TICK 40
PARTICLE EXPLOSION 3 0.1 0
```

Delayed queues stop if the player logs out or the item definition no longer exists.

`DELAY_TICK` only affects actions after it in the same queue or body. GUI save validation rejects it when it is the last action.

### `IF <condition> <action...>`

Runs the action body if the condition is true.

```text
IF {SELF_X}>100 SEND_MESSAGE &aYou are east of x=100
IF {SELF_WORLD}=world_nether DAMAGE 4
IF {TARGET}!={SELF} SEND_MESSAGE &eTarget: {TARGET}
```

Supported comparison operators:

```text
= != > < >= <=
```

Supported logical operators:

```text
&& ||
```

Keep the condition as one token without spaces. This is valid:

```text
IF {SELF_X}>=0&&{SELF_Y}>60 SEND_MESSAGE &aHigh ground
```

This is not valid because the current parser treats the first space as the start of the action body:

```text
IF {SELF_X} >= 0 SEND_MESSAGE &aBad spacing
```

Numeric comparisons are used when both sides parse as numbers. Otherwise, string comparisons are used.

### `LOOP_START <times> [delayTicks]` / `LOOP_END`

Repeats the enclosed action lines.

```text
LOOP_START 5 10
PARTICLE CRIT 15 0.2 0.01
SEND_MESSAGE &ePulse
LOOP_END
```

`delayTicks` is optional. If provided, it is inserted between loop iterations.

### `RANDOM_RUN selectionCount:<n>` / `RANDOM_END`

Randomly chooses `n` enclosed lines or blocks to run.

```text
RANDOM_RUN selectionCount:1
SEND_MESSAGE &aRolled green
SEND_MESSAGE &bRolled blue
SEND_MESSAGE &cRolled red
RANDOM_END
```

### `FOR [a,b,c] > var` / `FOR [var=start;step;count]` / `END_FOR var`

Repeats the enclosed action lines once for each listed value.

```text
FOR [1,2,3] > step
SEND_MESSAGE &eStep {step}
END_FOR step

FOR [x=0.5;1;4]
SEND_MESSAGE &eX is {x}
END_FOR x
```

`ENDFOR var` is also accepted.

## Selector Actions

Selector actions temporarily replace the current action target for their nested body. Entity/player actions affect the selected target. Message actions send to the selected player when the selected target is a player; otherwise they fall back to `{SELF}`.

### `AROUND <radius> <action...>`

Runs the nested action once for each nearby player except self.

```text
AROUND 10 SEND_MESSAGE &e{SELF} activated an aura
```

### `MOB_AROUND <radius> <action...>`

Runs the nested action once for each nearby living non-player mob.

```text
MOB_AROUND 8 DAMAGE 3
```

### `NEAREST <radius> <action...>`

Runs the nested action for the nearest nearby player except self.

```text
NEAREST 16 ACTIONBAR &cYou were marked by {SELF}
```

### `MOB_NEAREST <radius> <action...>`

Runs the nested action for the nearest nearby living non-player mob.

```text
MOB_NEAREST 12 BURN 4
```

### `HITSCAN <distance> [target:ENTITY|PLAYER|MOB|BLOCK] [max-hits:<n>|max-hits:all] [speed:instant|<blocksPerSecond>] [particle:<type>] [points:<n>] [offset:<value>] [particle-speed:<value>] <action...>`

Looks along `{SELF}`'s view direction. Raycasts always stop at the first blocking block. `target:ENTITY` is the default and selects entities before that block. `target:PLAYER` only selects players, `target:MOB` only selects living non-player mobs, and `target:BLOCK` ignores entities and selects the first block. Use `max-hits` for entity modes. Use `speed:<blocksPerSecond>` to delay the nested action until the ray reaches each hit; `speed:instant` is the default. If `particle` is set, ConfigurableItems draws the trail at the same ray speed.

Inside the nested body, HITSCAN exposes `{HIT_TYPE}`, `{HIT_WORLD}`, `{HIT_X}`, `{HIT_Y}`, and `{HIT_Z}`. Entity modes also expose target variables. Block mode exposes block variables.

```text
HITSCAN 32 target:ENTITY DAMAGE 5 target:TARGET
HITSCAN 32 target:BLOCK SET_TEMP_BLOCK GLOWSTONE 60
HITSCAN 32 target:PLAYER max-hits:all speed:80 particle:CRIT points:24 ACTIONBAR target:TARGET &cMarked
HITSCAN 32 target:BLOCK speed:1 particle:CRIT points:32 minecraft:fill {HIT_X-1} {HIT_Y-1} {HIT_Z-1} {HIT_X+1} {HIT_Y+1} {HIT_Z+1} minecraft:cobweb replace #air
```

Old HITSCAN particle velocity written as `speed:<small decimal>` with a `particle:` option is normalized to `particle-speed:<value>`. Use `speed:` for ray travel speed.

## Entity And Player Actions

Entity actions accept `target:SELF`, `target:TARGET`, or `target:CURRENT`. If omitted, `target:CURRENT` is used for legacy configs. `target:TARGET` requires a trigger, selector, or HITSCAN entity context.

| Action | Description |
| --- | --- |
| `DAMAGE <amount> [target:<receiver>]` | Damages the selected living receiver. Supports `amount:<value>`. |
| `HEAL [amount] [target:<receiver>]` | Heals the selected living receiver. Omit amount to heal to max health. |
| `SET_HEALTH <amount> [target:<receiver>]` | Sets selected living receiver health, clamped to valid health range. |
| `KILL [target:<receiver>]` | Sets selected living receiver health to `0`. |
| `BURN <seconds> [target:<receiver>]` | Sets selected living receiver fire ticks. |
| `INVULNERABILITY <ticks> [target:<receiver>]` | Sets selected living receiver no-damage ticks. |
| `TELEPORT target:<receiver> <x> <y> <z> [world]` | Teleports selected receiver to coordinates. Uses current world if omitted. |
| `TELEPORT target:<receiver> to:SELF|TARGET|CURRENT|HIT|BLOCK` | Teleports selected receiver to a context location. |
| `VELOCITY <x> <y> <z> [target:<receiver>]` | Sets selected living receiver velocity. |
| `DASH <strength>` | Pushes `{SELF}` forward in their look direction. |

Examples:

```text
DAMAGE 4
MOB_AROUND 8 DAMAGE amount:2 target:TARGET
NEAREST 12 TELEPORT target:TARGET to:SELF
```

In a `HIT_PLAYER` trigger folder, `DAMAGE 4` still damages the hit player through legacy `target:CURRENT`. New GUI entries write the receiver explicitly.

## Messaging And Visual Actions

| Action | Description |
| --- | --- |
| `SEND_MESSAGE [target:<receiver>] <text>` | Sends a chat message to the selected player receiver. Omitted target keeps legacy current-player-or-self behavior. Supports `&` color/style codes. |
| `ACTIONBAR [target:<receiver>] <text>` | Sends an actionbar message to the selected player receiver. Omitted target keeps legacy current-player-or-self behavior. Supports `&` color/style codes. |
| `PARTICLE <type> <count> [offset] [speed] [at:<location>]` | Spawns a Bukkit particle at `SELF`, `TARGET`, `CURRENT`, `BLOCK`, `HIT`, or `PROJECTILE`. Omitted `at:` keeps legacy current location behavior. |
| `PARTICLE_LINE <type> <distance> <points> [offset] [speed] [at:<location>]` | Spawns one particle per point in a line from the selected location along the player's look direction. |
| `PROJECTILE_TRAIL [particle:<type>] [count:<n>] [points:<n>] [interval:<ticks>] [duration:<ticks>] [offset:<value>] [speed:<value>]` / `END_PROJECTILE_TRAIL` | Runs nested actions at the current projectile location until the projectile stops or duration expires. Intended for `LAUNCH_PROJECTILE`. |

Examples:

```text
SEND_MESSAGE target:SELF &aItem ready.
ACTIONBAR target:TARGET &eMarked
PARTICLE FLAME 20 0.2 0.01 at:SELF
PARTICLE_LINE FLAME 8 24 0 0 at:HIT
PROJECTILE_TRAIL particle:FLAME points:3 interval:1 duration:100 offset:0 speed:0
PARTICLE CRIT 2 0.05 0
AROUND 1.5 DAMAGE 2
END_PROJECTILE_TRAIL
```

Particle types are validated against the Paper/Bukkit particle registry.

`PROJECTILE_TRAIL` requires a projectile context, normally from a `LAUNCH_PROJECTILE` trigger. `interval` and `duration` default to `1` and `100` ticks. `particle` is optional; when set, `points` interpolates particles between the previous and current projectile locations to avoid visual gaps. Nested actions keep the original target unless they select a new one.

## Block And Item Actions

Block actions require block context. Use block triggers such as `RIGHT_CLICK_BLOCK`, `LEFT_CLICK_BLOCK`, `BLOCK_BREAK`, `BLOCK_PLACE`, `PROJECTILE_HIT_BLOCK`, or `HITSCAN target:BLOCK`.

| Action | Description |
| --- | --- |
| `SET_BLOCK <material>` | Sets the current block to a block material. |
| `SET_TEMP_BLOCK <material> <ticks>` | Temporarily changes the current block, then restores its old material. |
| `BREAK_BLOCK [drop:true|false]` | Breaks the current block. Defaults to dropping items. |
| `DROPITEM <material> [amount] [at:<location>]` | Drops an item at `SELF`, `TARGET`, `CURRENT`, `BLOCK`, `HIT`, or `PROJECTILE`. |
| `VEINMINE [limit] [drop:true|false] [filter:<material|#tag>[,<material|#tag>...]] [match:all|same-type] [mode:destroy|replace] [replace:<material>] [use-enchants:true|false] [use-durability:true|false] [effect:true|false] [xp:true|false]` | Breaks or replaces connected blocks from the current block. Defaults limit to the configured veinmine warning threshold. |

Examples:

```text
SET_BLOCK STONE
SET_TEMP_BLOCK GLOWSTONE 100
BREAK_BLOCK drop:false
DROPITEM DIAMOND 1
VEINMINE 64 drop:true
VEINMINE 128 filter:#minecraft:logs drop:true use-durability:false
VEINMINE 128 filter:#minecraft:logs,#minecraft:leaves,minecraft:bee_nest drop:true
VEINMINE 128 filter:#minecraft:logs,#minecraft:leaves match:same-type drop:true
VEINMINE 64 filter:minecraft:diamond_ore mode:replace replace:STONE
```

Without `filter`, `VEINMINE` only affects connected blocks matching the starting block type. `filter:<material>` matches one block material. `filter:#<tag>` matches a Bukkit block tag. Separate multiple filter entries with commas to match any listed material or tag. With a filter, `match:all` is the default and mines every matching block in the filter; `match:same-type` only mines matching blocks with the same material as the starting block. `mode:destroy` is the default. `mode:replace` requires `replace:<material>` and ignores drop, enchant, durability, effect, and XP flags. Material names are validated against Bukkit materials. Block-setting actions require block materials.

In destroy mode, `use-enchants:true` uses the held main-hand tool for natural drops, and `use-durability:true` damages that tool once per matched block. `effect` and `xp` are passed to natural block breaking when `drop:true`; with `drop:false`, blocks are removed directly and `effect:true` only plays a break visual.

## Practical Examples

### Right-Click Dash

Trigger: `RIGHT_CLICK`

```text
ACTIONBAR &bDash
DASH 1.8
PARTICLE CLOUD 30 0.2 0.05
```

### Hit Player Mark

Trigger: `HIT_PLAYER`

```text
SEND_MESSAGE &cYou were marked by {SELF}
ACTIONBAR &cMarked by {SELF}
minecraft:effect give {TARGET} minecraft:glowing 5 0 true
```

The first two lines go to the hit player because `HIT_PLAYER` makes that player the current target. The third line is a console command and can directly use `{TARGET}`.

### Nearby Player Pulse

Trigger: `RIGHT_CLICK`

```text
AROUND 10 ACTIONBAR &ePulse from {SELF} <+> DAMAGE 2
PARTICLE SONIC_BOOM 1 0 0
```

`AROUND` retargets its nested actions to each selected player. The particle line runs once at the original context location.

### Random Proc On Kill

Trigger: `KILL_ENTITY`

```text
RANDOM_RUN selectionCount:1
SEND_MESSAGE &aVampiric proc +++ HEAL 4
SEND_MESSAGE &6Fire proc +++ BURN 4
SEND_MESSAGE &bSpeed proc +++ minecraft:effect give {SELF} minecraft:speed 5 1 true
RANDOM_END
```

### Delayed Burst

Trigger: `RIGHT_CLICK`

```text
SEND_MESSAGE &eCharging burst...
DELAY_TICK 40
HITSCAN 24 DAMAGE 8 <+> PARTICLE CRIT 40 0.3 0.05
```

### Veinmine Pickaxe

Trigger: `BLOCK_BREAK`

```text
VEINMINE 64 drop:true
```

Use a sensible limit. `VEINMINE` is intentionally warning-based, not hard-blocked.

## Safety And Performance

ConfigurableItems does not refuse expensive actions by default. It logs warnings when action inputs exceed configured thresholds.

Default `config.yml`:

```yaml
actions:
  warnings:
    warn-radius: 32
    warn-selected-entities: 50
    warn-loop-iterations: 100
    warn-for-iterations: 200
    warn-veinmine-blocks: 128
    warn-hitscan-distance: 128
    warn-delayed-queues: 500
```

Warnings include the item id, trigger, player name, and threshold detail. If the triggering player is OP, ConfigurableItems sends the warning or runtime command/action error to that player; otherwise it writes a concise warning to the server log. They are meant to catch costly or invalid item designs during testing without silently changing behavior.

Practical guidance:

- Keep selector radii tight for frequently used items.
- Put limits on `VEINMINE`.
- Avoid long loops with very small delays on items that many players can trigger at once.
- Prefer built-in actions for simple behavior and console commands for integrations with other plugins.

## Parser Errors

The parser reports warnings and continues where possible.

Common errors:

| Error | Cause |
| --- | --- |
| `Unmatched block terminator` | `LOOP_END`, `RANDOM_END`, or `END_FOR` appeared without a matching opener. |
| `Missing block terminator` | A block opener was not closed. |
| `Malformed number` | A numeric argument could not be parsed. |
| `Malformed FOR block` | `FOR` did not match `FOR [a,b,c] > var`. |
| `IF is missing an action body` | `IF` has a condition but no action after it. |
| `selector is missing an action body` | `AROUND`, `NEAREST`, `MOB_AROUND`, `MOB_NEAREST`, or `HITSCAN` has no nested action. |

## Troubleshooting

If a trigger does not fire:

- Make sure the item was generated by ConfigurableItems, not recreated manually without the item-id tag.
- Check that the trigger folder has at least one command/action line.
- Check server logs for parser or validation warnings.
- For click triggers, test with the item in main hand.
- For restricted items, remember that cancelled events do not continue into trigger execution.

If a variable does not replace:

- Check the trigger editor's Variables button. It groups the variables that can exist when that trigger starts.
- If `{BLOCK}` is missing from a click trigger, use `RIGHT_CLICK_BLOCK` or `LEFT_CLICK_BLOCK`.
- If `{TARGET}` is missing, use a target trigger or a selector action such as `AROUND`, `NEAREST`, or `HITSCAN`.
- Keep `FOR` variables inside their matching `FOR` / `END_FOR` block.

If an action runs as a console command:

- The first word is not a known CI action name.
- Action names are case-insensitive, but spelling must match the supported action list.

## Supported Action List

```text
DELAY_TICK
IF
LOOP_START / LOOP_END
RANDOM_RUN / RANDOM_END
FOR / END_FOR
AROUND
MOB_AROUND
NEAREST
MOB_NEAREST
HITSCAN
DAMAGE
HEAL
SET_HEALTH
KILL
BURN
INVULNERABILITY
TELEPORT
VELOCITY
DASH
SEND_MESSAGE
ACTIONBAR
PARTICLE
PARTICLE_LINE
PROJECTILE_TRAIL
SET_BLOCK
SET_TEMP_BLOCK
BREAK_BLOCK
DROPITEM
VEINMINE
```
