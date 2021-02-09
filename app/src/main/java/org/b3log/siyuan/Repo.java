package org.b3log.siyuan;

import android.app.Activity;
import android.os.Environment;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.widget.Toast;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.PullCommand;
import org.eclipse.jgit.api.PullResult;
import org.eclipse.jgit.api.PushCommand;
import org.eclipse.jgit.transport.JschConfigSessionFactory;
import org.eclipse.jgit.transport.OpenSshConfig;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.SshSessionFactory;
import org.eclipse.jgit.transport.SshTransport;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;

import androidk.Androidk;

public final class Repo {
    private Activity activity;

    public Repo(Activity activity) {
        this.activity = activity;
    }

    @JavascriptInterface
    public void sync() {
        Toast.makeText(activity, Androidk.language(22), Toast.LENGTH_SHORT).show();
        String keyFile = null;
        try {
            Androidk.prepareSync();

            final String siyuan = Environment.getExternalStorageDirectory() + "/siyuan";
            final String confStr = FileUtils.readFileToString(new File(siyuan + "/conf/conf.json"));
            final JSONObject conf = new JSONObject(confStr);
            final JSONArray boxes = conf.optJSONArray("boxes");
            keyFile = Androidk.genTempKeyFile();
            for (int i = 0; i < boxes.length(); i++) {
                final JSONObject box = boxes.getJSONObject(i);
                if (box.optBoolean("isRemote")) {
                    continue;
                }

                final String localPath = box.optString("path");
                final Git repo = Git.open(new File(localPath));

                pull(repo, keyFile);
                Androidk.reloadBox(localPath);
                push(repo, keyFile);
                repo.close();
                Log.i("", "synced box [" + box.optString("name") + "]");
            }

            Androidk.reloadRecentBlocks();
        } catch (final Exception e) {
            Toast.makeText(activity, e.getMessage(), Toast.LENGTH_SHORT).show();
        } finally {
            if (!StringUtils.isEmptyOrNull(keyFile)) {
                FileUtils.deleteQuietly(new File(keyFile));
            }
        }
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
        final PullResult pullResult = pullCommand.call();
        Log.i("", pullResult.toString());
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

        final Iterable<PushResult> pushResults = pushCommand.call();
        for (final PushResult result : pushResults) {
            Log.i("", result.toString());
        }
    }
}