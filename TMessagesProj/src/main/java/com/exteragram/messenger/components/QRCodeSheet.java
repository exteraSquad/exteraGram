/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.net.Uri;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiNetworkSuggestion;
import android.os.Build;
import android.provider.Settings;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.util.Base64;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.content.ContextCompat;

import com.exteragram.messenger.utils.system.SystemUtils;
import com.exteragram.messenger.utils.text.LocaleUtils;
import com.google.zxing.EncodeHintType;
import org.telegram.messenger.TelegramQRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LinkifyPort;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.browser.Browser;
import org.telegram.messenger.utils.ViewOutlineProviderImpl;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.DialogCell;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.ColoredImageSpan;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.ScaleStateListAnimator;
import org.telegram.ui.Components.StickerImageView;

import org.telegram.ui.Stories.recorder.ButtonWithCounterView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

public class QRCodeSheet extends BottomSheet {

    private final int TEXT_TYPE_LINK = 0;
    private final int TEXT_TYPE_TEXT = 1;
    private final int TEXT_TYPE_AUTH_TOKEN = 2;
    private final int TEXT_TYPE_PHONE = 3;
    private final int TEXT_TYPE_WIFI = 4;

    private final BaseFragment fragment;

    private String ssid;
    private String password;
    private String wifiAuthType = "WPA";

    public QRCodeSheet(BaseFragment fragment, String text) {
        super(fragment.getParentActivity(), false, fragment.getResourceProvider());
        this.fragment = fragment;

        int type;
        CharSequence firstButton, secondButton;
        SpannableStringBuilder spannableStringBuilder;

        if (text.startsWith("tg://login?token=")) {
            type = TEXT_TYPE_AUTH_TOKEN;
            firstButton = LocaleController.getString(R.string.Cancel);
            secondButton = LocaleController.getString(R.string.Allow);
        } else if (LinkifyPort.WEB_URL.matcher(text).matches() || text.startsWith("tel:")) {
            type = text.startsWith("tel:") ? TEXT_TYPE_PHONE : TEXT_TYPE_LINK;
            spannableStringBuilder = new SpannableStringBuilder(LocaleController.getString(R.string.Open)).append(".");
            int end = spannableStringBuilder.length();
            spannableStringBuilder.setSpan(new ColoredImageSpan(ContextCompat.getDrawable(fragment.getParentActivity(), R.drawable.msg_mini_topicarrow)), end - 1, end, 0);
            firstButton = spannableStringBuilder;
            secondButton = getTextWithIcon("share");
        } else if (text.startsWith("WIFI:")) {
            type = TEXT_TYPE_WIFI;
            parseWifiInfo(text);
            firstButton = LocaleController.getString(R.string.WifiConnect);
            secondButton = getTextWithIcon("share");
        } else {
            type = TEXT_TYPE_TEXT;
            firstButton = getTextWithIcon("copy");
            secondButton = getTextWithIcon("share");
        }

        Activity activity = fragment.getParentActivity();

        fixNavigationBar();

        FrameLayout frameLayout = new FrameLayout(activity);
        LinearLayout linearLayout = new LinearLayout(activity);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        frameLayout.addView(linearLayout);

        View scrollUp = new View(fragment.getParentActivity()) {
            @Override
            protected void onDraw(@NonNull Canvas canvas) {
                super.onDraw(canvas);

                int w = getMeasuredWidth();
                @SuppressLint("DrawAllocation") RectF rect = new RectF();
                rect.set((float) (getMeasuredWidth() - w) / 2, 0, (float) (getMeasuredWidth() + w) / 2, dp(4));
                Theme.dialogs_onlineCirclePaint.setColor(getThemedColor(Theme.key_sheet_scrollUp));
                canvas.drawRoundRect(rect, dp(2), dp(2), Theme.dialogs_onlineCirclePaint);
            }
        };
        linearLayout.addView(scrollUp, LayoutHelper.createLinear(36, 4, Gravity.CENTER_HORIZONTAL, 18, 2, 18, 0));

        if (type == TEXT_TYPE_AUTH_TOKEN) {
            StickerImageView imageView = new StickerImageView(activity, currentAccount);
            imageView.setStickerPackName(AndroidUtilities.STICKERS_PLACEHOLDER_PACK_NAME);
            imageView.setStickerNum(6);
            imageView.getImageReceiver().setAutoRepeat(1);
            imageView.getImageReceiver().setAutoRepeatCount(1);
            linearLayout.addView(imageView, LayoutHelper.createLinear(144, 144, Gravity.CENTER_HORIZONTAL, 0, 20, 0, 10));
        } else {
            ImageView imageView = new ImageView(activity);
            ScaleStateListAnimator.apply(imageView, 0.03f, 1.2f);
            imageView.setScaleType(ImageView.ScaleType.FIT_XY);
            imageView.setOutlineProvider(ViewOutlineProviderImpl.boundsWithRoundRect(dp(12)));
            imageView.setClipToOutline(true);

            Bitmap qrBitmap = createQR(text);
            imageView.setImageBitmap(qrBitmap);
            imageView.setOnClickListener(v -> {
                if (qrBitmap != null) {
                    copyQR(qrBitmap, activity);
                }
            });

            linearLayout.addView(imageView, LayoutHelper.createLinear(200, 200, Gravity.CENTER_HORIZONTAL, 18, 20, 18, 10));
        }

        TextView description = new TextView(activity);
        ScaleStateListAnimator.apply(description, 0.02f, 1.5f);
        description.setGravity(Gravity.CENTER_HORIZONTAL);
        description.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        description.setPadding(dp(8), dp(4), dp(8), dp(4));
        description.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
        if (type == TEXT_TYPE_WIFI && !TextUtils.isEmpty(ssid)) {
            description.setText(MessageFormat.format("SSID: {0}{1}", ssid, !TextUtils.isEmpty(password) ? ", Password: " + password : ""));
        } else {
            description.setText(type == TEXT_TYPE_AUTH_TOKEN ? LocaleController.getString(R.string.AreYouSureToLogin) : text);
        }
        if (type == TEXT_TYPE_LINK) {
            text = LocaleUtils.ensureUrlHasHttps(text);
        }
        String finalText = text;
        if (type != TEXT_TYPE_AUTH_TOKEN) {
            description.setBackground(Theme.createSelectorDrawable(Theme.multAlpha(getThemedColor(Theme.key_windowBackgroundWhiteGrayText), Theme.isCurrentThemeDark() ? .20f : .15f), Theme.RIPPLE_MASK_ROUNDRECT_6DP, dp(8)));
            description.setOnClickListener(v -> {
                if (AndroidUtilities.addToClipboard(finalText)) {
                    showCopyBulletin(true);
                }
            });
        }
        linearLayout.addView(description, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 21, 2, 21, 8));

