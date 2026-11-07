package app.ui.components;

import app.framework.metadata.PrincipalPermissions;
import app.framework.metadata.PrincipalPermissions.Membership;
import app.framework.metadata.PrincipalPermissions.Permission;
import app.framework.metadata.PrincipalPermissionsService;
import app.services.NotificationService;
import com.lltool.core.application.connection.dto.ConnectionProfileDto;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.javafx.FontIcon;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * "Permissions: name" tab (LLTool {@code user-permissions.js}): header with Refresh / Script CREATE / Script DROP and four
 * sub-tabs — Overview, Role Membership, Database Permissions, Object Permissions (with a filter box). Read-only.
 */
public class PrincipalPermissionsView extends BorderPane {

    private enum Sub { OVERVIEW, MEMBERSHIPS, DATABASE, OBJECTS }

    private final ConnectionProfileDto profile;
    private final String database;
    private final String name;
    private final BiConsumer<String, String> openScript; // (title, sql)

    private final StackPane content = new StackPane();
    private final ToggleButton overviewBtn = new ToggleButton("Overview");
    private final ToggleButton memberBtn = new ToggleButton("Role Membership");
    private final ToggleButton dbBtn = new ToggleButton("Database Permissions");
    private final ToggleButton objBtn = new ToggleButton("Object Permissions");
    private final Label headerScope = new Label();
    private Sub active = Sub.OVERVIEW;
    private PrincipalPermissions data;
    private String error;
    private boolean loading;

    public PrincipalPermissionsView(ConnectionProfileDto profile, String database, String name, BiConsumer<String, String> openScript) {
        this.profile = profile;
        this.database = database;
        this.name = name;
        this.openScript = openScript;
        getStyleClass().add("principal-permissions");
        setPadding(new Insets(12));

        boolean pg = PrincipalPermissionsService.isPostgres(profile);
        Label title = new Label(name);
        title.setStyle("-fx-font-size: 15px; -fx-font-weight: bold;");
        Label engine = badge(pg ? "PostgreSQL" : "SQL Server");
        headerScope.setText(pg ? "Cluster Scope" : (database != null ? "DB: [" + database + "]" : ""));
        headerScope.setStyle("-fx-font-size: 10px; -fx-text-fill: -color-fg-muted;");
        HBox titleBox = new HBox(8, new FontIcon("fth-shield"), title, engine, headerScope);
        titleBox.setAlignment(Pos.CENTER_LEFT);

        Button refresh = new Button("Refresh", new FontIcon("fth-refresh-cw"));
        Button create = new Button("Script CREATE", new FontIcon("fth-code"));
        Button drop = new Button("Script DROP", new FontIcon("fth-trash-2"));
        drop.getStyleClass().add("danger-item");
        refresh.setOnAction(e -> reload());
        create.setOnAction(e -> script(true));
        drop.setOnAction(e -> script(false));
        Region grow = new Region();
        HBox.setHgrow(grow, Priority.ALWAYS);
        HBox header = new HBox(8, titleBox, grow, refresh, create, drop);
        header.setAlignment(Pos.CENTER_LEFT);

        ToggleGroup tg = new ToggleGroup();
        HBox tabs = new HBox(4);
        int i = 0;
        for (ToggleButton b : new ToggleButton[]{overviewBtn, memberBtn, dbBtn, objBtn}) {
            b.setToggleGroup(tg);
            b.setFocusTraversable(false);
            final Sub s = Sub.values()[i++];
            b.setOnAction(e -> {
                if (!b.isSelected()) b.setSelected(true); // one tab always stays selected
                active = s;
                render();
            });
            tabs.getChildren().add(b);
        }
        overviewBtn.setSelected(true);

        VBox top = new VBox(10, header, tabs);
        top.setPadding(new Insets(0, 0, 10, 0));
        setTop(top);
        setCenter(content);
        reload();
    }

    private static Label badge(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-font-size: 10px; -fx-padding: 1 6 1 6; -fx-background-radius: 8; -fx-background-color: -color-neutral-muted;");
        return l;
    }

    private void reload() {
        loading = true;
        error = null;
        render();
        PrincipalPermissionsService.getInstance().loadAsync(profile, database, name, d -> {
            loading = false;
            data = d;
            render();
        }, err -> {
            loading = false;
            error = err;
            render();
        });
    }

    private void script(boolean create) {
        if (data == null) {
            NotificationService.getInstance().showWarning("Permissions", "Chưa tải xong thông tin của " + name + ".");
            return;
        }
        String ts = ZonedDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + " UTC";
        String sql = create ? data.createScript(profile.name(), true, ts) : data.dropScript(profile.name(), ts);
        openScript.accept((create ? "CREATE_" : "DROP_") + name, sql);
    }

    // ------------------------------------------------------------------ rendering

