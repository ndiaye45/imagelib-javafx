package fr.ndiaye.imagelib.transform;

import fr.ndiaye.imagelib.model.Raster;

import java.util.Map;

/** Noir et blanc (niveaux de gris, pondération de luminance usuelle). */
public record Grayscale() implements ImageTransformation {

    @Override
    public String type() {
        return TransformationFactory.GRAYSCALE;
    }

    @Override
    public Map<String, Double> params() {
        return Map.of();
    }

    @Override
    public Raster apply(Raster source) {
        return source.map(p -> {
            int luma = (int) Math.round(
                    0.299 * Argb.red(p) + 0.587 * Argb.green(p) + 0.114 * Argb.blue(p));
            return Argb.pack(Argb.alpha(p), luma, luma, luma);
        });
    }
}
