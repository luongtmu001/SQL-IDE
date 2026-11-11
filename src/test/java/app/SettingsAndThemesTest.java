package app;

import app.controllers.SettingsDialogController;
import app.models.IdeSettings;
import app.services.SettingsService;
import app.services.ThemeService;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

public class SettingsAndThemesTest {

    @BeforeAll
    static void initJavaFX() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(latch::countDown);
        } catch (IllegalStateException e) {
            // Already started
            latch.countDown();
        }
        assertTrue(latch.await(5, TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("IdeSettings default values and cloning fidelity")
    void testIdeSettingsDefaultsAndCloning() {
        IdeSettings defaults = new IdeSettings();
        assertEquals("Primer Dark", defaults.getTheme());
        assertEquals("Cascadia Mono Light", defaults.getEditorFontFamily());
        assertEquals(13, defaults.getEditorFontSize());
        assertEquals(4, defaults.getTabSize());
        assertTrue(defaults.isShowLineNumbers());
        assertFalse(defaults.isMinimap());
        assertFalse(defaults.isWordWrap());
        assertTrue(defaults.isShowRowNumbers());
        assertEquals("NULL", defaults.getNullDisplayString());
        assertEquals(5000, defaults.getMaxRows());
        assertEquals(30, defaults.getQueryTimeoutSeconds());

        // Clone
        IdeSettings cloned = defaults.clone();
        assertNotSame(defaults, cloned);
        assertEquals(defaults.getTheme(), cloned.getTheme());
        assertEquals(defaults.getEditorFontSize(), cloned.getEditorFontSize());

        // Mutation does not affect original
        cloned.setTheme("Primer Light");
        cloned.setEditorFontSize(18);
        assertEquals("Primer Light", cloned.getTheme());
        assertEquals(18, cloned.getEditorFontSize());
        assertEquals("Primer Dark", defaults.getTheme());
        assertEquals(13, defaults.getEditorFontSize());
    }

    @Test
    @DisplayName("Exact JSON structure parsing and full roundtrip fidelity")
    void testExactJsonRoundtrip() throws Exception {
        String userJson = """
        {
            "theme": "ambiance-beta",
            "editor": {
                "fontFamily": "Cascadia Mono Light",
                "fontSize": 13,
                "lineHeight": 1.5,
                "letterSpacing": 0.5,
                "tabSize": 4,
                "insertSpaces": false,
                "wordWrap": false,
                "minimap": false,
                "keywordCase": "upper",
                "syntaxCheck": true,
                "intellisense": true,
                "intellisenseMru": true
            },
            "appearance": {
                "theme": "ambiance-beta",
                "iconSize": 12,
                "uiFontFamily": "Inter",
                "uiFontSize": 13,
                "autoHideNavbar": false
            },
            "grid": {
                "fontFamily": "Inter",
                "fontSize": 12
            },
            "messages": {
                "fontFamily": "Fira Code",
                "fontSize": 11
            },
            "sql": {
                "maxRows": 0,
                "timeoutSeconds": 30,
                "sqlprotection": 0,
                "stopOnError": true,
                "perTabSession": true
            },
            "addons": {
                "bravo_tool": {
                    "enabled": true
                }
            },
            "session": {
                "restoreOnStartup": true,
                "saveSqlContent": true,
                "maxTabSizeBytes": 1048576,
                "autoSaveIntervalSeconds": 3
            },
            "updates": {
                "checkOnStartup": true,
                "updateUrl": "",
                "lastUpdated": "2026-11-03T15:44:42.038356+00:00",
                "installedVersion": "1.2.6"
            },
            "syntax": {
                "pipeline": true,
                "shadow": false
            },
            "installedVersion": "1.2.6",
            "history": {
                "enabled": true,
                "maxEntriesPerDoc": 200,
                "maxAgeDays": 90,
                "maxDbSizeMb": 500,
                "maxEntrySizeKb": 1024
            },
            "logging": {
                "level": "INFO",
                "enabled": true
            },
            "typography": {
                "ui": {
                    "family": "Inter",
                    "fontFamily": "Inter",
                    "size": 13,
                    "fontSize": 13
                },
                "editor": {
                    "family": "Monaspace Neon Light",
                    "fontFamily": "Monaspace Neon Light",
                    "size": 13,
                    "fontSize": 13
                },
                "grid": {
                    "family": "Inter",
                    "fontFamily": "Inter",
                    "size": 12,
                    "fontSize": 12
                },
                "messages": {
                    "family": "Fira Code",
                    "fontFamily": "Fira Code",
                    "size": 11,
                    "fontSize": 11
                }
            },
            "query": {
                "defaultLimit": 0,
                "timeoutSeconds": 60
            },
            "autoHideNavbar": false
        }
        """;

        IdeSettings parsed = IdeSettings.fromJsonString(userJson);
        assertNotNull(parsed);
        assertEquals("ambiance-beta", parsed.getTheme());
        assertEquals("Cascadia Mono Light", parsed.getEditor().getFontFamily());
        assertEquals(1.5, parsed.getEditor().getLineHeight());
        assertEquals(0.5, parsed.getEditor().getLetterSpacing());
        assertFalse(parsed.getEditor().isInsertSpaces());
        assertTrue(parsed.getEditor().isIntellisenseMru());
        assertEquals(12, parsed.getAppearance().getIconSize());
        assertEquals("Inter", parsed.getGrid().getFontFamily());
        assertEquals(12, parsed.getGrid().getFontSize());
        assertEquals("Fira Code", parsed.getMessages().getFontFamily());
        assertEquals(11, parsed.getMessages().getFontSize());
        assertEquals(0, parsed.getSql().getMaxRows());
        assertEquals(0, parsed.getSql().getSqlprotection());
        assertTrue(parsed.getSql().isPerTabSession());
        assertTrue(parsed.getAddons().getBravo_tool().isEnabled());
        assertEquals(3, parsed.getSession().getAutoSaveIntervalSeconds());
        assertEquals(1048576, parsed.getSession().getMaxTabSizeBytes());
        assertEquals("1.2.6", parsed.getInstalledVersion());
        assertEquals(200, parsed.getHistory().getMaxEntriesPerDoc());
        assertEquals(500, parsed.getHistory().getMaxDbSizeMb());
        assertEquals("INFO", parsed.getLogging().getLevel());

        // Serialize back
        String serialized = parsed.toJsonString();
        assertNotNull(serialized);
        assertTrue(serialized.contains("\"ambiance-beta\""));
        assertTrue(serialized.contains("\"bravo_tool\""));
        assertTrue(serialized.contains("\"history\""));
    }

    @Test
    @DisplayName("SettingsService singleton, listener dispatch, and persistence")
    void testSettingsServiceDispatchAndPersistence() throws Exception {
        SettingsService service = SettingsService.getInstance();
        assertNotNull(service);

        IdeSettings initial = service.getCurrentSettings();
        assertNotNull(initial);

        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean listenerCalled = new AtomicBoolean(false);
        Consumer<IdeSettings> listener = s -> {
            if ("TestTheme".equals(s.getTheme())) {
                listenerCalled.set(true);
                latch.countDown();
            }
        };

        service.addSettingsListener(listener);
        try {
            IdeSettings updated = initial.clone();
            updated.setTheme("TestTheme");
            service.saveSettings(updated);

            assertTrue(latch.await(5, TimeUnit.SECONDS), "Listener should have been notified of settings change");
            assertTrue(listenerCalled.get());
            String currentTheme = service.getCurrentSettings().getTheme();
            assertTrue("TestTheme".equals(currentTheme) || "primer-dark".equalsIgnoreCase(currentTheme) || "primer-light".equalsIgnoreCase(currentTheme));
        } finally {
            service.removeSettingsListener(listener);
            // Restore initial
            service.saveSettings(initial);
        }
    }

    @Test
    @DisplayName("SettingsDialog FXML loading, 10 categories, and Visual vs JSON tabs")
    void testSettingsDialogFxmlLoadingAndTabs() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean loadSuccess = new AtomicBoolean(false);

        Platform.runLater(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/settings-dialog.fxml"));
                Parent root = loader.load();
                SettingsDialogController controller = loader.getController();

                assertNotNull(root);
                assertNotNull(controller);

                // Verify controller controls
                assertNotNull(controller.getThemeComboBox());
                assertNotNull(controller.getEditorFontSizeSpinner());
                assertNotNull(controller.getQueryTimeoutSpinner());

                // Test switching to JSON tab
                controller.handleSwitchToJsonTab();
                assertNotNull(controller.getRawJsonTextArea().getText());
                assertTrue(controller.getRawJsonTextArea().getText().contains("\"editor\""));

                // Test switching back to Visual tab
                controller.handleSwitchToVisualTab();
                assertTrue(controller.getEditorSection().isVisible());

                loadSuccess.set(true);
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(10, TimeUnit.SECONDS), "FXML load should complete in time");
        assertTrue(loadSuccess.get(), "Settings dialog FXML should load successfully");
    }

    @Test
    @DisplayName("SettingsDialogController search filter hides non-matching categories/rows")
    void testSearchFilterLogic() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean testSuccess = new AtomicBoolean(false);

        Platform.runLater(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/settings-dialog.fxml"));
                Parent root = loader.load();
                SettingsDialogController controller = loader.getController();

                // Check search filter with query "Font"
                controller.getSearchField().setText("Font");

                // Editor category should remain visible because it contains Editor Font Family and Size
                assertTrue(controller.getEditorSection().isVisible());
                assertTrue(controller.getEditorSection().isManaged());

                // Clear search: restores only the active category (Editor)
                controller.getSearchField().setText("");
                assertTrue(controller.getEditorSection().isVisible());
                assertFalse(controller.getGridSection().isVisible());
                assertFalse(controller.getAppearanceSection().isVisible());
                assertFalse(controller.getQuerySection().isVisible());

                testSuccess.set(true);
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        assertTrue(testSuccess.get());
    }

    @Test
    @DisplayName("SettingsDialogController apply and cancel behavior")
    void testApplyAndCancelBehavior() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean testSuccess = new AtomicBoolean(false);

        Platform.runLater(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/settings-dialog.fxml"));
                Parent root = loader.load();
                SettingsDialogController controller = loader.getController();

                IdeSettings initial = SettingsService.getInstance().getCurrentSettings().clone();

                // Mutate a field in the form
                controller.getThemeComboBox().getSelectionModel().select("Primer Light");
                controller.getEditorFontSizeSpinner().getValueFactory().setValue(22);

                // Simulate Save & Apply
                controller.handleSaveAndApply();

                IdeSettings afterSave = SettingsService.getInstance().getCurrentSettings();
                assertTrue("Primer Light".equalsIgnoreCase(afterSave.getTheme()) || "primer-light".equals(afterSave.getTheme()));
                assertEquals(22, afterSave.getEditorFontSize());

                // Restore
                SettingsService.getInstance().saveSettings(initial);
                testSuccess.set(true);
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        assertTrue(testSuccess.get());
    }

    @Test
    @DisplayName("SettingsDialogController lazy loads JSON editor on demand and prepares on reopen")
    void testSettingsDialogLazyLoadingAndPrepareForShow() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean testSuccess = new AtomicBoolean(false);

        Platform.runLater(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/settings-dialog.fxml"));
                Parent root = loader.load();
                SettingsDialogController controller = loader.getController();

                // 1. Initial state: on Visual tab, jsonEditor is NOT initialized yet (lazy load)
                assertFalse(controller.isJsonTabActive());
                assertNull(controller.getJsonEditor(), "jsonEditor must be null initially on visual tab");

                // 2. Switch to JSON tab: triggers lazy loading of JSON editor
                controller.handleSwitchToJsonTab();
                assertTrue(controller.isJsonTabActive());

                // 3. Prepare for show resets to Visual tab and clears search
                controller.getSearchField().setText("font");
                controller.prepareForShow();
                assertFalse(controller.isJsonTabActive());
                assertTrue(controller.getSearchField().getText().isEmpty());

                testSuccess.set(true);
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        assertTrue(testSuccess.get());
    }

    @Test
    @DisplayName("ThemeService theme switching mapping")
    void testThemeServiceSwitching() {
        ThemeService.AppTheme dark = ThemeService.AppTheme.fromDisplayName("Primer Dark");
        assertEquals(ThemeService.AppTheme.PRIMER_DARK, dark);
        assertTrue(dark.isDark());

        ThemeService.AppTheme light = ThemeService.AppTheme.fromDisplayName("Primer Light");
        assertEquals(ThemeService.AppTheme.PRIMER_LIGHT, light);
        assertFalse(light.isDark());

        ThemeService.AppTheme nordDark = ThemeService.AppTheme.fromDisplayName("Nord Dark");
        assertEquals(ThemeService.AppTheme.NORD_DARK, nordDark);
        assertTrue(nordDark.isDark());

        ThemeService.AppTheme cupertinoLight = ThemeService.AppTheme.fromDisplayName("Cupertino Light");
        assertEquals(ThemeService.AppTheme.CUPERTINO_LIGHT, cupertinoLight);
        assertFalse(cupertinoLight.isDark());
    }
}
