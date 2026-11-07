package app;

import app.models.DatabaseNode;
import app.models.DatabaseNodeType;
import app.services.ThemeService;
import app.ui.components.DatabaseTreeCell;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.image.WritableImage;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Proxy;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Renders the Object Explorer rows to target/explorer-look-*.png so the layout (chevron / icon / name alignment,
 * hover action bar) can be checked by eye. Passes whenever the images are written.
 */
public class ExplorerLookSnapshotTest {

    @BeforeAll
    static void initJavaFX() {
        try { Platform.startup(() -> {}); } catch (IllegalStateException ignored) { }
        try { Platform.setImplicitExit(false); } catch (Exception ignored) { }
    }

    private static TreeItem<DatabaseNode> item(String name, DatabaseNodeType t, String sub, boolean expanded, TreeItem<DatabaseNode> parent) {
        DatabaseNode n = new DatabaseNode(name, t, sub);
        n.setConnectionId("conn_demo");
        TreeItem<DatabaseNode> ti = new TreeItem<>(n);
        ti.setExpanded(expanded);
        if (parent != null) parent.getChildren().add(ti);
        return ti;
    }

    private static void hover(javafx.scene.Node n, boolean on) {
        try {
            var m = javafx.scene.Node.class.getDeclaredMethod("setHover", boolean.class);
            m.setAccessible(true);
            m.invoke(n, on);
        } catch (Throwable ignored) { }
    }

    private static void render(ThemeService.AppTheme theme, boolean hoverRow, String file) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        Throwable[] err = new Throwable[1];
        Platform.runLater(() -> {
            try {
                var actions = (DatabaseTreeCell.ExplorerActions) Proxy.newProxyInstance(
                        DatabaseTreeCell.class.getClassLoader(), new Class[]{DatabaseTreeCell.ExplorerActions.class},
                        (p, m, a) -> null);
                TreeItem<DatabaseNode> root = item("root", DatabaseNodeType.ROOT, null, true, null);
                TreeItem<DatabaseNode> g = item("Production", DatabaseNodeType.GROUP, null, true, root);
                TreeItem<DatabaseNode> c1 = item("SQL Server - Warehouse reporting (read replica, Hanoi)", DatabaseNodeType.CONNECTION, null, true, g);
                TreeItem<DatabaseNode> db = item("Sales2026", DatabaseNodeType.DATABASE, "3", true, c1);
                TreeItem<DatabaseNode> sc = item("dbo", DatabaseNodeType.SCHEMA, null, true, db);
                TreeItem<DatabaseNode> tb = item("Tables", DatabaseNodeType.CATEGORY_TABLES, "12", true, sc);
                item("Customers", DatabaseNodeType.TABLE, null, false, tb);
                item("Orders", DatabaseNodeType.TABLE, null, false, tb);
                item("Views", DatabaseNodeType.CATEGORY_VIEWS, "4", false, sc);
                TreeItem<DatabaseNode> pg = item("PostgreSQL dev-local", DatabaseNodeType.CONNECTION, null, false, g);
                item("postgres", DatabaseNodeType.DATABASE, "1", false, pg);

                TreeView<DatabaseNode> tv = new TreeView<>(root);
                tv.setShowRoot(false);
                tv.getStyleClass().add("explorer-tree");
                tv.setCellFactory(x -> new DatabaseTreeCell(n -> { }, actions));
                Scene scene = new Scene(tv, 300, 300);
                ThemeService.getInstance().setTheme(theme, scene);
                Stage st = new Stage();
                st.setScene(scene);
                st.setX(-3000);
                st.setY(0);
                st.show();

                javafx.animation.PauseTransition pt = new javafx.animation.PauseTransition(javafx.util.Duration.millis(700));
                pt.setOnFinished(e -> {
                    try {
                        if (hoverRow) {
                            for (javafx.scene.Node n : tv.lookupAll(".tree-cell")) {
                                if (n instanceof DatabaseTreeCell dc && dc.getItem() != null
                                        && dc.getItem().getType() == DatabaseNodeType.CONNECTION
                                        && dc.getItem().getName().startsWith("SQL Server")) {
                                    hover(dc, true);
                                }
                            }
                            tv.applyCss();
                            tv.layout();
                        }
                        SnapshotParameters sp = new SnapshotParameters();
                        sp.setTransform(javafx.scene.transform.Transform.scale(3, 3));
                        WritableImage img = tv.snapshot(sp, null);
                        int w = (int) img.getWidth(), h = (int) img.getHeight();
                        BufferedImage bi = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
                        var pr = img.getPixelReader();
                        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) bi.setRGB(x, y, pr.getArgb(x, y));
                        File out = new File("target/" + file);
                        out.getParentFile().mkdirs();
                        ImageIO.write(bi, "png", out);
                        st.close();
                    } catch (Throwable t) {
                        err[0] = t;
                    } finally {
                        done.countDown();
                    }
                });
                pt.play();
            } catch (Throwable t) {
                err[0] = t;
                done.countDown();
            }
        });
        assertTrue(done.await(30, TimeUnit.SECONDS), "snapshot timed out");
        if (err[0] != null) throw new RuntimeException(err[0]);
    }

    @Test
    void rendersExplorerRows() throws Exception {
        render(ThemeService.AppTheme.PRIMER_LIGHT, false, "explorer-look-light.png");
        render(ThemeService.AppTheme.PRIMER_LIGHT, true, "explorer-look-light-hover.png");
        render(ThemeService.AppTheme.PRIMER_DARK, true, "explorer-look-dark-hover.png");
        assertTrue(new File("target/explorer-look-light.png").exists());
    }
}
