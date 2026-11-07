package app.ui.editor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class EditorBarZoomTest {

    @Test
    void parsesTypedPercentages() {
        assertEquals(150, EditorBar.parse("150"));
        assertEquals(150, EditorBar.parse(" 150 % "));
        assertEquals(117, EditorBar.parse("117%"));
        assertNull(EditorBar.parse(""));
        assertNull(EditorBar.parse("abc"));
        assertNull(EditorBar.parse(null));
    }

    @Test
    void clampsToRange() {
        assertEquals(20, EditorBar.clamp(5));
        assertEquals(400, EditorBar.clamp(900));
        assertEquals(117, EditorBar.clamp(117));
    }
}
