package org.b3log.siyuan;

public final class KeyboardHideStateTest {
    public static void main(String[] args) {
        final KeyboardHideState state = new KeyboardHideState();
        final int[] calls = {0};
        final Runnable cleanup = () -> calls[0]++;
        final Object animation = new Object();
        state.visibilityChanged(true);
        state.request(true, cleanup);
        state.flush();
        check(calls[0] == 0);
        state.animationStarted(animation);
        state.visibilityChanged(false);
        state.request(false, () -> calls[0] += 100);
        state.flush();
        check(calls[0] == 0);
        state.animationEnded(animation);
        state.flush();
        state.flush();
        check(calls[0] == 1);

        // 重新打开键盘和窗口失焦都应取消尚未执行的清理。
        state.request(false, cleanup);
        state.visibilityChanged(true);
        state.visibilityChanged(false);
        state.flush();
        check(calls[0] == 1);
        state.request(true, cleanup);
        state.cancel();
        state.flush();
        check(calls[0] == 1);

        // 没有动画时立即完成；交叠动画全部结束后才允许清理。
        state.request(false, cleanup);
        state.flush();
        check(calls[0] == 2);
        final Object second = new Object();
        state.animationStarted(animation);
        state.animationStarted(second);
        state.request(false, cleanup);
        state.animationEnded(animation);
        state.flush();
        check(calls[0] == 2);
        state.animationEnded(second);
        state.flush();
        check(calls[0] == 3);
    }

    private static void check(boolean condition) {
        if (!condition) {
            throw new AssertionError("Unexpected keyboard cleanup state");
        }
    }
}
