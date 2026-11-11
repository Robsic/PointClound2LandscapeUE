package tds;

import java.util.Random;

/**
 * Global Tolerance Diamond-Square.
 *
 * Same rule as {@link LocalTDS}, but for each empty pixel the radius d of the
 * mask grows from 1 up to floor(0.35 * max(W, H)) - 1 and the pixel is filled at
 * the first radius whose diamond (four orthogonal positions at distance d) or
 * square (four diagonal positions at distance d) holds at least
 * {@code tolerance + r} values. The value written is the diamond neighbor at
 * that radius closest to the mean of the neighbors inspected. Positions outside
 * the image are ignored, so border pixels are filled too.
 *
 * Complete version of interpolacoes.proposed.prop_GlobalDiamondSquareInterpolation
 * (folder first/), with the same sequence of random numbers and the same output.
 */
public final class GlobalTDS {

    public int tolerance = 2;
    public int randomCeil = 1;
    public long seed = 123L;
    /** fraction of max(W, H) that bounds the radius; the first version uses 0.35 */
    public float maxRadiusFraction = 0.35f;
    /** 0 = no limit */
    public int maxSweeps = 0;

    public int sweeps;

    public HeightMap interpolate(HeightMap in) {
        HeightMap outMap = in.copy();
        int[] v = outMap.data;
        final int w = in.width, h = in.height;
        final Random r = new Random(seed);
        final float ceil = randomCeil;
        final int maxDist = (int) (Math.max(w, h) * maxRadiusFraction);

        sweeps = 0;
        while (true) {
            sweeps++;
            boolean allFilled = true, changed = false;
            for (int i = 0; i < h; i++) {
                for (int j = 0; j < w; j++) {
                    final int p = i * w + j;
                    if (v[p] != 0) continue;
                    allFilled = false;

                    for (int d = 1; d < maxDist; d++) {
                        final boolean right = j + d < w, left = j - d >= 0, up = i - d >= 0, down = i + d < h;

                        // diamond: right, left, up, down
                        int v1 = 0, v2 = 0, v3 = 0, v4 = 0, count = 0, s;
                        double mean = 0;
                        if (right && (s = v[p + d]) != 0) { v1 = s; count++; mean += s; }
                        if (left && (s = v[p - d]) != 0) { v2 = s; count++; mean += s; }
                        if (up && (s = v[p - d * w]) != 0) { v3 = s; count++; mean += s; }
                        if (down && (s = v[p + d * w]) != 0) { v4 = s; count++; mean += s; }
                        mean /= count;
                        if (count >= tolerance + Math.round(r.nextFloat() * ceil)) {
                            final int value = LocalTDS.closest(v1, v2, v3, v4, mean);
                            if (value != 0) changed = true;
                            v[p] = value;
                            break;
                        }

                        // square: up-right, down-right, up-left, down-left
                        count = 0;
                        mean = 0;
                        if (right && up && (s = v[p - d * w + d]) != 0) { count++; mean += s; }
                        if (right && down && (s = v[p + d * w + d]) != 0) { count++; mean += s; }
                        if (left && up && (s = v[p - d * w - d]) != 0) { count++; mean += s; }
                        if (left && down && (s = v[p + d * w - d]) != 0) { count++; mean += s; }
                        mean /= count;
                        if (count >= tolerance + Math.round(r.nextFloat() * ceil)) {
                            final int value = LocalTDS.closest(v1, v2, v3, v4, mean);
                            if (value != 0) changed = true;
                            v[p] = value;
                            break;
                        }
                    }
                }
            }
            if (allFilled) break;
            if (sweeps > 1 && !changed) break;
            if (maxSweeps > 0 && sweeps >= maxSweeps) break;
        }
        return outMap;
    }
}
