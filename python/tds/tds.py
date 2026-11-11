"""Local and global Tolerance Diamond-Square.

Python version of the Java classes tds.LocalTDS and tds.GlobalTDS (and of the
first version in first/java). The random increment of the threshold is
drawn with the generator of java.util.Random, in the same order, so the output
is the same pixel by pixel.
"""

import math

import numpy as np

from ._jit import jit
from .jrandom import new_state, next_float


@jit
def _closest(v1, v2, v3, v4, avg):
    d1 = abs(v1 - avg)
    d2 = abs(v2 - avg)
    d3 = abs(v3 - avg)
    d4 = abs(v4 - avg)
    m = min(d1, min(d2, min(d3, d4)))
    if m == d1:
        return v1
    if m == d2:
        return v2
    if m == d3:
        return v3
    return v4


@jit
def _threshold(tolerance, state, ceil32):
    u = next_float(state) * ceil32
    return tolerance + int(math.floor(np.float64(u) + 0.5))


@jit
def _local(v, w, h, tolerance, ceil32, state, max_sweeps):
    sweeps = 0
    while True:
        sweeps += 1
        all_filled = True
        changed = False
        for i in range(1, h - 1):
            for j in range(1, w - 1):
                p = i * w + j
                if v[p] != 0:
                    continue
                all_filled = False
                v1 = v[p + 1]
                v2 = v[p - 1]
                v3 = v[p - w]
                v4 = v[p + w]
                count = 0
                mean = 0.0
                if v1 != 0:
                    count += 1
                    mean += v1
                if v2 != 0:
                    count += 1
                    mean += v2
                if v3 != 0:
                    count += 1
                    mean += v3
                if v4 != 0:
                    count += 1
                    mean += v4
                mean = mean / count if count > 0 else math.nan
                if count >= _threshold(tolerance, state, ceil32):
                    value = _closest(v1, v2, v3, v4, mean)
                    if value != 0:
                        changed = True
                    v[p] = value
                    continue
                count = 0
                mean = 0.0
                s = v[p - w + 1]
                if s != 0:
                    count += 1
                    mean += s
                s = v[p + w + 1]
                if s != 0:
                    count += 1
                    mean += s
                s = v[p - w - 1]
                if s != 0:
                    count += 1
                    mean += s
                s = v[p + w - 1]
                if s != 0:
                    count += 1
                    mean += s
                mean = mean / count if count > 0 else math.nan
                if count >= _threshold(tolerance, state, ceil32):
                    value = _closest(v1, v2, v3, v4, mean)
                    if value != 0:
                        changed = True
                    v[p] = value
        if all_filled:
            break
        if sweeps > 1 and not changed:
            break
        if max_sweeps > 0 and sweeps >= max_sweeps:
            break
    return sweeps


@jit
def _global(v, w, h, tolerance, ceil32, state, max_dist, max_sweeps):
    sweeps = 0
    while True:
        sweeps += 1
        all_filled = True
        changed = False
        for i in range(h):
            for j in range(w):
                p = i * w + j
                if v[p] != 0:
                    continue
                all_filled = False
                for d in range(1, max_dist):
                    right = j + d < w
                    left = j - d >= 0
                    up = i - d >= 0
                    down = i + d < h
                    v1 = 0
                    v2 = 0
                    v3 = 0
                    v4 = 0
                    count = 0
                    mean = 0.0
                    if right and v[p + d] != 0:
                        v1 = v[p + d]
                        count += 1
                        mean += v1
                    if left and v[p - d] != 0:
                        v2 = v[p - d]
                        count += 1
                        mean += v2
                    if up and v[p - d * w] != 0:
                        v3 = v[p - d * w]
                        count += 1
                        mean += v3
                    if down and v[p + d * w] != 0:
                        v4 = v[p + d * w]
                        count += 1
                        mean += v4
                    mean = mean / count if count > 0 else math.nan
                    if count >= _threshold(tolerance, state, ceil32):
                        value = _closest(v1, v2, v3, v4, mean)
                        if value != 0:
                            changed = True
                        v[p] = value
                        break
                    count = 0
                    mean = 0.0
                    if right and up and v[p - d * w + d] != 0:
                        count += 1
                        mean += v[p - d * w + d]
                    if right and down and v[p + d * w + d] != 0:
                        count += 1
                        mean += v[p + d * w + d]
                    if left and up and v[p - d * w - d] != 0:
                        count += 1
                        mean += v[p - d * w - d]
                    if left and down and v[p + d * w - d] != 0:
                        count += 1
                        mean += v[p + d * w - d]
                    mean = mean / count if count > 0 else math.nan
                    if count >= _threshold(tolerance, state, ceil32):
                        value = _closest(v1, v2, v3, v4, mean)
                        if value != 0:
                            changed = True
                        v[p] = value
                        break
        if all_filled:
            break
        if sweeps > 1 and not changed:
            break
        if max_sweeps > 0 and sweeps >= max_sweeps:
            break
    return sweeps


def local_tds(heightmap, tolerance=2, random_ceil=1, seed=123, max_sweeps=0):
    """Local Tolerance Diamond-Square.

    heightmap   2-D integer array, 0 = no sample
    tolerance   minimum number of filled neighbors in the mask
    random_ceil the threshold receives round(u * random_ceil), u uniform in [0, 1);
                0 disables the random increment
    seed        seed of the java.util.Random generator
    max_sweeps  0 = sweep until nothing changes; otherwise stop after that many

    Returns (filled array, number of sweeps). Border pixels are never filled and
    pixels that never gather enough support stay at 0.
    """
    a = np.asarray(heightmap)
    h, w = a.shape
    v = a.astype(np.int64).ravel().copy()
    sweeps = _local(v, w, h, int(tolerance), np.float32(random_ceil), new_state(seed), int(max_sweeps))
    return v.reshape(h, w), sweeps


def global_tds(heightmap, tolerance=2, random_ceil=1, seed=123, radius_fraction=0.35, max_sweeps=0):
    """Global Tolerance Diamond-Square.

    Same parameters as local_tds, plus radius_fraction: the mask radius grows
    from 1 to int(max(W, H) * radius_fraction) - 1 (computed in float32, as in Java).
    """
    a = np.asarray(heightmap)
    h, w = a.shape
    v = a.astype(np.int64).ravel().copy()
    max_dist = int(np.float32(max(w, h)) * np.float32(radius_fraction))
    sweeps = _global(v, w, h, int(tolerance), np.float32(random_ceil), new_state(seed), max_dist, int(max_sweeps))
    return v.reshape(h, w), sweeps
