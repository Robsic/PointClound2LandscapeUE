package tds;

import java.util.Random;

/**
 * Local Tolerance Diamond-Square.
 *
 * Every empty pixel (value 0) that is not on the image border is examined in
 * raster order. If at least {@code tolerance + r} of its four orthogonal
 * neighbors at distance 1 hold a value (diamond rule), or else at least
 * {@code tolerance + r} of its four diagonal neighbors (square rule), the pixel
 * receives the orthogonal neighbor whose value is closest to the mean of the
 * neighbors inspected. r is round(u * randomCeil), with u drawn from
 * java.util.Random(seed) at every test. Values are written in place, so a
 * value written is seen by the pixels visited after it. Sweeps repeat until one
 * of them changes nothing.
 *
 * This is the complete version of the first class
 * interpolacoes.proposed.prop_LocalToleranceDiamondSquareInterpolation
 * (folder first/). It consumes the random numbers in the same order and
 * produces the same output, pixel by pixel.
 */
public final class LocalTDS {

    /** minimum number of filled neighbors in the mask */
    public int tolerance = 2;
    /** the threshold receives round(u * randomCeil); 0 disables the random increment */
    public int randomCeil = 1;
    /** seed of java.util.Random */
    public long seed = 123L;
    /** 0 = no limit; otherwise the number of sweeps stops there */
    public int maxSweeps = 0;

    /** number of sweeps of the last call */
    public int sweeps;

    public HeightMap interpolate(HeightMap in) {
        HeightMap outMap = in.copy();
        int[] v = outMap.data;
        final int w = in.width, h = in.height;
        final Random r = new Random(seed);
        final float ceil = randomCeil;

        sweeps = 0;
        while (true) {
            sweeps++;
            boolean allFilled = true, changed = false;
            for (int i = 1; i < h - 1; i++) {
                final int row = i * w;
                for (int j = 1; j < w - 1; j++) {
                    final int p = row + j;
                    if (v[p] != 0) continue;
                    allFilled = false;

                    // diamond: right, left, up, down
                    final int v1 = v[p + 1], v2 = v[p - 1], v3 = v[p - w], v4 = v[p + w];
                    int count = 0;
                    double mean = 0;
                    if (v1 != 0) { count++; mean += v1; }
                    if (v2 != 0) { count++; mean += v2; }
                    if (v3 != 0) { count++; mean += v3; }
                    if (v4 != 0) { count++; mean += v4; }
                    mean /= count;
                    if (count >= tolerance + Math.round(r.nextFloat() * ceil)) {
                        final int value = closest(v1, v2, v3, v4, mean);
                        if (value != 0) changed = true;
                        v[p] = value;
                        continue;
                    }

                    // square: up-right, down-right, up-left, down-left
                    count = 0;
                    mean = 0;
                    int s;
                    if ((s = v[p - w + 1]) != 0) { count++; mean += s; }
                    if ((s = v[p + w + 1]) != 0) { count++; mean += s; }
                    if ((s = v[p - w - 1]) != 0) { count++; mean += s; }
                    if ((s = v[p + w - 1]) != 0) { count++; mean += s; }
                    mean /= count;
                    if (count >= tolerance + Math.round(r.nextFloat() * ceil)) {
                        // as in the first version, the value is taken from the diamond neighbors
                        final int value = closest(v1, v2, v3, v4, mean);
                        if (value != 0) changed = true;
                        v[p] = value;
                    }
                }
            }
            // the first version compares the raster with the one of the previous
            // sweep, and the first comparison always reports a change
            if (allFilled) break;
            if (sweeps > 1 && !changed) break;
            if (maxSweeps > 0 && sweeps >= maxSweeps) break;
        }
        return outMap;
    }

    /** the first of v1..v4 whose distance to avg is the smallest (ties keep the first) */
    static int closest(int v1, int v2, int v3, int v4, double avg) {
        final double d1 = Math.abs(v1 - avg), d2 = Math.abs(v2 - avg), d3 = Math.abs(v3 - avg), d4 = Math.abs(v4 - avg);
        final double min = Math.min(d1, Math.min(d2, Math.min(d3, d4)));
        if (min == d1) return v1;
        if (min == d2) return v2;
        if (min == d3) return v3;
        return v4;
    }
}
