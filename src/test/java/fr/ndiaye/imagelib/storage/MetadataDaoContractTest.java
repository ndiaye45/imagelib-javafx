package fr.ndiaye.imagelib.storage;

import fr.ndiaye.imagelib.model.ImageEntry;
import fr.ndiaye.imagelib.model.LibraryMetadata;
import fr.ndiaye.imagelib.model.TransformationSpec;
import fr.ndiaye.imagelib.transform.Brightness;
import fr.ndiaye.imagelib.transform.Grayscale;
import fr.ndiaye.imagelib.transform.Rotation;
import fr.ndiaye.imagelib.transform.Sepia;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Le même jeu de tests est exécuté sur les deux implémentations du DAO : c'est la preuve
 * qu'elles sont interchangeables derrière l'interface {@link MetadataDao}.
 */
class MetadataDaoContractTest {

    enum Format {
        JSON {
            MetadataDao create(Path folder) {
                return new JsonMetadataDao(folder.resolve("library.json"));
            }
        },
        TEXT {
            MetadataDao create(Path folder) {
                return new TextMetadataDao(folder.resolve("library.txt"));
            }
        };

        abstract MetadataDao create(Path folder);
    }

    @TempDir
    Path folder;

    private static LibraryMetadata sample() {
        return new LibraryMetadata(List.of(
                new ImageEntry("vacances.jpg",
                        List.of("plage", "été", "famille", "voyage"),
                        List.of(new Rotation(90).toSpec(), new Grayscale().toSpec(), new Sepia().toSpec())),
                new ImageEntry("sous-dossier/portrait été.png",
                        List.of("portrait"),
                        List.of(new Brightness(-12.5).toSpec())),
                ImageEntry.empty("vide.gif")));
    }

    @ParameterizedTest
    @EnumSource(Format.class)
    void missingFileGivesAnEmptyLibrary(Format format) throws IOException {
        MetadataDao dao = format.create(folder);

        assertTrue(dao.load().images().isEmpty());
    }

    @ParameterizedTest
    @EnumSource(Format.class)
    void saveThenLoadGivesTheSameData(Format format) throws IOException {
        MetadataDao dao = format.create(folder);

        dao.save(sample());

        assertEquals(sample(), dao.load());
    }

    @ParameterizedTest
    @EnumSource(Format.class)
    void transformationOrderIsPreserved(Format format) throws IOException {
        MetadataDao dao = format.create(folder);
        dao.save(sample());

        List<TransformationSpec> loaded = dao.load().images().get(0).transformations();

        assertEquals(List.of("rotation", "grayscale", "sepia"), loaded.stream().map(TransformationSpec::type).toList());
        assertEquals(Map.of("angle", 90.0), loaded.get(0).params());
    }

    @ParameterizedTest
    @EnumSource(Format.class)
    void saveReplacesPreviousContentAndLeavesNoTemporaryFile(Format format) throws IOException {
        MetadataDao dao = format.create(folder);
        dao.save(sample());
        LibraryMetadata smaller = new LibraryMetadata(List.of(ImageEntry.empty("seule.png")));

        dao.save(smaller);

        assertEquals(smaller, dao.load());
        try (var files = Files.list(folder)) {
            assertFalse(files.anyMatch(f -> f.getFileName().toString().endsWith(".tmp")));
        }
    }

    @ParameterizedTest
    @EnumSource(Format.class)
    void bothFormatsDescribeTheSameLibrary(Format format) throws IOException {
        // Contrat : quel que soit le format, ce qui est relu est strictement ce qui a été écrit.
        MetadataDao dao = format.create(folder);
        dao.save(sample());

        assertEquals(sample().images().size(), dao.load().images().size());
        assertEquals(sample().images().get(1), dao.load().images().get(1));
    }

    @ParameterizedTest
    @EnumSource(Format.class)
    void corruptedFileIsReportedAsAnIOException(Format format) throws IOException {
        MetadataDao dao = format.create(folder);
        Files.writeString(dao.file(), "{ ceci n'est pas valide [[[", StandardCharsets.UTF_8);

        assertThrows(IOException.class, dao::load);
    }
}
