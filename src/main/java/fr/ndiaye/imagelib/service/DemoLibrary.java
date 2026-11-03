package fr.ndiaye.imagelib.service;

import fr.ndiaye.imagelib.model.ImageEntry;
import fr.ndiaye.imagelib.storage.JsonMetadataDao;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * Bibliothèque de démonstration embarquée dans l'application (dossier {@code samples} copié dans le JAR).
 * Elle permet d'essayer le programme immédiatement après l'avoir téléchargé, sans avoir d'images sous la main.
 * Elle est copiée dans un dossier temporaire, supprimé à la fermeture : les images d'origine ne sont jamais modifiées.
 */
public final class DemoLibrary {

    private static final String LIBRARY_FILE = "library.json";

    private DemoLibrary() {
    }

    /** Copie la démo dans un nouveau dossier temporaire et renvoie ce dossier. */
    public static Path extract() throws IOException {
        Path folder = Files.createTempDirectory("imagelib-demo-");
        Runtime.getRuntime().addShutdownHook(new Thread(() -> deleteQuietly(folder)));
        extractTo(folder);
        return folder;
    }

    /** Copie la démo dans un dossier existant (utile pour les tests). */
    public static void extractTo(Path folder) throws IOException {
        Path library = folder.resolve(LIBRARY_FILE);
        copyResource(LIBRARY_FILE, library);
        // library.json liste les images : pas besoin d'un index séparé, ni de lister le contenu du JAR.
        for (ImageEntry entry : new JsonMetadataDao(library).load().images()) {
            copyResource(entry.path(), folder.resolve(entry.path()));
        }
    }

    private static void copyResource(String name, Path target) throws IOException {
        try (InputStream in = DemoLibrary.class.getResourceAsStream("/demo/" + name)) {
            if (in == null) {
                throw new IOException("ressource de démonstration absente : " + name);
            }
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void deleteQuietly(Path folder) {
        try (Stream<Path> files = Files.walk(folder)) {
            files.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // meilleur effort : un fichier temporaire restant n'est pas grave
                }
            });
        } catch (IOException | UncheckedIOException ignored) {
            // idem
        }
    }
}
