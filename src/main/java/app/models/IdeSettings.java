package app.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * Complete settings model matching the exact JSON schema of LLTool.
 * Fully compliant with Python settings.json structure and all configuration categories.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class IdeSettings implements Serializable, Cloneable {

    private static final ObjectMapper MAPPER = jsonMapper();

    /**
     * The one JSON mapper for settings: FIELDS only. The convenience getters / setters of this class (getMaxRows
     * returns 5000 for "unlimited", setQueryTimeoutSeconds writes two sections, ...) must not become extra
     * top-level keys: read back, they overwrote the real values (e.g. sql.maxRows 0 -> 5000). Field names are
     * the keys of LLTool's settings.json.
     */
    public static ObjectMapper jsonMapper() {
        return new ObjectMapper()
                .setVisibility(com.fasterxml.jackson.annotation.PropertyAccessor.ALL,
                        com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.NONE)
                .setVisibility(com.fasterxml.jackson.annotation.PropertyAccessor.FIELD,
                        com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.ANY)
                .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .enable(SerializationFeature.INDENT_OUTPUT);
    }

    // Root properties
    private String theme = "Primer Dark";
    private String installedVersion = "1.2.6";
    private boolean autoHideNavbar = false;

    // Sub-objects
    private EditorSettings editor = new EditorSettings();
    private AppearanceSettings appearance = new AppearanceSettings();
    private GridSettings grid = new GridSettings();
    private MessagesSettings messages = new MessagesSettings();
    private SqlSettings sql = new SqlSettings();
    private AddonsSettings addons = new AddonsSettings();
    private SessionSettings session = new SessionSettings();
    private UpdatesSettings updates = new UpdatesSettings();
    private SyntaxSettings syntax = new SyntaxSettings();
    private HistorySettings history = new HistorySettings();
    private LoggingSettings logging = new LoggingSettings();
    private TypographySettings typography = new TypographySettings();
    private QuerySettings query = new QuerySettings();

    public IdeSettings() {
    }

    public static IdeSettings createDefault() {
        return new IdeSettings();
    }

    // ─────────────────────────────────────────────────────────────
    // Nested Category Classes
    // ─────────────────────────────────────────────────────────────

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class EditorSettings implements Serializable, Cloneable {
        public static final String DETAIL_MODE_ALWAYS = "always";
        public static final String DETAIL_MODE_ON_DEMAND = "onDemand";

        private String fontFamily = "Cascadia Mono Light";
        private int fontSize = 13;
        private double lineHeight = 1.5;
        private double letterSpacing = 0.5;
        private int tabSize = 4;
        private boolean insertSpaces = false;
        private boolean wordWrap = false;
        private boolean minimap = false;
        private String keywordCase = "upper";
        private boolean syntaxCheck = true;
        private boolean intellisense = true;
        private boolean intellisenseMru = true;
        /** Panel with the object's summary / script beside the suggestion list. */
        private boolean intellisenseDetail = true;
        /** {@code always}: the panel opens with the list; {@code onDemand}: arrow right opens it, only then it loads. */
        private String intellisenseDetailMode = DETAIL_MODE_ALWAYS;

        public String getFontFamily() { return fontFamily; }
        public void setFontFamily(String fontFamily) { this.fontFamily = fontFamily; }
        public int getFontSize() { return fontSize; }
        public void setFontSize(int fontSize) { this.fontSize = fontSize; }
        public double getLineHeight() { return lineHeight; }
        public void setLineHeight(double lineHeight) { this.lineHeight = lineHeight; }
        public double getLetterSpacing() { return letterSpacing; }
        public void setLetterSpacing(double letterSpacing) { this.letterSpacing = letterSpacing; }
        public int getTabSize() { return tabSize; }
        public void setTabSize(int tabSize) { this.tabSize = tabSize; }
        public boolean isInsertSpaces() { return insertSpaces; }
        public void setInsertSpaces(boolean insertSpaces) { this.insertSpaces = insertSpaces; }
        public boolean isWordWrap() { return wordWrap; }
        public void setWordWrap(boolean wordWrap) { this.wordWrap = wordWrap; }
        public boolean isMinimap() { return minimap; }
        public void setMinimap(boolean minimap) { this.minimap = minimap; }
        public String getKeywordCase() { return keywordCase; }
        public void setKeywordCase(String keywordCase) { this.keywordCase = keywordCase; }
        public boolean isSyntaxCheck() { return syntaxCheck; }
        public void setSyntaxCheck(boolean syntaxCheck) { this.syntaxCheck = syntaxCheck; }
        public boolean isIntellisense() { return intellisense; }
        public void setIntellisense(boolean intellisense) { this.intellisense = intellisense; }
        public boolean isIntellisenseMru() { return intellisenseMru; }
        public void setIntellisenseMru(boolean intellisenseMru) { this.intellisenseMru = intellisenseMru; }
        public boolean isIntellisenseDetail() { return intellisenseDetail; }
        public void setIntellisenseDetail(boolean intellisenseDetail) { this.intellisenseDetail = intellisenseDetail; }
        public String getIntellisenseDetailMode() { return intellisenseDetailMode; }
        public void setIntellisenseDetailMode(String mode) {
            this.intellisenseDetailMode = DETAIL_MODE_ON_DEMAND.equals(mode) ? DETAIL_MODE_ON_DEMAND : DETAIL_MODE_ALWAYS;
        }

        @Override
        public EditorSettings clone() {
            try {
                return (EditorSettings) super.clone();
            } catch (CloneNotSupportedException e) {
                EditorSettings c = new EditorSettings();
                c.fontFamily = this.fontFamily;
                c.fontSize = this.fontSize;
                c.lineHeight = this.lineHeight;
                c.letterSpacing = this.letterSpacing;
                c.tabSize = this.tabSize;
                c.insertSpaces = this.insertSpaces;
                c.wordWrap = this.wordWrap;
                c.minimap = this.minimap;
                c.keywordCase = this.keywordCase;
                c.syntaxCheck = this.syntaxCheck;
                c.intellisense = this.intellisense;
                c.intellisenseMru = this.intellisenseMru;
                c.intellisenseDetail = this.intellisenseDetail;
                c.intellisenseDetailMode = this.intellisenseDetailMode;
                return c;
            }
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AppearanceSettings implements Serializable, Cloneable {
        private String theme = "Primer Dark";
        private int iconSize = 12;
        private String uiFontFamily = "Inter";
        private int uiFontSize = 13;
        private boolean autoHideNavbar = false;
        private String density = "compact";
        /** Workspace panel style: "rounded" (cards with 6px gaps) or "flat" (VS Code-like hairlines). */
        private String panelStyle = "rounded";

        public String getTheme() { return theme; }
        public void setTheme(String theme) { this.theme = theme; }
        public int getIconSize() { return iconSize; }
        public void setIconSize(int iconSize) { this.iconSize = iconSize; }
        public String getUiFontFamily() { return uiFontFamily; }
        public void setUiFontFamily(String uiFontFamily) { this.uiFontFamily = uiFontFamily; }
        public int getUiFontSize() { return uiFontSize; }
        public void setUiFontSize(int uiFontSize) { this.uiFontSize = uiFontSize; }
        public boolean isAutoHideNavbar() { return autoHideNavbar; }
        public void setAutoHideNavbar(boolean autoHideNavbar) { this.autoHideNavbar = autoHideNavbar; }
        public String getDensity() { return density; }
        public void setDensity(String density) { this.density = density; }
        public String getPanelStyle() { return panelStyle; }
        public void setPanelStyle(String panelStyle) { this.panelStyle = panelStyle; }

        @Override
        public AppearanceSettings clone() {
            try {
                return (AppearanceSettings) super.clone();
            } catch (CloneNotSupportedException e) {
                AppearanceSettings c = new AppearanceSettings();
                c.theme = this.theme;
                c.iconSize = this.iconSize;
                c.uiFontFamily = this.uiFontFamily;
                c.uiFontSize = this.uiFontSize;
                c.autoHideNavbar = this.autoHideNavbar;
                c.density = this.density;
                c.panelStyle = this.panelStyle;
                return c;
            }
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GridSettings implements Serializable, Cloneable {
        private String fontFamily = "Inter";
        private int fontSize = 12;
        private boolean showRowNumbers = true;
        private String nullDisplayString = "NULL";
        /** Several result sets: "list" (stacked, default) or "tabs" (one tab per result set). */
        private String resultLayout = "list";

        public String getResultLayout() { return resultLayout != null ? resultLayout : "list"; }
        public void setResultLayout(String resultLayout) { this.resultLayout = resultLayout; }
        public boolean isResultTabs() { return "tabs".equalsIgnoreCase(resultLayout); }

        public String getFontFamily() { return fontFamily; }
        public void setFontFamily(String fontFamily) { this.fontFamily = fontFamily; }
        public int getFontSize() { return fontSize; }
        public void setFontSize(int fontSize) { this.fontSize = fontSize; }
        public boolean isShowRowNumbers() { return showRowNumbers; }
        public void setShowRowNumbers(boolean showRowNumbers) { this.showRowNumbers = showRowNumbers; }
        public String getNullDisplayString() { return nullDisplayString; }
        public void setNullDisplayString(String nullDisplayString) { this.nullDisplayString = nullDisplayString; }

        @Override
        public GridSettings clone() {
            try {
                return (GridSettings) super.clone();
            } catch (CloneNotSupportedException e) {
                GridSettings c = new GridSettings();
                c.fontFamily = this.fontFamily;
                c.fontSize = this.fontSize;
                c.showRowNumbers = this.showRowNumbers;
                c.nullDisplayString = this.nullDisplayString;
                c.resultLayout = this.resultLayout;
                return c;
            }
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MessagesSettings implements Serializable, Cloneable {
        private String fontFamily = "Fira Code";
        private int fontSize = 11;

        public String getFontFamily() { return fontFamily; }
        public void setFontFamily(String fontFamily) { this.fontFamily = fontFamily; }
        public int getFontSize() { return fontSize; }
        public void setFontSize(int fontSize) { this.fontSize = fontSize; }

        @Override
        public MessagesSettings clone() {
            try {
                return (MessagesSettings) super.clone();
            } catch (CloneNotSupportedException e) {
                MessagesSettings c = new MessagesSettings();
                c.fontFamily = this.fontFamily;
                c.fontSize = this.fontSize;
                return c;
            }
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SqlSettings implements Serializable, Cloneable {
        private int maxRows = 0; // 0 = unlimited
        private int timeoutSeconds = 30;
        private int sqlprotection = 0; // 0 = off, 1 = warn
        private boolean stopOnError = true;
        private boolean perTabSession = true;
        private boolean autoShowResultsPanel = true;

        public int getMaxRows() { return maxRows; }
        public void setMaxRows(int maxRows) { this.maxRows = maxRows; }
        public int getTimeoutSeconds() { return timeoutSeconds; }
        public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
        public int getSqlprotection() { return sqlprotection; }
        public void setSqlprotection(int sqlprotection) { this.sqlprotection = sqlprotection; }
        public boolean isStopOnError() { return stopOnError; }
        public void setStopOnError(boolean stopOnError) { this.stopOnError = stopOnError; }
        public boolean isPerTabSession() { return perTabSession; }
        public void setPerTabSession(boolean perTabSession) { this.perTabSession = perTabSession; }
        public boolean isAutoShowResultsPanel() { return autoShowResultsPanel; }
        public void setAutoShowResultsPanel(boolean autoShowResultsPanel) { this.autoShowResultsPanel = autoShowResultsPanel; }

        @Override
        public SqlSettings clone() {
            try {
                return (SqlSettings) super.clone();
            } catch (CloneNotSupportedException e) {
                SqlSettings c = new SqlSettings();
                c.maxRows = this.maxRows;
                c.timeoutSeconds = this.timeoutSeconds;
                c.sqlprotection = this.sqlprotection;
                c.stopOnError = this.stopOnError;
                c.perTabSession = this.perTabSession;
                c.autoShowResultsPanel = this.autoShowResultsPanel;
                return c;
            }
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AddonsSettings implements Serializable, Cloneable {
        private BravoToolSettings bravo_tool = new BravoToolSettings();

        public BravoToolSettings getBravo_tool() { return bravo_tool; }
        public void setBravo_tool(BravoToolSettings bravo_tool) { this.bravo_tool = bravo_tool; }

        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class BravoToolSettings implements Serializable, Cloneable {
            private boolean enabled = true;
            public boolean isEnabled() { return enabled; }
            public void setEnabled(boolean enabled) { this.enabled = enabled; }

            @Override
            public BravoToolSettings clone() {
                try {
                    return (BravoToolSettings) super.clone();
                } catch (Exception e) {
                    BravoToolSettings c = new BravoToolSettings();
                    c.enabled = this.enabled;
                    return c;
                }
            }
        }

        @Override
        public AddonsSettings clone() {
            AddonsSettings c = new AddonsSettings();
            c.bravo_tool = this.bravo_tool != null ? this.bravo_tool.clone() : new BravoToolSettings();
            return c;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SessionSettings implements Serializable, Cloneable {
        private boolean restoreOnStartup = true;
        private boolean saveSqlContent = true;
        private long maxTabSizeBytes = 1048576; // 1 MB
        private int autoSaveIntervalSeconds = 3;

        public boolean isRestoreOnStartup() { return restoreOnStartup; }
        public void setRestoreOnStartup(boolean restoreOnStartup) { this.restoreOnStartup = restoreOnStartup; }
        public boolean isSaveSqlContent() { return saveSqlContent; }
        public void setSaveSqlContent(boolean saveSqlContent) { this.saveSqlContent = saveSqlContent; }
        public long getMaxTabSizeBytes() { return maxTabSizeBytes; }
        public void setMaxTabSizeBytes(long maxTabSizeBytes) { this.maxTabSizeBytes = maxTabSizeBytes; }
        public int getAutoSaveIntervalSeconds() { return autoSaveIntervalSeconds; }
        public void setAutoSaveIntervalSeconds(int autoSaveIntervalSeconds) { this.autoSaveIntervalSeconds = autoSaveIntervalSeconds; }

        @Override
        public SessionSettings clone() {
            try {
                return (SessionSettings) super.clone();
            } catch (CloneNotSupportedException e) {
                SessionSettings c = new SessionSettings();
                c.restoreOnStartup = this.restoreOnStartup;
                c.saveSqlContent = this.saveSqlContent;
                c.maxTabSizeBytes = this.maxTabSizeBytes;
                c.autoSaveIntervalSeconds = this.autoSaveIntervalSeconds;
                return c;
            }
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class UpdatesSettings implements Serializable, Cloneable {
        private boolean checkOnStartup = true;
        private String updateUrl = "";
        private String lastUpdated = "";
        private String installedVersion = "1.2.6";

        public boolean isCheckOnStartup() { return checkOnStartup; }
        public void setCheckOnStartup(boolean checkOnStartup) { this.checkOnStartup = checkOnStartup; }
        public String getUpdateUrl() { return updateUrl; }
        public void setUpdateUrl(String updateUrl) { this.updateUrl = updateUrl; }
        public String getLastUpdated() { return lastUpdated; }
        public void setLastUpdated(String lastUpdated) { this.lastUpdated = lastUpdated; }
        public String getInstalledVersion() { return installedVersion; }
        public void setInstalledVersion(String installedVersion) { this.installedVersion = installedVersion; }

        @Override
        public UpdatesSettings clone() {
            try {
                return (UpdatesSettings) super.clone();
            } catch (CloneNotSupportedException e) {
                UpdatesSettings c = new UpdatesSettings();
                c.checkOnStartup = this.checkOnStartup;
                c.updateUrl = this.updateUrl;
                c.lastUpdated = this.lastUpdated;
                c.installedVersion = this.installedVersion;
                return c;
            }
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SyntaxSettings implements Serializable, Cloneable {
        private boolean pipeline = true;
        private boolean shadow = false;

        public boolean isPipeline() { return pipeline; }
        public void setPipeline(boolean pipeline) { this.pipeline = pipeline; }
        public boolean isShadow() { return shadow; }
        public void setShadow(boolean shadow) { this.shadow = shadow; }

        @Override
        public SyntaxSettings clone() {
            try {
                return (SyntaxSettings) super.clone();
            } catch (CloneNotSupportedException e) {
                SyntaxSettings c = new SyntaxSettings();
                c.pipeline = this.pipeline;
                c.shadow = this.shadow;
                return c;
            }
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class HistorySettings implements Serializable, Cloneable {
        private boolean enabled = true;
        private int maxEntriesPerDoc = 200;
        private int maxAgeDays = 90;
        private int maxDbSizeMb = 500;
        private int maxEntrySizeKb = 1024;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public int getMaxEntriesPerDoc() { return maxEntriesPerDoc; }
        public void setMaxEntriesPerDoc(int maxEntriesPerDoc) { this.maxEntriesPerDoc = maxEntriesPerDoc; }
        public int getMaxAgeDays() { return maxAgeDays; }
        public void setMaxAgeDays(int maxAgeDays) { this.maxAgeDays = maxAgeDays; }
        public int getMaxDbSizeMb() { return maxDbSizeMb; }
        public void setMaxDbSizeMb(int maxDbSizeMb) { this.maxDbSizeMb = maxDbSizeMb; }
        public int getMaxEntrySizeKb() { return maxEntrySizeKb; }
        public void setMaxEntrySizeKb(int maxEntrySizeKb) { this.maxEntrySizeKb = maxEntrySizeKb; }

        @Override
        public HistorySettings clone() {
            try {
                return (HistorySettings) super.clone();
            } catch (CloneNotSupportedException e) {
                HistorySettings c = new HistorySettings();
                c.enabled = this.enabled;
                c.maxEntriesPerDoc = this.maxEntriesPerDoc;
                c.maxAgeDays = this.maxAgeDays;
                c.maxDbSizeMb = this.maxDbSizeMb;
                c.maxEntrySizeKb = this.maxEntrySizeKb;
                return c;
            }
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class LoggingSettings implements Serializable, Cloneable {
        private String level = "INFO";
        private boolean enabled = true;
        private int maxFileSizeMb = 2;
        private int maxFiles = 5;

        public String getLevel() { return level; }
        public void setLevel(String level) { this.level = level; }
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public int getMaxFileSizeMb() { return maxFileSizeMb; }
        public void setMaxFileSizeMb(int maxFileSizeMb) { this.maxFileSizeMb = maxFileSizeMb; }
        public int getMaxFiles() { return maxFiles; }
        public void setMaxFiles(int maxFiles) { this.maxFiles = maxFiles; }

        @Override
        public LoggingSettings clone() {
            try {
                return (LoggingSettings) super.clone();
            } catch (CloneNotSupportedException e) {
                LoggingSettings c = new LoggingSettings();
                c.level = this.level;
                c.enabled = this.enabled;
                c.maxFileSizeMb = this.maxFileSizeMb;
                c.maxFiles = this.maxFiles;
                return c;
            }
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TypographySettings implements Serializable, Cloneable {
        private Map<String, Object> ui = new HashMap<>(Map.of("family", "Inter", "size", 13));
        private Map<String, Object> editor = new HashMap<>(Map.of("family", "Cascadia Mono Light", "size", 13));
        private Map<String, Object> grid = new HashMap<>(Map.of("family", "Inter", "size", 12));
        private Map<String, Object> messages = new HashMap<>(Map.of("family", "Fira Code", "size", 11));

        public Map<String, Object> getUi() { return ui; }
        public void setUi(Map<String, Object> ui) { this.ui = ui; }
        public Map<String, Object> getEditor() { return editor; }
        public void setEditor(Map<String, Object> editor) { this.editor = editor; }
        public Map<String, Object> getGrid() { return grid; }
        public void setGrid(Map<String, Object> grid) { this.grid = grid; }
        public Map<String, Object> getMessages() { return messages; }
        public void setMessages(Map<String, Object> messages) { this.messages = messages; }

        @Override
        public TypographySettings clone() {
            TypographySettings c = new TypographySettings();
            if (this.ui != null) c.ui = new HashMap<>(this.ui);
            if (this.editor != null) c.editor = new HashMap<>(this.editor);
            if (this.grid != null) c.grid = new HashMap<>(this.grid);
            if (this.messages != null) c.messages = new HashMap<>(this.messages);
            return c;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class QuerySettings implements Serializable, Cloneable {
        private int defaultLimit = 0;
        private int timeoutSeconds = 60;

        public int getDefaultLimit() { return defaultLimit; }
        public void setDefaultLimit(int defaultLimit) { this.defaultLimit = defaultLimit; }
        public int getTimeoutSeconds() { return timeoutSeconds; }
        public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }

        @Override
        public QuerySettings clone() {
            try {
                return (QuerySettings) super.clone();
            } catch (CloneNotSupportedException e) {
                QuerySettings c = new QuerySettings();
                c.defaultLimit = this.defaultLimit;
                c.timeoutSeconds = this.timeoutSeconds;
                return c;
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Top-Level Getters & Setters
    // ─────────────────────────────────────────────────────────────

    public String getTheme() { return theme; }
    public void setTheme(String theme) {
        this.theme = theme;
        if (this.appearance != null) {
            this.appearance.setTheme(theme);
        }
    }

    public String getInstalledVersion() { return installedVersion; }
    public void setInstalledVersion(String installedVersion) { this.installedVersion = installedVersion; }

    public boolean isAutoHideNavbar() { return autoHideNavbar; }
    public void setAutoHideNavbar(boolean autoHideNavbar) {
        this.autoHideNavbar = autoHideNavbar;
        if (this.appearance != null) {
            this.appearance.setAutoHideNavbar(autoHideNavbar);
        }
    }

    public EditorSettings getEditor() { return editor; }
    public void setEditor(EditorSettings editor) { this.editor = editor; }

    public AppearanceSettings getAppearance() { return appearance; }
    public void setAppearance(AppearanceSettings appearance) { this.appearance = appearance; }

    public GridSettings getGrid() { return grid; }
    public void setGrid(GridSettings grid) { this.grid = grid; }

    public MessagesSettings getMessages() { return messages; }
    public void setMessages(MessagesSettings messages) { this.messages = messages; }

    public SqlSettings getSql() { return sql; }
    public void setSql(SqlSettings sql) { this.sql = sql; }

    public AddonsSettings getAddons() { return addons; }
    public void setAddons(AddonsSettings addons) { this.addons = addons; }

    public SessionSettings getSession() { return session; }
    public void setSession(SessionSettings session) { this.session = session; }

    public UpdatesSettings getUpdates() { return updates; }
    public void setUpdates(UpdatesSettings updates) { this.updates = updates; }

    public SyntaxSettings getSyntax() { return syntax; }
    public void setSyntax(SyntaxSettings syntax) { this.syntax = syntax; }

    public HistorySettings getHistory() { return history; }
    public void setHistory(HistorySettings history) { this.history = history; }

    public LoggingSettings getLogging() { return logging; }
    public void setLogging(LoggingSettings logging) { this.logging = logging; }

    public TypographySettings getTypography() { return typography; }
    public void setTypography(TypographySettings typography) { this.typography = typography; }

    public QuerySettings getQuery() { return query; }
    public void setQuery(QuerySettings query) { this.query = query; }

    // ─────────────────────────────────────────────────────────────
    // Backward-Compatibility Convenience Methods
    // ─────────────────────────────────────────────────────────────

    public String getAppName() { return "SQL IDE"; }
    public void setAppName(String name) { }
    public String getEnvironment() { return "Production"; }
    public void setEnvironment(String env) { }

    public boolean isRestoreSessionOnStartup() { return session.isRestoreOnStartup(); }
    public void setRestoreSessionOnStartup(boolean v) { session.setRestoreOnStartup(v); }

    public boolean isAutoSave() { return session.isSaveSqlContent(); }
    public void setAutoSave(boolean v) { session.setSaveSqlContent(v); }

    public int getAutoSaveIntervalSeconds() { return session.getAutoSaveIntervalSeconds(); }
    public void setAutoSaveIntervalSeconds(int v) { session.setAutoSaveIntervalSeconds(v); }

    @com.fasterxml.jackson.annotation.JsonIgnore
    public String getPanelStyle() { return appearance != null && appearance.getPanelStyle() != null ? appearance.getPanelStyle() : "rounded"; }
    @com.fasterxml.jackson.annotation.JsonIgnore
    public boolean isFlatPanels() { return "flat".equalsIgnoreCase(getPanelStyle()); }

    public String getDensity() { return appearance.getDensity(); }
    public void setDensity(String v) { appearance.setDensity(v); }

    public String getUiFontFamily() { return appearance.getUiFontFamily(); }
    public void setUiFontFamily(String v) {
        appearance.setUiFontFamily(v);
        if (typography != null && typography.getUi() != null) {
            typography.getUi().put("family", v);
            typography.getUi().put("fontFamily", v);
        }
    }

    public int getUiFontSize() { return appearance.getUiFontSize(); }
    public void setUiFontSize(int v) {
        appearance.setUiFontSize(v);
        if (typography != null && typography.getUi() != null) {
            typography.getUi().put("size", v);
            typography.getUi().put("fontSize", v);
        }
    }

    public String getEditorFontFamily() { return editor.getFontFamily(); }
    public void setEditorFontFamily(String v) {
        editor.setFontFamily(v);
        if (typography != null && typography.getEditor() != null) {
            typography.getEditor().put("family", v);
            typography.getEditor().put("fontFamily", v);
        }
    }

    public int getEditorFontSize() { return editor.getFontSize(); }
    public void setEditorFontSize(int v) {
        editor.setFontSize(v);
        if (typography != null && typography.getEditor() != null) {
            typography.getEditor().put("size", v);
            typography.getEditor().put("fontSize", v);
        }
    }

    public int getTabSize() { return editor.getTabSize(); }
    public void setTabSize(int v) { editor.setTabSize(v); }

    public boolean isInsertSpaces() { return editor.isInsertSpaces(); }
    public void setInsertSpaces(boolean v) { editor.setInsertSpaces(v); }

    public boolean isWordWrap() { return editor.isWordWrap(); }
    public void setWordWrap(boolean v) { editor.setWordWrap(v); }

    public boolean isShowLineNumbers() { return true; }
    public void setShowLineNumbers(boolean v) { }

    public boolean isMinimap() { return editor.isMinimap(); }
    public void setMinimap(boolean v) { editor.setMinimap(v); }

    public boolean isIntellisense() { return editor.isIntellisense(); }
    public void setIntellisense(boolean v) { editor.setIntellisense(v); }

    public String getKeywordCase() { return editor.getKeywordCase(); }
    public void setKeywordCase(String v) { editor.setKeywordCase(v); }

    public int getMaxRows() { return sql.getMaxRows() > 0 ? sql.getMaxRows() : 5000; }
    public void setMaxRows(int v) { sql.setMaxRows(v); }

    public int getGridFontSize() { return grid.getFontSize(); }
    public void setGridFontSize(int v) {
        grid.setFontSize(v);
        if (typography != null && typography.getGrid() != null) {
            typography.getGrid().put("size", v);
            typography.getGrid().put("fontSize", v);
        }
    }

    public String getNullDisplayString() { return grid.getNullDisplayString(); }
    public void setNullDisplayString(String v) { grid.setNullDisplayString(v); }

    public boolean isShowRowNumbers() { return grid.isShowRowNumbers(); }
    public void setShowRowNumbers(boolean v) { grid.setShowRowNumbers(v); }

    public int getQueryTimeoutSeconds() { return sql.getTimeoutSeconds(); }
    public void setQueryTimeoutSeconds(int v) {
        sql.setTimeoutSeconds(v);
        query.setTimeoutSeconds(v);
    }

    public boolean isStopOnError() { return sql.isStopOnError(); }
    public void setStopOnError(boolean v) { sql.setStopOnError(v); }

    public boolean isAutoShowResultsPanel() { return sql.isAutoShowResultsPanel(); }
    public void setAutoShowResultsPanel(boolean v) { sql.setAutoShowResultsPanel(v); }

    // ─────────────────────────────────────────────────────────────
    // JSON Serialization Helpers
    // ─────────────────────────────────────────────────────────────

    public String toJsonString() {
        try {
            return MAPPER.writeValueAsString(this);
        } catch (Exception e) {
            return "{}";
        }
    }

    public static IdeSettings fromJsonString(String json) throws Exception {
        return MAPPER.readValue(json, IdeSettings.class);
    }

    @Override
    public IdeSettings clone() {
        try {
            IdeSettings copy = (IdeSettings) super.clone();
            if (this.editor != null) copy.editor = this.editor.clone();
            if (this.appearance != null) copy.appearance = this.appearance.clone();
            if (this.grid != null) copy.grid = this.grid.clone();
            if (this.messages != null) copy.messages = this.messages.clone();
            if (this.sql != null) copy.sql = this.sql.clone();
            if (this.addons != null) copy.addons = this.addons.clone();
            if (this.session != null) copy.session = this.session.clone();
            if (this.updates != null) copy.updates = this.updates.clone();
            if (this.syntax != null) copy.syntax = this.syntax.clone();
            if (this.history != null) copy.history = this.history.clone();
            if (this.logging != null) copy.logging = this.logging.clone();
            if (this.typography != null) copy.typography = this.typography.clone();
            if (this.query != null) copy.query = this.query.clone();
            return copy;
        } catch (CloneNotSupportedException e) {
            try {
                return fromJsonString(this.toJsonString());
            } catch (Exception ex) {
                return new IdeSettings();
            }
        }
    }
}
