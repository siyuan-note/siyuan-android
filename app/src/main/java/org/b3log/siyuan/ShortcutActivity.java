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

import android.app.PendingIntent;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ContentInfoCompat;
import androidx.core.view.ViewCompat;

import com.blankj.utilcode.util.BarUtils;
import com.blankj.utilcode.util.KeyboardUtils;
import com.blankj.utilcode.util.StringUtils;
import com.zackratos.ultimatebarx.ultimatebarx.java.UltimateBarX;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.util.List;

import mobile.Mobile;

/**
 * 闪念速记.
 *
 * @author <a href="https://88250.b3log.org">Liang Ding</a>
 * @version 1.0.0.2, May 9, 2026
 * @since 3.7.0
 */
public class ShortcutActivity extends AppCompatActivity {

    private boolean inputSetupDone = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_shortcut);

        final EditText input = findViewById(R.id.full_screen_input);
        ViewCompat.setOnReceiveContentListener(input, new String[]{"text/html"}, (view, contentInfo) -> {
            final ClipData clip = contentInfo.getClip();
            if (null != clip && 0 < clip.getItemCount()) {
                final ClipData.Item item = clip.getItemAt(0);
                final String html = item.getHtmlText();
                if (null != html && !html.isEmpty()) {
                    final String md = Mobile.htmL2Markdown(html);
                    if (null != md && !md.isEmpty()) {
                        final ClipData newClip = ClipData.newPlainText("", md);
                        return new ContentInfoCompat.Builder(contentInfo)
                                .setClip(newClip)
                                .build();
                    }
                }
            }
            return null;
        });
        UltimateBarX.statusBarOnly(this).transparent().apply();
        BarUtils.setNavBarVisibility(this, false);
        ((ViewGroup) input.getParent()).setPadding(0, UltimateBarX.getStatusBarHeight(), 0, 0);
        BarUtils.setNavBarVisibility(this, false);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // Full screen display in landscape mode on Android https://github.com/siyuan-note/siyuan/issues/14448
            getWindow().getAttributes().layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }

        handleIntent(getIntent());
    }

    @Override
    public void onNewIntent(final Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(final Intent intent) {
        if (Intent.ACTION_SEND.equals(intent.getAction())) { // 来自其他应用分享
            final String type = intent.getType();
            if (type == null) {
                Log.w("shortcut", "Unknown type [null]");
                return;
            }

            if ("text/plain".equals(type)) {
                setupFullScreenInput();
                final String sharedText = intent.getStringExtra(Intent.EXTRA_TEXT);
                if (sharedText != null) {
                    final EditText input = findViewById(R.id.full_screen_input);
                    input.append(sharedText);
                    input.setSelection(sharedText.length());
                }
                return;
            } else if (type.startsWith("image/") || type.startsWith("video/") || type.startsWith("audio") || type.startsWith("application/")) {
                setupFullScreenInput();
                final Uri assetUri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
                if (assetUri != null) {
                    final List<Uri> assets = List.of(assetUri);
                    writeAssets(assets, type);
                }
                return;
            }

            setupFullScreenInput();
            Log.w("shortcut", "Unknown share type [" + type + "]");
            return;
        } else if (Intent.ACTION_SEND_MULTIPLE.equals(intent.getAction())) {
            final String type = intent.getType();
            if (type == null) {
                Log.w("shortcut", "Unknown type [null]");
                return;
            }

            if (type.startsWith("image/") || type.startsWith("video/") || type.startsWith("audio") || type.startsWith("application/")) {
                setupFullScreenInput();
                final List<Uri> imageUris = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
                if (null != imageUris) {
                    writeAssets(imageUris, type);
                }
                return;
            }
            return;
        }

        // 来自桌面快捷方式或菜单快捷方式 — 显示文本输入界面
        setupFullScreenInput();
        if (Intent.ACTION_VIEW.equals(intent.getAction())) {
            final String data = intent.getDataString();
            if (!StringUtils.equals(data, "shorthand")) {
                Log.w("shortcut", "Unknown data [" + data + "]");
            }
        }

        final EditText input = findViewById(R.id.full_screen_input);
        input.postDelayed(() -> {
            input.requestFocus();
            KeyboardUtils.showSoftInput(input);
        }, 500);
    }

    private void writeAssets(final List<Uri> assetUris, final String type) {
        if (null == assetUris || assetUris.isEmpty()) {
            return;
        }

        final EditText input = findViewById(R.id.full_screen_input);
        input.requestFocus();
        final String shorthandsDir = getShorthandsDir();
        for (final Uri uri : assetUris) {
            final String p = uri.getLastPathSegment();
            if (null == p) {
                continue;
            }
            String baseName = Mobile.filepathBase(p);
            baseName = Mobile.filterUploadFileName(baseName);
            final String fileName = Mobile.assetName(baseName);
            final File f = new File(shorthandsDir + "assets", fileName);
            try {
                FileUtils.copyInputStreamToFile(getContentResolver().openInputStream(uri), f);
                if (type.startsWith("image/")) {
                    input.append("![" + baseName + "](assets/" + fileName + ")");
                } else {
                    input.append("[" + baseName + "](assets/" + fileName + ")");
                }
                input.append("\n\n");
                input.setSelection(input.getText().length());
            } catch (final Exception e) {
                Utils.logError("shortcut", "copy file failed", e);
                Utils.showToast(this, "Failed to copy file [" + e.getMessage() + "]");
            }
        }
    }

    private void setupFullScreenInput() {
        if (inputSetupDone) {
            return;
        }
        inputSetupDone = true;

        initAddToHomeButton();

        final EditText input = findViewById(R.id.full_screen_input);
        final Button submitButton = findViewById(R.id.submit_button);
        submitButton.setEnabled(false);
        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                submitButton.setEnabled(!StringUtils.isEmpty(s.toString().trim()));
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        submitButton.setOnClickListener(v -> {
            final String userInput = input.getText().toString().trim();
            if (StringUtils.isEmpty(userInput)) {
                return;
            }

            final long now = System.currentTimeMillis();
            final String shorthandsDir = getShorthandsDir();
            final File f = new File(shorthandsDir, now + ".md");
            try {
                FileUtils.writeStringToFile(f, userInput, "UTF-8");
            } catch (final Exception e) {
                Utils.logError("shortcut", "Write file failed", e);
                Utils.showToast(this, "Failed to write to file [" + e.getMessage() + "]");
                return;
            }

            finish();
        });
    }

    private void addShortcutToHome() {
        final ShortcutManager shortcutManager = getSystemService(ShortcutManager.class);
        if (!shortcutManager.isRequestPinShortcutSupported()) {
            Utils.showToast(this, R.string.add_to_home_failed);
            Utils.logError("shortcut", "Request pin shortcut not supported");
            return;
        }

        final Intent shortcutIntent = new Intent(getApplicationContext(), ShortcutActivity.class);
        shortcutIntent.setAction(Intent.ACTION_MAIN);
        final ShortcutInfo shortcutInfo = new ShortcutInfo.Builder(this, "shortcut_shorthand")
                .setShortLabel(getString(R.string.shortcut_shorthand))
                .setLongLabel(getString(R.string.shortcut_shorthand))
                .setIcon(Icon.createWithResource(this, R.drawable.shorthand_icon))
                .setIntent(shortcutIntent)
                .build();
        final Intent pinnedShortcutCallbackIntent = shortcutManager.createShortcutResultIntent(shortcutInfo);
        final PendingIntent successCallback = PendingIntent.getBroadcast(this, 0,
                pinnedShortcutCallbackIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        shortcutManager.requestPinShortcut(shortcutInfo, successCallback.getIntentSender());
        Utils.showToast(this, R.string.adding_to_home);

        new Thread(() -> {
            try {
                Thread.sleep(1000);
            } catch (final Exception e) {
                Log.e("shortcut", "Failed to sleep", e);
            }

            if (isShortcutExists("shortcut_shorthand")) {
                runOnUiThread(() -> {
                    findViewById(R.id.add_to_home_button).setVisibility(View.GONE);
                    Utils.showToast(this, R.string.add_to_home_success);
                });

                return;
            }

            runOnUiThread(() -> {
                Utils.showToast(this, R.string.add_to_home_failed);
            });
        }).start();
    }

    private void initAddToHomeButton() {
        final Button addToHomeButton = findViewById(R.id.add_to_home_button);
        if (isShortcutExists("shortcut_shorthand")) {
            addToHomeButton.setVisibility(View.GONE);
        } else {
            addToHomeButton.setOnClickListener(v -> addShortcutToHome());
        }
    }

    private boolean isShortcutExists(final String shortcutId) {
        final ShortcutManager shortcutManager = getSystemService(ShortcutManager.class);
        for (ShortcutInfo shortcut : shortcutManager.getPinnedShortcuts()) {
            if (shortcutId.equals(shortcut.getId())) {
                return true;
            }
        }
        return false;
    }

    private String getShorthandsDir() {
        final String ret = getExternalFilesDir(null).getAbsolutePath() + "/home/.config/siyuan/shortcuts/shorthands/";
        new File(ret).mkdirs();
        return ret;
    }
}
