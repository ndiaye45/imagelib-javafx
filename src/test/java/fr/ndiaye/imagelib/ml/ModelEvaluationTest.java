package fr.ndiaye.imagelib.ml;

import fr.ndiaye.imagelib.ml.ModelEvaluation.Report;
import fr.ndiaye.imagelib.ml.TagModel.Example;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class ModelEvaluationTest {

    @Test
    void modelBeatsTheMostFrequentTagsBaselineOnLearnableData() {
        Random random = new Random(21);
        List<Example> examples = new ArrayList<>();
        for (int kind = 0; kind < Scenes.NAMES.length; kind++) {
            for (int i = 0; i < 6; i++) {
                examples.add(new Example(ImageFeatures.extract(Scenes.scene(kind, random)), List.of(Scenes.NAMES[kind])));
            }
        }
        Report report = ModelEvaluation.leaveOneOut(examples, 2);
        assertEquals(examples.size(), report.evaluatedImages());
        assertTrue(report.modelHitAt3() >= 0.95, "modèle : " + report.modelHitAt3());
        assertTrue(report.baselineHitAt3() <= 0.6, "base de référence : " + report.baselineHitAt3());
        assertTrue(report.beatsBaseline());
    }

    @Test
    void doesNotInventSkillOnPureNoise() {
        // Tags attribués au hasard, sans lien avec l'image : le modèle ne doit pas battre nettement le hasard.
        Random random = new Random(5);
        List<Example> examples = new ArrayList<>();
        for (int i = 0; i < 36; i++) {
            examples.add(new Example(ImageFeatures.extract(Scenes.scene(random.nextInt(6), random)),
                    List.of(Scenes.NAMES[random.nextInt(6)])));
        }
        Report report = ModelEvaluation.leaveOneOut(examples, 2);
        assertTrue(report.modelHitAt3() < report.baselineHitAt3() + 0.25,
                "un modèle qui bat la base de référence sur du bruit signalerait une fuite de données : " + report);
    }

    @Test
    void emptyOrUnlearnableDataGivesAnEmptyReport() {
        assertEquals(0, ModelEvaluation.leaveOneOut(List.of(), 2).evaluatedImages());
        List<Example> unique = List.of(new Example(new double[ImageFeatures.DIMENSIONS], List.of("a")),
                new Example(new double[ImageFeatures.DIMENSIONS], List.of("b")));
        assertEquals(0, ModelEvaluation.leaveOneOut(unique, 2).evaluatedImages());
    }
}
