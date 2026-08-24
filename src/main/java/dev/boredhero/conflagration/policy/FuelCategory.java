package dev.boredhero.conflagration.policy;

/**
 * Groups of blocks that share fire tuning.
 *
 * <p><b>Declaration order is precedence.</b> A block can legitimately match several categories
 * (a bamboo block is both {@link #BAMBOO} and, in some packs, tagged as a log), so
 * {@link FlammabilityPolicy} resolves against the first category in this enum that matches.
 * Order from most-specific to most-general.
 */
public enum FuelCategory {

    /**
     * Logs, stripped logs and wood blocks that are actually combustible.
     *
     * <p>Backed by {@code #minecraft:logs_that_burn} rather than {@code #minecraft:logs} —
     * the latter includes crimson and warped stems, which are deliberately fireproof.
     * This is the single most important category: vanilla logs sit at 5/5, which is why
     * forest canopies flash over but trunks survive.
     */
    LOGS,

    /** Bamboo blocks. Separate from logs so bamboo forests can be tuned independently. */
    BAMBOO,

    /** Planks. The structural material of most player builds and village houses. */
    PLANKS,

    /** Stairs, slabs, fences, gates, doors, trapdoors, buttons and pressure plates made of wood. */
    WOODEN_FEATURES,

    /** Small wooden attachments such as torches and ladders, consumed quickly and without drops. */
    KINDLING,

    /** Wooden chests and trapped chests; inventory destruction is controlled separately. */
    CHESTS,

    /** Leaves. Already quite flammable in vanilla (30/60). */
    LEAVES,

    /** Wool blocks and beds. Beds are inert in stock 1.21.1 but village fires should consume them. */
    WOOL,

    /** Wool carpets. Vanilla's most ignitable block (60/20). */
    CARPETS,

    /** Saplings. */
    SAPLINGS,

    /** Grass, ferns, flowers and similar ground cover — how fire crosses open ground. */
    PLANTS,

    /**
     * Farmland crops. Non-flammable in vanilla; giving these a non-zero value is an
     * intentional gameplay change that lets fields burn.
     */
    CROPS
}
