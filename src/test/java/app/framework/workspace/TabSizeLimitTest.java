package app.framework.workspace;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** S3: session.maxTabSizeBytes counts UTF-8 bytes (Vietnamese text takes 2-3 bytes per letter). */
class TabSizeLimitTest {

    @Test
    void utf8SizeAgainstTheLimit() {
        assertFalse(WorkspaceDesktopManager.tooBig("SELECT 1", 0), "0 = no limit");
        assertFalse(WorkspaceDesktopManager.tooBig("a".repeat(100), 100));
        assertTrue(WorkspaceDesktopManager.tooBig("a".repeat(101), 100));
        assertFalse(WorkspaceDesktopManager.tooBig("ệ".repeat(33), 100), "33 x 3 bytes = 99");
        assertTrue(WorkspaceDesktopManager.tooBig("ệ".repeat(34), 100), "34 x 3 bytes = 102");
        assertTrue(WorkspaceDesktopManager.tooBig("😀".repeat(26), 100), "26 x 4 bytes = 104");
    }
}
