package dev.boredhero.conflagration.policy;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * Named tuning tables.
 *
 * <p>Each preset supplies an {@link Odds} pair for every {@link FuelCategory}. {@link #VANILLA}
 * reproduces Mojang's stock numbers, so selecting it restores unmodified behaviour without
 * needing a snapshot of the pre-modification state.
 *
 * <p>The design rule across the non-vanilla presets: <b>raise ignite far more than burn</b>.
 * High ignite makes fire travel; keeping burn moderate means fuel survives long enough for the
 * fire to actually consume a structure instead of flashing out.
 */
public enum FirePreset {

    /** Mojang's stock values. Selecting this restores vanilla behaviour. */
    VANILLA(table(
            /* LOGS            */ new Odds(5, 5),
            /* BAMBOO          */ new Odds(5, 5),
            /* PLANKS          */ new Odds(5, 20),
            /* WOODEN_FEATURES */ new Odds(5, 20),
            /* LEAVES          */ new Odds(30, 60),
            /* WOOL            */ new Odds(30, 60),
            /* CARPETS         */ new Odds(60, 20),
            /* SAPLINGS        */ new Odds(60, 100),
            /* PLANTS          */ new Odds(60, 100),
            /* CROPS           */ Odds.INERT)),

    /**
     * A restrained step up. Structures burn, but slowly, and fire is still survivable.
     * Good for servers that want consequence without losing builds to a stray flint and steel.
     */
    SMOULDERING(table(
            new Odds(15, 5),
            new Odds(15, 5),
            new Odds(15, 10),
            new Odds(15, 10),
            new Odds(40, 40),
            new Odds(35, 40),
            new Odds(60, 20),
            new Odds(60, 100),
            new Odds(60, 100),
            Odds.INERT)),

    /**
     * The intended default. A lit village house burns to the ground; a forest fire carries
     * through the canopy and down the trunks.
     */
    AGGRESSIVE(table(
            /* LOGS  — the key change: vanilla 5 ignite is why trunks never catch */
            new Odds(35, 8),
            new Odds(35, 8),
            new Odds(40, 12),
            new Odds(40, 15),
            new Odds(60, 45),
            new Odds(50, 45),
            new Odds(70, 30),
            new Odds(60, 100),
            new Odds(70, 100),
            new Odds(40, 60))),

    /**
     * Deliberately unreasonable. Nearly everything combustible catches, and fire crosses open
     * ground through grass and crops. Expect to lose things.
     */
    INFERNO(table(
            new Odds(70, 12),
            new Odds(70, 12),
            new Odds(80, 18),
            new Odds(80, 20),
            new Odds(90, 55),
            new Odds(80, 55),
            new Odds(95, 35),
            new Odds(90, 100),
            new Odds(95, 100),
            new Odds(80, 80))),

    /** Use the per-category values from the config file verbatim. */
    CUSTOM(table(
            new Odds(35, 8),
            new Odds(35, 8),
            new Odds(40, 12),
            new Odds(40, 15),
            new Odds(60, 45),
            new Odds(50, 45),
            new Odds(70, 30),
            new Odds(60, 100),
            new Odds(70, 100),
            new Odds(40, 60)));

    private final Map<FuelCategory, Odds> odds;

    FirePreset(Map<FuelCategory, Odds> odds) {
        this.odds = Collections.unmodifiableMap(odds);
    }

    /** Never null: every preset defines every category. */
    public Odds get(FuelCategory category) {
        return odds.get(category);
    }

    public Map<FuelCategory, Odds> table() {
        return odds;
    }

    /** True when this preset leaves the game exactly as Mojang shipped it. */
    public boolean isVanilla() {
        return this == VANILLA;
    }

    private static Map<FuelCategory, Odds> table(Odds... values) {
        FuelCategory[] categories = FuelCategory.values();
        if (values.length != categories.length) {
            throw new IllegalArgumentException(
                    "preset defines " + values.length + " entries but there are "
                            + categories.length + " fuel categories");
        }
        Map<FuelCategory, Odds> map = new EnumMap<>(FuelCategory.class);
        for (int i = 0; i < categories.length; i++) {
            map.put(categories[i], values[i]);
        }
        return map;
    }
}
