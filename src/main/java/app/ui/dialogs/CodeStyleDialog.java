package app.ui.dialogs;

import app.ui.codestyle.SimpleSqlFormatter;
import app.ui.codestyle.SqlStyleMetadata;
import app.ui.codestyle.SqlStyleMetadata.Category;
import app.ui.codestyle.SqlStyleMetadata.Option;
import app.ui.codestyle.SqlStyleStore;
import app.ui.codestyle.SqlStyleStore.Style;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.File;
import java.nio.file.Files;
import java.util.*;
import java.util.function.Supplier;

/**
 * SCR-DLG-009 SQL code style manager (Phase 5, Gate B 2026-11-05: full UI + preview).
 * Styles: read-only system style + user styles (new / duplicate / rename / set default /
 * export / import / delete). 15 categories in 4 groups, 31 options (LLTool metadata),
 * live preview with the sample of the category or the statement of the active tab.
 */
public final class CodeStyleDialog {

    private final SqlStyleMetadata meta = SqlStyleMetadata.get();
    private final SqlStyleStore.Data data = SqlStyleStore.load();
    private final Supplier<String> currentSql;
    private DialogChrome chrome;

    private final ComboBox<Style> styleBox = new ComboBox<>();
    private final VBox navBox = new VBox(2);
    private final VBox optionsBox = new VBox(14);
    private final TextField search = new TextField();
    /** Preview = Monaco (SQL highlighting); the user can type / paste SQL and format it (2026-11-05). */
    private app.ui.editor.EmbeddedMonaco preview;
    private final TextArea previewFallback = new TextArea();
    /** true once the user typed in the preview: option / category changes then reformat that text. */
    private boolean userText = false;
    private final CheckBox useCurrent = new CheckBox("Xem trước bằng câu lệnh đang mở");
    private final ToggleGroup dialect = new ToggleGroup();
    private final Label status = new Label();
    private final Label readOnlyHint = new Label("Style hệ thống chỉ đọc. Bấm Nhân bản để tạo style của bạn rồi chỉnh.");
    private final Button deleteBtn = DialogChrome.secondary("");
    private final Button renameBtn = DialogChrome.secondary("Đổi tên");
    private final Button defaultBtn = DialogChrome.secondary("Đặt mặc định");

    private String categoryId;
    private boolean dirty = false;
    private final Map<String, Node> controls = new HashMap<>();

    private CodeStyleDialog(Supplier<String> currentSql) {
        this.currentSql = currentSql;
        this.categoryId = meta.categories().isEmpty() ? "" : meta.categories().get(0).id();
    }

    /** @param currentSql statement of the active editor tab (for "Xem trước bằng câu lệnh đang mở") */
    public static void show(Window owner, Supplier<String> currentSql) {
        new CodeStyleDialog(currentSql).open(owner);
    }

    private Style editing() {
        return styleBox.getValue();
    }

    // ------------------------------------------------------------------ layout

