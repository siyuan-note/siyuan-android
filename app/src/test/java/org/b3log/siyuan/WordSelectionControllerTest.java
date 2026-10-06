package org.b3log.siyuan;

import android.os.LocaleList;
import android.view.textclassifier.TextClassification;
import android.view.textclassifier.TextClassifier;
import android.view.textclassifier.TextSelection;

import java.lang.reflect.Proxy;

public final class WordSelectionControllerTest {
    public static void main(String[] args) {
        final Delegate delegate = new Delegate();
        final WordSelectionController controller = new WordSelectionController(delegate);
        final TextClassifier classifier = (TextClassifier) Proxy.newProxyInstance(TextClassifier.class.getClassLoader(),
                new Class<?>[]{TextClassifier.class}, controller);
        final String text = "😀呀，为什么？";

        // 新旧重载均使用 UTF-16 偏移，已匹配的中文词语不再委托实体分类。
        check(controller.prepare("为什么", -1, 1));
        range(classifier.suggestSelection(text, 5, 6, LocaleList.getEmptyLocaleList()), 4, 7);
        classifier.classifyText(text, 4, 7, LocaleList.getEmptyLocaleList());
        check(delegate.classifications == 0);
        check(controller.prepare("为什么", -1, 1));
        range(classifier.suggestSelection(new TextSelection.Request.Builder(text, 5, 6).build()), 4, 7);
        classifier.classifyText(new TextClassification.Request.Builder(text, 4, 7).build());
        check(delegate.classifications == 0);
        range(classifier.suggestSelection(new TextSelection.Request.Builder(text, 5, 6).build()), 5, 6);
        check(delegate.suggestions == 1);

        // 过期请求不得消耗新一轮准备的选词，取消后正常委托系统。
        check(controller.prepare("为什么", -1, 1));
        range(classifier.suggestSelection("为什吗", 1, 2, LocaleList.getEmptyLocaleList()), 1, 2);
        range(classifier.suggestSelection(text, 5, 6, LocaleList.getEmptyLocaleList()), 4, 7);
        check(controller.prepare("为什么", -1, 1));
        controller.cancel();
        range(classifier.suggestSelection(text, 5, 6, LocaleList.getEmptyLocaleList()), 5, 6);
        classifier.classifyText(text, 4, 7, LocaleList.getEmptyLocaleList());
        check(delegate.classifications == 1);

        // 原生请求的上下文截取变化和代理对均保持相对偏移。
        check(controller.prepare("为什么", -1, 1));
        range(classifier.suggestSelection("为什么", 1, 2, LocaleList.getEmptyLocaleList()), 0, 3);
        check(controller.prepare("什么", 0, 1));
        range(classifier.suggestSelection("为什么", 1, 2, LocaleList.getEmptyLocaleList()), 1, 3);
        check(controller.prepare("𠀀字", 0, 1));
        range(classifier.suggestSelection("😀𠀀字", 2, 4, LocaleList.getEmptyLocaleList()), 2, 5);
        for (String word : new String[]{null, "", "hello", new String(new char[4097])}) {
            check(!controller.prepare(word, -1, 1));
        }
        check(!controller.prepare("为什么", 1, 1));
        check(!controller.prepare("为什么", -1, -1));
        check(!controller.prepare("为什么", 0, 0));
        check(!controller.prepare("为什么", Integer.MIN_VALUE, Integer.MAX_VALUE));
        check(!controller.prepare("为什么", -1, 0));
        check(controller.prepare("为什么", -1, 1));
        range(classifier.suggestSelection("为什么", 0, 1, LocaleList.getEmptyLocaleList()), 0, 1);
        range(classifier.suggestSelection("为什么", 1, 2, LocaleList.getEmptyLocaleList()), 0, 3);

        // 未拦截的接口及其异常保持系统行为。
        check(classifier.getMaxGenerateLinksTextLength() == 17);
        check(delegate.otherCalls == 1);
        delegate.fail = true;
        try {
            classifier.getMaxGenerateLinksTextLength();
            throw new AssertionError("Missing delegated exception");
        } catch (IllegalStateException expected) {
            check(expected.getMessage().equals("delegate failure"));
        }
        System.out.println("Native word selection controller cases passed");
    }

    private static void range(TextSelection selection, int start, int end) {
        check(selection.getSelectionStartIndex() == start && selection.getSelectionEndIndex() == end);
    }

    private static void check(boolean value) {
        if (!value) throw new AssertionError("Native word selection regression");
    }

    private static final class Delegate implements TextClassifier {
        int suggestions;
        int classifications;
        int otherCalls;
        boolean fail;

        @Override public TextSelection suggestSelection(CharSequence text, int start, int end, LocaleList locales) {
            suggestions++;
            return new TextSelection.Builder(start, end).build();
        }
        @Override public TextSelection suggestSelection(TextSelection.Request request) {
            return suggestSelection(request.getText(), request.getStartIndex(), request.getEndIndex(), null);
        }
        @Override public TextClassification classifyText(CharSequence text, int start, int end, LocaleList locales) {
            classifications++;
            return new TextClassification.Builder().build();
        }
        @Override public TextClassification classifyText(TextClassification.Request request) {
            return classifyText(request.getText(), request.getStartIndex(), request.getEndIndex(), null);
        }
        @Override public int getMaxGenerateLinksTextLength() {
            otherCalls++;
            if (fail) throw new IllegalStateException("delegate failure");
            return 17;
        }
    }
}
