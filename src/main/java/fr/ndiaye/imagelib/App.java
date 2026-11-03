package fr.ndiaye.imagelib;

import fr.ndiaye.imagelib.controller.MainController;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

/**
 * Point d'entrée. Lancement : {@code mvn javafx:run}, ou {@code mvn javafx:run -Djavafx.args="--folder=/chemin/images"}.
 */
public class App extends Application {

    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader loader = new FXMLLoader(App.class.getResource("main.fxml"));
        Parent root = loader.load();
        MainController controller = loader.getController();

        Scene scene = new Scene(root, 1280, 800);
        scene.getStylesheets().add(App.class.getResource("style.css").toExternalForm());

        stage.setTitle("Gestionnaire d'images");
        stage.setMinWidth(1040);
        stage.setMinHeight(660);
        stage.setScene(scene);
        stage.show();

        controller.openInitialFolder(getParameters().getNamed().get("folder"));
    }

    public static void main(String[] args) {
        launch(args);
    }
}
