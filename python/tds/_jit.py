"""Optional compilation with numba.

With numba installed the loops run as compiled code. Without it the same
functions run as plain Python, which gives the same result but is only practical
for small images (a few hundred pixels across).
"""

try:
    from numba import njit as _njit

    def jit(f):
        return _njit(cache=True)(f)

    HAVE_NUMBA = True
except ImportError:  # pragma: no cover
    def jit(f):
        return f

    HAVE_NUMBA = False
