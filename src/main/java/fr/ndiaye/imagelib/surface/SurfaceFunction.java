package fr.ndiaye.imagelib.surface;

import java.util.function.DoubleBinaryOperator;

/** Surfaces z = f(x, y) proposées dans la démo 3D, avec leur domaine d'étude. */
public enum SurfaceFunction {

    ONDES("Ondes  sin(r) / r", -12, 12, -12, 12,
            (x, y) -> {
                double r = Math.hypot(x, y);
                return r < 1e-9 ? 1.0 : Math.sin(r) / r;      // limite en 0 : évite 0/0
            }),
    SELLE("Selle de cheval  x² − y²", -2, 2, -2, 2,
            (x, y) -> x * x - y * y),
    VAGUES("Vagues  sin(x) · cos(y)", -2 * Math.PI, 2 * Math.PI, -2 * Math.PI, 2 * Math.PI,
            (x, y) -> Math.sin(x) * Math.cos(y)),
    GAUSSIENNES("Deux gaussiennes", -3, 3, -3, 3,
            (x, y) -> Math.exp(-((x - 1) * (x - 1) + (y - 1) * (y - 1)))
                    + 0.7 * Math.exp(-((x + 1.2) * (x + 1.2) + (y + 0.8) * (y + 0.8)) / 0.6));

    private final String label;
    private final double xMin;
    private final double xMax;
    private final double yMin;
    private final double yMax;
    private final DoubleBinaryOperator function;

    SurfaceFunction(String label, double xMin, double xMax, double yMin, double yMax, DoubleBinaryOperator function) {
        this.label = label;
        this.xMin = xMin;
        this.xMax = xMax;
        this.yMin = yMin;
        this.yMax = yMax;
        this.function = function;
    }

    public String label() { return label; }
    public double xMin() { return xMin; }
    public double xMax() { return xMax; }
    public double yMin() { return yMin; }
    public double yMax() { return yMax; }

    public double apply(double x, double y) {
        return function.applyAsDouble(x, y);
    }

    @Override
    public String toString() {
        return label;
    }
}
