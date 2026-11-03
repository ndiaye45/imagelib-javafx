package fr.ndiaye.imagelib.ml;

import fr.ndiaye.imagelib.ml.TagModel.Example;
import fr.ndiaye.imagelib.model.Tags;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

/**
 * Mesure honnêtement si le modèle apprend quelque chose, par <b>validation croisée « leave-one-out »</b> :
 * pour chaque image, on entraîne un modèle sur toutes les <i>autres</i> images, puis on lui demande de deviner
 * les tags de celle qu'il n'a jamais vue. Mesurer sur les images d'entraînement ne prouverait rien.
 * <p>
 * Métrique « hit@3 » : part des images pour lesquelles au moins un des 3 tags les plus probables est un vrai tag.
 * Elle est comparée à une <b>base de référence</b> naïve (proposer toujours les 3 tags les plus fréquents) :
 * si le modèle ne la bat pas, il n'apporte rien.
 */
public final class ModelEvaluation {

    /** Résultat de la validation croisée ; les deux taux sont entre 0 et 1. */
    public record Report(int evaluatedImages, double modelHitAt3, double baselineHitAt3) {
        public boolean beatsBaseline() {
            return modelHitAt3 > baselineHitAt3;
        }
    }

    private static final int TOP = 3;

    private ModelEvaluation() {
    }

    public static Report leaveOneOut(List<Example> examples, int minPositives) {
        int n = examples.size();
        // Les validations sont indépendantes : on les répartit sur les cœurs du processeur.
        int[] outcome = IntStream.range(0, n).parallel().map(held -> evaluateFold(examples, held, minPositives)).toArray();
        int evaluated = 0;
        int modelHits = 0;
        int baselineHits = 0;
        for (int o : outcome) {
            if (o >= 0) {                    // -1 : image non évaluable (aucun de ses tags n'était apprenable)
                evaluated++;
                modelHits += o & 1;
                baselineHits += o >> 1 & 1;
            }
        }
        return evaluated == 0
                ? new Report(0, 0, 0)
                : new Report(evaluated, modelHits / (double) evaluated, baselineHits / (double) evaluated);
    }

    /** Renvoie -1 si l'image n'est pas évaluable, sinon un entier : bit 0 = le modèle a trouvé, bit 1 = la base de référence a trouvé. */
    private static int evaluateFold(List<Example> examples, int held, int minPositives) {
        List<Example> training = new java.util.ArrayList<>(examples);
        Example test = training.remove(held);
        TagModel model = TagModel.train(training, minPositives);

        Set<String> learned = new HashSet<>();
        for (String tag : model.learnedTags()) {
            learned.add(Tags.fold(tag));
        }
        Set<String> truth = new HashSet<>();
        for (String tag : test.tags()) {
            if (learned.contains(Tags.fold(tag))) {
                truth.add(Tags.fold(tag));
            }
        }
        if (truth.isEmpty()) {
            return -1;
        }

        boolean modelHit = model.predict(test.features()).stream().limit(TOP)
                .anyMatch(s -> truth.contains(Tags.fold(s.tag())));

        Map<String, Integer> frequency = new HashMap<>();
        for (Example e : training) {
            for (String tag : new HashSet<>(e.tags().stream().map(Tags::fold).toList())) {
                if (learned.contains(tag)) {
                    frequency.merge(tag, 1, Integer::sum);
                }
            }
        }
        boolean baselineHit = frequency.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .limit(TOP)
                .anyMatch(e -> truth.contains(e.getKey()));
        return (modelHit ? 1 : 0) | (baselineHit ? 2 : 0);
    }
}
