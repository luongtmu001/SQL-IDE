package spike.grid;

/** Launcher (a class that does not extend Application, so JavaFX starts from the class path). */
public final class Main {
    public static void main(String[] args) {
        javafx.application.Application.launch(SpikeApp.class, args);
    }
}
