/*
 * Copyright (C) 2026 The GrapheneGallery Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.gallery3d.help;

import android.app.ActionBar;
import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.MenuItem;
import android.widget.ScrollView;
import android.widget.TextView;

import com.android.gallery3d.R;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Shows the plain-language help. The text is HELP.md from the repository root,
 * copied into the APK's assets at build time (see app/build.gradle.kts), so the
 * in-app help and the GitHub page are the same file. Rendered offline into a
 * plain TextView: no WebView, no links, no network.
 *
 * Not exported; reached only from Settings. Nothing in here may throw: a
 * missing or unreadable asset shows a short fallback message instead.
 */
public class HelpActivity extends Activity {

    private static final String TAG = "HelpActivity";

    /** Name of the generated asset. Must match the copy task in app/build.gradle.kts. */
    public static final String ASSET_NAME = "HELP.md";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(R.string.help_title);
        try {
            ActionBar bar = getActionBar();
            if (bar != null) {
                bar.setDisplayOptions(ActionBar.DISPLAY_SHOW_TITLE
                        | ActionBar.DISPLAY_SHOW_HOME | ActionBar.DISPLAY_HOME_AS_UP);
                bar.setTitle(R.string.help_title);
            }
        } catch (RuntimeException e) {
            Log.w(TAG, "action bar setup failed", e);
        }

        TextView text = new TextView(this);
        int padding = dp(16);
        text.setPadding(padding, padding, padding, dp(32));
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        text.setLineSpacing(dp(2), 1.0f);
        // Lets people copy the issues address; nothing is clickable.
        text.setTextIsSelectable(true);
        text.setText(content());

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(text);
        setContentView(scroll);
    }

    private CharSequence content() {
        String markdown = loadAsset();
        if (markdown == null || markdown.trim().isEmpty()) {
            return getString(R.string.help_unavailable);
        }
        try {
            return HelpMarkdown.render(markdown, dp(8));
        } catch (RuntimeException e) {
            Log.w(TAG, "could not render help; showing it unformatted", e);
            return markdown;
        }
    }

    /** The help text, or null when the asset is missing or unreadable. */
    private String loadAsset() {
        try (InputStream in = getAssets().open(ASSET_NAME)) {
            return readUtf8(in);
        } catch (IOException | RuntimeException e) {
            Log.w(TAG, "help asset unavailable", e);
            return null;
        }
    }

    static String readUtf8(InputStream in) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) != -1) {
            bytes.write(buffer, 0, read);
        }
        return new String(bytes.toByteArray(), StandardCharsets.UTF_8);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
