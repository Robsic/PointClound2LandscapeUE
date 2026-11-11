package tds;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.io.File;
import java.io.IOException;

/**
 * A single-band raster held as an int array in row-major order.
 * The value 0 marks a cell without a sample.
 */
public final class HeightMap {

    public final int width, height;
    public final int[] data;
    /** largest representable value: 65535 for 16-bit images, 255 for 8-bit */
    public final int maxValue;
    private final BufferedImage template;

    public HeightMap(int width, int height, int maxValue) {
        this.width = width;
        this.height = height;
        this.maxValue = maxValue;
        this.data = new int[width * height];
        this.template = null;
    }

    private HeightMap(BufferedImage img) {
        WritableRaster r = img.getRaster();
        this.width = r.getWidth();
        this.height = r.getHeight();
        this.maxValue = (1 << r.getSampleModel().getSampleSize(0)) - 1;
        this.data = new int[width * height];
        r.getSamples(0, 0, width, height, 0, data);
        this.template = img;
    }

    /** reads the first band of a PNG (8 or 16 bit grayscale) */
    public static HeightMap read(String path) throws IOException {
        BufferedImage img = ImageIO.read(new File(path));
        if (img == null) throw new IOException("cannot read image: " + path);
        return new HeightMap(img);
    }

    public HeightMap copy() {
        HeightMap c = template != null ? new HeightMap(template) : new HeightMap(width, height, maxValue);
        System.arraycopy(data, 0, c.data, 0, data.length);
        return c;
    }

    /** writes a grayscale PNG with the bit depth of the image that was read */
    public void write(String path) throws IOException {
        BufferedImage out;
        if (template != null) {
            WritableRaster r = template.getRaster().createCompatibleWritableRaster();
            r.setSamples(0, 0, width, height, 0, data);
            out = new BufferedImage(template.getColorModel(), r, template.isAlphaPremultiplied(), null);
        } else {
            out = new BufferedImage(width, height, maxValue > 255 ? BufferedImage.TYPE_USHORT_GRAY : BufferedImage.TYPE_BYTE_GRAY);
            out.getRaster().setSamples(0, 0, width, height, 0, data);
        }
        ImageIO.write(out, "png", new File(path));
    }

    public int countEmpty() {
        int c = 0;
        for (int v : data) if (v == 0) c++;
        return c;
    }
}
