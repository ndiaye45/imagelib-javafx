package fr.ndiaye.imagelib.service;

import fr.ndiaye.imagelib.ml.ImageFeatures;
import fr.ndiaye.imagelib.ml.ModelEvaluation;
import fr.ndiaye.imagelib.ml.TagModel;
import fr.ndiaye.imagelib.ml.TagModel.Example;
import fr.ndiaye.imagelib.ml.TagModel.Suggestion;
import fr.ndiaye.imagelib.model.ImageEntry;
import fr.ndiaye.imagelib.model.Raster;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Propose des tags pour une image en apprenant de celles que l'utilisateur a déjà taguées.
 * <p>
 * L'image évaluée est <b>exclue de l'entraînement</b> : on lui propose ce que le modèle déduit des <i>autres</i>
 * images, pas ce qu'on lui a déjà écrit. Les caractéristiques de chaque image sont calculées une seule fois et gardées en mémoire.
 */
public final class TagAdvisor {

    /** Façon d'obtenir les pixels d'une image du dossier (fournie par l'interface ; remplaçable dans les tests). */
    @FunctionalInterface
    public interface RasterLoader {
        Raster load(String path) throws IOException;
    }

    /** Résultat d'une analyse : tags proposés, et qualité estimée du modèle (null si trop peu d'images pour l'évaluer). */
    public record Advice(List<Suggestion> suggestions, int trainingImages, ModelEvaluation.Report quality) {
    }

    /** Il faut un minimum d'exemples pour que « apprendre » ait un sens. */
    public static final int MIN_TRAINING_IMAGES = 6;
    static final int MIN_POSITIVES = 2;
    static final int MAX_SUGGESTIONS = 5;
    static final double THRESHOLD = 0.5;

    private final ImageLibrary library;
    private final RasterLoader loader;
    private final Map<String, double[]> featureCache = new ConcurrentHashMap<>();

    public TagAdvisor(ImageLibrary library, RasterLoader loader) {
        this.library = library;
        this.loader = loader;
    }

    public Advice advise(String path) throws IOException {
        ImageEntry current = library.find(path).orElseThrow(() -> new IllegalArgumentException("image inconnue : " + path));

        List<Example> training = new ArrayList<>();
        for (ImageEntry entry : library.entries()) {
            if (!entry.path().equals(path) && !entry.tags().isEmpty()) {
                training.add(new Example(features(entry.path()), entry.tags()));
            }
        }
        if (training.size() < MIN_TRAINING_IMAGES) {
            throw new NotEnoughDataException(training.size());
        }

        TagModel model = TagModel.train(training, MIN_POSITIVES);
        List<Suggestion> suggestions = model.suggest(features(path), current.tags(), MAX_SUGGESTIONS, THRESHOLD);
        ModelEvaluation.Report quality = ModelEvaluation.leaveOneOut(training, MIN_POSITIVES);
        return new Advice(suggestions, training.size(), quality.evaluatedImages() == 0 ? null : quality);
    }

    private double[] features(String path) throws IOException {
        double[] cached = featureCache.get(path);
        if (cached == null) {
            cached = ImageFeatures.extract(loader.load(path));
            featureCache.put(path, cached);
        }
        return cached;
    }

    /** Pas assez d'images taguées pour entraîner un modèle utile. */
    public static final class NotEnoughDataException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        NotEnoughDataException(int available) {
            super("Il faut au moins " + MIN_TRAINING_IMAGES + " autres images taguées pour apprendre (il y en a "
                    + available + "). Ajoutez des tags à d'autres images.");
        }
    }
}
