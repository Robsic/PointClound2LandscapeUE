#!/usr/bin/env python3

import numpy as np
from PIL import Image
import argparse
import random
import sys


class Point2D:
    def __init__(self, x, y):
        self.x = x
        self.y = y


class Vector3:
    def __init__(self, x=0, y=0, z=0):
        self.x = x
        self.y = y
        self.z = z
        self.r = 0
        self.g = 0
        self.b = 0


class AvgPixel:
    def __init__(self):
        self.value = 0
        self.counter = 0
        self.r = 0
        self.g = 0
        self.b = 0

    def getAvg(self):
        return self.value // self.counter


def box_blur(in_array, out_array, kernel_size):
    if kernel_size % 2 == 0:
        kernel_size += 1
    kernel_half = kernel_size // 2

    for i in range(kernel_half, in_array.shape[0] - kernel_half):
        for j in range(kernel_half, in_array.shape[1] - kernel_half):
            average = 0
            counter = 0
            for i2 in range(-kernel_half, kernel_half):
                for j2 in range(-kernel_half, kernel_half):
                    average += in_array[i + i2, j + j2]
                    counter += 1
            average //= counter
            out_array[i, j] = average


def deep_copy(array):
    return np.copy(array)


def diamond_algorithm(
    input_gray_image, out_gray_img, in_rgb, out_rgb, max_dist, tolerance, r
):
    perc_counter = 0
    it = 0
    while it < max_dist:
        chunks = 10
        chunk_size = max_dist // chunks
        if it % chunk_size == 0:
            print(f"    ::::{chunks * perc_counter}%...")
            perc_counter += 1

        for i in range(1, out_gray_img.shape[0] - 1):
            for j in range(1, out_gray_img.shape[1] - 1):
                if input_gray_image[i, j] == 0:
                    nei_counter = 0
                    average = 0
                    if input_gray_image[i, j + 1] != 0:
                        nei_counter += 1
                        average += input_gray_image[i, j + 1]
                    if input_gray_image[i, j - 1] != 0:
                        nei_counter += 1
                        average += input_gray_image[i, j - 1]
                    if input_gray_image[i + 1, j] != 0:
                        nei_counter += 1
                        average += input_gray_image[i + 1, j]
                    if input_gray_image[i - 1, j] != 0:
                        nei_counter += 1
                        average += input_gray_image[i - 1, j]

                    nei2_counter = 0
                    average2 = 0
                    if input_gray_image[i + 1, j + 1] != 0:
                        nei2_counter += 1
                        average2 += input_gray_image[i + 1, j + 1]
                    if input_gray_image[i - 1, j + 1] != 0:
                        nei2_counter += 1
                        average2 += input_gray_image[i - 1, j + 1]
                    if input_gray_image[i - 1, j + 1] != 0:
                        nei2_counter += 1
                        average2 += input_gray_image[i - 1, j + 1]
                    if input_gray_image[i + 1, j - 1] != 0:
                        nei2_counter += 1
                        average2 += input_gray_image[i + 1, j - 1]

                    var = r.getrandbits(1)
                    if not (
                        nei_counter >= tolerance + (1 if var else 0)
                    ) and nei2_counter >= tolerance + (1 if var else 0):
                        average = average2
                        nei_counter = nei2_counter

                    if nei_counter >= tolerance + (1 if var else 0):
                        average //= nei_counter
                        out_gray_img[i, j] = average

                        right_x = j
                        right_y = i
                        right_average = sys.maxsize
                        if (
                            input_gray_image[i, j + 1] != 0
                            and abs(average - input_gray_image[i, j + 1])
                            < right_average
                        ):
                            right_x = j + 1
                            right_y = i
                            right_average = abs(average - input_gray_image[i, j + 1])
                        if (
                            input_gray_image[i, j - 1] != 0
                            and abs(average - input_gray_image[i, j - 1])
                            < right_average
                        ):
                            right_x = j - 1
                            right_y = i
                            right_average = abs(average - input_gray_image[i, j - 1])
                        if (
                            input_gray_image[i + 1, j] != 0
                            and abs(average - input_gray_image[i + 1, j])
                            < right_average
                        ):
                            right_x = j
                            right_y = i + 1
                            right_average = abs(average - input_gray_image[i + 1, j])
                        if (
                            input_gray_image[i - 1, j] != 0
                            and abs(average - input_gray_image[i - 1, j])
                            < right_average
                        ):
                            right_x = j
                            right_y = i - 1
                            right_average = abs(average - input_gray_image[i - 1, j])
                        out_rgb[i, j] = in_rgb[right_y, right_x]
                        out_gray_img[i, j] = input_gray_image[right_y, right_x]

        np.copyto(input_gray_image, out_gray_img)
        np.copyto(in_rgb, out_rgb)
        it += 1


