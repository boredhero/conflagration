# Experimental frontier fire engine

`[performance].engine = "FRONTIER"` replaces only vanilla's repeated 53-position air spread loop.
It is disabled by default. Fire lifecycle, survival, rain extinction, age, fire sources, scheduled
ticks, and the six face-sensitive direct burnout checks still run in `FireBlock.tick`.

## Model

FRONTIER treats a viable source-fire/target-air relationship as a directed graph edge. Discovering
an edge samples one deterministic arrival delay. Arrivals are deduplicated by target (earliest
wins), stored in a primitive timing wheel, and completely revalidated before fire is placed.
Sources are rescanned at a configurable interval so changed surroundings are eventually observed,
without repeating vanilla's full candidate scan every 30–39 ticks.

The delay distribution is derived from vanilla's ignite odds, age, difficulty, vertical penalty,
and increased-burnout biome adjustment. It is statistically vanilla-shaped, not bit-for-bit
vanilla. A positional hash based on the world seed and source/target positions makes results stable
when queue budgets change; no shared world RNG is consumed.

`frontier_spread_speed` multiplies the stochastic arrival rate. This is a hazard-rate control, not
a scan-frequency shortcut: `1.0` is the vanilla-speed baseline, the default `2.0` gives half the
mean wait, and `4.0` gives one quarter while keeping the same exponential distribution. FRONTIER
itself remains statistically vanilla-shaped rather than bit-for-bit vanilla. The setting applies
when an edge is discovered; already queued arrivals keep their sampled due time. Queue caps and
per-tick work budgets still apply at every setting.

This is closer to a minimum-travel-time/event simulation than a synchronous cellular automaton:

- Finney's minimum-travel-time formulation motivates earliest-arrival propagation over a graph:
  <https://doi.org/10.1139/X02-068>
- Gillespie's stochastic simulation method motivates sampling event time once rather than retrying
  failed probabilities every fixed step: <https://doi.org/10.1021/j100540a008>
- A cellular wildfire model can express heterogeneous fuel and terrain, but still pays for grid
  updates and introduces synchronous-step artifacts: <https://doi.org/10.1016/S0304-3800(96)01942-4>
- Rothermel/FARSITE models were rejected: Minecraft does not expose the physical fuel moisture,
  wind, slope, and heat inputs needed to justify their extra machinery. Background:
  <https://research.fs.usda.gov/treesearch/32533> and <https://www.fs.usda.gov/rm/pubs/rmrs_rp004.pdf>

## Data structures and limits

- packed `BlockPos` longs throughout queue state;
- 2,048-bucket timing wheel with parallel primitive arrays (no event objects after growth);
- primitive earliest-arrival map for target deduplication;
- deterministic per-level event/source budgets;
- hard pending-event cap;
- weak per-level ownership, and no chunk loads, asynchronous world access, or cross-tick
  `BlockState` cache.

Events whose chunks are not already loaded expire. Queue work over budget remains pending in stable
order. It is never silently selected by wall-clock timing, which would make the same world behave
differently across hardware.

## Compatibility policy

FTB Chunks, Open Parties and Claims, and Flan have exact fail-closed adapters. Their official
fire-protection helpers are resolved once at startup and called for every candidate and again
before placement. Permission verdicts are never cached, so ownership and flag changes take effect
immediately. If a helper is absent in an incompatible provider version, `AUTO_STRICT` refuses
FRONTIER instead of guessing. A runtime adapter error denies that ignition, logs once, disables
FRONTIER, and lets subsequent fire ticks resume VANILLA.

`frontier_compatibility = "AUTO_STRICT"` also refuses FRONTIER for unaudited seams: Cadmus,
GriefDefender, Claim My Land, Supplementaries, Connector, and hybrid Bukkit WorldGuard/Towny/
GriefDefender installations. Those providers combine contextual claim rules, block-placement
events, or special fire behavior that direct placement cannot generically reproduce.

Audited provider seams:

- [FTB Chunks `FireSpreadHelper`](https://github.com/FTBTeam/FTB-Chunks/blob/1.21.1/main/common/src/main/java/dev/ftb/mods/ftbchunks/util/FireSpreadHelper.java)
- [Open Parties and Claims `ServerCore.canSpreadFire`](https://github.com/thexaero/open-parties-and-claims/blob/1.21/Common/src/main/java/xaero/pac/common/server/core/ServerCore.java)
- [Flan `WorldEvents.canFireSpread`](https://github.com/Flemmli97/Flan/blob/1.21/common/src/main/java/io/github/flemmli97/flan/event/WorldEvents.java)
- [Cadmus operation-specific fire mixin](https://github.com/terrarium-earth/Cadmus/blob/1.21.x/neoforge/src/main/java/earth/terrarium/cadmus/mixins/common/neoforge/protections/FireBlockMixin.java)
- [Claim My Land placement handler](https://github.com/gottsch/gottsch-minecraft-Claim-My-Land/blob/neoforge-1.21.1-main/src/main/java/mod/gottsch/neo/claimmyland/core/event/ModEvents.java)

Lithium, ModernFix, FerriteCore, Canary/Radium, and ServerCore are detected and reported but do not
disable FRONTIER merely for being performance mods. `FORCE_UNSAFE` bypasses the conflict guard and
is intentionally blunt: fire may cross protected claims or skip special liquid behavior.

Fallback is logged one blocker at a time with display name, mod ID, detected version, the exact
vanilla seam it owns, and the adapter needed for future support. This is intentional operational
data: a generic conflict warning would not tell maintainers whether the missing work is a
source/target claim check, target-only claim check, liquid ignition hook, or transformed-class
audit.

## Validation target

VANILLA mode must remain an exact oracle: same reads, callbacks, RNG, and block results. FRONTIER is
validated statistically over fixed seeds: burned-block count, radius, duration, vertical gain,
queue cap hits, MSPT percentiles, contextual hook counts, and allocations. Compatibility smoke
tests should cover bare NeoForge, Lithium, ModernFix, FerriteCore, FTB Chunks, OpenPAC, Flan,
Cadmus, Claim My Land, Supplementaries/Moonlight, and Bumblezone before promoting FRONTIER out of
experimental status.
