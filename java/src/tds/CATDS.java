package tds;

import java.util.Random;

/**
 * Confidence-Adaptive Tolerance Diamond-Square (CATDS).
 *
 * Elevation map Z (0 = empty) and confidence map C (1 measured, 0 empty,
 * rho * mean confidence of the support for an estimated pixel).
 *
 *  1. Delta = two-pass chamfer distance (1, 1.414) to the nearest measured pixel.
 *  2. P = empty pixels with Delta <= dMax, stably sorted by floor(Delta).
 *  3. For tau = tolerance .. 1, for every ring d_i of D = 1,2,3,4,6,8,12,16,24,...
 *     not above dMax, repeat (at most 64 passes, alternating the direction):
 *     for each p in P, N = neighbors with C > 0 at the eight diamond-square
 *     positions of every ring d <= d_i; if sum C(q) >= tau, write
 *     Z(p) = clip[1, Zmax]( sum w_q Z(q) / sum w_q ), w_q = C(q) * dist(p,q)^-beta,
 *     and C(p) = rho * mean C(q), in place. Pixels filled leave P.
 *  4. A four-connected breadth-first search from every filled pixel closes what
 *     is left: the pixel takes the value of the pixel that reaches it and
 *     rho times its confidence.
 *  5. Refinement: every interior pixel with C < cMin becomes the average of its
 *     value and the C/dist weighted mean of its eight neighbors (read from a copy).
 *  6. Optional fractal displacement (randomScale > 0), see {@link #restoreRoughness}.
 *
 * Heights and confidences are stored as float, sums are accumulated as double,
 * and the result is rounded half up and clipped to [0, Zmax].
 */
public final class CATDS {

    /** initial tolerance tau_0: minimum sum of confidences of the support */
    public int tolerance = 2;
    /** rho, confidence decay: an estimated pixel inherits rho x mean confidence of its support */
    public double confidenceDecay = 0.30;
    /** beta, distance exponent of the weights: 1 or 2 */
    public int distancePower = 2;
    /** d_max, largest ring of the mask; farther pixels are closed by the search */
    public int maxRadius = 16;
    /** run the refinement of the low-confidence pixels */
    public boolean refine = true;
    /** c_min, pixels whose confidence is below it are refined */
    public double refineConfidence = 0.60;
    /** true = every pass reads the state of the beginning of the pass (Jacobi) */
    public boolean synchronous = false;
    /** 0 = deterministic; 1 = fractal displacement calibrated on the measured roughness */
    public double randomScale = 0.0;
    /** largest scale of the fractal displacement, in pixels */
    public int octaveCutoff = 8;
    /** seed of the fractal displacement */
    public long seed = 123L;

    static final int[] LADDER = {1, 2, 3, 4, 6, 8, 12, 16, 24, 32, 48, 64, 96, 128, 192, 256, 384, 512};
    static final double INV_SQRT2 = 1.0 / Math.sqrt(2.0);
    static final int MAX_PASSES = 64;

    /** passes run in the last call */
    public int passes;
    /** exponent of the variogram fitted on the measured pixels, divided by two */
    public double hurst = 0.5;

    private double varC = 0, varExp = 1, gEmp1 = -1;

