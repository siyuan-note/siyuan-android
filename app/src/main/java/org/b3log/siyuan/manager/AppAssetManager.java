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
package org.b3log.siyuan.manager;

import android.content.Context;
import android.util.Log;

import com.blankj.utilcode.util.StringUtils;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.b3log.siyuan.config.AppConfig;
import org.b3log.siyuan.util.Utils;

import java.io.File;
import java.nio.charset.StandardCharsets;

/**
 * Application asset manager for extracting and managing app.zip.
 * 应用资源管理器，用于解压和管理 app.zip
 *
 * @version 1.0.0.0, Mar 6, 2026
 */
public class AppAssetManager {

    /**
     * Progress callback interface.
     * 进度回调接口
     */
    public interface ProgressCallback {
        void onProgress(String message, int percent);
    }

    private final Context context;
    private final String appDir;
    private final File appDirFile;
    private final File appVersionFile;

    /**
     * Constructor.
     *
     * @param context Application context
     */
    public AppAssetManager(Context context) {
        this.context = context;
        this.appDir = context.getFilesDir().getAbsolutePath() + AppConfig.APP_DIR;
        this.appDirFile = new File(appDir);
        this.appVersionFile = new File(appDir, AppConfig.APP_VERSION_FILE);
    }

    /**
     * Initialize appearance assets if needed.
     * 如果需要则初始化外观资源
     *
     * @param callback Progress callback
     * @return true if initialization was successful
     */
    public boolean initializeIfNeeded(ProgressCallback callback) {
        if (!needsExtraction()) {
            Log.i("asset", "App assets are up to date, skipping extraction");
            return true;
        }

        return extractAssets(callback);
    }

    /**
     * Check if asset extraction is needed.
     * 检查是否需要解压资源
     *
     * @return true if extraction is needed
     */
    private boolean needsExtraction() {
        // Ensure directory exists
        appDirFile.mkdirs();

        // Always extract in debug mode
        if (Utils.isDebugPackageAndMode(context)) {
            Log.i("asset", "Debug mode: always extract assets");
            return true;
        }

        // Check if version file exists
        if (!appVersionFile.exists()) {
            Log.i("asset", "Version file not found, extraction needed");
            return true;
        }

        // Check version
        try {
            String storedVersion = FileUtils.readFileToString(appVersionFile, StandardCharsets.UTF_8);
            if (StringUtils.isEmpty(storedVersion)) {
                return true;
            }

            storedVersion = storedVersion.trim();
            try {
                int storedVersionCode = Integer.parseInt(storedVersion);
                boolean needsUpdate = storedVersionCode != Utils.versionCode;
                if (needsUpdate) {
                    Log.i("asset", "Version mismatch: stored=" + storedVersionCode +
                            ", current=" + Utils.versionCode);
                }
                return needsUpdate;
            } catch (final NumberFormatException e) {
                Log.w("asset", "Invalid version format: " + storedVersion);
                return true;
            }
        } catch (final Exception e) {
            Utils.logError("asset", "Failed to check version", e);
            return true;
        }
    }

    /**
     * Extract app assets.
     * 解压应用资源
     *
     * @param callback Progress callback
     * @return true if extraction was successful
     */
    private boolean extractAssets(ProgressCallback callback) {
        try {
            // Clear existing directory
            callback.onProgress("Clearing appearance...", 20);
            if (appDirFile.exists()) {
                FileUtils.deleteDirectory(appDirFile);
                Log.i("asset", "Deleted existing app directory");
            }

            // Extract assets
            callback.onProgress("Initializing appearance...", 60);
            final String appZipPath = context.getCacheDir() + "/app.zip";
            IOUtils.copy(context.getAssets().open(AppConfig.APP_ZIP),
                        FileUtils.openOutputStream(new File(appZipPath)));
            Utils.unzipAsset(appZipPath, appDir + AppConfig.APP_DIR);
            Log.i("asset", "Extracted app.zip to: " + appDir + AppConfig.APP_DIR);

            // Write version file
            FileUtils.writeStringToFile(appVersionFile, String.valueOf(Utils.versionCode),
                    StandardCharsets.UTF_8);
            Log.i("asset", "Wrote version file: " + Utils.versionCode);

            callback.onProgress("Booting kernel...", 80);
            return true;
        } catch (final Exception e) {
            Utils.logError("asset", "Failed to extract assets", e);
            return false;
        }
    }

    /**
     * Get the app directory path.
     * 获取应用目录路径
     *
     * @return App directory absolute path
     */
    public String getAppDir() {
        return appDir;
    }
}
