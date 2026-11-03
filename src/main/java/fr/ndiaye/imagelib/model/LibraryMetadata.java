package fr.ndiaye.imagelib.model;

import java.util.List;

/** Toutes les métadonnées de la bibliothèque : un seul fichier les contient. */
public record LibraryMetadata(List<ImageEntry> images) {

    public LibraryMetadata {
        images = images == null ? List.of() : List.copyOf(images);
    }

    public static LibraryMetadata empty() {
        return new LibraryMetadata(List.of());
    }
}
