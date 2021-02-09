package org.b3log.siyuan;

import android.os.Environment;

import org.apache.commons.io.IOUtils;

import java.io.File;

public class Conf {

    public static test() {
        final File conf = new File(Environment.getExternalStorageDirectory() + "/siyuan/conf/conf.json");
        try {
            IOUtils.toString(conf.toURI());
        } catch (final Exception e) {

        }
    }

}