        LinearLayout buttons = new LinearLayout(activity);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        linearLayout.addView(buttons, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 16, 15, 16, 4));

        ButtonWithCounterView button = new ButtonWithCounterView(activity, fragment.getResourceProvider());
        button.setRound();
        button.setText(firstButton, false);
        button.setOnClickListener(view -> {
            if (type == TEXT_TYPE_AUTH_TOKEN) {
                dismiss();
                return;
            }
            switch (type) {
                case TEXT_TYPE_LINK, TEXT_TYPE_PHONE ->
                        Browser.openUrl(fragment.getParentActivity(), Uri.parse(finalText));
                case TEXT_TYPE_WIFI -> AndroidUtilities.runOnUIThread(this::connectToWifi, 750);
                case TEXT_TYPE_TEXT -> {
                    if (AndroidUtilities.addToClipboard(finalText)) {
                        showCopyBulletin(false);
                    }
                }
            }
            dismiss();
        });

        if (type == TEXT_TYPE_AUTH_TOKEN) button.setNeutral();
        buttons.addView(button, LayoutHelper.createLinear(0, LayoutHelper.MATCH_PARENT, 1f));

        View separator = new View(activity);
        buttons.addView(separator, LayoutHelper.createLinear(0, LayoutHelper.MATCH_PARENT, 0.06f));

        ButtonWithCounterView secondaryButton = new ButtonWithCounterView(activity, fragment.getResourceProvider());
        secondaryButton.setRound();
        secondaryButton.setText(secondButton, false);
        secondaryButton.setFilled(true);
        secondaryButton.setOnClickListener(view -> {
            if (type == TEXT_TYPE_AUTH_TOKEN) {
                AndroidUtilities.runOnUIThread(() -> {
                    try {
                        String code = finalText.substring("tg://login?token=".length());
                        code = code.replaceAll("/", "_");
                        code = code.replaceAll("\\+", "-");
                        byte[] token = Base64.decode(code, Base64.URL_SAFE);
                        TLRPC.TL_auth_acceptLoginToken req = new TLRPC.TL_auth_acceptLoginToken();
                        req.token = token;
                        ConnectionsManager.getInstance(UserConfig.selectedAccount).sendRequest(req, (response, error) -> dismiss());
                    } catch (Exception e) {
                        FileLog.e("Failed to pass qr code auth", e);
                        AndroidUtilities.runOnUIThread(() -> AlertsCreator.showSimpleAlert(fragment, LocaleController.getString(R.string.AuthAnotherClient), LocaleController.getString(R.string.ErrorOccurred)));
                    }
                }, 750);
            } else {
                try {
                    Intent intent = new Intent(Intent.ACTION_SEND);
                    intent.setType("text/plain");
                    intent.putExtra(Intent.EXTRA_TEXT, finalText);
                    fragment.startActivityForResult(Intent.createChooser(intent, LocaleController.getString(R.string.QrCode)), 500);
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
            dismiss();
        });
        buttons.addView(secondaryButton, LayoutHelper.createLinear(0, LayoutHelper.MATCH_PARENT, 1f));

        ScrollView scrollView = new ScrollView(activity);
        scrollView.addView(frameLayout);
        setCustomView(scrollView);
    }

    private Spanned getTextWithIcon(String key) {
        var spannableStringBuilder = new SpannableStringBuilder();
        spannableStringBuilder.append("..").setSpan(new ColoredImageSpan(ContextCompat.getDrawable(fragment.getParentActivity(), key.equals("copy") ? R.drawable.msg_copy_filled : R.drawable.msg_share_filled)), 0, 1, 0);
        spannableStringBuilder.setSpan(new DialogCell.FixedWidthSpan(dp(4)), 1, 2, 0);
        spannableStringBuilder.append(key.equals("copy") ? LocaleController.getString(R.string.LinkActionCopy) : LocaleController.getString(R.string.LinkActionShare));
        return spannableStringBuilder;
    }

    private Bitmap createQR(String key) {
        try {
            HashMap<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
            hints.put(EncodeHintType.MARGIN, 0);
            TelegramQRCodeWriter writer = new TelegramQRCodeWriter();
            return writer.encode(key, 768, 768, hints, null, 1, 0xffffffff, 0xff000000, false);
        } catch (Exception e) {
            FileLog.e(e);
        }
        return null;
    }

    @SuppressLint("SetWorldReadable")
    private void copyQR(Bitmap bitmap, Activity activity) {
        try {
            File file = new File(activity.getExternalFilesDir(null), "qr_code.jpg");

            if (file.exists()) {
                file.delete();
            }
            file.createNewFile();

            FileOutputStream fOut = new FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fOut);
            fOut.flush();
            fOut.close();
            file.setReadable(true, false);

            SystemUtils.addFileToClipboard(file, () -> BulletinFactory.of(getContainer(), null).createCopyBulletin(LocaleController.getString(R.string.PhotoCopied), resourcesProvider).show());
        } catch (IOException e) {
            FileLog.e(e);
        }
    }

    private void parseWifiInfo(String text) {
        String data = text.substring(text.indexOf(":") + 1);
        String[] parts = data.split("(?<!\\\\);");
        for (String part : parts) {
            if (part.startsWith("S:")) {
                ssid = unescapeWifiString(part.substring(2));
            } else if (part.startsWith("P:")) {
                password = unescapeWifiString(part.substring(2));
            } else if (part.startsWith("T:")) {
                wifiAuthType = part.substring(2);
            }
        }
    }

    private String unescapeWifiString(String value) {
        return value.replace("\\\\", "\\")
                .replace("\\;", ";")
                .replace("\\:", ":")
                .replace("\\,", ",")
                .replace("\\\"", "\"");
    }

    private void connectToWifi() {
        if (TextUtils.isEmpty(ssid)) {
            showErrorBulletin(LocaleController.getString(R.string.WifiFailed));
            return;
        }

        WifiManager wifiManager = (WifiManager) ApplicationLoader.applicationContext.getSystemService(Context.WIFI_SERVICE);
        if (wifiManager == null) {
            showErrorBulletin(LocaleController.getString(R.string.WifiFailed));
            return;
        }

        if (!wifiManager.isWifiEnabled()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                showErrorBulletin(LocaleController.getString(R.string.WifiDisabled));
                Intent panelIntent = new Intent(Settings.Panel.ACTION_WIFI);
                fragment.startActivityForResult(panelIntent, 501);
            } else {
                showErrorBulletin(LocaleController.getString(R.string.WifiDisabled));
            }
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            connectWifiModern(wifiManager);
        } else {
            connectWifiLegacy(wifiManager);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    private void connectWifiModern(WifiManager wifiManager) {
        WifiNetworkSuggestion.Builder suggestionBuilder = new WifiNetworkSuggestion.Builder()
                .setSsid(ssid)
                .setIsAppInteractionRequired(true);

        if (!TextUtils.isEmpty(password)) {
            if ("WPA".equalsIgnoreCase(wifiAuthType)) {
                suggestionBuilder.setWpa2Passphrase(password);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && "SAE".equalsIgnoreCase(wifiAuthType)) {
                suggestionBuilder.setWpa3Passphrase(password);
            }
        }

        WifiNetworkSuggestion suggestion = suggestionBuilder.build();
        List<WifiNetworkSuggestion> suggestionsList = Collections.singletonList(suggestion);

        int status = wifiManager.addNetworkSuggestions(suggestionsList);
        if (status == WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS) {
            Intent intent = new Intent(Settings.ACTION_WIFI_SETTINGS);
            fragment.getParentActivity().startActivity(intent);
        } else {
            showErrorBulletin(LocaleController.getString(R.string.WifiFailed));
        }
    }

    @SuppressWarnings("deprecation")
    private void connectWifiLegacy(WifiManager wifiManager) {
        WifiConfiguration wifiConfig = new WifiConfiguration();
        wifiConfig.SSID = String.format("\"%s\"", ssid);

        boolean isOpenNetwork = TextUtils.isEmpty(wifiAuthType) || "nopass".equalsIgnoreCase(wifiAuthType);

        if (!TextUtils.isEmpty(password) && !isOpenNetwork) {
            if ("WPA".equalsIgnoreCase(wifiAuthType)) {
                wifiConfig.preSharedKey = String.format("\"%s\"", password);
            } else if ("WEP".equalsIgnoreCase(wifiAuthType)) {
                wifiConfig.wepKeys[0] = String.format("\"%s\"", password);
                wifiConfig.wepTxKeyIndex = 0;
                wifiConfig.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
                wifiConfig.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP40);
            }
        } else {
            wifiConfig.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
        }

        int netId = wifiManager.addNetwork(wifiConfig);
        if (netId != -1) {
            boolean enabled = wifiManager.enableNetwork(netId, true);
            if (enabled) {
                wifiManager.reconnect();
                BulletinFactory.of(fragment).createSimpleBulletin(
                        R.raw.contact_check,
                        LocaleController.getString(R.string.WifiSuccess)
                ).show();
            } else {
                showErrorBulletin(LocaleController.getString(R.string.WifiFailed));
            }
        } else {
            showErrorBulletin(LocaleController.getString(R.string.WifiFailed));
        }
    }

    private void showErrorBulletin(String message) {
        AndroidUtilities.runOnUIThread(() -> BulletinFactory.of(fragment).createErrorBulletin(message).show());
    }

    private void showCopyBulletin(boolean container) {
        AndroidUtilities.runOnUIThread(() -> {
            var bf = container ? BulletinFactory.of(getContainer(), null) : BulletinFactory.of(fragment);
            bf.createCopyBulletin(LocaleController.getString(R.string.TextCopied)).show();
        });
    }
}