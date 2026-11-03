package fr.ndiaye.imagelib.storage;

import fr.ndiaye.imagelib.model.ImageEntry;
import fr.ndiaye.imagelib.model.LibraryMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextMetadataDaoTest {

    @TempDir
    Path folder;

    @Test
    void readsAHandWrittenFile() throws IOException {
        Path file = folder.resolve("library.txt");
        Files.writeString(file, """
                # commentaire
                [vacances.jpg]
                tags = plage, été , famille

                transform = rotation angle=90
                transform = grayscale
                transform = brightness percent=-10

                [portrait.png]
                """, StandardCharsets.UTF_8);

        LibraryMetadata loaded = new TextMetadataDao(file).load();

        assertEquals(2, loaded.images().size());
        ImageEntry vacances = loaded.images().get(0);
        assertEquals(List.of("plage", "été", "famille"), vacances.tags());
        assertEquals(3, vacances.transformations().size());
        assertEquals(Map.of("percent", -10.0), vacances.transformations().get(2).params());
        assertTrue(loaded.images().get(1).tags().isEmpty());
    }

    @Test
    void writtenFileIsHumanReadable() throws IOException {
        Path file = folder.resolve("library.txt");
        new TextMetadataDao(file).save(new LibraryMetadata(List.of(
                new ImageEntry("a.jpg", List.of("x", "y"), List.of()))));

        String content = Files.readString(file, StandardCharsets.UTF_8);

        assertTrue(content.contains("[a.jpg]"));
        assertTrue(content.contains("tags = x, y"));
    }

    @Test
    void invalidLinesAreReportedWithTheirNumber() throws IOException {
        Path file = folder.resolve("library.txt");
        Files.writeString(file, "[a.jpg]\ntags = x\nn'importe quoi\n", StandardCharsets.UTF_8);

        IOException error = assertThrows(IOException.class, () -> new TextMetadataDao(file).load());

        assertTrue(error.getMessage().contains("Ligne 3"), error.getMessage());
    }

    @Test
    void dataBeforeAnyImageSectionIsRejected() throws IOException {
        Path file = folder.resolve("library.txt");
        Files.writeString(file, "tags = x\n", StandardCharsets.UTF_8);

        assertThrows(IOException.class, () -> new TextMetadataDao(file).load());
    }

    @Test
    void invalidNumbersAreRejected() throws IOException {
        Path file = folder.resolve("library.txt");
        Files.writeString(file, "[a.jpg]\ntransform = rotation angle=beaucoup\n", StandardCharsets.UTF_8);

        assertThrows(IOException.class, () -> new TextMetadataDao(file).load());
    }

    @Test
    void daoFactoryPicksTheFormatFoundInTheFolder() throws IOException {
        assertTrue(MetadataDaos.forFolder(folder) instanceof JsonMetadataDao);

        Files.writeString(folder.resolve("library.txt"), "", StandardCharsets.UTF_8);
        assertTrue(MetadataDaos.forFolder(folder) instanceof TextMetadataDao);

        Files.writeString(folder.resolve("library.json"), "{}", StandardCharsets.UTF_8);
        assertTrue(MetadataDaos.forFolder(folder) instanceof JsonMetadataDao);
    }
}
