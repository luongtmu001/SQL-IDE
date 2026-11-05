package app;

import org.junit.jupiter.api.Test;
import org.kordamp.ikonli.feather.Feather;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class CheckIconsTest {

    @Test
    void testFeatherLiterals() {
        List<String> requiredIcons = List.of(
            "fth-file-plus", "fth-folder", "fth-save", "fth-log-out",
            "fth-sun", "fth-moon", "fth-info", "fth-plus", "fth-play",
            "fth-square", "fth-refresh-cw", "fth-database", "fth-more-vertical",
            "fth-code", "fth-grid", "fth-check-circle", "fth-file-text",
            "fth-bell", "fth-terminal", "fth-minus", "fth-x", "fth-copy",
            "fth-sidebar", "fth-layout", "fth-search",
            "fth-server", "fth-columns", "fth-key", "fth-eye", "fth-zap",
            "fth-filter", "fth-chevrons-up", "fth-chevrons-down", "fth-layers",
            "fth-list", "fth-trash-2", "fth-edit-2", "fth-sliders", "fth-check",
            "fth-bookmark", "fth-align-left",
            // Results Panel icons
            "fth-download", "fth-maximize-2", "fth-minimize-2", "fth-alert-circle",
            // Layout & Docking icons
            "fth-external-link", "fth-chevron-left", "fth-chevron-right"
        );

        for (String desc : requiredIcons) {
            Feather f = Feather.findByDescription(desc);
            assertNotNull(f, "Should resolve icon: " + desc);
        }
    }
}
