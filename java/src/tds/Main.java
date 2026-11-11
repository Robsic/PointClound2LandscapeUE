package tds;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Command line.
 *
 *   java -cp java/build tds.Main <algorithm> <input.png> <output.png> [--option value ...]
 *
 * algorithm: local | global | catds
 *
 * local and global
 *   --tolerance N      minimum number of filled neighbors (default 2)
 *   --random N         ceiling of the random increment of the threshold (default 1, 0 = none)
 *   --seed N           seed of java.util.Random (default 123)
 *   --max-sweeps N     stop after N sweeps (default 0 = until nothing changes)
 *   --radius-fraction F  global only: largest radius as a fraction of max(W, H) (default 0.35)
 *
 * catds
 *   --tolerance N      initial tolerance tau_0 (default 2)
 *   --rho F            confidence decay (default 0.30)
 *   --beta N           distance exponent, 1 or 2 (default 2)
 *   --dmax N           largest ring of the mask (default 16)
 *   --cmin F           confidence below which a pixel is refined (default 0.60)
 *   --no-refine        skip the refinement
 *   --synchronous      Jacobi passes instead of in-place passes
 *   --random-scale F   fractal displacement, 0 = none (default 0)
 *   --octave-cutoff N  largest scale of the displacement (default 8)
 *   --seed N           seed of the displacement (default 123)
 */
public final class Main {

    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            System.err.println("usage: tds.Main local|global|catds input.png output.png [--option value ...]");
            System.exit(1);
        }
        final String alg = args[0];
        final Map<String, String> opt = new HashMap<>();
        for (int i = 3; i < args.length; i++) {
            String k = args[i];
            if (!k.startsWith("--")) throw new IllegalArgumentException("unexpected argument: " + k);
            if (k.equals("--no-refine") || k.equals("--synchronous")) opt.put(k, "true");
            else opt.put(k, args[++i]);
        }

        final HeightMap in = HeightMap.read(args[1]);
        final HeightMap out;
        final long t0 = System.nanoTime();
        String extra = "";
        switch (alg) {
            case "local": {
                LocalTDS a = new LocalTDS();
                a.tolerance = Integer.parseInt(opt.getOrDefault("--tolerance", "2"));
                a.randomCeil = Integer.parseInt(opt.getOrDefault("--random", "1"));
                a.seed = Long.parseLong(opt.getOrDefault("--seed", "123"));
                a.maxSweeps = Integer.parseInt(opt.getOrDefault("--max-sweeps", "0"));
                out = a.interpolate(in);
                extra = a.sweeps + " sweeps";
                break;
            }
            case "global": {
                GlobalTDS a = new GlobalTDS();
                a.tolerance = Integer.parseInt(opt.getOrDefault("--tolerance", "2"));
                a.randomCeil = Integer.parseInt(opt.getOrDefault("--random", "1"));
                a.seed = Long.parseLong(opt.getOrDefault("--seed", "123"));
                a.maxSweeps = Integer.parseInt(opt.getOrDefault("--max-sweeps", "0"));
                a.maxRadiusFraction = Float.parseFloat(opt.getOrDefault("--radius-fraction", "0.35"));
                out = a.interpolate(in);
                extra = a.sweeps + " sweeps";
                break;
            }
            case "catds": {
                CATDS a = new CATDS();
                a.tolerance = Integer.parseInt(opt.getOrDefault("--tolerance", "2"));
                a.confidenceDecay = Double.parseDouble(opt.getOrDefault("--rho", "0.30"));
                a.distancePower = Integer.parseInt(opt.getOrDefault("--beta", "2"));
                a.maxRadius = Integer.parseInt(opt.getOrDefault("--dmax", "16"));
                a.refineConfidence = Double.parseDouble(opt.getOrDefault("--cmin", "0.60"));
                a.refine = !opt.containsKey("--no-refine");
                a.synchronous = opt.containsKey("--synchronous");
                a.randomScale = Double.parseDouble(opt.getOrDefault("--random-scale", "0"));
                a.octaveCutoff = Integer.parseInt(opt.getOrDefault("--octave-cutoff", "8"));
                a.seed = Long.parseLong(opt.getOrDefault("--seed", "123"));
                out = a.interpolate(in);
                extra = a.passes + " passes";
                break;
            }
            default:
                throw new IllegalArgumentException("unknown algorithm: " + alg);
        }
        final double ms = (System.nanoTime() - t0) / 1e6;
        out.write(args[2]);
        System.out.printf(Locale.US, "%s: %dx%d, %d empty in, %d empty out, %s, %.1f ms%n",
                alg, in.width, in.height, in.countEmpty(), out.countEmpty(), extra, ms);
    }
}
