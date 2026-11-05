package app;

import app.models.DatabaseNode;
import app.models.DatabaseNodeType;
import app.services.NotificationService;
import app.services.ThemeService;
import app.ui.components.*;
import javafx.application.Platform;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ComponentsTest {

    @BeforeAll
    static void initJavaFX() {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException ignored) {
            // Platform already initialized
        }
    }

    @Test
    void testDatabaseNodeIconInference() {
        // Table & categories
        DatabaseNode tableNode = new DatabaseNode("users", DatabaseNodeType.TABLE);
        assertEquals("mdi2t-table", tableNode.getEffectiveIconLiteral());

        DatabaseNode tablesCat = new DatabaseNode("Tables", DatabaseNodeType.CATEGORY_TABLES);
        assertEquals("mdi2t-table-multiple", tablesCat.getEffectiveIconLiteral());

        // Views
        DatabaseNode viewNode = new DatabaseNode("v_active", DatabaseNodeType.VIEW);
        assertEquals("mdi2t-table-eye", viewNode.getEffectiveIconLiteral());

        DatabaseNode viewsCat = new DatabaseNode("Views", DatabaseNodeType.CATEGORY_VIEWS);
        assertEquals("mdi2t-table-headers-eye", viewsCat.getEffectiveIconLiteral());

        // Functions & Procedures
        DatabaseNode funcNode = new DatabaseNode("calc_tax", DatabaseNodeType.FUNCTION);
        assertEquals("mdi2f-function-variant", funcNode.getEffectiveIconLiteral());

        DatabaseNode procNode = new DatabaseNode("archive_old", DatabaseNodeType.PROCEDURE);
        assertEquals("mdi2c-cog-outline", procNode.getEffectiveIconLiteral());

        // Columns by Data Type
        DatabaseNode pkCol = new DatabaseNode("id", DatabaseNodeType.COLUMN, "int", null, true, false);
        assertEquals("mdi2k-key-variant", pkCol.getEffectiveIconLiteral());
        assertTrue(pkCol.isPrimaryKey());

        DatabaseNode fkCol = new DatabaseNode("user_id", DatabaseNodeType.COLUMN, "bigint", null, false, true);
        assertEquals("mdi2k-key-link", fkCol.getEffectiveIconLiteral());
        assertTrue(fkCol.isForeignKey());

        DatabaseNode textCol = new DatabaseNode("email", DatabaseNodeType.COLUMN, "varchar(255)");
        assertEquals("mdi2f-format-letter-case", textCol.getEffectiveIconLiteral());

        DatabaseNode numCol = new DatabaseNode("salary", DatabaseNodeType.COLUMN, "numeric(12,2)");
        assertEquals("mdi2n-numeric", numCol.getEffectiveIconLiteral());

        DatabaseNode dateCol = new DatabaseNode("created_at", DatabaseNodeType.COLUMN, "timestamp");
        assertEquals("mdi2c-calendar-clock", dateCol.getEffectiveIconLiteral());

        DatabaseNode genericCol = new DatabaseNode("raw_blob", DatabaseNodeType.COLUMN, "blob");
        assertEquals("mdi2t-table-column", genericCol.getEffectiveIconLiteral());

        // Explicit override
        DatabaseNode customNode = new DatabaseNode("custom", DatabaseNodeType.TABLE, null, "fth-star");
        assertEquals("fth-star", customNode.getEffectiveIconLiteral());
    }

    @Test
    void testAppButton() {
        AppButton btn = new AppButton("Execute", "fth-play", AppButton.Variant.PRIMARY, AppButton.Size.SMALL);
        assertEquals("Execute", btn.getText());
        assertEquals(AppButton.Variant.PRIMARY, btn.getVariant());
        assertEquals(AppButton.Size.SMALL, btn.getButtonSize());
        assertNotNull(btn.getGraphic());

        btn.setVariant(AppButton.Variant.DANGER);
        assertEquals(AppButton.Variant.DANGER, btn.getVariant());
    }

    @Test
    void testIconButton() {
        IconButton btn = new IconButton("fth-refresh-cw", "Refresh explorer");
        assertNotNull(btn.getGraphic());
        assertEquals("fth-refresh-cw", btn.getIcon());
        assertNotNull(btn.getTooltip());
        assertEquals("Refresh explorer", btn.getTooltip().getText());

        btn.setIcon("fth-play");
        assertEquals("fth-play", btn.getIcon());
    }

    @Test
    void testSearchField() {
        SearchField searchField = new SearchField("Filter objects...");
        assertEquals("Filter objects...", searchField.getInputField().getPromptText());

        searchField.setText("customer");
        assertEquals("customer", searchField.getText());
        assertEquals("customer", searchField.textProperty().get());

        final String[] received = new String[1];
        searchField.setOnTextChange(val -> received[0] = val);
        searchField.setText("orders");
        assertEquals("orders", received[0]);
    }

    @Test
    void testStatusIndicator() {
        StatusIndicator indicator = new StatusIndicator(StatusIndicator.Status.ONLINE, "Connected");
        assertEquals(StatusIndicator.Status.ONLINE, indicator.getCurrentStatus());

        indicator.setStatus(StatusIndicator.Status.BUSY, "Executing query...");
        assertEquals(StatusIndicator.Status.BUSY, indicator.getCurrentStatus());

        indicator.setStatus(StatusIndicator.Status.OFFLINE);
        assertEquals(StatusIndicator.Status.OFFLINE, indicator.getCurrentStatus());
    }

    @Test
    void testEmptyStateAndLoadingView() {
        EmptyState empty = new EmptyState("fth-inbox", "No Data", "Table has no records.");
        assertNotNull(empty);

        LoadingView loading = new LoadingView("Running query...");
        assertEquals("Running query...", loading.getMessage());
        loading.setMessage("Formatting...");
        assertEquals("Formatting...", loading.getMessage());
    }

    @Test
    void testNotificationService() {
        NotificationService ns = NotificationService.getInstance();
        assertNotNull(ns);
        // Ensure methods execute safely without throwing
        assertDoesNotThrow(() -> {
            ns.showSuccess("Test Success", "Unit test message");
            ns.showInfo("Test Info", "Unit test message");
            ns.showWarning("Test Warning", "Unit test message");
            ns.showError("Test Error", "Unit test message");
        });
    }

    @Test
    void testThemeServiceEnumeration() {
        ThemeService ts = ThemeService.getInstance();
        assertNotNull(ts.getCurrentTheme());

        for (ThemeService.AppTheme theme : ThemeService.AppTheme.values()) {
            assertNotNull(theme.getDisplayName());
            assertNotNull(theme.name());
        }
    }
}

