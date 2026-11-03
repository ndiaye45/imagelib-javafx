package fr.ndiaye.imagelib.transform;

import fr.ndiaye.imagelib.model.Raster;

import java.util.Map;

/** Luminosité : +20 éclaircit de 20 %, -20 assombrit de 20 % (de -100 à +100). */
public record Brightness(double percent) implements ImageTransformation {

    public Brightness {
        if (Double.isNaN(percent) || percent < -100 || percent > 100) {
            throw new IllegalArgumentException("La luminosité doit être comprise entre -100 et 100 : " + percent);
        }
    }

    @Override
    public String type() {
        return TransformationFactory.BRIGHTNESS;
    }

    @Override
    public Map<String, Double> params() {
        return Map.of("percent", percent);
    }

    @Override
    public Raster apply(Raster source) {
        double factor = 1 + percent / 100.0;
        return source.map(p -> Argb.pack(
                Argb.alpha(p),
                (int) Math.round(Argb.red(p) * factor),
                (int) Math.round(Argb.green(p) * factor),
                (int) Math.round(Argb.blue(p) * factor)));
    }
}
