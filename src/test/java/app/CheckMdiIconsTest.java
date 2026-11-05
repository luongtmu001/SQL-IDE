package app;

import org.junit.jupiter.api.Test;
import org.kordamp.ikonli.javafx.FontIcon;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class CheckMdiIconsTest {

    @Test
    void testValidateExplorerIcons() {
        String[] icons = {
            "fth-server",
            "mdi2d-database",
            "fth-layers",
            "mdi2t-table-multiple",
            "mdi2t-table",
            "mdi2t-table-headers-eye",
            "mdi2t-table-eye",
            "mdi2f-function",
            "mdi2f-function-variant",
            "mdi2c-cogs",
            "mdi2c-cog-outline",
            "mdi2k-key-variant",
            "mdi2k-key-link",
            "mdi2f-format-letter-case",
            "mdi2n-numeric",
            "mdi2c-calendar-clock",
            "mdi2t-table-column"
        };

        for (String literal : icons) {
            assertDoesNotThrow(() -> {
                FontIcon fontIcon = new FontIcon(literal);
                fontIcon.getIconCode();
            }, "Icon must resolve: " + literal);
        }
    }
}
