package fr.ndiaye.imagelib.transform;

/** Petites fonctions pour manipuler un pixel ARGB codé sur un entier. */
final class Argb {

    private Argb() {
    }

    static int alpha(int pixel) {
        return pixel >>> 24;
    }

    static int red(int pixel) {
        return (pixel >> 16) & 0xFF;
    }

    static int green(int pixel) {
        return (pixel >> 8) & 0xFF;
    }

    static int blue(int pixel) {
        return pixel & 0xFF;
    }

    static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }

    static int pack(int alpha, int red, int green, int blue) {
        return (clamp(alpha) << 24) | (clamp(red) << 16) | (clamp(green) << 8) | clamp(blue);
    }
}
