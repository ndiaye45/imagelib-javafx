package fr.ndiaye.imagelib.storage;

import java.nio.file.Files;
import java.nio.file.Path;

/** Choisit le DAO d'un dossier : JSON par défaut, texte si seul un fichier texte existe. */
public final class MetadataDaos {

    public static final String JSON_FILE = "library.json";
    public static final String TEXT_FILE = "library.txt";

    private MetadataDaos() {
    }

    public static MetadataDao forFolder(Path folder) {
        Path json = folder.resolve(JSON_FILE);
        Path text = folder.resolve(TEXT_FILE);
        if (!Files.exists(json) && Files.exists(text)) {
            return new TextMetadataDao(text);
        }
        return new JsonMetadataDao(json);
    }
}
