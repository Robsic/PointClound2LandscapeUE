package vale;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;

public class GerarHeightMap16Bits {

    public static short[][] map;
    private static WritableRaster Raster;

    static class Point2D{
        Point2D(int x, int y){this.x = x; this.y = y;}
        int x, y;
    }
    static class Vector3{
        Vector3(int x, int y, int z){this.x = x; this.y = y; this.z = z;}
        Vector3(){}
        public float x, y, z;
        public int r, g, b;
    }
    static class AvgPixel{
        public int value;
        public int counter;
        public int r, g, b;
        public int getAvg(){return this.value/this.counter;}
    }
    public static HashMap<Integer, HashMap<Integer, AvgPixel>> img;
    public static ArrayList<Vector3> points;


    //VARIAVEIS
    private static Random r;
    static String FOLDER = "", PATH = "";
    static int WIDTH = 0, HEIGHT = 0, MAX_IT = 0, TOLERANCE = 0, SEED_NUMBER = 0,
            MIN_HEIGHT_POINTCLOUD = 0, MAX_HEIGHT_POINTCLOUD = 0, MIN_HEIGHT_IMAGE = 0, MAX_HEIGHT_IMAGE = 0, KERNEL_BLUR_SIZE = 0;
    static boolean NORMALIZE = false, INVERT_Y = false, EXPORT_COLOR = false, EXPORT_TEXT_COORD_FILES = false;


