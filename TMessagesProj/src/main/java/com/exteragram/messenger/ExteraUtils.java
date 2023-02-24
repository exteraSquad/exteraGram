/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @immat0x1, 2023

*/

package com.exteragram.messenger;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.OvalShape;
import android.net.Uri;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.URLSpan;
import android.view.View;
import android.widget.LinearLayout;

import androidx.core.content.ContextCompat;

import com.exteragram.messenger.updater.UpdaterUtils;

import org.json.JSONArray;
import org.json.JSONTokener;
import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.DispatchQueue;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.RadioColorCell;
import org.telegram.ui.Components.CombinedDrawable;
import org.telegram.ui.Components.URLSpanNoUnderline;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Locale;

public class ExteraUtils {

    public static final DispatchQueue translateQueue = new DispatchQueue("translateQueue", false);

    public static Drawable drawFab() {
        return drawFab(false);
    }

    public static Drawable drawFab(boolean altColor) {
        int r = AndroidUtilities.dp(ExteraConfig.squareFab ? 16 : 100);
        int c = Theme.getColor(altColor ? Theme.key_dialogFloatingButton : Theme.key_chats_actionBackground);
        int pc = Theme.getColor(altColor ? Theme.key_dialogFloatingButtonPressed : Theme.key_chats_actionPressedBackground);
        return Theme.createSimpleSelectorRoundRectDrawable(r, c, pc);
    }

    public static String getDC(TLRPC.User user) {
        return getDC(user, null);
    }

    public static String getDC(TLRPC.Chat chat) {
        return getDC(null, chat);
    }

    public static String getDC(TLRPC.User user, TLRPC.Chat chat) {
        int DC = 0, myDC = AccountInstance.getInstance(UserConfig.selectedAccount).getConnectionsManager().getCurrentDatacenterId();
        if (user != null) {
            if (UserObject.isUserSelf(user) && myDC != -1) {
                DC = myDC;
            } else {
                DC = user.photo != null ? user.photo.dc_id : -1;
            }
        } else if (chat != null) {
            DC = chat.photo != null ? chat.photo.dc_id : -1;
        }
        if (DC == -1 || DC == 0) {
            return getDCName(0);
        } else {
            return String.format(Locale.ROOT, "DC%d, %s", DC, getDCName(DC));
        }
    }

    public static String getDCName(int dc) {
        switch (dc) {
            case 1:
            case 3:
                return "Miami FL, USA";
            case 2:
            case 4:
                return "Amsterdam, NL";
            case 5:
                return "Singapore, SG";
            default:
                return null;
        }
    }

    public static String getAppName() {
        try {
            return ApplicationLoader.applicationContext.getString(R.string.exteraAppName) + (BuildVars.isBetaApp() ? " β" : "");
        } catch (Exception e) {
            FileLog.e(e);
        }
        return "exteraGram";
    }

    public static boolean notSubbedTo(long id) {
        TLRPC.Chat chat = MessagesController.getInstance(UserConfig.selectedAccount).getChat(id);
        return chat == null || chat.left || chat.kicked;
    }

    public static int[] getDrawerIconPack() {
        switch (Theme.getEventType()) {
            case 0:
                return new int[]{
                        R.drawable.msg_groups_ny,
                        R.drawable.msg_secret_ny,
                        R.drawable.msg_channel_ny,
                        R.drawable.msg_contacts_ny,
                        R.drawable.msg_calls_ny,
                        R.drawable.msg_saved_ny,
                        R.drawable.msg_invite_ny,
                        R.drawable.msg_help_ny,
                        R.drawable.msg_nearby_ny
                };
            case 1:
                return new int[]{
                        R.drawable.msg_groups_14,
                        R.drawable.msg_secret_14,
                        R.drawable.msg_channel_14,
                        R.drawable.msg_contacts_14,
                        R.drawable.msg_calls_14,
                        R.drawable.msg_saved_14,
                        R.drawable.msg_invite_14,
                        R.drawable.msg_help_14,
                        R.drawable.msg_nearby_14
                };
            case 2:
                return new int[]{
                        R.drawable.msg_groups_hw,
                        R.drawable.msg_secret_hw,
                        R.drawable.msg_channel_hw,
                        R.drawable.msg_contacts_hw,
                        R.drawable.msg_calls_hw,
                        R.drawable.msg_saved_hw,
                        R.drawable.msg_invite_hw,
                        R.drawable.msg_help_hw,
                        R.drawable.msg_nearby_hw
                };
            default:
                return new int[]{
                        R.drawable.msg_groups,
                        R.drawable.msg_secret,
                        R.drawable.msg_channel,
                        R.drawable.msg_contacts,
                        R.drawable.msg_calls,
                        R.drawable.msg_saved,
                        R.drawable.msg_invite,
                        R.drawable.msg_help,
                        R.drawable.msg_nearby
                };
        }
    }

