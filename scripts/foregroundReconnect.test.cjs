const assert = require("node:assert/strict");
const {readFileSync} = require("node:fs");
const {join} = require("node:path");
const {test} = require("node:test");
const {runInNewContext} = require("node:vm");

const source = readFileSync(join(__dirname, "../app/src/main/java/org/b3log/siyuan/MainActivity.java"), "utf8");
const foreground = source.slice(source.indexOf("public void onForeground(Activity activity)"), source.indexOf("public void onBackground(Activity activity)"));
const script = foreground.match(/evaluateJavascript\("([^"]*)", null\)/)[1];

test("foreground reconnection waits until the frontend callback exists", () => {
    for (const callback of [undefined, null, false, "not a function"]) {
        assert.doesNotThrow(() => runInNewContext(script, {window: {reconnectWebSocket: callback}}));
    }
});

test("foreground reconnection calls the available frontend callback once", () => {
    let calls = 0;
    const window = {reconnectWebSocket() {
        assert.equal(this, window);
        calls++;
    }};
    runInNewContext(script, {window});
    assert.equal(calls, 1);
});

test("foreground reconnection preserves errors from an available callback", () => {
    const error = new Error("reconnection failed");
    assert.throws(() => runInNewContext(script, {window: {reconnectWebSocket() { throw error; }}}), value => value === error);
});
