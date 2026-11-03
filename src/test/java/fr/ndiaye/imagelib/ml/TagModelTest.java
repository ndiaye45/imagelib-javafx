package fr.ndiaye.imagelib.ml;

import fr.ndiaye.imagelib.ml.TagModel.Example;
import fr.ndiaye.imagelib.ml.TagModel.Suggestion;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class TagModelTest {

    /** perClass images par scène ; chaque image porte le tag de sa scène et « extérieur » (présent partout). */
    private static List<Example> dataset(int perClass, long seed) {
        Random random = new Random(seed);
        List<Example> examples = new ArrayList<>();
        for (int kind = 0; kind < Scenes.NAMES.length; kind++) {
            for (int i = 0; i < perClass; i++) {
                examples.add(new Example(ImageFeatures.extract(Scenes.scene(kind, random)),
                        List.of(Scenes.NAMES[kind], "extérieur")));
            }
        }
        return examples;
    }

    @Test
    void learnsToRecogniseEachSceneOnImagesItNeverSaw() {
        TagModel model = TagModel.train(dataset(6, 1), 2);
        Random fresh = new Random(999);
        for (int kind = 0; kind < Scenes.NAMES.length; kind++) {
            List<Suggestion> predicted = model.predict(ImageFeatures.extract(Scenes.scene(kind, fresh)));
            assertEquals(Scenes.NAMES[kind], predicted.get(0).tag(), "scène " + Scenes.NAMES[kind]);
            assertTrue(predicted.get(0).probability() > 0.5);
        }
    }

    @Test
    void probabilitiesAreBetweenZeroAndOneAndSortedDescending() {
        TagModel model = TagModel.train(dataset(5, 2), 2);
        List<Suggestion> predicted = model.predict(ImageFeatures.extract(Scenes.scene(0, new Random(5))));
        for (int i = 0; i < predicted.size(); i++) {
            assertTrue(predicted.get(i).probability() >= 0 && predicted.get(i).probability() <= 1);
            if (i > 0) {
                assertTrue(predicted.get(i - 1).probability() >= predicted.get(i).probability());
            }
        }
    }

    @Test
    void suggestionsLeaveOutTagsTheImageAlreadyHasEvenWithDifferentSpelling() {
        TagModel model = TagModel.train(dataset(6, 3), 2);
        double[] sea = ImageFeatures.extract(Scenes.scene(0, new Random(11)));
        assertEquals("mer", model.suggest(sea, List.of(), 3, 0.5).get(0).tag());
        assertTrue(model.suggest(sea, List.of("MER"), 3, 0.5).stream().noneMatch(s -> s.tag().equals("mer")));
        assertTrue(model.suggest(sea, List.of(), 3, 0.999999).size() <= 3);
    }

    @Test
    void tagsThatCannotBeLearnedAreSkipped() {
        List<Example> examples = new ArrayList<>(dataset(4, 4));
        examples.add(new Example(ImageFeatures.extract(Scenes.scene(0, new Random(1))), List.of("mer", "unique", "extérieur")));
        TagModel model = TagModel.train(examples, 2);
        assertFalse(model.learnedTags().contains("unique"), "un tag vu une seule fois ne se généralise pas");
        assertFalse(model.learnedTags().contains("extérieur"), "un tag présent sur toutes les images n'apprend rien");
        assertTrue(model.learnedTags().contains("mer"));
    }

    @Test
    void emptyTrainingSetGivesNoSuggestion() {
        TagModel model = TagModel.train(List.of(), 2);
        assertTrue(model.predict(new double[ImageFeatures.DIMENSIONS]).isEmpty());
        assertTrue(model.learnedTags().isEmpty());
    }

    @Test
    void inconsistentFeatureVectorsAreRejected() {
        List<Example> bad = List.of(new Example(new double[3], List.of("a")), new Example(new double[4], List.of("a")));
        assertThrows(IllegalArgumentException.class, () -> TagModel.train(bad, 1));
    }

    @Test
    void trainingIsDeterministic() {
        double[] probe = ImageFeatures.extract(Scenes.scene(2, new Random(77)));
        assertEquals(TagModel.train(dataset(5, 6), 2).predict(probe), TagModel.train(dataset(5, 6), 2).predict(probe));
    }
}
