package app;

import app.services.ThemeService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AppTest {

    @Test
    void testThemeServiceDefaults() {
        ThemeService themeService = ThemeService.getInstance();
        assertNotNull(themeService, "ThemeService should not be null");
        assertEquals(ThemeService.AppTheme.PRIMER_DARK, themeService.getCurrentTheme(), "Default theme should be PRIMER_DARK");
        assertTrue(themeService.isDarkMode(), "isDarkMode() should return true initially");
    }
}
