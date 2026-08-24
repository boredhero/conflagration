<p align="center">
  <img src="assets/icon-512.png" width="160" alt="Conflagration">
</p>

<h1 align="center">Conflagration</h1>

<p align="center">
  <em>Fire that spreads, grows, and keeps burning until the fuel is gone.</em><br>
  <a href="#"><img alt="Minecraft 1.21.1" src="https://img.shields.io/badge/Minecraft-1.21.1-brightgreen"></a>
  <a href="#"><img alt="NeoForge" src="https://img.shields.io/badge/loader-NeoForge-orange"></a>
  <a href="LICENSE"><img alt="GPL-3.0-or-later" src="https://img.shields.io/badge/license-GPL--3.0--or--later-blue"></a>
</p>

---

## Why vanilla fire is disappointing

Light a village house in vanilla Minecraft and it scorches a bit and goes out. Light a forest and
the canopy flashes over while every trunk survives. That isn't an accident — it's two numbers.

Minecraft gives every flammable block **two independent values**, and conflating them is the most
common mistake people make when tuning fire:

| | what it does |
|---|---|
| **ignite** (encouragement) | how likely the block is to **catch** from nearby fire — raise it to make fire **travel** |
| **burn** (flammability) | how fast the block is **consumed** once alight — raise it too far and fires get *shorter*, because the fire loses the fuel it was standing on |

Here is the stock table:

| Block | ignite | burn |
|---|---|---|
| **Logs, wood** | **5** | **5** |
| Planks, stairs, fences | 5 | 20 |
| Leaves, wool | 30 | 60 |
| Carpet, hay | 60 | 20 |

Logs at **ignite 5** are the whole problem. Leaves catch easily and burn away fast, so the canopy
goes up and vanishes — and then the fire has nothing left to stand on. Structural wood is
deliberately near-fireproof so villages and player builds don't routinely burn down.

Conflagration changes those numbers, with the rule **high ignite, moderate burn**: fire travels
readily, but fuel sticks around long enough for a building to actually come down.

## What it does not do

