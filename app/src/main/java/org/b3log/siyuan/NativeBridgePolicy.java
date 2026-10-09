package org.b3log.siyuan;

import java.net.URI;
import java.net.URISyntaxException;

/** Pure navigation policy, shared by the WebView callbacks and native bridge. */
final class NativeBridgePolicy {
    static final String ORIGIN = "http://127.0.0.1:6806";

    static boolean isTrustedOrigin(final String value) {
        try {
            final URI uri = new URI(value);
            return "http".equals(uri.getScheme()) && "127.0.0.1".equals(uri.getHost())
                    && uri.getPort() == 6806 && uri.getRawUserInfo() == null;
        } catch (URISyntaxException | NullPointerException e) {
            return false;
        }
    }

    static boolean isTrustedDocument(final String value) {
        if (!isTrustedOrigin(value)) {
            return false;
        }
        try {
            final String path = new URI(value).getRawPath();
            return "/".equals(path) || "/check-auth".equals(path)
                    || "/appearance/boot/index.html".equals(path)
                    || "/stage/build/app/".equals(path) || "/stage/build/app/index.html".equals(path)
                    || "/stage/build/mobile/".equals(path) || "/stage/build/mobile/index.html".equals(path)
                    || "/stage/build/desktop/".equals(path) || "/stage/build/desktop/index.html".equals(path);
        } catch (URISyntaxException e) {
            return false;
        }
    }

    static boolean allowSubframeNavigation(final String value) {
        try {
            final URI uri = new URI(value);
            final String scheme = uri.getScheme();
            if ("http".equals(scheme) || "https".equals(scheme)) {
                if (uri.getHost() == null) {
                    return false;
                }
                if (isTrustedOrigin(value)) {
                    // Check decoded/normalized aliases too, but never turn a child
                    // navigation into loadUrl() on the privileged main WebView.
                    final String decoded = uri.getPath().replaceAll("/{2,}", "/");
                    String path = new URI(null, null, decoded, null).normalize().getPath();
                    if (path.isEmpty()) {
                        path = "/";
                    }
                    if (isTrustedDocument(ORIGIN + path) || isTrustedDocument(ORIGIN + path + "/")
                            || path.endsWith("/") && isTrustedDocument(ORIGIN + path.substring(0, path.length() - 1))) {
                        return false;
                    }
                }
                // Preserve existing HTML assets, widgets and user-authored local or
                // external iframe links. Only the main document can obtain a native
                // capability; the map's own navigation is additionally limited by CSP.
                return true;
            }
            return "about:blank".equals(value) || "about:srcdoc".equals(value);
        } catch (URISyntaxException | NullPointerException e) {
            return false;
        }
    }

    private NativeBridgePolicy() { }
}