    public static void main(String[] args) throws Exception {
        /* PARAMS / ARGUMENTS */
        //argumentos padrões
        final int ARG_SIZE = 30;
        String[] stdArgs = new String[ARG_SIZE];
        if (args == null) args = stdArgs;
        stdArgs[0] = "-input"; //Caminho do arquivo de entrada (txt contendo as coordenadas e rgb, dados separados por espaço)
        stdArgs[1] = "E:\\Downloads\\TOPO_NOVO\\Primeiro Mapa\\lidar.txt";
        stdArgs[2] = "-width";
        stdArgs[3] = "4033"; //4033
        stdArgs[4] = "-height";
        stdArgs[5] = "4033";
        stdArgs[6] = "-dilation_times"; //Quantidade máxima de "dilatações"/espalhamentos dos pixels na point cloud (quanto menor mais rápido mas pode deixar mais buracos)
        stdArgs[7] = "20";
        stdArgs[8] = "-normalize_x_y"; //Se é pra normalizar as dimensões x e y da imagem pra ficar do tamanho da resolução passada como parâmetro
        stdArgs[9] = "false";
        stdArgs[10] = "-tolerance_number"; //número de tolerância para a dilatação, utilizado no algoritmo diamante-quadrado
        stdArgs[11] = "2";
        stdArgs[12] = "-seed"; //seed para a geração randômica
        stdArgs[13] = "1";
        stdArgs[14] = "-min_heightvalue_pointcloud"; //altura mínima para considerar dos dados do point cloud
        stdArgs[15] = "0"; //700
        stdArgs[16] = "-max_heightvalue_pointcloud"; //altura máxima para considerar dos dados do point cloud
        stdArgs[17] = "9999999"; //32000
        stdArgs[18] = "-min_heightvalue_image"; //começar a desenhar os pontos/alturas na imagem a partir desse valor
        stdArgs[19] = "0";
        stdArgs[20] = "-max_heightvalue_image"; //terminar de desenhar os pontos/alturas da imagem nesse valor (valor máximo)
        stdArgs[21] = "65535";
        stdArgs[22] = "-invert_y"; //inventer em y
        stdArgs[23] = "true";
        stdArgs[24] = "-export_color"; //exportar imagens em cor
        stdArgs[25] = "true";
        stdArgs[26] = "-export_text_coord_files"; //exportar as correspondencias das coordenadas em arquivos .txt
        stdArgs[27] = "true";
        stdArgs[28] = "-box_blur_size"; //tamanho do kernel do algoritmo box-blur
        stdArgs[29] = "5";

        if (args.length == 1 && args[0].contains("help")) {
            System.out.println("Os parâmetros disponíveis e seus padrões são: ");
            for (int l=0; l< stdArgs.length; l++) System.out.println(stdArgs[l]);
            System.exit(0);
        }

        String[] itArgs = stdArgs;
        for (int t=0; t<2; t++) {
            if (t==1) itArgs = args;
            for (int k = 0; k < itArgs.length; k++) {
                try {
                    if (itArgs[k].contains(stdArgs[0])) {
                        PATH = new File(itArgs[k + 1]).getAbsolutePath();
                    }
                    if (itArgs[k].contains(stdArgs[2])) {
                        WIDTH = Integer.parseInt(itArgs[k + 1]);
                    }
                    if (itArgs[k].contains(stdArgs[4])) {
                        HEIGHT = Integer.parseInt(itArgs[k + 1]);
                    }
                    if (itArgs[k].contains(stdArgs[6])) {
                        MAX_IT = Integer.parseInt(itArgs[k + 1]);
                    }
                    if (itArgs[k].contains(stdArgs[8])) {
                        NORMALIZE = Boolean.parseBoolean(itArgs[k + 1]);
                    }
                    if (itArgs[k].contains(stdArgs[10])) {
                        TOLERANCE = Integer.parseInt(itArgs[k + 1]);
                    }
                    if (itArgs[k].contains(stdArgs[12])) {
                        SEED_NUMBER = Integer.parseInt(itArgs[k + 1]);
                    }
                    if (itArgs[k].contains(stdArgs[14])) {
                        MIN_HEIGHT_POINTCLOUD = Integer.parseInt(itArgs[k + 1]);
                    }
                    if (itArgs[k].contains(stdArgs[16])) {
                        MAX_HEIGHT_POINTCLOUD = Integer.parseInt(itArgs[k + 1]);
                    }
                    if (itArgs[k].contains(stdArgs[18])) {
                        MIN_HEIGHT_IMAGE = Integer.parseInt(itArgs[k + 1]);
                    }
                    if (itArgs[k].contains(stdArgs[20])) {
                        MAX_HEIGHT_IMAGE = Integer.parseInt(itArgs[k + 1]);
                    }
                    if (itArgs[k].contains(stdArgs[22])) {
                        INVERT_Y = Boolean.parseBoolean(itArgs[k + 1]);
                    }
                    if (itArgs[k].contains(stdArgs[24])) {
                        EXPORT_COLOR = Boolean.parseBoolean(itArgs[k + 1]);
                    }
                    if (itArgs[k].contains(stdArgs[26])) {
                        EXPORT_TEXT_COORD_FILES = Boolean.parseBoolean(itArgs[k + 1]);
                    }
                    if (itArgs[k].contains(stdArgs[28])) {
                        KERNEL_BLUR_SIZE = Integer.parseInt(itArgs[k + 1]);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    System.out.println("Houve um erro nos parâmetros.");
                    System.exit(0);
                }
            }
        }
        r = new Random(SEED_NUMBER);
        FOLDER = new File(PATH).getParentFile().getAbsolutePath();
        //FIM DOS PARAMETROS


        //LEITURAS
        String path = PATH;
        File txtFile = new File(path);
        BufferedReader br = new BufferedReader(new FileReader(txtFile));
        String line;

        BufferedWriter bwImg = null, bwOrig = null;
        if (EXPORT_TEXT_COORD_FILES) {
            File foutImg = new File(FOLDER + "/heightMap_coordenada_imagem.txt");
            FileOutputStream fosImg = new FileOutputStream(foutImg);
            bwImg = new BufferedWriter(new OutputStreamWriter(fosImg));

            File foutOrig = new File(FOLDER + "/heightMap_original.txt");
            FileOutputStream fosOrig = new FileOutputStream(foutOrig);
            bwOrig = new BufferedWriter(new OutputStreamWriter(fosOrig));
        }

        //DECLARAÇÕES
        points = new ArrayList<Vector3>();
        //

        System.out.println("As imagens finais serão exportadas com largura " + WIDTH + " e altura " + HEIGHT + "...");
        System.out.println("Fazendo leitura dos dados em texto...");

        //CALCULAR OS VALORES MÁXIMOS E MINIMOS
        float maxX = 0, maxY = 0, maxZ = 0;
        float minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        String[] split;
        while ((line = br.readLine()) != null){
            Vector3 v3 = new Vector3();
            split = line.split(" ");

            float n = Float.parseFloat(split[0]);
            if (maxX < n)
                maxX = n;
            if (minX > n)
                minX = n;
            v3.x = n;

            n = Float.parseFloat(split[1]);
            if (maxY < n)
                maxY = n;
            if (minY > n)
                minY = n;
            v3.y = n;

            n = Float.parseFloat(split[2]);
            if (maxZ < n)
                maxZ = n;
            if (minZ > n)
                minZ = n;
            v3.z = n;

            if (v3.z < MIN_HEIGHT_POINTCLOUD) continue; //pular se for menor que o tamanho minimo estipulado
            if (v3.z > MAX_HEIGHT_POINTCLOUD) continue; //pular se for maior que o tamanho máximo estipulado

            //rgb
            try {
                if (EXPORT_COLOR) {
                    v3.r = Integer.parseInt(split[3]);
                    v3.g = Integer.parseInt(split[4]);
                    v3.b = Integer.parseInt(split[5]);
                }
            }catch(Exception e){
                e.printStackTrace();
                EXPORT_COLOR = false;
            }

            points.add(v3);
        }
        //VARIAÇÃO EM CADA EIXO
        float dX = maxX - minX, dY = maxY - minY, dZ = maxZ - minZ;

        System.out.println("Um total de " + points.size() + " pontos foram lidos e adicionados à memória para processamento...");
        System.out.println("O tamanho/variação/delta das dimensões em x, y e z são respectivamente: " + dX + "," + dY + "," + dZ + "...");
        System.out.println("    ::::[Min x: " + minX + ", Max x: " + maxX + ", Min y: " + minY + ", Max y: " + maxY + ", Min z: " + minZ + ", Max z: " + maxZ + "]");

        //COMEÇAR A PREENCHER A IMAGEM NA MEMÓRIA
        img = new HashMap<Integer, HashMap<Integer, AvgPixel>>(WIDTH);
        Vector3 v3;
        for (int k=0; k< points.size(); k++){
            v3 = points.get(k);

            if (EXPORT_TEXT_COORD_FILES) {
                bwOrig.write(v3.x + "," + v3.y + "," + v3.z);
                bwOrig.newLine();
            }

            int x, y, z;
            if (NORMALIZE){
                final float aspectRatio = dX/dY; //pra não distorcer a imagem se os tamanhos (altura e largura) forem diferentes
                if (dX > dY) {
                    x = Math.round(((v3.x - minX) / (dX)) * WIDTH);
                    y = Math.round(((v3.y - minY) / (dY)) * HEIGHT/aspectRatio);
                }else{
                    x = Math.round(((v3.x - minX) / (dX)) * WIDTH*aspectRatio);
                    y = Math.round(((v3.y - minY) / (dY)) * HEIGHT);
                }
            }else {
                x = Math.round(((v3.x - minX)));
                y = Math.round(((v3.y - minY)));
            }
            z = MIN_HEIGHT_IMAGE + Math.round(((v3.z - minZ) / (dZ)) * Math.abs(MIN_HEIGHT_IMAGE-MAX_HEIGHT_IMAGE));

            if (EXPORT_TEXT_COORD_FILES) {
                bwImg.write(x + "," + y + "," + z);
                bwImg.newLine();
            }

            if (!img.containsKey(x))
                img.put(x, new HashMap<Integer, AvgPixel>(HEIGHT));
            if (!img.get(x).containsKey(y))
                img.get(x).put(y, new AvgPixel());
            img.get(x).get(y).counter ++;
            img.get(x).get(y).value += z;

            //rgb
            img.get(x).get(y).r = v3.r;
            img.get(x).get(y).g = v3.g;
            img.get(x).get(y).b = v3.b;

        }

        if (EXPORT_TEXT_COORD_FILES) {
            bwImg.flush();
            bwImg.close();
            bwOrig.flush();
            bwOrig.close();
        }


        System.out.println("Finalizada a conversão das coordenadas de point cloud para as coordenadas de imagem...");


        BufferedImage grayImg = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_USHORT_GRAY);
        WritableRaster grayRaster = grayImg.getRaster();

        BufferedImage rgbImg = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        WritableRaster rgbRaster = rgbImg.getRaster();


        for (int i=0; i<HEIGHT; i++){
            for (int j=0; j<WIDTH; j++){

                if (!img.containsKey(j)) continue;
                if (!img.get(j).containsKey(i)) continue;

                grayRaster.setPixel(j, INVERT_Y ? HEIGHT - i - 1 : i, new int[]{img.get(j).get(i).getAvg()});
                rgbRaster.setPixel(j, INVERT_Y ? HEIGHT - i - 1 : i, new int[]{
                        img.get(j).get(i).r*255/65535,
                        img.get(j).get(i).g*255/65535,
                        img.get(j).get(i).b*255/65535});

            }
        }

        //grayImg.exportImage(args[1] + "/cloudpoint.png");
        ImageIO.write(grayImg, "png", new File(FOLDER + "/01.cloudpoint.png"));

        if (EXPORT_COLOR) {
            System.out.println("Exportando a versão colorida...");
            ImageIO.write(rgbImg, "png", new File(FOLDER + "/01.2.cloudpoint-colorido.png"));
        }


        System.out.println("Aplicando o algoritmo diamante em cinza e exportando...");



        diamondAlgorithm(deepCopy(grayImg).getRaster(), grayRaster, rgbImg.getRaster(),
                deepCopy(rgbImg).getRaster(), MAX_IT);

        ImageIO.write(grayImg, "png", new File(FOLDER + "/02.cinza-expandido.png"));


        if (EXPORT_COLOR) {
            System.out.println("Exportando a versão colorida expandida...");
            ImageIO.write(rgbImg, "png", new File(FOLDER + "/02.2.colorido-expandido.png"));
        }

        System.out.println("Descendo a tolerância do algoritmo e exportando a versão final...");


        TOLERANCE = 1;
        diamondAlgorithm(deepCopy(grayImg).getRaster(), grayRaster, rgbImg.getRaster(),
                deepCopy(rgbImg).getRaster(), MAX_IT);
        ImageIO.write(grayImg, "png", new File(FOLDER + "/03.cinza-expandido-aindamais.png"));
        if (EXPORT_COLOR) {
            System.out.println("Exportando a versão colorida expandida...");
            ImageIO.write(rgbImg, "png", new File(FOLDER + "/03.2.colorido-expandido-aindamais.png"));
        }

        //aplicando desfoque gaussiano
        boxBlur(deepCopy(grayImg).getRaster(), grayImg.getRaster(), KERNEL_BLUR_SIZE);
        ImageIO.write(grayImg, "png", new File(FOLDER + "/04.cinza-expandido-aindamais-5kernelblur.png"));


        System.out.println("Finalizado com sucesso.");

    }

