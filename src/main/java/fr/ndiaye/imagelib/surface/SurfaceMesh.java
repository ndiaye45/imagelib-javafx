package fr.ndiaye.imagelib.surface;

/**
 * Maillage triangulaire d'une surface z = f(x, y), prêt à être donné à un {@code TriangleMesh} JavaFX.
 * <p>
 * Tout le calcul (échantillonnage, normalisation des couleurs, triangulation) est ici, sans JavaFX :
 * la vue n'a plus qu'à recopier les tableaux, et le calcul se teste comme n'importe quelle fonction.
 *
 * @param points     x, y, z de chaque sommet, à plat (JavaFX : y vers le bas, donc la hauteur est négative)
 * @param texCoords  u, v de chaque sommet : u = hauteur normalisée dans [0, 1] (indexe une palette de couleurs)
 * @param faces      pour chaque triangle, 3 paires (sommet, coordonnée de texture) — même indice pour les deux
 * @param cells      nombre de cellules par côté de la grille ; il y a (cells + 1)² sommets
 * @param zMin       hauteur minimale réelle de la fonction (pour la légende)
 * @param zMax       hauteur maximale réelle de la fonction
 */
public record SurfaceMesh(float[] points, float[] texCoords, int[] faces, int cells, double zMin, double zMax) {

    public static final int MIN_CELLS = 2;
    public static final int MAX_CELLS = 400;

    /**
     * @param size        côté de la surface dans la scène (unités JavaFX)
     * @param heightScale hauteur de la surface dans la scène : la plus grande hauteur réelle occupe cette valeur
     */
    public static SurfaceMesh build(SurfaceFunction surface, int cells, float size, float heightScale) {
        if (cells < MIN_CELLS || cells > MAX_CELLS) {
            throw new IllegalArgumentException("Résolution hors limites [" + MIN_CELLS + ", " + MAX_CELLS + "] : " + cells);
        }
        int side = cells + 1;
        double[] z = new double[side * side];
        double lo = Double.POSITIVE_INFINITY;
        double hi = Double.NEGATIVE_INFINITY;
        for (int j = 0; j < side; j++) {
            for (int i = 0; i < side; i++) {
                double x = lerp(surface.xMin(), surface.xMax(), i, cells);
                double y = lerp(surface.yMin(), surface.yMax(), j, cells);
                double value = surface.apply(x, y);
                if (!Double.isFinite(value)) {
                    value = 0;                           // une singularité ne doit pas casser tout le maillage
                }
                z[j * side + i] = value;
                lo = Math.min(lo, value);
                hi = Math.max(hi, value);
            }
        }
        double range = hi - lo;
        boolean flat = range < 1e-12;
        // Même échelle verticale pour tous les points : on ne déforme pas la surface.
        double amplitude = Math.max(Math.abs(lo), Math.abs(hi));
        double scale = amplitude < 1e-12 ? 0 : heightScale / amplitude;

        float[] points = new float[side * side * 3];
        float[] tex = new float[side * side * 2];
        for (int j = 0; j < side; j++) {
            for (int i = 0; i < side; i++) {
                int v = j * side + i;
                points[3 * v] = (float) (lerp(-size / 2.0, size / 2.0, i, cells));
                points[3 * v + 1] = (float) (-z[v] * scale);
                points[3 * v + 2] = (float) (lerp(-size / 2.0, size / 2.0, j, cells));
                tex[2 * v] = flat ? 0.5f : (float) ((z[v] - lo) / range);
                tex[2 * v + 1] = 0.5f;
            }
        }

        int[] faces = new int[cells * cells * 2 * 6];
        int f = 0;
        for (int j = 0; j < cells; j++) {
            for (int i = 0; i < cells; i++) {
                int a = j * side + i;          // coin (i, j)
                int b = a + 1;                 // (i + 1, j)
                int c = a + side;              // (i, j + 1)
                int d = c + 1;                 // (i + 1, j + 1)
                f = triangle(faces, f, a, c, b);
                f = triangle(faces, f, b, c, d);
            }
        }
        return new SurfaceMesh(points, tex, faces, cells, lo, hi);
    }

    public int vertexCount() {
        return points.length / 3;
    }

    public int triangleCount() {
        return faces.length / 6;
    }

    private static int triangle(int[] faces, int at, int p, int q, int r) {
        faces[at++] = p; faces[at++] = p;
        faces[at++] = q; faces[at++] = q;
        faces[at++] = r; faces[at++] = r;
        return at;
    }

    private static double lerp(double from, double to, int step, int steps) {
        return from + (to - from) * step / steps;
    }
}
