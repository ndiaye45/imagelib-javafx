package fr.ndiaye.imagelib.transform;

import fr.ndiaye.imagelib.model.Raster;
import fr.ndiaye.imagelib.model.TransformationSpec;

import java.util.Map;

/**
 * Une transformation d'image. L'interface est « scellée » : la liste des transformations
 * possibles est connue du compilateur, ce qui permet d'utiliser un {@code switch} exhaustif.
 */
public sealed interface ImageTransformation
        permits Rotation, Mirror, Grayscale, Sepia, Invert, Brightness {

    /** Identifiant stable utilisé dans les fichiers de métadonnées. */
    String type();

    /** Paramètres à enregistrer (vide si la transformation n'en a pas). */
    Map<String, Double> params();

    /** Applique la transformation sans modifier la source. */
    Raster apply(Raster source);

    default TransformationSpec toSpec() {
        return new TransformationSpec(type(), params());
    }
}
