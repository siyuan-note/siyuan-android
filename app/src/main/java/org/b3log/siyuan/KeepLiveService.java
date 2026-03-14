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

import android.Manifest;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.IBinder;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

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
 * @version 1.0.2.5, Mar 14, 2026
 * @since 1.0.0
 */
public class KeepLiveService extends Service {

    @Override
    public IBinder onBind(Intent intent) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void onCreate() {
        try {
            super.onCreate();
            startMyOwnForeground();
        } catch (final Throwable e) {
            Utils.logError("keeplive", "start foreground service failed", e);
        }
    }

    private Random random = new Random();

    private void startMyOwnForeground() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        final String channel = "Keep Live Service";
        if (!NotificationReceiver.createNotificationChannel(this, channel)) {
            return;
        }

        final String[] texts = getNotificationTexts();
        if (null == texts || 1 > texts.length) {
            Utils.logError("keeplive", "notification texts is empty");
            return;
        }

        final NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, channel);
        final PendingIntent resultPendingIntent = NotificationReceiver.createNotificationPendingIntent(this);
        final Notification notification = notificationBuilder.setOngoing(true).
                setVisibility(NotificationCompat.VISIBILITY_PRIVATE).
                setPriority(NotificationCompat.PRIORITY_HIGH).
                setSmallIcon(R.drawable.icon).
                setContentTitle(texts[random.nextInt(texts.length)]).
                setCategory(Notification.CATEGORY_SERVICE).
                setContentIntent(resultPendingIntent).build();
        NotificationManagerCompat.from(this).notify(1, notification);
    }

    private String[] getNotificationTexts() {
        try {
            final String workspacePath = Mobile.getCurrentWorkspacePath();
            final String notificationTxtPath = workspacePath + "/data/assets/android-notification-texts.txt";
            final File notificationTxtFile = new File(notificationTxtPath);
            if (!notificationTxtFile.exists()) {
                return getLyrics();
            }

            final List<String> tmp = FileUtils.readLines(notificationTxtFile, StandardCharsets.UTF_8);
            final List<String> lines = new ArrayList<>();
            for (final String line : tmp) {
                if (line.trim().isEmpty()) {
                    continue;
                }
                lines.add(line);
            }
            if (lines.isEmpty()) {
                return getLyrics();
            }

            final String[] ret = new String[lines.size()];
            return lines.toArray(ret);
        } catch (final Exception e) {
            Utils.logError("keeplive", "get notification texts failed", e);
            return getLyrics();
        }
    }

    private String[] getLyrics() {
        final String lang = Utils.getLanguage();
        switch (lang) {
            case "zh_CN":
                return zhCNLyrics;
            case "zh_CHT":
                return zhCHTLyrics;
            default:
                return lyrics;
        }
    }

    private final String[] lyrics = new String[]{"We are programmed to receive", "Then the piper will lead us to reason", "You're not the only one", "Sometimes I need some time all alone", "We still can find a way", "You gotta make it your own way", "Everybody needs somebody", "Now, there is a fire within me",};

    private final String[] zhCNLyrics = new String[]{"原谅我这一生不羁放纵爱自由", "我要再次找那旧日的足迹", "心中一股冲劲勇闯，抛开那现实没有顾虑", "愿望是努力走向那一方", "其实怕被忘记至放大来演吧", "荣耀的背后刻着一道孤独", "动机也只有一种名字那叫做欲望",};

    private final String[] zhCHTLyrics = new String[]{"原諒我這一生不羈放縱愛自由", "我要再次找那舊日的足跡", "心中一股衝勁勇闖，拋開那現實沒有顧慮", "願望是努力走向那一方", "其實怕被忘記至放大來演吧", "榮耀的背後刻著一道孤獨", "動機也只有一種名字那叫做慾望",};
}

