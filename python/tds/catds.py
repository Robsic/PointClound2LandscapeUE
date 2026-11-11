"""Confidence-Adaptive Tolerance Diamond-Square (CATDS).

Python version of the Java class tds.CATDS, with the same arithmetic (heights
and confidences in float32, sums in float64, the same visiting order), so the
output is the same pixel by pixel.
"""

import math

import numpy as np

from ._jit import jit
from .jrandom import new_state, next_gaussian

LADDER = np.array([1, 2, 3, 4, 6, 8, 12, 16, 24, 32, 48, 64, 96, 128, 192, 256, 384, 512], dtype=np.int64)
INV_SQRT2 = 1.0 / math.sqrt(2.0)
MAX_PASSES = 64


@jit
def chamfer(conf, w, h):
    """two-pass chamfer distance (1 orthogonal, 1.414 diagonal), float32, to the nearest pixel with conf > 0"""
    n = w * h
    one = np.float32(1.0)
    diag = np.float32(1.414)
    d = np.empty(n, dtype=np.float32)
    for i in range(n):
        d[i] = np.float32(0.0) if conf[i] > 0 else np.float32(1e9)
    for y in range(h):
        for x in range(w):
            i = y * w + x
            b = d[i]
            if x > 0:
                b = min(b, d[i - 1] + one)
            if y > 0:
                b = min(b, d[i - w] + one)
            if x > 0 and y > 0:
                b = min(b, d[i - w - 1] + diag)
            if x + 1 < w and y > 0:
                b = min(b, d[i - w + 1] + diag)
            d[i] = b
    for y in range(h - 1, -1, -1):
        for x in range(w - 1, -1, -1):
            i = y * w + x
            b = d[i]
            if x + 1 < w:
                b = min(b, d[i + 1] + one)
            if y + 1 < h:
                b = min(b, d[i + w] + one)
            if x + 1 < w and y + 1 < h:
                b = min(b, d[i + w + 1] + diag)
            if x > 0 and y + 1 < h:
                b = min(b, d[i + w - 1] + diag)
            d[i] = b
    return d


@jit
def _propagate(v, conf, pend, m, w, h, tolerance, rho, dx, dy, wt, rings, synchronous, max_sample):
    new_v = np.zeros(m, dtype=np.float32)
    new_c = np.zeros(m, dtype=np.float32)
    target = np.zeros(m, dtype=np.int64)
    passes = 0
    tol = tolerance
    while tol >= 1 and m > 0:
        for e in range(rings):
            if m == 0:
                break
            limit = (e + 1) * 8
            for pss in range(MAX_PASSES):
                if m == 0:
                    break
                k = 0
                passes += 1
                backward = (not synchronous) and (pss % 2 == 1)
                for tt in range(m):
                    idx = pend[m - 1 - tt] if backward else pend[tt]
                    x = idx % w
                    y = idx // w
                    sw = 0.0
                    swv = 0.0
                    sc = 0.0
                    support = 0.0
                    cnt = 0
                    for q in range(limit):
                        nx = x + dx[q]
                        ny = y + dy[q]
                        if nx < 0 or nx >= w or ny < 0 or ny >= h:
                            continue
                        j = ny * w + nx
                        cj = conf[j]
                        if cj <= 0:
                            continue
                        p = wt[q] * np.float64(cj)
                        sw += p
                        swv += p * np.float64(v[j])
                        sc += np.float64(cj)
                        cnt += 1
                        support += np.float64(cj)
                    if cnt > 0 and support >= tol:
                        val = swv / sw
                        if val < 1:
                            val = 1.0
                        if val > max_sample:
                            val = np.float64(max_sample)
                        target[k] = idx
                        new_v[k] = np.float32(val)
                        new_c[k] = np.float32(rho * (sc / cnt))
                        if not synchronous:
                            v[idx] = new_v[k]
                            conf[idx] = new_c[k]
                        k += 1
                if k == 0:
                    break
                if synchronous:
                    for t in range(k):
                        v[target[t]] = new_v[t]
                        conf[target[t]] = new_c[t]
                m2 = 0
                for t in range(m):
                    if conf[pend[t]] == 0:
                        pend[m2] = pend[t]
                        m2 += 1
                m = m2
        tol -= 1
    return passes


@jit
def _close_by_search(v, conf, w, h, rho):
    n = w * h
    queue = np.empty(n, dtype=np.int64)
    seen = np.zeros(n, dtype=np.bool_)
    head = 0
    tail = 0
    for i in range(n):
        if conf[i] > 0:
            queue[tail] = i
            tail += 1
            seen[i] = True
    while head < tail:
        i = queue[head]
        head += 1
        x = i % w
        y = i // w
        for direction in range(4):
            nx = x + (1 if direction == 0 else (-1 if direction == 1 else 0))
            ny = y + (1 if direction == 2 else (-1 if direction == 3 else 0))
            if nx < 0 or nx >= w or ny < 0 or ny >= h:
                continue
            j = ny * w + nx
            if seen[j] or conf[j] > 0:
                continue
            v[j] = v[i]
            conf[j] = np.float32(rho * np.float64(conf[i]))
            seen[j] = True
            queue[tail] = j
            tail += 1


