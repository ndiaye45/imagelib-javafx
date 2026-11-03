package fr.ndiaye.imagelib.ml;

import fr.ndiaye.imagelib.ml.DominantColors.Swatch;
import fr.ndiaye.imagelib.model.Raster;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DominantColorsTest {

    private static Raster bands(int[] colours, int[] rows, int w) {
        int h = 0;
        for (int r : rows) {
            h += r;
        }
        int[] px = new int[w * h];
        int y = 0;
        for (int band = 0; band < colours.length; band++) {
            for (int i = 0; i < rows[band]; i++, y++) {
                for (int x = 0; x < w; x++) {
                    px[y * w + x] = 0xFF000000 | colours[band];
                }
            }
        }
        return new Raster(w, h, px);
    }

    @Test
    void findsTwoColoursAndTheirShare() {
        List<Swatch> result = DominantColors.of(bands(new int[]{0xFF0000, 0x0000FF}, new int[]{30, 30}, 40), 2);
        assertEquals(2, result.size());
        assertEquals(0.5, result.get(0).share(), 0.02);
        assertEquals(0.5, result.get(1).share(), 0.02);
        assertEquals(java.util.Set.of(0xFF0000, 0x0000FF), java.util.Set.of(result.get(0).rgb(), result.get(1).rgb()));
    }

    @Test
    void swatchesAreSortedByDecreasingShareAndSumToOne() {
        List<Swatch> result = DominantColors.of(bands(new int[]{0x00AA00, 0xFFEE00}, new int[]{45, 15}, 40), 2);
        assertEquals(0x00AA00, result.get(0).rgb());
        assertEquals(0.75, result.get(0).share(), 0.02);
        assertEquals(1.0, result.stream().mapToDouble(Swatch::share).sum(), 1e-9);
    }

    @Test
    void neverReturnsMoreColoursThanTheImageHas() {
        List<Swatch> result = DominantColors.of(bands(new int[]{0x123456}, new int[]{20}, 20), 5);
        assertEquals(1, result.size());
        assertEquals(0x123456, result.get(0).rgb());
        assertEquals(1.0, result.get(0).share(), 1e-9);
    }

    @Test
    void groupsNoisyShadesTogether() {
        // Deux familles de couleurs légèrement bruitées : 2 centres, proches des couleurs de base.
        java.util.Random random = new java.util.Random(3);
        Raster image = Scenes.scene(5, random);        // ciel violet / sol orange
        List<Swatch> result = DominantColors.of(image, 2);
        assertEquals(2, result.size());
        for (Swatch s : result) {
            boolean nearViolet = Math.abs((s.rgb() >> 16 & 0xFF) - 0x50) < 12 && Math.abs((s.rgb() & 0xFF) - 0x50) < 12;
            boolean nearOrange = Math.abs((s.rgb() >> 16 & 0xFF) - 0xFF) < 12 && Math.abs((s.rgb() >> 8 & 0xFF) - 0x70) < 12;
            assertTrue(nearViolet || nearOrange, "couleur inattendue : " + Integer.toHexString(s.rgb()));
        }
    }

    @Test
    void resultIsReproducible() {
        Raster image = Scenes.scene(1, new java.util.Random(9));
        assertEquals(DominantColors.of(image, 4), DominantColors.of(image, 4));
    }

    @Test
    void invalidKIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> DominantColors.of(Raster.blank(4, 4), 0));
    }
}
