const {test} = require("node:test");
const assert = require("node:assert/strict");
const {mkdtempSync, rmSync, readFileSync} = require("node:fs");
const {execFileSync} = require("node:child_process");
const {join, dirname, resolve} = require("node:path");
const {tmpdir} = require("node:os");

test("native Markdown encoding, routing, session reservation, image types and draft storage", () => {
    const output = mkdtempSync(join(tmpdir(), "siyuan-markdown-core-"));
    try {
        const sources = ["MarkdownCodec", "MarkdownRegistry", "MarkdownStore", "MarkdownRouting", "MarkdownImages", "MarkdownImageImport"].map(name =>
            join(__dirname, "../app/src/main/java/org/b3log/siyuan/markdown", name + ".java"));
        sources.push(join(__dirname, "../app/src/test/java/org/b3log/siyuan/markdown/MarkdownCoreTest.java"));
        execFileSync("javac", ["-encoding", "UTF-8", "-d", output, ...sources], {windowsHide: true});
        execFileSync("java", ["-cp", output, "org.b3log.siyuan.markdown.MarkdownCoreTest"], {windowsHide: true});
    } finally {
        assert.equal(dirname(resolve(output)), resolve(tmpdir()));
        rmSync(output, {recursive: true, force: true});
    }
});

test("destroyed startup cleans the host created by earlier accepted I/O and cannot rebuild its page", () => {
    const container = readFileSync(join(__dirname, "../app/src/main/java/org/b3log/siyuan/MarkdownActivity.java"), "utf8");
    const destroy = container.slice(container.indexOf("@Override protected void onDestroy()"));
    assert.ok(destroy.indexOf("destroyed = true") < destroy.indexOf("IO.execute(() ->"));
    assert.ok(destroy.indexOf("IO.execute(() ->") < destroy.indexOf("MarkdownHost previous = host"));
    assert.match(container, /runtime = MarkdownRuntime\.prepare\(this\);\s*if \(destroyed\) return;/);
    assert.match(container, /if \(!destroyed\) runOnUiThread\(this::loadEditor\)/);
});

test("lightweight process has separate WebView storage and retains original URI grants", () => {
    const source = relative => readFileSync(join(__dirname, "../app/src/main", relative), "utf8");
    const manifest = source("AndroidManifest.xml");
    const activity = manifest.match(/<activity\s+android:name="\.MarkdownActivity"[\s\S]*?\/>/)[0];
    assert.match(activity, /android:process=":markdown"/);
    assert.match(activity, /android:exported="false"/);
    const application = source("java/org/b3log/siyuan/App.java");
    assert.match(application, /ProcessGlobalConfig\.apply\(new ProcessGlobalConfig\(\)\.setDataDirectorySuffix\(this, "markdown"\)\)/);
    assert.ok(application.indexOf("ProcessGlobalConfig.apply") < application.indexOf("Utils.init(this)"));
    const boot = source("java/org/b3log/siyuan/BootActivity.java");
    assert.match(boot, /new Intent\(request\)\.setClass\(this, MarkdownActivity\.class\)/);
    assert.match(boot, /markdown\.addFlags\(Intent\.FLAG_ACTIVITY_NEW_TASK\)/);
    const container = source("java/org/b3log/siyuan/MarkdownActivity.java");
    assert.match(container, /!App\.isMarkdownWebViewReady\(\)/);
    assert.doesNotMatch(container, /\bmobile\.Mobile\.|\bMobile\.exit\(/);
    assert.doesNotMatch(container, /addJavascriptInterface\(/);
    assert.match(container, /!isMainFrame \|\| !trusted\(sourceOrigin\)/);
    assert.match(container, /setDomStorageEnabled\(false\)/);
    assert.match(container, /frame\.addView\(web,/);
    assert.match(container, /setContentView\(frame\); SystemBars\.fitContent\(this\)/);
});

test("tree protocol isolates directory reads, document epochs and API 26-safe grants", () => {
    const container = readFileSync(join(__dirname, "../app/src/main/java/org/b3log/siyuan/MarkdownActivity.java"), "utf8");
    const files = readFileSync(join(__dirname, "../app/src/main/java/org/b3log/siyuan/markdown/MarkdownFiles.java"), "utf8");
    const treeFiles = readFileSync(join(__dirname, "../app/src/main/java/org/b3log/siyuan/markdown/MarkdownTreeFiles.java"), "utf8");
    for (const action of ["tree-state", "tree-list", "tree-choose-root", "tree-save-view", "tree-prepare-open", "tree-commit-open", "tree-cancel-open"]) assert.ok(container.includes('"' + action + '"'));
    assert.match(container, /private final ExecutorService treeIO = Executors\.newSingleThreadExecutor\(\)/);
    assert.match(container, /token\.documentEpoch != switchEpoch/);
    assert.match(container, /host\.sessionId\.equals\(\(\(JSONObject\) payload\)\.optString\("sessionId"\)\)/);
    assert.match(container, /treeSwitch != null\) treeSwitch\.drain\(\)/);
    assert.match(treeFiles, /buildChildDocumentsUriUsingTree/);
    assert.match(treeFiles, /CancellationSignal/);
    assert.match(treeFiles, /findDocumentPath/);
    assert.doesNotMatch(files + treeFiles, /DocumentsContract\.isChildDocument\(/);
    assert.doesNotMatch(treeFiles, /getExternalStorageDirectory|\.getPath\(\)\.split|new File\(/);
});

test("system generic MIME routing accepts opaque document IDs then rejects non-Markdown names before workspace boot", () => {
    const manifest = readFileSync(join(__dirname, "../app/src/main/AndroidManifest.xml"), "utf8");
    const generic = manifest.match(/<intent-filter>[\s\S]*?<\/intent-filter>/g).find(value => value.includes('mimeType="text/plain"'));
    assert.ok(generic); assert.doesNotMatch(generic, /pathPattern|pathSuffix/);
    assert.match(generic, /scheme="content"/);
    const boot = readFileSync(join(__dirname, "../app/src/main/java/org/b3log/siyuan/BootActivity.java"), "utf8");
    assert.match(boot, /MarkdownRouting\.genericDocument/);
    assert.match(boot, /setMessage\(R\.string\.markdown_unsupported\)/);
});
