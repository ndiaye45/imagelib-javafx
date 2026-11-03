package fr.ndiaye.imagelib.service;

import fr.ndiaye.imagelib.storage.MetadataDaos;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class DemoLibraryTest {

    @Test
    void embeddedDemoIsCompleteAndLoadable(@TempDir Path folder) throws IOException {
        DemoLibrary.extractTo(folder);

        ImageLibrary library = new ImageLibrary(folder, MetadataDaos.forFolder(folder));
        library.reload();

        assertEquals(48, library.entries().size());
        assertTrue(library.warnings().isEmpty(), "aucune image référencée ne doit manquer : " + library.warnings());
        for (var entry : library.entries()) {
            assertTrue(Files.size(library.resolve(entry.path())) > 1000, entry.path() + " est vide");
        }
        assertFalse(library.search("ete").isEmpty());
    }
}
