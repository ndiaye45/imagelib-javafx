package fr.ndiaye.imagelib.fx;

import fr.ndiaye.imagelib.App;
import fr.ndiaye.imagelib.surface.SurfaceFunction;
import fr.ndiaye.imagelib.surface.SurfaceMesh;
import javafx.application.ConditionalFeature;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.PerspectiveCamera;
import javafx.scene.Scene;
import javafx.scene.SceneAntialiasing;
import javafx.scene.SubScene;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.paint.Stop;
import javafx.scene.shape.CullFace;
import javafx.scene.shape.DrawMode;
import javafx.scene.shape.MeshView;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.TriangleMesh;
import javafx.scene.transform.Rotate;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.util.Locale;

/**
 * Démo d'affichage 3D d'une surface z = f(x, y) avec les classes 3D natives de JavaFX
 * ({@link SubScene}, {@link MeshView}, {@link TriangleMesh}) : aucune bibliothèque externe.
 * Le maillage est calculé par {@link SurfaceMesh}, qui ne dépend pas de JavaFX.
 */
public final class SurfaceWindow {

    private static final float SIZE = 360f;
    private static final float HEIGHT = 130f;
    private static final int PALETTE = 256;

    /** Faux sur une machine sans 3D matérielle : la surface est alors dessinée en 2D par {@link SoftwareSurfaceRenderer}. */
    private final boolean hardware3d = Platform.isSupported(ConditionalFeature.SCENE3D);
    private MeshView view;                       // null en rendu logiciel
    private PerspectiveCamera camera;            // null en rendu logiciel
    private Rotate tiltX;
    private Rotate turnY;
    private double tilt = -58;                   // angles et distance : état commun aux deux rendus
    private double turn = 35;
    private double distance = 900;
    private Canvas canvas;                       // non null en rendu logiciel
    private SurfaceMesh current;
    private final Color[] lut = lookupTable();
    private final ComboBox<SurfaceFunction> choice = new ComboBox<>();
    private final Slider resolution = new Slider(10, 160, 70);
    private final Label resolutionLabel = new Label();
    private final CheckBox wireframe = new CheckBox("Filaire");
    private final Label maxLabel = new Label();
    private final Label minLabel = new Label();
    private final Label stats = new Label();
    private double lastX;
    private double lastY;

    public static void show(Window owner) {
        new SurfaceWindow().open(owner);
    }

    private void open(Window owner) {
        // --- zone d'affichage : scène 3D matérielle, ou canvas 2D de secours ---
        Pane host = new Pane();
        host.getStyleClass().add("preview-pane");
        if (hardware3d) {
            view = new MeshView();
            camera = new PerspectiveCamera(true);
            tiltX = new Rotate(tilt, Rotate.X_AXIS);
            turnY = new Rotate(turn, Rotate.Y_AXIS);
            view.setCullFace(CullFace.NONE);                       // les deux faces sont visibles
            PhongMaterial material = new PhongMaterial(Color.WHITE);
            material.setDiffuseMap(palette());
            view.setMaterial(material);
            view.getTransforms().addAll(tiltX, turnY);
            camera.setNearClip(1);
            camera.setFarClip(4000);
            camera.setFieldOfView(32);
            camera.setTranslateZ(-distance);

            SubScene scene3d = new SubScene(new Group(view), 800, 600, true, SceneAntialiasing.BALANCED);
            scene3d.setCamera(camera);
            scene3d.setFill(Color.web("#0a0f1c"));
            scene3d.widthProperty().bind(host.widthProperty());
            scene3d.heightProperty().bind(host.heightProperty());
            host.getChildren().add(scene3d);
        } else {
            canvas = new Canvas();
            canvas.widthProperty().bind(host.widthProperty());
            canvas.heightProperty().bind(host.heightProperty());
            canvas.widthProperty().addListener((obs, old, value) -> redraw());
            canvas.heightProperty().addListener((obs, old, value) -> redraw());
            Label notice = new Label("Rendu 3D matériel indisponible sur cette machine : affichage logiciel (même vue, moins fluide).");
            notice.getStyleClass().add("hint");
            notice.setLayoutX(14);
            notice.setLayoutY(10);
            host.getChildren().addAll(canvas, notice);
        }

        // Glisser pour tourner, molette pour zoomer.
        host.setOnMousePressed(e -> { lastX = e.getSceneX(); lastY = e.getSceneY(); });
        host.setOnMouseDragged(e -> {
            turn += (e.getSceneX() - lastX) * 0.45;
            tilt = Math.max(-90, Math.min(0, tilt + (e.getSceneY() - lastY) * 0.35));
            lastX = e.getSceneX();
            lastY = e.getSceneY();
            applyView();
        });
        host.setOnScroll(e -> {
            distance = Math.max(300, Math.min(2200, distance - e.getDeltaY() * 0.9));
            applyView();
        });

        // --- barre de commandes ---
        choice.getItems().addAll(SurfaceFunction.values());
        choice.getSelectionModel().selectFirst();
        choice.setOnAction(e -> rebuild());
        resolution.setPrefWidth(160);
        resolution.valueProperty().addListener((obs, old, value) -> {
            resolutionLabel.setText(value.intValue() + " × " + value.intValue());
            if (!resolution.isValueChanging()) {
                rebuild();
            }
        });
        resolution.valueChangingProperty().addListener((obs, was, changing) -> {
            if (!changing) {
                rebuild();
            }
        });
        wireframe.setOnAction(e -> {
            if (hardware3d) {
                view.setDrawMode(wireframe.isSelected() ? DrawMode.LINE : DrawMode.FILL);
            } else {
                redraw();
            }
        });
        resolutionLabel.getStyleClass().add("muted");

        Label title = new Label("SURFACE 3D");
        title.getStyleClass().add("section-title");
        Region grow = new Region();
        HBox.setHgrow(grow, Priority.ALWAYS);
        HBox bar = new HBox(14, title, choice, new Label("Résolution"), resolution, resolutionLabel, wireframe, grow, stats);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().add("header");

        // --- légende des couleurs ---
        Rectangle gradient = new Rectangle(14, 220);
        gradient.setArcWidth(8);
        gradient.setArcHeight(8);
        gradient.setFill(new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE, legendStops()));
        maxLabel.getStyleClass().add("muted");
        minLabel.getStyleClass().add("muted");
        VBox legend = new VBox(8, maxLabel, gradient, minLabel);
        legend.setAlignment(Pos.CENTER);
        legend.setPadding(new Insets(0, 4, 0, 14));
        Label hint = new Label("Glisser : tourner   ·   Molette : zoom");
        hint.getStyleClass().add("hint");

