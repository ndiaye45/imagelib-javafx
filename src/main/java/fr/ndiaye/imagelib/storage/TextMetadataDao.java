package fr.ndiaye.imagelib.storage;

import fr.ndiaye.imagelib.model.ImageEntry;
import fr.ndiaye.imagelib.model.LibraryMetadata;
import fr.ndiaye.imagelib.model.TransformationSpec;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Stockage des métadonnées dans un fichier texte simple :
 * <pre>
 * [vacances.jpg]
 * tags = plage, été, famille
 * transform = rotation angle=90.0
 * transform = grayscale
 * </pre>
 * Les lignes « transform » sont écrites dans l'ordre d'application.
 */
public final class TextMetadataDao implements MetadataDao {

    private static final String HEADER = "# Gestionnaire d'images - métadonnées (format texte v1)";

    private final Path file;

    public TextMetadataDao(Path file) {
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
        List<ImageEntry> entries = new ArrayList<>();
        String currentPath = null;
        List<String> tags = new ArrayList<>();
        List<TransformationSpec> transformations = new ArrayList<>();

        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).strip();
            int number = i + 1;
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (line.startsWith("[") && line.endsWith("]") && line.length() > 2) {
                if (currentPath != null) {
                    entries.add(new ImageEntry(currentPath, tags, transformations));
                }
                currentPath = line.substring(1, line.length() - 1);
                tags = new ArrayList<>();
                transformations = new ArrayList<>();
            } else if (currentPath == null) {
                throw new IOException("Ligne " + number + " : une section [image] est attendue avant « " + line + " »");
            } else if (line.startsWith("tags")) {
                tags.addAll(parseTags(valueOf(line, number)));
            } else if (line.startsWith("transform")) {
                transformations.add(parseTransformation(valueOf(line, number), number));
            } else {
                throw new IOException("Ligne " + number + " : instruction inconnue « " + line + " »");
            }
        }
        if (currentPath != null) {
            entries.add(new ImageEntry(currentPath, tags, transformations));
        }
        return new LibraryMetadata(entries);
    }

    @Override
    public void save(LibraryMetadata metadata) throws IOException {
        StringBuilder out = new StringBuilder(HEADER).append(System.lineSeparator());
        for (ImageEntry entry : metadata.images()) {
            out.append(System.lineSeparator()).append('[').append(entry.path()).append(']').append(System.lineSeparator());
            if (!entry.tags().isEmpty()) {
                out.append("tags = ").append(String.join(", ", entry.tags())).append(System.lineSeparator());
            }
            for (TransformationSpec spec : entry.transformations()) {
                out.append("transform = ").append(spec.type());
                for (Map.Entry<String, Double> param : spec.params().entrySet()) {
                    out.append(' ').append(param.getKey()).append('=').append(param.getValue());
                }
                out.append(System.lineSeparator());
            }
        }
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(temp, out.toString(), StandardCharsets.UTF_8);
        Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
    }

    private static String valueOf(String line, int number) throws IOException {
        int equals = line.indexOf('=');
        if (equals < 0) {
            throw new IOException("Ligne " + number + " : « = » attendu dans « " + line + " »");
        }
        return line.substring(equals + 1).strip();
    }

    private static List<String> parseTags(String value) {
        List<String> tags = new ArrayList<>();
        for (String part : value.split(",")) {
            String tag = part.strip();
            if (!tag.isEmpty()) {
                tags.add(tag);
            }
        }
        return tags;
    }

    private static TransformationSpec parseTransformation(String value, int number) throws IOException {
        String[] parts = value.split("\\s+");
        Map<String, Double> params = new LinkedHashMap<>();
        for (int i = 1; i < parts.length; i++) {
            int equals = parts[i].indexOf('=');
            if (equals <= 0) {
                throw new IOException("Ligne " + number + " : paramètre invalide « " + parts[i] + " »");
            }
            try {
                params.put(parts[i].substring(0, equals), Double.parseDouble(parts[i].substring(equals + 1)));
            } catch (NumberFormatException e) {
                throw new IOException("Ligne " + number + " : valeur numérique invalide dans « " + parts[i] + " »", e);
            }
        }
        return new TransformationSpec(parts[0], params);
    }
}
