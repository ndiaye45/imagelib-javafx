package fr.ndiaye.imagelib.transform;

import fr.ndiaye.imagelib.model.Raster;
import fr.ndiaye.imagelib.model.TransformationSpec;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransformationFactoryTest {

    private static final List<ImageTransformation> ALL = List.of(
            new Rotation(90), new Rotation(270), new Mirror(), new Grayscale(),
            new Sepia(), new Invert(), new Brightness(15.5));

    @Test
    void everyTransformationSurvivesASpecRoundTrip() {
        for (ImageTransformation transformation : ALL) {
            ImageTransformation rebuilt = TransformationFactory.create(transformation.toSpec());

            assertEquals(transformation, rebuilt);
        }
    }

    @Test
    void allKnownTypesAreCovered() {
        List<String> covered = ALL.stream().map(ImageTransformation::type).distinct().toList();

        assertTrue(covered.containsAll(TransformationFactory.knownTypes()));
    }

    @Test
    void unknownTypeIsRejected() {
        TransformationSpec spec = TransformationSpec.of("hologramme");

        IllegalArgumentException error =
                assertThrows(IllegalArgumentException.class, () -> TransformationFactory.create(spec));
        assertTrue(error.getMessage().contains("hologramme"));
    }

    @Test
    void missingOrInvalidParametersAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> TransformationFactory.create(TransformationSpec.of("rotation")));
        assertThrows(IllegalArgumentException.class,
                () -> TransformationFactory.create(new TransformationSpec("rotation", Map.of("angle", 45.0))));
        assertThrows(IllegalArgumentException.class,
                () -> TransformationFactory.create(new TransformationSpec("rotation", Map.of("angle", 90.5))));
        assertThrows(IllegalArgumentException.class,
                () -> TransformationFactory.create(TransformationSpec.of("brightness")));
    }

    @Test
    void labelsAreReadableInFrench() {
        assertEquals("Rotation 90° vers la droite", TransformationFactory.label(new Rotation(90).toSpec()));
        assertEquals("Rotation 90° vers la gauche", TransformationFactory.label(new Rotation(270).toSpec()));
        assertEquals("Noir et blanc", TransformationFactory.label(new Grayscale().toSpec()));
        assertEquals("Sépia", TransformationFactory.label(new Sepia().toSpec()));
        assertEquals("Luminosité +20 %", TransformationFactory.label(new Brightness(20).toSpec()));
        assertEquals("Luminosité -10 %", TransformationFactory.label(new Brightness(-10).toSpec()));
    }

    @Test
    void specParametersAreSortedAndImmutable() {
        TransformationSpec spec = new TransformationSpec("x", Map.of("b", 1.0, "a", 2.0));

        assertEquals(List.of("a", "b"), List.copyOf(spec.params().keySet()));
        assertThrows(UnsupportedOperationException.class, () -> spec.params().put("c", 3.0));
    }

    @Test
    void pipelineAppliesTransformationsInTheRecordedOrder() {
        Raster source = new Raster(2, 1, new int[]{0xFF000001, 0xFF000002});
        List<TransformationSpec> mirrorThenRotate = List.of(new Mirror().toSpec(), new Rotation(90).toSpec());
        List<TransformationSpec> rotateThenMirror = List.of(new Rotation(90).toSpec(), new Mirror().toSpec());

        Raster first = TransformationPipeline.apply(source, mirrorThenRotate);
        Raster second = TransformationPipeline.apply(source, rotateThenMirror);

        // L'ordre compte : les deux résultats ont la même taille mais pas les mêmes pixels.
        assertEquals(1, first.width());
        assertEquals(2, first.height());
        assertEquals(new Raster(1, 2, new int[]{0xFF000002, 0xFF000001}), first);
        assertEquals(new Raster(1, 2, new int[]{0xFF000001, 0xFF000002}), second);
    }

    @Test
    void emptyHistoryReturnsTheSourceUnchanged() {
        Raster source = new Raster(1, 1, new int[]{0xFF123456});

        assertEquals(source, TransformationPipeline.apply(source, List.of()));
    }
}