There is **no gamerule, datapack, tag, or config file in the game that can change flammability.**
It lives in a private `Object2IntMap` inside `FireBlock`, populated in Java at bootstrap. That is
why this has to be a mod at all. `randomTickSpeed` in particular does **nothing** for fire — since
1.16, fire runs on *scheduled* ticks, not random ticks
([MC-181868](https://bugs.mojang.com/browse/MC-181868)). Turning it up costs you TPS and buys
nothing.

## Install

Drop the jar in your server's `mods/` folder. That's it.

**Clients do not need it.** Conflagration registers no blocks, items, or renderers and only changes
server-side game logic, so it declares `displayTest = "IGNORE_ALL_VERSION"` and players can connect
without installing anything.

Requires **NeoForge** on **Minecraft 1.21.1**. Builds for 1.21.4 and 1.21.8 are produced on a
best-effort basis (see [Version support](#version-support)).

## Configuration

`config/conflagration-common.toml`, generated on first run.

### Presets

| Preset | Log ignite | Effect |
|---|---:|---|
| `VANILLA` | 5 | Mojang's stock values. Effectively disables the mod. |
| `SMOULDERING` | 15 | Restrained. Buildings burn slowly; fire is survivable. |
| **`AGGRESSIVE`** *(default)* | 35 | A lit house burns down. Forest fires carry through trunks. |
| `INFERNO` | 70 | Deliberately unreasonable. Fire crosses open ground. You will lose things. |
| `CUSTOM` | — | Use the `[custom]` table verbatim. |

### Fuel categories

Categories are **tag-driven**, so modded woods are picked up automatically. Notably it targets
`#minecraft:logs_that_burn` rather than `#minecraft:logs`, leaving crimson and warped stems
fireproof exactly as vanilla intends.

`LOGS`, `BAMBOO`, `PLANKS`, `WOODEN_FEATURES`, `LEAVES`, `WOOL`, `CARPETS`, `SAPLINGS`, `PLANTS`,
`CROPS`. A block matching several resolves to whichever is declared first.

> **Crops are inert in vanilla.** Giving them a non-zero value is a real gameplay change that lets
> fields burn. `AGGRESSIVE` and `INFERNO` do this deliberately.

### Per-block control

```toml
[blocks]
    # Exact values for specific blocks, whatever the preset says.
    overrides = ["minecraft:oak_log=80,10", "create:andesite_casing=0,0"]
    # Never touched, whatever else is configured. Beats overrides.
    blacklist = ["minecraft:bookshelf"]
```

Malformed entries are logged as warnings and skipped — a typo will never stop your server booting.

### Claim protection

```toml
[integration]
    claim_fire_protection = "ENABLE"
```

If **FTB Chunks** is installed, Conflagration drives its `fire_spread_protection` setting.
Aggressive fire plus unprotected claims means a neighbour's forest fire can take out someone's
base, so this defaults to `ENABLE`. Set `LEAVE_ALONE` to manage it yourself, or `DISABLE` for
full chaos.

FTB Chunks is a **soft** dependency, reached by reflection. On a server without it, this option is
ignored and the mod works normally.

## Compatibility

**Conflagration contains no mixins.** `FireBlock#setFlammable` is public API in 1.21.1, so there is
nothing to clash with another mod at the bytecode level.

Values are applied on **tag load**, which means they survive `/reload` and pick up datapack tag
changes. The flammability table is global and rebuilt from scratch every launch, so removing the
mod restores vanilla behaviour on the next restart with nothing left behind.

If another mod also sets flammability for the same block, **last write wins**. Set
`log_applied_values = true` once to see exactly what changed.

If another mod has replaced `minecraft:fire` entirely, Conflagration detects this, logs an error,
and does nothing rather than breaking that mod.

## Performance

Fire is one of the heavier vanilla block ticks. Measured against the 1.21.1 sources, a single fire
block scans **53 candidate positions** per tick, costing up to **~390 `getBlockState` calls** and
**~380 short-lived allocations** — though each block only ticks every 30–39 game ticks, so the cost
is roughly `active_fire_blocks × 11` block-state reads per game tick.

Raising flammability increases the number of simultaneously burning blocks, and therefore this
cost. If you run `INFERNO` on a busy server, profile it — the pack-standard tool is
[spark](https://spark.lucko.me/docs):

```
/spark profiler start --only-ticks-over 100 --timeout 120
```

Look for `FireBlock.tick` in the resulting flame graph.

A future release may add opt-in optimisations to the fire tick path itself. Those require mixins
and therefore carry real compatibility risk, so they will ship **off by default** and gated behind
detection of conflicting mods — not bundled into this release.

## Version support

| Minecraft | Status |
|---|---|
| **1.21.1** | **Reference target.** Verified against a real server. |
| 1.21.4 | Best-effort. Built by CI, not play-tested. |
| 1.21.8 | Best-effort. Built by CI, not play-tested. |

From **1.21.5** Mojang gated fire ticking near players (`allowFireTicksAwayFromPlayer`), and
**1.21.11** replaced `doFireTick` with `fire_spread_radius_around_player`. Behaviour on those
versions therefore differs from 1.21.1 even where the mod compiles cleanly.

## Building

```bash
./gradlew build                       # 1.21.1 by default
./gradlew test                        # unit tests only
./gradlew build -Pminecraft_version=1.21.4 -Pneoforge_version=21.4.157
```

Jars land in `build/libs/`.

The policy layer (`dev.boredhero.conflagration.policy`) deliberately contains **no Minecraft
imports**, so preset tables, value clamping, config parsing and category precedence are all unit
tested in CI without a game harness.

## Contributing

`master` is the release branch and `develop` is the integration branch; both are protected, so
work happens on a branch and lands via pull request. CI runs the test suite across the full
version matrix on every PR.

## License

[GPL-3.0-or-later](LICENSE).