    private void open(Window owner) {
        chrome = DialogChrome.create(owner, "Code style SQL", "fth-align-left")
                .subtitle("Định dạng mã SQL (Format)");

        // ---- toolbar
        styleBox.setItems(FXCollections.observableArrayList(data.styles));
        styleBox.setCellFactory(lv -> new StyleCell());
        styleBox.setButtonCell(new StyleCell());
        styleBox.setPrefWidth(250);
        styleBox.getSelectionModel().select(data.styles.stream()
                .filter(s -> s.id.equals(data.activeStyleId)).findFirst().orElse(data.styles.get(0)));
        styleBox.valueProperty().addListener((o, a, b) -> refreshAll());

        Button newBtn = iconButton("Mới", "fth-plus");
        newBtn.setOnAction(e -> addStyle(new Style(), "Style mới"));
        Button dupBtn = iconButton("Nhân bản", "fth-copy");
        dupBtn.setOnAction(e -> addStyle(editing(), editing().name + " (bản sao)"));
        renameBtn.setOnAction(e -> renameStyle());
        defaultBtn.setOnAction(e -> {
            data.activeStyleId = editing().id;
            markDirty();
            styleBox.setButtonCell(new StyleCell());
            refreshToolbar();
        });
        Button exportBtn = iconOnly("fth-download", "Xuất style ra file .json");
        exportBtn.setOnAction(e -> exportStyle());
        Button importBtn = iconOnly("fth-upload", "Nhập style từ file .json");
        importBtn.setOnAction(e -> importStyle());
        deleteBtn.setGraphic(new FontIcon("fth-trash-2"));
        deleteBtn.getStyleClass().add("cs-danger-icon");
        deleteBtn.setTooltip(new Tooltip("Xóa style"));
        deleteBtn.setOnAction(e -> deleteStyle());

        Label styleLbl = new Label("Style");
        styleLbl.getStyleClass().add("dialog-label");
        Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        HBox toolbar = new HBox(6, styleLbl, styleBox, newBtn, dupBtn, renameBtn, defaultBtn, gap, exportBtn, importBtn, deleteBtn);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.getStyleClass().add("cs-toolbar");

        // ---- left: search + categories
        search.setPromptText("Tìm tùy chọn…");
        search.textProperty().addListener((o, a, b) -> {
            buildNav();
            buildOptions();
        });
        ScrollPane navScroll = new ScrollPane(navBox);
        navScroll.setFitToWidth(true);
        navScroll.getStyleClass().add("cs-scroll");
        VBox.setVgrow(navScroll, Priority.ALWAYS);
        VBox left = new VBox(8, search, navScroll);
        left.getStyleClass().add("cs-left");
        left.setPrefWidth(230);
        left.setMinWidth(200);

        // ---- middle: options
        readOnlyHint.getStyleClass().add("cs-readonly-hint");
        readOnlyHint.setWrapText(true);
        ScrollPane optScroll = new ScrollPane(optionsBox);
        optScroll.setFitToWidth(true);
        optScroll.getStyleClass().add("cs-scroll");
        optionsBox.getStyleClass().add("cs-options");
        VBox.setVgrow(optScroll, Priority.ALWAYS);
        VBox middle = new VBox(8, readOnlyHint, optScroll);
        middle.getStyleClass().add("cs-middle");
        HBox.setHgrow(middle, Priority.ALWAYS);
        middle.setPrefWidth(380);

        // ---- right: preview
        ToggleButton mssql = new ToggleButton("SQL Server");
        ToggleButton pg = new ToggleButton("PostgreSQL");
        mssql.setToggleGroup(dialect);
        pg.setToggleGroup(dialect);
        mssql.setSelected(true);
        mssql.getStyleClass().addAll("cs-seg", "left-pill");
        pg.getStyleClass().addAll("cs-seg", "right-pill");
        dialect.selectedToggleProperty().addListener((o, a, b) -> {
            if (b == null) a.setSelected(true);
            refreshPreview();
        });
        Label pvTitle = new Label("Xem trước");
        pvTitle.getStyleClass().add("cs-preview-title");
        Region pvGap = new Region();
        HBox.setHgrow(pvGap, Priority.ALWAYS);
        HBox pvHead = new HBox(6, pvTitle, pvGap, mssql, pg);
        pvHead.setAlignment(Pos.CENTER_LEFT);
        Node previewNode;
        try {
            preview = new app.ui.editor.EmbeddedMonaco("code-style-preview", "sql", "");
            preview.setOnUserEdit(t -> userText = true);
            previewNode = preview.node();
        } catch (Throwable t) {
            preview = null;
            previewFallback.getStyleClass().add("cs-preview");
            previewNode = previewFallback;
        }
        previewNode.getStyleClass().add("cs-preview-editor");
        VBox.setVgrow(previewNode, Priority.ALWAYS);
        Button formatBtn = DialogChrome.secondary("Format");
        formatBtn.setGraphic(new FontIcon("fth-align-left"));
        formatBtn.setTooltip(new Tooltip("Định dạng SQL trong khung theo style đang chọn"));
        formatBtn.setOnAction(e -> formatPreviewText());
        Button sampleBtn = DialogChrome.secondary("SQL mẫu");
        sampleBtn.setTooltip(new Tooltip("Trả về câu SQL mẫu của nhóm đang chọn"));
        sampleBtn.setOnAction(e -> {
            userText = false;
            useCurrent.setSelected(false);
            refreshPreview();
        });
        Label hint = new Label("Gõ hoặc dán SQL vào khung rồi bấm Format.");
        hint.getStyleClass().add("dialog-hint");
        hint.setMinWidth(0);
        Region pvGap2 = new Region();
        HBox.setHgrow(pvGap2, Priority.ALWAYS);
        HBox pvFoot = new HBox(6, useCurrent, pvGap2, sampleBtn, formatBtn);
        pvFoot.setAlignment(Pos.CENTER_LEFT);
        useCurrent.setDisable(currentSql == null);
        useCurrent.selectedProperty().addListener((o, a, b) -> {
            userText = false;
            refreshPreview();
        });
        VBox right = new VBox(8, pvHead, previewNode, hint, pvFoot);
        right.getStyleClass().add("cs-right");
        right.setPrefWidth(400);
        HBox.setHgrow(right, Priority.ALWAYS);

        HBox body = new HBox(left, middle, right);
        body.getStyleClass().add("cs-body");
        VBox.setVgrow(body, Priority.ALWAYS);
        VBox content = new VBox(toolbar, body);
        content.getStyleClass().add("cs-root");
        VBox.setVgrow(content, Priority.ALWAYS);

        // ---- footer
        status.getStyleClass().add("dialog-hint");
        // no "Đóng" button: the window's × closes (2026-11-05)
        Button save = DialogChrome.primary("Lưu");
        save.setDefaultButton(false);
        save.setOnAction(e -> {
            SqlStyleStore.save(data);
            dirty = false;
            refreshStatus();
        });

        chrome.rawContent(content).footer(new Node[]{status}, save).size(1060, 640);
        var css = CodeStyleDialog.class.getResource("/css/code-style.css");
        if (css != null) chrome.stage().getScene().getStylesheets().add(css.toExternalForm());
        app.services.ThemeService.getInstance().applyShape(chrome.stage().getScene());
        chrome.onCloseRequest(() -> !dirty || ConfirmDialog.show(chrome.stage(), "Đóng code style",
                "Có thay đổi chưa lưu.", "Đóng mà không lưu thì các thay đổi này sẽ mất.", "Đóng không lưu", true));
        refreshAll();
        chrome.showAndWait();
    }