    public HeightMap interpolate(HeightMap in) {
        final int w = in.width, h = in.height, n = w * h;
        final int maxSample = in.maxValue;
        final HeightMap outMap = in.copy();

        final float[] v = new float[n];
        final float[] conf = new float[n];
        int empty = 0;
        for (int i = 0; i < n; i++) {
            int s = in.data[i];
            v[i] = s;
            if (s != 0) conf[i] = 1f; else empty++;
        }
        if (empty == n || empty == 0) return outMap;

        // pending list, ordered by the integer part of the distance to the data
        final float[] dist0 = chamfer(conf, w, h);
        int m = 0;
        int maxD = 1;
        for (int i = 0; i < n; i++) {
            if (conf[i] == 0f && dist0[i] <= maxRadius) {
                m++;
                int di = (int) dist0[i];
                if (di > maxD) maxD = di;
            }
        }
        int[] count = new int[maxD + 2];
        for (int i = 0; i < n; i++)
            if (conf[i] == 0f && dist0[i] <= maxRadius) count[Math.min(maxD, (int) dist0[i]) + 1]++;
        for (int i = 1; i < count.length; i++) count[i] += count[i - 1];
        int[] pend = new int[m];
        for (int i = 0; i < n; i++)
            if (conf[i] == 0f && dist0[i] <= maxRadius) pend[count[Math.min(maxD, (int) dist0[i])]++] = i;

        fitVariogram(v, conf, w, h);

        // offsets and weights of the eight positions of every ring
        int rings = 0;
        while (rings < LADDER.length && LADDER[rings] <= maxRadius) rings++;
        final int[] dx = new int[rings * 8], dy = new int[rings * 8];
        final double[] wt = new double[rings * 8];
        final int[] ox = {1, -1, 0, 0, 1, 1, -1, -1}, oy = {0, 0, 1, -1, 1, -1, 1, -1};
        for (int a = 0; a < rings; a++) {
            final int d = LADDER[a];
            final double wDiam = (distancePower == 2) ? 1.0 / ((double) d * d) : 1.0 / d;
            final double wSq = wDiam * ((distancePower == 2) ? 0.5 : INV_SQRT2);
            for (int k = 0; k < 8; k++) {
                dx[a * 8 + k] = ox[k] * d;
                dy[a * 8 + k] = oy[k] * d;
                wt[a * 8 + k] = k < 4 ? wDiam : wSq;
            }
        }

        final float[] newV = new float[m], newC = new float[m];
        final int[] target = new int[m];
        passes = 0;
        for (int tol = tolerance; tol >= 1 && m > 0; tol--) {
            for (int e = 0; e < rings && m > 0; e++) {
                final int limit = (e + 1) * 8;
                for (int pass = 0; pass < MAX_PASSES && m > 0; pass++) {
                    int k = 0;
                    passes++;
                    final boolean backward = !synchronous && (pass % 2 == 1);
                    for (int tt = 0; tt < m; tt++) {
                        final int idx = pend[backward ? (m - 1 - tt) : tt];
                        final int x = idx % w, y = idx / w;
                        double sw = 0, swv = 0, sc = 0, support = 0;
                        int cnt = 0;
                        for (int q = 0; q < limit; q++) {
                            final int nx = x + dx[q], ny = y + dy[q];
                            if (nx < 0 || nx >= w || ny < 0 || ny >= h) continue;
                            final int j = ny * w + nx;
                            final float cj = conf[j];
                            if (cj <= 0f) continue;
                            final double p = wt[q] * cj;
                            sw += p;
                            swv += p * v[j];
                            sc += cj;
                            cnt++;
                            support += cj;
                        }
                        if (cnt > 0 && support >= tol) {
                            double val = swv / sw;
                            if (val < 1) val = 1;
                            if (val > maxSample) val = maxSample;
                            target[k] = idx;
                            newV[k] = (float) val;
                            newC[k] = (float) (confidenceDecay * (sc / cnt));
                            if (!synchronous) {
                                v[idx] = newV[k];
                                conf[idx] = newC[k];
                            }
                            k++;
                        }
                    }
                    if (k == 0) break;
                    if (synchronous)
                        for (int t = 0; t < k; t++) {
                            v[target[t]] = newV[t];
                            conf[target[t]] = newC[t];
                        }
                    int m2 = 0;
                    for (int t = 0; t < m; t++) if (conf[pend[t]] == 0f) pend[m2++] = pend[t];
                    m = m2;
                }
            }
        }
        closeBySearch(v, conf, w, h);
        if (refine) refine(v, conf, w, h, maxSample);
        if (randomScale > 0) restoreRoughness(v, conf, w, h, maxSample, dist0);

        for (int i = 0; i < n; i++) {
            int s = Math.round(v[i]);
            if (s < 0) s = 0;
            if (s > maxSample) s = maxSample;
            outMap.data[i] = s;
        }
        return outMap;
    }

