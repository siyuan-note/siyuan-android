package org.b3log.siyuan;

public final class KeyboardContentHeightTest {
    public static void main(String[] args) {
        check("hidden", -1, 900, 50, 1000, 0);
        check("overlap", 650, 900, 50, 1000, 300);
        check("already resized", -1, 650, 50, 1000, 300);
        check("navigation bar", 650, 850, 50, 1000, 300);
        check("multi window", 350, 500, 50, 600, 200);
        check("floating keyboard", -1, 900, 50, 1000, 0);
        check("clamped", 0, 500, 50, 600, 600);
        // 状态栏占位由原生容器保留，WebView 高度不重复包含该区域。
        check("status bar padding", 1273, 2273, 127, 2400, 1000);
        check("status bar and resized window", -1, 1273, 127, 2400, 1000);
        // 收起目标状态在父容器恢复高度前到达时，也应立即解除 WebView 的固定高度。
        check("hide before parent layout", -1, 1273, 127, 2400, 0);
        check("hide after parent layout", -1, 2273, 127, 2400, 0);
    }

    private static void check(String name, int expected, int contentHeight, int contentTop,
                              int windowHeight, int imeHeight) {
        final int actual = KeyboardContentHeight.calculate(contentHeight, contentTop, windowHeight, imeHeight);
        if (actual != expected) {
            throw new AssertionError(name + ": expected " + expected + ", got " + actual);
        }
    }
}
