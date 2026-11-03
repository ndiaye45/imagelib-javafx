package fr.ndiaye.imagelib.transform;

import fr.ndiaye.imagelib.model.TransformationSpec;

import java.util.List;
import java.util.Map;

/**
 * Fait le lien entre les métadonnées (un type et des paramètres) et les objets
 * {@link ImageTransformation} : c'est ce qui permet de rejouer l'historique d'une image.
 */
public final class TransformationFactory {

    public static final String ROTATION = "rotation";
    public static final String MIRROR = "mirror";
    public static final String GRAYSCALE = "grayscale";
    public static final String SEPIA = "sepia";
    public static final String INVERT = "invert";
    public static final String BRIGHTNESS = "brightness";

    private TransformationFactory() {
    }

    public static List<String> knownTypes() {
        return List.of(ROTATION, MIRROR, GRAYSCALE, SEPIA, INVERT, BRIGHTNESS);
    }

    public static ImageTransformation create(TransformationSpec spec) {
        Map<String, Double> params = spec.params();
        return switch (spec.type()) {
            case ROTATION -> new Rotation(integerParam(params, "angle"));
            case MIRROR -> new Mirror();
            case GRAYSCALE -> new Grayscale();
            case SEPIA -> new Sepia();
            case INVERT -> new Invert();
            case BRIGHTNESS -> new Brightness(requiredParam(params, "percent"));
            default -> throw new IllegalArgumentException(
                    "Transformation inconnue : « " + spec.type() + " »");
        };
    }

    /** Libellé lisible (en français) affiché dans l'historique. */
    public static String label(TransformationSpec spec) {
        return switch (create(spec)) {
            case Rotation r when r.angle() == 270 -> "Rotation 90° vers la gauche";
            case Rotation r -> "Rotation " + r.angle() + "° vers la droite";
            case Mirror m -> "Miroir horizontal";
            case Grayscale g -> "Noir et blanc";
            case Sepia s -> "Sépia";
            case Invert i -> "Inversion des couleurs";
            case Brightness b -> String.format("Luminosité %+d %%", Math.round(b.percent()));
        };
    }

    private static double requiredParam(Map<String, Double> params, String name) {
        Double value = params.get(name);
        if (value == null) {
            throw new IllegalArgumentException("Paramètre manquant : " + name);
        }
        return value;
    }

    private static int integerParam(Map<String, Double> params, String name) {
        double value = requiredParam(params, name);
        if (value != Math.rint(value)) {
            throw new IllegalArgumentException("Le paramètre " + name + " doit être un entier : " + value);
        }
        return (int) value;
    }
}
