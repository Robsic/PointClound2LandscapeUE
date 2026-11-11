"""Tolerance Diamond-Square (local and global) and Confidence-Adaptive
Tolerance Diamond-Square (CATDS) for filling sparse heightmaps."""

from .catds import catds
from .heightmap import read, write
from .tds import global_tds, local_tds

__all__ = ["local_tds", "global_tds", "catds", "read", "write"]
__version__ = "1.0.0"
