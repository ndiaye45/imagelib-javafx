package fr.ndiaye.imagelib.storage;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import fr.ndiaye.imagelib.model.LibraryMetadata;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Stockage des métadonnées dans un fichier JSON lisible (indenté). */
public final class JsonMetadataDao implements MetadataDao {

    private final Path file;
    private final ObjectMapper mapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    public JsonMetadataDao(Path file) {
        this.file = file;
    }

    @Override
    public Path file() {
        return file;
    }

    @Override
    public LibraryMetadata load() throws IOException {
        if (!Files.exists(file)) {
            return LibraryMetadata.empty();
        }
        try {
            LibraryMetadata metadata = mapper.readValue(Files.readString(file, StandardCharsets.UTF_8), LibraryMetadata.class);
            return metadata == null ? LibraryMetadata.empty() : metadata;
        } catch (JacksonException e) {
            throw new IOException("Fichier JSON invalide (" + file.getFileName() + ") : " + e.getOriginalMessage(), e);
        }
    }

    @Override
    public void save(LibraryMetadata metadata) throws IOException {
        // On écrit dans un fichier temporaire puis on le déplace : un plantage en cours
        // d'écriture ne laisse jamais un fichier de métadonnées à moitié écrit.
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(temp, mapper.writeValueAsString(metadata) + System.lineSeparator(), StandardCharsets.UTF_8);
        Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
    }
}
