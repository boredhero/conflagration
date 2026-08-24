![Conflagration](assets/banner.png)

# Conflagration

Vanilla fire doesn't burn anything down. Light a village house and you get scorch marks and a
missing plank. Light a forest and the canopy flashes over in ten seconds while every single trunk
survives. Conflagration makes fire spread hard enough that buildings and forests actually come
down.

NeoForge server mod for Minecraft 1.21.1, by boredhero, [GPL-3.0-or-later](LICENSE).

## The two numbers

Every flammable block carries two independent values, and confusing them is why most attempts at
"make fire worse" end up making fires fizzle faster.

**Ignite** (Mojang calls it encouragement) is how readily a block catches from a fire next to it.
This is the value that makes fire *travel*.

**Burn** (flammability) is how fast the block is consumed once it's alight. Raise this too far and
your fires get *shorter*, because the fire eats the fuel it's standing on.

Stock values:

| Block | ignite | burn |
|---|---:|---:|
| Logs, wood | 5 | 5 |
| Planks, stairs, fences | 5 | 20 |
| Leaves, wool | 30 | 60 |
| Carpet, hay | 60 | 20 |

Logs at ignite 5 are the whole problem. Structural wood is near-fireproof on purpose so that
villages and player builds don't routinely burn down: a reasonable call for vanilla, a boring one
for a server that wants fire to mean something. Leaves catch easily and burn away fast, so the
canopy goes up in a flash and then the fire is standing on nothing.

Conflagration's rule is high ignite, moderate burn. Fire travels eagerly, but fuel sticks around
long enough for a house to actually collapse.

Two things people try before installing a mod, neither of which works:

- **There is no gamerule, datapack, tag or config in the game that touches flammability.** The
  table is a private `Object2IntMap` inside `FireBlock`, populated from Java at bootstrap. That is
  the entire reason this has to be a mod.