    private void render() {
        if (data != null) {
            long db = data.permissions.stream().filter(p -> "database".equals(p.scope())).count();
            long obj = data.permissions.stream().filter(p -> "object".equals(p.scope()) || "schema".equals(p.scope())).count();
            memberBtn.setText("Role Membership (" + data.memberships.size() + ")");
            dbBtn.setText("Database Permissions (" + db + ")");
            objBtn.setText("Object Permissions (" + obj + ")");
        }
        if (loading) {
            content.getChildren().setAll(message("Đang tải thông tin phân quyền…"));
        } else if (error != null) {
            content.getChildren().setAll(message("Không tải được: " + error));
        } else if (data == null) {
            content.getChildren().setAll(message(""));
        } else {
            switch (active) {
                case OVERVIEW -> content.getChildren().setAll(overview());
                case MEMBERSHIPS -> content.getChildren().setAll(memberships());
                case DATABASE -> content.getChildren().setAll(permissionTable(
                        data.permissions.stream().filter(p -> "database".equals(p.scope())).toList(), false,
                        "Không có quyền Database trực tiếp"));
                case OBJECTS -> content.getChildren().setAll(permissionTable(
                        data.permissions.stream().filter(p -> "object".equals(p.scope()) || "schema".equals(p.scope())).toList(), true,
                        "Không có quyền Object riêng lẻ"));
            }
        }
    }

    private static Label message(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-text-fill: -color-fg-muted;");
        l.setWrapText(true);
        return l;
    }

    private javafx.scene.Node overview() {
        FlowPane grid = new FlowPane(10, 10);
        for (var e : data.overview.entrySet()) {
            Label k = new Label(e.getKey());
            k.setStyle("-fx-font-size: 10px; -fx-text-fill: -color-fg-muted;");
            Label v = new Label(e.getValue());
            v.setWrapText(true);
            v.setMaxWidth(260);
            v.setStyle("-fx-font-size: 12px; -fx-font-weight: bold;");
            VBox card = new VBox(2, k, v);
            card.setPadding(new Insets(8, 12, 8, 12));
            card.setMinWidth(160);
            card.setStyle("-fx-border-color: -color-border-default; -fx-border-radius: 6; -fx-background-radius: 6;");
            grid.getChildren().add(card);
        }
        Label note = message("Hiển thị thông tin phân quyền và vai trò theo metadata catalog đã xác thực từ cơ sở dữ liệu.");
        VBox box = new VBox(10, note, grid);
        return box;
    }

    private javafx.scene.Node memberships() {
        if (data.memberships.isEmpty()) return message("Không thuộc Role nào — principal này chưa là thành viên của Role nào.");
        TableView<Membership> t = new TableView<>(FXCollections.observableArrayList(data.memberships));
        t.getColumns().add(col("Role Name", 260, Membership::role));
        t.getColumns().add(col("Type", 160, Membership::type));
        t.getColumns().add(col("Option", 120, m -> m.admin() ? "Admin Option" : ""));
        t.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        return t;
    }

    private javafx.scene.Node permissionTable(List<Permission> perms, boolean objectColumns, String empty) {
        if (perms.isEmpty()) return message(empty);
        TableView<Permission> t = new TableView<>();
        FilteredList<Permission> filtered = new FilteredList<>(FXCollections.observableArrayList(perms), p -> true);
        t.setItems(filtered);
        if (objectColumns) {
            t.getColumns().add(col("Schema", 110, p -> p.schema() != null ? p.schema() : "dbo"));
            t.getColumns().add(col("Object Name", 190, p -> p.object() != null ? p.object() : p.schema() != null ? p.schema() : "--"));
            t.getColumns().add(col("Object Type", 130, p -> p.objectType() != null ? p.objectType() : p.scope()));
        }
        t.getColumns().add(col("Permission", 150, Permission::permission));
        t.getColumns().add(col("State", 80, p -> "DENY".equals(p.state()) ? "DENY" : "GRANT"));
        t.getColumns().add(col("Source", 160, p -> "ROLE".equals(p.source()) ? "ROLE (" + p.viaRole() + ")" : "DIRECT"));
        if (!objectColumns) t.getColumns().add(col("Grantor", 120, p -> p.grantor() != null ? p.grantor() : "--"));
        t.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        VBox.setVgrow(t, Priority.ALWAYS);
        if (!objectColumns) return t;
        TextField filter = new TextField();
        filter.setPromptText("Lọc đối tượng…");
        filter.setMaxWidth(260);
        filter.textProperty().addListener((o, a, q) -> {
            String x = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
            filtered.setPredicate(p -> x.isEmpty()
                    || (p.object() != null && p.object().toLowerCase(Locale.ROOT).contains(x))
                    || (p.schema() != null && p.schema().toLowerCase(Locale.ROOT).contains(x))
                    || (p.permission() != null && p.permission().toLowerCase(Locale.ROOT).contains(x)));
        });
        VBox box = new VBox(8, filter, t);
        return box;
    }

    private static <T> TableColumn<T, String> col(String title, double width, Function<T, String> value) {
        TableColumn<T, String> c = new TableColumn<>(title);
        c.setPrefWidth(width);
        c.setCellValueFactory(cd -> new SimpleStringProperty(value.apply(cd.getValue())));
        return c;
    }
}
