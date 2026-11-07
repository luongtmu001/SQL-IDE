package app.ui.settings;

import javafx.beans.value.ObservableValue;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;
import org.controlsfx.control.ToggleSwitch;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

/**
 * SCR-SET-001 Phase 5 (Gate B 2026-11-05): turns the FXML rows
 * {@code HBox.settings-row [VBox(label, "key — description"), control]} into VS Code style items:
 *
 * <pre>
 *  Title (English name, muted)                                  ⋯
 *  Description · editor.fontSize
 *  [control]                   (check boxes: [x] Description on one line)
 * </pre>
 *
 * A row whose value differs from the saved one gets an accent bar on the left ("modified");
 * the ⋯ menu reverts that row or copies the setting key. Tracks the number of unsaved changes.
 */
public final class SettingsRowStyler {

    /** One restyled setting. */
    public static final class Item {
        final VBox node;
        final String key;
        final String searchText;
        final List<Binding> bindings = new ArrayList<>();

        Item(VBox node, String key, String searchText) {
            this.node = node;
            this.key = key;
            this.searchText = searchText;
        }

        public VBox node() {
            return node;
        }

        boolean modified() {
            return bindings.stream().anyMatch(Binding::modified);
        }
    }

    /** Value of one control: getter, setter and the value at the last save. */
    private static final class Binding {
        final Supplier<Object> get;
        final Consumer<Object> set;
        Object saved;

        Binding(Supplier<Object> get, Consumer<Object> set) {
            this.get = get;
            this.set = set;
        }

        boolean modified() {
            return !Objects.equals(norm(get.get()), norm(saved));
        }

        static Object norm(Object o) {
            return o instanceof String s ? s.trim() : o;
        }
    }

    /**
     * S3 (2026-11-06): settings that are stored (same file as LLTool) but have no effect in this version yet,
     * so the user is not misled. Key = setting key, or "section.*". Value = {badge, tooltip}.
     */
    private static final java.util.Map<String, String[]> NOTES = java.util.Map.of(
            "editor.keywordCase", new String[]{"Chưa hỗ trợ", "Được lưu lại; có tác dụng khi có bộ định dạng SQL (D6)."},
            "editor.syntaxCheck", new String[]{"Chưa hỗ trợ", "Được lưu lại; có tác dụng khi có kiểm tra cú pháp."},
            "sql.perTabSession", new String[]{"Luôn bật", "Bản Java luôn mở một phiên kết nối riêng cho mỗi tab; tùy chọn này không đổi được."},
            "history.*", new String[]{"Chưa hỗ trợ", "Được lưu lại; chưa có lịch sử truy vấn trong bản Java."},
            "updates.*", new String[]{"Chưa hỗ trợ", "Được lưu lại; kiểm tra cập nhật đang là bản mẫu (D8)."});

    private final List<Item> items = new ArrayList<>();
    private IntConsumer onDirtyCount;
    private boolean suspended = false;

    public List<Item> items() {
        return items;
    }

    public void setOnDirtyCount(IntConsumer c) {
        onDirtyCount = c;
    }

    /** Restyles every {@code .settings-row} directly inside the given sections. */
    public void restyle(List<? extends Pane> sections) {
        for (Pane sec : sections) {
            List<Node> children = new ArrayList<>(sec.getChildren());
            for (Node n : children) {
                if (n instanceof HBox row && row.getStyleClass().contains("settings-row")) {
                    Item item = build(row);
                    if (item == null) continue;
                    int idx = sec.getChildren().indexOf(row);
                    sec.getChildren().set(idx, item.node);
                    items.add(item);
                }
            }
        }
    }

