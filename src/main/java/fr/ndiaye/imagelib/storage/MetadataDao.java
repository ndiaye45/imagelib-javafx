package fr.ndiaye.imagelib.storage;

import fr.ndiaye.imagelib.model.LibraryMetadata;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Accès aux métadonnées de la bibliothèque (patron DAO) : le reste de l'application ne sait
 * pas si les données sont stockées en JSON ou en texte, on peut changer de format sans
 * toucher à la logique métier.
 */
public interface MetadataDao {

    /** Fichier utilisé pour le stockage. */
    Path file();

    /** Charge les métadonnées ; une bibliothèque vide si le fichier n'existe pas encore. */
    LibraryMetadata load() throws IOException;

    void save(LibraryMetadata metadata) throws IOException;
}
