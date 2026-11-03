package fr.ndiaye.imagelib.service;

import fr.ndiaye.imagelib.ml.ModelEvaluation;
import fr.ndiaye.imagelib.model.Raster;
import fr.ndiaye.imagelib.service.TagAdvisor.Advice;
import fr.ndiaye.imagelib.service.TagAdvisor.NotEnoughDataException;
import fr.ndiaye.imagelib.storage.MetadataDaos;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class TagAdvisorTest {

    private static final String[] KINDS = {"mer", "foret", "desert", "nuit"};
    private static final int[][] COLOURS = {{0x87CEEB, 0x1E50C8}, {0x9ACD9B, 0x1F5A2A}, {0xFFD28A, 0xD2842F}, {0x0A0A28, 0x050510}};

    private static Raster scene(int kind, Random random) {
        int w = 40, h = 30;
        int[] px = new int[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int base = COLOURS[kind][y < h / 2 ? 0 : 1];
                int r = Math.max(0, Math.min(255, (base >> 16 & 0xFF) + random.nextInt(21) - 10));
                int g = Math.max(0, Math.min(255, (base >> 8 & 0xFF) + random.nextInt(21) - 10));
                int b = Math.max(0, Math.min(255, (base & 0xFF) + random.nextInt(21) - 10));
                px[y * w + x] = 0xFF000000 | r << 16 | g << 8 | b;
            }
        }
        return new Raster(w, h, px);
    }

    /** Dossier de n images par catégorie ; les images « fichier » sont des fichiers vides, les pixels viennent du chargeur factice. */
    private record Fixture(ImageLibrary library, Map<String, Raster> rasters, AtomicInteger loads) {
        TagAdvisor advisor() {
            return new TagAdvisor(library, path -> {
                loads.incrementAndGet();
                Raster r = rasters.get(path);
                if (r == null) {
                    throw new IOException("illisible : " + path);
                }
                return r;
            });
        }
    }

    private static Fixture folder(Path dir, int perKind, boolean tagged) throws IOException {
        Random random = new Random(4);
        Map<String, Raster> rasters = new HashMap<>();
        for (int kind = 0; kind < KINDS.length; kind++) {
            for (int i = 0; i < perKind; i++) {
                String name = KINDS[kind] + "-" + i + ".png";
                Files.write(dir.resolve(name), new byte[]{1});
                rasters.put(name, scene(kind, random));
            }
        }
        ImageLibrary library = new ImageLibrary(dir, MetadataDaos.forFolder(dir));
        library.reload();
        if (tagged) {
            for (String name : rasters.keySet()) {
                library.addTag(name, name.substring(0, name.indexOf('-')));
            }
        }
        return new Fixture(library, rasters, new AtomicInteger());
    }

    @Test
    void suggestsTheRightTagForAnImageLearnedFromTheOthers(@TempDir Path dir) throws IOException {
        Fixture fixture = folder(dir, 6, true);
        fixture.library().removeTag("desert-0.png", "desert");            // image à « re-découvrir »

        Advice advice = fixture.advisor().advise("desert-0.png");

        assertEquals("desert", advice.suggestions().get(0).tag());
        assertEquals(4 * 6 - 1, advice.trainingImages());
        assertNotNull(advice.quality());
        assertTrue(advice.quality().beatsBaseline());
    }

    @Test
    void doesNotSuggestTagsTheImageAlreadyHas(@TempDir Path dir) throws IOException {
        Fixture fixture = folder(dir, 6, true);
        assertTrue(fixture.advisor().advise("mer-0.png").suggestions().isEmpty());
    }

    @Test
    void refusesToLearnFromTooFewTaggedImages(@TempDir Path dir) throws IOException {
        Fixture fixture = folder(dir, 6, false);
        fixture.library().addTag("mer-0.png", "mer");
        fixture.library().addTag("mer-1.png", "mer");
        NotEnoughDataException e = assertThrows(NotEnoughDataException.class, () -> fixture.advisor().advise("foret-0.png"));
        assertTrue(e.getMessage().contains("au moins"));
    }

    @Test
    void featuresAreComputedOnlyOncePerImage(@TempDir Path dir) throws IOException {
        Fixture fixture = folder(dir, 5, true);
        TagAdvisor advisor = fixture.advisor();
        advisor.advise("mer-0.png");
        int afterFirst = fixture.loads().get();
        advisor.advise("foret-0.png");
        assertEquals(afterFirst, fixture.loads().get(), "la deuxième analyse doit réutiliser les caractéristiques en cache");
        assertEquals(20, afterFirst);
    }

    @Test
    void unreadableImageIsReportedAsAnIOException(@TempDir Path dir) throws IOException {
        Fixture fixture = folder(dir, 6, true);
        fixture.rasters().remove("mer-3.png");
        assertThrows(IOException.class, () -> fixture.advisor().advise("mer-0.png"));
    }

    @Test
    void qualityReportIsAbsentWhenNothingCanBeEvaluated(@TempDir Path dir) throws IOException {
        // Chaque image a un tag qui lui est propre : rien n'est apprenable (un seul exemple par tag).
        Fixture fixture = folder(dir, 2, false);
        int n = 0;
        for (String name : fixture.rasters().keySet()) {
            fixture.library().addTag(name, "unique" + n++);
        }
        Advice advice = fixture.advisor().advise("mer-0.png");
        assertNull(advice.quality());
        assertTrue(advice.suggestions().isEmpty());
        assertEquals(ModelEvaluation.Report.class, ModelEvaluation.leaveOneOut(java.util.List.of(), 2).getClass());
    }
}
