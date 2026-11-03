package fr.ndiaye.imagelib.surface;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.*;

class SurfaceMeshTest {

    @Test
    void gridHasTheExpectedNumberOfVerticesAndTriangles() {
        SurfaceMesh mesh = SurfaceMesh.build(SurfaceFunction.SELLE, 10, 300, 100);
        assertEquals(11 * 11, mesh.vertexCount());
        assertEquals(10 * 10 * 2, mesh.triangleCount());
        assertEquals(mesh.vertexCount() * 2, mesh.texCoords().length);
    }

    @ParameterizedTest
    @EnumSource(SurfaceFunction.class)
    void everySurfaceProducesValidFiniteMeshes(SurfaceFunction surface) {
        SurfaceMesh mesh = SurfaceMesh.build(surface, 40, 300, 100);
        for (float p : mesh.points()) {
            assertTrue(Float.isFinite(p), surface + " : coordonnée non finie");
        }
        for (float t : mesh.texCoords()) {
            assertTrue(t >= 0f && t <= 1f, surface + " : coordonnée de texture hors [0,1]");
        }
        for (int index : mesh.faces()) {
            assertTrue(index >= 0 && index < mesh.vertexCount(), surface + " : indice de sommet invalide");
        }
        assertTrue(mesh.zMin() < mesh.zMax());
    }

    @Test
    void heightIsScaledToTheRequestedAmplitudeWithoutDistortion() {
        SurfaceMesh mesh = SurfaceMesh.build(SurfaceFunction.SELLE, 40, 300, 100);   // x² − y² : de −4 à 4
        float lowest = Float.POSITIVE_INFINITY;
        float highest = Float.NEGATIVE_INFINITY;
        for (int v = 0; v < mesh.vertexCount(); v++) {
            float y = mesh.points()[3 * v + 1];
            lowest = Math.min(lowest, y);
            highest = Math.max(highest, y);
        }
        assertEquals(-100f, lowest, 1e-3);   // JavaFX : le haut est négatif, donc le maximum de f est à y = −100
        assertEquals(100f, highest, 1e-3);
        assertEquals(-4.0, mesh.zMin(), 1e-9);
        assertEquals(4.0, mesh.zMax(), 1e-9);
    }

    @Test
    void colourFollowsTheHeight() {
        SurfaceMesh mesh = SurfaceMesh.build(SurfaceFunction.SELLE, 20, 300, 100);
        float min = Float.POSITIVE_INFINITY;
        float max = Float.NEGATIVE_INFINITY;
        for (int v = 0; v < mesh.vertexCount(); v++) {
            float u = mesh.texCoords()[2 * v];
            min = Math.min(min, u);
            max = Math.max(max, u);
        }
        assertEquals(0f, min, 1e-6);
        assertEquals(1f, max, 1e-6);
    }

    @Test
    void singularityAtTheOriginDoesNotBreakTheWavesSurface() {
        // sin(r)/r vaut 0/0 en (0, 0) : la limite (1) doit être utilisée.
        assertEquals(1.0, SurfaceFunction.ONDES.apply(0, 0), 1e-12);
        SurfaceMesh mesh = SurfaceMesh.build(SurfaceFunction.ONDES, 24, 300, 100);   // la grille passe par l'origine
        assertEquals(1.0, mesh.zMax(), 1e-12);
    }

    @Test
    void nonFiniteValuesAreReplacedInsteadOfPoisoningTheMesh() {
        SurfaceMesh mesh = SurfaceMesh.build(SurfaceFunction.GAUSSIENNES, 10, 300, 100);
        assertTrue(Double.isFinite(mesh.zMin()) && Double.isFinite(mesh.zMax()));
    }

    @Test
    void resolutionOutOfBoundsIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> SurfaceMesh.build(SurfaceFunction.SELLE, 1, 300, 100));
        assertThrows(IllegalArgumentException.class, () -> SurfaceMesh.build(SurfaceFunction.SELLE, 5000, 300, 100));
    }

    @Test
    void trianglesCoverEachCellExactlyOnce() {
        SurfaceMesh mesh = SurfaceMesh.build(SurfaceFunction.VAGUES, 6, 300, 100);
        int[] usage = new int[mesh.vertexCount()];
        for (int t = 0; t < mesh.triangleCount(); t++) {
            for (int k = 0; k < 3; k++) {
                usage[mesh.faces()[6 * t + 2 * k]]++;
            }
        }
        // Un sommet intérieur appartient à 6 triangles ; un coin, à 1 ou 2.
        int side = 7;
        assertEquals(6, usage[3 * side + 3]);
        assertTrue(usage[0] <= 2 && usage[0] >= 1);
    }
}
