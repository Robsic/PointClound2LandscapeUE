package interpolacoes.proposed;

import interpolacoes.inter.Changed;
import interpolacoes.inter.Interpolation;

import java.awt.image.WritableRaster;
import java.util.Random;

/**
 * Propaga o pixel apenas se os 4 pixels da regra do diamond-square estiverem preenchidos.
 * While global que fica repetindo até preencher toda imagem.
 * Começa da menor distancia e vai aumentando (é centrado no pixel que é pra ser preenchido - não tem um senso local).
 */
public class prop_GlobalDiamondSquareInterpolation extends Changed implements Interpolation {

    public int TOLERANCE = 4; //pelo menos 4 preenchidos (std)
    public int RANDOM_TOLERANCE_CEIL = 1;
    private Random r = new Random(123);
    private int maxDist;


    public WritableRaster interpolate(WritableRaster image) {
        WritableRaster out = Interpolation.deepCopy(image);

        boolean allFilled = false;

        int outCounter = 0;

        //diminuir pra ir mais rapido
        maxDist = (int) (Math.max(image.getWidth(), image.getHeight())*0.35f);

        while (!allFilled) {
            allFilled = true;

            outCounter++;
            System.out.println("Contador while (global): " + outCounter);


            for (int i = 0; i < image.getHeight(); i++) {
                if (i%500==0) System.out.println("Linha (resto da div por 500): " + i);

                J:
                for (int j = 0; j < image.getWidth(); j++) {
                    if (out.getSample(j, i, 0) != 0) continue;
                    else allFilled = false;


                    for (int d = 1; d < maxDist; d++) {
                        int allFours = 0;
                        double mean = 0;
                        double v1 =0, v2 = 0, v3 = 0, v4 = 0;

                        //diamond
                        //if (d % 2 == 0) {
                            //direita
                            if (j+d < image.getWidth() && out.getSample(j + d, i,0) != 0) {
                                v1 = out.getSample(j + d, i,0);
                                allFours ++;
                                mean += v1;
                            }
                            //esquerda
                            if (j-d >= 0 && out.getSample(j - d, i, 0) != 0) {
                                v2 = out.getSample(j - d, i, 0);
                                allFours ++;
                                mean += v2;
                            }
                            //cima
                            if (i - d >= 0 && out.getSample(j, i - d,0) != 0) {
                                v3 = out.getSample(j, i - d,0);
                                allFours ++;
                                mean += v3;
                            }
                            //baixo
                            if (i+d < image.getHeight() && out.getSample(j, i + d,0) != 0) {
                                v4 = out.getSample(j, i + d,0);
                                allFours ++;
                                mean += v4;
                            }

                            mean /= allFours;

                            if (allFours >= (TOLERANCE + Math.round(r.nextFloat()*RANDOM_TOLERANCE_CEIL))){
                                final double correctValue = findClosestToAvgValue(
                                        v1,
                                        v2,
                                        v3,
                                        v4,
                                        mean
                                );

                                out.setSample(j,i,0, correctValue);
                                continue J;
                            }

                            mean = 0;
                            allFours = 0;
                            //v1=0; v2=0; v3=0; v4=0;

                        //} else {
                            //square

                            //direita-cima
                            if (j+d < image.getWidth() && i-d >= 0 && out.getSample(j + d, i - d, 0) != 0) {
                                //v1 = out.getSample(j + d, i - d, 0);
                                allFours ++;
                                mean += out.getSample(j + d, i - d, 0);
                            }
                            //direita-baixo
                            if (j+d < image.getWidth() && i+d < image.getHeight() && out.getSample(j + d, i + d,0) != 0) {
                                //v2 = out.getSample(j + d, i + d,0);
                                allFours ++;
                                mean += out.getSample(j + d, i + d,0);
                            }
                            //esq-cima
                            if (j-d >= 0 && i-d >=0 && out.getSample(j - d, i - d,0) != 0) {
                                //v3 = out.getSample(j - d, i - d,0);
                                allFours ++;
                                mean += out.getSample(j - d, i - d,0);
                            }
                            //esq-baixo
                            if (j-d>=0 && i+d < image.getHeight() && out.getSample(j - d, i + d,0) != 0) {
                                //v4 = out.getSample(j - d, i + d,0);
                                allFours ++;
                                mean += out.getSample(j - d, i + d,0);
                            }

                            mean /= allFours;

                            if (allFours >= (TOLERANCE + Math.round(r.nextFloat()*RANDOM_TOLERANCE_CEIL))){
                                //this takes the closest to the avg (but only considering the diamond with distance 1)
                                final double correctValue = findClosestToAvgValue(
                                        v1,
                                        v2,
                                        v3,
                                        v4,
                                        mean
                                );

                                out.setSample(j, i, 0, correctValue);
                                continue J;
                            }
                        //}

                    }

                }
            }

            //check if changed
            if (!changed(out)) return out;

        }

        return out;
    }


}
