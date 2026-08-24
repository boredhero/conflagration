# Radiant heat and thermal fracture

Conflagration models the hazard around a fire as incident radiant heat flux plus accumulated
exposure, not as a fictional radial air-temperature falloff. Real fire size is heat-release rate:
NIST's overview notes that a candle and ten candles can have similar flame temperatures while the
group releases roughly ten times the heat. Typical flame temperatures span about 500-1400 C, so
temperature by itself does not tell you how close is safe.

## Source field

Each recently observed fire block contributes a gameplay-calibrated radiative power `Q = 12.5 kW`.
For source-target distance `R` in blocks/metres:

```text
q = Q * ageWeight * densityMultiplier / (4 pi max(R, 0.75)^2)
```

This is the standard far-field point-source shape. Near a flame it is an approximation, so the
distance is softened and ordinary contact fire stays authoritative. Minecraft fire age scales a
source from 1.0 down to 0.5. A smooth, capped density multiplier represents mutual heating, while
the sum of all sources remains the main reason a large fire is dangerous. Far 4x4x4 cells are
aggregated; nearby cells retain exact voxel distances. Only loaded chunks are read.

The field can also be expressed as an equivalent black-body radiant temperature:

```text
T_eq = (T_ambient^4 + 1000 q / sigma)^(1/4)
```

That number increases with fire density, but it is a radiation metric, not a claim about local air
or literal flame chemistry.

## Living entities

Every 0.5 seconds, staggered by entity ID, heat above the default `2.5 kW/m^2` threshold adds dose:

```text
dose += q^(4/3) * elapsed_minutes
```

At the reference dose `1.33`, the entity receives a two-second vanilla ignition pulse. The shipped
default is `0.45`, which reaches a pulse in roughly 4.3 seconds at 4 kW/m² and 1.3 seconds at
10 kW/m² so short-lived Minecraft flame blocks can produce a noticeable hazard. Mapping the
pain/tolerance dose to Minecraft health is a gameplay decision; the exponent, units, and selected
threshold come from ISO/SFPE-style tenability work. Vanilla then owns actual damage, so fire-immune
entities, Fire Resistance, creative immunity, damage events, and water/rain keep working. Dose
cools with a 10-second half-life below 1.7 kW/m²; this represents skin cooling, not healing already
lost health. Animals use the same default because no defensible universal cross-species radiant
threshold exists.

The shipped `entity_heating_multiplier = 4.0` is an explicit gameplay calibration applied after
the physical source field and before dose. It makes the effect noticeable around Minecraft's
short-lived structure fires. Set it to `1.0` for the reference field described by the equation.

NIST summarizes roughly 1 kW/m² as strong sunlight, about 2.5 kW/m² as a common firefighter
exposure, 3-5 kW/m² as pain within seconds, and about 20 kW/m² near floor-level flashover:
<https://www.nist.gov/el/fire-research-division-73300/firegov-fire-service/fire-dynamics>.
NASA's SFPE-derived table reports pain at about 9 seconds under 4 kW/m² and 1.3 seconds under
10 kW/m²: <https://ntrs.nasa.gov/api/citations/20110008043/downloads/20110008043.pdf>.

## Glass

Fire-exposed glazing usually fails from a hot-center/cool-edge gradient, not one universal glass
temperature. Conflagration integrates a first-order ordinary 4 mm soda-lime model:

```text
deltaT_ss = absorption * q * timeConstant / arealHeatCapacity
deltaT_new = deltaT_ss + (deltaT_old - deltaT_ss) exp(-dt / timeConstant)
```

Defaults are effective absorption `0.78`, time constant `240 s`, areal heat capacity
`8.2 kJ/(m^2 K)`, and fracture at `deltaT = 60 C`. That gives approximately 179 seconds at
5 kW/m² and 83 seconds at 9 kW/m²; 2.5 kW/m² asymptotes below fracture. Double-glazing experiments
reported roughly a 60 C critical gradient and about 6 kW/m² at fire-side crack initiation:
<https://doi.org/10.1016/j.applthermaleng.2016.09.079>. A UK government/BRE review reports single
glazing failure under three minutes at 5 kW/m² and under two minutes at 9 kW/m²:
<https://assets.publishing.service.gov.uk/media/693001ae375aee4a15ee8ad2/fire-safety-trigger-thresholds.pdf>.

Those reference times use `glass_heating_multiplier = 1.0`. Minecraft structure fires often last
far less than two minutes, so the shipped default is an explicit `8.0` gameplay time compression:
the same differential equation and 60 C criterion receive eight times the incident heating. This
is intentionally more aggressive than real glazing and makes village windows fail while the house
is still visibly involved.

Minecraft glass blocks are not literal architectural panes, so applying the same behavior to them
is intentionally cinematic. The default allowlist is `#c:glass_blocks/cheap` plus
`#c:glass_panes`; reinforced or functional glass should join
`#conflagration:thermal_fracture_immune`.

## Performance and compatibility

- primitive per-level source/cell maps; no per-fire entity AABB queries;
- exact sources within four blocks, one aggregate per farther 4x4x4 cell;
- staggered entity sampling and one line-of-sight ray only after a harmful broad-phase result;
- rotating glass-cell scan with a default 256 block-read budget per level per tick;
- a hard 100,000-source cap, 50-tick stale expiry, weak level ownership, and no chunk loads;
- source/target claim checks and a public cancellable `ThermalFractureEvent` before destruction;
- unaudited claim or hybrid-server seams disable glass fracture, not entity heat;
- `destroyBlock(..., false)` supplies the glass block's own break sound, particles, neighbor updates,
  and game event without item drops.

The point-source model and the common 20-30% radiative fraction are standard fire-protection
approximations; FEMA notes that the far-field assumption is best beyond roughly 2.5 fire diameters:
<https://apps.usfa.fema.gov/ax/sm/sm_r0204.pdf>. NIST's fire-model catalog also explains why a
full glass-breakage model needs construction-specific material and frame inputs Minecraft does not
have: <https://www.nist.gov/el/fire/fire-modeling-programs>.

## Smoke haze

Smoke visuals use vanilla `LARGE_SMOKE` particles in a single batched server packet per exposed
player per second. Open-sky exposure is strongly dispersed; a roof above the player's eyes applies
an indoor concentration factor. Severe indoor exposure refreshes a short, hidden vanilla Blindness
effect instead of using Darkness's pulsing or Nausea. Both particle count and the two thresholds
are bounded/configurable, and leaving the smoky area clears the effect within about two seconds.
No custom packet, texture, or client-side mod is required.
