package org.b3log.siyuan.markdown;

/** 在编辑页首次绘制之前选择可信安装资源中的主题样式。 */
public final class MarkdownTheme {
    private MarkdownTheme() { }
    public static boolean dark(String preference, boolean systemDark) {
        return "dark".equals(preference) || "system".equals(preference) && systemDark;
    }
    public static String page(String html, boolean dark) {
        String source = "./appearance/themes/daylight/theme.css";
        int index = dark ? html.indexOf(source) : -1;
        return index < 0 ? html : html.substring(0, index) + "./appearance/themes/midnight/theme.css" + html.substring(index + source.length());
    }
}
