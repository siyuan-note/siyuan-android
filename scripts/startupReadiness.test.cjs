const assert = require("node:assert/strict");
const {readFileSync, mkdtempSync, writeFileSync, rmSync} = require("node:fs");
const {join} = require("node:path");
const {tmpdir} = require("node:os");
const {execFileSync} = require("node:child_process");
const {test} = require("node:test");
const {runInNewContext} = require("node:vm");

const source = readFileSync(join(__dirname, "../app/src/main/java/org/b3log/siyuan/MainActivity.java"), "utf8");
const monitor = source.slice(source.indexOf("private void pollMainPageReadiness"));
const script = monitor.match(/evaluateJavascript\("""([\s\S]*?)"""/)[1];
const status = (ready, failed, pathname = "/stage/build/mobile/", stage = "") => JSON.parse(JSON.stringify(runInNewContext(script, {
    window: {siyuan: ready === undefined ? undefined : {isReady: ready}}, location: {pathname},
    document: {documentElement: {getAttribute: name => name === "data-startup-stage" ? stage : failed ? "failed" : "loading"}},
})));

test("native boot remains after HTML navigation until the frontend is painted", () => {
    assert.deepEqual(status(undefined, false), {ready: false, failed: false, stage: ""});
    assert.deepEqual(status(false, false), {ready: false, failed: false, stage: ""});
    const navigation = source.slice(source.indexOf("public void onPageFinished"), source.indexOf("public void onReceivedError"));
    assert.doesNotMatch(navigation, /mainPageReady\s*=\s*true|removeBootWebView\(/);
});

test("readiness, frontend failures and authentication release native startup independently", () => {
    assert.deepEqual(status(true, false), {ready: true, failed: false, stage: ""});
    assert.deepEqual(status(false, true), {ready: false, failed: true, stage: ""});
    assert.deepEqual(status(undefined, false, "/check-auth"), {ready: true, failed: false, stage: ""});
});

test("frontend stage progress is available for extending the native deadline", () => {
    assert.deepEqual(status(false, false, "/stage/build/mobile/", "interface"), {ready: false, failed: false, stage: "interface"});
});

test("native readiness releases the boot view once and ignores cancelled or obsolete callbacks", () => {
    const methods = source.slice(source.indexOf("private void scheduleMainPageWatchdog()"),
        source.indexOf("private void handleMainPageWatchdog()"));
    const temporary = mkdtempSync(join(tmpdir(), "siyuan-android-startup-"));
    const harness = `import java.util.*;
import java.util.function.Consumer;
public class ReadinessHarness {
    private static final long MAIN_PAGE_LOAD_TIMEOUT = 30000;
    private boolean kernelBootCompleted = true, mainPageReady, bootVisible = true, finishing;
    private int mainPageReadinessGeneration;
    private String mainPageStartupStage = "";
    private Runnable mainPageReadinessPoll;
    private final Handler mainPageWatchdogHandler = new Handler();
    private final Runnable mainPageWatchdog = () -> {};
    private WebView webView = new WebView();
    private boolean isFinishing() { return finishing; }
    private void removeBootWebView() { bootVisible = false; }
    ${methods}
    static class Handler {
        final Map<Runnable, Long> timers = new LinkedHashMap<>();
        void removeCallbacks(Runnable callback) { timers.remove(callback); }
        void postDelayed(Runnable callback, long delay) { timers.put(callback, delay); }
        void tick() {
            Runnable callback = timers.entrySet().stream().filter(item -> item.getValue() == 250)
                .findFirst().orElseThrow().getKey();
            timers.remove(callback);
            callback.run();
        }
    }
    static class WebView {
        Consumer<String> callback;
        void evaluateJavascript(String script, Consumer<String> result) { callback = result; }
    }
    static class JSONObject {
        final String value;
        JSONObject(String value) { this.value = value; }
        boolean optBoolean(String key) { return key.equals(value); }
        String optString(String key) { return value.startsWith("stage:") ? value.substring(6) : ""; }
    }
    private static void check(boolean value) { if (!value) throw new AssertionError("Unexpected startup state"); }
    public static void main(String[] args) {
        ReadinessHarness normal = new ReadinessHarness();
        normal.scheduleMainPageWatchdog();
        normal.webView.callback.accept("stage:interface");
        check(normal.bootVisible && !normal.mainPageReady && normal.mainPageWatchdogHandler.timers.size() == 2);
        normal.mainPageWatchdogHandler.tick();
        normal.webView.callback.accept("ready");
        check(!normal.bootVisible && normal.mainPageReady && normal.mainPageWatchdogHandler.timers.isEmpty());

        ReadinessHarness failed = new ReadinessHarness();
        failed.scheduleMainPageWatchdog();
        failed.webView.callback.accept("failed");
        check(!failed.bootVisible && !failed.mainPageReady && failed.mainPageWatchdogHandler.timers.isEmpty());

        ReadinessHarness stale = new ReadinessHarness();
        stale.scheduleMainPageWatchdog();
        Consumer<String> previous = stale.webView.callback;
        stale.scheduleMainPageWatchdog();
        previous.accept("ready");
        check(stale.bootVisible && !stale.mainPageReady);
        stale.webView.callback.accept("ready");
        check(!stale.bootVisible && stale.mainPageReady);

        ReadinessHarness replacement = new ReadinessHarness();
        replacement.scheduleMainPageWatchdog();
        previous = replacement.webView.callback;
        replacement.webView = new WebView();
        previous.accept("ready");
        check(replacement.bootVisible && !replacement.mainPageReady);
        replacement.cancelMainPageWatchdog();
        check(replacement.mainPageWatchdogHandler.timers.isEmpty());
    }
}`;
    try {
        writeFileSync(join(temporary, "ReadinessHarness.java"), harness, "utf8");
        execFileSync("javac", ["-encoding", "UTF-8", "-d", temporary, join(temporary, "ReadinessHarness.java")], {windowsHide: true});
        execFileSync("java", ["-cp", temporary, "ReadinessHarness"], {windowsHide: true});
    } finally {
        assert.equal(require("node:path").dirname(temporary), tmpdir());
        rmSync(temporary, {recursive: true, force: true});
    }
});
