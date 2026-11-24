/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.adblock.ui;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;

import com.exteragram.messenger.ExteraConfig;
import com.exteragram.messenger.adblock.WebAdBlocker;
import com.exteragram.messenger.adblock.backend.AdBlockManager;
import com.exteragram.messenger.utils.ui.material.SwitchUiHelper;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.Switch;

@SuppressLint("ViewConstructor")
public class AdBlockMenuItem extends ActionBarMenuSubItem {
    private static final String BLOCKED_COUNT_KEY = "BlockedRequests";
    private static final int[] BLOCKED_COUNT_SAMPLES = {991, 992, 999};
    private static final int ITEM_HEIGHT = 48;
    private static final int ITEM_HEIGHT_WITH_SUBTEXT = 56;
    private static final int SWITCH_WIDTH = 44;
    private static final int SWITCH_HEIGHT = 28;
    private static final int LEGACY_SWITCH_WIDTH = 37;
    private static final int LEGACY_SWITCH_HEIGHT = 20;
    private static final float MATERIAL3_SWITCH_SCALE = 0.85f;
    private static final int SWITCH_GAP = 12;
    private static final int SUBTEXT_SIZE = 13;
    private static final long UPDATE_INTERVAL = 500;
    private static final long DISMISS_DELAY = 200;

    private final WebAdBlocker adBlocker;
    private final ItemOptions options;
    private final Switch switchView;
    private CharSequence subtext;
    private boolean toggled;

    private final Runnable updateRunnable = new Runnable() {
        @Override
        public void run() {
            if (!toggled) {
                update();
                AndroidUtilities.runOnUIThread(this, UPDATE_INTERVAL);
            }
        }
    };

    public static void addTo(ItemOptions options, @Nullable WebAdBlocker adBlocker) {
        if (adBlocker == null || !AdBlockManager.isAvailable()) {
            return;
        }
        if (ExteraConfig.getEnableAdBlock() && !AdBlockManager.isActive()) {
            AdBlockManager.initialize();
        }
        var item = new AdBlockMenuItem(options.getContext(), adBlocker, options);
        options.add(item);
        item.alignSwitch();
        item.fitWidth();
        options.addGap();
    }

    private AdBlockMenuItem(Context context, WebAdBlocker adBlocker, ItemOptions options) {
        super(context, false, false, null);
        this.adBlocker = adBlocker;
        this.options = options;

        setTextAndIcon(getString(R.string.BlockAds), R.drawable.msg_policy);
        setClipToPadding(false);

        switchView = new Switch(context);
        switchView.setColors(Theme.key_switchTrack, Theme.key_switchTrackChecked, Theme.key_windowBackgroundWhite, Theme.key_windowBackgroundWhite);
        switchView.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        switchView.setChecked(ExteraConfig.getEnableAdBlock(), false);
        if (SwitchUiHelper.isMaterial3SwitchStyle()) {
            switchView.setScaleX(MATERIAL3_SWITCH_SCALE);
            switchView.setScaleY(MATERIAL3_SWITCH_SCALE);
        }
        addView(switchView, LayoutHelper.createFrame(SWITCH_WIDTH, SWITCH_HEIGHT, Gravity.CENTER_VERTICAL | (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT)));

        setOnClickListener(v -> toggle());
        update();
    }

    private void alignSwitch() {
        float switchWidth;
        float switchHeight;
        if (SwitchUiHelper.isMaterial3SwitchStyle()) {
            var track = new RectF();
            SwitchUiHelper.setTrackBounds(track, 0, 0);
            switchWidth = track.width() * MATERIAL3_SWITCH_SCALE;
            switchHeight = track.height() * MATERIAL3_SWITCH_SCALE;
        } else {
            switchWidth = dp(LEGACY_SWITCH_WIDTH);
            switchHeight = dp(LEGACY_SWITCH_HEIGHT);
        }
        var edgeGap = (dp(subtext != null ? ITEM_HEIGHT_WITH_SUBTEXT : ITEM_HEIGHT) - switchHeight) / 2f;
        var endPadding = LocaleController.isRTL ? getPaddingLeft() : getPaddingRight();
        var switchMargin = Math.round(edgeGap + (switchWidth - dp(SWITCH_WIDTH)) / 2f - endPadding);
        var textPadding = Math.round(edgeGap + switchWidth + dp(SWITCH_GAP) - endPadding);

        var layoutParams = (FrameLayout.LayoutParams) switchView.getLayoutParams();
        if (LocaleController.isRTL) {
            layoutParams.leftMargin = switchMargin;
            textView.setPadding(textPadding, 0, textView.getPaddingRight(), 0);
        } else {
            layoutParams.rightMargin = switchMargin;
            textView.setPadding(textView.getPaddingLeft(), 0, textPadding, 0);
        }
        if (subtextView != null) {
            subtextView.setPadding(textView.getPaddingLeft(), 0, textView.getPaddingRight(), 0);
        }
    }

    private void fitWidth() {
        var minWidth = getMinimumWidth();
        if (minWidth <= 0) {
            return;
        }
        var subtextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        subtextPaint.setTextSize(dp(SUBTEXT_SIZE));
        var subtextWidth = subtextPaint.measureText(getString(R.string.AdBlockFiltersLoading));
        for (var count : BLOCKED_COUNT_SAMPLES) {
            subtextWidth = Math.max(subtextWidth, subtextPaint.measureText(LocaleController.formatPluralString(BLOCKED_COUNT_KEY, count)));
        }
        var contentWidth = Math.max(textView.getPaint().measureText(textView.getText().toString()), subtextWidth);
        var width = Math.min(
                getPaddingLeft() + getPaddingRight() + textView.getPaddingLeft() + textView.getPaddingRight() + (int) Math.ceil(contentWidth),
                AndroidUtilities.displaySize.x - dp(32)
        );
        if (width > minWidth) {
            var widthDp = (int) Math.ceil(width / AndroidUtilities.density);
            options.setMinWidth(widthDp);
            for (int i = 0; i < options.getItemsCount(); i++) {
                var item = options.getItemAt(i);
                if (item instanceof ActionBarMenuSubItem) {
                    item.setMinimumWidth(dp(widthDp));
                    item.getLayoutParams().width = dp(widthDp);
                }
            }
        }
    }

    private void update() {
        CharSequence newSubtext = null;
        if (ExteraConfig.getEnableAdBlock()) {
            newSubtext = AdBlockManager.isActive()
                    ? LocaleController.formatPluralString(BLOCKED_COUNT_KEY, adBlocker.getBlockedCount())
                    : getString(R.string.AdBlockFiltersLoading);
        }
        if (subtextView != null && TextUtils.equals(subtext, newSubtext)) {
            return;
        }
        subtext = newSubtext;
        setItemHeight(subtext != null ? ITEM_HEIGHT_WITH_SUBTEXT : ITEM_HEIGHT);
        setSubtext(subtext);
    }

    private void toggle() {
        if (toggled) {
            return;
        }
        toggled = true;
        var enabled = !ExteraConfig.getEnableAdBlock();
        switchView.setChecked(enabled, true);
        if (enabled) {
            AdBlockManager.setEnabled(true, adBlocker::reload);
        } else {
            AdBlockManager.setEnabled(false, null);
            adBlocker.reload();
        }
        AndroidUtilities.runOnUIThread(options::dismiss, DISMISS_DELAY);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        AndroidUtilities.runOnUIThread(updateRunnable, UPDATE_INTERVAL);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        AndroidUtilities.cancelRunOnUIThread(updateRunnable);
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClassName("android.widget.Switch");
        info.setCheckable(true);
        info.setChecked(switchView.isChecked());
    }
}