    private static Button iconButton(String text, String icon) {
        Button b = DialogChrome.secondary(text);
        b.setGraphic(new FontIcon(icon));
        return b;
    }

    private static Button iconOnly(String icon, String tip) {
        Button b = DialogChrome.secondary("");
        b.setGraphic(new FontIcon(icon));
        b.setTooltip(new Tooltip(tip));
        return b;
    }

    /** Style name + "Hệ thống" / "Mặc định" badges. */
    private final class StyleCell extends ListCell<Style> {
        @Override
        protected void updateItem(Style s, boolean empty) {
            super.updateItem(s, empty);
            if (empty || s == null) {
                setText(null);
                setGraphic(null);
                return;
            }
            Label name = new Label(s.name);
            HBox box = new HBox(6, name);
            box.setAlignment(Pos.CENTER_LEFT);
            if (s.system) box.getChildren().add(badge("Hệ thống", "system"));
            if (s.id.equals(data.activeStyleId)) box.getChildren().add(badge("Mặc định", "default"));
            setText(null);
            setGraphic(box);
        }

        private Label badge(String t, String cls) {
            Label l = new Label(t);
            l.getStyleClass().addAll("cs-badge", cls);
            return l;
        }
    }

    // ------------------------------------------------------------------ refresh

    private void refreshAll() {
        refreshToolbar();
        buildNav();
        buildOptions();
        refreshPreview();
        refreshStatus();
    }

    private void refreshToolbar() {
        boolean sys = editing() == null || editing().system;
        deleteBtn.setDisable(sys);
        renameBtn.setDisable(sys);
        defaultBtn.setDisable(editing() != null && editing().id.equals(data.activeStyleId));
        readOnlyHint.setVisible(sys);
        readOnlyHint.setManaged(sys);
    }

    private void refreshStatus() {
        status.setText(dirty ? "Có thay đổi chưa lưu" : "Đã lưu");
        status.getStyleClass().removeAll("dialog-success", "cs-dirty");
        status.getStyleClass().add(dirty ? "cs-dirty" : "dialog-success");
    }

    private void markDirty() {
        dirty = true;
        refreshStatus();
    }

    private String query() {
        return search.getText() == null ? "" : search.getText().trim().toLowerCase(Locale.ROOT);
    }

    private boolean matches(Option o, String q) {
        return q.isEmpty() || o.label().toLowerCase(Locale.ROOT).contains(q) || o.desc().toLowerCase(Locale.ROOT).contains(q)
                || o.id().toLowerCase(Locale.ROOT).contains(q);
    }

    private void buildNav() {
        navBox.getChildren().clear();
        String q = query();
        String group = null;
        for (Category c : meta.categories()) {
            long count = meta.options(c.id()).stream().filter(o -> matches(o, q)).count();
            if (!q.isEmpty() && count == 0) continue;
            if (!c.groupLabel().equals(group)) {
                group = c.groupLabel();
                Label g = new Label(group.toUpperCase(Locale.ROOT));
                g.getStyleClass().add("cs-group");
                navBox.getChildren().add(g);
            }
            Button b = new Button(c.name());
            b.getStyleClass().add("cs-nav-item");
            b.setMaxWidth(Double.MAX_VALUE);
            b.setAlignment(Pos.CENTER_LEFT);
            Label cnt = new Label(String.valueOf(count));
            cnt.getStyleClass().add("cs-count");
            b.setGraphic(cnt);
            b.setContentDisplay(ContentDisplay.RIGHT);
            b.setGraphicTextGap(8);
            if (q.isEmpty() && c.id().equals(categoryId)) b.getStyleClass().add("active");
            b.setOnAction(e -> {
                categoryId = c.id();
                search.clear();
                buildNav();
                buildOptions();
                refreshPreview();
            });
            navBox.getChildren().add(b);
        }
    }

