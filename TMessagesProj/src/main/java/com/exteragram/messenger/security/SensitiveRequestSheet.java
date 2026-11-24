/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.security;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.hardware.input.InputManager;
import android.os.Build;
import android.os.IBinder;
import android.os.IInterface;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.TableView;
import org.telegram.ui.Stars.StarsIntroActivity;
import org.telegram.ui.Stars.StarsController;
import org.telegram.tgnet.tl.TL_stars;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;

public class SensitiveRequestSheet extends BottomSheet {

    private static final int DETAIL_RECIPIENT = 1;
    private static final int DETAIL_CHAT = 2;
    private static final int DETAIL_USER = 3;
    private static final int DETAIL_BOT = 4;
    private static final int DETAIL_STARS = 5;
    private static final int DETAIL_GRAMS = 6;
    private static final int DETAIL_GIFT = 7;

    private static final int DETAIL_FORM = 8;
    private static final int DETAIL_INVOICE_HASH = 9;
    private static final int DETAIL_GIFT_ID = 10;
    private static final int DETAIL_ANONYMOUS = 11;
    private static final int DETAIL_UPGRADE = 12;
    private static final int DETAIL_GIFT_MESSAGE = 13;
    private static final int DETAIL_SAVED_GIFT_ID = 14;

    private static final long MERGE_ALLOW_DELAY = 1000;
    private static final long VERIFIED_TAP_TIMEOUT = 1000;

    private final int constructor;
    private final CharSequence category;
    private final SensitivePaymentForms.Payment payment;
    private final String method;
    private final String[] texts;
    private final TextView subtitle;
    private final TableView tableView;
    private final ButtonWithCounterView allowButton;
    private final ArrayList<Utilities.Callback<Boolean>> callbacks = new ArrayList<>();
    private final Runnable enableAllowButton;
    private long[] details;
    private boolean answered;
    private boolean verifiedDown;
    private long verifiedTapTime;

    public SensitiveRequestSheet(Context context, Theme.ResourcesProvider resourcesProvider, int account, int constructor, CharSequence category, String method, long[] details, String[] texts, Utilities.Callback<Boolean> onResult) {
        super(context, false, resourcesProvider);
        currentAccount = account;
        this.constructor = constructor;
        this.method = method;
        this.details = details != null ? details : new long[0];
        this.texts = texts;
        this.category = category;
        long formId = 0;
        String invoiceHash = null;
        for (int i = 0; i + 1 < this.details.length; i += 2) {
            long value = this.details[i + 1];
            if (this.details[i] == DETAIL_FORM) {
                formId = value;
            } else if (this.details[i] == DETAIL_INVOICE_HASH && texts != null && value >= 0 && value < texts.length) {
                invoiceHash = texts[(int) value];
            }
        }
        payment = SensitivePaymentForms.get(currentAccount, formId, invoiceHash);
        callbacks.add(onResult);
        fixNavigationBar();

        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.VERTICAL);

