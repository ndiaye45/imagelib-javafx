package fr.ndiaye.imagelib.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Métadonnées d'une image : son chemin (relatif au dossier de la bibliothèque, avec des « / »),
 * ses tags et la liste ordonnée des transformations appliquées.
 * Immuable : chaque modification produit une nouvelle instance.
 */
public record ImageEntry(String path, List<String> tags, List<TransformationSpec> transformations) {

    public ImageEntry {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("Chemin d'image manquant");
        }
        tags = tags == null ? List.of() : List.copyOf(tags);
        transformations = transformations == null ? List.of() : List.copyOf(transformations);
    }

    public static ImageEntry empty(String path) {
        return new ImageEntry(path, List.of(), List.of());
    }

    /** Nom du fichier, sans le dossier. */
    public String displayName() {
        int slash = path.lastIndexOf('/');
        return slash < 0 ? path : path.substring(slash + 1);
    }

    public boolean hasTag(String tag) {
        String wanted = Tags.fold(tag);
        return tags.stream().anyMatch(existing -> Tags.fold(existing).equals(wanted));
    }

    public ImageEntry withTags(List<String> newTags) {
        return new ImageEntry(path, newTags, transformations);
    }

    public ImageEntry withTransformations(List<TransformationSpec> newTransformations) {
        return new ImageEntry(path, tags, newTransformations);
    }

    public ImageEntry plusTag(String tag) {
        List<String> updated = new ArrayList<>(tags);
        updated.add(tag);
        return withTags(updated);
    }

    public ImageEntry minusTag(String tag) {
        String unwanted = Tags.fold(tag);
        return withTags(tags.stream().filter(t -> !Tags.fold(t).equals(unwanted)).toList());
    }

    public ImageEntry plusTransformation(TransformationSpec spec) {
        List<TransformationSpec> updated = new ArrayList<>(transformations);
        updated.add(spec);
        return withTransformations(updated);
    }

    public ImageEntry withoutLastTransformation() {
        if (transformations.isEmpty()) {
            return this;
        }
        return withTransformations(transformations.subList(0, transformations.size() - 1));
    }
}
