package app.application;

import app.services.ThemeService;
import javafx.stage.Stage;

/**
 * Global application context holding runtime references and services.
 */
public class AppContext {

    private static final AppContext INSTANCE = new AppContext();

    private Stage primaryStage;
    private final ThemeService themeService = ThemeService.getInstance();
    private app.controllers.MainController mainController;

    private AppContext() {
    }

    public static AppContext getInstance() {
        return INSTANCE;
    }

    public Stage getPrimaryStage() {
        return primaryStage;
    }

    public void setPrimaryStage(Stage primaryStage) {
        this.primaryStage = primaryStage;
    }

    public ThemeService getThemeService() {
        return themeService;
    }

    public app.controllers.MainController getMainController() {
        return mainController;
    }

    public void setMainController(app.controllers.MainController mainController) {
        this.mainController = mainController;
    }
}

