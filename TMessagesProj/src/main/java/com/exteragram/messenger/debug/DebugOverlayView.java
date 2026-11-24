/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.debug;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatTextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

public class DebugOverlayView extends AppCompatTextView {

    public interface DataSource {
        void build(@NonNull ContentBuilder builder);
    }

    public static class ContentBuilder {
        private final StringBuilder stringBuilder = new StringBuilder(512);

        public ContentBuilder reset() {
            stringBuilder.setLength(0);
            return this;
        }

        public ContentBuilder title(CharSequence title) {
            return line(title);
        }

        public ContentBuilder section(CharSequence title) {
            if (stringBuilder.length() > 0) {
                stringBuilder.append('\n');
            }
            stringBuilder.append('[').append(title).append(']');
            return this;
        }

        public ContentBuilder kv(String key, Object value) {
            return line(key + "=" + value);
        }

        public ContentBuilder line(CharSequence line) {
            if (stringBuilder.length() > 0) {
                stringBuilder.append('\n');
            }
            stringBuilder.append(line);
            return this;
        }

        @NonNull
        public CharSequence build() {
            return stringBuilder;
        }
    }

    private static final long DEFAULT_UPDATE_INTERVAL_MS = 250L;

    private final ContentBuilder contentBuilder = new ContentBuilder();
    private final Runnable updater = new Runnable() {
        @Override
        public void run() {
            if (!isAttachedToWindow()) {
                return;
            }
            refresh();
            postDelayed(this, updateIntervalMs);
        }
    };

    @Nullable
    private DataSource dataSource;
    private long updateIntervalMs = DEFAULT_UPDATE_INTERVAL_MS;

    public DebugOverlayView(Context context) {
        super(context);
        setTextColor(Color.WHITE);
        setTextSize(10);
        setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MONO));
        setPadding(dp(8), dp(8), dp(8), dp(8));
        setGravity(Gravity.LEFT | Gravity.TOP);
        setBackground(Theme.createRoundRectDrawable(dp(10), 0xB0000000));
        setOnClickListener(v -> {
            if (dataSource != null && AndroidUtilities.addToClipboard(contentBuilder.build()) && AndroidUtilities.shouldShowClipboardToast()) {
                Toast.makeText(getContext(), LocaleController.getString(R.string.TextCopied), Toast.LENGTH_SHORT).show();
            }
        });
        setLongClickable(false);
        setFocusable(false);
        setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    public void setDataSource(@Nullable DataSource dataSource) {
        this.dataSource = dataSource;
        refresh();
    }

    public void setUpdateInterval(long updateIntervalMs) {
        this.updateIntervalMs = Math.max(16L, updateIntervalMs);
    }

    public void refresh() {
        if (dataSource == null) {
            setText("");
            return;
        }
        contentBuilder.reset();
        dataSource.build(contentBuilder);
        setText(contentBuilder.build());
    }

    public static FrameLayout.LayoutParams createLayoutParams() {
        var layoutParams = new FrameLayout.LayoutParams(dp(220), LayoutHelper.WRAP_CONTENT, Gravity.RIGHT | Gravity.TOP);
        layoutParams.setMargins(dp(10), dp(AndroidUtilities.statusBarHeight) - ActionBar.getCurrentActionBarHeight(), dp(10), 0);
        return layoutParams;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        removeCallbacks(updater);
        post(updater);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        removeCallbacks(updater);
    }
}