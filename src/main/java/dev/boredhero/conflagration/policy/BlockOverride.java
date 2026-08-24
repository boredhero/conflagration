package dev.boredhero.conflagration.policy;

import java.util.Locale;
import java.util.Optional;

/**
 * A single per-block override parsed from the config file.
 *
 * <p>Wire format is {@code namespace:path=ignite,burn}, e.g. {@code minecraft:oak_log=80,10}.
 * The namespace may be omitted and defaults to {@code minecraft}.
 *
 * <p>Parsing is deliberately total: a malformed entry yields {@link Optional#empty()} together
 * with a human-readable reason rather than throwing, because a typo in a config file should
 * produce a warning in the log and not prevent the server from starting.
 */
public record BlockOverride(String blockId, Odds odds) {

    public static final String DEFAULT_NAMESPACE = "minecraft";

    /** Result of attempting to parse one config line. */
    public record Result(Optional<BlockOverride> value, String error) {
        public boolean ok() {
            return value.isPresent();
        }

        static Result of(BlockOverride override) {
            return new Result(Optional.of(override), null);
        }

        static Result fail(String message) {
            return new Result(Optional.empty(), message);
        }
    }

    public static Result parse(String raw) {
        if (raw == null) {
            return Result.fail("entry is null");
        }
        String line = raw.trim();
        if (line.isEmpty()) {
            return Result.fail("entry is empty");
        }

        int eq = line.indexOf('=');
        if (eq < 0) {
            return Result.fail("missing '=' in \"" + line + "\" (expected id=ignite,burn)");
        }

        String id = normaliseId(line.substring(0, eq).trim());
        String values = line.substring(eq + 1).trim();

        if (!isValidId(id)) {
            return Result.fail("\"" + id + "\" is not a valid block id");
        }

        int comma = values.indexOf(',');
        if (comma < 0) {
            return Result.fail("missing ',' in \"" + values + "\" (expected ignite,burn)");
        }

        Integer ignite = parseInt(values.substring(0, comma).trim());
        Integer burn = parseInt(values.substring(comma + 1).trim());
        if (ignite == null || burn == null) {
            return Result.fail("\"" + values + "\" is not a pair of integers");
        }

        return Result.of(new BlockOverride(id, new Odds(ignite, burn)));
    }

    /** Adds the default namespace when the entry omits one. */
    public static String normaliseId(String id) {
        String lower = id.toLowerCase(Locale.ROOT);
        return lower.indexOf(':') < 0 ? DEFAULT_NAMESPACE + ":" + lower : lower;
    }

    /**
     * Mirrors Minecraft's ResourceLocation rules without depending on the class, so this stays
     * unit-testable: namespace allows [a-z0-9_.-], path additionally allows '/'.
     */
    public static boolean isValidId(String id) {
        int colon = id.indexOf(':');
        if (colon <= 0 || colon == id.length() - 1) {
            return false;
        }
        if (id.indexOf(':', colon + 1) >= 0) {
            return false;
        }
        return isValidPart(id.substring(0, colon), false) && isValidPart(id.substring(colon + 1), true);
    }

    private static boolean isValidPart(String part, boolean allowSlash) {
        if (part.isEmpty()) {
            return false;
        }
        for (int i = 0; i < part.length(); i++) {
            char c = part.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z')
                    || (c >= '0' && c <= '9')
                    || c == '_' || c == '.' || c == '-'
                    || (allowSlash && c == '/');
            if (!ok) {
                return false;
            }
        }
        return true;
    }

    private static Integer parseInt(String s) {
        try {
            return Integer.valueOf(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
