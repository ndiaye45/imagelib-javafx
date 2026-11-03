package fr.ndiaye.imagelib.model;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/**
 * Description d'une transformation telle qu'elle est enregistrée dans les métadonnées :
 * un type (« rotation », « sepia »...) et ses paramètres éventuels.
 */
public record TransformationSpec(String type, Map<String, Double> params) {

    public TransformationSpec {
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("Type de transformation manquant");
        }
        // TreeMap : ordre des paramètres stable, donc fichiers de métadonnées reproductibles.
        params = params == null
                ? Map.of()
                : Collections.unmodifiableMap(new TreeMap<>(params));
    }

    public static TransformationSpec of(String type) {
        return new TransformationSpec(type, Map.of());
    }
}