    static void boxBlur(Raster in, WritableRaster out, int KERNEL_SIZE){
        if (KERNEL_SIZE % 2 == 0) KERNEL_SIZE++;
        final int KERNEL_HALF = KERNEL_SIZE/2;

        for (int i=KERNEL_HALF; i<in.getHeight()-KERNEL_HALF; i++){
            for (int j=KERNEL_HALF; j<in.getWidth()-KERNEL_HALF; j++){

                int average = 0; int counter = 0;
                for (int i2=-KERNEL_HALF; i2<KERNEL_HALF; i2++){
                    for (int j2=-KERNEL_HALF; j2<KERNEL_HALF; j2++){
                        average += in.getSample(j + j2, i + i2, 0);
                        counter++;
                    }
                }
                average /= counter;

                out.setSample(j, i, 0, average);

            }
        }
    }

    static BufferedImage deepCopy(BufferedImage bi) {
        ColorModel cm = bi.getColorModel();
        boolean isAlphaPremultiplied = cm.isAlphaPremultiplied();
        WritableRaster raster = bi.copyData(null);
        return new BufferedImage(cm, raster, isAlphaPremultiplied, null);
    }
    static WritableRaster deepCopy(Raster inRaster, WritableRaster outRaster) {
        int v[] = new int[inRaster.getNumBands()];
        for (int i=0; i<inRaster.getHeight(); i++){
            for (int j=0; j<inRaster.getWidth(); j++){
                v = inRaster.getPixel(j, i, v);
                outRaster.setPixel(j, i, v);
            }
        }
        return outRaster;
    }

