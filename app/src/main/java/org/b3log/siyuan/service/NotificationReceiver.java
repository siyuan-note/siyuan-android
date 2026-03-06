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
package org.b3log.siyuan.service;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import org.b3log.siyuan.R;
import org.b3log.siyuan.activity.MainActivity;
import org.b3log.siyuan.util.Utils;

/**
 * 通知接收器.
 *
 * @author <a href="https://88250.b3log.org">Liang Ding</a>
 * @version 1.0.0.1, Mar 5, 2026
 * @since 3.5.9
 */
public class NotificationReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(final Context context, final Intent intent) {
        final String channel = intent.getStringExtra("channel");
        final String title = intent.getStringExtra("title");
        final String body = intent.getStringExtra("body");
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            Utils.logError("notification", "Notification permission not granted [title=" + title + ", body=" + body + "]");
            return;
        }

        final int id = intent.getIntExtra("id", (int) System.currentTimeMillis());
        final PendingIntent resultPendingIntent = NotificationReceiver.createNotificationPendingIntent(context);
        final NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channel)
                .setSmallIcon(R.drawable.icon)
                .setContentTitle(title)
                .setContentText(body)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(resultPendingIntent);
        NotificationManagerCompat.from(context).notify(id, builder.build());
    }

    public static boolean createNotificationChannel(final Context context, final String channel) {
        final NotificationChannel chan = new NotificationChannel(channel, channel, NotificationManager.IMPORTANCE_HIGH);
        chan.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
        final NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (null == manager) {
            Utils.logError("Notification", "get notification manager failed");
            return false;
        }
        manager.createNotificationChannel(chan);
        return true;
    }

    public static PendingIntent createNotificationPendingIntent(final Context context) {
        final Intent resultIntent = new Intent(context, MainActivity.class).
                setAction(Intent.ACTION_MAIN).setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent resultPendingIntent;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { // Android 端部分系统闪退 https://github.com/siyuan-note/siyuan/issues/7188
            resultPendingIntent = PendingIntent.getActivity(context, 0, resultIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
        } else {
            resultPendingIntent = PendingIntent.getActivity(context, 0, resultIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        }
        return resultPendingIntent;
    }
}
