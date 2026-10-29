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
        // 透明导航栏由容器 padding 避让，不与输入法高度相加。
        check("gesture navigation and keyboard", 1273, 2249, 127, 2400, 1000);
        check("three button navigation and keyboard", 1273, 2129, 127, 2400, 1000);
        check("navigation bar already above floating keyboard", -1, 2129, 127, 2400, 100);
        check("navigation bar after keyboard hide", -1, 2129, 127, 2400, 0);
        check("landscape side navigation", 600, 900, 0, 900, 300);
        check("caption bar in freeform window", 350, 500, 50, 600, 200);
    }

    private static void check(String name, int expected, int contentHeight, int contentTop,
                              int windowHeight, int imeHeight) {
        final int actual = KeyboardContentHeight.calculate(contentHeight, contentTop, windowHeight, imeHeight);
        if (actual != expected) {
            throw new AssertionError(name + ": expected " + expected + ", got " + actual);
        }
    }
}
