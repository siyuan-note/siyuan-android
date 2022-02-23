package org.b3log.siyuan;

import android.app.Application;

import com.blankj.utilcode.util.Utils;


/**
 * SiYuan Application.
 *
 * @author <a href="http://88250.b3log.org">Liang Ding</a>
 * @version 1.0.0.0, Feb 23, 2022
 * @since 1.0.0
 */
public class App extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        Utils.init(this);
    }
}
