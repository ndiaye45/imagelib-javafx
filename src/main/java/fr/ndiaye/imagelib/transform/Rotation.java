package fr.ndiaye.imagelib.transform;

import fr.ndiaye.imagelib.model.Raster;

import java.util.Map;

/** Rotation par multiples de 90°, dans le sens des aiguilles d'une montre (270 = 90° à gauche). */
public record Rotation(int angle) implements ImageTransformation {

    public Rotation {
        if (angle % 90 != 0) {
            throw new IllegalArgumentException("L'angle doit être un multiple de 90 : " + angle);
        }
        angle = ((angle % 360) + 360) % 360;
    }

    @Override
    public String type() {
        return TransformationFactory.ROTATION;
    }

    @Override
    public Map<String, Double> params() {
        return Map.of("angle", (double) angle);
    }

    @Override
    public Raster apply(Raster source) {
        int w = source.width();
        int h = source.height();
        int[] in = source.argb();
        switch (angle) {
            case 90 -> {
                int[] out = new int[in.length];
                for (int y = 0; y < h; y++) {
                    for (int x = 0; x < w; x++) {
                        // le pixel (x, y) arrive en (h-1-y, x) dans une image de largeur h
                        out[x * h + (h - 1 - y)] = in[y * w + x];
                    }
                }
                return new Raster(h, w, out);
            }
            case 180 -> {
                int[] out = new int[in.length];
                for (int i = 0; i < in.length; i++) {
                    out[in.length - 1 - i] = in[i];
                }
                return new Raster(w, h, out);
            }
            case 270 -> {
                int[] out = new int[in.length];
                for (int y = 0; y < h; y++) {
                    for (int x = 0; x < w; x++) {
                        // le pixel (x, y) arrive en (y, w-1-x) dans une image de largeur h
                        out[(w - 1 - x) * h + y] = in[y * w + x];
                    }
                }
                return new Raster(h, w, out);
            }
            default -> {
                return source;
            }
        }
    }
}
