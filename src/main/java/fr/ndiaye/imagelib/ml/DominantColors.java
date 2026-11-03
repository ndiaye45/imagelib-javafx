package fr.ndiaye.imagelib.ml;

import fr.ndiaye.imagelib.model.Raster;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Couleurs dominantes d'une image par <b>k-means</b> (apprentissage non supervisé) :
 * on regroupe les pixels en k paquets de couleurs proches ; le centre de chaque paquet est une couleur dominante.
 * <ol>
 *   <li>initialisation k-means++ : les centres de départ sont choisis loin les uns des autres ;</li>
 *   <li>répéter : rattacher chaque pixel au centre le plus proche, puis recalculer chaque centre comme la moyenne de son paquet ;</li>
 *   <li>s'arrêter quand plus aucun pixel ne change de paquet.</li>
 * </ol>
 * La graine est fixe : le résultat est reproductible.
 */
public final class DominantColors {

    /** Une couleur (0xRRGGBB) et la part des pixels qu'elle représente (entre 0 et 1). */
    public record Swatch(int rgb, double share) {
    }

    private static final int SAMPLES = 64;
    private static final int MAX_ITERATIONS = 30;

    private DominantColors() {
    }

    public static List<Swatch> of(Raster image, int k) {
        if (k < 1) {
            throw new IllegalArgumentException("k doit être au moins 1");
        }
        int[][] pixels = sample(image);
        int distinct = distinctColours(pixels);
        k = Math.min(k, distinct);

        double[][] centres = initialCentres(pixels, k, new Random(7));
        int[] owner = new int[pixels.length];
        Arrays.fill(owner, -1);
        for (int iteration = 0; iteration < MAX_ITERATIONS; iteration++) {
            boolean changed = false;
            for (int i = 0; i < pixels.length; i++) {
                int nearest = nearest(pixels[i], centres);
                if (nearest != owner[i]) {
                    owner[i] = nearest;
                    changed = true;
                }
            }
            if (!changed) {
                break;
            }
            double[][] sums = new double[k][3];
            int[] sizes = new int[k];
            for (int i = 0; i < pixels.length; i++) {
                for (int c = 0; c < 3; c++) {
                    sums[owner[i]][c] += pixels[i][c];
                }
                sizes[owner[i]]++;
            }
            for (int j = 0; j < k; j++) {
                if (sizes[j] > 0) {
                    for (int c = 0; c < 3; c++) {
                        centres[j][c] = sums[j][c] / sizes[j];
                    }
                }
            }
        }

        int[] sizes = new int[k];
        for (int o : owner) {
            sizes[o]++;
        }
        List<Swatch> result = new ArrayList<>();
        for (int j = 0; j < k; j++) {
            if (sizes[j] > 0) {
                int rgb = (int) Math.round(centres[j][0]) << 16 | (int) Math.round(centres[j][1]) << 8 | (int) Math.round(centres[j][2]);
                result.add(new Swatch(rgb, sizes[j] / (double) pixels.length));
            }
        }
        result.sort(Comparator.comparingDouble(Swatch::share).reversed());
        return result;
    }

    private static int[][] sample(Raster image) {
        int step = Math.max(1, (int) Math.ceil(Math.max(image.width(), image.height()) / (double) SAMPLES));
        List<int[]> list = new ArrayList<>();
        for (int y = 0; y < image.height(); y += step) {
            for (int x = 0; x < image.width(); x += step) {
                int p = image.get(x, y);
                list.add(new int[]{p >> 16 & 0xFF, p >> 8 & 0xFF, p & 0xFF});
            }
        }
        return list.toArray(new int[0][]);
    }

    private static int distinctColours(int[][] pixels) {
        Set<Integer> seen = new HashSet<>();
        for (int[] p : pixels) {
            seen.add(p[0] << 16 | p[1] << 8 | p[2]);
        }
        return seen.size();
    }

    /** k-means++ : chaque nouveau centre est tiré avec une probabilité proportionnelle au carré de sa distance au centre le plus proche. */
    private static double[][] initialCentres(int[][] pixels, int k, Random random) {
        double[][] centres = new double[k][];
        centres[0] = toDouble(pixels[random.nextInt(pixels.length)]);
        double[] distance = new double[pixels.length];
        for (int j = 1; j < k; j++) {
            double total = 0;
            for (int i = 0; i < pixels.length; i++) {
                double best = Double.MAX_VALUE;
                for (int c = 0; c < j; c++) {
                    best = Math.min(best, squared(pixels[i], centres[c]));
                }
                distance[i] = best;
                total += best;
            }
            double pick = random.nextDouble() * total;
            int chosen = pixels.length - 1;
            for (int i = 0; i < pixels.length; i++) {
                pick -= distance[i];
                if (pick <= 0 && distance[i] > 0) {
                    chosen = i;
                    break;
                }
            }
            centres[j] = toDouble(pixels[chosen]);
        }
        return centres;
    }

    private static int nearest(int[] pixel, double[][] centres) {
        int best = 0;
        double bestDistance = Double.MAX_VALUE;
        for (int j = 0; j < centres.length; j++) {
            double d = squared(pixel, centres[j]);
            if (d < bestDistance) {
                bestDistance = d;
                best = j;
            }
        }
        return best;
    }

    private static double squared(int[] pixel, double[] centre) {
        double dr = pixel[0] - centre[0], dg = pixel[1] - centre[1], db = pixel[2] - centre[2];
        return dr * dr + dg * dg + db * db;
    }

    private static double[] toDouble(int[] pixel) {
        return new double[]{pixel[0], pixel[1], pixel[2]};
    }
}
