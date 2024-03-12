# PointClound2LandscapeUE
This repository concerns the process of transforming a point cloud data of a real terrain in a landscape for UE.



Drive for the point cloud files (.las, .tif and .tfw): https://drive.google.com/file/d/1oF8HwK_MJfxDBFOdH6SP_8Yl8fjD0WWy/view?usp=sharing

## Tools Used:

    liblas for Point Cloud Extraction (to TXT):
        Tool Link: [liblas] (https://liblas.org/utilities/txt2las.html)
        Purpose: liblas is a library for reading and writing the LAS LiDAR format. The txt2las utility allows you to convert LAS data to ASCII text format, which can be useful for various applications.

    LAStools for OBJ Generation:
        Tool Link: [LAStools] (https://www.cs.unc.edu/~isenburg/lastools/)
        Purpose: LAStools is a collection of highly efficient tools for LiDAR processing. In this case, it seems you're using it for generating OBJ (Wavefront .obj) files, which is a common 3D model file format. This step likely involves converting the point cloud data into a format compatible with Unreal Engine for creating landscapes.


## Example:

-keep_circle 680970.807 7825086.104 1600

las2txt -i "<PATH>\file.las" -o output_lidar.txt --parse xyzRGB

:: To convert the RGB:

.\las2las.exe -keep_circle 680970.807 7825086.104 1600 -i "<PATH>\file.las" -scale_rgb_down

las2txt -i "<PATH>\file.las" -o output_with_rgb.txt --parse xyzRGB

:: To convert for Landscape:

java HeightMap16Bits_generator -width 4033 -normalize_x_y false


:: Heightmap result:

