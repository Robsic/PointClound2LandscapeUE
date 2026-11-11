"""The generator of java.util.Random, so that the Python and the Java versions
draw the same numbers from the same seed.

The state is kept in a one-element int64 array so that the functions can be
compiled by numba and still update it in place.
"""

import math

import numpy as np

from ._jit import jit

MULTIPLIER = 0x5DEECE66D
ADDEND = 0xB
MASK = (1 << 48) - 1


def new_state(seed):
    """state of java.util.Random(seed)"""
    return np.array([(int(seed) ^ MULTIPLIER) & MASK], dtype=np.int64)


@jit
def next_bits(state, bits):
    s = (int(state[0]) * 0x5DEECE66D + 0xB) & 0xFFFFFFFFFFFF
    state[0] = s
    r = s >> (48 - bits)
    # signed 32-bit result, as Java's (int) cast
    if r >= 0x80000000:
        r -= 0x100000000
    return r


@jit
def next_float(state):
    """java.util.Random.nextFloat(), as a float32"""
    return np.float32(next_bits(state, 24)) / np.float32(16777216.0)


@jit
def next_double(state):
    """java.util.Random.nextDouble()"""
    hi = next_bits(state, 26)
    lo = next_bits(state, 27)
    return float(hi * 134217728 + lo) * 1.1102230246251565e-16


@jit
def next_gaussian(state, spare):
    """java.util.Random.nextGaussian(); spare = [has_spare, value] (float64 array)"""
    if spare[0] != 0.0:
        spare[0] = 0.0
        return spare[1]
    while True:
        v1 = 2.0 * next_double(state) - 1.0
        v2 = 2.0 * next_double(state) - 1.0
        s = v1 * v1 + v2 * v2
        if s < 1.0 and s != 0.0:
            break
    multiplier = math.sqrt(-2.0 * math.log(s) / s)
    spare[0] = 1.0
    spare[1] = v2 * multiplier
    return v1 * multiplier


@jit
def java_round_float(x):
    """Math.round(float): nearest integer, halves rounded up"""
    return int(math.floor(float(x) + 0.5))