        BorderPane root = new BorderPane(host);
        root.getStyleClass().add("app");
        root.setTop(bar);
        root.setRight(legend);
        root.setBottom(new HBox(hint));
        BorderPane.setMargin(host, new Insets(16, 0, 8, 16));
        BorderPane.setMargin(legend, new Insets(16, 16, 8, 0));
        BorderPane.setMargin(root.getBottom(), new Insets(0, 16, 12, 16));

        Scene scene = new Scene(root, 980, 700);
        scene.getStylesheets().add(App.class.getResource("style.css").toExternalForm());
        Stage stage = new Stage();
        stage.initOwner(owner);
        stage.setTitle("Démo 3D — surface z = f(x, y)");
        stage.setScene(scene);
        resolutionLabel.setText("70 × 70");
        rebuild();
        stage.show();
    }

    private void rebuild() {
        SurfaceFunction surface = choice.getValue();
        int cells = (int) Math.round(resolution.getValue());
        long start = System.nanoTime();
        SurfaceMesh data = SurfaceMesh.build(surface, cells, SIZE, HEIGHT);

        current = data;
        if (hardware3d) {
            TriangleMesh mesh = new TriangleMesh();
            mesh.getPoints().setAll(data.points());
            mesh.getTexCoords().setAll(data.texCoords());
            mesh.getFaces().setAll(data.faces());
            view.setMesh(mesh);
        } else {
            redraw();
        }

        maxLabel.setText(format(data.zMax()));
        minLabel.setText(format(data.zMin()));
        double ms = (System.nanoTime() - start) / 1e6;
        stats.setText(String.format(Locale.FRANCE, "%,d triangles  ·  %.0f ms", data.triangleCount(), ms));
        stats.getStyleClass().setAll("muted");
    }

    /** Répercute l'angle et la distance sur la scène 3D, ou redessine le canvas de secours. */
    private void applyView() {
        if (hardware3d) {
            tiltX.setAngle(tilt);
            turnY.setAngle(turn);
            camera.setTranslateZ(-distance);
        } else {
            redraw();
        }
    }

    private void redraw() {
        if (canvas != null && canvas.getWidth() > 0 && canvas.getHeight() > 0) {
            SoftwareSurfaceRenderer.render(canvas.getGraphicsContext2D(), canvas.getWidth(), canvas.getHeight(),
                    current, turn, tilt, distance, wireframe.isSelected(), lut);
        }
    }

    private static Color[] lookupTable() {
        Color[] table = new Color[PALETTE];
        for (int i = 0; i < PALETTE; i++) {
            table[i] = colourAt(i / (double) (PALETTE - 1));
        }
        return table;
    }

    private static String format(double value) {
        return String.format(Locale.FRANCE, "%.2f", value);
    }

    /** Palette 256 × 1 pixel : bleu (bas) → cyan → vert → jaune → rouge (haut). */
    private static WritableImage palette() {
        WritableImage image = new WritableImage(PALETTE, 1);
        for (int x = 0; x < PALETTE; x++) {
            image.getPixelWriter().setColor(x, 0, colourAt(x / (double) (PALETTE - 1)));
        }
        return image;
    }

    private static Stop[] legendStops() {
        Stop[] stops = new Stop[9];
        for (int i = 0; i < stops.length; i++) {
            double t = i / (double) (stops.length - 1);
            stops[i] = new Stop(t, colourAt(1 - t));       // le haut de la légende = valeur maximale
        }
        return stops;
    }

    private static Color colourAt(double t) {
        return Color.hsb(240 - 240 * t, 0.85, 0.95);
    }
}