    /** two-pass chamfer distance (1 orthogonal, 1.414 diagonal) to the nearest pixel with conf > 0 */
    static float[] chamfer(float[] conf, int w, int h) {
        final int n = w * h;
        final float[] d = new float[n];
        for (int i = 0; i < n; i++) d[i] = (conf[i] > 0f) ? 0f : 1e9f;
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int i = y * w + x;
                float b = d[i];
                if (x > 0) b = Math.min(b, d[i - 1] + 1);
                if (y > 0) b = Math.min(b, d[i - w] + 1);
                if (x > 0 && y > 0) b = Math.min(b, d[i - w - 1] + 1.414f);
                if (x + 1 < w && y > 0) b = Math.min(b, d[i - w + 1] + 1.414f);
                d[i] = b;
            }
        for (int y = h - 1; y >= 0; y--)
            for (int x = w - 1; x >= 0; x--) {
                int i = y * w + x;
                float b = d[i];
                if (x + 1 < w) b = Math.min(b, d[i + 1] + 1);
                if (y + 1 < h) b = Math.min(b, d[i + w] + 1);
                if (x + 1 < w && y + 1 < h) b = Math.min(b, d[i + w + 1] + 1.414f);
                if (x > 0 && y + 1 < h) b = Math.min(b, d[i + w - 1] + 1.414f);
                d[i] = b;
            }
        return d;
    }

    /** first-in first-out search from every filled pixel; neighbors visited right, left, down, up */
    private void closeBySearch(float[] v, float[] conf, int w, int h) {
        final int n = w * h;
        final int[] queue = new int[n];
        final boolean[] seen = new boolean[n];
        int head = 0, tail = 0;
        for (int i = 0; i < n; i++) if (conf[i] > 0f) { queue[tail++] = i; seen[i] = true; }
        while (head < tail) {
            final int i = queue[head++];
            final int x = i % w, y = i / w;
            for (int dir = 0; dir < 4; dir++) {
                final int nx = x + (dir == 0 ? 1 : dir == 1 ? -1 : 0);
                final int ny = y + (dir == 2 ? 1 : dir == 3 ? -1 : 0);
                if (nx < 0 || nx >= w || ny < 0 || ny >= h) continue;
                final int j = ny * w + nx;
                if (seen[j] || conf[j] > 0f) continue;
                v[j] = v[i];
                conf[j] = (float) (confidenceDecay * conf[i]);
                seen[j] = true;
                queue[tail++] = j;
            }
        }
    }

    private void refine(float[] v, float[] conf, int w, int h, int maxSample) {
        final float[] copy = v.clone();
        final int[] ox = {1, -1, 0, 0, 1, 1, -1, -1}, oy = {0, 0, 1, -1, 1, -1, 1, -1};
        for (int y = 1; y < h - 1; y++)
            for (int x = 1; x < w - 1; x++) {
                final int i = y * w + x;
                if (conf[i] >= refineConfidence) continue;
                double sw = 0, swv = 0;
                for (int k = 0; k < 8; k++) {
                    final int j = (y + oy[k]) * w + x + ox[k];
                    final double p = (k < 4 ? 1.0 : INV_SQRT2) * conf[j];
                    sw += p;
                    swv += p * copy[j];
                }
                if (sw > 0) {
                    double val = 0.5 * copy[i] + 0.5 * (swv / sw);
                    if (val < 0) val = 0;
                    if (val > maxSample) val = maxSample;
                    v[i] = (float) val;
                }
            }
    }

    // ------------------------------------------------------------------ fractal displacement

    private double gamma(double d) { return varC * Math.pow(d, varExp); }

    /** fits gamma(d) = c d^(2H) in log-log on pairs of measured pixels at the ladder distances */
    private void fitVariogram(float[] v, float[] conf, int w, int h) {
        double sx = 0, sy = 0, sxx = 0, sxy = 0;
        int nb = 0;
        varC = 0; varExp = 1; gEmp1 = -1;
        for (int e = 0; e < LADDER.length; e++) {
            final int d = LADDER[e];
            if (d > Math.min(w, h) / 2) break;
            double sum = 0;
            long pairs = 0;
            final int step = Math.max(1, (int) Math.sqrt((double) w * h / 200000.0));
            for (int y = 0; y < h; y += step) {
                final int off = y * w;
                for (int x = 0; x < w; x += step) {
                    final int i = off + x;
                    if (conf[i] <= 0f) continue;
                    if (x + d < w) { int j = i + d; if (conf[j] > 0f) { double dz = v[i] - v[j]; sum += dz * dz; pairs++; } }
                    if (y + d < h) { int j = i + d * w; if (conf[j] > 0f) { double dz = v[i] - v[j]; sum += dz * dz; pairs++; } }
                }
            }
            if (pairs >= 30) {
                final double g = 0.5 * sum / pairs;
                if (d == 1) gEmp1 = g;
                if (g > 0) {
                    final double lx = Math.log(d), ly = Math.log(g);
                    sx += lx; sy += ly; sxx += lx * lx; sxy += lx * ly; nb++;
                }
            }
        }
        if (nb >= 2) {
            final double det = nb * sxx - sx * sx;
            varExp = (nb * sxy - sx * sy) / det;
            varC = Math.exp((sy - varExp * sx) / nb);
            if (varExp < 0) varExp = 0;
            if (varExp > 2) varExp = 2;
        } else {
            double s = 0, s2 = 0;
            int c = 0;
            for (int i = 0; i < v.length; i++) if (conf[i] > 0f) { s += v[i]; s2 += (double) v[i] * v[i]; c++; }
            varC = (c > 1) ? Math.max(s2 / c - (s / c) * (s / c), 1e-9) : 1e-9;
            varExp = 0;
        }
        hurst = varExp / 2.0;
    }

    /** sum of value-noise octaves (smoothstep interpolation), scaled to gamma(1) at distance 1 */
    private float[] fractalField(int w, int h) {
        final int n = w * h;
        final float[] f = new float[n];
        final Random rnd = new Random(seed);
        for (int a = LADDER.length - 1; a >= 0; a--) {
            final int d = LADDER[a];
            if (d > octaveCutoff) continue;
            final double amp = Math.sqrt(Math.max(gamma(d) - gamma(Math.max(1, d / 2.0)), 0.0));
            if (amp <= 0) continue;
            final int nx = w / d + 2, ny = h / d + 2;
            final float[] node = new float[nx * ny];
            for (int i = 0; i < node.length; i++) node[i] = (float) rnd.nextGaussian();
            for (int y = 0; y < h; y++) {
                final int gy = y / d;
                final double ty = (y - gy * d) / (double) d;
                final int off = y * w;
                for (int x = 0; x < w; x++) {
                    final int gx = x / d;
                    final double tx = (x - gx * d) / (double) d;
                    final double v00 = node[gy * nx + gx], v10 = node[gy * nx + gx + 1];
                    final double v01 = node[(gy + 1) * nx + gx], v11 = node[(gy + 1) * nx + gx + 1];
                    final double sxs = tx * tx * (3 - 2 * tx), sys = ty * ty * (3 - 2 * ty);
                    final double v0 = v00 + (v10 - v00) * sxs, v1 = v01 + (v11 - v01) * sxs;
                    f[off + x] += (float) (amp * (v0 + (v1 - v0) * sys));
                }
            }
        }
        double sum = 0;
        long pairs = 0;
        for (int y = 0; y < h; y++) {
            final int off = y * w;
            for (int x = 0; x + 1 < w; x++) { double dz = f[off + x] - f[off + x + 1]; sum += dz * dz; pairs++; }
        }
        final double gF = (pairs > 0) ? 0.5 * sum / pairs : 0;
        if (gF > 0) {
            final double k = Math.sqrt(gamma(1) / gF);
            for (int i = 0; i < n; i++) f[i] *= k;
        }
        return f;
    }

    /**
     * Adds back the pixel-scale roughness the reconstruction lost: the deficit
     * between the semivariance at distance 1 of the measured pixels and that of
     * the reconstructed region, as a fractal field weighted by min(1, Delta/8).
     * Skipped when no two measured pixels are adjacent.
     */
    private void restoreRoughness(float[] v, float[] conf, int w, int h, int maxSample, float[] dist0) {
        double sum = 0;
        long pairs = 0;
        for (int y = 0; y < h; y++) {
            final int off = y * w;
            for (int x = 0; x + 1 < w; x++) {
                final int i = off + x;
                if (conf[i] >= 1f && conf[i + 1] >= 1f) continue;
                double dz = v[i] - v[i + 1];
                sum += dz * dz;
                pairs++;
            }
        }
        if (pairs == 0) return;
        final double gRec = 0.5 * sum / pairs;
        if (gEmp1 <= 0) return;
        final double deficit = gEmp1 - gRec;
        if (deficit <= 0) return;

        final float[] f = fractalField(w, h);
        double s2 = 0;
        long p2 = 0;
        for (int y = 0; y < h; y++) {
            final int off = y * w;
            for (int x = 0; x + 1 < w; x++) { double dz = f[off + x] - f[off + x + 1]; s2 += dz * dz; p2++; }
        }
        final double gF = (p2 > 0) ? 0.5 * s2 / p2 : 0;
        if (gF <= 0) return;
        final double k = randomScale * Math.sqrt(deficit / gF);
        for (int i = 0; i < v.length; i++) {
            if (conf[i] >= 1f) continue;
            final float lam = Math.min(1f, dist0[i] / 8f);
            double val = v[i] + k * lam * f[i];
            if (val < 1) val = 1;
            if (val > maxSample) val = maxSample;
            v[i] = (float) val;
        }
    }
}