    public static void diamondAlgorithm(final WritableRaster inputGrayImage, final WritableRaster outGrayImg, final WritableRaster inRGB, final WritableRaster outRGB,
                                        final int maxDist) throws Exception {
        int percCounter = 0;
        int it = 0;
        int[] v = new int[1];
        while (it < maxDist){

            final int CHUNKS = 10;
            final int CHUNK_SIZE = maxDist/CHUNKS;
            if (it % CHUNK_SIZE == 0){
                System.out.println("    ::::" + CHUNKS*(percCounter) + "%...");
                percCounter++;
            }

            for (int i=1; i<outGrayImg.getHeight()-1; i++){
                for (int j=1; j<outGrayImg.getWidth()-1; j++){

                    v = inputGrayImage.getPixel(j, i, v);
                    if (v[0] == 0){

                        //diamante
                        int neiCounter = 0;
                        double average = 0;
                        v = inputGrayImage.getPixel(j + 1, i, v);
                        if (v[0] != 0) {neiCounter++; average += v[0];}
                        v = inputGrayImage.getPixel(j - 1, i, v);
                        if (v[0] != 0) {neiCounter++; average += v[0];}
                        v = inputGrayImage.getPixel(j, i+1, v);
                        if (v[0] != 0) {neiCounter++; average += v[0];}
                        v = inputGrayImage.getPixel(j, i-1, v);
                        if (v[0] != 0) {neiCounter++; average += v[0];}

                        //quadrado
                        int nei2Counter = 0;
                        double average2 = 0;
                        v = inputGrayImage.getPixel(j + 1, i + 1, v);
                        if (v[0] != 0) {nei2Counter++; average2 += v[0];}
                        v = inputGrayImage.getPixel(j - 1, i + 1, v);
                        if (v[0] != 0) {nei2Counter++; average2 += v[0];}
                        v = inputGrayImage.getPixel(j - 1, i+1, v);
                        if (v[0] != 0) {nei2Counter++; average2 += v[0];}
                        v = inputGrayImage.getPixel(j + 1, i-1, v);
                        if (v[0] != 0) {nei2Counter++; average2 += v[0];}

                        boolean var = r.nextBoolean();
                        if (!(neiCounter >= TOLERANCE + ((var)?1:0)) &&
                                nei2Counter >= TOLERANCE + ((var)?1:0)){
                            average = average2;
                            neiCounter = nei2Counter;
                        }

                        if (neiCounter >= TOLERANCE + ((var)?1:0)) {
                            average /= neiCounter;

                            v = new int[]{(int) average};
                            outGrayImg.setPixel(j, i, v);

                            //buscar o pixel mais próximo do "average"
                            int rightX = j, rightY = i;
                            double rightAverage = Integer.MAX_VALUE;
                            v = inputGrayImage.getPixel(j + 1, i, v);
                            if (v[0] != 0 && Math.abs(average-v[0]) < rightAverage) {rightX = j+1; rightY = i; rightAverage = Math.abs(average-v[0]);}
                            v = inputGrayImage.getPixel(j - 1, i, v);
                            if (v[0] != 0 && Math.abs(average-v[0]) < rightAverage) {rightX = j-1; rightY = i; rightAverage = Math.abs(average-v[0]);}
                            v = inputGrayImage.getPixel(j, i+1, v);
                            if (v[0] != 0 && Math.abs(average-v[0]) < rightAverage) {rightX = j; rightY = i+1; rightAverage = Math.abs(average-v[0]);}
                            v = inputGrayImage.getPixel(j, i-1, v);
                            if (v[0] != 0 && Math.abs(average-v[0]) < rightAverage) {rightX = j; rightY = i-1; rightAverage = Math.abs(average-v[0]);}
                            v = new int[3];
                            outRGB.setPixel(j, i, inRGB.getPixel(rightX, rightY, v));
                            outGrayImg.setPixel(j, i, inputGrayImage.getPixel(rightX, rightY, v));


                        }

                    }

                }
            }

            deepCopy(outGrayImg, inputGrayImage);
            deepCopy(outRGB, inRGB);

            it++;
        }
    }

