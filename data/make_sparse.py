#!/usr/bin/env python3
"""Regenerates the five additional sampling experiments of sparse/ from the
complete terrains, with the seed that produced the maps in this folder.

  grid_16, grid_32   keep only the pixels with x mod s == 0 and y mod s == 0
                     (a decimated DEM, the initialization lattice of a multiscale scheme)
  grid_16_jitter     lattice of step 16 with every node displaced by up to 4 pixels
                     in each axis: same density, no alignment
  extreme_99.5       each pixel kept with probability 0.005
  large_voids        30 percent of the pixels kept at random, then blocks of
                     128 x 128 pixels cleared at random positions until about a
                     third of the area is empty


    python3 data/make_sparse.py [--maps "rolling,ridge"]
"""
import os
import sys

import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
TERRAINS = os.path.join(HERE, "terrains")
OUT = os.path.join(HERE, "sparse")
SEED = 123


def save(a, folder, name):
    d = os.path.join(OUT, folder)
    os.makedirs(d, exist_ok=True)
    Image.fromarray(a.astype(np.uint16)).save(os.path.join(d, name))


def generate(name, arr, rng):
    h, w = arr.shape

    for s in (16, 32):
        m = np.zeros((h, w), bool)
        m[::s, ::s] = True
        save(np.where(m, arr, 0), "grid_%d" % s, name)

    s = 16
    m = np.zeros((h, w), bool)
    ys, xs = np.mgrid[0:h:s, 0:w:s]
    jy = rng.integers(-s // 4, s // 4 + 1, ys.shape)
    jx = rng.integers(-s // 4, s // 4 + 1, xs.shape)
    yy = np.clip(ys + jy, 0, h - 1)
    xx = np.clip(xs + jx, 0, w - 1)
    m[yy.ravel(), xx.ravel()] = True
    save(np.where(m, arr, 0), "grid_16_jitter", name)

    m = rng.random((h, w)) < 0.005
    save(np.where(m, arr, 0), "extreme_99.5", name)

    m = rng.random((h, w)) < 0.30
    b = 128
    target = int(0.35 * h * w)
    cleared = 0
    while cleared < target:
        y0 = int(rng.integers(0, max(1, h - b)))
        x0 = int(rng.integers(0, max(1, w - b)))
        if m[y0:y0 + b, x0:x0 + b].sum() == 0:
            continue
        cleared += int(m[y0:y0 + b, x0:x0 + b].sum() / max(m.mean(), 1e-9))
        m[y0:y0 + b, x0:x0 + b] = False
    save(np.where(m, arr, 0), "large_voids", name)


def main():
    only = None
    if "--maps" in sys.argv:
        only = sys.argv[sys.argv.index("--maps") + 1].split(",")
    rng = np.random.default_rng(SEED)
    for f in sorted(os.listdir(TERRAINS)):
        if not f.lower().endswith(".png"):
            continue
        if only and not any(k.strip() in f for k in only):
            continue
        arr = np.asarray(Image.open(os.path.join(TERRAINS, f)))
        generate(f, arr, rng)
        print("done:", f, arr.shape)
    for folder in ("grid_16", "grid_16_jitter", "grid_32", "extreme_99.5", "large_voids"):
        d = os.path.join(OUT, folder)
        first = sorted(os.listdir(d))[0]
        a = np.asarray(Image.open(os.path.join(d, first)))
        print("  %-16s %5.2f%% of the pixels kept (%s)" % (folder, 100 * (a != 0).mean(), first))


if __name__ == "__main__":
    main()
