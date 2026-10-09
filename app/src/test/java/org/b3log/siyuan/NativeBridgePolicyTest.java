package org.b3log.siyuan;

public final class NativeBridgePolicyTest {
    public static void main(String[] args) {
        for (String path : new String[]{"/", "/check-auth", "/appearance/boot/index.html",
                "/stage/build/app/", "/stage/build/mobile/", "/stage/build/desktop/",
                "/stage/build/mobile/index.html?foo=bar#fragment"}) {
            check(NativeBridgePolicy.isTrustedDocument(NativeBridgePolicy.ORIGIN + path));
        }
        for (String url : new String[]{null, "", "https://127.0.0.1:6806/", "http://127.0.0.1:6807/",
                "http://localhost:6806/", "http://127.0.0.1:6806.evil/", "http://127.0.0.1.evil:6806/",
                "http://127.0.0.1:6806@evil/", "http://user@127.0.0.1:6806/", "http://127.0.0.1/",
                "http://127.0.0.1:6806/assets/evil.html", "http://127.0.0.1:6806/api/file/getFile",
                "http://127.0.0.1:6806/stage/build/mobile/../evil", "http://127.0.0.1:6806/%2f",
                "http://127.0.0.1:6806/stage/build/mobile/%69ndex.html", "file:///index.html",
                "javascript:alert(1)", "data:text/html,test"}) {
            check(!NativeBridgePolicy.isTrustedDocument(url));
        }
        for (String url : new String[]{"https://map.example/", "https://evil.example/?127.0.0.1",
                "http://map.example/", "about:blank", "about:srcdoc",
                "http://127.0.0.1:6806/assets/example.html?iframe=true&notebook=box",
                "http://127.0.0.1:6806/assets/%E4%B8%AD%E6%96%87.html?iframe=true",
                "http://127.0.0.1:6806/widgets/example/?siyuan-version=3.8.0",
                "http://127.0.0.1:6806/widgets/example/index.html?mode=compact#chart",
                "http://127.0.0.1:6806/stage/build/desktop/pdfjs/web/viewer.html?file=example.pdf",
                "http://localhost:8080/", "http://127.0.0.1:6807/", "http://[::1]:3000/"}) {
            check(NativeBridgePolicy.allowSubframeNavigation(url));
        }
        // The same policy is evaluated for iframe redirects, never view.loadUrl().
        for (String url : new String[]{null, "http://127.0.0.1:6806", "http://127.0.0.1:6806/", "http://127.0.0.1:6806/check-auth",
                "http://127.0.0.1:6806/check-auth/", "http://127.0.0.1:6806//stage/build/mobile/",
                "http://127.0.0.1:6806/stage/build/mobile",
                "http://127.0.0.1:6806/stage/build/mobile/", "http://127.0.0.1:6806/stage/build/app/index.html",
                "http://127.0.0.1:6806/assets/../stage/build/mobile/",
                "http://127.0.0.1:6806/%73tage/build/mobile/",
                "javascript:alert(1)", "intent://app", "file:///index.html", "data:text/html,test"}) {
            check(!NativeBridgePolicy.allowSubframeNavigation(url));
        }
        final String nonce = "0123456789abcdef0123456789abcdef0123456789abcdef";
        for (String page : new String[]{"wrapper.html", "index.html"}) {
            for (String provider : new String[]{"openfreemap", "amap", "tencent", "baidu"}) {
                final String url = NativeBridgePolicy.ORIGIN + "/stage/map/" + page + "?provider=" + provider;
                check(NativeBridgePolicy.allowSubframeNavigation(url));
                check(NativeBridgePolicy.allowSubframeNavigation(url + "#" + nonce + ":" + nonce));
                check(!NativeBridgePolicy.isTrustedDocument(url));
            }
        }
        System.out.println("Native bridge origin and navigation cases passed");
    }
    private static void check(boolean value) {
        if (!value) throw new AssertionError();
    }
}
