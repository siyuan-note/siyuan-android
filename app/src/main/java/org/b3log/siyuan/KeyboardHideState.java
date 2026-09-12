package org.b3log.siyuan;

import java.util.HashSet;
import java.util.Set;

final class KeyboardHideState {
    private final Set<Object> animations = new HashSet<>();
    private boolean visible;
    private Runnable pending;
    private boolean explicit;

    void visibilityChanged(boolean visible) {
        this.visible = visible;
        if (visible) {
            cancel();
        }
    }

    void animationStarted(Object animation) {
        animations.add(animation);
    }

    void animationEnded(Object animation) {
        animations.remove(animation);
    }

    void request(boolean explicit, Runnable cleanup) {
        // 系统隐藏通知不能覆盖主动关闭时清除选区的要求。
        if (pending == null || explicit || !this.explicit) {
            pending = cleanup;
            this.explicit = explicit;
        }
    }

    void cancel() {
        pending = null;
        explicit = false;
    }

    void flush() {
        if (visible || !animations.isEmpty() || pending == null) {
            return;
        }
        final Runnable cleanup = pending;
        cancel();
        cleanup.run();
    }
}
