package fr.ndiaye.imagelib.ml;

import fr.ndiaye.imagelib.model.Raster;

/**
 * Transforme une image en un vecteur de 70 nombres que le modèle sait comparer.
 * <pre>
 *   0..63  histogramme de couleurs 4 × 4 × 4 (proportion de pixels dans chaque « case » du cube RVB)
 *   64     luminosité moyenne            65  contraste (écart-type de la luminosité)
 *   66     saturation moyenne            67  densité de contours (différences entre pixels voisins)
 *   68     luminosité du tiers haut      69  luminosité du tiers bas   (ciel clair / sol sombre…)
 * </pre>
 * L'image est d'abord échantillonnée (au plus 64 × 64 points) : le coût ne dépend presque pas de sa taille.
 */
public final class ImageFeatures {

    public static final int DIMENSIONS = 70;
    private static final int SAMPLES = 64;
    private static final int BINS = 4;

    private ImageFeatures() {
    }

    public static double[] extract(Raster image) {
        int step = Math.max(1, (int) Math.ceil(Math.max(image.width(), image.height()) / (double) SAMPLES));
        int gw = (image.width() + step - 1) / step;
        int gh = (image.height() + step - 1) / step;
        double[] histogram = new double[BINS * BINS * BINS];
        double[] luminance = new double[gw * gh];
        double saturationSum = 0;

        for (int gy = 0; gy < gh; gy++) {
            for (int gx = 0; gx < gw; gx++) {
                int pixel = image.get(gx * step, gy * step);
                int r = pixel >> 16 & 0xFF, g = pixel >> 8 & 0xFF, b = pixel & 0xFF;
                histogram[(r >> 6) * 16 + (g >> 6) * 4 + (b >> 6)]++;
                luminance[gy * gw + gx] = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0;
                int max = Math.max(r, Math.max(g, b));
                int min = Math.min(r, Math.min(g, b));
                saturationSum += max == 0 ? 0 : (max - min) / (double) max;
            }
        }

        int cells = gw * gh;
        double[] features = new double[DIMENSIONS];
        for (int i = 0; i < histogram.length; i++) {
            features[i] = histogram[i] / cells;
        }
        double mean = mean(luminance, 0, cells);
        double variance = 0;
        for (double l : luminance) {
            variance += (l - mean) * (l - mean);
        }
        features[64] = mean;
        features[65] = Math.sqrt(variance / cells);
        features[66] = saturationSum / cells;
        features[67] = edgeDensity(luminance, gw, gh);
        int third = Math.max(1, gh / 3);
        features[68] = mean(luminance, 0, third * gw);
        features[69] = mean(luminance, (gh - third) * gw, gh * gw);
        return features;
    }

    private static double mean(double[] values, int from, int to) {
        double sum = 0;
        for (int i = from; i < to; i++) {
            sum += values[i];
        }
        return sum / (to - from);
    }

    private static double edgeDensity(double[] lum, int gw, int gh) {
        double sum = 0;
        int count = 0;
        for (int y = 0; y < gh; y++) {
            for (int x = 0; x < gw; x++) {
                if (x + 1 < gw) {
                    sum += Math.abs(lum[y * gw + x + 1] - lum[y * gw + x]);
                    count++;
                }
                if (y + 1 < gh) {
                    sum += Math.abs(lum[(y + 1) * gw + x] - lum[y * gw + x]);
                    count++;
                }
            }
        }
        return count == 0 ? 0 : sum / count;
    }
}
