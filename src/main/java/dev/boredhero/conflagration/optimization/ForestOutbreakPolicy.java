package dev.boredhero.conflagration.optimization;

/** Pure boundary and fuel-composition policy for the FRONTIER forest safety rail. */
final class ForestOutbreakPolicy {

    static final int MIN_LEAVES = 8;
    static final int MIN_NATURAL_FUEL = 16;
    private static final int ORIENTATION_COUNT = 16;
    private static final double[] COS = new double[ORIENTATION_COUNT];
    private static final double[] SIN = new double[ORIENTATION_COUNT];

    static {
        for (int index = 0; index < ORIENTATION_COUNT; index++) {
            double angle = Math.PI * index / ORIENTATION_COUNT;
            COS[index] = StrictMath.cos(angle);
            SIN[index] = StrictMath.sin(angle);
        }
    }

    private ForestOutbreakPolicy() {
    }

    static boolean outsideBoundary(long worldSeed,
                                   long originKey,
                                   int originX,
                                   int originZ,
                                   int targetX,
                                   int targetZ,
                                   int minimumRadiusBlocks,
                                   int maximumRadiusBlocks) {
        long deltaX = (long) targetX - originX;
        long deltaZ = (long) targetZ - originZ;
        long shapeSeed = shapeSeedFor(worldSeed, originX, originZ);
        int nominalRadiusBlocks = radiusFor(
                shapeSeed, minimumRadiusBlocks, maximumRadiusBlocks);
        int orientation = (int) shapeSeed & (ORIENTATION_COUNT - 1);
        double aspect = 1.0 + ((shapeSeed >>> 4) & 15L) / 25.0; // 1.0 .. 1.6
        double roughness = 0.08 + ((shapeSeed >>> 8) & 31L) / 31.0 * 0.32; // 8% .. 40%
        double rotatedX = deltaX * COS[orientation] + deltaZ * SIN[orientation];
        double rotatedZ = -deltaX * SIN[orientation] + deltaZ * COS[orientation];
        // Reciprocal scaling preserves the ellipse's nominal pi*r^2 area.
        double ellipticalX = rotatedX / aspect;
        double ellipticalZ = rotatedZ * aspect;
        double distanceSquared = ellipticalX * ellipticalX + ellipticalZ * ellipticalZ;
        double radiusSquared = (double) nominalRadiusBlocks * nominalRadiusBlocks;
        double minimumWarp = 1.0 - roughness;
        double maximumWarp = 1.0 + roughness;
        if (distanceSquared <= radiusSquared * minimumWarp * minimumWarp) {
            return false;
        }
        if (distanceSquared > radiusSquared * maximumWarp * maximumWarp) {
            return true;
        }

        double warp = 1.0 + roughness * (
                0.68 * valueNoise(targetX, targetZ, 32, shapeSeed)
                        + 0.32 * valueNoise(
                                targetX, targetZ, 11, Long.rotateLeft(shapeSeed, 29)));
        return distanceSquared > radiusSquared * warp * warp;
    }

    static boolean isForestDominant(int leaves, int logs, int structuralFuel) {
        int naturalFuel = leaves + logs;
        return leaves >= MIN_LEAVES
                && naturalFuel >= MIN_NATURAL_FUEL
                && structuralFuel * 4 <= naturalFuel;
    }

