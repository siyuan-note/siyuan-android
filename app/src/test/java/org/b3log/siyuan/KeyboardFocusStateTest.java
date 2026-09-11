package org.b3log.siyuan;

public final class KeyboardFocusStateTest {
    public static void main(String[] args) {
        final KeyboardFocusState state = new KeyboardFocusState();
        state.windowFocusChanged(true, true);
        check("initially hidden", false, state.shouldRestore());
        check("keyboard shown", true, state.visibilityChanged(true));
        check("duplicate shown notification", false, state.visibilityChanged(true));

        // 后台隐藏和返回前台时的过期隐藏通知不能取消恢复。
        state.windowFocusChanged(false, true);
        check("background hide ignored", false, state.visibilityChanged(false));
        check("no show in background", false, state.shouldRestore());
        state.windowFocusChanged(true, true);
        check("restore on return", true, state.shouldRestore());
        check("late hide ignored", false, state.visibilityChanged(false));
        check("cleanup blocked while restoring", true, state.shouldIgnoreSystemHide());
        check("restored notification", true, state.visibilityChanged(true));
        check("restore completed", false, state.shouldRestore());

        // 用户主动关闭后，再次切换应用不能重新弹出键盘。
        check("user dismisses keyboard", true, state.visibilityChanged(false));
        state.windowFocusChanged(false, true);
        state.windowFocusChanged(true, true);
        check("dismissed keyboard stays hidden", false, state.shouldRestore());

        state.visibilityChanged(true);
        state.windowFocusChanged(false, true);
        state.hideRequested();
        state.windowFocusChanged(true, true);
        check("explicit hide cancels background restore", false, state.shouldRestore());

        // 连续切换窗口保留恢复意图，编辑目标失去焦点时取消恢复。
        state.visibilityChanged(true);
        state.windowFocusChanged(false, true);
        state.windowFocusChanged(true, true);
        state.windowFocusChanged(false, true);
        state.windowFocusChanged(true, true);
        check("rapid switch retains restore", true, state.shouldRestore());
        state.windowFocusChanged(false, true);
        state.windowFocusChanged(true, false);
        check("different input target cancels restore", false, state.shouldRestore());
        check("cleanup allowed after cancellation", false, state.shouldIgnoreSystemHide());
    }

    private static void check(String name, boolean expected, boolean actual) {
        if (actual != expected) {
            throw new AssertionError(name + ": expected " + expected + ", got " + actual);
        }
    }
}
