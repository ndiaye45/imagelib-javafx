package fr.ndiaye.imagelib.ml;

import fr.ndiaye.imagelib.model.Raster;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ImageFeaturesTest {

    private static Raster halves(int top, int bottom, int w, int h) {
        int[] px = new int[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                px[y * w + x] = 0xFF000000 | (y < h / 2 ? top : bottom);
            }
        }
        return new Raster(w, h, px);
    }

    @Test
    void vectorHasTheDocumentedSize() {
        assertEquals(ImageFeatures.DIMENSIONS, ImageFeatures.extract(halves(0xFF0000, 0x0000FF, 20, 20)).length);
    }

    @Test
    void colourHistogramSumsToOne() {
        double[] f = ImageFeatures.extract(halves(0xFF0000, 0x00FF00, 50, 40));
        double sum = 0;
        for (int i = 0; i < 64; i++) {
            sum += f[i];
        }
        assertEquals(1.0, sum, 1e-9);
    }

    @Test
    void pureRedLandsInTheRedBinWithTheRightLuminance() {
        double[] f = ImageFeatures.extract(halves(0xFF0000, 0xFF0000, 30, 30));
        assertEquals(1.0, f[3 * 16], 1e-9);                 // case (R=3, G=0, B=0)
        assertEquals(0.299, f[64], 1e-3);                   // luminosité du rouge pur
        assertEquals(1.0, f[66], 1e-9);                     // saturation maximale
        assertEquals(0.0, f[65], 1e-9);                     // aucun contraste
        assertEquals(0.0, f[67], 1e-9);                     // aucun contour
    }

    @Test
    void brightSkyAboveDarkGroundIsVisibleInTheThirds() {
        double[] f = ImageFeatures.extract(halves(0xFFFFFF, 0x000000, 60, 60));
        assertEquals(1.0, f[68], 1e-9);
        assertEquals(0.0, f[69], 1e-9);
        assertTrue(f[67] > 0, "la frontière ciel/sol est un contour");
    }

    @Test
    void sameContentAtDifferentSizesGivesSimilarFeatures() {
        double[] small = ImageFeatures.extract(halves(0x336699, 0xCC9933, 100, 80));
        double[] large = ImageFeatures.extract(halves(0x336699, 0xCC9933, 640, 480));
        for (int i = 0; i < small.length; i++) {
            assertEquals(small[i], large[i], 0.06, "caractéristique " + i);
        }
    }

    @Test
    void onePixelImageDoesNotCrash() {
        double[] f = ImageFeatures.extract(new Raster(1, 1, new int[]{0xFF123456}));
        for (double v : f) {
            assertTrue(Double.isFinite(v));
        }
    }
}
