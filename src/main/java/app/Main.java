package app;

import app.application.AppLauncher;
import javafx.application.Application;

/**
 * Standard entry point for the application.
 * Using a separate Main class without extending Application avoids
 * JavaFX runtime module path launcher issues when running as a fat JAR or via classpath.
 */
public class Main {
    public static void main(String[] args) {
        Application.launch(AppLauncher.class, args);
    }
}

