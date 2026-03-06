/*
 * SiYuan - 源于思考,饮水思源
 * Copyright (c) 2020-present, b3log.org
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.b3log.siyuan;

import android.util.Log;

import com.koushikdutta.async.http.AsyncHttpClient;
import com.koushikdutta.async.http.AsyncHttpPost;
import com.koushikdutta.async.http.body.JSONObjectBody;

import org.json.JSONObject;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Data synchronization manager.
 * 数据同步管理器
 *
 * @version 1.0.0.0, Mar 6, 2026
 */
public class SyncManager {

    private final AtomicBoolean syncing = new AtomicBoolean(false);

    /**
     * Start data synchronization asynchronously.
     * 异步启动数据同步
     */
    public void startSync() {
        new Thread(this::performSync, "DataSyncThread").start();
    }

    /**
     * Perform data synchronization.
     * 执行数据同步
     * <p>
     * This method is thread-safe and will skip if sync is already in progress.
     * 此方法是线程安全的，如果同步正在进行中则跳过。
     */
    public void performSync() {
        if (!syncing.compareAndSet(false, true)) {
            Log.i("sync", "Data sync already in progress, skipping");
            return;
        }

        try {
            final AsyncHttpPost req = new AsyncHttpPost(AppConfig.API_SYNC_PERFORM);
            req.setBody(new JSONObjectBody(new JSONObject().put("mobileSwitch", true)));

            AsyncHttpClient.getDefaultInstance().executeJSONObject(req,
                    new AsyncHttpClient.JSONObjectCallback() {
                        @Override
                        public void onCompleted(Exception e, com.koushikdutta.async.http.AsyncHttpResponse source, JSONObject result) {
                            if (null != e) {
                                Utils.logError("sync", "Data sync failed", e);
                            } else {
                                Log.i("sync", "Data sync completed successfully");
                            }
                        }
                    });
        } catch (final Throwable e) {
            Utils.logError("sync", "Data sync failed with exception", e);
        } finally {
            syncing.set(false);
        }
    }

    /**
     * Check if synchronization is in progress.
     * 检查同步是否正在进行
     *
     * @return true if sync is in progress
     */
    public boolean isSyncing() {
        return syncing.get();
    }
}
