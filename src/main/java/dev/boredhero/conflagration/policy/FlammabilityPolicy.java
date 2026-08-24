package dev.boredhero.conflagration.policy;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Decides the final {@link Odds} for a block. Pure logic, no Minecraft types, so the whole
 * resolution order is unit-testable without a game harness.
 *
 * <p>Resolution order, highest priority first:
 * <ol>
 *   <li><b>Blacklist</b> — the block is never touched, whatever else says.</li>
 *   <li><b>Per-block override</b> — an explicit {@code id=ignite,burn} entry.</li>
 *   <li><b>Category table</b> — the first matching {@link FuelCategory} in enum declaration
 *       order, looked up in the active {@link FirePreset} (or the custom table).</li>
 * </ol>
 */
public final class FlammabilityPolicy {

    private final FirePreset preset;
    private final Map<FuelCategory, Odds> categoryOdds;
    private final Map<String, Odds> overrides;
    private final Set<String> blacklist;
    private final List<String> warnings;

    private FlammabilityPolicy(FirePreset preset,
                               Map<FuelCategory, Odds> categoryOdds,
                               Map<String, Odds> overrides,
                               Set<String> blacklist,
                               List<String> warnings) {
        this.preset = preset;
        this.categoryOdds = categoryOdds;
        this.overrides = overrides;
        this.blacklist = blacklist;
        this.warnings = List.copyOf(warnings);
    }

    public static Builder builder(FirePreset preset) {
        return new Builder(preset);
    }

    /**
     * @param blockId    the block's registry id, e.g. {@code minecraft:oak_log}
     * @param categories every category this block belongs to, in any order
     * @return the odds to apply, or empty when the block should be left alone
     */
    public Optional<Odds> resolve(String blockId, Collection<FuelCategory> categories) {
        String id = BlockOverride.normaliseId(blockId);

        if (blacklist.contains(id)) {
            return Optional.empty();
        }

        Odds override = overrides.get(id);
        if (override != null) {
            return Optional.of(override);
        }

        // Enum declaration order is precedence; iterate the enum rather than the argument so
        // callers can pass categories in any order and still get a deterministic answer.
        for (FuelCategory category : FuelCategory.values()) {
            if (categories.contains(category)) {
                return Optional.ofNullable(categoryOdds.get(category));
            }
        }
        return Optional.empty();
    }

    public FirePreset preset() {
        return preset;
    }

    public Odds forCategory(FuelCategory category) {
        return categoryOdds.get(category);
    }

    public Set<String> blacklist() {
        return blacklist;
    }

    public Map<String, Odds> overrides() {
        return overrides;
    }

    /** Non-fatal problems found while building, e.g. malformed config entries. */
    public List<String> warnings() {
        return warnings;
    }

    /** True when applying this policy would leave the game in stock condition. */
    public boolean isNoOp() {
        return preset.isVanilla() && overrides.isEmpty();
    }

    public static final class Builder {
        private final FirePreset preset;
        private final Map<FuelCategory, Odds> categoryOdds;
        private final Map<String, Odds> overrides = new LinkedHashMap<>();
        private final Set<String> blacklist = new LinkedHashSet<>();
        private final List<String> warnings = new ArrayList<>();

        private Builder(FirePreset preset) {
            this.preset = preset;
            this.categoryOdds = new java.util.EnumMap<>(preset.table());
        }

        /** Overrides a single category, used when the preset is {@link FirePreset#CUSTOM}. */
        public Builder category(FuelCategory category, Odds odds) {
            categoryOdds.put(category, odds);
            return this;
        }

        /** Parses raw {@code id=ignite,burn} config lines, collecting warnings for bad entries. */
        public Builder overrides(Iterable<? extends String> rawEntries) {
            for (String raw : rawEntries) {
                BlockOverride.Result result = BlockOverride.parse(raw);
                if (result.ok()) {
                    BlockOverride override = result.value().orElseThrow();
                    overrides.put(override.blockId(), override.odds());
                } else {
                    warnings.add("ignoring override: " + result.error());
                }
            }
            return this;
        }

        public Builder blacklist(Iterable<? extends String> rawIds) {
            for (String raw : rawIds) {
                if (raw == null || raw.isBlank()) {
                    continue;
                }
                String id = BlockOverride.normaliseId(raw.trim());
                if (BlockOverride.isValidId(id)) {
                    blacklist.add(id);
                } else {
                    warnings.add("ignoring blacklist entry: \"" + raw + "\" is not a valid block id");
                }
            }
            return this;
        }

        public FlammabilityPolicy build() {
            return new FlammabilityPolicy(preset, categoryOdds, overrides, blacklist, warnings);
        }
    }
}
