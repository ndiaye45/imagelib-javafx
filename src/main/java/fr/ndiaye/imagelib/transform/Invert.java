package fr.ndiaye.imagelib.transform;

import fr.ndiaye.imagelib.model.Raster;

import java.util.Map;

/** Inversion des couleurs (négatif). */
public record Invert() implements ImageTransformation {

    @Override
    public String type() {
        return TransformationFactory.INVERT;
    }

    @Override
    public Map<String, Double> params() {
        return Map.of();
    }

    @Override
    public Raster apply(Raster source) {
        return source.map(p -> Argb.pack(
                Argb.alpha(p), 255 - Argb.red(p), 255 - Argb.green(p), 255 - Argb.blue(p)));
    }
}
