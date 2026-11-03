package fr.ndiaye.imagelib.model;

import java.util.Arrays;
import java.util.function.IntUnaryOperator;

/**
 * Image en mémoire : largeur, hauteur et pixels au format ARGB (un entier par pixel).
 * <p>
 * Cette classe ne dépend pas de JavaFX : toute la logique de transformation peut donc
 * être testée sans interface graphique. Par convention, une transformation ne modifie
 * jamais son entrée : elle renvoie un nouveau {@code Raster}.
 */
public record Raster(int width, int height, int[] argb) {

    public Raster {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Dimensions invalides : " + width + " x " + height);
        }
        if (argb == null || argb.length != width * height) {
            throw new IllegalArgumentException("Le nombre de pixels ne correspond pas aux dimensions");
        }
    }

    public static Raster blank(int width, int height) {
        return new Raster(width, height, new int[width * height]);
    }

    public int get(int x, int y) {
        return argb[y * width + x];
    }

    /** Applique une fonction à chaque pixel et renvoie le résultat dans un nouveau raster. */
    public Raster map(IntUnaryOperator operator) {
        int[] out = new int[argb.length];
        for (int i = 0; i < out.length; i++) {
            out[i] = operator.applyAsInt(argb[i]);
        }
        return new Raster(width, height, out);
    }

    // Un record compare ses tableaux par référence : on compare le contenu.
    @Override
    public boolean equals(Object o) {
        return o instanceof Raster other
                && width == other.width
                && height == other.height
                && Arrays.equals(argb, other.argb);
    }

    @Override
    public int hashCode() {
        return 31 * (31 * width + height) + Arrays.hashCode(argb);
    }

    @Override
    public String toString() {
        return "Raster[" + width + "x" + height + "]";
    }
}
