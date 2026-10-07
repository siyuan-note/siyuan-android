/*
 * SiYuan - From thought to insight, with agents
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

import android.Manifest;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import mobile.Mobile;

/**
 * 保活服务.
 *
 * @author <a href="https://88250.b3log.org">Liang Ding</a>
 * @version 1.1.0.1, Jul 3, 2026
 * @since 1.0.0
 */
public class KeepLiveService extends Service {

    private final Handler refreshHandler = new Handler(Looper.getMainLooper());
    private final Runnable refreshNotification = new Runnable() {
        @Override
        public void run() {
            try {
                if (!isKeepLiveEnabled() || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ActivityCompat.checkSelfPermission(KeepLiveService.this, Manifest.permission.POST_NOTIFICATIONS)
                                != PackageManager.PERMISSION_GRANTED)) {
                    stopSelf();
                    return;
                }
                if (!startMyOwnForeground()) {
                    stopSelf();
                    return;
                }
                // 保持前台服务运行，仅更新通知，避免在后台反复启动服务。
                refreshHandler.postDelayed(this, 45 * 1000);
            } catch (final Exception e) {
                Utils.logError("keeplive", "update foreground notification failed", e);
                stopSelf();
            }
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        refreshNotification.run();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        refreshHandler.removeCallbacks(refreshNotification);
        stopForeground(STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }

    private Random random = new Random();

    private boolean startMyOwnForeground() {
        final String channel = "Keep Live Service";
        if (!NotificationReceiver.createNotificationChannel(this, channel)) {
            return false;
        }

        final String[] texts = getNotificationTexts();

        final NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, channel);
        final PendingIntent resultPendingIntent = NotificationReceiver.createNotificationPendingIntent(this);
        final Notification notification = notificationBuilder.setOngoing(true).
                setVisibility(NotificationCompat.VISIBILITY_PRIVATE).
                setPriority(NotificationCompat.PRIORITY_HIGH).
                setSmallIcon(R.drawable.icon).
                setContentTitle(texts[random.nextInt(texts.length)]).
                setCategory(Notification.CATEGORY_SERVICE).
                setContentIntent(resultPendingIntent).build();
        // 从 Android 14 (API 34) 起必须显式传入前台服务类型，否则 startForeground
        // 会以未定义类型校验权限并抛出 SecurityException。
        final int foregroundServiceType;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            foregroundServiceType = ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE;
        } else {
            foregroundServiceType = 0;
        }
        ServiceCompat.startForeground(this, 1, notification, foregroundServiceType);
        return true;
    }

    private String[] getNotificationTexts() {
        try {
            final String notificationTxtPath = getNotificationTxtPath();
            final File notificationTxtFile = new File(notificationTxtPath);
            final List<String> tmp = FileUtils.readLines(notificationTxtFile, StandardCharsets.UTF_8);
            final List<String> lines = new ArrayList<>();
            for (final String line : tmp) {
                if (line.trim().isEmpty()) {
                    continue;
                }
                lines.add(line);
            }
            if (!lines.isEmpty()) {
                return lines.toArray(new String[0]);
            }
        } catch (final Exception e) {
            Utils.logError("keeplive", "get notification texts failed", e);
        }
        // 空文件也可作为保活开关，缺少可用文本时显示应用名称。
        return new String[]{getString(R.string.app_name)};
    }

    public static boolean isKeepLiveEnabled() {
        final String notificationTxtPath = getNotificationTxtPath();
        final File notificationTxtFile = new File(notificationTxtPath);
        return notificationTxtFile.isFile();
    }

    private static String getNotificationTxtPath() {
        final String workspacePath = Mobile.getCurrentWorkspacePath();
        return workspacePath + "/data/assets/android-notification-texts.txt";
    }
}

