package fr.ndiaye.imagelib.ml;

import fr.ndiaye.imagelib.model.Tags;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Modèle qui <b>apprend à reconnaître les tags</b> de l'utilisateur à partir d'images déjà taguées.
 * <p>
 * C'est une classification multi-étiquettes « un contre tous » : pour chaque tag, une
 * <b>régression logistique</b> distincte répond à « cette image porte-t-elle ce tag ? » avec une probabilité.
 * <pre>
 *   p(tag | image) = sigmoïde( b + w · x )         x = caractéristiques standardisées de l'image
 * </pre>
 * Les poids w et b sont appris par <b>descente de gradient</b> sur la perte logistique (entropie croisée),
 * avec une régularisation L2 (pour éviter de « retenir par cœur » quand il y a peu d'images) et des
 * exemples pondérés pour que les tags rares ne soient pas écrasés par les tags fréquents.
 * <p>
 * Tout est implémenté ici, sans bibliothèque : le code se lit en entier.
 */
public final class TagModel {

    /** Une image d'entraînement : ses caractéristiques et les tags que l'utilisateur lui a donnés. */
    public record Example(double[] features, List<String> tags) {
    }

    /** Un tag proposé et la probabilité que le modèle lui attribue (entre 0 et 1). */
    public record Suggestion(String tag, double probability) {
    }

    static final int EPOCHS = 250;
    static final double LEARNING_RATE = 0.3;
    static final double REGULARISATION = 0.02;

    private final double[] mean;
    private final double[] deviation;
    private final Map<String, double[]> weights;     // une ligne par tag : w[0..d-1], puis le biais b
    private final Map<String, String> display;       // clé standardisée (sans accent ni casse) → orthographe d'origine

    private TagModel(double[] mean, double[] deviation, Map<String, double[]> weights, Map<String, String> display) {
        this.mean = mean;
        this.deviation = deviation;
        this.weights = weights;
        this.display = display;
    }

    /**
     * @param minPositives nombre minimal d'images portant un tag pour qu'on tente de l'apprendre
     *                     (un tag vu une seule fois ne permet pas de généraliser)
     */
    public static TagModel train(List<Example> examples, int minPositives) {
        int n = examples.size();
        if (n == 0) {
            return new TagModel(new double[0], new double[0], Map.of(), Map.of());
        }
        int d = examples.get(0).features().length;
        for (Example e : examples) {
            if (e.features().length != d) {
                throw new IllegalArgumentException("Toutes les images doivent avoir le même nombre de caractéristiques");
            }
        }

        // Standardisation : moyenne 0 et écart-type 1 pour chaque caractéristique (sinon l'une d'elles domine les autres).
        double[] mean = new double[d];
        double[] deviation = new double[d];
        for (Example e : examples) {
            for (int j = 0; j < d; j++) {
                mean[j] += e.features()[j] / n;
            }
        }
        for (Example e : examples) {
            for (int j = 0; j < d; j++) {
                double diff = e.features()[j] - mean[j];
                deviation[j] += diff * diff / n;
            }
        }
        for (int j = 0; j < d; j++) {
            deviation[j] = Math.sqrt(deviation[j]);
            if (deviation[j] < 1e-9) {
                deviation[j] = 1;           // caractéristique constante : elle n'apporte rien, on évite la division par 0
            }
        }
        double[][] x = new double[n][];
        for (int i = 0; i < n; i++) {
            x[i] = standardise(examples.get(i).features(), mean, deviation);
        }

        // Quels tags apprendre, et quelles images les portent
        Map<String, String> display = new LinkedHashMap<>();
        Map<String, boolean[]> labels = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            for (String tag : examples.get(i).tags()) {
                String key = Tags.fold(tag);
                display.putIfAbsent(key, tag);
                labels.computeIfAbsent(key, k -> new boolean[n])[i] = true;
            }
        }
        Map<String, double[]> weights = new LinkedHashMap<>();
        for (Map.Entry<String, boolean[]> entry : labels.entrySet()) {
            int positives = 0;
            for (boolean b : entry.getValue()) {
                positives += b ? 1 : 0;
            }
            if (positives >= minPositives && positives < n) {      // un tag présent partout n'a rien à apprendre
                weights.put(entry.getKey(), fit(x, entry.getValue(), positives));
            }
        }
        display.keySet().retainAll(weights.keySet());
        return new TagModel(mean, deviation, weights, display);
    }

    /** Descente de gradient sur la perte logistique pondérée, avec régularisation L2. */
    private static double[] fit(double[][] x, boolean[] y, int positives) {
        int n = x.length;
        int d = x[0].length;
        double[] w = new double[d + 1];                           // w[d] = biais
        double positiveWeight = n / (2.0 * positives);            // équilibre les classes : tags rares ≈ tags fréquents
        double negativeWeight = n / (2.0 * (n - positives));
        double[] gradient = new double[d + 1];
        for (int epoch = 0; epoch < EPOCHS; epoch++) {
            java.util.Arrays.fill(gradient, 0);
            for (int i = 0; i < n; i++) {
                double z = w[d];
                for (int j = 0; j < d; j++) {
                    z += w[j] * x[i][j];
                }
                double error = (sigmoid(z) - (y[i] ? 1 : 0)) * (y[i] ? positiveWeight : negativeWeight);
                for (int j = 0; j < d; j++) {
                    gradient[j] += error * x[i][j];
                }
                gradient[d] += error;
            }
            for (int j = 0; j < d; j++) {
                w[j] -= LEARNING_RATE * (gradient[j] / n + REGULARISATION * w[j]);
            }
            w[d] -= LEARNING_RATE * gradient[d] / n;               // le biais n'est pas régularisé
        }
        return w;
    }

    /** Probabilité de chaque tag appris pour cette image, du plus probable au moins probable. */
    public List<Suggestion> predict(double[] features) {
        List<Suggestion> result = new ArrayList<>();
        if (weights.isEmpty()) {
            return result;
        }
        double[] x = standardise(features, mean, deviation);
        for (Map.Entry<String, double[]> entry : weights.entrySet()) {
            double[] w = entry.getValue();
            double z = w[x.length];
            for (int j = 0; j < x.length; j++) {
                z += w[j] * x[j];
            }
            result.add(new Suggestion(display.get(entry.getKey()), sigmoid(z)));
        }
        result.sort(Comparator.comparingDouble(Suggestion::probability).reversed());
        return result;
    }

    /** Les tags à proposer : probabilité d'au moins {@code threshold}, hors tags que l'image porte déjà. */
    public List<Suggestion> suggest(double[] features, Collection<String> alreadyPresent, int max, double threshold) {
        Set<String> present = new HashSet<>();
        for (String tag : alreadyPresent) {
            present.add(Tags.fold(tag));
        }
        return predict(features).stream()
                .filter(s -> s.probability() >= threshold && !present.contains(Tags.fold(s.tag())))
                .limit(max)
                .toList();
    }

    /** Tags que ce modèle a pu apprendre. */
    public Set<String> learnedTags() {
        return new HashSet<>(display.values());
    }

    private static double[] standardise(double[] features, double[] mean, double[] deviation) {
        double[] out = new double[features.length];
        for (int j = 0; j < out.length; j++) {
            out[j] = (features[j] - mean[j]) / deviation[j];
        }
        return out;
    }

    private static double sigmoid(double z) {
        return 1.0 / (1.0 + Math.exp(-Math.max(-35, Math.min(35, z))));
    }
}
