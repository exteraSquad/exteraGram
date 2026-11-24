/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.pillstack.custom;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;

import com.exteragram.messenger.pillstack.core.PillStackConfig;
import com.exteragram.messenger.pillstack.custom.ui.CustomPillEditorActivity;
import com.exteragram.messenger.pillstack.ui.PillStackPreferencesActivity;
import com.exteragram.messenger.pillstack.ui.pills.BasePill;
import com.exteragram.messenger.pillstack.ui.pills.crypto.utils.ColoredBackground;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.browser.Browser;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedTextView;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.ScaleStateListAnimator;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.Stories.recorder.Weather;

@SuppressLint("ViewConstructor")
public class CustomPill extends BasePill implements NotificationCenter.NotificationCenterDelegate {

    public static final int MAX_TEXT_WIDTH_DP = 150;
    private static final int MIN_TEXT_WIDTH_DP = 60;
    private static final int MAX_DETAIL_WIDTH_DP = 280;
    private static final int CHROME_WIDTH_DP = 36;

    private final int pillId;
    private int textMaxWidth;
    @Nullable
    private CustomPillSpec previewSpec;
    private final LinearLayout layout;
    private final PillIconView iconView;
    private final AnimatedTextView textView;

    @Nullable
    private CustomPillResult result;
    private boolean secretRequired;
    private boolean requestInFlight;
    private boolean contentVisible;

    public CustomPill(Context context, Theme.ResourcesProvider resourcesProvider, int pillId) {
        this(context, resourcesProvider, pillId, null);
    }

    public CustomPill(Context context, Theme.ResourcesProvider resourcesProvider, CustomPillSpec previewSpec) {
        this(context, resourcesProvider, 0, previewSpec);
    }

