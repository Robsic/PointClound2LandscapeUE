"""Reading and writing grayscale PNG heightmaps (8 or 16 bit)."""

import numpy as np
from PIL import Image


def read(path):
    """Returns (array int64 of shape (H, W), max_value). The value 0 marks a cell without a sample."""
    im = Image.open(path)
    a = np.array(im)
    if a.ndim == 3:            # color image: first band, as the Java version
        a = a[..., 0]
    if im.mode in ("I;16", "I;16B", "I;16L", "I"):
        max_value = 65535
    elif a.dtype == np.uint16:
        max_value = 65535
    else:
        max_value = 255
    return a.astype(np.int64), max_value


def write(path, array, max_value):
    """Writes a 16-bit PNG when max_value > 255, an 8-bit PNG otherwise."""
    a = np.asarray(array)
    if max_value > 255:
        Image.fromarray(a.astype(np.uint16)).save(path)
    else:
        Image.fromarray(a.astype(np.uint8)).save(path)
