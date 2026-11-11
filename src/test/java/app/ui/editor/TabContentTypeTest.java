package app.ui.editor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TabContentTypeTest {

    @Test
    @DisplayName("Default SqlEditorTab has SQL_EDITOR type and isSqlTextTab returns true")
    void testDefaultSqlEditorTabType() {
        SqlEditorTab tab = new SqlEditorTab("query.sql", "SELECT 1;", null, "conn", "db", "dbo", null);
        assertEquals(SqlEditorTab.TabContentType.SQL_EDITOR, tab.getTabContentType());
        assertTrue(tab.isSqlTextTab(), "Regular SQL editor tab must be considered a SQL text tab");
    }

    @Test
    @DisplayName("Tab with custom view content is not considered a SQL text tab")
    void testCustomViewNotSqlTextTab() {
        SqlEditorTab tab = new SqlEditorTab("view", "", null, "conn", "db", "dbo", null);
        tab.setTabContentType(SqlEditorTab.TabContentType.VIEW_PANEL);
        assertEquals(SqlEditorTab.TabContentType.VIEW_PANEL, tab.getTabContentType());
        assertFalse(tab.isSqlTextTab());
    }

    @Test
    @DisplayName("Specialized workspace tabs have distinct TabContentType and isSqlTextTab returns false")
    void testSpecializedTabsNotSqlTextTab() {
        SqlEditorTab tab = new SqlEditorTab("Designer", "", null, null, null, null, null);

        tab.setTabContentType(SqlEditorTab.TabContentType.TABLE_DESIGNER);
        assertFalse(tab.isSqlTextTab());

        tab.setTabContentType(SqlEditorTab.TabContentType.TABLE_DATA_EDITOR);
        assertFalse(tab.isSqlTextTab());

        tab.setTabContentType(SqlEditorTab.TabContentType.SCHEMA_COMPARE);
        assertFalse(tab.isSqlTextTab());

        tab.setTabContentType(SqlEditorTab.TabContentType.DEPENDENCY_GRAPH);
        assertFalse(tab.isSqlTextTab());

        tab.setTabContentType(SqlEditorTab.TabContentType.OBJECT_SEARCH);
        assertFalse(tab.isSqlTextTab());

        tab.setTabContentType(SqlEditorTab.TabContentType.SQL_VISUALIZER);
        assertFalse(tab.isSqlTextTab());
    }
}

