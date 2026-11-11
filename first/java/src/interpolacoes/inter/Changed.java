package interpolacoes.inter;

import java.awt.image.WritableRaster;

public class Changed {


    WritableRaster last;
    public boolean changed(WritableRaster img) {
        if (last == null) {
            last = Interpolation.deepCopy(img);
            return true;
        }

        boolean same = true;
        OuterFor:
        for (int i=0; i<last.getHeight(); i++) {
            for (int j=0; j<last.getWidth(); j++) {
                same &= last.getSample(j, i, 0) == img.getSample(j, i, 0);
                if (!same) break OuterFor;
            }
        }

        last = Interpolation.deepCopy(img);
        return !same;
    }

    public double findClosestToAvgValue(double v1, double v2, double v3, double v4, double avg){
        //if (v1 == 0) v1 = Integer.MAX_VALUE;
        //if (v2 == 0) v2 = Integer.MAX_VALUE;
        //if (v3 == 0) v3 = Integer.MAX_VALUE;
        //if (v4 == 0) v4 = Integer.MAX_VALUE;

        final double v1Dif = Math.abs(v1 - avg),
                v2Dif = Math.abs(v2 - avg),
                v3Dif = Math.abs(v3 - avg),
                v4Dif = Math.abs(v4 - avg);

        final double minDiff = Math.min(v1Dif,  Math.min(v2Dif, Math.min(v3Dif, v4Dif)));
        if (minDiff == v1Dif) return v1;
        else if (minDiff == v2Dif) return v2;
        else if (minDiff == v3Dif) return v3;
        else return v4;
    }

}
