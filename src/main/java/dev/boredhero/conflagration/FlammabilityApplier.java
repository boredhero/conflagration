package dev.boredhero.conflagration;

import dev.boredhero.conflagration.config.ConflagrationConfig;
import dev.boredhero.conflagration.policy.FlammabilityPolicy;
import dev.boredhero.conflagration.policy.FuelCategory;
import dev.boredhero.conflagration.policy.Odds;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;

import java.util.EnumSet;
import java.util.Set;

/**
 * Pushes the resolved {@link FlammabilityPolicy} into the game.
 *
 * <p>Minecraft keeps flammability in a private {@code Object2IntMap} inside {@code FireBlock},
 * populated once during bootstrap. It is not data-driven: no tag, datapack, gamerule or JSON can
 * reach it. {@code FireBlock#setFlammable} is however {@code public} in 1.21.1, so this needs no
 * access transformer, no reflection and no mixin - which is precisely why it cannot conflict with
 * other mods at the bytecode level.
 *
 * <p>Two consequences worth knowing:
 * <ul>
 *   <li>The table is global and rebuilt from scratch at every launch, so removing this mod
 *       restores vanilla behaviour on the next restart with nothing left behind.</li>
 *   <li>If another mod also calls {@code setFlammable} for the same block, last write wins.
 *       Applying on tag load (rather than during mod construction) means we generally run after
 *       other mods' setup, but this is load-order dependent - hence
 *       {@code log_applied_values} for diagnosing surprises.</li>
 * </ul>
 */
public final class FlammabilityApplier {

    /**
     * Tags that make up {@link FuelCategory#WOODEN_FEATURES}: the worked-wood blocks a building is
     * actually made of, as opposed to raw planks.
     */
    private static final TagKey<Block>[] WOODEN_FEATURE_TAGS = tags(
            BlockTags.WOODEN_STAIRS,
            BlockTags.WOODEN_SLABS,
            BlockTags.WOODEN_FENCES,
            BlockTags.FENCE_GATES,
            BlockTags.WOODEN_DOORS,
            BlockTags.WOODEN_TRAPDOORS,
            BlockTags.WOODEN_BUTTONS,
            BlockTags.WOODEN_PRESSURE_PLATES);

    private FlammabilityApplier() {
    }

    /**
     * Applies the configured policy to every registered block.
     *
     * @return the number of blocks whose values were changed
     */
    public static int apply(Logger log) {
        if (!ConflagrationConfig.ENABLED.get()) {
            log.info("[Conflagration] disabled by config; fire left as vanilla");
            return 0;
        }

        FlammabilityPolicy policy = ConflagrationConfig.buildPolicy();
        policy.warnings().forEach(warning -> log.warn("[Conflagration] {}", warning));

        if (policy.isNoOp()) {
            log.info("[Conflagration] preset is VANILLA with no overrides; nothing to change");
            return 0;
        }

        FireBlock fire = fireBlock();
        if (fire == null) {
            log.error("[Conflagration] minecraft:fire is not a FireBlock - another mod has "
                    + "replaced it. Skipping, to avoid breaking that mod.");
            return 0;
        }

        boolean verbose = ConflagrationConfig.LOG_APPLIED_VALUES.get();
        int changed = 0;

        for (Block block : BuiltInRegistries.BLOCK) {
            Set<FuelCategory> categories = categorise(block.defaultBlockState());
            if (categories.isEmpty() && policy.overrides().isEmpty()) {
                continue;
            }

            ResourceLocation key = BuiltInRegistries.BLOCK.getKey(block);
            if (key == null) {
                continue;
            }

            Odds odds = policy.resolve(key.toString(), categories).orElse(null);
            if (odds == null) {
                continue;
            }

            // setFlammable(block, encouragement/ignite, flammability/burn)
            fire.setFlammable(block, odds.ignite(), odds.burn());
            changed++;

            if (verbose) {
                log.info("[Conflagration]   {} -> ignite={} burn={}", key, odds.ignite(), odds.burn());
            }
        }

        log.info("[Conflagration] preset {} applied to {} blocks", policy.preset(), changed);
        return changed;
    }

    /** Null when another mod has replaced {@code minecraft:fire} with something else. */
    private static FireBlock fireBlock() {
        return Blocks.FIRE instanceof FireBlock fb ? fb : null;
    }

    /**
     * Buckets a block into every category it belongs to. {@link FlammabilityPolicy} decides which
     * one wins, using {@link FuelCategory} declaration order.
     *
     * <p>Uses {@code #minecraft:logs_that_burn} rather than {@code #minecraft:logs}: the latter
     * includes crimson and warped stems, which are deliberately fireproof. Tag-based matching also
     * means modded woods are picked up automatically, since mods add their blocks to these tags.
     */
    private static Set<FuelCategory> categorise(BlockState state) {
        EnumSet<FuelCategory> categories = EnumSet.noneOf(FuelCategory.class);

        if (state.is(BlockTags.LOGS_THAT_BURN)) {
            categories.add(FuelCategory.LOGS);
        }
        if (state.is(BlockTags.BAMBOO_BLOCKS)) {
            categories.add(FuelCategory.BAMBOO);
        }
        if (state.is(BlockTags.PLANKS)) {
            categories.add(FuelCategory.PLANKS);
        }
        for (TagKey<Block> tag : WOODEN_FEATURE_TAGS) {
            if (state.is(tag)) {
                categories.add(FuelCategory.WOODEN_FEATURES);
                break;
            }
        }
        if (state.is(BlockTags.LEAVES)) {
            categories.add(FuelCategory.LEAVES);
        }
        if (state.is(BlockTags.WOOL)) {
            categories.add(FuelCategory.WOOL);
        }
        if (state.is(BlockTags.WOOL_CARPETS)) {
            categories.add(FuelCategory.CARPETS);
        }
        if (state.is(BlockTags.SAPLINGS)) {
            categories.add(FuelCategory.SAPLINGS);
        }
        if (state.is(BlockTags.FLOWERS) || state.is(BlockTags.REPLACEABLE_BY_TREES)) {
            categories.add(FuelCategory.PLANTS);
        }
        if (state.is(BlockTags.CROPS)) {
            categories.add(FuelCategory.CROPS);
        }

        return categories;
    }

    @SafeVarargs
    private static TagKey<Block>[] tags(TagKey<Block>... values) {
        return values;
    }
}
