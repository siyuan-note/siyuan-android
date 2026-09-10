package org.b3log.siyuan;

final class KeyboardContentHeight {
    private KeyboardContentHeight() {
    }

    static int calculate(int contentHeight, int contentTop, int windowHeight, int imeHeight) {
        if (imeHeight <= 0) {
            return -1;
        }
        final int availableHeight = Math.max(0, windowHeight - imeHeight - contentTop);
        return availableHeight >= contentHeight ? -1 : availableHeight;
    }
}
