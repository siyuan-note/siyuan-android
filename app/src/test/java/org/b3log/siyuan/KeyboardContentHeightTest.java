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
        // 收起过程中可用高度连续增长，键盘离开内容区域后恢复自动布局。
        check("closing start", 650, 900, 50, 1000, 300);
        check("closing middle", 800, 900, 50, 1000, 150);
        check("closing end", -1, 900, 50, 1000, 0);
        // 中断收起并重新弹出时，按当前高度计算，不保留上一轮终点。
        check("reopened", 650, 900, 50, 1000, 300);
    }

    private static void check(String name, int expected, int contentHeight, int contentTop,
                              int windowHeight, int imeHeight) {
        final int actual = KeyboardContentHeight.calculate(contentHeight, contentTop, windowHeight, imeHeight);
        if (actual != expected) {
            throw new AssertionError(name + ": expected " + expected + ", got " + actual);
        }
    }
}