    private void buildOptions() {
        optionsBox.getChildren().clear();
        controls.clear();
        String q = query();
        List<Option> list = q.isEmpty() ? meta.options(categoryId)
                : meta.options().stream().filter(o -> matches(o, q)).toList();
        if (q.isEmpty()) {
            meta.categories().stream().filter(c -> c.id().equals(categoryId)).findFirst().ifPresent(c -> {
                Label t = new Label(c.name());
                t.getStyleClass().add("cs-cat-title");
                optionsBox.getChildren().add(t);
            });
        }
        if (list.isEmpty()) {
            Label none = new Label("Không có tùy chọn phù hợp.");
            none.getStyleClass().add("dialog-hint");
            optionsBox.getChildren().add(none);
        }
        for (Option o : list) optionsBox.getChildren().add(optionRow(o, !q.isEmpty()));
        applyDependencies();
    }

    private Node optionRow(Option o, boolean showCategory) {
        boolean ro = editing() == null || editing().system;
        Object v = editing() != null ? editing().values.getOrDefault(o.id(), o.defaultValue()) : o.defaultValue();
        Label title = new Label(o.label());
        title.getStyleClass().add("cs-opt-title");
        title.setWrapText(true);
        Label desc = new Label(o.desc());
        desc.getStyleClass().add("cs-opt-desc");
        desc.setWrapText(true);
        VBox row = new VBox(4);
        row.getStyleClass().add("cs-opt");
        if (showCategory) {
            meta.categories().stream().filter(c -> c.id().equals(o.category())).findFirst().ifPresent(c -> {
                Label cat = new Label(c.name());
                cat.getStyleClass().add("cs-opt-cat");
                row.getChildren().add(cat);
            });
        }
        Node control;
        switch (o.type()) {
            case "boolean" -> {
                CheckBox cb = new CheckBox(o.label());
                cb.setSelected(Boolean.TRUE.equals(v));
                cb.setWrapText(true);
                cb.selectedProperty().addListener((obs, a, b) -> set(o, b));
                control = cb;
                row.getChildren().addAll(cb, desc);
            }
            case "number" -> {
                int min = o.min() != null ? o.min().intValue() : 0, max = o.max() != null ? o.max().intValue() : 99;
                Spinner<Integer> sp = new Spinner<>(min, max, v instanceof Number n ? n.intValue() : min,
                        o.step() != null ? o.step().intValue() : 1);
                sp.setEditable(true);
                sp.setPrefWidth(100);
                sp.valueProperty().addListener((obs, a, b) -> set(o, b));
                control = sp;
                row.getChildren().addAll(title, desc, sp);
            }
            default -> {
                ComboBox<SqlStyleMetadata.Choice> cb = new ComboBox<>(FXCollections.observableArrayList(o.choices()));
                cb.setConverter(new javafx.util.StringConverter<>() {
                    @Override
                    public String toString(SqlStyleMetadata.Choice c) {
                        return c == null ? "" : c.label();
                    }

                    @Override
                    public SqlStyleMetadata.Choice fromString(String s) {
                        return null;
                    }
                });
                o.choices().stream().filter(c -> c.value().equals(String.valueOf(v))).findFirst().ifPresent(cb::setValue);
                cb.setMaxWidth(320);
                cb.valueProperty().addListener((obs, a, b) -> {
                    if (b != null) set(o, b.value());
                });
                control = cb;
                row.getChildren().addAll(title, desc, cb);
            }
        }
        control.setDisable(ro);
        controls.put(o.id(), control);
        return row;
    }

    private void set(Option o, Object value) {
        if (editing() == null || editing().system) return;
        editing().values.put(o.id(), value);
        markDirty();
        applyDependencies();
        refreshPreview();
    }

