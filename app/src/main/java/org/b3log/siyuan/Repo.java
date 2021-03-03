/*
 * SiYuan - 源于思考，饮水思源
 * Copyright (c) 2020-present, ld246.com
 *
 * 本文件属于思源笔记源码的一部分，云南链滴科技有限公司版权所有。
 */
package org.b3log.siyuan;

import android.app.Activity;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.widget.Toast;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.PullCommand;
import org.eclipse.jgit.api.PullResult;
import org.eclipse.jgit.api.PushCommand;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.transport.JschConfigSessionFactory;
import org.eclipse.jgit.transport.OpenSshConfig;
import org.eclipse.jgit.transport.SshSessionFactory;
import org.eclipse.jgit.transport.SshTransport;
import org.eclipse.jgit.util.FS;

import java.io.File;

import androidk.Androidk;
import androidk.Syncer;

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
        try {
            final Syncer syncer = new JavaSyncer();
            Androidk.repoSync(syncer);

            final WebView webView = ((MainActivity) activity).webView;
            webView.post(webView::reload);
        } catch (final Exception e) {
            Toast.makeText(activity, e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private final class JavaSyncer implements Syncer {

        @Override
        public boolean pull(final String localPath, final String keyFilePath) throws Exception {
            return Repo.pull(localPath, keyFilePath);
        }

        @Override
        public void push(final String localPath, final String keyFilePath) throws Exception {
            Repo.push(localPath, keyFilePath);
        }
    }

    private static boolean pull(final String localPath, final String keyFile) throws Exception {
        final Git repo = Git.open(new File(localPath));

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
            final PullResult call = pullCommand.call();
            final String msg = call.toString();
            if (msg.contains("Already-up-to-date")) {
                return true;
            }
        } catch (final JGitInternalException /* RefNotAdvertisedException */ e) {
            // 忽略初次空仓库 pull
            e.printStackTrace();
        } finally {
            repo.close();
        }
        return false;
    }

    private static void push(final String localPath, final String keyFile) throws Exception {
        final Git repo = Git.open(new File(localPath));

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

        try {
            pushCommand.call();
        } finally {
            repo.close();
        }
    }
}