- **`randomTickSpeed` does nothing for fire.** Fire has run on scheduled ticks since 1.16
  ([MC-181868](https://bugs.mojang.com/browse/MC-181868)). Cranking it costs TPS and buys nothing.
  The advice is everywhere and it is wrong.

## Install

Drop the jar in your server's `mods/` folder. Done.

Clients don't need it. The mod registers nothing client-side and declares
`displayTest = "IGNORE_SERVER_VERSION"`, so players connect with whatever they already have.

1.21.1 is the target I actually play on and the only version I'd call verified. CI also builds
1.21.4 and 1.21.8; those jars compile and pass tests, but nobody has run them in a world. Mojang
kept moving fire's gamerules around in later versions, adding player-proximity controls and
eventually retiring `doFireTick` altogether, so a clean compile there doesn't mean identical
behaviour. On 1.21.1 itself `doFireTick` is the only fire gamerule that exists.

## Configuration

`config/conflagration-common.toml`, written on first run.

### Presets

| Preset | Log ignite | What happens |
|---|---:|---|
| `VANILLA` | 5 | Mojang's numbers. The mod effectively does nothing. |
| `SMOULDERING` | 15 | Restrained. Buildings burn slowly and you can fight it. |
| `AGGRESSIVE` *(default)* | 35 | A lit house burns down. Forest fires carry through trunks. |
| `INFERNO` | 70 | Unreasonable on purpose. Fire crosses open ground. You will lose things. |
| `CUSTOM` | — | The `[custom]` table is used verbatim. |

### Categories

Categories are tag-driven, so modded wood is picked up without me maintaining a block list:
`LOGS`, `BAMBOO`, `PLANKS`, `WOODEN_FEATURES`, `LEAVES`, `WOOL`, `CARPETS`, `SAPLINGS`, `PLANTS`,
`CROPS`. A block matching several of them takes the first match in that order.

Logs come from `#minecraft:logs_that_burn`, not `#minecraft:logs`. The latter drags in crimson and
warped stems, which are meant to be fireproof. Planks and worked wood are also checked against
`#minecraft:non_flammable_wood`, so their crimson and warped variants stay fireproof. Ground cover
uses the narrower `#conflagration:plants` tag; using `#minecraft:replaceable_by_trees` here would
accidentally classify water, seagrass, and Nether roots as fuel. Datapacks can extend the
Conflagration tag for modded plants.

Crops are inert in vanilla. `AGGRESSIVE` and `INFERNO` give them real values, which means wheat
fields burn now. That's a genuine gameplay change and probably the first thing your players will
complain about.

### Per-block control

```toml
[blocks]
    # Exact values for specific blocks, whatever the preset says.
    overrides = ["minecraft:oak_log=80,10", "create:andesite_casing=0,0"]
    # Never touched. Beats overrides.
    blacklist = ["minecraft:bookshelf"]
```

Malformed entries get a warning and are skipped. A typo here will never stop your server booting.
Unknown block ids and the forbidden `minecraft:air` target are also warned and ignored. Values are
accepted from 0 through 300; zero/zero makes a block inert.

### Performance

```toml
[performance]
    optimize_neighbour_scans = true
    engine = "VANILLA"
    frontier_spread_speed = 2.0
```

Vanilla computes each of a candidate air block's six neighbours twice. The default optimization
reuses the first immutable position for the second lookup and reuses the read-only direction array.
It changes no world reads, hook calls, scan order, random calls, or fire odds. Set it to `false` as
a per-pack escape hatch; the mixin stays loaded but delegates every operation back to vanilla.

`engine = "FRONTIER"` enables an experimental, behavior-changing spread engine. It discovers
viable source/target edges occasionally, samples deterministic ignition-arrival times, deduplicates
them by target, and processes them through a bounded primitive timing wheel. That trades vanilla's
repeated 53-position scans for sparse scheduled work. It is not bit-for-bit vanilla and remains off
by default. Exact, fail-closed adapters preserve FTB Chunks, Open Parties and Claims, and Flan claim
checks. `AUTO_STRICT` falls back to `VANILLA` for an incompatible adapter version or a known
unaudited claim/special-fire seam; every blocker is logged with mod name, id, version, reason, and
the adapter needed for future support. See [`docs/FIRE_ENGINE.md`](docs/FIRE_ENGINE.md) for the
algorithm, limits, research basis, compatibility matrix, and unsafe override.

`frontier_spread_speed` is an arrival-rate multiplier used only by FRONTIER. `1.0` is the
vanilla-speed baseline (FRONTIER still is not bit-for-bit vanilla); the default `2.0` halves the
mean ignition delay and `4.0` quarters it while retaining the engine's exponential timing. It
changes newly discovered arrivals, not events already waiting in the queue. The accepted range is
`0.05`–`20.0`; queue and per-tick budgets remain hard safety limits at every speed.

### FTB Chunks

```toml
[integration]
    claim_fire_protection = "ENABLE"
```

If FTB Chunks is installed, Conflagration drives its `fire_spread_protection` setting. It defaults
to `ENABLE`, because aggressive fire plus unprotected claims means somebody's base burns down while
they're offline and you get to hear about it. `LEAVE_ALONE` if you'd rather manage it yourself,
`DISABLE` if you dislike your players. FTB Chunks is a soft dependency reached by reflection; on a
server without it, the option is ignored.

## Compatibility

Flammability tuning still uses the public `FireBlock#setFlammable` API. The default performance
layer uses three narrow, composable MixinExtras wrappers around allocations inside the private
neighbour helper. It does not replace `FireBlock.tick`, `checkBurnOut`, the helper itself, or any
contextual NeoForge fire hook. The opt-in FRONTIER injection runs only after vanilla lifecycle and
six face-sensitive burnout calls, then replaces the candidate loop under the compatibility policy
above. This boundary was chosen around the actual mixins used by FTB Chunks, Open Parties and
Claims, Flan, Supplementaries, and The Bumblezone. Known fire/performance mods are detected and
reported at startup, and both optimizations have config escape hatches.

Values are applied on the server side of `TagsUpdatedEvent`, so `/reload` re-applies them without a
restart. Before reapplying, Conflagration restores every value it still owns. Disabling the mod,
choosing `VANILLA`, adding a blacklist, removing an override, or removing a datapack tag therefore
takes effect in the same process. A later write by another mod is preserved and adopted as the new
baseline. Pulling Conflagration out still restores startup behavior on the next boot.

If another mod also sets flammability for the same block, last write wins, and I can't tell you
which of us that'll be. Set `log_applied_values = true` once and read the log. If something has
replaced `minecraft:fire` outright, Conflagration logs an error and does nothing rather than
breaking that mod.

## Performance

Fire is one of the heavier vanilla block ticks before you touch anything. From the decompiled
1.21.1 sources: one fire block scans 53 candidate positions per tick, costing up to 371
`getBlockState` calls. The inner helper alone can allocate 636 relative positions and 53 cloned
direction arrays. Conflagration removes half of those position allocations and all of those array
clones without caching world state or bypassing mod hooks. Fire only ticks every 30-39 game ticks, so
steady state lands around `active_fire_blocks × 11` block-state reads per tick.

Higher flammability means more blocks alight at once, which means more of that. If you're running
`INFERNO` on a populated server, measure it with [spark](https://spark.lucko.me/docs) instead of
guessing:

```
/spark profiler start --only-ticks-over 100 --timeout 120
```

Look for `FireBlock.tick` in the flame graph. The transparent allocation optimization is enabled by
default; behavior-changing load shedding is not part of it.

## Building

```bash
./gradlew build                       # 1.21.1
./gradlew test                        # unit tests only
./gradlew build -Pminecraft_version=1.21.4 -Pneoforge_version=21.4.157
```

Jars land in `build/libs/`.

The `dev.boredhero.conflagration.policy` package has no Minecraft imports, deliberately. Preset
tables, value clamping, ownership restoration, config parsing and category precedence are plain
Java, so the tests run in seconds with no game harness. CI runs them across the version matrix on
every PR. Each jar now declares only its exact Minecraft and NeoForge patch line; mixin-enabled jars
must not claim the old overlapping `[1.21.x,1.22)` ranges.

`master` and `develop` are protected. Work on a branch and open a PR.

## License

[GPL-3.0-or-later](LICENSE). Logo and banner live in [`assets/`](assets/).
