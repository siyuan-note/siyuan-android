package org.b3log.siyuan;

import java.net.URI;
import java.net.URISyntaxException;

final class AssetDownload {
    private AssetDownload() {
    }

    // 校验下载地址属于本地内核，保留资源路径编码和笔记本查询参数。
    static String toAssetPath(final String url, final String kernelURL) {
        if (null == url) {
            return null;
        }
        try {
            final URI source = new URI(url);
            final URI kernel = new URI(kernelURL);
            final String path = source.getRawPath();
            if (!kernel.getScheme().equals(source.getScheme()) || !kernel.getHost().equals(source.getHost())
                    || kernel.getPort() != source.getPort() || null != source.getRawUserInfo()
                    || null == path || !path.startsWith("/assets/") || path.length() <= "/assets/".length()) {
                return null;
            }
            final String query = source.getRawQuery();
            return path.substring(1) + (null == query ? "" : "?" + query);
        } catch (final URISyntaxException e) {
            return null;
        }
    }
}
