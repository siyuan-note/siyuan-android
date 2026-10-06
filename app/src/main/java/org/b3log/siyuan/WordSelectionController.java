package org.b3log.siyuan;

import android.content.Context;
import android.os.Build;
import android.view.textclassifier.TextClassification;
import android.view.textclassifier.TextClassificationManager;
import android.view.textclassifier.TextClassifier;
import android.view.textclassifier.TextSelection;
import android.webkit.WebView;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicReference;

final class WordSelectionController implements InvocationHandler {
    private final TextClassifier delegate;
    private final AtomicReference<Adjustment> pending = new AtomicReference<>();
    private final AtomicReference<TextRange> applied = new AtomicReference<>();

    WordSelectionController(final TextClassifier delegate) {
        this.delegate = delegate;
    }

    static WordSelectionController install(final WebView webView) {
        final TextClassificationManager manager = Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1 ?
                (TextClassificationManager) webView.getContext().getSystemService(Context.TEXT_CLASSIFICATION_SERVICE) : null;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1 && manager == null) {
            return null;
        }
        final TextClassifier delegate = manager == null ? webView.getTextClassifier() : manager.getTextClassifier();
        if (Proxy.isProxyClass(delegate.getClass()) &&
                Proxy.getInvocationHandler(delegate) instanceof WordSelectionController) {
            return (WordSelectionController) Proxy.getInvocationHandler(delegate);
        }
        final WordSelectionController controller = new WordSelectionController(delegate);
        // 只拦截本轮中文选词，其余接口继续委托系统分类器，包括后续新增的接口。
        final TextClassifier classifier = (TextClassifier) Proxy.newProxyInstance(TextClassifier.class.getClassLoader(),
                new Class<?>[]{TextClassifier.class}, controller);
        if (manager == null) {
            webView.setTextClassifier(classifier);
        } else {
            manager.setTextClassifier(classifier);
        }
        return controller;
    }

    boolean prepare(final String word, final int startAdjust, final int endAdjust) {
        if (word == null || word.isEmpty() || word.length() > 4096 || startAdjust > 0 || endAdjust < 0 ||
                startAdjust == 0 && endAdjust == 0) {
            return false;
        }
        final long selectedLength = (long) word.length() + startAdjust - endAdjust;
        if (selectedLength < 1 || selectedLength > 2 || -((long) startAdjust) >= word.length() ||
                Character.charCount(word.codePointAt(-startAdjust)) != selectedLength ||
                Character.UnicodeScript.of(word.codePointAt(-startAdjust)) != Character.UnicodeScript.HAN) {
            return false;
        }
        pending.set(new Adjustment(word, startAdjust, endAdjust));
        return true;
    }

    void cancel() {
        pending.set(null);
        applied.set(null);
    }

    @Override
    public Object invoke(final Object proxy, final Method method, final Object[] args) throws Throwable {
        if (method.getName().equals("suggestSelection")) {
            applied.set(null);
            final Adjustment adjustment = pending.get();
            if (adjustment != null) {
                final TextRange original = TextRange.from(args);
                final long start = (long) original.start + adjustment.start;
                final long end = (long) original.end + adjustment.end;
                if (original.start >= 0 && original.end <= original.text.length() &&
                        original.start < original.end && original.end - original.start ==
                        Character.charCount(original.text.codePointAt(original.start)) &&
                        Character.UnicodeScript.of(original.text.codePointAt(original.start)) == Character.UnicodeScript.HAN &&
                        start >= 0 && end <= original.text.length() &&
                        adjustment.word.equals(original.text.substring((int) start, (int) end)) &&
                        pending.compareAndSet(adjustment, null)) {
                    applied.set(new TextRange(original.text, (int) start, (int) end));
                    return new TextSelection.Builder((int) start, (int) end).build();
                }
            }
        } else if (method.getName().equals("classifyText")) {
            final TextRange selection = applied.getAndSet(null);
            final TextRange request = selection == null ? null : TextRange.from(args);
            if (request != null && selection.text.equals(request.text) &&
                    selection.start == request.start && selection.end == request.end) {
                // 已确定的中文词语仅需本地选区边界，无需再次进行实体分类。
                return new TextClassification.Builder().build();
            }
        }
        try {
            return method.invoke(delegate, args);
        } catch (final InvocationTargetException error) {
            throw error.getCause();
        }
    }

    private static final class Adjustment {
        final String word;
        final int start;
        final int end;

        Adjustment(final String word, final int start, final int end) {
            this.word = word;
            this.start = start;
            this.end = end;
        }
    }

    private static final class TextRange {
        final String text;
        final int start;
        final int end;

        TextRange(final String text, final int start, final int end) {
            this.text = text;
            this.start = start;
            this.end = end;
        }

        static TextRange from(final Object[] args) throws ReflectiveOperationException {
            if (args.length > 1) {
                return new TextRange(args[0].toString(), (int) args[1], (int) args[2]);
            }
            // 通过公开请求接口读取参数，同时兼容 Android 8.1 的旧重载。
            final Object request = args[0];
            final Class<?> type = request.getClass();
            return new TextRange(type.getMethod("getText").invoke(request).toString(),
                    (int) type.getMethod("getStartIndex").invoke(request),
                    (int) type.getMethod("getEndIndex").invoke(request));
        }
    }
}