    /*
        public static boolean containsRGB(HashMap<int[], Byte> hashMap, int[] values){
        for (int[] key : hashMap.keySet()){
            boolean equal = true;
            for (int k=0; k<key.length; k++){
                equal &= key[k] == values[k];
            }

            if (equal) {
                hashMap.put(key, (byte) (hashMap.remove(key) + 1));
                return true;
            }
        }
        return false;
    }
    public static boolean isBlack(final Raster img, final int x, final int y){
        if (x < 0 || y < 0 || x >= img.getWidth() || y >= img.getHeight()) return false;
        boolean black = true;
        int[] result = new int[1];
        result = img.getPixel(x, y, result);
        black &= result[0] == 0;
        return black;
    }

    public static boolean withinBoundaries(final Raster img, int x, int y){
        if (x < 0 || y < 0 || x >= img.getWidth() || y >= img.getHeight()) return false;
        else return true;
    }

    public static boolean withinBoundaries(final Image img, int x, int y){
        if (x < 0 || y < 0 || x >= img.getWidth() || y >= img.getHeight()) return false;
        else return true;
    }
    public static boolean isBlack(final Image img, final int x, final int y){
        if (x < 0 || y < 0 || x >= img.getWidth() || y >= img.getHeight()) return false;
        boolean black = true;
        for (int b=0; b<img.getNumBands(); b++){
            black &= img.getPixel(x, y, b) == 0;
        }
        return black;
    }
*/


}
