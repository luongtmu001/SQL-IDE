package app.ui.components;

import javafx.application.Platform;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class ComponentFactoryTest {

    @BeforeAll
    static void initJavaFX() {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException ignored) {
            // Already initialized
        }
    }

    @Test
    @DisplayName("Badge correctly applies semantic styling variants")
    void testBadgeVariants() {
        Badge badge = ComponentFactory.badge("Production", Badge.Variant.DANGER);
        assertEquals("Production", badge.getText());
        assertTrue(badge.getStyleClass().contains("badge"));
        assertTrue(badge.getStyleClass().contains("badge-danger"));

        badge.setVariant(Badge.Variant.SUCCESS);
        assertEquals(Badge.Variant.SUCCESS, badge.getVariant());
        assertTrue(badge.getStyleClass().contains("badge-success"));
    }

    @Test
    @DisplayName("CardPane applies styling and clips corners")
    void testCardPane() {
        CardPane card = ComponentFactory.cardPane();
        assertTrue(card.getStyleClass().contains("card-pane"));
        assertNotNull(card.getClip(), "CardPane must have a clip shape assigned for rounded corner clipping");
    }

    @Test
    @DisplayName("BreadcrumbBar pushes, pops and manages crumb history")
    void testBreadcrumbBar() {
        BreadcrumbBar breadcrumbs = ComponentFactory.breadcrumbBar();
        assertEquals(0, breadcrumbs.size());

        AtomicBoolean homeClicked = new AtomicBoolean(false);
        breadcrumbs.pushItem("Home", () -> homeClicked.set(true));
        breadcrumbs.pushItem("Schema", null);
        breadcrumbs.pushItem("Users Table", null);

        assertEquals(3, breadcrumbs.size());
        assertEquals("Users Table", breadcrumbs.peekItem().text());

        breadcrumbs.popItem();
        assertEquals(2, breadcrumbs.size());
        assertEquals("Schema", breadcrumbs.peekItem().text());

        breadcrumbs.clear();
        assertEquals(0, breadcrumbs.size());
    }

    @Test
    @DisplayName("AppButton creates buttons with correct variant styles")
    void testAppButton() {
        AppButton btn = ComponentFactory.primaryButton("Save", "mdi2c-content-save");
        assertEquals("Save", btn.getText());
        assertTrue(btn.getStyleClass().contains("app-button"));
        assertTrue(btn.getStyleClass().contains(atlantafx.base.theme.Styles.ACCENT));

        btn.setVariant(AppButton.Variant.DANGER);
        assertEquals(AppButton.Variant.DANGER, btn.getVariant());
        assertTrue(btn.getStyleClass().contains(atlantafx.base.theme.Styles.DANGER));
    }

    @Test
    @DisplayName("IconButton creates icon-only button with tooltip")
    void testIconButton() {
        IconButton iconBtn = ComponentFactory.iconButton("mdi2p-play", "Run SQL");
        assertNotNull(iconBtn.getTooltip());
        assertEquals("Run SQL", iconBtn.getTooltip().getText());
        assertTrue(iconBtn.getStyleClass().contains("icon-button"));
    }

    @Test
    @DisplayName("SearchField manages query and prompt")
    void testSearchField() {
        SearchField search = ComponentFactory.searchField("Search tables...");
        assertEquals("Search tables...", search.getTextField().getPromptText());
        search.setText("users");
        assertEquals("users", search.getText());
        search.clear();
        assertEquals("", search.getText());
    }

    @Test
    @DisplayName("StatusIndicator reflects connection and operational status")
    void testStatusIndicator() {
        StatusIndicator status = ComponentFactory.statusIndicator(StatusIndicator.Status.ONLINE);
        assertEquals(StatusIndicator.Status.ONLINE, status.getStatus());
        status.setStatus(StatusIndicator.Status.BUSY, "Running query...");
        assertEquals(StatusIndicator.Status.BUSY, status.getStatus());
    }

    @Test
    @DisplayName("EmptyState displays icon, title and description")
    void testEmptyState() {
        EmptyState empty = ComponentFactory.emptyState("mdi2f-folder-open-outline", "No Queries Found", "Click New to start writing SQL");
        assertNotNull(empty);
        assertTrue(empty.getStyleClass().contains("empty-state-view"));
    }

    @Test
    @DisplayName("LoadingView displays spinner and operational message")
    void testLoadingView() {
        LoadingView loading = ComponentFactory.loadingView("Executing query...");
        assertNotNull(loading);
        assertTrue(loading.getStyleClass().contains("loading-view"));
        loading.setMessage("Formatting results...");
        assertEquals("Formatting results...", loading.getMessage());
    }
}
