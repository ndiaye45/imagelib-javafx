package fr.ndiaye.imagelib.transform;

import fr.ndiaye.imagelib.model.Raster;
import fr.ndiaye.imagelib.model.TransformationSpec;

import java.util.List;

/** Rejoue une liste de transformations, dans l'ordre exact où elles ont été enregistrées. */
public final class TransformationPipeline {

    private TransformationPipeline() {
    }

    public static Raster apply(Raster source, List<TransformationSpec> history) {
        Raster current = source;
        for (TransformationSpec spec : history) {
            current = TransformationFactory.create(spec).apply(current);
        }
        return current;
    }
}
