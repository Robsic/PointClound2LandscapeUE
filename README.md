# Tolerance Diamond-Square (TDS) and Confidence-Adaptive Tolerance Diamond-Square (CATDS)

[![Donate](https://img.shields.io/badge/Donate-Stripe-635BFF?logo=stripe&logoColor=white)](https://donate.stripe.com/7sY4gz51905J1nRd8egQE00)

**English** | [Português](#português)

Interpolation algorithms that fill the empty cells of a sparse heightmap, in Java and in Python, with the first version of the code, and the maps used in the experiments of the article:

> É. O. Rodrigues, T. M. Porcino, J. V. Ferreira, T. S. Martins, B. M. G. Silva, F. F. Rodor, W. G. Almeida, G. Bernardes. **Rapid reconstruction and real-time rendering of realistic terrains from aerial data.** *Array*, Elsevier, 2026. [Article](https://authors.elsevier.com/sd/article/S2590-0056(26)00680-6)

If this work is useful to you, please cite the article. If you want to support it, you can make a [donation](https://donate.stripe.com/7sY4gz51905J1nRd8egQE00).

```bibtex
@article{rodrigues2026terrains,
  author  = {Rodrigues, {\'E}rick Oliveira and Porcino, Thiago Malheiros and Ferreira, Janito Vaqueiro and
             Martins, Thiago Santos and Silva, Bruno Messias Gon{\c{c}}alves and Rodor, Fadul Ferreira and
             Almeida, Willian Gon{\c{c}}alves and Bernardes, Giovani},
  title   = {Rapid reconstruction and real-time rendering of realistic terrains from aerial data},
  journal = {Array},
  year    = {2026},
  url     = {https://authors.elsevier.com/sd/article/S2590-0056(26)00680-6}
}
```

## What the algorithms do

A heightmap arrives with the value **0** wherever no sample fell (a rasterized point cloud, a decimated DEM, a map with voids). The algorithms write a height into those cells using the cells that hold a value, over the mask of the diamond-square algorithm: the four orthogonal positions at distance *d* (the *diamond*) and the four diagonal positions at distance *d* (the *square*).

| Algorithm | Idea | Fills every cell? |
|---|---|---|
| **Local TDS** | An empty cell is filled when enough of its 8 immediate neighbors hold a value; it copies the neighbor whose height is closest to their mean. Sweeps repeat until nothing changes. | No: border cells and cells that never gather enough neighbors stay at 0 |
| **Global TDS** | Same rule, but the radius *d* of the mask grows until enough values are found, so isolated cells are reached by distant samples. | Almost always, but not guaranteed |
| **CATDS** | Weighted mean instead of a copy, a confidence map that discounts estimated cells, a tolerance that is relaxed only when nothing else can be filled, and a final search that closes whatever is left. | Yes, whenever at least one cell is measured |

## Repository layout

```
java/            the complete Java version (tds.LocalTDS, tds.GlobalTDS, tds.CATDS) and command line (tds.Main)
python/          the complete Python version (package tds), same results pixel by pixel
first/           the first version, in Java, of the local and global TDS, unchanged, with a small runner
examples/        small inputs, the commands, the expected outputs and a script that checks them
data/            the maps used in the experiments of the article
```

The three produce **the same output pixel by pixel**. The versions in `java/` and `python/` are the first version made more complete: the same local and global TDS, together with CATDS, more parameters and a command line. In Java the TDS works on arrays instead of reading the raster pixel by pixel (2 to 3.5 times faster on a 1025 × 1025 map), and the Python version reproduces the random generator of `java.util.Random` and the float arithmetic of the Java code. `examples/run_examples.py` confirms it on every example.

## Quick start

### Java (Java 11 or later)

```
java/build.sh                       (Windows: java\build.cmd)
java -cp java/build tds.Main catds  examples/inputs/random_90.png out.png
java -cp java/build tds.Main local  examples/inputs/random_90.png out.png --tolerance 2 --random 1
java -cp java/build tds.Main global examples/inputs/random_90.png out.png --tolerance 2 --random 1
```

From Java code:

```java
HeightMap in = HeightMap.read("input.png");      // 8 or 16-bit grayscale PNG, 0 = empty
CATDS c = new CATDS();
c.confidenceDecay = 0.30;                        // every parameter is a public field
HeightMap out = c.interpolate(in);
out.write("output.png");
```

### Python (3.8 or later)

```
pip install -r python/requirements.txt
cd python
python -m tds catds  ../examples/inputs/random_90.png out.png
python -m tds local  ../examples/inputs/random_90.png out.png --tolerance 2 --random 1
python -m tds global ../examples/inputs/random_90.png out.png --tolerance 2 --random 1
```

From Python code (with `python/` on the path, or after `pip install ./python`):

```python
import tds
z, max_value = tds.read("input.png")             # int array, 0 = empty
out, passes = tds.catds(z, rho=0.30, d_max=16, max_value=max_value)
tds.write("output.png", out, max_value)
out, sweeps = tds.local_tds(z, tolerance=2, random_ceil=1)
out, sweeps = tds.global_tds(z, tolerance=2, random_ceil=1)
```

`numba` is optional. With it the Python version runs at compiled speed (the first call compiles and caches the functions); without it the same code runs as plain Python, which is fine for images of a few hundred pixels and slow for large maps.

### First version

```
first/build.sh                   (Windows: first\build.cmd)
java -cp first/java/build RunFirst local  input.png output.png 2 1
java -cp first/java/build RunFirst global input.png output.png 2 1
```

The classes `prop_LocalToleranceDiamondSquareInterpolation` and `prop_GlobalDiamondSquareInterpolation` in `first/java/src/interpolacoes` are kept as they were written. The two numbers at the end are the tolerance and the ceiling of the random increment.

## Input and output

* Grayscale PNG, 8 or 16 bit (a color image is read from its first band). The output has the bit depth of the input.
* **0 means "no sample"**. A real height of 0 is therefore indistinguishable from a gap; if your data has true zeros, shift it by one before filling.
* Measured cells are never changed by the local and global TDS. CATDS writes them back unchanged too, since a measured cell has confidence 1 and is excluded from every step that modifies values (as long as `cmin` ≤ 1).

## Parameters

### Local and global TDS

| Java (`tds.Main`) | Python | Default | Meaning |
|---|---|---|---|
| `--tolerance` | `tolerance` | 2 | Minimum number of neighbors that must hold a value, among the 4 positions of the diamond, or else among the 4 of the square. 1 fills almost everything at once; 2 is the usual choice; 3 and 4 leave most cells of sparse maps empty, because few cells ever have 3 or 4 filled neighbors at the same radius. |
| `--random` | `random_ceil` | 1 | A pseudo-random increment added to the threshold at every test: `round(u × random)`, with *u* uniform in [0, 1). With 1 the threshold is the tolerance or the tolerance plus one, with equal probability, which breaks the regular pattern of the fill. 0 disables it. |
| `--seed` | `seed` | 123 | Seed of the generator (`java.util.Random`). The same seed gives the same output in every version. |
| `--max-sweeps` | `max_sweeps` | 0 | 0 sweeps until a sweep changes nothing. A positive value stops earlier, which leaves cells far from the data empty (useful when a later step fills them, as in the deployed map of the mine: 20 sweeps with tolerance 2, then 20 with tolerance 1). |
| `--radius-fraction` | `radius_fraction` | 0.35 | Global only: the radius grows from 1 to `int(max(W, H) × fraction) − 1`. Smaller values are faster but may leave isolated cells empty. |

Behavior to keep in mind, inherited from the first version:

* The value written is always one of the **diamond** neighbors at the radius where the test passed, the one closest to the mean of the neighbors inspected. When the square rule passes and the diamond holds no value, the cell receives 0, that is, it stays empty for that sweep.
* The local formulation does not visit the one-pixel border of the image.
* Values are written in place, in raster order (rows top to bottom, columns left to right), so a value written is used by the cells visited after it in the same sweep.

### CATDS

| Java (`tds.Main`) | Python | Default | Meaning |
|---|---|---|---|
| `--tolerance` | `tolerance` | 2 | τ₀, initial tolerance: the **sum of confidences** of the neighbors needed to fill a cell. With measured neighbors only (confidence 1) it is a count. It is lowered by one each time the whole ring ladder is swept without emptying the list of pending cells, down to 1. |
| `--rho` | `rho` | 0.30 | ρ, confidence decay in (0, 1]. An estimated cell receives ρ × the mean confidence of the neighbors used, so a chain of estimates loses weight quickly and errors do not accumulate. 1 means no decay (estimated cells count as measured ones, which degrades the result); values from 0.15 to 0.5 behave similarly on scattered samples. |
| `--beta` | `beta` | 2 | β, distance exponent: a neighbor at distance *r* weighs `C(q) × r^−β`. 1 or 2. |
| `--dmax` | `d_max` | 16 | Largest ring of the mask, in pixels. The rings are 1, 2, 3, 4, 6, 8, 12, 16, then 24, 32, 48, 64, ... up to `dmax`. Cells farther than `dmax` from any sample are closed by the final search, which copies the nearest value. Raise it when the samples are farther apart than 16 pixels (a lattice of step 32, for example); the run time grows with it. |
| `--cmin` | `c_min` | 0.60 | Refinement threshold: every interior cell whose final confidence is below it becomes the average of its value and the weighted mean of its 8 neighbors. With ρ = 0.30 every estimated cell has confidence ≤ 0.30, so all of them are refined. |
| `--no-refine` | `refine=False` | refine on | Skips the refinement. |
| `--synchronous` | `synchronous=True` | off | Each pass reads the map as it was at the start of the pass (Jacobi). By default values are written in place and the direction of the pass alternates, which advances the fill front several cells per pass. |
| `--random-scale` | `random_scale` | 0 | 0 gives a deterministic result. A positive value adds, after the fill, a fractal displacement that restores the pixel-scale roughness measured on the samples (fitted variogram), weighted by the distance to the nearest sample. It acts only when adjacent measured cells exist and the reconstruction is smoother than the samples. |
| `--octave-cutoff` | `octave_cutoff` | 8 | Largest scale of the displacement, in pixels. |
| `--seed` | `seed` | 123 | Seed of the displacement. |
| | `max_value` | from the image | Largest height (65535 for 16 bit, 255 for 8 bit). The command line reads it from the image. |

The steps of CATDS are:

1. Distance to the nearest measured cell, by a two-pass chamfer transform (steps 1 and 1.414).
2. List of pending cells: the empty cells within `dmax`, ordered by the integer part of that distance (closest to the data first).
3. For τ = τ₀ down to 1, for every ring *dᵢ* ≤ `dmax`, passes (at most 64) over the pending list: a cell is filled when the sum of the confidences of the cells that hold a value at the 8 positions of every ring up to *dᵢ* reaches τ. Its height is the weighted mean of those cells, and its confidence is ρ × their mean confidence.
4. A breadth-first search from every filled cell closes what is left.
5. Refinement of the cells with confidence below `cmin`.
6. Optional fractal displacement (`random-scale` > 0).

## Examples

`examples/inputs` holds seven small inputs (128 × 128, or 129 × 129 for the lattices), each built from a map of `data/` by `examples/make_inputs.py`. The `_complete` image next to each input is the map before the cells were removed, used to measure the error. `examples/cases.txt` lists 27 runs, and `examples/expected/<case>/<run>.png` holds the expected output of each one.

| Input | Situation |
|---|---|
| `random_90` | scattered samples: 90% of the cells removed at random (Rolling Hills) |
| `gradient` | density that grows from the top of the image to the bottom (Rocky Land and Rivers) |
| `grid_16` | regular lattice of step 16, 0.4% of the cells (Ridge Through Terrain) |
| `grid_16_jitter` | the same lattice with each node moved by up to 4 cells |
| `large_void` | 30% of the cells kept and a 48 × 48 block without any sample (Rugged Terrain) |
| `mine` | a crop of the real survey of an open-pit mine, where the samples follow the bench crests |
| `mountain_8bit` | an 8-bit image (Grand Mountain reduced to 128 × 128), 70% removed |

To run them and compare with the expected outputs:

```
python3 examples/run_examples.py                  Python only
java/build.sh && first/build.sh
python3 examples/run_examples.py --impl all       Python, Java and the first version
```

Each run prints `OK` when its output is identical to the expected one. The first version is checked on the runs that use only the tolerance and the random increment, which are the parameters it exposes.

A few of the runs, with the command and the result recorded in `examples/expected/summary.csv` (empty cells left, and RMSE in height units over the removed cells; cells left empty count as height 0 in `rmse_missing` and are excluded from `rmse_filled`):

| Command | Empty in | Empty out | RMSE missing | RMSE filled |
|---|---|---|---|---|
| `local  random_90.png  --tolerance 2 --random 1` | 14 786 | 460 | 586.17 | 64.56 |
| `global random_90.png  --tolerance 2 --random 1` | 14 786 | 0 | 104.09 | 104.09 |
| `catds  random_90.png` | 14 786 | 0 | 24.95 | 24.95 |
| `local  grid_16.png    --tolerance 2 --random 1` | 16 560 | 16 560 | 27 870.14 | — |
| `global grid_16.png    --tolerance 2 --random 1` | 16 560 | 0 | 6479.83 | 6479.83 |
| `catds  grid_16.png` | 16 560 | 0 | 537.19 | 537.19 |
| `catds  grid_16.png --dmax 8` | 16 560 | 0 | 864.53 | 864.53 |
| `catds  large_void.png` | 12 250 | 0 | 55.47 | 55.47 |
| `local  mine.png --tolerance 2 --random 1 --max-sweeps 20` | 10 080 | 292 | — | — |
| `catds  mine.png --random-scale 1` | 10 080 | 0 | — | — |
| `catds  mountain_8bit.png` | 11 451 | 0 | 2.13 | 2.13 |

These examples illustrate the behavior of each algorithm and are not the experiments of the article. On a lattice, for instance, the local formulation fills nothing (no cell has two filled neighbors at distance 1), the global one fills everything with copies, and CATDS fills everything with weighted means; on the mine, 20 sweeps of the local formulation leave the cells farthest from the samples empty.

## Data

| Folder | Content |
|---|---|
| `data/terrains` | the six complete terrains, 16-bit PNG: Rolling Hills and Ridge Through Terrain (1025 × 1025), Rocky Land and Rivers and Dark Alien (2048 × 2048), Rugged Terrain and Grand Mountain (4096 × 4096) |
| `data/sparse/global_0-90` | each cell removed with probability 0.90 |
| `data/sparse/local_0-90` | removal probability 1 − 0.8 (t/N)^(7/4) along the raster order, from almost every cell at the top to 20% at the bottom |
| `data/sparse/grid_16`, `grid_32` | only the cells whose coordinates are multiples of 16 or 32 |
| `data/sparse/grid_16_jitter` | lattice of step 16 with every node moved by up to 4 cells in each axis |
| `data/sparse/extreme_99.5` | each cell kept with probability 0.005 |
| `data/sparse/large_voids` | 30% of the cells kept, then blocks of 128 × 128 cleared until about a third of the area is empty |
| `data/mine/survey_raster.png` | the rasterized point cloud of the mine, 4033 × 4033, 0.79 m per cell, 0 where no point fell |
| `data/mine/deployed_local_tds_boxblur5.png` | the heightmap used in the training simulator: local TDS (tolerance 2 for 20 sweeps, then tolerance 1 for 20 sweeps) followed by a 5-pixel box blur |

In Dark Alien and Grand Mountain part of the terrain has height exactly 0, so those regions count as missing and are reconstructed too. `data/make_sparse.py` regenerates the five additional experiments from the terrains with the seed that produced them.

## License and support

The code and the data are made available for research and education; please cite the article above when you use them. Questions and suggestions are welcome through the issues of this repository.

[![Donate](https://img.shields.io/badge/Donate-Stripe-635BFF?logo=stripe&logoColor=white)](https://donate.stripe.com/7sY4gz51905J1nRd8egQE00)

---

# Português

[English](#tolerance-diamond-square-tds-and-confidence-adaptive-tolerance-diamond-square-catds) | **Português**

Algoritmos de interpolação que preenchem as células vazias de um mapa de altura esparso, em Java e em Python, com a primeira versão do código, e os mapas usados nos experimentos do artigo:

> É. O. Rodrigues, T. M. Porcino, J. V. Ferreira, T. S. Martins, B. M. G. Silva, F. F. Rodor, W. G. Almeida, G. Bernardes. **Rapid reconstruction and real-time rendering of realistic terrains from aerial data.** *Array*, Elsevier, 2026. [Artigo](https://authors.elsevier.com/sd/article/S2590-0056(26)00680-6)

Se este trabalho for útil, por favor cite o artigo (BibTeX na seção em inglês). Se quiser apoiar, você pode fazer uma [doação](https://donate.stripe.com/7sY4gz51905J1nRd8egQE00).

## O que os algoritmos fazem

O mapa de altura chega com o valor **0** onde não caiu nenhuma amostra (uma nuvem de pontos rasterizada, um MDE decimado, um mapa com vazios). Os algoritmos escrevem uma altura nessas células a partir das células que têm valor, sobre a máscara do algoritmo diamond-square: as quatro posições ortogonais à distância *d* (o *diamante*) e as quatro diagonais à distância *d* (o *quadrado*).

| Algoritmo | Ideia | Preenche todas as células? |
|---|---|---|
| **TDS local** | Uma célula vazia é preenchida quando vizinhos suficientes, entre os 8 imediatos, têm valor; ela copia o vizinho cuja altura é a mais próxima da média deles. As varreduras se repetem até nada mudar. | Não: a borda e as células que nunca reúnem vizinhos suficientes ficam em 0 |
| **TDS global** | A mesma regra, mas o raio *d* da máscara cresce até encontrar valores suficientes, de modo que células isoladas são alcançadas por amostras distantes. | Quase sempre, sem garantia |
| **CATDS** | Média ponderada em vez de cópia, um mapa de confiança que desconta as células estimadas, uma tolerância relaxada só quando nada mais pode ser preenchido e uma busca final que fecha o que sobrar. | Sim, sempre que ao menos uma célula for medida |

## Organização do repositório

```
java/            a versão completa em Java (tds.LocalTDS, tds.GlobalTDS, tds.CATDS) e linha de comando (tds.Main)
python/          a versão completa em Python (pacote tds), com o mesmo resultado pixel a pixel
first/           a primeira versão, em Java, do TDS local e global, sem alterações, com um executor
examples/        entradas pequenas, os comandos, as saídas esperadas e um script que as confere
data/            os mapas usados nos experimentos do artigo
```

As três produzem **a mesma saída, pixel a pixel**. As versões de `java/` e `python/` são a primeira versão tornada mais completa: o mesmo TDS local e global, junto com o CATDS, mais parâmetros e linha de comando. Em Java o TDS trabalha sobre vetores em vez de ler o raster pixel a pixel (de 2 a 3,5 vezes mais rápido num mapa de 1025 × 1025), e a versão Python reproduz o gerador de `java.util.Random` e a aritmética de ponto flutuante do código Java. O script `examples/run_examples.py` confere isso em todos os exemplos.

## Uso rápido

### Java (Java 11 ou superior)

```
java/build.sh                       (Windows: java\build.cmd)
java -cp java/build tds.Main catds  examples/inputs/random_90.png saida.png
java -cp java/build tds.Main local  examples/inputs/random_90.png saida.png --tolerance 2 --random 1
java -cp java/build tds.Main global examples/inputs/random_90.png saida.png --tolerance 2 --random 1
```

No código Java:

```java
HeightMap in = HeightMap.read("entrada.png");    // PNG em tons de cinza, 8 ou 16 bits, 0 = vazio
CATDS c = new CATDS();
c.confidenceDecay = 0.30;                        // todos os parâmetros são campos públicos
HeightMap out = c.interpolate(in);
out.write("saida.png");
```

### Python (3.8 ou superior)

```
pip install -r python/requirements.txt
cd python
python -m tds catds  ../examples/inputs/random_90.png saida.png
python -m tds local  ../examples/inputs/random_90.png saida.png --tolerance 2 --random 1
python -m tds global ../examples/inputs/random_90.png saida.png --tolerance 2 --random 1
```

No código Python (com `python/` no caminho, ou após `pip install ./python`):

```python
import tds
z, max_value = tds.read("entrada.png")           # vetor de inteiros, 0 = vazio
out, passes = tds.catds(z, rho=0.30, d_max=16, max_value=max_value)
tds.write("saida.png", out, max_value)
out, sweeps = tds.local_tds(z, tolerance=2, random_ceil=1)
out, sweeps = tds.global_tds(z, tolerance=2, random_ceil=1)
```

O `numba` é opcional. Com ele a versão Python roda em velocidade de código compilado (a primeira chamada compila as funções e as guarda em cache); sem ele o mesmo código roda como Python puro, o que basta para imagens de algumas centenas de pixels e é lento para mapas grandes.

### Primeira versão

```
first/build.sh                   (Windows: first\build.cmd)
java -cp first/java/build RunFirst local  entrada.png saida.png 2 1
java -cp first/java/build RunFirst global entrada.png saida.png 2 1
```

As classes `prop_LocalToleranceDiamondSquareInterpolation` e `prop_GlobalDiamondSquareInterpolation`, em `first/java/src/interpolacoes`, estão como foram escritas. Os dois números no fim são a tolerância e o teto do incremento aleatório.

## Entrada e saída

* PNG em tons de cinza, 8 ou 16 bits (de uma imagem colorida é lida a primeira banda). A saída tem a profundidade de bits da entrada.
* **0 significa "sem amostra"**. Uma altura real igual a 0 não se distingue de um vazio; se os seus dados têm zeros verdadeiros, some 1 antes de preencher.
* As células medidas nunca são alteradas pelo TDS local e global. O CATDS também as devolve inalteradas, pois uma célula medida tem confiança 1 e fica fora de todo passo que modifica valores (desde que `cmin` ≤ 1).

## Parâmetros

### TDS local e global

| Java (`tds.Main`) | Python | Padrão | Significado |
|---|---|---|---|
| `--tolerance` | `tolerance` | 2 | Número mínimo de vizinhos com valor entre as 4 posições do diamante, ou então entre as 4 do quadrado. Com 1 quase tudo é preenchido de uma vez; 2 é a escolha usual; 3 e 4 deixam vazia a maior parte das células de mapas esparsos, porque poucas células chegam a ter 3 ou 4 vizinhos preenchidos no mesmo raio. |
| `--random` | `random_ceil` | 1 | Incremento pseudoaleatório somado ao limiar a cada teste: `round(u × random)`, com *u* uniforme em [0, 1). Com 1 o limiar é a tolerância ou a tolerância mais um, com igual probabilidade, o que quebra o padrão regular do preenchimento. 0 desliga. |
| `--seed` | `seed` | 123 | Semente do gerador (`java.util.Random`). A mesma semente dá a mesma saída em todas as versões. |
| `--max-sweeps` | `max_sweeps` | 0 | 0 varre até uma varredura não mudar nada. Um valor positivo para antes, o que deixa vazias as células longe dos dados (útil quando outro passo as preenche depois, como no mapa entregue da mina: 20 varreduras com tolerância 2 e depois 20 com tolerância 1). |
| `--radius-fraction` | `radius_fraction` | 0.35 | Só no global: o raio cresce de 1 até `int(max(W, H) × fração) − 1`. Valores menores são mais rápidos, mas podem deixar células isoladas vazias. |

Comportamentos herdados da primeira versão:

* O valor escrito é sempre um dos vizinhos do **diamante** no raio em que o teste passou, o mais próximo da média dos vizinhos inspecionados. Quando a regra do quadrado passa e o diamante não tem valor, a célula recebe 0, ou seja, continua vazia naquela varredura.
* A formulação local não visita a borda de um pixel da imagem.
* Os valores são escritos no próprio mapa, na ordem de varredura (linhas de cima para baixo, colunas da esquerda para a direita), de modo que um valor escrito é usado pelas células visitadas depois dele na mesma varredura.

### CATDS

| Java (`tds.Main`) | Python | Padrão | Significado |
|---|---|---|---|
| `--tolerance` | `tolerance` | 2 | τ₀, tolerância inicial: a **soma das confianças** dos vizinhos necessária para preencher uma célula. Só com vizinhos medidos (confiança 1) é uma contagem. Diminui de um a cada vez que a escada de anéis inteira é varrida sem esvaziar a lista de pendentes, até 1. |
| `--rho` | `rho` | 0.30 | ρ, decaimento da confiança, em (0, 1]. Uma célula estimada recebe ρ × a confiança média dos vizinhos usados, de modo que uma cadeia de estimativas perde peso rapidamente e o erro não se acumula. 1 é sem decaimento (as estimadas valem tanto quanto as medidas, o que piora o resultado); valores de 0,15 a 0,5 se comportam de forma parecida em amostras espalhadas. |
| `--beta` | `beta` | 2 | β, expoente da distância: um vizinho à distância *r* pesa `C(q) × r^−β`. 1 ou 2. |
| `--dmax` | `d_max` | 16 | Maior anel da máscara, em pixels. Os anéis são 1, 2, 3, 4, 6, 8, 12, 16, depois 24, 32, 48, 64, ... até `dmax`. As células a mais de `dmax` de qualquer amostra são fechadas pela busca final, que copia o valor mais próximo. Aumente quando as amostras estiverem a mais de 16 pixels umas das outras (uma grade de passo 32, por exemplo); o tempo cresce junto. |
| `--cmin` | `c_min` | 0.60 | Limiar do refinamento: toda célula interior com confiança final abaixo dele passa a ser a média entre seu valor e a média ponderada dos 8 vizinhos. Com ρ = 0,30 toda célula estimada tem confiança ≤ 0,30, então todas são refinadas. |
| `--no-refine` | `refine=False` | refinamento ligado | Desliga o refinamento. |
| `--synchronous` | `synchronous=True` | desligado | Cada passada lê o mapa como estava no início da passada (Jacobi). Por padrão os valores são escritos no próprio mapa e o sentido da passada alterna, o que faz a frente de preenchimento avançar várias células por passada. |
| `--random-scale` | `random_scale` | 0 | 0 dá resultado determinístico. Um valor positivo soma, depois do preenchimento, um deslocamento fractal que devolve a rugosidade em escala de pixel medida nas amostras (variograma ajustado), ponderado pela distância à amostra mais próxima. Só age quando existem células medidas adjacentes e a reconstrução é mais lisa que as amostras. |
| `--octave-cutoff` | `octave_cutoff` | 8 | Maior escala do deslocamento, em pixels. |
| `--seed` | `seed` | 123 | Semente do deslocamento. |
| | `max_value` | da imagem | Maior altura (65535 em 16 bits, 255 em 8 bits). A linha de comando lê da imagem. |

Os passos do CATDS são:

1. Distância à célula medida mais próxima, por uma transformada chanfrada de duas passadas (passos 1 e 1,414).
2. Lista de pendentes: as células vazias a até `dmax`, ordenadas pela parte inteira dessa distância (as mais próximas dos dados primeiro).
3. Para τ = τ₀ até 1, para cada anel *dᵢ* ≤ `dmax`, passadas (no máximo 64) sobre a lista: uma célula é preenchida quando a soma das confianças das células com valor nas 8 posições de todos os anéis até *dᵢ* chega a τ. A altura é a média ponderada dessas células e a confiança é ρ × a confiança média delas.
4. Uma busca em largura a partir de toda célula preenchida fecha o que sobrar.
5. Refinamento das células com confiança abaixo de `cmin`.
6. Deslocamento fractal opcional (`random-scale` > 0).

## Exemplos

A pasta `examples/inputs` tem sete entradas pequenas (128 × 128, ou 129 × 129 nas grades), cada uma feita a partir de um mapa de `data/` pelo script `examples/make_inputs.py`. A imagem `_complete` ao lado de cada entrada é o mapa antes de as células serem removidas, usado para medir o erro. O arquivo `examples/cases.txt` lista 27 execuções, e `examples/expected/<caso>/<execução>.png` guarda a saída esperada de cada uma.

| Entrada | Situação |
|---|---|
| `random_90` | amostras espalhadas: 90% das células removidas ao acaso (Rolling Hills) |
| `gradient` | densidade que cresce do topo para a base da imagem (Rocky Land and Rivers) |
| `grid_16` | grade regular de passo 16, 0,4% das células (Ridge Through Terrain) |
| `grid_16_jitter` | a mesma grade com cada nó deslocado de até 4 células |
| `large_void` | 30% das células mantidas e um bloco de 48 × 48 sem nenhuma amostra (Rugged Terrain) |
| `mine` | um recorte do levantamento real de uma mina a céu aberto, em que as amostras seguem as cristas das bancadas |
| `mountain_8bit` | uma imagem de 8 bits (Grand Mountain reduzida a 128 × 128), com 70% removidos |

Para rodar e comparar com as saídas esperadas:

```
python3 examples/run_examples.py                  só Python
java/build.sh && first/build.sh
python3 examples/run_examples.py --impl all       Python, Java e a primeira versão
```

Cada execução imprime `OK` quando a saída é idêntica à esperada. A primeira versão é conferida nas execuções que usam só a tolerância e o incremento aleatório, que são os parâmetros que ele expõe.

Os resultados de todas as execuções estão em `examples/expected/summary.csv` (células vazias que restaram e RMSE em unidades de altura sobre as células removidas; as que ficaram vazias contam como altura 0 em `rmse_missing` e ficam fora de `rmse_filled`). A tabela da seção em inglês mostra alguns deles. Os exemplos ilustram o comportamento de cada algoritmo e não são os experimentos do artigo. Numa grade, por exemplo, a formulação local não preenche nada (nenhuma célula tem dois vizinhos preenchidos à distância 1), a global preenche tudo com cópias e o CATDS preenche tudo com médias ponderadas; na mina, 20 varreduras da formulação local deixam vazias as células mais distantes das amostras.

## Dados

| Pasta | Conteúdo |
|---|---|
| `data/terrains` | os seis terrenos completos, PNG de 16 bits: Rolling Hills e Ridge Through Terrain (1025 × 1025), Rocky Land and Rivers e Dark Alien (2048 × 2048), Rugged Terrain e Grand Mountain (4096 × 4096) |
| `data/sparse/global_0-90` | cada célula removida com probabilidade 0,90 |
| `data/sparse/local_0-90` | probabilidade de remoção 1 − 0,8 (t/N)^(7/4) ao longo da ordem de varredura, de quase todas as células no topo a 20% na base |
| `data/sparse/grid_16`, `grid_32` | só as células cujas coordenadas são múltiplas de 16 ou 32 |
| `data/sparse/grid_16_jitter` | grade de passo 16 com cada nó deslocado de até 4 células em cada eixo |
| `data/sparse/extreme_99.5` | cada célula mantida com probabilidade 0,005 |
| `data/sparse/large_voids` | 30% das células mantidas e depois blocos de 128 × 128 apagados até cerca de um terço da área ficar vazio |
| `data/mine/survey_raster.png` | a nuvem de pontos da mina rasterizada, 4033 × 4033, 0,79 m por célula, 0 onde não caiu ponto |
| `data/mine/deployed_local_tds_boxblur5.png` | o mapa de altura usado no simulador de treinamento: TDS local (tolerância 2 por 20 varreduras e depois tolerância 1 por 20 varreduras) seguido de um filtro de média de 5 pixels |

Em Dark Alien e Grand Mountain parte do terreno tem altura exatamente 0, então essas regiões contam como vazias e também são reconstruídas. O script `data/make_sparse.py` regenera os cinco experimentos adicionais a partir dos terrenos, com a semente que os produziu.

## Licença e apoio

O código e os dados estão disponíveis para pesquisa e ensino; por favor cite o artigo acima ao usá-los. Dúvidas e sugestões são bem-vindas pelas issues do repositório.

[![Doar](https://img.shields.io/badge/Doar-Stripe-635BFF?logo=stripe&logoColor=white)](https://donate.stripe.com/7sY4gz51905J1nRd8egQE00)
