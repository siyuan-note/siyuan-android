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
import android.os.Build;
import android.util.Log;

import org.b3log.siyuan.util.Utils;

import java.util.TimeZone;

import mobile.Mobile;

/**
 * Kernel lifecycle manager.
 * 内核生命周期管理器
 *
 * @version 1.0.0.0, Mar 6, 2026
 */
public class KernelManager {

    private final Context context;
    private final String appDir;
    private final String workspaceBaseDir;
    private final String webViewVer;
    private final String userAgent;
    private final int serverPort;

    /**
     * Constructor.
     *
     * @param context          Application context
     * @param appDir           Application directory path
     * @param workspaceBaseDir Workspace base directory path
     * @param webViewVer       WebView version string
     * @param userAgent        User agent string
     * @param serverPort       HTTP server port
     */
    public KernelManager(Context context, String appDir, String workspaceBaseDir,
                         String webViewVer, String userAgent, int serverPort) {
        this.context = context;
        this.appDir = appDir;
        this.workspaceBaseDir = workspaceBaseDir;
        this.webViewVer = webViewVer;
        this.userAgent = userAgent;
        this.serverPort = serverPort;
    }

    /**
     * Start the SiYuan kernel.
     * 启动思源内核
     */
    public void startKernel() {
        Mobile.setHttpServerPort(serverPort);

        if (Mobile.isHttpServing()) {
            Log.i("kernel", "Kernel HTTP server is already running");
            return;
        }

        try {
            new Thread(() -> {
                try {
                    // Disable AI feature for Chinese mainland channels
                    // 为中国大陆渠道禁用 AI 功能
                    if (Utils.isCnChannel(context.getPackageManager())) {
                        Mobile.disableFeature("ai");
                    }

                    final String timezone = TimeZone.getDefault().getID();
                    final String localIPs = Utils.getIPAddressList();
                    final String langCode = Utils.getLanguage();

                    final String containerInfo = Build.VERSION.RELEASE +
                            "/SDK " + Build.VERSION.SDK_INT +
                            "/WebView " + webViewVer +
                            "/Manufacturer " + android.os.Build.MANUFACTURER +
                            "/Brand " + android.os.Build.BRAND +
                            "/UA " + userAgent;

                    Mobile.startKernel("android", appDir, workspaceBaseDir,
                            timezone, localIPs, langCode, containerInfo);

                    Log.i("kernel", "Kernel started successfully");
                } catch (final Exception e) {
                    Utils.logError("kernel", "Failed to start kernel", e);
                }
            }, "KernelBootThread").start();
        } catch (final Exception e) {
            Utils.logError("kernel", "Failed to create kernel boot thread", e);
        }
    }

    /**
     * Wait for kernel HTTP server to be ready.
     * 等待内核 HTTP 服务就绪
     */
    public void waitForKernelReady() {
        while (true) {
            try {
                Thread.sleep(10);
            } catch (final InterruptedException e) {
                Utils.logError("kernel", "Kernel ready check interrupted", e);
                Thread.currentThread().interrupt();
                break;
            }

            if (Mobile.isHttpServing()) {
                Log.i("kernel", "Kernel HTTP server is ready");
                break;
            }
        }
    }

    /**
     * Check if kernel HTTP server is running.
     * 检查内核 HTTP 服务是否正在运行
     *
     * @return true if kernel is serving HTTP requests
     */
    public boolean isKernelServing() {
        return Mobile.isHttpServing();
    }

    /**
     * Shutdown the kernel.
     * 关闭内核
     */
    public void shutdown() {
        try {
            Mobile.exit();
            Log.i("kernel", "Kernel shutdown successfully");
        } catch (final Exception e) {
            Utils.logError("kernel", "Failed to shutdown kernel", e);
        }
    }
}
