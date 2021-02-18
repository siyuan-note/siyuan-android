/*
 * SiYuan - 源于思考，饮水思源
 * Copyright (c) 2020-present, ld246.com
 *
 * 本文件属于思源笔记源码的一部分，云南链滴科技有限公司版权所有。
 */
package org.b3log.siyuan;

import android.app.Activity;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.widget.Toast;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.PullCommand;
import org.eclipse.jgit.api.PushCommand;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.transport.JschConfigSessionFactory;
import org.eclipse.jgit.transport.OpenSshConfig;
import org.eclipse.jgit.transport.SshSessionFactory;
import org.eclipse.jgit.transport.SshTransport;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;

import androidk.Androidk;

/**
 * 仓库同步.
 *
 * @author <a href="http://88250.b3log.org">Liang Ding</a>
 * @version 1.0.0.0, Feb 19, 2020
 * @since 1.0.0
 */
public final class Repo {
    private Activity activity;

    public Repo(final Activity activity) {
        this.activity = activity;
    }

    @JavascriptInterface
    public void sync() {
        String keyFile = null;
        try {
            Androidk.prepareSync();

            final String siyuan = Utils.getSiYuanDir(activity);
            final String confStr = FileUtils.readFileToString(new File(siyuan + "/conf/conf.json"));
            final JSONObject conf = new JSONObject(confStr);
            final JSONArray boxes = conf.optJSONArray("boxes");

            final File dataDirectory = activity.getCacheDir();
            keyFile = Androidk.genTempKeyFile(dataDirectory.getAbsolutePath());
            for (int i = 0; i < boxes.length(); i++) {
                final JSONObject box = boxes.getJSONObject(i);
                if (box.optBoolean("isRemote")) {
                    continue;
                }

                final String localPath = box.optString("path");
                final Git repo = Git.open(new File(localPath));
                commit(repo);
                pull(repo, keyFile);
                Androidk.reloadBox(localPath);
                push(repo, keyFile);
                repo.close();
                Log.i("", "synced box [" + box.optString("name") + "]");
            }

            Androidk.reloadRecentBlocks();

            final WebView webView = ((MainActivity) activity).webView;
            webView.post(() -> webView.reload());
        } catch (final Exception e) {
            Toast.makeText(activity, e.getMessage(), Toast.LENGTH_LONG).show();
        } finally {
            if (!StringUtils.isEmptyOrNull(keyFile)) {
                FileUtils.deleteQuietly(new File(keyFile));
            }
        }
    }

    private static void commit(final Git repo) throws Exception {
        repo.add().addFilepattern(".").call();
        repo.commit().setAll(true).setMessage("android ").call();
    }

    private static void pull(final Git repo, final String keyFile) throws Exception {
        final SshSessionFactory jschConfigSessionFactory = new JschConfigSessionFactory() {
            @Override
            protected void configure(final OpenSshConfig.Host host, final Session session) {
                session.setConfig("StrictHostKeyChecking", "no");
            }

            @Override
            protected JSch createDefaultJSch(final FS fs) throws JSchException {
                final JSch defaultJSch = super.createDefaultJSch(fs);
                defaultJSch.addIdentity(keyFile);
                return defaultJSch;
            }
        };

        final PullCommand pullCommand = repo.pull();
        pullCommand.setTransportConfigCallback(transport -> {
            final SshTransport sshTransport = (SshTransport) transport;
            sshTransport.setSshSessionFactory(jschConfigSessionFactory);
        });

        try {
            pullCommand.call();
        } catch (final JGitInternalException /* RefNotAdvertisedException */ e) {
            // 忽略初次空仓库 pull
            e.printStackTrace();
        }
    }

    private static void push(final Git repo, final String keyFile) throws Exception {
        final SshSessionFactory jschConfigSessionFactory = new JschConfigSessionFactory() {
            @Override
            protected void configure(final OpenSshConfig.Host host, final Session session) {
                session.setConfig("StrictHostKeyChecking", "no");
            }

            @Override
            protected JSch createDefaultJSch(final FS fs) throws JSchException {
                final JSch defaultJSch = super.createDefaultJSch(fs);
                defaultJSch.addIdentity(keyFile);
                return defaultJSch;
            }
        };

        final PushCommand pushCommand = repo.push();
        pushCommand.setTransportConfigCallback(transport -> {
            final SshTransport sshTransport = (SshTransport) transport;
            sshTransport.setSshSessionFactory(jschConfigSessionFactory);
        });

        pushCommand.call();
    }
}