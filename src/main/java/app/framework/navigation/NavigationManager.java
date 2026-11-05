package app.framework.navigation;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Perspective &amp; Navigation Coordination (Phase 4).
 */
public class NavigationManager {

    private static final Logger log = LoggerFactory.getLogger(NavigationManager.class);
    private static NavigationManager instance;

    public enum Perspective {
        EXPLORER,
        QUERY_CONSOLE,
        DATABASE_OBJECT_VIEWER,
        SETTINGS
    }

    private final ObjectProperty<Perspective> currentPerspective = new SimpleObjectProperty<>(Perspective.EXPLORER);

    private NavigationManager() {}

    public static synchronized NavigationManager getInstance() {
        if (instance == null) {
            instance = new NavigationManager();
        }
        return instance;
    }

    public ObjectProperty<Perspective> currentPerspectiveProperty() {
        return currentPerspective;
    }

    public Perspective getCurrentPerspective() {
        return currentPerspective.get();
    }

    public void navigateTo(Perspective perspective) {
        if (perspective != null && perspective != currentPerspective.get()) {
            log.info("Navigating perspective to {}", perspective);
            currentPerspective.set(perspective);
        }
    }
}

