package org.b3log.siyuan;

public final class WordSelectionTest {
    public static void main(String[] args) {
        check("[7,10]", WordSelection.expand("我的天空大地呀为什么", 8, 9));
        check("[0,3]", WordSelection.expand("为什么", 0, 1));
        check("[0,3]", WordSelection.expand("为什么", 2, 3));
        check("[4,7]", WordSelection.expand("😀呀，为什么？", 5, 6));
        check("[0,3]", WordSelection.expand("為什麼", 1, 2));
        check("", WordSelection.expand("hello world", 8, 9));
        check("", WordSelection.expand("为什么", 0, 2));
        check("", WordSelection.expand("为什么", -1, 1));
        check("", WordSelection.expand("为什么", 1, 4));
        check("", WordSelection.expand("为什么", 2, 1));
        check("", WordSelection.expand(null, 0, 1));
        check("", WordSelection.expand("𠀀", 0, 1));
        check("", WordSelection.expand("𠀀", 1, 2));
        check("[0,2]", WordSelection.expand("𠀀", 0, 2));
        check("", WordSelection.expand(new String(new char[4097]), 0, 1));
        System.out.println("Word selection cases passed");
    }

    private static void check(final String expected, final String actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError("Expected " + expected + ", got " + actual);
        }
    }
}
