package dev.boredhero.conflagration.optimization;

/** Pure version gates for optional APIs that appeared mid-Minecraft release line. */
final class ModVersionRules {

    private ModVersionRules() {
    }

    static boolean ftbChunksOwnsFireSpread(String version) {
        int[] parts = firstThreeNumbers(version);
        if (parts == null) {
            return true; // Unknown versions fail closed and require the adapter.
        }
        if (parts[0] != 2101) {
            return parts[0] > 2101;
        }
        if (parts[1] != 1) {
            return parts[1] > 1;
        }
        return parts[2] >= 15;
    }

    private static int[] firstThreeNumbers(String version) {
        if (version == null) {
            return null;
        }
        String[] components = version.split("[.-]", 4);
        if (components.length < 3) {
            return null;
        }
        try {
            return new int[] {
                    Integer.parseInt(components[0]),
                    Integer.parseInt(components[1]),
                    Integer.parseInt(components[2])
            };
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
