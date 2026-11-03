const assert = require("node:assert/strict");
const {execFileSync} = require("node:child_process");
const {mkdtempSync, rmSync, readFileSync} = require("node:fs");
const {tmpdir} = require("node:os");
const {join, dirname, resolve} = require("node:path");
const {test} = require("node:test");

test("file chooser converts accepted extensions to usable Android MIME types", () => {
    const output = mkdtempSync(join(tmpdir(), "siyuan-file-chooser-"));
    try {
        execFileSync("javac", ["-encoding", "UTF-8", "-d", output,
            join(__dirname, "../app/src/main/java/org/b3log/siyuan/FileChooserMimeTypes.java"),
            join(__dirname, "../app/src/test/java/org/b3log/siyuan/FileChooserMimeTypesTest.java")], {windowsHide: true});
        assert.match(execFileSync("java", ["-cp", output, "org.b3log.siyuan.FileChooserMimeTypesTest"],
            {windowsHide: true, encoding: "utf8"}), /MIME cases passed/);
    } finally {
        assert.equal(dirname(resolve(output)), resolve(tmpdir()));
        rmSync(output, {recursive: true, force: true});
    }
});

test("WebView file intents apply normalized types and retain multi-file selection", () => {
    const source = readFileSync(join(__dirname, "../app/src/main/java/org/b3log/siyuan/MainActivity.java"), "utf8");
    const chooser = source.slice(source.indexOf("final Intent intent = fileChooserParams.createIntent();"),
        source.indexOf("startActivityForResult(intent, REQUEST_SELECT_FILE)"));
    assert.match(chooser, /FileChooserMimeTypes\.normalize\(fileChooserParams\.getAcceptTypes\(\)/);
    assert.match(chooser, /intent\.setType\(mimeTypes\.length == 1 \? mimeTypes\[0\] : "\*\/\*"\)/);
    assert.match(chooser, /intent\.removeExtra\(Intent\.EXTRA_MIME_TYPES\)/);
    assert.match(chooser, /intent\.putExtra\(Intent\.EXTRA_MIME_TYPES, mimeTypes\)/);
    assert.match(chooser, /intent\.putExtra\(Intent\.EXTRA_ALLOW_MULTIPLE, true\)/);
});
