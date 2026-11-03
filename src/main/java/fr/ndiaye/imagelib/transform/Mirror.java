package fr.ndiaye.imagelib.transform;

import fr.ndiaye.imagelib.model.Raster;

import java.util.Map;

/** Miroir horizontal (gauche et droite échangées). */
public record Mirror() implements ImageTransformation {

    @Override
    public String type() {
        return TransformationFactory.MIRROR;
    }

    @Override
    public Map<String, Double> params() {
        return Map.of();
    }

    @Override
    public Raster apply(Raster source) {
        int w = source.width();
        int h = source.height();
        int[] in = source.argb();
        int[] out = new int[in.length];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                out[y * w + (w - 1 - x)] = in[y * w + x];
            }
        }
        return new Raster(w, h, out);
    }
}
