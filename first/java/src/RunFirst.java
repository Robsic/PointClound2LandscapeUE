import interpolacoes.inter.Interpolation;
import interpolacoes.proposed.prop_GlobalDiamondSquareInterpolation;
import interpolacoes.proposed.prop_LocalToleranceDiamondSquareInterpolation;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.io.File;
import java.io.OutputStream;
import java.io.PrintStream;

/**
 * Runs the first version of the local and global Tolerance Diamond-Square classes on a PNG.
 *
 *   java -cp build RunFirst local|global input.png output.png [tolerance] [randomCeil]
 *
 * tolerance  minimum number of filled neighbors in the mask (default 2)
 * randomCeil the threshold receives round(u * randomCeil), u uniform in [0,1),
 *            drawn from java.util.Random(123) (default 1; 0 disables it)
 *
 * The classes in interpolacoes/ are kept exactly as they were written; this file
 * only reads the image, sets the two public parameters and writes the result.
 */
public class RunFirst {

    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            System.err.println("usage: RunFirst local|global input.png output.png [tolerance] [randomCeil]");
            System.exit(1);
        }
        String which = args[0];
        int tolerance = args.length > 3 ? Integer.parseInt(args[3]) : 2;
        int randomCeil = args.length > 4 ? Integer.parseInt(args[4]) : 1;

        BufferedImage in = ImageIO.read(new File(args[1]));
        WritableRaster raster = in.getRaster();

        Interpolation alg;
        if (which.equals("local")) {
            prop_LocalToleranceDiamondSquareInterpolation a = new prop_LocalToleranceDiamondSquareInterpolation();
            a.TOLERANCE = tolerance;
            a.RANDOM_TOLERANCE_CEIL = randomCeil;
            alg = a;
        } else if (which.equals("global")) {
            prop_GlobalDiamondSquareInterpolation a = new prop_GlobalDiamondSquareInterpolation();
            a.TOLERANCE = tolerance;
            a.RANDOM_TOLERANCE_CEIL = randomCeil;
            alg = a;
        } else {
            throw new IllegalArgumentException("unknown formulation: " + which);
        }

        // the classes of the first version print one line per sweep; keep the console clean
        PrintStream stdout = System.out;
        System.setOut(new PrintStream(new OutputStream() { public void write(int b) { } }));
        long t0 = System.nanoTime();
        WritableRaster res = alg.interpolate(raster);
        double ms = (System.nanoTime() - t0) / 1e6;
        System.setOut(stdout);

        BufferedImage out = new BufferedImage(in.getColorModel(), res, in.isAlphaPremultiplied(), null);
        ImageIO.write(out, "png", new File(args[2]));
        System.out.printf(java.util.Locale.US, "%s TDS (tolerance %d, random %d): %.1f ms%n", which, tolerance, randomCeil, ms);
    }
}
