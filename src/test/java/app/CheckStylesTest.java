package app;

import atlantafx.base.theme.Styles;
import org.junit.jupiter.api.Test;

class CheckStylesTest {

    @Test
    void testStylesConstants() {
        System.out.println("ACCENT: " + Styles.ACCENT);
        System.out.println("SUCCESS: " + Styles.SUCCESS);
        System.out.println("DANGER: " + Styles.DANGER);
        System.out.println("WARNING: " + Styles.WARNING);
        System.out.println("FLAT: " + Styles.FLAT);
        System.out.println("BUTTON_OUTLINED: " + Styles.BUTTON_OUTLINED);
        System.out.println("BUTTON_ICON: " + Styles.BUTTON_ICON);
        System.out.println("BUTTON_CIRCLE: " + Styles.BUTTON_CIRCLE);
        System.out.println("SMALL: " + Styles.SMALL);
        System.out.println("MEDIUM: " + Styles.MEDIUM);
        System.out.println("LARGE: " + Styles.LARGE);
    }
}

