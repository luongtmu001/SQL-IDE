package app.ui.settings;

import javafx.application.Platform;
import javafx.scene.text.Font;
import javafx.scene.text.Text;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * S4 (LLTool get_system_fonts): the font families installed on this computer, read once per app run in the
 * background and cached (performance rules: no repeated work, nothing heavy on the FX thread).
 * Code fonts = families whose "i" and "W" have the same width (monospaced).
 */
public final class SystemFonts {

    public record Catalog(List<String> all, List<String> monospaced) {
    }

    private static volatile Catalog cached;
    private static volatile boolean loading;
    private static final List<Consumer<Catalog>> waiting = new ArrayList<>();

    private SystemFonts() {
    }

    /** {@code done} runs on the FX thread, at once when the list is already known. */
    public static void load(Consumer<Catalog> done) {
        Catalog c = cached;
        if (c != null) {
            done.accept(c);
            return;
        }
        synchronized (waiting) {
            waiting.add(done);
            if (loading) return;
            loading = true;
        }
        Thread.ofVirtual().name("system-fonts").start(() -> {
            Catalog result;
            try {
                result = read();
            } catch (Throwable t) {
                result = new Catalog(List.of(), List.of());
            }
            Catalog fin = result;
            cached = fin;
            Platform.runLater(() -> {
                List<Consumer<Catalog>> list;
                synchronized (waiting) {
                    list = new ArrayList<>(waiting);
                    waiting.clear();
                }
                for (Consumer<Catalog> w : list) w.accept(fin);
            });
        });
    }

    private static Catalog read() {
        List<String> all = new ArrayList<>();
        List<String> mono = new ArrayList<>();
        for (String family : Font.getFamilies()) {
            if (family == null || family.isBlank() || family.startsWith("@")) continue; // "@Font" = vertical CJK variant
            all.add(family);
            if (isMonospaced(family)) mono.add(family);
        }
        all.sort(String.CASE_INSENSITIVE_ORDER);
        mono.sort(String.CASE_INSENSITIVE_ORDER);
        return new Catalog(List.copyOf(all), List.copyOf(mono));
    }

    private static boolean isMonospaced(String family) {
        Font f = Font.font(family, 13);
        if (!f.getFamily().equalsIgnoreCase(family)) return false; // not really installed: JavaFX fell back
        double narrow = width("iiiiiiiiii", f), wide = width("WWWWWWWWWW", f);
        return narrow > 0 && Math.abs(narrow - wide) < 0.5;
    }

    private static double width(String s, Font f) {
        Text t = new Text(s);
        t.setFont(f);
        return t.getLayoutBounds().getWidth();
    }

    /** Preferred names first (kept even when not installed: the editor falls back), then the rest, no duplicates. */
    public static List<String> merge(List<String> preferred, List<String> system) {
        LinkedHashSet<String> out = new LinkedHashSet<>(preferred);
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (String p : preferred) seen.add(p.toLowerCase(Locale.ROOT));
        for (String s : system) if (seen.add(s.toLowerCase(Locale.ROOT))) out.add(s);
        return new ArrayList<>(out);
    }
}
