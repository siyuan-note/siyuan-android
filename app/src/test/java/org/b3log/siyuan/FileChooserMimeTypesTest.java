package org.b3log.siyuan;

import java.util.Arrays;
import java.util.Map;

public final class FileChooserMimeTypesTest {
    public static void main(String[] args) {
        check(new String[]{"*/*"}, null);
        check(new String[]{"*/*"}, new String[]{null, "", " "});
        check(new String[]{"*/*"}, new String[]{".onnx"});
        check(new String[]{"*/*"}, new String[]{".yml,.yaml"});
        check(new String[]{"application/zip"}, new String[]{".ZIP", ".sy.zip"});
        check(new String[]{"image/png", "application/pdf"}, new String[]{" .png, application/pdf ", "IMAGE/PNG"});
        check(new String[]{"*/*"}, new String[]{"application/pdf", ".onnx"});
        check(new String[]{"image/*"}, new String[]{"image/*"});
        check(new String[]{"*/*"}, new String[]{"image/png", "*/*"});
        check(new String[]{"*/*"}, new String[]{"invalid"});
        check(new String[]{"*/*"}, new String[]{"image/"});
        System.out.println("File chooser MIME cases passed");
    }

    private static void check(final String[] expected, final String[] accepts) {
        final Map<String, String> extensions = Map.of("png", "image/png", "zip", "application/zip");
        final String[] actual = FileChooserMimeTypes.normalize(accepts, extensions::get);
        if (!Arrays.equals(expected, actual)) {
            throw new AssertionError(Arrays.toString(accepts) + ": " + Arrays.toString(actual));
        }
    }
}
