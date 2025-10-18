package org.b3log.siyuan;

import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.provider.DocumentsContract;

public class AndroidFileUtils {
    public static String getPath(final Context context, final Uri uri) {
        if (DocumentsContract.isDocumentUri(context, uri)) {
            if (isExternalStorageDocument(uri)) {
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                if (split.length != 2) return null;

                final String type = split[0];

                if ("primary".equalsIgnoreCase(type))
                    return Environment.getExternalStorageDirectory() + "/" + split[1];
            }
        }

        return null;
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is ExternalStorageProvider.
     */
    public static boolean isExternalStorageDocument(Uri uri) {
        return "com.android.externalstorage.documents".equals(uri.getAuthority());
    }
}
