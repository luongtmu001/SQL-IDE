package app.services.theme;

import app.services.ThemeService;

import java.nio.file.Path;

/**
 * T1 (theme analysis 2026-11-06): one theme of the app, built in (an AtlantaFX theme) or imported from a
 * VS Code {@code .vsix}. What the app needs to show it:
 * <ul>
 *   <li>{@code base}: the AtlantaFX theme under it (Primer Dark / Light for an imported theme, by {@link #kind});</li>
 *   <li>{@code cssFile}: an imported theme's stylesheet that overrides the {@code -color-*} / {@code -ide-*}
 *       variables (null for a built-in theme);</li>
 *   <li>{@code monacoFile}: an imported theme's Monaco theme (null: built in, made by {@link MonacoThemes}).</li>
 * </ul>
 * {@code id} is what Settings store ({@code theme}); old display names ("Primer Dark") still resolve.
 * {@code family} (T4): the group a theme is listed under: {@link #BUILT_IN_FAMILY} or the name of the VS Code
 * extension it came from ("GitHub Theme"), so themes of one package stay together in Settings and the menu.
 */
public record ThemeDefinition(
        String id,
        String label,
        Kind kind,
        boolean highContrast,
        String source,
        ThemeService.AppTheme base,
        Path cssFile,
        Path monacoFile,
        String family) {

    public enum Kind { DARK, LIGHT }

    public static final String BUILT_IN_FAMILY = "Có sẵn";

    public boolean isDark() {
        return kind == Kind.DARK;
    }

    public boolean isBuiltIn() {
        return "built-in".equals(source);
    }

    /** Id of a built-in theme: the AtlantaFX enum, e.g. PRIMER_DARK -> "primer-dark". */
    public static String builtInId(ThemeService.AppTheme t) {
        return t.name().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
    }

    public static ThemeDefinition builtIn(ThemeService.AppTheme t) {
        return new ThemeDefinition(builtInId(t), t.getDisplayName(), t.isDark() ? Kind.DARK : Kind.LIGHT,
                false, "built-in", t, null, null, BUILT_IN_FAMILY);
    }
}
