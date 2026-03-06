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

import android.content.Context;

import com.koushikdutta.async.AsyncServer;
import com.koushikdutta.async.http.server.AsyncHttpServer;
import com.koushikdutta.async.util.Charsets;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.DirectoryFileFilter;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.lang.reflect.Field;
import java.net.InetAddress;
import java.net.ServerSocket;

/**
 * HTTP server manager for custom Android API endpoints.
 * HTTP 服务器管理器，用于自定义 Android API 端点
 *
 * @version 1.0.0.0, Mar 6, 2026
 */
public class HttpServerManager {

    private AsyncHttpServer server;
    private int serverPort;
    private final Context context;

    /**
     * Constructor.
     *
     * @param context Application context
     */
    public HttpServerManager(Context context) {
        this.context = context;
    }

    /**
     * Start the HTTP server.
     * 启动 HTTP 服务器
     *
     * @return The port number the server is listening on
     */
    public int startServer() {
        if (server != null) {
            stopServer();
        }

        // Fix charset encoding issue
        // 修复字符编码问题
        fixCharsetEncoding();

        server = new AsyncHttpServer();
        setupEndpoints();

        serverPort = getAvailablePort();
        final AsyncServer asyncServer = AsyncServer.getDefault();

        if (Utils.isDebugPackageAndMode(context)) {
            // Bind to all interfaces in debug mode for remote debugging
            // 调试模式下绑定所有网卡以便远程调试
            asyncServer.listen(null, serverPort, server.getListenCallback());
        } else {
            // Bind to loopback address in production to prevent remote access
            // 生产环境绑定回环地址以防止远程访问
            asyncServer.listen(InetAddress.getLoopbackAddress(), serverPort, server.getListenCallback());
        }

        Utils.logInfo("http", "HTTP server is listening on port [" + serverPort + "]");
        return serverPort;
    }

    /**
     * Setup API endpoints.
     * 设置 API 端点
     */
    private void setupEndpoints() {
        // Walk directory API endpoint
        server.post(AppConfig.API_WALK_DIR, (request, response) -> {
            try {
                final long start = System.currentTimeMillis();
                final JSONObject requestJSON = (JSONObject) request.getBody().get();
                final String dir = requestJSON.optString("dir");

                final JSONObject data = new JSONObject();
                final JSONArray files = new JSONArray();

                FileUtils.listFilesAndDirs(new File(dir), TrueFileFilter.INSTANCE, DirectoryFileFilter.DIRECTORY)
                        .forEach(file -> {
                            try {
                                final JSONObject info = new JSONObject();
                                info.put("path", file.getAbsolutePath());
                                info.put("name", file.getName());
                                info.put("size", file.length());
                                info.put("updated", file.lastModified());
                                info.put("isDir", file.isDirectory());
                                files.put(info);
                            } catch (final Exception e) {
                                Utils.logError("http", "Failed to process file: " + file.getAbsolutePath(), e);
                            }
                        });

                data.put("files", files);
                final JSONObject responseJSON = new JSONObject()
                        .put("code", 0)
                        .put("msg", "")
                        .put("data", data);

                response.send(responseJSON);
                Utils.logInfo("http", "Walk dir [" + dir + "] in [" + (System.currentTimeMillis() - start) + "] ms");
            } catch (final Exception e) {
                Utils.logError("http", "Walk dir failed", e);
                try {
                    response.send(new JSONObject()
                            .put("code", -1)
                            .put("msg", e.getMessage()));
                } catch (final Exception e2) {
                    Utils.logError("http", "Failed to send error response", e2);
                }
            }
        });
    }

    /**
     * Fix charset encoding issue in AndroidAsync library.
     * 修复 AndroidAsync 库的字符编码问题
     * <p>
     * Reference: https://github.com/koush/AndroidAsync/issues/656#issuecomment-523325452
     */
    private void fixCharsetEncoding() {
        try {
            final Class<Charsets> charsetClass = Charsets.class;
            Field usAscii = charsetClass.getDeclaredField("US_ASCII");
            usAscii.setAccessible(true);
            usAscii.set(Charsets.class, Charsets.UTF_8);
        } catch (final Exception e) {
            Utils.logError("http", "Failed to initialize charset", e);
        }
    }

    /**
     * Get an available port for the HTTP server.
     * 获取可用的端口号
     *
     * @return Available port number
     */
    private int getAvailablePort() {
        int port = AppConfig.DEFAULT_ASYNC_SERVER_PORT;
        try (ServerSocket s = new ServerSocket(0)) {
            port = s.getLocalPort();
        } catch (final Exception e) {
            Utils.logError("http", "Failed to get available port, using default: " + port, e);
        }
        return port;
    }

    /**
     * Stop the HTTP server.
     * 停止 HTTP 服务器
     */
    public void stopServer() {
        if (server != null) {
            try {
                server.stop();
                Utils.logInfo("http", "HTTP server stopped");
            } catch (final Exception e) {
                Utils.logError("http", "Failed to stop HTTP server", e);
            } finally {
                server = null;
            }
        }
    }

    /**
     * Get the server port.
     * 获取服务器端口
     *
     * @return Server port number
     */
    public int getServerPort() {
        return serverPort;
    }
}
