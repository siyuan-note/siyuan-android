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

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

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
 * @version 1.0.0.0, Mar 21, 2025
 * @since 3.1.26
 */
public class ShortcutActivity extends AppCompatActivity {

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
        final EditText input = findViewById(R.id.full_screen_input);
        this.runOnUiThread(() -> {
            input.requestFocus();
            KeyboardUtils.showSoftInput(this);
        });

        final Button submitButton = findViewById(R.id.submit_button);
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
                Toast.makeText(this, "Failed to write to file [" + e.getMessage() + "]", Toast.LENGTH_LONG).show();
            }

            finish();
        });
    }
}
