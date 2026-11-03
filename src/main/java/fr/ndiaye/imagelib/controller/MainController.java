package fr.ndiaye.imagelib.controller;

import fr.ndiaye.imagelib.crypto.ImageCipher;
import fr.ndiaye.imagelib.fx.FxImages;
import fr.ndiaye.imagelib.fx.SurfaceWindow;
import fr.ndiaye.imagelib.ml.DominantColors;
import fr.ndiaye.imagelib.ml.DominantColors.Swatch;
import fr.ndiaye.imagelib.ml.ModelEvaluation;
import fr.ndiaye.imagelib.ml.TagModel.Suggestion;
import fr.ndiaye.imagelib.model.ImageEntry;
import fr.ndiaye.imagelib.model.Raster;
import fr.ndiaye.imagelib.model.Tags;
import fr.ndiaye.imagelib.model.TransformationSpec;
import fr.ndiaye.imagelib.service.DemoLibrary;
import fr.ndiaye.imagelib.service.ImageLibrary;
import fr.ndiaye.imagelib.service.TagAdvisor;
import fr.ndiaye.imagelib.storage.MetadataDaos;
import fr.ndiaye.imagelib.transform.Brightness;
import fr.ndiaye.imagelib.transform.Grayscale;
import fr.ndiaye.imagelib.transform.ImageTransformation;
import fr.ndiaye.imagelib.transform.Invert;
import fr.ndiaye.imagelib.transform.Mirror;
import fr.ndiaye.imagelib.transform.Rotation;
import fr.ndiaye.imagelib.transform.Sepia;
import fr.ndiaye.imagelib.transform.TransformationFactory;
import fr.ndiaye.imagelib.transform.TransformationPipeline;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.PasswordField;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;

import javax.imageio.ImageIO;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Contrôleur de la fenêtre principale (le « C » de MVC) : il relie la vue (main.fxml)
 * à la logique métier ({@link ImageLibrary}), sans contenir de règle métier lui-même.
 */
public final class MainController {

    private static final int MAX_CLOUD_TAGS = 16;

    // ----- vue (injectée par le FXML) -----
    @FXML private Label folderLabel;
    @FXML private TextField searchField;
    @FXML private Label countLabel;
    @FXML private FlowPane tagCloud;
    @FXML private ListView<ImageEntry> imageList;
    @FXML private StackPane previewPane;
    @FXML private ImageView preview;
    @FXML private Label placeholder;
    @FXML private Label captionLabel;
    @FXML private ScrollPane inspectorScroll;
    @FXML private FlowPane tagsPane;
    @FXML private TextField newTagField;
    @FXML private FlowPane suggestionPane;
    @FXML private Button suggestButton;
    @FXML private Label modelLabel;
    @FXML private HBox paletteBar;
    @FXML private ListView<String> historyList;
    @FXML private Label historyCount;
    @FXML private Label statusLabel;
    @FXML private Label storageLabel;

    // ----- état -----
    private ImageLibrary library;
    private TagAdvisor advisor;                       // apprentissage des tags (cache des caractéristiques par dossier)
    private String currentPath;                       // image affichée, null si aucune
    private boolean updatingList;                     // évite les réactions en cascade
    private final ObservableList<ImageEntry> visibleImages = FXCollections.observableArrayList();
    private final ObservableList<String> historyItems = FXCollections.observableArrayList();
    private final Map<String, Image> thumbnails = new HashMap<>();
    private final AtomicLong renderToken = new AtomicLong();   // ignore les rendus devenus obsolètes
    private String cachedPath;                        // image d'origine gardée en mémoire
    private Raster cachedRaster;

    @FXML
    private void initialize() {
        imageList.setItems(visibleImages);
        imageList.setCellFactory(list -> new ImageCell());
        imageList.setPlaceholder(new Label("Aucune image"));
        imageList.getSelectionModel().selectedItemProperty().addListener((obs, old, selected) -> {
            if (!updatingList) {
                showEntry(selected);
            }
        });

        historyList.setItems(historyItems);
        historyList.setPlaceholder(new Label("Aucune transformation"));
        historyList.setFocusTraversable(false);

        searchField.textProperty().addListener((obs, old, text) -> applyFilter());

        preview.fitWidthProperty().bind(previewPane.widthProperty().subtract(48));
        preview.fitHeightProperty().bind(previewPane.heightProperty().subtract(96));

        showEntry(null);
    }

    // ================================================================= ouverture d'un dossier

