package fr.ndiaye.imagelib.transform;

import fr.ndiaye.imagelib.model.Raster;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TransformationsTest {

    private static final int OPAQUE = 0xFF000000;

    /** Image 3 x 2 : chaque pixel a une valeur distincte, pratique pour suivre les déplacements. */
    private static Raster sample() {
        return new Raster(3, 2, new int[]{
                0xFF000001, 0xFF000002, 0xFF000003,
                0xFF000004, 0xFF000005, 0xFF000006});
    }

    @Test
    void rotation90ClockwiseMovesPixels() {
        Raster result = new Rotation(90).apply(sample());

        assertEquals(2, result.width());
        assertEquals(3, result.height());
        assertEquals(new Raster(2, 3, new int[]{
                0xFF000004, 0xFF000001,
                0xFF000005, 0xFF000002,
                0xFF000006, 0xFF000003}), result);
    }

    @Test
    void rotation270IsCounterClockwise() {
        Raster result = new Rotation(270).apply(sample());

        assertEquals(new Raster(2, 3, new int[]{
                0xFF000003, 0xFF000006,
                0xFF000002, 0xFF000005,
                0xFF000001, 0xFF000004}), result);
    }

    @Test
    void rotation180ReversesPixels() {
        Raster result = new Rotation(180).apply(sample());

        assertEquals(new Raster(3, 2, new int[]{
                0xFF000006, 0xFF000005, 0xFF000004,
                0xFF000003, 0xFF000002, 0xFF000001}), result);
    }

    @Test
    void fourQuarterTurnsGiveTheOriginalImage() {
        Raster result = sample();
        for (int i = 0; i < 4; i++) {
            result = new Rotation(90).apply(result);
        }
        assertEquals(sample(), result);
    }

    @Test
    void rotationNormalizesAngles() {
        assertEquals(270, new Rotation(-90).angle());
        assertEquals(90, new Rotation(450).angle());
        assertEquals(0, new Rotation(360).angle());
    }

    @Test
    void rotationRejectsAnglesThatAreNotMultiplesOf90() {
        assertThrows(IllegalArgumentException.class, () -> new Rotation(45));
    }

    @Test
    void mirrorSwapsLeftAndRight() {
        assertEquals(new Raster(3, 2, new int[]{
                0xFF000003, 0xFF000002, 0xFF000001,
                0xFF000006, 0xFF000005, 0xFF000004}), new Mirror().apply(sample()));
    }

    @Test
    void mirrorTwiceGivesTheOriginalImage() {
        assertEquals(sample(), new Mirror().apply(new Mirror().apply(sample())));
    }

    @Test
    void transformationsDoNotModifyTheirSource() {
        Raster source = sample();
        Raster result = new Invert().apply(source);

        assertNotSame(source, result);
        assertEquals(sample(), source);
    }

    @Test
    void grayscaleKeepsAlphaAndEqualizesChannels() {
        Raster red = new Raster(1, 1, new int[]{0x80FF0000});

        int pixel = new Grayscale().apply(red).get(0, 0);

        assertEquals(0x80, pixel >>> 24);
        assertEquals(76, (pixel >> 16) & 0xFF);   // 0,299 x 255
        assertEquals(76, (pixel >> 8) & 0xFF);
        assertEquals(76, pixel & 0xFF);
    }

    @Test
    void sepiaClampsChannelsToTheValidRange() {
        Raster white = new Raster(1, 1, new int[]{OPAQUE | 0xFFFFFF});

        int pixel = new Sepia().apply(white).get(0, 0);

        assertEquals(255, (pixel >> 16) & 0xFF);
        assertEquals(255, (pixel >> 8) & 0xFF);
        assertEquals(239, pixel & 0xFF);          // (0,272 + 0,534 + 0,131) x 255 = 238,9
    }

    @Test
    void invertTwiceGivesTheOriginalImage() {
        Raster source = new Raster(2, 1, new int[]{0xFF336699, 0x80FFEEDD});

        assertEquals(source, new Invert().apply(new Invert().apply(source)));
    }

    @Test
    void invertFlipsColorsButNotAlpha() {
        Raster source = new Raster(1, 1, new int[]{0x80102030});

        assertEquals(0x80EFDFCF, new Invert().apply(source).get(0, 0));
    }

    @Test
    void brightnessScalesChannelsAndClamps() {
        Raster source = new Raster(1, 1, new int[]{OPAQUE | 0x646464}); // 100, 100, 100

        assertEquals(OPAQUE | 0x787878, new Brightness(20).apply(source).get(0, 0));   // 120
        assertEquals(OPAQUE | 0x505050, new Brightness(-20).apply(source).get(0, 0));  // 80
        assertEquals(OPAQUE | 0xC8C8C8, new Brightness(100).apply(source).get(0, 0));  // 200
        assertEquals(OPAQUE, new Brightness(-100).apply(source).get(0, 0));
    }

    @Test
    void brightnessRejectsOutOfRangeValues() {
        assertThrows(IllegalArgumentException.class, () -> new Brightness(150));
        assertThrows(IllegalArgumentException.class, () -> new Brightness(Double.NaN));
    }
}