@jit
def _refine(v, conf, w, h, c_min, max_sample):
    copy = v.copy()
    ox = np.array([1, -1, 0, 0, 1, 1, -1, -1])
    oy = np.array([0, 0, 1, -1, 1, -1, 1, -1])
    inv_sqrt2 = 1.0 / math.sqrt(2.0)
    for y in range(1, h - 1):
        for x in range(1, w - 1):
            i = y * w + x
            if conf[i] >= c_min:
                continue
            sw = 0.0
            swv = 0.0
            for k in range(8):
                j = (y + oy[k]) * w + x + ox[k]
                p = (1.0 if k < 4 else inv_sqrt2) * np.float64(conf[j])
                sw += p
                swv += p * np.float64(copy[j])
            if sw > 0:
                val = 0.5 * np.float64(copy[i]) + 0.5 * (swv / sw)
                if val < 0:
                    val = 0.0
                if val > max_sample:
                    val = np.float64(max_sample)
                v[i] = np.float32(val)


@jit
def _fit_variogram(v, conf, w, h, ladder):
    """returns (c, exponent, gamma at distance 1 or -1)"""
    sx = 0.0
    sy = 0.0
    sxx = 0.0
    sxy = 0.0
    nb = 0
    g1 = -1.0
    step = max(1, int(math.sqrt(np.float64(w) * h / 200000.0)))
    for e in range(ladder.shape[0]):
        d = ladder[e]
        if d > min(w, h) // 2:
            break
        total = 0.0
        pairs = 0
        for y in range(0, h, step):
            off = y * w
            for x in range(0, w, step):
                i = off + x
                if conf[i] <= 0:
                    continue
                if x + d < w:
                    j = i + d
                    if conf[j] > 0:
                        dz = np.float64(v[i] - v[j])
                        total += dz * dz
                        pairs += 1
                if y + d < h:
                    j = i + d * w
                    if conf[j] > 0:
                        dz = np.float64(v[i] - v[j])
                        total += dz * dz
                        pairs += 1
        if pairs >= 30:
            g = 0.5 * total / pairs
            if d == 1:
                g1 = g
            if g > 0:
                lx = math.log(d)
                ly = math.log(g)
                sx += lx
                sy += ly
                sxx += lx * lx
                sxy += lx * ly
                nb += 1
    if nb >= 2:
        det = nb * sxx - sx * sx
        ex = (nb * sxy - sx * sy) / det
        c = math.exp((sy - ex * sx) / nb)
        if ex < 0:
            ex = 0.0
        if ex > 2:
            ex = 2.0
        return c, ex, g1
    s = 0.0
    s2 = 0.0
    cnt = 0
    for i in range(v.shape[0]):
        if conf[i] > 0:
            s += np.float64(v[i])
            s2 += np.float64(v[i]) * np.float64(v[i])
            cnt += 1
    var = max(s2 / cnt - (s / cnt) * (s / cnt), 1e-9) if cnt > 1 else 1e-9
    return var, 0.0, g1


@jit
def _fractal_field(w, h, c, ex, ladder, cutoff, state, spare):
    n = w * h
    f = np.zeros(n, dtype=np.float32)
    for a in range(ladder.shape[0] - 1, -1, -1):
        d = ladder[a]
        if d > cutoff:
            continue
        half = max(1.0, d / 2.0)
        amp = math.sqrt(max(c * math.pow(d, ex) - c * math.pow(half, ex), 0.0))
        if amp <= 0:
            continue
        nx = w // d + 2
        ny = h // d + 2
        node = np.empty(nx * ny, dtype=np.float32)
        for i in range(nx * ny):
            node[i] = np.float32(next_gaussian(state, spare))
        for y in range(h):
            gy = y // d
            ty = (y - gy * d) / np.float64(d)
            off = y * w
            for x in range(w):
                gx = x // d
                tx = (x - gx * d) / np.float64(d)
                v00 = np.float64(node[gy * nx + gx])
                v10 = np.float64(node[gy * nx + gx + 1])
                v01 = np.float64(node[(gy + 1) * nx + gx])
                v11 = np.float64(node[(gy + 1) * nx + gx + 1])
                sxs = tx * tx * (3 - 2 * tx)
                sys_ = ty * ty * (3 - 2 * ty)
                v0 = v00 + (v10 - v00) * sxs
                v1 = v01 + (v11 - v01) * sxs
                f[off + x] += np.float32(amp * (v0 + (v1 - v0) * sys_))
    total = 0.0
    pairs = 0
    for y in range(h):
        off = y * w
        for x in range(w - 1):
            dz = np.float64(f[off + x] - f[off + x + 1])
            total += dz * dz
            pairs += 1
    g_f = 0.5 * total / pairs if pairs > 0 else 0.0
    if g_f > 0:
        k = math.sqrt(c * math.pow(1.0, ex) / g_f)
        for i in range(n):
            f[i] = np.float32(np.float64(f[i]) * k)
    return f