def main():
    parser = argparse.ArgumentParser(description="Process some integers.")
    parser.add_argument("-input", type=str, default="./lidar.txt")
    parser.add_argument("-width", type=int, default=4033)
    parser.add_argument("-height", type=int, default=4033)
    parser.add_argument("-dilation_times", type=int, default=20)
    parser.add_argument(
        "-normalize_x_y", type=lambda x: (str(x).lower() == "true"), default=False
    )
    parser.add_argument("-tolerance_number", type=int, default=2)
    parser.add_argument("-seed", type=int, default=1)
    parser.add_argument("-min_heightvalue_pointcloud", type=int, default=0)
    parser.add_argument("-max_heightvalue_pointcloud", type=int, default=9999999)
    parser.add_argument("-min_heightvalue_image", type=int, default=0)
    parser.add_argument("-max_heightvalue_image", type=int, default=65535)
    parser.add_argument(
        "-invert_y", type=lambda x: (str(x).lower() == "true"), default=True
    )
    parser.add_argument(
        "-export_color", type=lambda x: (str(x).lower() == "true"), default=True
    )
    parser.add_argument(
        "-export_text_coord_files",
        type=lambda x: (str(x).lower() == "true"),
        default=True,
    )
    parser.add_argument("-box_blur_size", type=int, default=5)

    args = parser.parse_args()

    r = random.Random(args.seed)
    folder = "/".join(args.input.split("/")[:-1])

    points = []

    print(
        f"As imagens finais serão exportadas com largura {args.width} e altura {args.height}..."
    )
    print("Fazendo leitura dos dados em texto...")

    max_x = max_y = max_z = -sys.maxsize
    min_x = min_y = min_z = sys.maxsize

    with open(args.input, "r") as file:
        for line in file:
            split = line.split(sep=",")
            v3 = Vector3()
            v3.x = float(split[0])
            v3.y = float(split[1])
            v3.z = float(split[2])

            if v3.x > max_x:
                max_x = v3.x
            if v3.x < min_x:
                min_x = v3.x
            if v3.y > max_y:
                max_y = v3.y
            if v3.y < min_y:
                min_y = v3.y
            if v3.z > max_z:
                max_z = v3.z
            if v3.z < min_z:
                min_z = v3.z

            if v3.z < args.min_heightvalue_pointcloud:
                continue
            if v3.z > args.max_heightvalue_pointcloud:
                continue

            if args.export_color:
                try:
                    v3.r = int(split[3])
                    v3.g = int(split[4])
                    v3.b = int(split[5])
                except:
                    args.export_color = False

            points.append(v3)

    d_x = max_x - min_x
    d_y = max_y - min_y
    d_z = max_z - min_z

    print(
        f"Um total de {len(points)} pontos foram lidos e adicionados à memória para processamento..."
    )
    print(
        f"O tamanho/variação/delta das dimensões em x, y e z são respectivamente: {d_x},{d_y},{d_z}..."
    )
    print(
        f"    ::::[Min x: {min_x}, Max x: {max_x}, Min y: {min_y}, Max y: {max_y}, Min z: {min_z}, Max z: {max_z}]"
    )

    img = {}
    for v3 in points:
        x = y = z = 0
        if args.normalize_x_y:
            aspect_ratio = d_x / d_y
            if d_x > d_y:
                x = round(((v3.x - min_x) / d_x) * args.width)
                y = round(((v3.y - min_y) / d_y) * args.height / aspect_ratio)
            else:
                x = round(((v3.x - min_x) / d_x) * args.width * aspect_ratio)
                y = round(((v3.y - min_y) / d_y) * args.height)
        else:
            x = round(v3.x - min_x)
            y = round(v3.y - min_y)
        z = args.min_heightvalue_image + round(
            ((v3.z - min_z) / d_z)
            * abs(args.min_heightvalue_image - args.max_heightvalue_image)
        )

        if x not in img:
            img[x] = {}
        if y not in img[x]:
            img[x][y] = AvgPixel()
        img[x][y].counter += 1
        img[x][y].value += z
        img[x][y].r = v3.r
        img[x][y].g = v3.g
        img[x][y].b = v3.b

    print(
        "Finalizada a conversão das coordenadas de point cloud para as coordenadas de imagem..."
    )

    gray_img = np.zeros((args.height, args.width), dtype=np.uint16)
    rgb_img = np.zeros((args.height, args.width, 3), dtype=np.uint8)

    for i in range(args.height):
        for j in range(args.width):
            if j not in img or i not in img[j]:
                continue
            gray_img[
                args.invert_y * (args.height - i - 1) if args.invert_y else i, j
            ] = img[j][i].getAvg()
            rgb_img[
                args.invert_y * (args.height - i - 1) if args.invert_y else i, j
            ] = [
                img[j][i].r * 255 // 65535,
                img[j][i].g * 255 // 65535,
                img[j][i].b * 255 // 65535,
            ]

    Image.fromarray(gray_img).save(f"{folder}/01.cloudpoint.png")

    if args.export_color:
        print("Exportando a versão colorida...")
        Image.fromarray(rgb_img).save(f"{folder}/01.2.cloudpoint-colorido.png")

    print("Aplicando o algoritmo diamante em cinza e exportando...")

    diamond_algorithm(
        deep_copy(gray_img),
        gray_img,
        rgb_img,
        deep_copy(rgb_img),
        args.dilation_times,
        args.tolerance_number,
        r,
    )

    Image.fromarray(gray_img).save(f"{folder}/02.cinza-expandido.png")

    if args.export_color:
        print("Exportando a versão colorida expandida...")
        Image.fromarray(rgb_img).save(f"{folder}/02.2.colorido-expandido.png")

    print("Descendo a tolerância do algoritmo e exportando a versão final...")

    args.tolerance_number = 1
    diamond_algorithm(
        deep_copy(gray_img),
        gray_img,
        rgb_img,
        deep_copy(rgb_img),
        args.dilation_times,
        args.tolerance_number,
        r,
    )
    Image.fromarray(gray_img).save(f"{folder}/03.cinza-expandido-aindamais.png")

    if args.export_color:
        print("Exportando a versão colorida expandida...")
        Image.fromarray(rgb_img).save(f"{folder}/03.2.colorido-expandido-aindamais.png")

    box_blur(deep_copy(gray_img), gray_img, args.box_blur_size)
    Image.fromarray(gray_img).save(
        f"{folder}/04.cinza-expandido-aindamais-5kernelblur.png"
    )

    print("Finalizado com sucesso.")


if __name__ == "__main__":
    main()
