# Conflagration — Logo & Brand Assets

## Research notes

- **Modrinth** displays project icons small and square (list/search rows render
  around ~64–96px; the project page itself only blows them up to ~140px). There
  is no hard minimum/maximum enforced today, but square, high-contrast, simple
  silhouettes are the de facto convention because everything is judged at thumbnail
  size first.
- **CurseForge** mod icons are likewise square; CurseForge's own upload guidance
  for pack icons calls for **at least 400×400**, and mod icons follow the same
  square convention scaled down in listings. 512×512 covers both platforms with
  headroom.
- **NeoForge's in-game mod list** (`neoforge.mods.toml` → `logoFile`) renders the
  icon at a small fixed size next to a wall of other mods, competing for
  attention at a glance — same lesson as Modrinth: bold shapes, few colors, no
  fine detail that disappears under 100px.
- **GitHub social preview / README banners** are wide (GitHub crops to
  1280×640), opaque (no transparency — GitHub composites on its own chrome),
  and give room for a wordmark next to the mark, unlike the square icon.
- **Minecraft's own visual language**: textures are authored on a native 16×16
  pixel grid with a handful of flat, saturated colors and hard pixel edges (no
  anti-aliasing, no gradients within a texture). Vanilla fire leans on hard
  reds/oranges/yellows with almost no desaturated tones. That grid-and-flat-color
  discipline is the single most recognizable "Minecraft" visual cue available
  to a mod icon, more so than trying to reproduce the game's blocky title font
  (which is trademarked/bundled, not something to embed).

## Concepts considered

1. **Pixel-art flame consuming a log** (chosen). A chunky, layered flame
   silhouette sitting directly on a charred log with a couple of visible embers
   and small flame licks creeping onto the log's edges. Reads as "fire actively
   eating its fuel" — the mod's core mechanic — in one glance, and is built
   entirely from axis-aligned rectangles on a 16×16 grid, so it stays crisp at
   any raster size and degrades gracefully to a handful of big color blocks at
   very small sizes.
2. **Burning tree / house silhouette.** More narratively on-the-nose (this is
   literally what the mod does to a house or forest), but a recognizable tree
   or building silhouette needs enough outline detail that it turns to mud
   under ~100px — it wants to be a banner illustration, not an icon.
3. **Stylized fire "C" lettermark.** Very common lettermark move (a flame
   bending into the shape of the mod's initial). Distinctive as a piece of
   branding, but it reads as a generic "letter" before it reads as "fire", and
   loses the log/fuel storytelling entirely.

**Decision:** concept 1. It is the only one of the three whose entire
information content (bright flame mass + dark fuel mass + a couple of ember
accents) survives being shrunk to a 64px thumbnail, which is the size it will
actually be judged at on Modrinth, CurseForge, and the NeoForge mod list. Concept
2 was kept in mind for the *banner* composition (the flame glow behind the
wordmark gestures at the "whole scene burning" idea) without forcing that
complexity into the small icon.

## Palette

| Role | Hex | Used for |
|---|---|---|
| Outer flame | `#E8420C` | Flame silhouette edge — deep red-orange |
| Mid flame | `#FF7A1A` | Flame body — vivid orange |
| Inner flame | `#FFB238` | Flame core — bright orange-yellow |
| Hotspot highlight | `#FFE9A8` | Small hot-core accent + flame tip |
| Log — mid brown | `#6B4423` | Log body |
| Log — char brown | `#452B18` | Log shading / char |
| Char / near-black | `#1C120A` | Log underside, ground shadow |
| Ember accent | `#FF5A1F` | Glowing embers in the log, edge licks |
| Banner background | `#1C1512` → `#0B0705` | Vertical gradient, banner only |
| Tagline text | `#C98B5A` | Banner subtitle |
| Meta text | `#8A5A34` | Banner footer line |

All flame/log tones are flat, saturated, and high-contrast against both light
and dark surroundings by design (checked against a dark `#2B2B2B` swatch to
mimic the NeoForge/Modrinth dark UI, and against white).

## Construction

- `icon.svg` — the mark itself: a 16×16 unit grid (`viewBox="0 0 16 16"`), one
  `<rect>` per pixel, `shape-rendering="crispEdges"`. No gradients, no filters,
  no embedded assets — fully self-contained and hand-authored. Transparent
  background (standard for mod icons).
- `banner.svg` — 1280×640. Reuses the exact same icon rect data (scaled up
  ~20×) so the banner mark and the small icon are pixel-identical in shape, not
  a redrawn approximation. Adds a dark charcoal gradient background, a soft
  radial ember glow behind the mark, and a "CONFLAGRATION" wordmark set in
  Liberation Sans Bold (SIL OFL, freely embeddable), base64-embedded via
  `@font-face` directly in the SVG so the file has zero external
  dependencies and renders identically anywhere.

## Rasterization

Rendered with **`rsvg-convert`** (found on this machine; `inkscape` was not
installed, `magick`/`convert` was used only for verification/compositing
checks, not the primary raster pass).

```bash
rsvg-convert -w 256  -h 256  assets/icon.svg   -o src/main/resources/logo.png
rsvg-convert -w 512  -h 512  assets/icon.svg   -o assets/icon-512.png
rsvg-convert -w 1280 -h 640  assets/banner.svg -o assets/banner.png
```

## Files

| File | Size | Purpose |
|---|---|---|
| `assets/icon.svg` | vector | Source of truth for the mark |
| `assets/banner.svg` | vector | Source of truth for the README/social banner |
| `src/main/resources/logo.png` | 256×256, RGBA | In-game NeoForge mod-list logo (`neoforge.mods.toml` → `logoFile`) |
| `assets/icon-512.png` | 512×512, RGBA | CurseForge / Modrinth project icon upload |
| `assets/banner.png` | 1280×640, RGB (opaque) | GitHub social preview / README header |

## Small-size legibility check

Checked by downscaling the icon to 64×64 and 32×32 (Minecraft's own native
texture resolution) and by compositing the transparent PNG over a dark
`#2B2B2B` swatch to mimic Modrinth/NeoForge's dark UI at ~96px.

**Honest assessment:** it holds up well. At 32px and 64px the flame-over-log
silhouette is still immediately readable as "fire eating wood" — the three
flame tones read as one warm gradient-like mass, the log stays legible as a
darker block underneath, and the two ember dots survive as small bright flecks
rather than dissolving into noise. The one soft spot: the two 1px flame "licks"
on the log's left/right edges are a nice detail at 512px but are the first
thing to get lost at 32px — they're a bonus flourish, not load-bearing, so
losing them at the smallest size costs nothing structurally. If this ever
needs to go smaller than 32px (e.g. a 16px favicon), I'd drop those edge licks
and the inner hotspot fleck and keep just the three-tone flame + log block,
since those two elements are the ones doing the actual identification work.