@jit
def _restore_roughness(v, conf, w, h, max_sample, dist0, g1, c, ex, scale, ladder, cutoff, state, spare):
    total = 0.0
    pairs = 0
    for y in range(h):
        off = y * w
        for x in range(w - 1):
            i = off + x
            if conf[i] >= 1 and conf[i + 1] >= 1:
                continue
            dz = np.float64(v[i] - v[i + 1])
            total += dz * dz
            pairs += 1
    if pairs == 0:
        return
    g_rec = 0.5 * total / pairs
    if g1 <= 0:
        return
    deficit = g1 - g_rec
    if deficit <= 0:
        return
    f = _fractal_field(w, h, c, ex, ladder, cutoff, state, spare)
    s2 = 0.0
    p2 = 0
    for y in range(h):
        off = y * w
        for x in range(w - 1):
            dz = np.float64(f[off + x] - f[off + x + 1])
            s2 += dz * dz
            p2 += 1
    g_f = 0.5 * s2 / p2 if p2 > 0 else 0.0
    if g_f <= 0:
        return
    k = scale * math.sqrt(deficit / g_f)
    eight = np.float32(8.0)
    for i in range(v.shape[0]):
        if conf[i] >= 1:
            continue
        lam = min(np.float32(1.0), dist0[i] / eight)
        val = np.float64(v[i]) + k * np.float64(lam) * np.float64(f[i])
        if val < 1:
            val = 1.0
        if val > max_sample:
            val = np.float64(max_sample)
        v[i] = np.float32(val)


def catds(heightmap, tolerance=2, rho=0.30, beta=2, d_max=16, c_min=0.60, refine=True,
          synchronous=False, random_scale=0.0, octave_cutoff=8, seed=123, max_value=65535):
    """Confidence-Adaptive Tolerance Diamond-Square.

    heightmap     2-D integer array, 0 = no sample
    tolerance     tau_0, initial tolerance: minimum sum of confidences of the support;
                  it is lowered by one each time the whole ring ladder is swept
    rho           confidence decay in (0, 1]: an estimated pixel receives
                  rho x mean confidence of its support
    beta          distance exponent of the weights, 1 or 2
    d_max         largest ring of the mask; farther pixels are closed by the search
    c_min         pixels whose final confidence is below it are refined
    refine        run the refinement
    synchronous   True = each pass reads the state at the start of the pass (Jacobi)
    random_scale  0 = deterministic; > 0 adds a fractal displacement that restores
                  the pixel-scale roughness measured on the samples
    octave_cutoff largest scale of the displacement, in pixels
    seed          seed of the displacement
    max_value     largest representable height (65535 for 16 bit, 255 for 8 bit)

    Returns (filled array, number of passes). No pixel is left at 0 when at least
    one pixel is measured.
    """
    a = np.asarray(heightmap)
    h, w = a.shape
    n = w * h
    flat = a.astype(np.int64).ravel()
    v = flat.astype(np.float32)
    conf = (flat != 0).astype(np.float32)
    empty = int((flat == 0).sum())
    if empty == 0 or empty == n:
        return a.astype(np.int64).copy(), 0

    dist0 = chamfer(conf, w, h)
    sel = (conf == 0) & (dist0 <= d_max)
    idx = np.nonzero(sel)[0]
    band = dist0[idx].astype(np.int64)                 # integer part (distances are >= 0)
    pend = idx[np.argsort(band, kind="stable")].astype(np.int64)

    c, ex, g1 = _fit_variogram(v, conf, w, h, LADDER)

    rings = int((LADDER <= d_max).sum())
    ox = np.array([1, -1, 0, 0, 1, 1, -1, -1], dtype=np.int64)
    oy = np.array([0, 0, 1, -1, 1, -1, 1, -1], dtype=np.int64)
    dx = np.zeros(rings * 8, dtype=np.int64)
    dy = np.zeros(rings * 8, dtype=np.int64)
    wt = np.zeros(rings * 8, dtype=np.float64)
    for r in range(rings):
        d = int(LADDER[r])
        w_diam = 1.0 / (np.float64(d) * d) if beta == 2 else 1.0 / d
        w_sq = w_diam * (0.5 if beta == 2 else INV_SQRT2)
        dx[r * 8:(r + 1) * 8] = ox * d
        dy[r * 8:(r + 1) * 8] = oy * d
        wt[r * 8:r * 8 + 4] = w_diam
        wt[r * 8 + 4:(r + 1) * 8] = w_sq

    passes = _propagate(v, conf, pend, pend.shape[0], w, h, int(tolerance), np.float64(rho),
                        dx, dy, wt, rings, bool(synchronous), int(max_value))
    _close_by_search(v, conf, w, h, np.float64(rho))
    if refine:
        _refine(v, conf, w, h, np.float64(c_min), int(max_value))
    if random_scale > 0:
        _restore_roughness(v, conf, w, h, int(max_value), dist0, g1, c, ex, np.float64(random_scale),
                           LADDER, int(octave_cutoff), new_state(seed), np.zeros(2))

    out = np.floor(v.astype(np.float64) + 0.5)
    out = np.clip(out, 0, max_value).astype(np.int64)
    return out.reshape(h, w), passes
