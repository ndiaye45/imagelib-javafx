package fr.ndiaye.imagelib.ml;

import fr.ndiaye.imagelib.model.Raster;

import java.util.Random;

/** Images synthétiques pour les tests : un ciel (haut) et un sol (bas) de couleurs bruitées. */
final class Scenes {

    static final String[] NAMES = {"mer", "forêt", "désert", "nuit", "neige", "coucher"};
    private static final int[][] COLOURS = {
            {0x87CEEB, 0x1E50C8},   // mer : ciel bleu clair, eau bleue
            {0x9ACD9B, 0x1F5A2A},   // forêt : ciel pâle, feuillage vert foncé
            {0xFFD28A, 0xD2842F},   // désert : ciel et sable orangés
            {0x0A0A28, 0x050510},   // nuit : tout sombre
            {0xF0F4FF, 0xFFFFFF},   // neige : tout blanc
            {0x502050, 0xFF7030},   // coucher de soleil : ciel violet, horizon orange
    };

    private Scenes() {
    }

    static Raster scene(int kind, Random random, int width, int height) {
        int[] px = new int[width * height];
        for (int y = 0; y < height; y++) {
            int base = COLOURS[kind][y < height / 2 ? 0 : 1];
            for (int x = 0; x < width; x++) {
                int r = clamp((base >> 16 & 0xFF) + random.nextInt(31) - 15);
                int g = clamp((base >> 8 & 0xFF) + random.nextInt(31) - 15);
                int b = clamp((base & 0xFF) + random.nextInt(31) - 15);
                px[y * width + x] = 0xFF000000 | r << 16 | g << 8 | b;
            }
        }
        return new Raster(width, height, px);
    }

    static Raster scene(int kind, Random random) {
        return scene(kind, random, 80, 60);
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }
}
