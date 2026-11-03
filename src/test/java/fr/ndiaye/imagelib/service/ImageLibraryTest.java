package fr.ndiaye.imagelib.service;

import fr.ndiaye.imagelib.model.ImageEntry;
import fr.ndiaye.imagelib.model.Tags;
import fr.ndiaye.imagelib.model.TransformationSpec;
import fr.ndiaye.imagelib.storage.JsonMetadataDao;
import fr.ndiaye.imagelib.storage.MetadataDao;
import fr.ndiaye.imagelib.storage.TextMetadataDao;
import fr.ndiaye.imagelib.transform.Brightness;
import fr.ndiaye.imagelib.transform.Grayscale;
import fr.ndiaye.imagelib.transform.Rotation;
import fr.ndiaye.imagelib.transform.Sepia;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageLibraryTest {

    @TempDir
    Path folder;

    @BeforeEach
    void createImages() throws IOException {
        // Le contenu n'a pas d'importance : la bibliothèque ne décode pas les images.
        for (String name : List.of("vacances.jpg", "Portrait.PNG", "chat.gif", "notes.txt")) {
            Files.writeString(folder.resolve(name), "x", StandardCharsets.UTF_8);
        }
        Files.createDirectory(folder.resolve("sous-dossier"));
    }

    private ImageLibrary open(MetadataDao dao) {
        ImageLibrary library = new ImageLibrary(folder, dao);
        library.reload();
        return library;
    }

    private ImageLibrary openJson() {
        return open(new JsonMetadataDao(folder.resolve("library.json")));
    }

    @Test
    void listsOnlySupportedImagesSortedByName() {
        List<String> names = openJson().entries().stream().map(ImageEntry::displayName).toList();

        assertEquals(List.of("chat.gif", "Portrait.PNG", "vacances.jpg"), names);
    }

    @Test
    void addTagKeepsTagsWithoutDuplicates() {
        ImageLibrary library = openJson();

        library.addTag("vacances.jpg", "plage");
        library.addTag("vacances.jpg", "  #Été ");
        library.addTag("vacances.jpg", "ETE");        // doublon : casse et accents ignorés
        library.addTag("vacances.jpg", "plage");

        assertEquals(List.of("plage", "Été"), library.find("vacances.jpg").orElseThrow().tags());
    }

    @Test
    void invalidTagsAreRefusedAndNothingIsChanged() {
        ImageLibrary library = openJson();

        assertThrows(IllegalArgumentException.class, () -> library.addTag("vacances.jpg", "  "));
        assertThrows(IllegalArgumentException.class, () -> library.addTag("vacances.jpg", "a, b"));
        assertTrue(library.find("vacances.jpg").orElseThrow().tags().isEmpty());
    }

    @Test
    void unknownImageIsRefused() {
        assertThrows(NoSuchElementException.class, () -> openJson().addTag("absente.jpg", "x"));
    }

    @Test
    void removeTagIgnoresCaseAndAccents() {
        ImageLibrary library = openJson();
        library.addTag("vacances.jpg", "Été");

        library.removeTag("vacances.jpg", "ete");

        assertTrue(library.find("vacances.jpg").orElseThrow().tags().isEmpty());
    }

    @Test
    void searchRequiresEveryTermToMatch() {
        ImageLibrary library = openJson();
        library.addTag("vacances.jpg", "plage");
        library.addTag("vacances.jpg", "été");
        library.addTag("vacances.jpg", "famille");
        library.addTag("chat.gif", "animal");
        library.addTag("chat.gif", "été");

        assertEquals(List.of("chat.gif", "vacances.jpg"), names(library.search("ete")));
        assertEquals(List.of("vacances.jpg"), names(library.search("plage ÉTÉ")));
        assertEquals(List.of("vacances.jpg"), names(library.search("plage, famille")));
        assertEquals(List.of(), names(library.search("plage animal")));
        assertEquals(3, library.search("").size());
    }

    @Test
    void searchAlsoMatchesFileNamesAndPartialWords() {
        ImageLibrary library = openJson();
        library.addTag("chat.gif", "animal domestique");

        assertEquals(List.of("Portrait.PNG"), names(library.search("portr")));
        assertEquals(List.of("chat.gif"), names(library.search("domest")));
    }

    @Test
    void tagCountsAreSortedByFrequency() {
        ImageLibrary library = openJson();
        library.addTag("vacances.jpg", "été");
        library.addTag("chat.gif", "Ete");
        library.addTag("vacances.jpg", "plage");

        List<String> tags = List.copyOf(library.tagCounts().keySet());

        // « été » et « Ete » sont le même tag : un seul compteur, avec la première graphie rencontrée.
        assertEquals(2, tags.size());
        assertEquals("ete", Tags.fold(tags.get(0)));
        assertEquals(2, library.tagCounts().get(tags.get(0)));
        assertEquals("plage", tags.get(1));
    }

    @Test
    void transformationsAreRecordedInOrderAndSurviveReloading() {
        ImageLibrary library = openJson();
        library.addTransformation("vacances.jpg", new Rotation(90));
        library.addTransformation("vacances.jpg", new Grayscale());
        library.addTransformation("vacances.jpg", new Sepia());

        ImageLibrary reopened = openJson();

        List<TransformationSpec> history = reopened.find("vacances.jpg").orElseThrow().transformations();
        assertEquals(List.of("rotation", "grayscale", "sepia"), history.stream().map(TransformationSpec::type).toList());
        assertEquals(90.0, history.get(0).params().get("angle"));
    }

    @Test
    void undoRemovesOnlyTheLastTransformation() {
        ImageLibrary library = openJson();
        library.addTransformation("vacances.jpg", new Rotation(90));
        library.addTransformation("vacances.jpg", new Brightness(10));

        library.undoLastTransformation("vacances.jpg");

        assertEquals(List.of("rotation"),
                library.find("vacances.jpg").orElseThrow().transformations().stream().map(TransformationSpec::type).toList());
        library.undoLastTransformation("vacances.jpg");
        library.undoLastTransformation("vacances.jpg");   // historique vide : sans effet
        assertTrue(library.find("vacances.jpg").orElseThrow().transformations().isEmpty());
    }

    @Test
    void clearTransformationsEmptiesTheHistoryButKeepsTags() {
        ImageLibrary library = openJson();
        library.addTag("vacances.jpg", "plage");
        library.addTransformation("vacances.jpg", new Sepia());

        library.clearTransformations("vacances.jpg");

        ImageEntry entry = library.find("vacances.jpg").orElseThrow();
        assertTrue(entry.transformations().isEmpty());
        assertEquals(List.of("plage"), entry.tags());
    }

    @ParameterizedTest
    @ValueSource(strings = {"json", "text"})
    void sameBehaviourWithBothStorageFormats(String format) {
        MetadataDao dao = format.equals("json")
                ? new JsonMetadataDao(folder.resolve("library.json"))
                : new TextMetadataDao(folder.resolve("library.txt"));
        ImageLibrary library = open(dao);
        library.addTag("chat.gif", "animal");
        library.addTransformation("chat.gif", new Rotation(180));

        ImageEntry reloaded = open(dao).find("chat.gif").orElseThrow();

        assertEquals(List.of("animal"), reloaded.tags());
        assertEquals(List.of(new Rotation(180).toSpec()), reloaded.transformations());
    }

    @Test
    void metadataOfMissingImagesIsKeptInTheFile() throws IOException {
        ImageLibrary library = openJson();
        library.addTag("chat.gif", "animal");
        Files.delete(folder.resolve("chat.gif"));

        ImageLibrary withoutCat = openJson();
        assertFalse(names(withoutCat.entries()).contains("chat.gif"));
        withoutCat.addTag("vacances.jpg", "plage");   // une sauvegarde ne doit pas effacer les orphelins

        Files.writeString(folder.resolve("chat.gif"), "x", StandardCharsets.UTF_8);
        assertEquals(List.of("animal"), openJson().find("chat.gif").orElseThrow().tags());
    }

    @Test
    void corruptedMetadataIsBackedUpAndTheLibraryStartsEmpty() throws IOException {
        Path file = folder.resolve("library.json");
        Files.writeString(file, "{ pas du json", StandardCharsets.UTF_8);

        ImageLibrary library = openJson();

        assertEquals(1, library.warnings().size());
        assertEquals(3, library.entries().size());
        assertTrue(Files.exists(folder.resolve("library.json.corrupt")));
        assertEquals("{ pas du json", Files.readString(folder.resolve("library.json.corrupt"), StandardCharsets.UTF_8));
    }

    @Test
    void failedSaveDoesNotKeepTheChangeInMemory() throws IOException {
        // Le chemin des métadonnées est un dossier : l'écriture est forcément impossible.
        Path blocked = folder.resolve("bloque.json");
        Files.createDirectory(blocked);
        Files.writeString(blocked.resolve("x"), "x", StandardCharsets.UTF_8);
        ImageLibrary library = new ImageLibrary(folder, new JsonMetadataDao(blocked));
        library.reload();   // lecture impossible aussi : avertissement, bibliothèque vide de métadonnées

        assertThrows(RuntimeException.class, () -> library.addTag("vacances.jpg", "plage"));

        assertTrue(library.find("vacances.jpg").orElseThrow().tags().isEmpty());
    }

    private static List<String> names(List<ImageEntry> entries) {
        return entries.stream().map(ImageEntry::displayName).toList();
    }
}
