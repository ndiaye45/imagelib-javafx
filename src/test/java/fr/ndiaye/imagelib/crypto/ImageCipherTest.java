package fr.ndiaye.imagelib.crypto;

import fr.ndiaye.imagelib.crypto.ImageCipher.ImageCipherException;
import fr.ndiaye.imagelib.model.Raster;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class ImageCipherTest {

    private static final char[] PASSWORD = "mot-de-passe".toCharArray();

    /** Image de test avec des pixels transparents et semi-transparents, pour vérifier l'alpha. */
    private static Raster sample(int w, int h) {
        Random random = new Random(42);
        int[] px = new int[w * h];
        for (int i = 0; i < px.length; i++) {
            px[i] = random.nextInt();
        }
        return new Raster(w, h, px);
    }

    @Test
    void decryptingGivesBackTheOriginalImage() {
        Raster original = sample(37, 23);
        Raster encrypted = ImageCipher.encrypt(original, PASSWORD);
        assertEquals(original, ImageCipher.decrypt(encrypted, PASSWORD));
    }

    @Test
    void worksOnTinyImages() {
        for (int[] size : new int[][]{{1, 1}, {1, 9}, {2, 2}, {7, 1}}) {
            Raster original = sample(size[0], size[1]);
            assertEquals(original, ImageCipher.decrypt(ImageCipher.encrypt(original, PASSWORD), PASSWORD),
                    size[0] + "x" + size[1]);
        }
    }

    @Test
    void encryptedImageKeepsItsWidthAndIsFullyOpaque() {
        Raster original = sample(40, 10);
        Raster encrypted = ImageCipher.encrypt(original, PASSWORD);
        assertEquals(original.width(), encrypted.width());
        assertTrue(encrypted.height() > original.height());       // en-tête + étiquette d'authentification
        for (int pixel : encrypted.argb()) {
            assertEquals(0xFF, pixel >>> 24, "un alpha non opaque serait altéré par JavaFX");
        }
    }

    @Test
    void encryptedImageLooksNothingLikeTheOriginal() {
        Raster flat = Raster.blank(30, 30).map(p -> 0xFF2040C0);      // image unie
        Raster encrypted = ImageCipher.encrypt(flat, PASSWORD);
        long distinct = java.util.Arrays.stream(encrypted.argb()).distinct().count();
        assertTrue(distinct > encrypted.argb().length * 0.9, "le résultat doit ressembler à du bruit");
    }

    @Test
    void twoEncryptionsOfTheSameImageDiffer() {
        Raster original = sample(20, 20);
        assertNotEquals(ImageCipher.encrypt(original, PASSWORD), ImageCipher.encrypt(original, PASSWORD));
    }

    @Test
    void wrongPasswordIsRejected() {
        Raster encrypted = ImageCipher.encrypt(sample(16, 16), PASSWORD);
        ImageCipherException e = assertThrows(ImageCipherException.class,
                () -> ImageCipher.decrypt(encrypted, "autre-mot-de-passe".toCharArray()));
        assertTrue(e.getMessage().contains("incorrect"));
    }

    @Test
    void tamperedImageIsRejected() {
        Raster encrypted = ImageCipher.encrypt(sample(16, 16), PASSWORD);
        int[] px = encrypted.argb().clone();
        px[200] ^= 0x00010000;                                        // un seul bit modifié
        assertThrows(ImageCipherException.class,
                () -> ImageCipher.decrypt(new Raster(encrypted.width(), encrypted.height(), px), PASSWORD));
    }

    @Test
    void plainImageIsRecognisedAsNotEncrypted() {
        ImageCipherException e = assertThrows(ImageCipherException.class,
                () -> ImageCipher.decrypt(sample(50, 50), PASSWORD));
        assertTrue(e.getMessage().contains("pas été chiffrée"));
        assertThrows(ImageCipherException.class, () -> ImageCipher.decrypt(sample(2, 2), PASSWORD));
    }

    @Test
    void tooShortPasswordIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> ImageCipher.encrypt(sample(4, 4), "abc".toCharArray()));
        assertThrows(IllegalArgumentException.class, () -> ImageCipher.encrypt(sample(4, 4), null));
    }
}