    private CustomPill(Context context, Theme.ResourcesProvider resourcesProvider, int pillId, @Nullable CustomPillSpec previewSpec) {
        super(context, resourcesProvider);
        this.pillId = pillId;
        this.previewSpec = previewSpec;

        layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER);
        layout.setMinimumWidth(dp(48));
        layout.setPadding(dp(8), 0, dp(8), 0);
        addView(layout, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 28, Gravity.CENTER_VERTICAL | (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT)));

        iconView = new PillIconView(context, 16);
        layout.addView(iconView, LayoutHelper.createLinear(16, 16, Gravity.CENTER_VERTICAL, 0, 0, 4, 0));

        textView = new AnimatedTextView(context, true, true, true);
        textView.setTextSize(dp(13));
        textView.setIncludeFontPadding(false);
        textView.setTypeface(AndroidUtilities.bold());
        textView.adaptWidth = true;
        textView.setMaxWidth(dp(MAX_TEXT_WIDTH_DP));
        NotificationCenter.listenEmojiLoading(textView);
        layout.addView(textView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL));

        setLoadingTargetView(layout);
        if (previewSpec == null) {
            ScaleStateListAnimator.apply(layout);
            CustomPillResult cached = CustomPillStore.getResult(pillId);
            if (cached != null) {
                setResult(cached, false);
            } else {
                setContentVisible(false);
                updateColors();
            }
        } else {
            updateColors();
        }
    }

    public void setPreviewSpec(CustomPillSpec spec) {
        previewSpec = spec;
        updateColors();
    }

    @Nullable
    public CustomPillSpec getSpec() {
        return previewSpec != null ? previewSpec : CustomPillStore.get(pillId);
    }

    @Override
    public int getPillId() {
        return pillId;
    }

    @Override
    public long getRefreshInterval() {
        if (previewSpec != null) {
            return 0;
        }
        CustomPillSpec spec = getSpec();
        if (spec == null) {
            return 0;
        }
        if (spec.http) {
            return spec.refresh * 1000L;
        }
        return CustomPillResult.usesClock(spec) ? 60 * 1000L : 0;
    }

    public void setPreview(@Nullable CustomPillResult result, boolean loading, boolean animated) {
        if (loading) {
            if (!this.loading) {
                startLoading();
            }
            if (result == null) {
                setContentVisible(false);
            }
            return;
        }
        if (result != null) {
            setResult(result, animated);
        } else {
            stopLoading();
            setContentVisible(false);
        }
    }

    @Override
    public void onUpdateData(boolean force) {
        if (previewSpec != null) {
            return;
        }
        CustomPillSpec spec = getSpec();
        if (spec == null) {
            return;
        }
        if (!spec.http) {
            CustomPillResult newResult = CustomPillResult.build(spec, null);
            CustomPillStore.putResult(pillId, newResult);
            markDataUpdated();
            setResult(newResult, result != null && !TextUtils.equals(result.text, newResult.text));
            return;
        }
        if (CustomPillStore.hasMissingSecrets(spec)) {
            showSecretRequired();
            return;
        }
        CustomPillResult cached = CustomPillStore.getResult(pillId);
        boolean due = spec.refresh > 0 && isRefreshDue();
        if (!force && cached != null && !due) {
            setResult(cached, false);
            return;
        }
        if (requestInFlight) {
            return;
        }
        requestInFlight = true;
        if (force) {
            animateSizeChange();
        }
        startLoading();
        if (result == null) {
            setContentVisible(false);
        }
        CustomPillFetcher.fetch(spec, CustomPillStore.getSecrets(pillId), response -> {
            requestInFlight = false;
            CustomPillSpec current = getSpec();
            if (current == null) {
                stopLoading();
                return;
            }
            CustomPillResult newResult = CustomPillResult.build(current, response);
            if (newResult.state == CustomPillResult.STATE_DATA) {
                CustomPillStore.putResponse(pillId, response);
                CustomPillStore.putResult(pillId, newResult);
                markDataUpdated();
            }
            setResult(newResult, true);
        });
    }

    private void setResult(CustomPillResult result, boolean animate) {
        this.result = result;
        secretRequired = false;
        stopLoading();
        if (animate) {
            animateSizeChange();
        }
        switch (result.state) {
            case CustomPillResult.STATE_LOCATION -> {
                iconView.setIconRes(R.drawable.filled_location);
                textView.setText(LocaleController.getString(R.string.WeatherLocationPermissionGrant), animate);
            }
            case CustomPillResult.STATE_ERROR -> {
                iconView.setIconRes(R.drawable.msg_retry);
                textView.setText(getErrorText(result), animate);
            }
            default -> {
                iconView.setIcon(result.icon);
                textView.setText(Emoji.replaceEmoji(result.text, textView.getPaint().getFontMetricsInt(), false), animate);
            }
        }
        setContentVisible(true);
        updateColors();
    }

    private void showSecretRequired() {
        secretRequired = true;
        stopLoading();
        iconView.setIconRes(R.drawable.msg_edit);
        textView.setText(LocaleController.getString(R.string.CustomPillSecretRequired), false);
        setContentVisible(true);
        updateColors();
    }

    private String getErrorText(CustomPillResult result) {
        if (result.error == CustomPillFetcher.ERROR_TOO_LARGE) {
            return LocaleController.getString(R.string.CustomPillErrorTooLarge);
        }
        if (result.error == CustomPillFetcher.ERROR_NONE && result.status > 0) {
            return "HTTP " + result.status;
        }
        return LocaleController.getString(R.string.Retry);
    }

    private void setContentVisible(boolean visible) {
        contentVisible = visible;
        iconView.setVisibility(visible ? VISIBLE : GONE);
        textView.setVisibility(visible ? VISIBLE : GONE);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (previewSpec != null) {
            return;
        }
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.pillStackSettingsChanged);
        boolean force = PillStackConfig.checkAndClearPendingUpdate(pillId);
        if (!requestInFlight && (force || CustomPillStore.getResult(pillId) == null || !contentVisible)) {
            onUpdateData(true);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (previewSpec == null) {
            NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.pillStackSettingsChanged);
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.pillStackSettingsChanged) {
            if (!PillStackConfig.shouldUpdatePill(args, pillId)) {
                return;
            }
            PillStackConfig.checkAndClearPendingUpdate(pillId);
            onUpdateData(true);
        }
    }

    @Override
    public void onPillClicked() {
        if (previewSpec != null) {
            return;
        }
        CustomPillSpec spec = getSpec();
        if (spec == null) {
            return;
        }
        if (secretRequired) {
            openEditor();
            return;
        }
        if (result == null || loading) {
            if (!loading) {
                onUpdateData(true);
            }
            return;
        }
        if (result.state == CustomPillResult.STATE_LOCATION) {
            Weather.getUserLocation(true, location -> {
                if (location != null) {
                    onUpdateData(true);
                }
            });
            return;
        }
        if (result.state == CustomPillResult.STATE_ERROR) {
            onUpdateData(true);
            return;
        }
        switch (spec.tap) {
            case CustomPillSpec.TAP_REFRESH -> onUpdateData(true);
            case CustomPillSpec.TAP_LINK -> openLink();
            case CustomPillSpec.TAP_COPY -> copyText();
            case CustomPillSpec.TAP_DETAILS -> showDetails();
        }
    }

    @Override
    public boolean onPillLongClicked() {
        if (previewSpec != null) {
            return false;
        }
        BaseFragment fragment = LaunchActivity.getSafeLastFragment();
        if (fragment == null) {
            return false;
        }
        ItemOptions options = ItemOptions.makeOptions(fragment, this);
        fillCommonOptions(options, fragment);
        addUpdatedAt(options);
        options.setDrawScrim(false)
                .setGravity(LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT)
                .setDimAlpha(0)
                .setDismissOnMoveOutside(true)
                .show();
        return true;
    }

    private void fillCommonOptions(ItemOptions options, BaseFragment fragment) {
        options.add(R.drawable.msg_retry, LocaleController.getString(R.string.Refresh), () -> onUpdateData(true));
        if (result != null && result.state == CustomPillResult.STATE_DATA) {
            options.add(R.drawable.msg_copy, LocaleController.getString(R.string.Copy), this::copyText);
        }
        options.add(R.drawable.msg_edit, LocaleController.getString(R.string.CustomPillEdit), this::openEditor);
        options.add(R.drawable.msg_settings, LocaleController.getString(R.string.Settings), () -> fragment.presentFragment(new PillStackPreferencesActivity()));
    }

    private void showDetails() {
        BaseFragment fragment = LaunchActivity.getSafeLastFragment();
        if (fragment == null || result == null) {
            return;
        }
        ItemOptions options = ItemOptions.makeOptions(fragment, this);
        for (String[] detail : result.details) {
            ActionBarMenuSubItem item = new ActionBarMenuSubItem(options.getContext(), false, false, resourcesProvider) {
                @Override
                protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                    int maxWidth = dp(MAX_DETAIL_WIDTH_DP);
                    if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED || MeasureSpec.getSize(widthMeasureSpec) > maxWidth) {
                        widthMeasureSpec = MeasureSpec.makeMeasureSpec(maxWidth, MeasureSpec.AT_MOST);
                    }
                    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                }
            };
            item.setTextAndIcon(detail[1], 0);
            item.setSubtext(detail[0]);
            item.setItemHeight(56);
            item.setOnClickListener(v -> {
                options.dismiss();
                copy(detail[1]);
            });
            options.addView(item);
        }
        if (!result.details.isEmpty()) {
            options.addGap();
        }
        fillCommonOptions(options, fragment);
        addUpdatedAt(options);
        options.setDrawScrim(false)
                .setGravity(LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT)
                .setDimAlpha(0)
                .show();
    }

    private void openLink() {
        if (result == null || !CustomPillSpec.isValidLink(result.link)) {
            return;
        }
        BaseFragment fragment = LaunchActivity.getSafeLastFragment();
        Context context = fragment != null && fragment.getParentActivity() != null ? fragment.getParentActivity() : getContext();
        Browser.openUrl(context, result.link);
    }

    private void copyText() {
        if (result != null) {
            copy(result.text);
        }
    }

    private void copy(String text) {
        if (AndroidUtilities.addToClipboard(text)) {
            BaseFragment fragment = LaunchActivity.getSafeLastFragment();
            if (fragment != null) {
                BulletinFactory.of(fragment).createCopyBulletin(LocaleController.getString(R.string.TextCopied)).show();
            }
        }
    }

    private void openEditor() {
        BaseFragment fragment = LaunchActivity.getSafeLastFragment();
        if (fragment != null && CustomPillStore.get(pillId) != null) {
            fragment.presentFragment(new CustomPillEditorActivity(pillId));
        }
    }

    @Override
    public void setPressed(boolean pressed) {
        if (loading) pressed = false;
        super.setPressed(pressed);
        layout.setPressed(pressed);
    }

    @Override
    public void drawableHotspotChanged(float x, float y) {
        if (loading) return;
        super.drawableHotspotChanged(x, y);
        layout.drawableHotspotChanged(x - layout.getLeft(), y - layout.getTop());
    }

    private String getEffectiveColor() {
        CustomPillSpec spec = getSpec();
        if (result != null && result.state == CustomPillResult.STATE_DATA && !secretRequired) {
            return result.color;
        }
        return spec != null ? spec.color : CustomPillSpec.COLOR_NEUTRAL;
    }

    @Override
    protected long getLastUpdatedAt() {
        return result != null && result.time > 0 ? result.time * 1000 : super.getLastUpdatedAt();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.UNSPECIFIED) {
            int textMaxWidth = Math.max(dp(MIN_TEXT_WIDTH_DP), MeasureSpec.getSize(widthMeasureSpec) - dp(CHROME_WIDTH_DP));
            if (textMaxWidth != this.textMaxWidth) {
                this.textMaxWidth = textMaxWidth;
                textView.setMaxWidth(textMaxWidth);
                textView.forceLayout();
                layout.forceLayout();
            }
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    private boolean isBaseColored() {
        CustomPillSpec spec = getSpec();
        return spec != null && spec.isColored();
    }

    @Override
    public void updateColors() {
        String color = getEffectiveColor();
        boolean neutral = CustomPillSpec.COLOR_NEUTRAL.equals(color);
        int textColor;
        Drawable background;
        if (!neutral && isBaseColored()) {
            PillColors colors = PillColors.get(color);
            textColor = colors.foreground();
            background = new ColoredBackground(colors.top(), colors.bottom());
        } else if (!neutral) {
            PillColors colors = PillColors.get(color);
            boolean dark = Theme.isCurrentThemeDark();
            textColor = dark ? colors.top() : colors.bottom();
            float brightness = AndroidUtilities.computePerceivedBrightness(textColor);
            if (dark ? brightness < .2f : brightness >= .721f) {
                textColor = getThemedColor(Theme.key_windowBackgroundWhiteBlackText, 0.75f);
            }
            background = Theme.createSimpleSelectorRoundRectDrawable(dp(14), Theme.multAlpha(textColor, 0.14f), Theme.multAlpha(textColor, 0.2f));
        } else {
            textColor = getThemedColor(Theme.key_windowBackgroundWhiteBlackText, 0.75f);
            int backgroundColor = Theme.isCurrentThemeDark() ? getThemedColor(Theme.key_windowBackgroundWhite) : Theme.multAlpha(textColor, 0.09f);
            background = Theme.createSimpleSelectorRoundRectDrawable(dp(14), backgroundColor, Theme.multAlpha(textColor, 0.1f));
        }
        layout.setBackground(background);
        textView.setTextColor(textColor);
        iconView.setColor(textColor);
        updateLoadingColors();
    }

    @Override
    protected void updateLoadingColors() {
        if (loadingDrawable == null) {
            return;
        }
        String color = getEffectiveColor();
        if (!CustomPillSpec.COLOR_NEUTRAL.equals(color) && isBaseColored()) {
            int foreground = PillColors.get(color).foreground();
            loadingDrawable.setColors(Theme.multAlpha(foreground, 0.1f), Theme.multAlpha(foreground, 0.3f));
        } else {
            super.updateLoadingColors();
        }
    }

    public View getContentView() {
        return layout;
    }
}
