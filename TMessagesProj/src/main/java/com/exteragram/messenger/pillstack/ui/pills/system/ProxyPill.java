/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.pillstack.ui.pills.system;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.exteragram.messenger.pillstack.core.PillType;
import com.exteragram.messenger.pillstack.ui.PillStackPreferencesActivity;
import com.exteragram.messenger.pillstack.ui.pills.BasePill;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedTextView;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.ScaleStateListAnimator;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.ProxyListActivity;

@SuppressLint("ViewConstructor")
public class ProxyPill extends BasePill implements NotificationCenter.NotificationCenterDelegate {

    private final ImageView iconView;
    private final AnimatedTextView textView;
    private final LinearLayout layout;

    public ProxyPill(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context, resourcesProvider);

        layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER);
        layout.setMinimumWidth(dp(48));
        layout.setPadding(dp(8), 0, dp(10), 0);
        addView(layout, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 28, Gravity.CENTER_VERTICAL | (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT)));

        iconView = new ImageView(context);
        iconView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        layout.addView(iconView, LayoutHelper.createLinear(16, 16, Gravity.CENTER_VERTICAL, 0, 0, 2, 0));

        textView = new AnimatedTextView(context, true, true, true);
        textView.setTextSize(dp(13));
        textView.setIncludeFontPadding(false);
        textView.setTypeface(AndroidUtilities.bold());
        textView.adaptWidth = true;
        layout.addView(textView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL));

        setLoadingTargetView(layout);
        updateColors();
        ScaleStateListAnimator.apply(layout);

        onUpdateData(false);
    }

    @Override
    public long getRefreshInterval() {
        return 0;
    }

    @Override
    public int getPillId() {
        return PillType.PROXY.getId();
    }

    @Override
    public void onUpdateData(boolean force) {
        boolean enabled = SharedConfig.isProxyEnabled();
        int state = ConnectionsManager.getInstance(UserConfig.selectedAccount).getConnectionState();
        boolean connected = state == ConnectionsManager.ConnectionStateConnected || state == ConnectionsManager.ConnectionStateUpdating;

        String oldText = textView.getText() != null ? textView.getText().toString() : "";
        String newText;

        if (enabled && SharedConfig.currentProxy != null) {
            if (connected) {
                long ping = Utilities.clamp(SharedConfig.currentProxy.ping, 9999L, 0L);
                iconView.setImageResource(R.drawable.drawer_proxy_on);
                if (ping > 0) {
                    newText = LocaleController.formatString(R.string.NavigationDrawerProxyPingShort, ping);
                } else {
                    newText = LocaleController.getString(R.string.MenuProxyConnected);
                }
                stopLoading();
            } else {
                iconView.setImageResource(R.drawable.drawer_proxy_off);
                newText = LocaleController.getString(R.string.MenuProxyConnecting);
                startLoading();
            }
        } else {
            iconView.setImageResource(R.drawable.drawer_proxy_off);
            newText = LocaleController.getString(R.string.Proxy);
            stopLoading();
        }

        if (force || !TextUtils.equals(oldText, newText)) {
            if (force) {
                animateSizeChange();
            }
            textView.setText(newText, force);
        }

        updateColors();
    }

    private int lastAccount;

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        onUpdateData(true);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.proxySettingsChanged);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.proxyPingUpdated);
        NotificationCenter.getInstance(lastAccount = UserConfig.selectedAccount).addObserver(this, NotificationCenter.didUpdateConnectionState);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.proxySettingsChanged);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.proxyPingUpdated);
        NotificationCenter.getInstance(lastAccount).removeObserver(this, NotificationCenter.didUpdateConnectionState);
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.proxySettingsChanged || id == NotificationCenter.proxyPingUpdated || id == NotificationCenter.didUpdateConnectionState) {
            onUpdateData(true);
        }
    }

    @Override
    public void onPillClicked() {
        BaseFragment fragment = LaunchActivity.getSafeLastFragment();
        if (fragment != null) {
            fragment.presentFragment(new ProxyListActivity());
        }
    }

    @Override
    public boolean onPillLongClicked() {
        BaseFragment fragment = LaunchActivity.getSafeLastFragment();
        if (fragment != null) {
            ItemOptions.makeOptions(fragment, this)
                    .add(R.drawable.msg_settings, LocaleController.getString(R.string.Settings), () -> fragment.presentFragment(new PillStackPreferencesActivity()))
                    .setDrawScrim(false)
                    .setDimAlpha(0)
                    .setDismissOnMoveOutside(true)
                    .show();
            return true;
        }
        return false;
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

    @Override
    public void updateColors() {
        boolean enabled = SharedConfig.isProxyEnabled();
        int state = ConnectionsManager.getInstance(UserConfig.selectedAccount).getConnectionState();
        boolean connected = state == ConnectionsManager.ConnectionStateConnected || state == ConnectionsManager.ConnectionStateUpdating;

        int textColor;
        if (enabled && SharedConfig.currentProxy != null && connected) {
            textColor = getThemedColor(Theme.key_windowBackgroundWhiteGreenText);
        } else {
            textColor = getThemedColor(Theme.key_windowBackgroundWhiteBlackText, 0.75f);
        }

        int backgroundColor = Theme.isCurrentThemeDark() ? getThemedColor(Theme.key_windowBackgroundWhite) : Theme.multAlpha(textColor, 0.09f);
        layout.setBackground(Theme.createSimpleSelectorRoundRectDrawable(dp(14), backgroundColor, Theme.multAlpha(textColor, 0.1f)));
        textView.setTextColor(textColor);
        iconView.setColorFilter(new PorterDuffColorFilter(textColor, PorterDuff.Mode.MULTIPLY));
        updateLoadingColors();
    }
}