    public static int getNotificationIconColor() {
        return BuildVars.isBetaApp() ? 0xff747f9f : 0xfff54142;
    }

    public interface OnTranslationSuccess {
        void run(CharSequence translated);
    }

    public interface OnTranslationFail {
        void run();
    }

    public static void translate(CharSequence text, String target, OnTranslationSuccess onSuccess, OnTranslationFail onFail) {
        if (!translateQueue.isAlive()) {
            translateQueue.start();
        }
        translateQueue.postRunnable(() -> {
            String uri;
            HttpURLConnection connection;
            try {
                uri = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=";
                uri += Uri.encode(target);
                uri += "&dt=t&ie=UTF-8&oe=UTF-8&otf=1&ssel=0&tsel=0&kc=7&dt=at&dt=bd&dt=ex&dt=ld&dt=md&dt=qca&dt=rw&dt=rm&dt=ss&q=";
                uri += Uri.encode(text.toString());
                connection = (HttpURLConnection) new URI(uri).toURL().openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("User-Agent", UpdaterUtils.getRandomUserAgent());
                connection.setRequestProperty("Content-Type", "application/json");

                StringBuilder textBuilder = new StringBuilder();
                try (Reader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                    int c;
                    while ((c = reader.read()) != -1) textBuilder.append((char) c);
                }
                JSONTokener tokener = new JSONTokener(textBuilder.toString());
                JSONArray array = new JSONArray(tokener);
                JSONArray array1 = array.getJSONArray(0);
                StringBuilder result = new StringBuilder();
                for (int i = 0; i < array1.length(); ++i) {
                    String blockText = array1.getJSONArray(i).getString(0);
                    if (blockText != null && !blockText.equals("null"))
                        result.append(blockText);
                }
                if (text.length() > 0 && text.charAt(0) == '\n') result.insert(0, "\n");
                if (onSuccess != null)
                    AndroidUtilities.runOnUIThread(() -> onSuccess.run(result.toString()));
            } catch (Exception e) {
                e.printStackTrace();
                if (onFail != null)
                    AndroidUtilities.runOnUIThread(onFail::run);
            }
        });
    }

    public static String getActionBarTitle() {
        return getActionBarTitle(ExteraConfig.actionBarTitle);
    }

    public static String getActionBarTitle(int num) {
        TLRPC.User user = UserConfig.getInstance(UserConfig.selectedAccount).getCurrentUser();
        String title;
        if (num == 0) {
            title = LocaleController.getString("exteraAppName", R.string.exteraAppName);
        } else if (num == 1) {
            title = LocaleController.getString("SearchAllChatsShort", R.string.SearchAllChatsShort);
        } else if (num == 2) {
            if (!TextUtils.isEmpty(UserObject.getPublicUsername(user))) {
                title = UserObject.getPublicUsername(user);
            } else {
                title = UserObject.getFirstName(user);
            }
        } else {
            title = UserObject.getFirstName(user);
        }
        return title;
    }