    private Item build(HBox row) {
        VBox textCol = null;
        List<Node> controls = new ArrayList<>();
        for (Node c : row.getChildren()) {
            if (textCol == null && c instanceof VBox v && v.getChildren().stream().anyMatch(x -> x instanceof Label)) {
                textCol = v;
            } else if (!(c instanceof Region r && r.getClass() == Region.class)) {
                controls.add(c);
            }
        }
        if (textCol == null) return null;
        List<Label> labels = new ArrayList<>();
        List<Node> extras = new ArrayList<>();
        for (Node c : textCol.getChildren()) {
            if (c instanceof Label l && labels.size() < 2) labels.add(l);
            else extras.add(c);
        }
        String title = labels.isEmpty() ? "" : labels.get(0).getText();
        String sub = labels.size() > 1 ? labels.get(1).getText() : "";

        // "Phông chữ soạn thảo (Editor Font Family)" -> bold Vietnamese + muted English
        String main = title, alt = "";
        int p = title.lastIndexOf(" (");
        if (p > 0 && title.endsWith(")")) {
            main = title.substring(0, p);
            alt = title.substring(p + 2, title.length() - 1);
        }
        // "editor.fontSize — description" -> description + key
        String key = "", desc = sub;
        int dash = sub.indexOf(" — ");
        if (dash > 0 && !sub.substring(0, dash).contains(" ")) {
            key = sub.substring(0, dash).trim();
            desc = sub.substring(dash + 3).trim();
        }

        Label mainLbl = new Label(main);
        mainLbl.getStyleClass().add("setting-title");
        Label altLbl = new Label(alt);
        altLbl.getStyleClass().add("setting-title-alt");
        altLbl.setVisible(!alt.isEmpty());
        altLbl.setManaged(!alt.isEmpty());
        Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        MenuButton more = new MenuButton();
        more.setGraphic(new FontIcon("fth-more-horizontal"));
        more.getStyleClass().add("setting-more");
        more.setFocusTraversable(false);
        HBox header = new HBox(6, mainLbl, altLbl, gap, more);
        String[] note = NOTES.get(key);
        if (note == null && key.contains(".")) note = NOTES.get(key.substring(0, key.indexOf('.')) + ".*");
        if (note != null) {
            Label badge = new Label(note[0]);
            badge.getStyleClass().add("setting-badge");
            badge.setTooltip(new Tooltip(note[1]));
            header.getChildren().add(2, badge);
        }
        header.setAlignment(Pos.CENTER_LEFT);

        Label descLbl = new Label(desc);
        descLbl.getStyleClass().add("setting-desc");
        descLbl.setWrapText(true);
        descLbl.setMinWidth(0);
        Label keyLbl = new Label(key);
        keyLbl.getStyleClass().add("setting-key");
        keyLbl.setVisible(!key.isEmpty());
        keyLbl.setManaged(!key.isEmpty());

        VBox node = new VBox(4);
        node.getStyleClass().add("settings-item");
        node.getChildren().add(header);

        boolean singleCheck = controls.size() == 1 && (controls.get(0) instanceof CheckBox || controls.get(0) instanceof ToggleSwitch);
        if (singleCheck) {
            // VS Code: [x] description on one line
            Node cb = controls.get(0);
            if (cb instanceof CheckBox c) c.setText(null);
            HBox line = new HBox(8, cb, descLbl);
            line.setAlignment(Pos.CENTER_LEFT);
            node.getChildren().addAll(line, keyLbl);
        } else {
            HBox descLine = new HBox(6, descLbl);
            descLine.setAlignment(Pos.CENTER_LEFT);
            node.getChildren().addAll(descLine, keyLbl);
            if (!controls.isEmpty()) {
                HBox ctl = new HBox(8);
                ctl.setAlignment(Pos.CENTER_LEFT);
                ctl.getStyleClass().add("setting-control");
                ctl.getChildren().addAll(controls);
                node.getChildren().add(ctl);
            }
        }
        node.getChildren().addAll(extras);

        String search = (title + " " + sub + " " + key).toLowerCase(Locale.ROOT);
        Item item = new Item(node, key, search);
        for (Node c : controls) collectBindings(c, item);

        MenuItem revert = new MenuItem("Hoàn tác thay đổi");
        revert.setGraphic(new FontIcon("fth-rotate-ccw"));
        revert.setOnAction(e -> {
            for (Binding b : item.bindings) b.set.accept(b.saved);
            refresh(item);
        });
        MenuItem copyKey = new MenuItem("Sao chép tên cài đặt");
        copyKey.setGraphic(new FontIcon("fth-copy"));
        copyKey.setDisable(key.isEmpty());
        String k = key;
        copyKey.setOnAction(e -> {
            ClipboardContent cc = new ClipboardContent();
            cc.putString(k);
            Clipboard.getSystemClipboard().setContent(cc);
        });
        more.getItems().addAll(revert, copyKey);
        more.setOnShowing(e -> revert.setDisable(!item.modified()));
        return item;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void collectBindings(Node n, Item item) {
        Binding b = null;
        ObservableValue<?> watch = null;
        if (n instanceof ComboBoxBase cb) {
            b = new Binding(cb::getValue, v -> cb.setValue(v));
            watch = cb.valueProperty();
        } else if (n instanceof ChoiceBox cb) {
            b = new Binding(cb::getValue, v -> cb.setValue(v));
            watch = cb.valueProperty();
        } else if (n instanceof Spinner sp && sp.getValueFactory() != null) {
            SpinnerValueFactory f = sp.getValueFactory();
            b = new Binding(f::getValue, v -> {
                if (v != null) f.setValue(v);
            });
            watch = f.valueProperty();
        } else if (n instanceof CheckBox c) {
            b = new Binding(c::isSelected, v -> c.setSelected(Boolean.TRUE.equals(v)));
            watch = c.selectedProperty();
        } else if (n instanceof ToggleSwitch t) {
            b = new Binding(t::isSelected, v -> t.setSelected(Boolean.TRUE.equals(v)));
            watch = t.selectedProperty();
        } else if (n instanceof ToggleButton t) {
            b = new Binding(t::isSelected, v -> t.setSelected(Boolean.TRUE.equals(v)));
            watch = t.selectedProperty();
        } else if (n instanceof TextInputControl t) {
            b = new Binding(t::getText, v -> t.setText(v != null ? v.toString() : ""));
            watch = t.textProperty();
        } else if (n instanceof Slider s) {
            b = new Binding(s::getValue, v -> {
                if (v instanceof Number num) s.setValue(num.doubleValue());
            });
            watch = s.valueProperty();
        } else if (n instanceof Parent parent && !(n instanceof Control)) {
            for (Node c : parent.getChildrenUnmodifiable()) collectBindings(c, item);
            return;
        }
        if (b == null) return;
        b.saved = b.get.get();
        item.bindings.add(b);
        watch.addListener((o, a, v) -> {
            if (!suspended) refresh(item);
        });
    }

    private void refresh(Item item) {
        item.node.getStyleClass().remove("modified");
        if (item.modified()) item.node.getStyleClass().add("modified");
        if (onDirtyCount != null) onDirtyCount.accept(dirtyCount());
    }

    public int dirtyCount() {
        int n = 0;
        for (Item i : items) if (i.modified()) n++;
        return n;
    }

    /** Current values become the saved ones (after load / save). */
    public void snapshot() {
        for (Item i : items) {
            for (Binding b : i.bindings) b.saved = b.get.get();
            i.node.getStyleClass().remove("modified");
        }
        if (onDirtyCount != null) onDirtyCount.accept(0);
    }

    /** Values are being loaded programmatically: no dirty updates until {@link #resume()}. */
    public void suspend() {
        suspended = true;
    }

    public void resume() {
        suspended = false;
        for (Item i : items) refresh(i);
    }

    /** Search: shows matching items; returns how many matched. */
    public int filter(String query) {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        int n = 0;
        for (Item i : items) {
            boolean m = q.isEmpty() || i.searchText.contains(q);
            i.node.setVisible(m);
            i.node.setManaged(m);
            if (m) n++;
        }
        return n;
    }
}
