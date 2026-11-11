package interpolacoes.proposed;

import interpolacoes.inter.Changed;
import interpolacoes.inter.Interpolation;

import java.awt.image.WritableRaster;
import java.util.Random;

/**
 * Propaga o pixel apenas se os x pixels da regra do diamond-square estiverem preenchidos (de acordo com o parametro de tolerancia).
 * While global que fica repetindo até preencher toda imagem.
 */
public class prop_LocalToleranceDiamondSquareInterpolation extends Changed implements Interpolation {

    public int TOLERANCE = 4;
    public int RANDOM_TOLERANCE_CEIL = 1;
    private Random r = new Random(123);

    public WritableRaster interpolate(WritableRaster image) {


        WritableRaster out = Interpolation.deepCopy(image);

        boolean allFilled = false;
        int outCounter = 0;


        while (!allFilled) {
            allFilled = true;

            outCounter++;
            System.out.println("Contador while (local): " + outCounter);


            for (int i = 1; i < image.getHeight()-1; i++) {
                J:
                for (int j = 1; j < image.getWidth()-1; j++) {
                    if (out.getSample(j, i, 0) != 0) continue;

                    allFilled = false;

                    int allFours = 0;
                    final int d = 1;
                    double mean = 0;

                    double v1 = 0, v2 = 0, v3 = 0, v4 = 0;

                    //diamond
                    //if (d % 2 == 0) {
                    //direita
                    if (/*j+d < image.getWidth() &&*/ out.getSample(j + d, i,0) != 0) {
                        v1 = out.getSample(j + d, i,0);
                        allFours ++;
                        mean += v1;
                    }
                    //esquerda
                    if (/*j-d >= 0 && */out.getSample(j - d, i, 0) != 0) {
                        v2 = out.getSample(j - d, i, 0);
                        allFours ++;
                        mean += v2;
                    }
                    //cima
                    if (/*i - d >= 0 &&*/ out.getSample(j, i - d, 0) != 0) {
                        v3 = out.getSample(j, i - d, 0);
                        allFours ++;
                        mean += v3;
                    }
                    //baixo
                    if (/*i+d < image.getHeight() &&*/ out.getSample(j, i + d, 0) != 0) {
                        v4 = out.getSample(j, i + d, 0);
                        allFours ++;
                        mean += v4;
                    }

                    mean/= allFours;


                    if (allFours >= (TOLERANCE + Math.round(r.nextFloat()*RANDOM_TOLERANCE_CEIL))){
                        final double correctValue = findClosestToAvgValue(
                                v1, v2, v3, v4,
                                mean
                        );

                        out.setSample(j,i,0,correctValue);
                        continue J;
                    }

                    mean = 0;
                    allFours = 0;

                    //v1=0; v2=0; v3=0; v4=0;

                    //} else {
                    //square

                    //direita-cima
                    if (/*j+d < image.getWidth() && i-d >= 0 &&*/ out.getSample(j + d, i - d,0) != 0) {
                        //v1 = out.getSample(j + d, i - d,0);
                        allFours ++;
                        mean += out.getSample(j + d, i - d,0);
                    }
                    //direita-baixo
                    if (/*j+d < image.getWidth() && i+d < image.getHeight() &&*/ out.getSample(j + d, i + d,0) != 0) {
                        //v2 = out.getSample(j + d, i + d,0);
                        allFours ++;
                        mean += out.getSample(j + d, i + d,0);
                    }
                    //esq-cima
                    if (/*j-d >= 0 && i-d >=0 &&*/ out.getSample(j - d, i - d,0) != 0) {
                        //v3 = out.getSample(j - d, i - d,0);
                        allFours ++;
                        mean += out.getSample(j - d, i - d,0);
                    }
                    //esq-baixo
                    if (/*j-d>=0 && i+d < image.getHeight() &&*/ out.getSample(j - d, i + d,0) != 0) {
                        //v4 = out.getSample(j - d, i + d,0);
                        allFours ++;
                        mean += out.getSample(j - d, i + d,0);
                    }

                    mean /= allFours;

                    if (allFours >= (TOLERANCE + Math.round(r.nextFloat()*RANDOM_TOLERANCE_CEIL))){
                        //this takes the closest to the avg (but only considering the diamond with distance 1)
                        final double correctValue = findClosestToAvgValue(
                                v1, v2, v3, v4,
                                mean
                        );

                        out.setSample(j, i, 0, correctValue);
                        continue J;
                    }
                    //}

                }
            }

            //check if changed
            if (!changed(out)) return out;
        }

        return out;
    }




}
