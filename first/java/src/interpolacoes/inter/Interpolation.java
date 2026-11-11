package interpolacoes.inter;

import java.awt.image.WritableRaster;

public interface Interpolation {

    public WritableRaster interpolate(WritableRaster image);


    public static WritableRaster deepCopy(WritableRaster raster) {
        // Create a compatible raster with the same size and sample model
        WritableRaster copy = raster.createCompatibleWritableRaster();

        // Copy all pixel data from source raster into the new one
        copy.setRect(raster);

        return copy;
    }

}
