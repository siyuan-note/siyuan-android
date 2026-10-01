package org.b3log.siyuan;

import android.icu.text.BreakIterator;

import java.util.Locale;

final class WordSelection {
    private static final int MAX_TEXT_LENGTH = 4096;

    private WordSelection() {
    }

    static String expand(final String text, final int start, final int end) {
        if (text == null || text.length() > MAX_TEXT_LENGTH || start < 0 || end > text.length() || start >= end
                || end - start != Character.charCount(text.codePointAt(start))
                || Character.UnicodeScript.of(text.codePointAt(start)) != Character.UnicodeScript.HAN) {
            return "";
        }
        final BreakIterator iterator = BreakIterator.getWordInstance(Locale.SIMPLIFIED_CHINESE);
        iterator.setText(text);
        // 使用系统词典扩展单字选区，保持 Java 与 DOM 的 UTF-16 偏移一致。
        final int wordStart = iterator.isBoundary(start) ? start : iterator.preceding(start);
        final int wordEnd = iterator.following(start);
        if (wordStart < 0 || wordStart > start || wordEnd < end || wordEnd > text.length()) {
            return "";
        }
        return "[" + wordStart + "," + wordEnd + "]";
    }
}
