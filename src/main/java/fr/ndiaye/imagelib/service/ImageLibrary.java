package fr.ndiaye.imagelib.service;

import fr.ndiaye.imagelib.model.ImageEntry;
import fr.ndiaye.imagelib.model.LibraryMetadata;
import fr.ndiaye.imagelib.model.Tags;
import fr.ndiaye.imagelib.storage.MetadataDao;
import fr.ndiaye.imagelib.transform.ImageTransformation;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Logique métier de la bibliothèque : images d'un dossier, tags, recherche et historique
 * des transformations. Chaque modification est enregistrée immédiatement via le DAO.
 * Cette classe ne dépend ni de JavaFX ni du format de stockage.
 */
public final class ImageLibrary {

    private static final Set<String> EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif", "bmp");

    private final Path folder;
    private final MetadataDao dao;
    private final Map<String, ImageEntry> entries = new LinkedHashMap<>();
    // Métadonnées d'images qui ne sont plus dans le dossier : conservées au cas où elles reviennent.
    private final Map<String, ImageEntry> orphans = new LinkedHashMap<>();
    private final List<String> warnings = new ArrayList<>();

    public ImageLibrary(Path folder, MetadataDao dao) {
        this.folder = folder;
        this.dao = dao;
    }

    public Path folder() {
        return folder;
    }

    public MetadataDao dao() {
        return dao;
    }

    /** Messages à afficher à l'utilisateur après le chargement (fichier illisible, etc.). */
    public List<String> warnings() {
        return List.copyOf(warnings);
    }

    public static boolean isSupportedImage(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        return dot > 0 && EXTENSIONS.contains(name.substring(dot + 1));
    }

    // ------------------------------------------------------------------ chargement

    /** (Re)lit le dossier et les métadonnées. */
    public void reload() {
        warnings.clear();
        entries.clear();
        orphans.clear();

        Map<String, ImageEntry> known = new HashMap<>();
        try {
            for (ImageEntry entry : dao.load().images()) {
                known.put(entry.path(), entry);
            }
        } catch (IOException e) {
            warnings.add("Métadonnées illisibles : " + e.getMessage()
                    + " Une bibliothèque vide est utilisée, l'ancien fichier est sauvegardé (.corrupt).");
            backupCorruptedFile();
        }

        for (String path : scanFolder()) {
            ImageEntry existing = known.remove(path);
            entries.put(path, existing != null ? existing : ImageEntry.empty(path));
        }
        orphans.putAll(known);
    }

    private List<String> scanFolder() {
        try (Stream<Path> files = Files.list(folder)) {
            return files.filter(Files::isRegularFile)
                    .filter(ImageLibrary::isSupportedImage)
                    .map(file -> folder.relativize(file).toString().replace('\\', '/'))
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Impossible de lire le dossier " + folder, e);
        }
    }

    private void backupCorruptedFile() {
        Path source = dao.file();
        try {
            if (Files.exists(source)) {
                Files.copy(source, source.resolveSibling(source.getFileName() + ".corrupt"),
                        StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            warnings.add("La sauvegarde du fichier illisible a échoué : " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------ consultation

    /** Images du dossier, triées par nom. */
    public List<ImageEntry> entries() {
        return entries.values().stream()
                .sorted(Comparator.comparing(ImageEntry::displayName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    public Optional<ImageEntry> find(String path) {
        return Optional.ofNullable(entries.get(path));
    }

    public Path resolve(String path) {
        return folder.resolve(path);
    }

    /**
     * Recherche : chaque terme (séparés par des espaces ou des virgules) doit correspondre
     * au nom de l'image ou à l'un de ses tags. Insensible à la casse et aux accents.
     */
    public List<ImageEntry> search(String query) {
        List<String> terms = Tags.terms(query);
        return entries().stream()
                .filter(entry -> terms.stream().allMatch(term -> matches(entry, term)))
                .toList();
    }

    private static boolean matches(ImageEntry entry, String term) {
        return Tags.fold(entry.displayName()).contains(term)
                || entry.tags().stream().anyMatch(tag -> Tags.fold(tag).contains(term));
    }

    /** Tags utilisés dans la bibliothèque avec leur nombre d'images, du plus fréquent au moins fréquent. */
    public Map<String, Integer> tagCounts() {
        Map<String, Integer> counts = new HashMap<>();
        Map<String, String> display = new HashMap<>();
        for (ImageEntry entry : entries.values()) {
            for (String tag : entry.tags()) {
                String key = Tags.fold(tag);
                counts.merge(key, 1, Integer::sum);
                display.putIfAbsent(key, tag);
            }
        }
        Map<String, Integer> sorted = new LinkedHashMap<>();
        counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed()
                        .thenComparing(Map.Entry.comparingByKey()))
                .forEach(e -> sorted.put(display.get(e.getKey()), e.getValue()));
        return sorted;
    }

    // ------------------------------------------------------------------ modification

    /** Ajoute un tag (sans doublon, sans tenir compte de la casse ni des accents). */
    public ImageEntry addTag(String path, String rawTag) {
        String tag = Tags.normalize(rawTag);
        ImageEntry entry = require(path);
        if (entry.hasTag(tag)) {
            return entry;
        }
        return update(entry.plusTag(tag));
    }

    public ImageEntry removeTag(String path, String tag) {
        return update(require(path).minusTag(tag));
    }

    /** Ajoute une transformation à la fin de l'historique de l'image. */
    public ImageEntry addTransformation(String path, ImageTransformation transformation) {
        return update(require(path).plusTransformation(transformation.toSpec()));
    }

    public ImageEntry undoLastTransformation(String path) {
        return update(require(path).withoutLastTransformation());
    }

    public ImageEntry clearTransformations(String path) {
        return update(require(path).withTransformations(List.of()));
    }

    private ImageEntry require(String path) {
        ImageEntry entry = entries.get(path);
        if (entry == null) {
            throw new NoSuchElementException("Image inconnue : " + path);
        }
        return entry;
    }

    private ImageEntry update(ImageEntry updated) {
        ImageEntry previous = entries.put(updated.path(), updated);
        try {
            save();
        } catch (UncheckedIOException e) {
            entries.put(updated.path(), previous); // on ne garde pas une modification non enregistrée
            throw e;
        }
        return updated;
    }

    private void save() {
        List<ImageEntry> all = new ArrayList<>(entries.values());
        all.addAll(orphans.values());
        all.sort(Comparator.comparing(ImageEntry::path, String.CASE_INSENSITIVE_ORDER));
        try {
            dao.save(new LibraryMetadata(all));
        } catch (IOException e) {
            throw new UncheckedIOException("Enregistrement des métadonnées impossible : " + e.getMessage(), e);
        }
    }
}
