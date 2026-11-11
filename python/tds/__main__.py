"""Command line: python -m tds <algorithm> input.png output.png [options]

    python -m tds local  in.png out.png --tolerance 2 --random 1
    python -m tds global in.png out.png --tolerance 2 --random 1
    python -m tds catds  in.png out.png --tolerance 2 --rho 0.30 --dmax 16
"""

import argparse
import sys
import time

from . import catds, global_tds, local_tds, read, write
from ._jit import HAVE_NUMBA


def main(argv=None):
    ap = argparse.ArgumentParser(prog="python -m tds", description="Fill the empty cells (value 0) of a heightmap.")
    ap.add_argument("algorithm", choices=["local", "global", "catds"])
    ap.add_argument("input")
    ap.add_argument("output")
    ap.add_argument("--tolerance", type=int, default=2, help="local/global: minimum filled neighbors; catds: initial tolerance tau_0 (default 2)")
    ap.add_argument("--random", type=int, default=1, help="local/global: ceiling of the random increment of the threshold, 0 = none (default 1)")
    ap.add_argument("--seed", type=int, default=123, help="seed of the java.util.Random generator (default 123)")
    ap.add_argument("--max-sweeps", type=int, default=0, help="local/global: stop after this many sweeps, 0 = until nothing changes")
    ap.add_argument("--radius-fraction", type=float, default=0.35, help="global: largest radius as a fraction of max(W, H) (default 0.35)")
    ap.add_argument("--rho", type=float, default=0.30, help="catds: confidence decay (default 0.30)")
    ap.add_argument("--beta", type=int, default=2, choices=[1, 2], help="catds: distance exponent (default 2)")
    ap.add_argument("--dmax", type=int, default=16, help="catds: largest ring of the mask (default 16)")
    ap.add_argument("--cmin", type=float, default=0.60, help="catds: refinement threshold (default 0.60)")
    ap.add_argument("--no-refine", action="store_true", help="catds: skip the refinement")
    ap.add_argument("--synchronous", action="store_true", help="catds: Jacobi passes instead of in-place passes")
    ap.add_argument("--random-scale", type=float, default=0.0, help="catds: fractal displacement, 0 = none (default 0)")
    ap.add_argument("--octave-cutoff", type=int, default=8, help="catds: largest scale of the displacement (default 8)")
    a = ap.parse_args(argv)

    z, max_value = read(a.input)
    t0 = time.perf_counter()
    if a.algorithm == "local":
        out, it = local_tds(z, a.tolerance, a.random, a.seed, a.max_sweeps)
        extra = "%d sweeps" % it
    elif a.algorithm == "global":
        out, it = global_tds(z, a.tolerance, a.random, a.seed, a.radius_fraction, a.max_sweeps)
        extra = "%d sweeps" % it
    else:
        out, it = catds(z, a.tolerance, a.rho, a.beta, a.dmax, a.cmin, not a.no_refine, a.synchronous,
                        a.random_scale, a.octave_cutoff, a.seed, max_value)
        extra = "%d passes" % it
    ms = (time.perf_counter() - t0) * 1000
    write(a.output, out, max_value)
    print("%s: %dx%d, %d empty in, %d empty out, %s, %.1f ms%s" % (
        a.algorithm, z.shape[1], z.shape[0], int((z == 0).sum()), int((out == 0).sum()), extra, ms,
        "" if HAVE_NUMBA else " (without numba)"))


if __name__ == "__main__":
    sys.exit(main())