    /** Appelé au démarrage : dossier passé en paramètre, sinon dossier « samples » s'il existe. */
    public void openInitialFolder(String folderArgument) {
        Path folder = folderArgument != null ? Path.of(folderArgument) : Path.of("samples");
        if (Files.isDirectory(folder)) {
            openFolder(folder);
        } else if (folderArgument == null) {
            openDemo();     // application téléchargée seule : on charge la démonstration embarquée
        }
    }

    private void openDemo() {
        try {
            openFolder(DemoLibrary.extract());
            status("Dossier de démonstration (temporaire) — « Ouvrir un dossier » pour utiliser vos propres images");
        } catch (IOException e) {
            error("Démonstration indisponible : " + e.getMessage());
        }
    }

    @FXML
    private void onOpenSurface() {
        SurfaceWindow.show(searchField.getScene().getWindow());
    }

    @FXML
    private void onOpenFolder() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Choisir un dossier d'images");
        if (library != null) {
            chooser.setInitialDirectory(library.folder().toFile());
        }
        File chosen = chooser.showDialog(searchField.getScene().getWindow());
        if (chosen != null) {
            openFolder(chosen.toPath());
        }
    }

    private void openFolder(Path folder) {
        try {
            ImageLibrary opened = new ImageLibrary(folder, MetadataDaos.forFolder(folder));
            opened.reload();
            library = opened;
            advisor = new TagAdvisor(opened, this::load);
        } catch (RuntimeException e) {
            error("Impossible d'ouvrir le dossier : " + e.getMessage());
            return;
        }
        thumbnails.clear();
        cachedPath = null;
        cachedRaster = null;
        currentPath = null;
        searchField.clear();

        folderLabel.setText(folder.toAbsolutePath().normalize().toString());
        storageLabel.setText("Métadonnées : " + library.dao().file().getFileName());
        applyFilter();
        showEntry(null);

        List<String> warnings = library.warnings();
        if (!warnings.isEmpty()) {
            error(String.join(" ", warnings));
        } else {
            status(library.entries().size() + " image(s) chargée(s)");
        }
        if (!visibleImages.isEmpty()) {
            imageList.getSelectionModel().selectFirst();
        }
    }

    // ================================================================= liste et recherche

    @FXML
    private void onClearSearch() {
        searchField.clear();
    }

    private void applyFilter() {
        if (library == null) {
            return;
        }
        String keep = currentPath;
        List<ImageEntry> results = library.search(searchField.getText());

        updatingList = true;
        visibleImages.setAll(results);
        ImageEntry toSelect = results.stream().filter(e -> e.path().equals(keep)).findFirst().orElse(null);
        if (toSelect != null) {
            imageList.getSelectionModel().select(toSelect);
        } else {
            imageList.getSelectionModel().clearSelection();
        }
        updatingList = false;

        if (keep != null && toSelect == null) {
            showEntry(null);        // l'image affichée ne correspond plus à la recherche
        }
        int total = library.entries().size();
        countLabel.setText(results.size() == total ? total + " images" : results.size() + " / " + total);
        rebuildTagCloud();
    }

    private void rebuildTagCloud() {
        tagCloud.getChildren().clear();
        int shown = 0;
        for (Map.Entry<String, Integer> tag : library.tagCounts().entrySet()) {
            if (shown++ == MAX_CLOUD_TAGS) {
                break;
            }
            Button chip = new Button(tag.getKey() + "  " + tag.getValue());
            chip.getStyleClass().add("tag-chip");
            chip.setOnAction(e -> addSearchTerm(tag.getKey()));
            tagCloud.getChildren().add(chip);
        }
    }

    private void addSearchTerm(String term) {
        String current = searchField.getText() == null ? "" : searchField.getText().strip();
        if (!Tags.terms(current).contains(Tags.fold(term))) {
            searchField.setText((current + " " + term).strip());
        }
    }

    // ================================================================= image sélectionnée

    private void showEntry(ImageEntry entry) {
        clearSuggestions();
        boolean hasImage = entry != null;
        inspectorScroll.setDisable(!hasImage);
        if (!hasImage) {
            currentPath = null;
            renderToken.incrementAndGet();
            preview.setImage(null);
            captionLabel.setText("");
            captionLabel.setVisible(false);
            placeholder.setVisible(true);
            placeholder.setText(library == null
                    ? "Ouvrez un dossier d'images pour commencer"
                    : "Sélectionnez une image dans la bibliothèque");
            tagsPane.getChildren().clear();
            paletteBar.getChildren().clear();
            historyItems.clear();
            historyCount.setText("");
            return;
        }
        currentPath = entry.path();
        placeholder.setVisible(false);
        refreshInspector(entry);
        render(entry);
    }

    private void refreshInspector(ImageEntry entry) {
        tagsPane.getChildren().clear();
        for (String tag : entry.tags()) {
            Label text = new Label(tag);
            Button remove = new Button("×");
            remove.getStyleClass().add("chip-remove");
            remove.setOnAction(e -> removeTag(tag));
            HBox chip = new HBox(6, text, remove);
            chip.setAlignment(Pos.CENTER_LEFT);
            chip.getStyleClass().add("chip");
            tagsPane.getChildren().add(chip);
        }
        if (entry.tags().isEmpty()) {
            Label none = new Label("Aucun tag pour cette image");
            none.getStyleClass().add("muted");
            tagsPane.getChildren().add(none);
        }
        historyItems.clear();
        int step = 1;
        for (TransformationSpec spec : entry.transformations()) {
            historyItems.add(step++ + ".  " + describe(spec));
        }
        int n = entry.transformations().size();
        historyCount.setText(n == 0 ? "" : n + (n == 1 ? " étape" : " étapes"));
    }

    private static String describe(TransformationSpec spec) {
        try {
            return TransformationFactory.label(spec);
        } catch (IllegalArgumentException e) {
            return spec.type() + " (non reconnue)";
        }
    }

    /** Une modification a été enregistrée : on rafraîchit la liste, l'inspecteur et l'aperçu. */
    private void afterChange(ImageEntry updated, boolean rerender) {
        applyFilter();
        refreshInspector(updated);
        if (rerender) {
            render(updated);
        }
    }

    // ================================================================= tags

    @FXML
    private void onAddTag() {
        if (currentPath == null) {
            return;
        }
        String raw = newTagField.getText();
        try {
            ImageEntry updated = library.addTag(currentPath, raw);
            newTagField.clear();
            afterChange(updated, false);
            status("Tag « " + Tags.normalize(raw) + " » ajouté");
        } catch (IllegalArgumentException e) {
            error(e.getMessage());
        } catch (RuntimeException e) {
            error(e.getMessage());
        }
    }

    private void removeTag(String tag) {
        if (currentPath == null) {
            return;
        }
        try {
            afterChange(library.removeTag(currentPath, tag), false);
            status("Tag « " + tag + " » retiré");
        } catch (RuntimeException e) {
            error(e.getMessage());
        }
    }

    // ================================================================= transformations

    @FXML private void onRotateRight() { apply(new Rotation(90)); }
    @FXML private void onRotateLeft() { apply(new Rotation(270)); }
    @FXML private void onMirror() { apply(new Mirror()); }
    @FXML private void onGrayscale() { apply(new Grayscale()); }
    @FXML private void onSepia() { apply(new Sepia()); }
    @FXML private void onInvert() { apply(new Invert()); }
    @FXML private void onBrighter() { apply(new Brightness(10)); }
    @FXML private void onDarker() { apply(new Brightness(-10)); }

    private void apply(ImageTransformation transformation) {
        if (currentPath == null) {
            return;
        }
        try {
            ImageEntry updated = library.addTransformation(currentPath, transformation);
            afterChange(updated, true);
            status(TransformationFactory.label(transformation.toSpec()) + " appliquée");
        } catch (RuntimeException e) {
            error(e.getMessage());
        }
    }

    @FXML
    private void onUndo() {
        if (currentPath == null) {
            return;
        }
        try {
            ImageEntry updated = library.undoLastTransformation(currentPath);
            afterChange(updated, true);
            status("Dernière transformation annulée");
        } catch (RuntimeException e) {
            error(e.getMessage());
        }
    }

    @FXML
    private void onClearHistory() {
        if (currentPath == null) {
            return;
        }
        try {
            afterChange(library.clearTransformations(currentPath), true);
            status("Image rétablie dans son état d'origine");
        } catch (RuntimeException e) {
            error(e.getMessage());
        }
    }

    // ================================================================= rendu de l'aperçu

    /** Recharge l'image d'origine puis rejoue tout son historique, hors du thread de l'interface. */
    private void render(ImageEntry entry) {
        long token = renderToken.incrementAndGet();
        String path = entry.path();
        List<TransformationSpec> history = entry.transformations();

        Task<Rendered> task = new Task<>() {
            @Override
            protected Rendered call() throws IOException {
                Raster result = TransformationPipeline.apply(original(path), history);
                return new Rendered(result, DominantColors.of(result, 5));      // k-means sur l'image affichée
            }
        };
        task.setOnSucceeded(e -> {
            if (token != renderToken.get()) {
                return;     // une autre image a été choisie entre-temps
            }
            Raster result = task.getValue().image();
            showPalette(task.getValue().colours());
            preview.setImage(FxImages.toImage(result));
            int steps = history.size();
            captionLabel.setText(entry.displayName() + "   ·   " + result.width() + " × " + result.height()
                    + (steps == 0 ? "" : "   ·   " + steps + (steps == 1 ? " transformation" : " transformations")));
            captionLabel.setVisible(true);
        });
        task.setOnFailed(e -> {
            if (token == renderToken.get()) {
                preview.setImage(null);
                captionLabel.setVisible(false);
                error("Impossible d'afficher " + entry.displayName() + " : " + task.getException().getMessage());
            }
        });
        Thread thread = new Thread(task, "render-" + path);
        thread.setDaemon(true);
        thread.start();
    }

    private synchronized Raster original(String path) throws IOException {
        if (!path.equals(cachedPath)) {
            cachedRaster = load(path);
            cachedPath = path;
        }
        return cachedRaster;
    }

    private Raster load(String path) throws IOException {
        Image image = new Image(library.resolve(path).toUri().toString());
        if (image.isError() || image.getWidth() <= 0) {
            throw new IOException("fichier d'image illisible");
        }
        return FxImages.toRaster(image);
    }

    // ================================================================= export

    @FXML
    private void onExport() {
        if (currentPath == null) {
            return;
        }
        ImageEntry entry = library.find(currentPath).orElse(null);
        if (entry == null) {
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Exporter l'image transformée");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Image PNG", "*.png"));
        String base = entry.displayName().replaceFirst("\\.[^.]+$", "");
        chooser.setInitialFileName(base + "-modifiee.png");
        chooser.setInitialDirectory(library.folder().toFile());
        File target = chooser.showSaveDialog(searchField.getScene().getWindow());
        if (target == null) {
            return;
        }
        List<TransformationSpec> history = entry.transformations();
        status("Export en cours…");
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws IOException {
                Raster result = TransformationPipeline.apply(load(entry.path()), history);
                if (!ImageIO.write(SwingFXUtils.fromFXImage(FxImages.toImage(result), null), "png", target)) {
                    throw new IOException("format PNG indisponible");
                }
                return null;
            }
        };
        task.setOnSucceeded(e -> {
            status("Image exportée : " + target.getName());
            // Le fichier exporté apparaît dans le dossier : on rafraîchit la liste.
            Platform.runLater(() -> {
                if (target.getParentFile().toPath().equals(library.folder())) {
                    library.reload();
                    applyFilter();
                }
            });
        });
        task.setOnFailed(e -> error("Export impossible : " + task.getException().getMessage()));
        Thread thread = new Thread(task, "export");
        thread.setDaemon(true);
        thread.start();
    }

    // ================================================================= apprentissage automatique

    /** Image affichée et ses couleurs dominantes, calculées ensemble hors du thread JavaFX. */
    private record Rendered(Raster image, List<Swatch> colours) {
    }

    private void showPalette(List<Swatch> colours) {
        paletteBar.getChildren().clear();
        double width = 268;
        for (Swatch swatch : colours) {
            Region box = new Region();
            box.setPrefWidth(Math.max(14, swatch.share() * width));
            box.setMinWidth(Region.USE_PREF_SIZE);
            box.getStyleClass().add("swatch");
            String hex = String.format("#%06X", swatch.rgb());
            box.setStyle("-fx-background-color: " + hex + ";");
            Tooltip.install(box, new Tooltip(hex + "  —  " + Math.round(swatch.share() * 100) + " % de l'image"));
            paletteBar.getChildren().add(box);
        }
    }

    private void clearSuggestions() {
        suggestionPane.getChildren().clear();
        modelLabel.setText("Un modèle de machine learning apprend de vos autres images déjà taguées.");
        suggestButton.setDisable(false);
    }

    @FXML
    private void onSuggest() {
        if (currentPath == null || advisor == null) {
            return;
        }
        String path = currentPath;
        suggestButton.setDisable(true);
        status("Entraînement du modèle sur vos images taguées…");
        Task<TagAdvisor.Advice> task = new Task<>() {
            @Override
            protected TagAdvisor.Advice call() throws IOException {
                return advisor.advise(path);
            }
        };
        task.setOnSucceeded(e -> {
            if (!path.equals(currentPath)) {
                return;                       // une autre image a été choisie pendant le calcul
            }
            showAdvice(path, task.getValue());
        });
        task.setOnFailed(e -> {
            if (path.equals(currentPath)) {
                suggestButton.setDisable(false);
                Throwable cause = task.getException();
                error(cause instanceof TagAdvisor.NotEnoughDataException ? cause.getMessage()
                        : "Suggestion impossible : " + cause.getMessage());
            }
        });
        Thread thread = new Thread(task, "tag-advisor");
        thread.setDaemon(true);
        thread.start();
    }

    private void showAdvice(String path, TagAdvisor.Advice advice) {
        suggestButton.setDisable(false);
        suggestionPane.getChildren().clear();
        for (Suggestion suggestion : advice.suggestions()) {
            Button chip = new Button("+ " + suggestion.tag() + "  " + Math.round(suggestion.probability() * 100) + " %");
            chip.getStyleClass().add("suggestion");
            chip.setOnAction(e -> {
                try {
                    afterChange(library.addTag(path, suggestion.tag()), false);
                    suggestionPane.getChildren().remove(chip);
                    status("Tag « " + suggestion.tag() + " » ajouté");
                } catch (RuntimeException ex) {
                    error(ex.getMessage());
                }
            });
            suggestionPane.getChildren().add(chip);
        }
        ModelEvaluation.Report quality = advice.quality();
        String learned = "Appris sur " + advice.trainingImages() + " images (cette image est exclue de l'entraînement). ";
        modelLabel.setText(quality == null
                ? learned + "Trop peu de tags répétés pour estimer la précision."
                : learned + "Validation croisée : un des 3 meilleurs tags est juste dans " + Math.round(quality.modelHitAt3() * 100)
                + " % des cas, contre " + Math.round(quality.baselineHitAt3() * 100) + " % en proposant toujours les tags les plus fréquents.");
        if (advice.suggestions().isEmpty()) {
            status("Aucune suggestion assez sûre pour cette image");
        } else {
            status(advice.suggestions().size() + " suggestion(s) : cliquez pour ajouter");
        }
    }

    // ================================================================= chiffrement

    @FXML
    private void onEncrypt() {
        if (currentPath == null) {
            return;
        }
        ImageEntry entry = library.find(currentPath).orElse(null);
        char[] password = entry == null ? null : askPassword("Chiffrer « " + entry.displayName() + " »",
                "Choisissez un mot de passe. Il est impossible de retrouver l'image sans lui.", true);
        if (password == null) {
            return;
        }
        Path target = freeName(library.folder(), baseName(entry) + "-chiffre", ".png");
        List<TransformationSpec> history = entry.transformations();
        runCipher("Chiffrement en cours…", target,
                () -> ImageCipher.encrypt(TransformationPipeline.apply(load(entry.path()), history), password),
                "Image chiffrée : " + target.getFileName());
    }

    @FXML
    private void onDecrypt() {
        if (currentPath == null) {
            return;
        }
        ImageEntry entry = library.find(currentPath).orElse(null);
        char[] password = entry == null ? null : askPassword("Déchiffrer « " + entry.displayName() + " »",
                "Saisissez le mot de passe utilisé lors du chiffrement.", false);
        if (password == null) {
            return;
        }
        String base = baseName(entry).replaceFirst("-chiffre$", "");
        Path target = freeName(library.folder(), base + "-dechiffre", ".png");
        runCipher("Déchiffrement en cours…", target,
                () -> ImageCipher.decrypt(load(entry.path()), password),
                "Image déchiffrée : " + target.getFileName());
    }

    private interface CipherJob {
        Raster run() throws IOException;
    }

    /** Chiffre ou déchiffre hors du thread JavaFX (PBKDF2 est volontairement lent), puis écrit le PNG. */
    private void runCipher(String start, Path target, CipherJob job, String success) {
        status(start);
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws IOException {
                Raster result = job.run();
                if (!ImageIO.write(SwingFXUtils.fromFXImage(FxImages.toImage(result), null), "png", target.toFile())) {
                    throw new IOException("format PNG indisponible");
                }
                return null;
            }
        };
        task.setOnSucceeded(e -> {
            library.reload();
            applyFilter();
            library.find(target.getFileName().toString()).ifPresent(imageList.getSelectionModel()::select);
            status(success);
        });
        task.setOnFailed(e -> error(task.getException().getMessage()));
        Thread thread = new Thread(task, "cipher");
        thread.setDaemon(true);
        thread.start();
    }

    /** Boîte de dialogue de saisie ; renvoie null si l'utilisateur annule ou si la saisie est refusée. */
    private char[] askPassword(String title, String explanation, boolean confirm) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(title);
        dialog.setHeaderText(title);
        dialog.initOwner(searchField.getScene().getWindow());
        dialog.getDialogPane().getStylesheets().addAll(searchField.getScene().getStylesheets());
        dialog.getDialogPane().getStyleClass().add("dialog");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        PasswordField first = new PasswordField();
        first.setPromptText("Mot de passe");
        PasswordField second = new PasswordField();
        second.setPromptText("Confirmer le mot de passe");
        Label help = new Label(explanation);
        help.setWrapText(true);
        help.setMaxWidth(340);
        help.getStyleClass().add("muted");
        VBox content = confirm ? new VBox(10, help, first, second) : new VBox(10, help, first);
        content.setPrefWidth(360);
        dialog.getDialogPane().setContent(content);
        Platform.runLater(first::requestFocus);

        if (dialog.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            return null;
        }
        if (first.getText().length() < ImageCipher.MIN_PASSWORD_LENGTH) {
            error("Le mot de passe doit faire au moins " + ImageCipher.MIN_PASSWORD_LENGTH + " caractères.");
            return null;
        }
        if (confirm && !first.getText().equals(second.getText())) {
            error("Les deux mots de passe ne sont pas identiques.");
            return null;
        }
        return first.getText().toCharArray();
    }

    private static String baseName(ImageEntry entry) {
        return entry.displayName().replaceFirst("\\.[^.]+$", "");
    }

    /** Nom de fichier qui n'écrase rien : nom.png, nom-2.png, nom-3.png… */
    private static Path freeName(Path folder, String base, String extension) {
        Path candidate = folder.resolve(base + extension);
        for (int n = 2; Files.exists(candidate); n++) {
            candidate = folder.resolve(base + "-" + n + extension);
        }
        return candidate;
    }

    // ================================================================= messages

    private void status(String message) {
        statusLabel.getStyleClass().remove("error");
        statusLabel.setText(message);
    }

    private void error(String message) {
        if (!statusLabel.getStyleClass().contains("error")) {
            statusLabel.getStyleClass().add("error");
        }
        statusLabel.setText(message);
    }

    // ================================================================= cellule de la liste

    private final class ImageCell extends ListCell<ImageEntry> {
        private final ImageView thumb = new ImageView();
        private final Label name = new Label();
        private final Label tags = new Label();
        private final Label badge = new Label();
        private final HBox root;

        ImageCell() {
            setPrefWidth(0);          // la cellule épouse la largeur de la liste (pas de défilement horizontal)
            tags.setMaxWidth(205);
            thumb.setFitWidth(76);
            thumb.setFitHeight(56);
            thumb.setPreserveRatio(true);
            StackPane frame = new StackPane(thumb);
            frame.getStyleClass().add("thumb-frame");
            frame.setMinSize(76, 56);
            frame.setPrefSize(76, 56);
            frame.setMaxSize(76, 56);

            name.getStyleClass().add("cell-name");
            tags.getStyleClass().add("cell-tags");
            badge.getStyleClass().add("cell-badge");
            VBox texts = new VBox(2, name, tags, badge);
            texts.setAlignment(Pos.CENTER_LEFT);
            HBox.setHgrow(texts, Priority.ALWAYS);
            root = new HBox(12, frame, texts);
            root.setAlignment(Pos.CENTER_LEFT);
        }

        @Override
        protected void updateItem(ImageEntry entry, boolean empty) {
            super.updateItem(entry, empty);
            if (empty || entry == null) {
                setGraphic(null);
                return;
            }
            thumb.setImage(thumbnails.computeIfAbsent(entry.path(), path ->
                    new Image(library.resolve(path).toUri().toString(), 160, 120, true, true, true)));
            name.setText(entry.displayName());
            tags.setText(entry.tags().isEmpty() ? "Sans tag" : String.join("  ·  ", entry.tags()));
            tags.getStyleClass().remove("untagged");
            if (entry.tags().isEmpty()) {
                tags.getStyleClass().add("untagged");
            }
            int n = entry.transformations().size();
            badge.setText(n == 0 ? "" : n + (n == 1 ? " transformation" : " transformations"));
            badge.setVisible(n > 0);
            badge.setManaged(n > 0);
            setGraphic(root);
        }
    }
}
