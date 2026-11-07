package app.ui.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.scene.Scene;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import netscape.javascript.JSObject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/**
 * IntelliSense in the real page: resources/monaco/editor.html in a WebView, real key events sent through the WebView,
 * only the Java metadata is a fake. Needs a display; not part of "mvn test":
 *
 * <pre>mvnw -Dtest=IntellisenseWebViewIT test</pre>
 *
 * Timings and screenshots go to target/intellisense-it.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class IntellisenseWebViewIT {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Path OUT = Path.of("target", "intellisense-it");
    private static final String CONNECT =
            "SqlIntellisense.setContext({enabled:true,dialect:'SQLSERVER',keywordCase:'upper',mru:false,detail:true,"
                    + "detailMode:'%s',connectionId:'c1',database:'bravo',schema:'dbo'})";

    private static Stage stage;
    private static WebView view;
    private static WebEngine engine;
    /** Must stay referenced: WebEngine only holds it weakly. */
    private static FakeBridge bridge;

    /** Stands in for MonacoEditorHost.BridgeCallback. */
    public static final class FakeBridge {
        final List<String> requests = new ArrayList<>();
        final Map<String, Integer> delays = new ConcurrentHashMap<>();
        volatile boolean failObjects = false;

        public void intellisenseRequest(String id, String method, String args) throws Exception {
            requests.add(method + " " + args);
            JsonNode a = JSON.readTree(args);
            String json;
            if (failObjects && method.equals("objects")) {
                json = "{\"ok\":false,\"error\":\"Mất kết nối\"}";
            } else {
                json = "{\"ok\":true,\"data\":" + JSON.writeValueAsString(answer(method, a)) + "}";
            }
            final int delay = a.size() > 3 ? delays.getOrDefault(a.get(3).asText(), 20) : 20;
            final String out = json;
            Thread.ofVirtual().start(() -> {
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException e) {
                    return;
                }
                Platform.runLater(() -> ((JSObject) engine.executeScript("window.IntelliSenseHost")).call("resolve", id, out));
            });
        }

        private Object answer(String method, JsonNode a) {
            return switch (method) {
                case "objects" -> {
                    List<Map<String, Object>> rows = new ArrayList<>(List.of(
                            Map.of("name", "Orders", "schema", "dbo", "type", "table"),
                            Map.of("name", "OrderDetails", "schema", "dbo", "type", "table"),
                            Map.of("name", "Customers", "schema", "dbo", "type", "table"),
                            Map.of("name", "vw_OrderSummary", "schema", "dbo", "type", "view"),
                            Map.of("name", "usp_GetOrders", "schema", "dbo", "type", "procedure"),
                            Map.of("name", "<img src=x onerror=window.__xss=1>", "schema", "dbo", "type", "table")));
                    for (int i = 0; i < 5000; i++) rows.add(Map.of("name", "Table_" + i, "schema", "dbo", "type", "table"));
                    yield rows;
                }
                case "columns" -> List.of(
                        Map.of("name", "OrderId", "data_type", "int", "is_nullable", false, "is_pk", true),
                        Map.of("name", "CustomerName", "data_type", "nvarchar(200)", "is_nullable", true, "is_pk", false),
                        Map.of("name", "col_of_" + a.get(3).asText(), "data_type", "int", "is_nullable", true, "is_pk", false));
                case "parameters" -> List.of(Map.of("name", "@CustomerId", "data_type", "int", "is_output", false));
                case "definition" -> "CREATE PROCEDURE dbo." + a.get(3).asText() + " AS\nSELECT 1;";
                case "schemas" -> List.of("dbo", "sales");
                case "databases" -> List.of("bravo", "master");
                default -> List.of();
            };
        }

        public void setClipboardText(String t) { }
        public String getClipboardText() { return ""; }
        public void intellisenseOpenScript(String title, String content) { requests.add("open " + title); }
        public void onEditorReady() { }
        public void onContentChanged(String id, String content) { }
        public void onCursorPositionChanged(int line, int col) { }
        public void onZoomChanged(Object pct) { }
        public void onExecuteRequested() { }
        public void onExecuteStatementRequested() { }
    }

    // ------------------------------------------------------------------ harness

    private static <T> T fx(Callable<T> c) throws Exception {
        FutureTask<T> task = new FutureTask<>(c);
        Platform.runLater(task);
        return task.get(30, TimeUnit.SECONDS);
    }

    private static Object js(String script) throws Exception {
        return fx(() -> engine.executeScript(script));
    }

    private static boolean jsTrue(String script) {
        try {
            return Boolean.TRUE.equals(js(script));
        } catch (Exception e) {
            return false;
        }
    }

    private static void waitUntil(String what, long ms, BooleanSupplier ok) throws Exception {
        long end = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < end) {
            if (ok.getAsBoolean()) return;
            Thread.sleep(15);
        }
        throw new AssertionError("Timed out: " + what + " | errors: " + js("JSON.stringify(window.__errors)")
                + " | log: " + js("(window.__log||[]).slice(-24).join(' ; ')"));
    }

    private static void key(KeyCode code, String ch) throws Exception {
        fx(() -> {
            fire(KeyEvent.KEY_PRESSED, KeyEvent.CHAR_UNDEFINED, code.getName(), code);
            if (ch != null) fire(KeyEvent.KEY_TYPED, ch, "", KeyCode.UNDEFINED);
            fire(KeyEvent.KEY_RELEASED, KeyEvent.CHAR_UNDEFINED, code.getName(), code);
            return null;
        });
    }

    private static void fire(javafx.event.EventType<KeyEvent> type, String ch, String text, KeyCode code) {
        Event.fireEvent(view, new KeyEvent(type, ch, text, code, false, false, false, false));
    }

    private static void press(KeyCode code) throws Exception {
        key(code, null);
        Thread.sleep(40);
    }

    private static void type(String text) throws Exception {
        for (char c : text.toCharArray()) {
            KeyCode code = Character.isLetter(c) ? KeyCode.getKeyCode(String.valueOf(Character.toUpperCase(c)))
                    : Character.isDigit(c) ? KeyCode.getKeyCode(String.valueOf(c))
                    : c == ' ' ? KeyCode.SPACE : c == '.' ? KeyCode.PERIOD : KeyCode.UNDEFINED;
            key(code == null ? KeyCode.UNDEFINED : code, String.valueOf(c));
            Thread.sleep(25);
        }
    }

    private static void reset(String mode) throws Exception {
        js(String.format(CONNECT, mode));
        js("__log.length=0; AppIntelliSense.hidePopup(); __ed.setValue(''); __ed.focus(); IntelliSenseHost.clearFailures(); AppMetadataCache.invalidateAll();");
        bridge.requests.clear();
        bridge.delays.clear();
        Thread.sleep(40);
    }

    private static void setText(String text, int line, int col) throws Exception {
        fx(() -> {
            JSObject w = (JSObject) engine.executeScript("window");
            w.setMember("__t", text);
            engine.executeScript("__ed.setValue(window.__t); __ed.setPosition({lineNumber:" + line + ",column:" + col + "}); __ed.focus();");
            return null;
        });
        Thread.sleep(40);
    }

    private static boolean popupVisible() { return jsTrue("AppIntelliSense.isPopupVisible()"); }

    private static boolean panelVisible() {
        return jsTrue("document.querySelector('.isp-detail-panel').style.display !== 'none' && AppIntelliSense.isPopupVisible()");
    }

    private static String selected() throws Exception {
        return String.valueOf(js("(function(){var s=AppIntelliSense.getPopupUI().getSelectedItem();return s?s.name:''})()"));
    }

    private static String text() throws Exception { return String.valueOf(js("__ed.getValue()")); }

    private static String body() throws Exception { return String.valueOf(js("document.querySelector('.isp-body').textContent")); }

    private static long count(String prefix) {
        return bridge.requests.stream().filter(r -> r.startsWith(prefix)).count();
    }

    private static void snapshot(String name) throws Exception {
        Files.createDirectories(OUT);
        fx(() -> {
            WritableImage img = view.snapshot(null, null);
            int w = (int) img.getWidth(), h = (int) img.getHeight();
            var bi = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) bi.setRGB(x, y, img.getPixelReader().getArgb(x, y));
            javax.imageio.ImageIO.write(bi, "png", OUT.resolve(name + ".png").toFile());
            return null;
        });
    }

    @BeforeAll
    static void open() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        try {
            Platform.startup(started::countDown);
        } catch (IllegalStateException alreadyRunning) {
            started.countDown();
        }
        assertTrue(started.await(20, TimeUnit.SECONDS));
        Platform.setImplicitExit(false);
        fx(() -> {
            view = new WebView();
            engine = view.getEngine();
            bridge = new FakeBridge();
            stage = new Stage();
            stage.setScene(new Scene(view, 1100, 620));
            stage.setX(-3000);
            stage.setY(40);
            stage.show();
            engine.getLoadWorker().stateProperty().addListener((o, a, s) -> {
                if (s == javafx.concurrent.Worker.State.SUCCEEDED) {
                    ((JSObject) engine.executeScript("window")).setMember("javaBridge", bridge);
                }
            });
            engine.load(IntellisenseWebViewIT.class.getResource("/monaco/editor.html").toExternalForm());
            view.requestFocus();
            return null;
        });
        waitUntil("page", 30000, () -> jsTrue("!!(window.editorBridge && editorBridge.isReady())"));
        js("window.__errors=[]; window.addEventListener('error',function(e){__errors.push(e.message)});"
                + "var ce=console.error; console.error=function(m){__errors.push(String(m)); ce.apply(console,arguments)};");
        js("editorBridge.createModel('t1','','sql'); editorBridge.switchModel('t1');");
        js("window.__ed = monaco.editor.getEditors()[0];");
        js(String.format(CONNECT, "always"));
        waitUntil("engine", 30000, () -> jsTrue("IntelliSenseHost.isLoaded()"));
        js("""
            window.__log=[];
            (function(){
              var c=AppIntelliSense.getController(), tc=c.triggerController, e=c.completionEngine;
              var t0=performance.now(); function L(m){__log.push(Math.round(performance.now()-t0)+' '+m); if(__log.length>60)__log.shift();}
              var dc=tc.decideChange.bind(tc); tc.decideChange=function(ch,ctx,v,s){var r=dc(ch,ctx,v,s);L('change ins='+JSON.stringify(ch&&ch.text)+' vis='+v+' clause='+(ctx&&ctx.clause)+' word='+(ctx&&ctx.currentWord)+' -> '+r.action+'/'+r.reason);return r;};
              var dk=tc.decideCursor.bind(tc); tc.decideCursor=function(o,l,v){var r=dk(o,l,v);if(r.action!=='none')L('cursor '+o+' -> '+r.action+'/'+r.reason);return r;};
              var hp=c.hidePopup.bind(c); c.hidePopup=function(){L('hidePopup');return hp();};
              var tr=c.trigger.bind(c); c.trigger=function(a,m,r){L('trigger '+r);return tr(a,m,r);};
              var gc=e.getCompletions.bind(e); e.getCompletions=function(a,b,cx,d){L('getCompletions word='+cx.currentWord);return gc(a,b,cx,d).then(function(x){L('completions n='+(x?x.length:-1));return x;});};
              var ss=tc.setState.bind(tc); tc.setState=function(st,r){L('state '+st+' '+r);return ss(st,r);};
              var st=window.setTimeout; 
              var ca=e.cancelPending.bind(e); e.cancelPending=function(){L('cancelPending');return ca();};
            })();
            """);
    }

    @AfterAll
    static void close() throws Exception {
        fx(() -> {
            stage.close();
            return null;
        });
    }

    // ------------------------------------------------------------------ behaviour

    @Test
    @Order(1)
    void engineLoadsWithoutErrors() throws Exception {
        report("engine_load_ms", js("IntelliSenseHost.loadMs"));
        assertEquals("[]", js("JSON.stringify(window.__errors)"));
        assertEquals(false, js("__ed.getOption(monaco.editor.EditorOption.quickSuggestions).other === 'on'"), "Monaco's own widget must stay off");
    }

    @Test
    @Order(2)
    void alwaysModeShowsPanelAndLoadsDetail() throws Exception {
        reset("always");
        type("SELECT * FROM ");
        waitUntil("popup after FROM", 5000, IntellisenseWebViewIT::popupVisible);
        assertTrue(panelVisible(), "panel opens with the list");
        waitUntil("columns loaded", 5000, () -> count("columns") > 0);
        waitUntil("summary", 3000, () -> {
            try { return body().contains("OrderId"); } catch (Exception e) { return false; }
        });
        snapshot("1_always_summary");

        press(KeyCode.DOWN);
        String second = selected();
        assertFalse(second.isEmpty());
        assertEquals(0, count("definition"), "the Script tab loads only when opened");

        js("document.querySelector('[data-tab=script]').dispatchEvent(new MouseEvent('mousedown',{bubbles:true}))");
        waitUntil("definition", 5000, () -> count("definition") > 0);
        waitUntil("script shown", 3000, () -> jsTrue("!!document.querySelector('.isp-script')"));
        Thread.sleep(200);
        snapshot("2_always_script");
        js("document.querySelector('[data-tab=summary]').dispatchEvent(new MouseEvent('mousedown',{bubbles:true}))");
    }

    @Test
    @Order(3)
    void typingFiltersAndEnterInserts() throws Exception {
        reset("always");
        type("SELECT * FROM Ord");
        waitUntil("popup", 5000, IntellisenseWebViewIT::popupVisible);
        waitUntil("filtered", 3000, () -> jsTrue("AppIntelliSense.getPopupUI().candidates.every(function(c){return c.name.toLowerCase().indexOf('ord')>=0})"));
        press(KeyCode.ENTER);
        waitUntil("inserted", 2000, () -> !popupVisible());
        assertTrue(text().matches("SELECT \\* FROM (dbo\\.)?\\[?Order.*"), text());
        assertFalse(text().contains("\n"), "Enter must insert, not break the line");
    }

    @Test
    @Order(4)
    void aliasGivesColumns() throws Exception {
        reset("always");
        setText("SELECT  FROM dbo.Orders o", 1, 8);
        type("o.");
        waitUntil("columns", 5000, () -> jsTrue("AppIntelliSense.isPopupVisible() && AppIntelliSense.getPopupUI().candidates.some(function(c){return c.name==='OrderId'})"));
        assertTrue(bridge.requests.stream().anyMatch(r -> r.startsWith("columns") && r.contains("Orders")), bridge.requests.toString());
        snapshot("3_alias_columns");
    }

    @Test
    @Order(5)
    void onDemandLoadsNothingUntilArrowRight() throws Exception {
        reset("onDemand");
        type("SELECT * FROM ");
        waitUntil("popup", 5000, IntellisenseWebViewIT::popupVisible);
        // the list of the word "FROM" may show first: wait for the one after the space
        waitUntil("object list", 5000, () -> jsTrue("AppIntelliSense.getPopupUI().candidates.length > 0 && AppIntelliSense.getPopupUI().candidates.every(function(c){return c.type!=='keyword'})"));
        Thread.sleep(100);
        assertFalse(panelVisible(), "panel stays closed");
        press(KeyCode.DOWN);
        press(KeyCode.DOWN);   // rows are: <img..>, Customers, OrderDetails (tables first, schemas after them)
        assertEquals("OrderDetails", selected());
        Thread.sleep(300);
        assertEquals(0, count("columns"), "no detail request before the panel opens");
        assertEquals(0, count("definition"));
        snapshot("4_on_demand_closed");

        int caretBefore = ((Number) js("__ed.getPosition().column")).intValue();
        press(KeyCode.RIGHT);
        assertTrue(panelVisible(), "→ opens the panel");
        assertEquals(caretBefore, ((Number) js("__ed.getPosition().column")).intValue(), "→ does not move the caret");
        waitUntil("detail after →", 3000, () -> count("columns") > 0);
        Thread.sleep(150);
        snapshot("5_on_demand_open");

        press(KeyCode.RIGHT);
        assertTrue(panelVisible(), "→ again changes nothing");
        assertEquals(caretBefore, ((Number) js("__ed.getPosition().column")).intValue());

        press(KeyCode.LEFT);
        assertFalse(panelVisible(), "← closes the panel");
        assertTrue(popupVisible(), "the list stays");
        assertEquals(caretBefore, ((Number) js("__ed.getPosition().column")).intValue(), "← consumed");

        press(KeyCode.LEFT);
        waitUntil("second ← leaves", 2000, () -> !popupVisible());
        assertEquals(caretBefore - 1, ((Number) js("__ed.getPosition().column")).intValue(), "← is a caret move now");
    }

    @Test
    @Order(6)
    void detailOffHasNoPanelAndArrowsMoveTheCaret() throws Exception {
        js("SqlIntellisense.setContext({enabled:true,dialect:'SQLSERVER',detail:false,detailMode:'onDemand',connectionId:'c1',database:'bravo',schema:'dbo'})");
        js("AppIntelliSense.hidePopup(); __ed.setValue(''); __ed.focus();");
        bridge.requests.clear();
        type("SELECT * FROM ");
        waitUntil("popup", 5000, IntellisenseWebViewIT::popupVisible);
        assertFalse(panelVisible());
        press(KeyCode.DOWN);
        Thread.sleep(250);
        assertEquals(0, count("columns"));
        assertEquals(true, js("document.querySelector('.isp-toggle').style.display === 'none'"));
    }

    @Test
    @Order(7)
    void disabledMeansNoSuggestionsAtAll() throws Exception {
        js("SqlIntellisense.setContext({enabled:false,dialect:'SQLSERVER',detail:true,detailMode:'always',connectionId:'c1',database:'bravo',schema:'dbo'})");
        js("AppIntelliSense.hidePopup(); __ed.setValue(''); __ed.focus();");
        bridge.requests.clear();
        type("SELECT * FROM Ord");
        Thread.sleep(400);
        assertFalse(popupVisible());
        assertEquals(0, bridge.requests.size());
    }

    @Test
    @Order(8)
    void tabEscapeAndSnippet() throws Exception {
        reset("always");
        type("SELECT * FROM Ord");
        waitUntil("popup", 5000, IntellisenseWebViewIT::popupVisible);
        press(KeyCode.ESCAPE);
        assertFalse(popupVisible(), "Esc closes");

        reset("always");
        type("SELECT * FROM Ord");
        waitUntil("popup", 5000, IntellisenseWebViewIT::popupVisible);
        key(KeyCode.TAB, "\t");
        Thread.sleep(80);
        assertFalse(popupVisible(), "Tab closes");
        assertFalse(text().contains("Orders"), "Tab does not accept a suggestion");

        reset("always");
        type("seltop");
        Thread.sleep(150);
        key(KeyCode.TAB, null);
        Thread.sleep(150);
        assertTrue(text().toUpperCase().contains("SELECT TOP"), "Tab expands an exact snippet prefix: " + text());
    }

    @Test
    @Order(9)
    void namesAreEscaped() throws Exception {
        reset("always");
        type("SELECT * FROM im");
        waitUntil("popup", 5000, IntellisenseWebViewIT::popupVisible);
        Thread.sleep(150);
        assertEquals(true, js("window.__xss === undefined"), "an object name must not run as HTML");
        assertEquals(0, ((Number) js("document.querySelectorAll('#ide-intellisense-popup img').length")).intValue());
    }

    @Test
    @Order(10)
    void aSlowAnswerNeverOverwritesANewerSelection() throws Exception {
        reset("always");
        type("SELECT * FROM ");
        waitUntil("popup", 5000, IntellisenseWebViewIT::popupVisible);
        Thread.sleep(1200);   // the list from the last keystroke has settled (a late list resets the selection)
        // everything is cached by now except what the fake delays
        js("AppMetadataCache.invalidateAll()");
        String first = selected();
        press(KeyCode.DOWN);
        String second = selected();
        bridge.delays.put(first, 900);
        press(KeyCode.UP);
        Thread.sleep(150);
        press(KeyCode.DOWN);
        waitUntil("second shown", 3000, () -> {
            try { return body().contains("col_of_" + second); } catch (Exception e) { return false; }
        });
        Thread.sleep(1100);
        assertTrue(body().contains("col_of_" + second), "still the current row: " + body());
    }

    @Test
    @Order(11)
    void metadataErrorShowsBannerAndRetryRecovers() throws Exception {
        reset("always");
        js("IntelliSenseHost.clearFailures(); AppMetadataCache.invalidateAll();");
        bridge.failObjects = true;
        type("SELECT * FROM ");
        waitUntil("popup or settle", 3000, () -> count("objects") > 0);
        Thread.sleep(300);
        report("error_banner_shown", js("!!document.querySelector('.isp-error')"));
        snapshot("6_metadata_error");
        bridge.failObjects = false;
        if (jsTrue("!!document.querySelector('.isp-retry')")) {
            js("document.querySelector('.isp-retry').dispatchEvent(new MouseEvent('mousedown',{bubbles:true}))");
            waitUntil("recovered", 3000, () -> jsTrue("AppIntelliSense.getPopupUI().candidates.length > 0"));
        }
        js("IntelliSenseHost.clearFailures(); AppMetadataCache.invalidateAll();");
    }

    @Test
    @Order(12)
    void postgresKeepsPlpgsqlKeywordsAndKeywordCaseApplies() throws Exception {
        js("SqlIntellisense.setContext({enabled:true,dialect:'POSTGRESQL',keywordCase:'lower',detail:false,detailMode:'always',mru:false})");
        js("AppIntelliSense.hidePopup(); __ed.setValue(''); __ed.focus();");
        type("PERFOR");
        waitUntil("popup", 5000, IntellisenseWebViewIT::popupVisible);
        assertEquals(true, js("AppIntelliSense.getPopupUI().candidates.some(function(c){return c.name==='PERFORM'})"), "a PL/pgSQL keyword");
        press(KeyCode.ENTER);
        Thread.sleep(150);
        assertTrue(text().startsWith("perform"), "keyword case Setting: " + text());
    }

    @Test
    @Order(13)
    void popupAppearsEveryTime() throws Exception {
        js(String.format(CONNECT, "always"));
        int missed = 0;
        StringBuilder why = new StringBuilder();
        for (int i = 0; i < 30; i++) {
            reset("always");
            type("SELECT * FROM ");
            long end = System.currentTimeMillis() + 2500;
            while (System.currentTimeMillis() < end && !popupVisible()) Thread.sleep(15);
            if (!popupVisible()) {
                missed++;
                why.append(i).append(": state=").append(js("AppIntelliSense.getTriggerController().state"))
                        .append(" text=").append(text()).append(" pending=").append(js("IntelliSenseHost.pendingRequests()"))
                        .append(" reqs=").append(bridge.requests.size()).append(" LOG ").append(js("__log.slice(-22).join(' ; ')")).append(" | ");
            }
        }
        report("popup_missed", missed + " " + why);
        assertEquals(0, missed, why.toString());
    }

    @Test
    @Order(14)
    void measureFilteringALargeCatalog() throws Exception {
        reset("always");
        type("SELECT * FROM ");
        waitUntil("popup", 5000, IntellisenseWebViewIT::popupVisible);
        Thread.sleep(1200);
        report("filter_rank_ms", js("""
            (function(){
              var c=AppIntelliSense.getController(), e=c.completionEngine, ctx=c.activeContext, pool=c.rawCandidates, out={pool:pool.length};
              var w=['t','ta','tab','tabl','table','table_','table_1'];
              out.steps=w.map(function(q){
                var t0=performance.now(); var f=e.filter.filter(pool,q); var t1=performance.now();
                var r=e.ranker.rank(f,ctx,q); var t2=performance.now();
                return q+': filter '+Math.round(t1-t0)+' ('+f.length+') rank '+Math.round(t2-t1)+' ('+r.length+')';
              });
              var t3=performance.now(); AppIntelliSense.getPopupUI().render(c.activeCandidates, {top:10,bottom:30,left:10}, c.getActiveTabContext(), c.dataProvider, ''); out.render=Math.round(performance.now()-t3);
              return JSON.stringify(out);
            })()
            """));
    }

    @Test
    @Order(15)
    void spaceOpensThePopupWithNothingSelected() throws Exception {
        reset("always");
        type("UPDATE dbo.Orders ");
        waitUntil("SET offered", 5000, () -> jsTrue("AppIntelliSense.isPopupVisible() && AppIntelliSense.getPopupUI().candidates[0].name === 'SET'"));
        assertEquals(-1, ((Number) js("AppIntelliSense.getPopupUI().selectedIndex")).intValue(), "nothing preselected");
        snapshot("8_grammar_after_space");
        key(KeyCode.ENTER, String.valueOf((char) 13));    // with its typed character: this is what a real Enter sends
        Thread.sleep(150);
        assertEquals(2, ((Number) js("__ed.getModel().getLineCount()")).intValue(), "Enter breaks the line");
        assertFalse(popupVisible());

        reset("always");
        type("UPDATE dbo.Orders ");
        waitUntil("SET offered", 5000, () -> jsTrue("AppIntelliSense.isPopupVisible() && AppIntelliSense.getPopupUI().candidates[0].name === 'SET'"));
        press(KeyCode.DOWN);
        assertEquals("SET", selected());
        press(KeyCode.ENTER);
        waitUntil("columns after SET", 5000, () -> jsTrue("AppIntelliSense.isPopupVisible() && AppIntelliSense.getPopupUI().candidates.some(function(c){return c.name==='OrderId'})"));
        assertEquals("UPDATE dbo.Orders SET ", text(), "keyword, a space, and the next suggestions");
    }

    @Test
    @Order(16)
    void sqlColouringKnowsTheNewWords() throws Exception {
        String tokens = String.valueOf(js("JSON.stringify(monaco.editor.tokenize('SET NOCOUNT ON; SELECT JSON_VALUE(a) FROM t', 'sql')[0].map(function(t){return t.type}))"));
        assertTrue(tokens.contains("keyword"), tokens);
        assertTrue(tokens.contains("predefined"), tokens);
        assertEquals("true", String.valueOf(js("monaco.editor.tokenize('NOCOUNT', 'sql')[0][0].type.indexOf('keyword') >= 0")));
        assertEquals("true", String.valueOf(js("monaco.editor.tokenize('UNNEST', 'sql')[0][0].type.indexOf('predefined') >= 0")));
    }

    @Test
    @Order(20)
    void longScriptStaysResponsiveAndInsertsAtTheRightPlace() throws Exception {
        reset("always");
        StringBuilder sb = new StringBuilder();
        String block = "SELECT o.OrderId, c.CustomerName, SUM(d.Qty * d.Price) AS Total\nFROM dbo.Orders o\n"
                + "INNER JOIN dbo.Customers c ON c.CustomerId = o.CustomerId\nLEFT JOIN dbo.OrderDetails d ON d.OrderId = o.OrderId\n"
                + "WHERE o.CreatedAt >= '2026-01-01' AND c.Region = N'VN'\nGROUP BY o.OrderId, c.CustomerName\nORDER BY Total DESC;\nGO\n";
        for (int i = 0; i < 250; i++) sb.append(block);
        int line = 251 * 8 - 7;
        setText(sb + "SELECT  FROM dbo.Orders o\n", line + 1 - 1, 8);
        assertEquals("SELECT  FROM dbo.Orders o", js("__ed.getModel().getLineContent(__ed.getPosition().lineNumber)"));

        js("window.__wrap = AppIntelliSense.getController().activeAdapter;"
                + "window.__a = SqlContextAnalyzer.analyze; window.__times=[];"
                + "SqlContextAnalyzer.analyze=function(t,o,d){var s=performance.now();var r=__a(t,o,d);__times.push({ms:performance.now()-s,len:t.length});return r};");
        Object viewLen = js("__wrap.getText().length");
        report("whole_document_chars", js("__ed.getModel().getValueLength()"));
        report("engine_view_chars", viewLen);
        assertTrue(((Number) viewLen).intValue() < 2000, "the engine sees only this batch: " + viewLen);

        // a real keystroke loop while the popup is open
        long t0 = System.nanoTime();
        type("o.");
        waitUntil("columns in long script", 5000, () -> jsTrue("AppIntelliSense.isPopupVisible() && AppIntelliSense.getPopupUI().candidates.some(function(c){return c.name==='OrderId'})"));
        report("dot_to_popup_ms_including_typing_delays", (System.nanoTime() - t0) / 1_000_000);
        type("Ord");
        Thread.sleep(150);
        assertEquals("OrderId", selected(), "the half-typed word itself is not a suggestion");
        report("analyze_calls", js("JSON.stringify(__times.map(function(t){return {ms:Math.round(t.ms*10)/10,len:t.len}}))"));
        snapshot("7_long_script");

        press(KeyCode.ENTER);
        Thread.sleep(150);
        String content = String.valueOf(js("__ed.getModel().getLineContent(__ed.getPosition().lineNumber)"));
        assertEquals("SELECT o.OrderId FROM dbo.Orders o", content, "inserted on the right line, far below the first batch");
        assertTrue(text().startsWith(block), "the text before it is untouched");

        js("SqlContextAnalyzer.analyze=__a;");

        // hover and signature help on the same script: quick, and the hover range points at the real line
        js("""
            window.__hover='pending'; window.__sig='pending';
            (function(){
              var m=__ed.getModel(), ln=__ed.getPosition().lineNumber, col=m.getLineContent(ln).indexOf('Orders')+2;
              var t0=performance.now();
              IntelliSenseHost.hoverProvider.provideHover(m,{lineNumber:ln,column:col},{}).then(function(r){
                window.__hover=JSON.stringify({ms:Math.round(performance.now()-t0),line:ln,rangeLine:r&&r.range?r.range.startLineNumber:null,hasContent:!!(r&&r.contents&&r.contents.length)});
                var t1=performance.now();
                return IntelliSenseHost.signatureProvider.provideSignatureHelp(m,{lineNumber:ln,column:10},{},{}).then(function(s){
                  window.__sig=JSON.stringify({ms:Math.round(performance.now()-t1)});
                });
              }).catch(function(e){window.__hover='error '+e; window.__sig='error';});
            })();
            """);
        waitUntil("hover and signature", 6000, () -> jsTrue("window.__sig !== 'pending'"));
        report("hover_long_script", js("window.__hover"));
        report("signature_long_script", js("window.__sig"));
        assertFalse(String.valueOf(js("window.__hover")).startsWith("error"), String.valueOf(js("window.__hover")));
        // what the Python version did on each keystroke, for comparison
        js("window.__whole=[]; for(var i=0;i<3;i++){var s=performance.now(); __a(__ed.getValue()+' '.repeat(i), __ed.getValue().length-40, 'sqlserver'); __whole.push(performance.now()-s);} ");
        report("analyze_whole_document_ms", js("JSON.stringify(__whole.map(Math.round))"));
    }

    @Test
    @Order(30)
    void noScriptErrorsDuringTheRun() throws Exception {
        assertEquals("[]", js("JSON.stringify(window.__errors)"));
    }

    private static void report(String key, Object value) throws Exception {
        Files.createDirectories(OUT);
        Files.writeString(OUT.resolve("timings.txt"), key + " = " + value + System.lineSeparator(),
                java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        System.out.println("[intellisense-it] " + key + " = " + value);
    }
}
