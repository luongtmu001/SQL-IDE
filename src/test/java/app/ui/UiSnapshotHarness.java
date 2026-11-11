package app.ui;

import app.services.ThemeService;
import app.services.ThemeService.AppTheme;
import app.ui.compare.SchemaCompareTab;
import app.ui.dataeditor.TableDataEditorTab;
import app.ui.dependency.DependencyGraphTab;
import app.ui.designer.TableDesignerTab;
import app.ui.dialogs.DialogChrome;
import app.ui.dialogs.PreviewDataChangesDialog;
import app.ui.dialogs.SaveDesignDialog;
import app.ui.search.ObjectContentSearchTab;
import app.ui.visualizer.SqlVisualizerTab;
import com.lltool.core.application.connection.dto.ConnectionProfileDto;
import com.lltool.core.application.tabledesigner.model.MigrationPlan;
import com.lltool.core.application.tabledesigner.model.MigrationStatement;
import com.lltool.core.domain.dependency.DependencyNodeType;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Tab;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class UiSnapshotHarness {

    @Test
    void snapshotAll() throws Exception {
        String outDir = System.getProperty("snap.out");
        if (outDir == null) return;
        new File(outDir).mkdirs();

        System.setProperty("sqlide.backend", "mock");

        String sizeProp = System.getProperty("snap.size", "1500x880");
        int targetWidth = 1500;
        int targetHeight = 880;
        if (sizeProp != null && sizeProp.contains("x")) {
            String[] parts = sizeProp.split("x");
            try {
                targetWidth = Integer.parseInt(parts[0].trim());
                targetHeight = Integer.parseInt(parts[1].trim());
            } catch (Exception ignored) {}
        }

        String themeProp = System.getProperty("snap.theme", "dark");
        boolean isLight = "light".equalsIgnoreCase(themeProp);

        CountDownLatch started = new CountDownLatch(1);
        try {
            Platform.startup(started::countDown);
        } catch (IllegalStateException e) {
            started.countDown();
        }
        started.await(20, TimeUnit.SECONDS);
        Platform.setImplicitExit(false);

        ConnectionProfileDto p = new ConnectionProfileDto(
                "snap-1", "MSSQL-DEV", "Default", "Dev", false, "sqlserver", "localhost", 1433,
                "DemoDb", null, "sa", "SQL_PASSWORD", null, false, 30, true, Collections.emptyMap(), null, null);

        Map<String, Tab> tabs = new LinkedHashMap<>();
        Map<String, Scene> scenes = new LinkedHashMap<>();
        CountDownLatch built = new CountDownLatch(1);
        final int finalWidth = targetWidth;
        final int finalHeight = targetHeight;

        Platform.runLater(() -> {
            try {
                build(tabs, "01_table_designer", () -> new TableDesignerTab(p, "DemoDb", "dbo", "Customers", false, a -> {}));
                build(tabs, "02_table_data_editor", () -> new TableDataEditorTab(p, "DemoDb", "dbo", "Customers", a -> {}));
                build(tabs, "03_object_search", () -> new ObjectContentSearchTab(p, "DemoDb", "dbo", a -> {}));
                build(tabs, "04_schema_compare", () -> new SchemaCompareTab(p, "DemoDb", "dbo", p, "DemoDb", "dbo", a -> {}));
                build(tabs, "05_dependency_graph", () -> new DependencyGraphTab(p, "DemoDb", "dbo", "orders", null, DependencyNodeType.TABLE, a -> {}));
                build(tabs, "06_sql_visualizer", () -> new SqlVisualizerTab(p, "DemoDb", "dbo", a -> {}));

                for (var e : tabs.entrySet()) {
                    Node n = e.getValue().getContent();
                    e.getValue().setContent(null);
                    Scene sc = new Scene(new StackPane(n), finalWidth, finalHeight);
                    try {
                        ThemeService.getInstance().setTheme(isLight ? AppTheme.PRIMER_LIGHT : AppTheme.PRIMER_DARK, sc);
                        ThemeService.getInstance().applyThemeToScene(sc);
                        ThemeService.getInstance().applyShape(sc);
                    } catch (Throwable t) {
                        System.out.println("SNAP theme failed: " + t);
                    }
                    scenes.put(e.getKey(), sc);
                }

                // Dialogs
                List<MigrationStatement> stmts = List.of(
                        new MigrationStatement("ALTER TABLE dbo.Customers ADD notes nvarchar(255) NULL;", "Add notes column", com.lltool.core.application.tabledesigner.model.StatementGroup.COLUMN_ADD, false, "notes"),
                        new MigrationStatement("ALTER TABLE dbo.Customers DROP COLUMN legacy_code;", "Drop legacy column", com.lltool.core.application.tabledesigner.model.StatementGroup.COLUMN_DROP, true, "legacy_code")
                );
                MigrationPlan mockPlan = new MigrationPlan(stmts, List.of("Cột legacy_code sẽ bị xóa hoàn toàn"), true, true);
                String mockSql = """
                        -- Migration script generated for dbo.Customers
                        ALTER TABLE dbo.Customers ADD notes nvarchar(255) NULL;
                        ALTER TABLE dbo.Customers DROP COLUMN legacy_code;
                        """;
                DialogChrome saveDialog = SaveDesignDialog.createDialog(null, "dbo", "Customers", mockPlan, mockSql, r -> {});
                Scene scSave = saveDialog.stage().getScene();
                ThemeService.getInstance().setTheme(isLight ? AppTheme.PRIMER_LIGHT : AppTheme.PRIMER_DARK, scSave);
                ThemeService.getInstance().applyThemeToScene(scSave);
                ThemeService.getInstance().applyShape(scSave);
                scenes.put("07_save_design_dialog", scSave);

                String mockDml = """
                        -- Transaction Batch for dbo.Customers
                        BEGIN TRANSACTION;
                        INSERT INTO dbo.Customers (CustomerCode, FullName) VALUES ('CUST-0031', N'Công ty Mới');
                        UPDATE dbo.Customers SET Balance = 2500000.00 WHERE Id = 1;
                        DELETE FROM dbo.Customers WHERE Id = 15;
                        COMMIT;
                        """;
                DialogChrome previewDialog = PreviewDataChangesDialog.createDialog(null, "dbo", "Customers", 1, 1, 1, mockDml, r -> {});
                Scene scPreview = previewDialog.stage().getScene();
                ThemeService.getInstance().setTheme(isLight ? AppTheme.PRIMER_LIGHT : AppTheme.PRIMER_DARK, scPreview);
                ThemeService.getInstance().applyThemeToScene(scPreview);
                ThemeService.getInstance().applyShape(scPreview);
                scenes.put("08_preview_data_changes_dialog", scPreview);

            } finally {
                built.countDown();
            }
        });
        built.await(60, TimeUnit.SECONDS);

        // trigger actions that need user input
        Platform.runLater(() -> {
            try {
                if (tabs.get("03_object_search") instanceof ObjectContentSearchTab t) {
                    t.getSearchField().getInputField().setText("orders");
                    t.executeSearchAsync();
                }
                if (tabs.get("04_schema_compare") instanceof SchemaCompareTab t) {
                    t.executeCompareAsync();
                }
            } catch (Throwable t) {
                System.out.println("SNAP action failed: " + t);
            }
        });
        Thread.sleep(6000);

        CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                for (var e : scenes.entrySet()) {
                    try {
                        Scene sc = e.getValue();
                        sc.getRoot().applyCss();
                        sc.getRoot().layout();
                        WritableImage img = sc.snapshot(null);
                        // second pass so late layout settles
                        img = sc.snapshot(null);
                        int w = (int) img.getWidth(), h = (int) img.getHeight();
                        BufferedImage b = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
                        PixelReader r = img.getPixelReader();
                        for (int y = 0; y < h; y++) {
                            for (int x = 0; x < w; x++) {
                                b.setRGB(x, y, r.getArgb(x, y));
                            }
                        }
                        ImageIO.write(b, "png", new File(outDir, e.getKey() + ".png"));
                        System.out.println("SNAP ok " + e.getKey());
                    } catch (Throwable t) {
                        System.out.println("SNAP failed " + e.getKey() + ": " + t);
                    }
                }
            } finally {
                done.countDown();
            }
        });
        done.await(60, TimeUnit.SECONDS);
    }

    private interface TabMaker { Tab make(); }

    private static void build(Map<String, Tab> tabs, String key, TabMaker m) {
        try {
            tabs.put(key, m.make());
        } catch (Throwable t) {
            System.out.println("SNAP build failed " + key + ": " + t);
            t.printStackTrace(System.out);
        }
    }
}
