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

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.drawable.Icon;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.blankj.utilcode.util.KeyboardUtils;
import com.blankj.utilcode.util.StringUtils;

import org.apache.commons.io.FileUtils;

import java.io.File;

/**
 * 追加到日记的快捷方式.
 *
 * @author <a href="https://88250.b3log.org">Liang Ding</a>
 * @version 1.0.0.0, Mar 27, 2025
 * @since 3.1.26
 */
public class ShortcutActivity extends AppCompatActivity {

    @SuppressLint("WrongThread")
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_shortcut);

        handleIntent(getIntent());
    }

    @Override
    public void onNewIntent(final Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(final Intent intent) {
        final String data = intent.getDataString();
        if (StringUtils.equals(data, "shorthand")) {
            setupFullScreenInput();
            return;
        }

        Log.i("shortcut", "Unknown data [" + data + "]");
    }

    private void setupFullScreenInput() {
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

        input.postDelayed(() -> {
            input.requestFocus();
            KeyboardUtils.showSoftInput(input);
        }, 200);

        submitButton.setOnClickListener(v -> {
            final String userInput = input.getText().toString().trim();
            if (StringUtils.isEmpty(userInput)) {
                return;
            }

            final long now = System.currentTimeMillis();
            final String shorthandsDir = getExternalFilesDir(null).getAbsolutePath() + "/home/.config/siyuan/shortcuts/shorthands/";
            new File(shorthandsDir).mkdirs();
            final File f = new File(shorthandsDir, now + ".md");
            try {
                FileUtils.writeStringToFile(f, userInput, "UTF-8");
            } catch (final Exception e) {
                Log.e("shortcut", "Failed to write to file", e);
                Utils.showToast(this, "Failed to write to file [" + e.getMessage() + "]");
            }

            finish();
        });
    }

    private void addShortcutToHome() {
        final ShortcutManager shortcutManager = getSystemService(ShortcutManager.class);
        if (!shortcutManager.isRequestPinShortcutSupported()) {
            Utils.showToast(this, R.string.add_to_home_failed);
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
}