        ImageView imageView = new ImageView(context);
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        imageView.setPadding(dp(18), dp(18), dp(18), dp(18));
        imageView.setImageResource(R.drawable.sensitive_request_stop);
        imageView.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_featuredStickers_buttonText), PorterDuff.Mode.SRC_IN));
        imageView.setBackground(Theme.createCircleDrawable(dp(80), getThemedColor(Theme.key_featuredStickers_addButton)));
        linearLayout.addView(imageView, LayoutHelper.createLinear(80, 80, Gravity.CENTER_HORIZONTAL, 0, 24, 0, 0));

        TextView title = new TextView(context);
        title.setGravity(Gravity.CENTER_HORIZONTAL);
        title.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        title.setTypeface(AndroidUtilities.bold());
        title.setText(getString(R.string.SensitiveRequestTitle));
        linearLayout.addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 24, 16, 24, 0));

        subtitle = new TextView(context);
        subtitle.setGravity(Gravity.CENTER_HORIZONTAL);
        subtitle.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        subtitle.setTypeface(AndroidUtilities.regular());
        subtitle.setText(category);
        subtitle.setVisibility(TextUtils.isEmpty(category) ? View.GONE : View.VISIBLE);
        linearLayout.addView(subtitle, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 24, 6, 24, 0));

        tableView = new TableView(context, resourcesProvider);
        fillTable();
        linearLayout.addView(tableView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 16, 20, 16, 0));

        TextView warning = new TextView(context);
        warning.setGravity(Gravity.CENTER_HORIZONTAL);
        warning.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
        warning.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        warning.setTypeface(AndroidUtilities.regular());
        warning.setText(getString(R.string.SensitiveRequestWarning));
        linearLayout.addView(warning, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 24, 14, 24, 0));

        allowButton = new ButtonWithCounterView(context, true, resourcesProvider);
        allowButton.setRound();
        allowButton.setText(getString(R.string.Allow), false);
        allowButton.setOnClickListener(v -> {
            if (!consumeVerifiedTap()) {
                return;
            }
            finish(true);
            dismiss();
        });
        enableAllowButton = () -> allowButton.setEnabled(true);
        linearLayout.addView(allowButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 0, 16, 20, 16, 8));

        ButtonWithCounterView declineButton = new ButtonWithCounterView(context, false, resourcesProvider);
        declineButton.setRound();
        declineButton.setNeutral();
        declineButton.setText(getString(R.string.Decline), false);
        declineButton.setOnClickListener(v -> {
            finish(false);
            dismiss();
        });
        linearLayout.addView(declineButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 0, 16, 0, 16, 8));

        ScrollView scrollView = new ScrollView(context);
        scrollView.addView(linearLayout);
        setCustomView(scrollView);
        setOnDismissListener(d -> {
            AndroidUtilities.cancelRunOnUIThread(enableAllowButton);
            finish(false);
        });
    }

    @Override
    public boolean dispatchTouchEvent(@NonNull MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                verifiedDown = isOverAllowButton(ev) && isGenuine(ev);
                break;
            case MotionEvent.ACTION_UP:
                if (verifiedDown && isOverAllowButton(ev) && isGenuine(ev)) {
                    verifiedTapTime = ev.getEventTime();
                }
                verifiedDown = false;
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
            case MotionEvent.ACTION_CANCEL:
                verifiedDown = false;
                break;
        }
        return super.dispatchTouchEvent(ev);
    }

    @Override
    public boolean dispatchKeyEvent(@NonNull KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_UP && isConfirmKey(event.getKeyCode()) && allowButton.isFocused() && isGenuine(event)) {
            verifiedTapTime = event.getEventTime();
        }
        return super.dispatchKeyEvent(event);
    }

    private static boolean isConfirmKey(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_SPACE || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER;
    }

    private boolean isOverAllowButton(MotionEvent ev) {
        if (!allowButton.isShown() || !allowButton.isEnabled()) {
            return false;
        }
        int[] location = new int[2];
        allowButton.getLocationOnScreen(location);
        float x = ev.getRawX(), y = ev.getRawY();
        return x >= location[0] && x < location[0] + allowButton.getWidth() && y >= location[1] && y < location[1] + allowButton.getHeight();
    }

    private boolean isGenuine(InputEvent event) {
        if (Build.VERSION.SDK_INT >= 30) {
            InputManager inputManager = getContext().getSystemService(InputManager.class);
            return inputManager != null && hasSystemBinders(inputManager, "android.hardware.input.IInputManager") && inputManager.verifyInputEvent(event) != null;
        }
        if (event instanceof MotionEvent) {
            return event.getDeviceId() > 0 && event.isFromSource(InputDevice.SOURCE_TOUCHSCREEN);
        }
        return event.getDeviceId() > 0 && event.getDeviceId() != KeyCharacterMap.VIRTUAL_KEYBOARD;
    }

    private boolean consumeVerifiedTap() {
        long tapTime = verifiedTapTime;
        verifiedTapTime = 0;
        if (tapTime != 0 && SystemClock.uptimeMillis() - tapTime <= VERIFIED_TAP_TIMEOUT) {
            return true;
        }
        AccessibilityManager accessibilityManager = (AccessibilityManager) getContext().getSystemService(Context.ACCESSIBILITY_SERVICE);
        return accessibilityManager != null && hasSystemBinders(accessibilityManager, "android.view.accessibility.IAccessibilityManager")
                && !accessibilityManager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_SPOKEN).isEmpty();
    }

    private static boolean hasSystemBinders(Object manager, String interfaceName) {
        ClassLoader bootLoader = Object.class.getClassLoader();
        ArrayList<Object> holders = new ArrayList<>();
        holders.add(manager);
        if (Build.VERSION.SDK_INT >= 34 && manager instanceof InputManager) {
            try {
                holders.add(Class.forName("android.hardware.input.InputManagerGlobal").getMethod("getInstance").invoke(null));
            } catch (Throwable ignore) {
            }
        }
        for (Object holder : holders) {
            if (holder == null) {
                continue;
            }
            for (Class<?> cls = holder.getClass(); cls != null && cls != Object.class; cls = cls.getSuperclass()) {
                Field[] fields;
                try {
                    fields = cls.getDeclaredFields();
                } catch (Throwable e) {
                    continue;
                }
                for (Field field : fields) {
                    if (Modifier.isStatic(field.getModifiers()) || !interfaceName.equals(field.getType().getName())) {
                        continue;
                    }
                    Object service;
                    try {
                        field.setAccessible(true);
                        service = field.get(holder);
                    } catch (Throwable e) {
                        continue;
                    }
                    if (service == null) {
                        continue;
                    }
                    Class<?> serviceClass = service.getClass();
                    if (!(interfaceName + "$Stub$Proxy").equals(serviceClass.getName()) || serviceClass.getClassLoader() != bootLoader) {
                        return false;
                    }
                    if (!(service instanceof IInterface)) {
                        return false;
                    }
                    IBinder binder = ((IInterface) service).asBinder();
                    if (binder == null || !"android.os.BinderProxy".equals(binder.getClass().getName()) || binder.getClass().getClassLoader() != bootLoader) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    public boolean merge(int account, int otherConstructor, long[] otherDetails, String[] otherTexts, Utilities.Callback<Boolean> onResult) {
        if (answered || !isShowing() || account != currentAccount || otherConstructor != constructor || !Arrays.equals(texts, otherTexts)) {
            return false;
        }
        long[] merged = mergeDetails(details, otherDetails != null ? otherDetails : new long[0]);
        if (merged == null) {
            return false;
        }
        details = merged;
        callbacks.add(onResult);
        subtitle.setText(TextUtils.isEmpty(category) ? String.format(Locale.US, "×%d", callbacks.size()) : String.format(Locale.US, "%s ×%d", category, callbacks.size()));
        subtitle.setVisibility(View.VISIBLE);
        tableView.clear();
        fillTable();
        allowButton.setEnabled(false);
        AndroidUtilities.cancelRunOnUIThread(enableAllowButton);
        AndroidUtilities.runOnUIThread(enableAllowButton, MERGE_ALLOW_DELAY);
        return true;
    }

    private static long[] mergeDetails(long[] current, long[] other) {
        if (current.length != other.length) {
            return null;
        }
        long[] merged = current.clone();
        for (int i = 0; i + 1 < current.length; i += 2) {
            if (current[i] != other[i]) {
                return null;
            }
            if (current[i] == DETAIL_STARS || current[i] == DETAIL_GRAMS) {
                try {
                    merged[i + 1] = Math.addExact(current[i + 1], other[i + 1]);
                } catch (ArithmeticException e) {
                    return null;
                }
            } else if (current[i + 1] != other[i + 1]) {
                return null;
            }
        }
        return merged;
    }

    public boolean isAnswered() {
        return answered;
    }

    public void bringToFront() {
        if (answered || !isShowing()) {
            return;
        }
        Window window = getWindow();
        if (window == null) {
            return;
        }
        View decor = window.getDecorView();
        ViewGroup.LayoutParams params = decor.getLayoutParams();
        if (!decor.isAttachedToWindow() || params == null) {
            return;
        }
        WindowManager windowManager = window.getWindowManager();
        try {
            windowManager.removeViewImmediate(decor);
            windowManager.addView(decor, params);
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private void finish(boolean approved) {
        if (answered) {
            return;
        }
        answered = true;
        for (Utilities.Callback<Boolean> callback : new ArrayList<>(callbacks)) {
            callback.run(approved);
        }
    }

    private void fillTable() {
        addPeerRow(tableView, getString(R.string.Account), UserConfig.getInstance(currentAccount).getClientUserId());
        for (int i = 0; i + 1 < details.length; i += 2) {
            addDetailRow(tableView, (int) details[i], details[i + 1], texts);
        }
        if (constructor == TL_stars.TL_payments_sendStarsForm.constructor || constructor == TLRPC.TL_payments_sendPaymentForm.constructor) {
            boolean hasForm = false;
            for (int i = 0; i + 1 < details.length; i += 2) {
                hasForm |= details[i] == DETAIL_FORM;
            }
            if (!hasForm) {
                tableView.addRow(getString(R.string.SensitiveRequestDebit), getString(R.string.SensitiveRequestUnknownAmount));
            }
        }
        if (!TextUtils.isEmpty(method)) {
            tableView.addRowMonospaced(getString(R.string.SensitiveRequestMethod), method, 13, null);
        }
    }

    private void addDetailRow(TableView tableView, int kind, long value, String[] texts) {
        switch (kind) {
            case DETAIL_FORM:
                CharSequence amount = getString(R.string.SensitiveRequestUnknownAmount);
                if (payment != null && "XTR".equals(payment.currency)) {
                    amount = StarsIntroActivity.replaceStars(false, "XTR " + LocaleController.formatNumber(payment.amount, ','), .85f);
                } else if (payment != null && "TON".equals(payment.currency)) {
                    amount = StarsIntroActivity.replaceStars(true, "XTR " + BigDecimal.valueOf(payment.amount, 9).stripTrailingZeros().toPlainString(), .85f);
                }
                tableView.addRow(getString(R.string.SensitiveRequestDebit), amount);
                break;
            case DETAIL_GIFT_ID:
                TL_stars.StarGift gift = StarsController.getInstance(currentAccount).getStarGift(value);
                tableView.addRow(getString(R.string.ActionStarGift), gift != null && !TextUtils.isEmpty(gift.title) ? gift.title + " · ID " + value : "ID " + value);
                break;
            case DETAIL_ANONYMOUS:
                tableView.addRow(getString(R.string.SensitiveRequestAnonymous), getString(value != 0 ? R.string.CheckPhoneNumberYes : R.string.CheckPhoneNumberNo));
                break;
            case DETAIL_UPGRADE:
                tableView.addRow(getString(R.string.SensitiveRequestUpgradeIncluded), getString(value != 0 ? R.string.CheckPhoneNumberYes : R.string.CheckPhoneNumberNo));
                break;
            case DETAIL_GIFT_MESSAGE:
                tableView.addRow(getString(R.string.SensitiveRequestGiftMessage), Long.toString(value));
                break;
            case DETAIL_SAVED_GIFT_ID:
                tableView.addRow(getString(R.string.SensitiveRequestSavedGift), Long.toString(value));
                break;
            case DETAIL_RECIPIENT:
                addPeerRow(tableView, getString(R.string.WalletRecipient), value);
                break;
            case DETAIL_CHAT:
                addPeerRow(tableView, getString(R.string.SensitiveRequestChat), value);
                break;
            case DETAIL_USER:
                addPeerRow(tableView, getString(R.string.SensitiveRequestUser), value);
                break;
            case DETAIL_BOT:
                addPeerRow(tableView, getString(R.string.StarsSubscriptionBot), value);
                break;
            case DETAIL_STARS:
                tableView.addRow(getString(R.string.SensitiveRequestAmount), StarsIntroActivity.replaceStars(false, "XTR " + LocaleController.formatNumber(value, ','), .85f));
                break;
            case DETAIL_GRAMS:
                tableView.addRow(getString(R.string.SensitiveRequestAmount), StarsIntroActivity.replaceStars(true, "XTR " + BigDecimal.valueOf(value, 9).stripTrailingZeros().toPlainString(), .85f));
                break;
            case DETAIL_GIFT:
                if (texts != null && value >= 0 && value < texts.length && !TextUtils.isEmpty(texts[(int) value])) {
                    tableView.addRow(getString(R.string.ActionStarGift), texts[(int) value]);
                }
                break;
        }
    }

    private void addPeerRow(TableView tableView, CharSequence title, long dialogId) {
        if (dialogId == 0) {
            dialogId = UserConfig.getInstance(currentAccount).getClientUserId();
        }
        if (tableView.addRowUser(title, currentAccount, dialogId, null) == null) {
            tableView.addRow(title, "ID " + dialogId);
        }
    }
}
