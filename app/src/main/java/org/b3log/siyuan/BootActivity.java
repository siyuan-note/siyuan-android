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

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.blankj.utilcode.util.BarUtils;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

/**
 * 引导启动.
 *
 * @author <a href="https://88250.b3log.org">Liang Ding</a>
 * @version 1.1.1.1, Sep 4, 2025
 * @since 1.0.0
 */
public class BootActivity extends AppCompatActivity {

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        Log.i("boot", "Create boot activity");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_boot);

        BarUtils.setNavBarVisibility(this, false);

        final String channel = Utils.getChannel(this.getPackageManager());
        final Set<String> showAgreementChannels = new HashSet<>();
        showAgreementChannels.add("cn");
        showAgreementChannels.add("huawei");
        final boolean needShowAgreement = showAgreementChannels.contains(channel);
        if (needShowAgreement && isFirstRun()) {
            showAgreements();
            return;
        }

        startMainActivity();
    }

    private boolean isFirstRun() {
        final String dataDir = getFilesDir().getAbsolutePath();
        final String appDir = dataDir + "/app";
        final File appDirFile = new File(appDir);
        return !appDirFile.exists();
    }

    private void startMainActivity() {
        final Intent intent = getIntent();
        // 获取可能存在的 block URL（通过 siyuan://blocks/xxx 打开应用时传递的）
        String blockURL = "";
        try {
            final Uri blockURLUri = intent.getData();
            if (null != blockURLUri && blockURLUri.toString().toLowerCase().startsWith("siyuan://")) {
                Log.i("boot", "Block URL [" + blockURLUri + "]");
                blockURL = blockURLUri.toString();
            }
        } catch (final Exception e) {
            Utils.logError("boot", "gets block URL failed", e);
        }

        // 获取可能存在的分享数据
        final String action = intent.getAction();
        final String type = intent.getType();
        if (Intent.ACTION_SEND.equals(action) && type != null) {
            if ("text/plain".equals(type)) {
                String text = intent.getStringExtra(Intent.EXTRA_TEXT);
                if (text != null) {
                    Log.i("boot", "Received shared text [" + text + "]");

                    if (Utils.isURL(text)) {
                        text = "[" + text + "](" + text + ")";
                    } else {
                        if (text.startsWith("\"") && text.contains("\"\n http") && text.contains("#:~:text=")) {
                            final int start = text.indexOf("\"");
                            final int end = text.indexOf("\"\n http");
                            text = text.substring(start + 1, end);
                        }
                    }

                    final ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    clipboard.setPrimaryClip(ClipData.newHtmlText("Copied text from shared", text, text));
                }
            }
        }

        final Intent startMainIntent = new Intent(getApplicationContext(), MainActivity.class);
        startMainIntent.putExtra("blockURL", blockURL);
        startActivity(startMainIntent);
    }

    private AlertDialog agreementDialog;
    private final Handler handler = new Handler(Looper.getMainLooper()) {

        @Override
        public void handleMessage(final Message msg) {
            final String cmd = msg.getData().getString("cmd");
            if ("agreement-y".equals(cmd)) {
                agreementDialog.dismiss();
                final ProgressBar progressBar = findViewById(R.id.progressBar);
                runOnUiThread(() -> progressBar.setVisibility(View.VISIBLE));
                startMainActivity();
            } else if ("agreement-n".equals(cmd)) {
                final String dataDir = getFilesDir().getAbsolutePath();
                final String appDir = dataDir + "/app";
                final File appDirFile = new File(appDir);
                try {
                    FileUtils.deleteQuietly(appDirFile);
                } catch (final Exception e) {
                    Utils.logError("boot", "delete [" + appDirFile.getAbsolutePath() + "] failed", e);
                }

                finishAffinity();
                finishAndRemoveTask();
                Log.i("boot", "User did not accept the agreement, exit");
            } else {
                Utils.logError("boot", "unknown agreement command [" + cmd + "]");
            }
        }
    };

    private void showAgreements() {
        final TextView msg = new TextView(this);
        msg.setPadding(32, 32, 32, 32);
        msg.setMovementMethod(new ScrollingMovementMethod());
        msg.setText(Html.fromHtml(agreement, Html.FROM_HTML_MODE_LEGACY));
        msg.setMovementMethod(LinkMovementMethod.getInstance());
        final AlertDialog.Builder ab = new AlertDialog.Builder(this);
        ab.setTitle("用户协议和隐私政策说明");
        ab.setView(msg);
        ab.setCancelable(false);
        ab.setPositiveButton("同意", (dialog, which) -> {
            final Bundle b = new Bundle();
            b.putString("cmd", "agreement-y");
            final Message m = new Message();
            m.setData(b);
            handler.sendMessage(m);
        });
        ab.setNegativeButton("拒绝", (dialog, which) -> {
            final Bundle b = new Bundle();
            b.putString("cmd", "agreement-n");
            final Message m = new Message();
            m.setData(b);
            handler.sendMessage(m);
        });

        agreementDialog = ab.show();
    }

    private final String agreement = "请您充分阅读并理解<a href=\"https://b3log.org/siyuan/eula.html\" target=\"_blank=\">《用户协议》</a>和" +
            "<a href=\"https://b3log.org/siyuan/privacy.html\" target=\"_blank\">《隐私政策》</a>。";
}