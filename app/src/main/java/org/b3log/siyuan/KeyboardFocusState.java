package org.b3log.siyuan;

final class KeyboardFocusState {
    private boolean visible;
    private boolean windowFocused;
    private boolean restorePending;

    void windowFocusChanged(boolean focused, boolean editorFocused) {
        windowFocused = focused;
        if (!focused) {
            restorePending = editorFocused && (visible || restorePending);
        } else if (!editorFocused) {
            hideRequested();
        }
    }

    boolean visibilityChanged(boolean shown) {
        if (!windowFocused || (!shown && restorePending)) {
            return false;
        }
        final boolean changed = visible != shown || restorePending;
        visible = shown;
        if (shown) {
            restorePending = false;
        }
        return changed;
    }

    boolean shouldRestore() {
        return windowFocused && restorePending;
    }

    boolean shouldIgnoreSystemHide() {
        return !windowFocused || restorePending;
    }

    void hideRequested() {
        visible = false;
        restorePending = false;
    }
}
