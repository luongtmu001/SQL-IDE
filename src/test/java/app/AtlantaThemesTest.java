package app;

import atlantafx.base.theme.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class AtlantaThemesTest {

    @Test
    void testAtlantaThemes() {
        assertNotNull(new PrimerDark());
        assertNotNull(new PrimerLight());
        assertNotNull(new Dracula());
        assertNotNull(new NordDark());
        assertNotNull(new NordLight());
        assertNotNull(new CupertinoDark());
        assertNotNull(new CupertinoLight());
    }
}

