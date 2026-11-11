package app.ui.components;

import javafx.application.Platform;
import javafx.scene.control.Button;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class WorkspaceComponentsTest {

    @BeforeAll
    static void initJavaFX() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(latch::countDown);
        } catch (IllegalStateException e) {
            latch.countDown();
        }
        assertTrue(latch.await(10, TimeUnit.SECONDS));
    }

    @Test
    void testWorkspaceToolbarOverflowCollapsing() {
        WorkspaceToolbar toolbar = new WorkspaceToolbar();
        Button btnHigh = new Button("High Priority");
        Button btnLow = new Button("Low Priority");

        toolbar.addLeft(btnHigh, WorkspaceToolbar.Priority.HIGH);
        toolbar.addRight(btnLow, WorkspaceToolbar.Priority.LOW);

        // At full width: low priority node is on toolbar
        toolbar.updateOverflow(1200);
        assertTrue(toolbar.getHiddenLowPriorityNodes().isEmpty());
        assertFalse(toolbar.getOverflowMenu().isVisible());
        assertTrue(toolbar.getRightBox().getChildren().contains(btnLow));

        // When width shrinks: low priority node moves into overflow menu
        toolbar.updateOverflow(600);
        assertFalse(toolbar.getHiddenLowPriorityNodes().isEmpty());
        assertTrue(toolbar.getHiddenLowPriorityNodes().contains(btnLow));
        assertFalse(toolbar.getRightBox().getChildren().contains(btnLow));
        assertTrue(toolbar.getOverflowMenu().isVisible());
        assertEquals(1, toolbar.getOverflowMenu().getItems().size());
        assertEquals("Low Priority", toolbar.getOverflowMenu().getItems().get(0).getText());

        // When width expands again: low priority node is restored to toolbar
        toolbar.updateOverflow(1200);
        assertTrue(toolbar.getHiddenLowPriorityNodes().isEmpty());
        assertFalse(toolbar.getOverflowMenu().isVisible());
        assertTrue(toolbar.getRightBox().getChildren().contains(btnLow));
    }

    @Test
    void testSegmentedToggleCannotBeDeselected() {
        SegmentedToggle<String> toggle = new SegmentedToggle<>();
        toggle.addItem("tab1", "Tab 1", "fth-grid");
        toggle.addItem("tab2", "Tab 2", "fth-list");
        toggle.addItem("tab3", "Tab 3", "fth-filter");

        // First item is selected by default
        assertEquals("tab1", toggle.getSelectedValue());
        assertTrue(toggle.getButton("tab1").isSelected());

        // Clicking the currently selected item again does NOT deselect it
        toggle.getButton("tab1").setSelected(false);
        toggle.getButton("tab1").getOnAction().handle(null);
        assertTrue(toggle.getButton("tab1").isSelected(), "Clicking active item must keep it selected");
        assertEquals("tab1", toggle.getSelectedValue());

        // Selecting another item
        toggle.select("tab2");
        assertEquals("tab2", toggle.getSelectedValue());
        assertTrue(toggle.getButton("tab2").isSelected());
        assertFalse(toggle.getButton("tab1").isSelected());

        // Re-clicking tab2 still keeps it selected
        toggle.getButton("tab2").setSelected(false);
        toggle.getButton("tab2").getOnAction().handle(null);
        assertTrue(toggle.getButton("tab2").isSelected());
        assertEquals("tab2", toggle.getSelectedValue());
    }

    @Test
    void testPaneHeaderAndFooter() {
        PaneHeader header = new PaneHeader("Dữ liệu bảng", "fth-grid");
        assertEquals("Dữ liệu bảng", header.getTitle());
        header.setBadge(Badge.accent("30 dòng"));
        assertNotNull(header.getBadge());

        WorkspaceFooter footer = new WorkspaceFooter();
        footer.addSection("30 dòng");
        footer.setSampleDataVisible(true);
        assertTrue(footer.isSampleDataVisible());
        footer.setSampleDataVisible(false);
        assertFalse(footer.isSampleDataVisible());
    }

    @Test
    void testFilterChip() {
        FilterChip chip = new FilterChip("Views", "fth-eye", false);
        assertFalse(chip.isSelected());

        chip.setSelected(true);
        assertTrue(chip.isSelected());
    }
}

