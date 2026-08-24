# Changelog

All notable changes to this project are documented here.
Format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/);
versioning follows [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- Allocation-safe fire neighbour scanning using composable MixinExtras wrappers, with a runtime
  config escape hatch and startup detection of known performance/fire mixin mods.
- A default-on experimental `FRONTIER` spread engine using deterministic arrival sampling, target
  deduplication, primitive timing-wheel storage, per-level budgets, hard queue caps, and strict
  compatibility fallback with actionable per-mod blocker logs.
- Fail-closed FRONTIER claim adapters for FTB Chunks, Open Parties and Claims, and Flan, plus
  explicit blockers for unaudited claim and hybrid-server fire seams.
- A `frontier_spread_speed` hazard-rate multiplier that changes ignition speed without discarding
  FRONTIER's exponential arrival behavior or bypassing its work limits.
- Distance-penalized FRONTIER ember jumps across short non-flammable gaps, a composable VANILLA
  ignition-speed multiplier, and explicit bed flammability through the wool fuel category.
- Vanilla-client ember arcs for successful FRONTIER jumps, with a bounded per-level packet budget.
- A datapack-extensible kindling category for ladders and torch variants.
- A sparse radiant-heat field with inverse-square source aggregation, standards-shaped entity
  exposure dose compressed for short Minecraft flame lifetimes, line-of-sight attenuation, and
  vanilla fire-damage semantics.
- Sustained thermal fracture for ordinary glass blocks and panes, including break sound/particles,
  strict claim fallback, hard work budgets, datapack allow/immune tags, and a cancellable event.
- Gameplay-scaled entity heat plus bounded, roof-sensitive vanilla smoke haze and severe indoor
  vision loss, requiring no client mod.
- Configurable wooden chest burning, default destructive inventory loss, and standard-tag flower
  consumption without item drops.
- A dedicated `#conflagration:plants` tag for safe datapack extension.

### Fixed
- Restore values owned by Conflagration when disabling it, selecting `VANILLA`, changing overrides
  or blacklists, or reloading changed tags, without clobbering a later write from another mod.
- Keep crimson/warped wood, water, aquatic plants, Nether roots, and chorus flowers fireproof.
- Ignore and warn about unknown block overrides and the invalid `minecraft:air` target.
- Apply FTB Chunks integration only while Conflagration is enabled and ignore duplicate client-side
  static tag update events.
- Allow meaningful NeoForge fire odds through 300 and narrow each built jar's declared game/API
  version ranges to the patch line it was compiled against.
- Consume doors, ladders, torches, and their synchronous support-loss counterparts without item
  drops when fire removes them; mature crops likewise cannot drop food or seeds.
- Make FRONTIER the new-install default while retaining VANILLA and strict automatic compatibility
  fallback; correctly version-gate FTB Chunks fire ownership at 2101.1.15.

## [1.0.0] - 2026-08-23

### Added
- Per-block flammability tuning via `FireBlock#setFlammable`, applied on tag load so it
  survives `/reload` and picks up datapack tag changes.
- Four presets: `VANILLA`, `SMOULDERING`, `AGGRESSIVE` (default), `INFERNO`, plus `CUSTOM`.
- Tag-driven fuel categories, so modded woods are covered automatically. Uses
  `#minecraft:logs_that_burn` rather than `#minecraft:logs`, leaving crimson and warped
  stems fireproof as vanilla intends.
- Per-block overrides (`namespace:block=ignite,burn`) and a blacklist that takes priority
  over everything else.
- Optional FTB Chunks integration driving `fire_spread_protection`, reached by reflection
  so FTB Chunks remains a soft dependency.
- Unit test suite covering the whole policy layer, run in CI across the version matrix.

### Notes
- No mixins. Everything uses public API, so there is nothing to conflict with other mods
  at the bytecode level.
- 1.21.1 is the verified reference target. 1.21.4 and 1.21.8 builds are best-effort.

[Unreleased]: https://github.com/boredhero/conflagration/compare/v1.0.0...HEAD
[1.0.0]: https://github.com/boredhero/conflagration/releases/tag/v1.0.0
