package fr.ndiaye.imagelib;

import javafx.application.Application;

/**
 * Classe de démarrage pour le JAR et l'application empaquetée par jpackage.
 * <p>
 * Elle n'étend pas {@link Application} : lancée depuis le classpath, une sous-classe d'Application
 * fait échouer le démarrage avec « composants JavaFX manquants ». Cette classe intermédiaire contourne le problème.
 */
public final class Launcher {

    private Launcher() {
    }

    public static void main(String[] args) {
        Application.launch(App.class, args);
    }
}
