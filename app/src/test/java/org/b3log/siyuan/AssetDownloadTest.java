package org.b3log.siyuan;

public final class AssetDownloadTest {
    public static void main(String[] args) {
        check("assets/movie.mp4", "http://127.0.0.1:6806/assets/movie.mp4");
        check("assets/%E8%A7%86%E9%A2%91.mp4?box=20261006000000-box0001&download=true",
                "http://127.0.0.1:6806/assets/%E8%A7%86%E9%A2%91.mp4?box=20261006000000-box0001&download=true#t=10");
        check("assets/a%23b%3Fc.mp4?box=one%26two",
                "http://127.0.0.1:6806/assets/a%23b%3Fc.mp4?box=one%26two");
        check("assets/audio.ogg", "http://127.0.0.1:6806/assets/audio.ogg");
        for (final String url : new String[]{null, "", "assets/movie.mp4", "/assets/movie.mp4",
                "https://127.0.0.1:6806/assets/movie.mp4", "http://127.0.0.1:6807/assets/movie.mp4",
                "http://127.0.0.1/assets/movie.mp4", "http://localhost:6806/assets/movie.mp4",
                "http://example.com/assets/movie.mp4", "http://127.0.0.1.example.com:6806/assets/movie.mp4",
                "http://127.0.0.1:6806@evil.example/assets/movie.mp4",
                "http://user@127.0.0.1:6806/assets/movie.mp4", "file:///assets/movie.mp4",
                "http://127.0.0.1:6806/export/movie.mp4", "http://127.0.0.1:6806/assets",
                "http://127.0.0.1:6806/assets/", "http://127.0.0.1:6806/assets/movie%ZZ.mp4"}) {
            check(null, url);
        }
        System.out.println("Asset download cases passed");
    }

    private static void check(final String expected, final String url) {
        final String actual = AssetDownload.toAssetPath(url, "http://127.0.0.1:6806/");
        if (null == expected ? null != actual : !expected.equals(actual)) {
            throw new AssertionError("Expected " + expected + ", got " + actual + " for " + url);
        }
    }
}
