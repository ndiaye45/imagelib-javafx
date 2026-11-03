package fr.ndiaye.imagelib.transform;

import fr.ndiaye.imagelib.model.Raster;

import java.util.Map;

/** Filtre sépia (teinte brune d'ancienne photographie). */
public record Sepia() implements ImageTransformation {

    @Override
    public String type() {
        return TransformationFactory.SEPIA;
    }

    @Override
    public Map<String, Double> params() {
        return Map.of();
    }

    @Override
    public Raster apply(Raster source) {
        return source.map(p -> {
            double r = Argb.red(p);
            double g = Argb.green(p);
            double b = Argb.blue(p);
            return Argb.pack(
                    Argb.alpha(p),
                    (int) Math.round(0.393 * r + 0.769 * g + 0.189 * b),
                    (int) Math.round(0.349 * r + 0.686 * g + 0.168 * b),
                    (int) Math.round(0.272 * r + 0.534 * g + 0.131 * b));
        });
    }
}