    /**
     * Detects overlap without rasterizing either shape. Close origins are caught directly; for
     * edge-only intersections, a few deterministic witnesses along the line between the centers
     * cover the convex ellipse and the broad lobes produced by the smooth boundary warp.
     */
    static boolean boundariesOverlap(long worldSeed,
                                     long firstOrigin,
                                     int firstX,
                                     int firstZ,
                                     long secondOrigin,
                                     int secondX,
                                     int secondZ,
                                     int minimumRadiusBlocks,
                                     int maximumRadiusBlocks) {
        if (firstOrigin == secondOrigin) {
            return true;
        }
        if (!outsideBoundary(worldSeed, firstOrigin, firstX, firstZ, secondX, secondZ,
                minimumRadiusBlocks, maximumRadiusBlocks)
                || !outsideBoundary(worldSeed, secondOrigin, secondX, secondZ, firstX, firstZ,
                        minimumRadiusBlocks, maximumRadiusBlocks)) {
            return true;
        }

        int firstRadius = nominalRadiusFor(
                worldSeed, firstX, firstZ, minimumRadiusBlocks, maximumRadiusBlocks);
        int secondRadius = nominalRadiusFor(
                worldSeed, secondX, secondZ, minimumRadiusBlocks, maximumRadiusBlocks);
        long deltaX = (long) secondX - firstX;
        long deltaZ = (long) secondZ - firstZ;
        double maximumCombinedExtent = (firstRadius + secondRadius) * 2.24;
        if ((double) deltaX * deltaX + (double) deltaZ * deltaZ
                > maximumCombinedExtent * maximumCombinedExtent) {
            return false;
        }

        for (int step = 1; step < 8; step++) {
            double fraction = step / 8.0;
            int witnessX = (int) StrictMath.round(firstX + deltaX * fraction);
            int witnessZ = (int) StrictMath.round(firstZ + deltaZ * fraction);
            if (!outsideBoundary(worldSeed, firstOrigin, firstX, firstZ, witnessX, witnessZ,
                    minimumRadiusBlocks, maximumRadiusBlocks)
                    && !outsideBoundary(worldSeed, secondOrigin, secondX, secondZ,
                            witnessX, witnessZ, minimumRadiusBlocks, maximumRadiusBlocks)) {
                return true;
            }
        }
        return false;
    }

    static int radiusFor(long shapeSeed, int minimumRadiusBlocks, int maximumRadiusBlocks) {
        int minimum = Math.min(minimumRadiusBlocks, maximumRadiusBlocks);
        int maximum = Math.max(minimumRadiusBlocks, maximumRadiusBlocks);
        int range = maximum - minimum + 1;
        return minimum + (int) Long.remainderUnsigned(shapeSeed, range);
    }

    private static int nominalRadiusFor(long worldSeed,
                                        int originX,
                                        int originZ,
                                        int minimumRadiusBlocks,
                                        int maximumRadiusBlocks) {
        return radiusFor(
                shapeSeedFor(worldSeed, originX, originZ),
                minimumRadiusBlocks,
                maximumRadiusBlocks);
    }

    private static long shapeSeedFor(long worldSeed, int originX, int originZ) {
        return mix64(worldSeed
                ^ (long) originX * 0x9E3779B97F4A7C15L
                ^ (long) originZ * 0xC2B2AE3D27D4EB4FL);
    }

    private static double valueNoise(int x, int z, int scale, long seed) {
        int cellX = Math.floorDiv(x, scale);
        int cellZ = Math.floorDiv(z, scale);
        double fractionX = Math.floorMod(x, scale) / (double) scale;
        double fractionZ = Math.floorMod(z, scale) / (double) scale;
        double smoothX = fractionX * fractionX * (3.0 - 2.0 * fractionX);
        double smoothZ = fractionZ * fractionZ * (3.0 - 2.0 * fractionZ);
        double northWest = lattice(cellX, cellZ, seed);
        double northEast = lattice(cellX + 1, cellZ, seed);
        double southWest = lattice(cellX, cellZ + 1, seed);
        double southEast = lattice(cellX + 1, cellZ + 1, seed);
        double north = northWest + (northEast - northWest) * smoothX;
        double south = southWest + (southEast - southWest) * smoothX;
        return north + (south - north) * smoothZ;
    }

    private static double lattice(int x, int z, long seed) {
        long hash = mix64(seed ^ (long) x * 0x9E3779B97F4A7C15L
                ^ (long) z * 0xC2B2AE3D27D4EB4FL);
        return ((hash >>> 11) * 0x1.0p-53) * 2.0 - 1.0;
    }

    private static long mix64(long value) {
        value ^= value >>> 30;
        value *= 0xbf58476d1ce4e5b9L;
        value ^= value >>> 27;
        value *= 0x94d049bb133111ebL;
        return value ^ value >>> 31;
    }
}