    /** Options that only make sense when another option has a given value are disabled otherwise. */
    private void applyDependencies() {
        boolean ro = editing() == null || editing().system;
        for (Option o : meta.options()) {
            Node c = controls.get(o.id());
            if (c == null) continue;
            boolean ok = true;
            for (SqlStyleMetadata.Dependency d : o.dependencies()) {
                Object other = editing() != null ? editing().values.get(d.optionId()) : null;
                if (d.equals() != null && !Objects.equals(String.valueOf(other), String.valueOf(d.equals()))) ok = false;
                if (d.notEquals() != null && Objects.equals(String.valueOf(other), String.valueOf(d.notEquals()))) ok = false;
            }
            c.setDisable(ro || !ok);
        }
    }

    private String previewText() {
        return preview != null ? preview.getText() : previewFallback.getText();
    }

    private void setPreviewText(String t) {
        if (preview != null) preview.setText(t); else previewFallback.setText(t);
    }

    private String format(String sql) {
        Map<String, Object> values = editing() != null ? editing().values : meta.defaults();
        try {
            return new SimpleSqlFormatter(values).format(sql);
        } catch (Exception e) {
            return sql;
        }
    }

    /** Formats what is in the preview now (the user's own SQL or the sample). */
    private void formatPreviewText() {
        String t = previewText();
        if (t == null || t.isBlank()) {
            refreshPreview();
            return;
        }
        setPreviewText(format(t));
    }

    /** Option / category / dialect change: reformat the user's SQL, or show the formatted sample. */
    private void refreshPreview() {
        if (userText) {
            formatPreviewText();
            return;
        }
        String sql = useCurrent.isSelected() && currentSql != null && currentSql.get() != null && !currentSql.get().isBlank()
                ? currentSql.get() : meta.sample(categoryId);
        boolean pg = dialect.getSelectedToggle() != null
                && "PostgreSQL".equals(((ToggleButton) dialect.getSelectedToggle()).getText());
        if (pg && !useCurrent.isSelected()) {
            sql = sql.replaceAll("\\[([^\\]]+)\\]", "\"$1\"").replaceAll("(?i)GETDATE\\(\\)", "NOW()")
                    .replaceAll("(?i)\\bISNULL\\(", "COALESCE(").replaceAll("(?i)\\bNVARCHAR\\b", "VARCHAR");
        }
        setPreviewText(format(sql));
    }

    // ------------------------------------------------------------------ style actions

    private void addStyle(Style from, String name) {
        Style s = (from == null || from.values.isEmpty() ? SqlStyleStore.systemStyle() : from)
                .copy(UUID.randomUUID().toString(), name);
        data.styles.add(s);
        styleBox.getItems().setAll(data.styles);
        styleBox.getSelectionModel().select(s);
        markDirty();
        renameStyle();
    }

    private void renameStyle() {
        Style s = editing();
        if (s == null || s.system) return;
        TextPromptDialog.show(chrome.stage(), "Đổi tên style", "fth-edit-2", "Tên style", s.name, "Lưu")
                .ifPresent(n -> {
                    s.name = n;
                    styleBox.getItems().setAll(data.styles);
                    styleBox.getSelectionModel().select(s);
                    markDirty();
                });
    }

    private void deleteStyle() {
        Style s = editing();
        if (s == null || s.system) return;
        if (!ConfirmDialog.show(chrome.stage(), "Xóa style", "Xóa style \"" + s.name + "\"?",
                "Các tab đang dùng style này sẽ chuyển về style hệ thống.", "Xóa style", true)) return;
        data.styles.remove(s);
        if (s.id.equals(data.activeStyleId)) data.activeStyleId = SqlStyleStore.SYSTEM_ID;
        styleBox.getItems().setAll(data.styles);
        styleBox.getSelectionModel().selectFirst();
        markDirty();
    }

    private void exportStyle() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Xuất code style");
        fc.setInitialFileName(editing().name.replaceAll("[^\\p{L}\\p{N}_-]+", "_") + ".sqlstyle.json");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Code style (*.json)", "*.json"));
        File f = fc.showSaveDialog(chrome.stage());
        if (f == null) return;
        try {
            Files.writeString(f.toPath(), SqlStyleStore.toJson(editing()));
            status.setText("Đã xuất: " + f.getName());
        } catch (Exception ex) {
            status.setText("Xuất lỗi: " + ex.getMessage());
        }
    }

    private void importStyle() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Nhập code style");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Code style (*.json)", "*.json"));
        File f = fc.showOpenDialog(chrome.stage());
        if (f == null) return;
        try {
            Style s = SqlStyleStore.fromJson(Files.readString(f.toPath()));
            data.styles.add(s);
            styleBox.getItems().setAll(data.styles);
            styleBox.getSelectionModel().select(s);
            markDirty();
        } catch (Exception ex) {
            status.setText("File không phải code style hợp lệ.");
        }
    }
}
