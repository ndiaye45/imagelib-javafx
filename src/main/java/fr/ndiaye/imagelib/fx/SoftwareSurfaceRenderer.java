package fr.ndiaye.imagelib.fx;

import fr.ndiaye.imagelib.surface.SurfaceMesh;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

import java.util.Arrays;

/**
 * Rendu de secours d'une surface quand la 3D matérielle de JavaFX n'est pas disponible
 * (machine virtuelle, WSL, bureau à distance, pilote graphique absent) : projection en perspective
 * et « algorithme du peintre » (triangles lointains dessinés en premier) sur un simple {@code Canvas} 2D.
 * <p>
 * Les maths reproduisent la scène 3D : rotation autour de Y puis de X, caméra à la distance donnée,
 * champ de vision de 32°. Le résultat est donc comparable à celui de la vue {@code MeshView}.
 */
final class SoftwareSurfaceRenderer {

    private static final double FIELD_OF_VIEW = Math.toRadians(32);

    private SoftwareSurfaceRenderer() {
    }

    static void render(GraphicsContext gc, double width, double height, SurfaceMesh mesh,
                       double yawDegrees, double pitchDegrees, double distance, boolean wireframe, Color[] palette) {
        gc.setFill(Color.web("#0a0f1c"));
        gc.fillRect(0, 0, width, height);
        if (mesh == null) {
            return;
        }
        int n = mesh.vertexCount();
        float[] points = mesh.points();
        double yaw = Math.toRadians(yawDegrees);
        double pitch = Math.toRadians(pitchDegrees);
        double cy = Math.cos(yaw), sy = Math.sin(yaw), cp = Math.cos(pitch), sp = Math.sin(pitch);
        double focal = (height / 2) / Math.tan(FIELD_OF_VIEW / 2);

        double[] sx = new double[n];
        double[] sY = new double[n];
        float[] depth = new float[n];
        for (int v = 0; v < n; v++) {
            double x = points[3 * v], y = points[3 * v + 1], z = points[3 * v + 2];
            double x1 = x * cy + z * sy;
            double z1 = -x * sy + z * cy;
            double y2 = y * cp - z1 * sp;
            double z2 = y * sp + z1 * cp;
            double d = distance + z2;                     // distance entre la caméra et le point
            depth[v] = (float) d;
            sx[v] = width / 2 + x1 * focal / d;
            sY[v] = height / 2 + y2 * focal / d;
        }

        // Clé de tri : profondeur du triangle (en bits, valable car positive) dans les 32 bits de poids fort.
        int[] faces = mesh.faces();
        int triangles = mesh.triangleCount();
        long[] keys = new long[triangles];
        int visible = 0;
        for (int t = 0; t < triangles; t++) {
            float a = depth[faces[6 * t]], b = depth[faces[6 * t + 2]], c = depth[faces[6 * t + 4]];
            if (a <= 1 || b <= 1 || c <= 1) {
                continue;                                   // derrière la caméra
            }
            keys[visible++] = (long) Float.floatToIntBits((a + b + c) / 3f) << 32 | t;
        }
        Arrays.sort(keys, 0, visible);

        float[] tex = mesh.texCoords();
        double[] xs = new double[3];
        double[] ys = new double[3];
        gc.setLineWidth(wireframe ? 0.7 : 0.8);
        for (int k = visible - 1; k >= 0; k--) {           // du plus lointain au plus proche
            int t = (int) (keys[k] & 0xFFFFFFFFL);
            float u = 0;
            for (int i = 0; i < 3; i++) {
                int v = faces[6 * t + 2 * i];
                xs[i] = sx[v];
                ys[i] = sY[v];
                u += tex[2 * v] / 3f;
            }
            Color colour = palette[Math.min(palette.length - 1, Math.max(0, Math.round(u * (palette.length - 1))))];
            if (wireframe) {
                gc.setStroke(colour);
                gc.strokePolygon(xs, ys, 3);
            } else {
                gc.setFill(colour);
                gc.setStroke(colour);                       // contour de même couleur : évite les fines coutures
                gc.fillPolygon(xs, ys, 3);
                gc.strokePolygon(xs, ys, 3);
            }
        }
    }
}
