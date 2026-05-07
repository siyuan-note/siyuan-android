/*
 * SiYuan - 源于思考，饮水思源
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

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;

import androidx.core.app.NotificationCompat;

/**
 * 内核常驻服务.
 *
 * <p>
 * Keeps the Go kernel HTTP server alive when the app is in the background
 * or the screen is off by holding a partial WakeLock and a WifiLock,
 * running as a foreground service with a minimal silent notification.
 * </p>
 *
 * @author <a href="https://88250.b3log.org">Liang Ding</a>
 * @author <a href="https://github.com/fayaz-modz">Fayaz Mohammad</a>
 * @version 1.0.0.0, May 6, 2026
 * @since 3.1.0
 */
public class KernelService extends Service {

    private static final String CHANNEL_ID = "siyuan_kernel_channel";
    private static final int NOTIFICATION_ID = 19860;

    private PowerManager.WakeLock wakeLock;
    private WifiManager.WifiLock wifiLock;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, buildNotification());
        acquireLocks();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // If the system kills this service, restart it automatically
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        releaseLocks();
        super.onDestroy();
    }

    /**
     * Create a low-importance notification channel so the notification is
     * silent, has no badge, and is minimally visible.
     */
    private void createNotificationChannel() {
        final NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Kernel Service",
                NotificationManager.IMPORTANCE_MIN   // lowest: no sound, no peek, collapsed in shade
        );
        channel.setDescription("Keeps the SiYuan kernel running in the background");
        channel.setShowBadge(false);
        channel.setLockscreenVisibility(Notification.VISIBILITY_SECRET);
        channel.enableLights(false);
        channel.enableVibration(false);
        channel.setSound(null, null);

        final NotificationManager manager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.createNotificationChannel(channel);
        }
    }

    /**
     * Build a minimal, silent foreground notification.
     */
    private Notification buildNotification() {
        final Intent resultIntent = new Intent(this, MainActivity.class)
                .setAction(Intent.ACTION_MAIN)
                .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        final int pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT
                | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? PendingIntent.FLAG_MUTABLE : 0);
        final PendingIntent pendingIntent =
                PendingIntent.getActivity(this, 0, resultIntent, pendingFlags);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.icon)
                .setContentTitle("SiYuan")
                .setContentText("Kernel is running")
                .setOngoing(true)
                .setSilent(true)                                    // no sound
                .setPriority(NotificationCompat.PRIORITY_MIN)       // minimal
                .setVisibility(NotificationCompat.VISIBILITY_SECRET) // hidden from lock screen
                .setCategory(Notification.CATEGORY_SERVICE)
                .setContentIntent(pendingIntent)
                .build();
    }

    /**
     * Acquire a partial WakeLock (CPU stays on) and a WifiLock (Wi-Fi stays on)
     * so the Go kernel HTTP server remains reachable from other devices.
     */
    private void acquireLocks() {
        try {
            final PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                wakeLock = pm.newWakeLock(
                        PowerManager.PARTIAL_WAKE_LOCK,
                        "siyuan:KernelWakeLock"
                );
                wakeLock.acquire();
            }
        } catch (final Exception e) {
            Utils.logError("kernel-service", "acquire wake lock failed", e);
        }

        try {
            final WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wm != null) {
                wifiLock = wm.createWifiLock(
                        WifiManager.WIFI_MODE_FULL_HIGH_PERF,
                        "siyuan:KernelWifiLock"
                );
                wifiLock.acquire();
            }
        } catch (final Exception e) {
            Utils.logError("kernel-service", "acquire wifi lock failed", e);
        }
    }

    /**
     * Release all held locks.
     */
    private void releaseLocks() {
        try {
            if (wakeLock != null && wakeLock.isHeld()) {
                wakeLock.release();
                wakeLock = null;
            }
        } catch (final Exception e) {
            Utils.logError("kernel-service", "release wake lock failed", e);
        }

        try {
            if (wifiLock != null && wifiLock.isHeld()) {
                wifiLock.release();
                wifiLock = null;
            }
        } catch (final Exception e) {
            Utils.logError("kernel-service", "release wifi lock failed", e);
        }
    }
}
