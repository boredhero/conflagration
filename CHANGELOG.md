# Changelog

All notable changes to this project are documented here.
Format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/);
versioning follows [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

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
