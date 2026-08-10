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

import android.os.SystemClock;
import android.util.Log;

import java.util.HashSet;
import java.util.Set;

/**
 * 记录调试构建的启动阶段耗时。
 */
public final class StartupTiming {
    private static final Set<String> MARKED_STAGES = new HashSet<>();
    private static long startedAt;
    private static long previousAt;

    private StartupTiming() {
    }

    public static synchronized void start(final String stage) {
        if (!BuildConfig.DEBUG) {
            return;
        }

        final long now = SystemClock.elapsedRealtime();
        startedAt = now;
        previousAt = now;
        MARKED_STAGES.clear();
        MARKED_STAGES.add(stage);
        log(stage, now, now);
    }

    public static synchronized void mark(final String stage) {
        if (!BuildConfig.DEBUG || 0 == startedAt || !MARKED_STAGES.add(stage)) {
            return;
        }

        final long now = SystemClock.elapsedRealtime();
        log(stage, now, previousAt);
        previousAt = now;
    }

    private static void log(final String stage, final long now, final long previous) {
        Log.d("startup", "startup timing [stage=" + stage + ", elapsed=" + (now - startedAt)
                + "ms, delta=" + (now - previous) + "ms]");
    }
}