    public static boolean hasGps() {
        boolean hasGps;
        try {
            hasGps = ApplicationLoader.applicationContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS);
        } catch (Throwable e) {
            hasGps = false;
        }
        return hasGps;
    }

    public static CombinedDrawable createCircleDrawableWithIcon(Context context, int iconRes, int size) {
        Drawable drawable = iconRes != 0 ? ContextCompat.getDrawable(context, iconRes).mutate() : null;
        OvalShape ovalShape = new OvalShape();
        ovalShape.resize(size, size);
        ShapeDrawable defaultDrawable = new ShapeDrawable(ovalShape);
        Paint paint = defaultDrawable.getPaint();
        paint.setColor(0xffffffff);
        CombinedDrawable combinedDrawable = new CombinedDrawable(defaultDrawable, drawable);
        combinedDrawable.setCustomSize(size, size);
        return combinedDrawable;
    }

    public static CharSequence formatWithUsernames(String text, BaseFragment fragment) {
        int start = -1, end;
        boolean parse = false;
        SpannableStringBuilder stringBuilder = new SpannableStringBuilder(text);
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '@') {
                start = i;
                parse = true;
            }
            if (parse && (i + 1 == text.length() || text.charAt(i + 1) == ' ')) {
                end = i + 1;
                parse = false;
                if (start != -1) {
                    String username = text.substring(start, end);
                    try {
                        URLSpanNoUnderline urlSpan = new URLSpanNoUnderline(username) {
                            @Override
                            public void onClick(View widget) {
                                MessagesController.getInstance(UserConfig.selectedAccount).openByUserName(username.substring(1), fragment, 1);
                            }
                        };
                        stringBuilder.setSpan(urlSpan, start, end, 0);
                        if (i + 1 == text.length()) {
                            return stringBuilder;
                        }
                    } catch (Exception e) {
                        FileLog.e(e);
                        return text;
                    }
                }
            }
        }
        return text;
    }

    public static CharSequence formatWithURLs(CharSequence charSequence) {
        Spannable spannable = new SpannableString(charSequence);
        URLSpan[] spans = spannable.getSpans(0, charSequence.length(), URLSpan.class);
        for (URLSpan urlSpan : spans) {
            URLSpan span = urlSpan;
            int start = spannable.getSpanStart(span), end = spannable.getSpanEnd(span);
            spannable.removeSpan(span);
            span = new URLSpanNoUnderline(span.getURL()) {
                @Override
                public void onClick(View widget) {
                    super.onClick(widget);
                }
            };
            spannable.setSpan(span, start, end, 0);
        }
        return spannable;
    }

    public static void showDialog(ArrayList<? extends CharSequence> items, String title, int selected, Context context, OnItemClickListener listener) {
        showDialog(items.stream().map(String::valueOf).toArray(CharSequence[]::new), null, title, selected, context, listener, null);
    }

    public static void showDialog(ArrayList<? extends CharSequence> items, int[] icons, String title, int selected, Context context, OnItemClickListener listener) {
        showDialog(items.stream().map(String::valueOf).toArray(CharSequence[]::new), icons, title, selected, context, listener, null);
    }

    public static void showDialog(CharSequence[] items, String title, int selected, Context context, OnItemClickListener listener) {
        showDialog(items, null, title, selected, context, listener, null);
    }

    public static void showDialog(CharSequence[] items, int[] icons, String title, int selected, Context context, OnItemClickListener listener) {
        showDialog(items, icons, title, selected, context, listener, null);
    }

    public static void showDialog(CharSequence[] items, int[] icons, String title, int selected, Context context, OnItemClickListener listener, Theme.ResourcesProvider resourcesProvider) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context, resourcesProvider);
        builder.setTitle(title);
        if (icons == null) {
            final LinearLayout linearLayout = new LinearLayout(context);
            linearLayout.setOrientation(LinearLayout.VERTICAL);
            builder.setView(linearLayout);
            for (int a = 0; a < items.length; a++) {
                RadioColorCell cell = new RadioColorCell(context);
                cell.setPadding(AndroidUtilities.dp(4), 0, AndroidUtilities.dp(4), 0);
                cell.setTag(a);
                cell.setCheckColor(Theme.getColor(Theme.key_radioBackground, resourcesProvider), Theme.getColor(Theme.key_dialogRadioBackgroundChecked, resourcesProvider));
                cell.setTextAndValue(items[a], selected == a);
                cell.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), Theme.RIPPLE_MASK_ALL));
                linearLayout.addView(cell);
                cell.setOnClickListener(v -> {
                    Integer which = (Integer) v.getTag();
                    builder.getDismissRunnable().run();
                    listener.onClick(which);
                });
            }
        } else {
            builder.setItems(items, icons, (dialog, which) -> {
                builder.getDismissRunnable().run();
                listener.onClick(which);
            });
            builder.create();
        }
        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
        builder.show();
    }

    public interface OnItemClickListener {
        void onClick(int i);
    }

    public static String getNavigationBarColorKey() {
        return SharedConfig.useLNavigation ? Theme.key_chat_messagePanelBackground : Theme.key_windowBackgroundGray;
    }
}