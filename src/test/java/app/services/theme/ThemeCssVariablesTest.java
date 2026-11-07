package app.services.theme;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * T2: every -ide-* variable a stylesheet uses has a default in ide-vars.css (an undefined looked-up colour
 * makes JavaFX drop the rule with a warning), and the region stylesheets carry no fixed colours any more,
 * so an imported theme reaches every region.
 */
class ThemeCssVariablesTest {

    private static final String[] SHEETS = {"base.css", "ide-vars.css", "layout.css", "components.css", "editor.css",
            "dialogs.css", "toast.css", "settings.css", "connection-manager.css", "code-style.css",
            "dark-theme.css", "light-theme.css", "shape.css", "shape-flat.css", "titlebar/titlebar.css", "titlebar/window-controls.css"};
    private static final Pattern USE = Pattern.compile("-ide-[a-z0-9-]+");
    private static final Pattern DEF = Pattern.compile("(?m)^\\s*(-ide-[a-z0-9-]+)\\s*:");
    private static final Pattern HEX = Pattern.compile("#[0-9a-fA-F]{6}\\b");

    private static String css(String name) throws Exception {
        try (InputStream in = ThemeCssVariablesTest.class.getResourceAsStream("/css/" + name)) {
            assertNotNull(in, name);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).replaceAll("(?s)/\\*.*?\\*/", "");
        }
    }

    @Test
    void everyIdeVariableHasADefault() throws Exception {
        Set<String> defined = new TreeSet<>();
        Matcher d = DEF.matcher(css("ide-vars.css"));
        while (d.find()) defined.add(d.group(1));
        assertTrue(defined.size() >= 60, "ide-vars.css defines " + defined.size());
        List<String> missing = new ArrayList<>();
        for (String sheet : SHEETS) {
            Matcher u = USE.matcher(css(sheet));
            while (u.find()) if (!defined.contains(u.group())) missing.add(sheet + ": " + u.group());
        }
        assertEquals(List.of(), missing);
    }

    @Test
    void regionSheetsHaveNoFixedColours() throws Exception {
        for (String sheet : new String[]{"layout.css", "components.css", "editor.css", "toast.css"}) {
            Matcher h = HEX.matcher(css(sheet));
            List<String> found = new ArrayList<>();
            while (h.find()) found.add(h.group());
            assertEquals(List.of(), found, sheet + " still has fixed colours");
        }
    }
}
