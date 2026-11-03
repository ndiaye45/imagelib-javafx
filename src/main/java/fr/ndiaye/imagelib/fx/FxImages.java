package fr.ndiaye.imagelib.fx;

import fr.ndiaye.imagelib.model.Raster;
import javafx.scene.image.Image;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;

/** Passerelle entre les images JavaFX et le {@link Raster} indépendant de JavaFX. */
public final class FxImages {

    private FxImages() {
    }

    public static Raster toRaster(Image image) {
        int width = (int) image.getWidth();
        int height = (int) image.getHeight();
        PixelReader reader = image.getPixelReader();
        if (reader == null || width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Image illisible");
        }
        int[] pixels = new int[width * height];
        reader.getPixels(0, 0, width, height, PixelFormat.getIntArgbInstance(), pixels, 0, width);
        return new Raster(width, height, pixels);
    }

    public static WritableImage toImage(Raster raster) {
        WritableImage image = new WritableImage(raster.width(), raster.height());
        image.getPixelWriter().setPixels(0, 0, raster.width(), raster.height(),
                PixelFormat.getIntArgbInstance(), raster.argb(), 0, raster.width());
        return image;
    }
}
