package dev.boredhero.conflagration.policy;

/**
 * A pair of vanilla fire tuning values for a single block.
 *
 * <p>Minecraft's {@code FireBlock} keeps two independent numbers per flammable block, and
 * conflating them is the most common mistake when tuning fire:
 *
 * <ul>
 *   <li>{@code ignite} ("encouragement" / fire-spread-speed) — how likely this block is to
 *       <em>catch</em> when fire burns nearby. Raising this makes fire <em>travel</em>.</li>
 *   <li>{@code burn} ("flammability" / burn-odds) — how quickly the block is <em>consumed</em>
 *       once alight. Raising this makes fuel disappear faster, which paradoxically makes fires
 *       shorter-lived, because the fire block loses the fuel it was standing on.</li>
 * </ul>
 *
 * <p>For long, spreading fires that level a building you want <em>high ignite, moderate burn</em>.
 *
 * <p>Values are clamped to [{@value #MIN}, {@value #MAX}]. Vanilla never exceeds 100 and the
 * engine treats the numbers as relative weights, so larger values buy nothing but confusion.
 */
public record Odds(int ignite, int burn) {

    public static final int MIN = 0;
    public static final int MAX = 100;

    /** Fully inert: never catches, never burns. */
    public static final Odds INERT = new Odds(0, 0);

    public Odds {
        ignite = clamp(ignite);
        burn = clamp(burn);
    }

    public static int clamp(int value) {
        return Math.max(MIN, Math.min(MAX, value));
    }

    /** True when this entry would leave the block non-flammable. */
    public boolean isInert() {
        return ignite == 0 && burn == 0;
    }

    @Override
    public String toString() {
        return ignite + "," + burn;
    }
}
