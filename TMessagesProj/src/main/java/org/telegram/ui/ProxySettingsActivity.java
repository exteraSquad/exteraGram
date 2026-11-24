/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui;

import org.telegram.messenger.FileLog;
import android.animation.ValueAnimator;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.method.PasswordTransformationMethod;
import android.transition.ChangeBounds;
import android.transition.Fade;
import android.transition.Transition;
import android.transition.TransitionManager;
import android.transition.TransitionSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;

import com.exteragram.messenger.ExteraConfig;
import com.exteragram.messenger.proxy.ProxyController;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.SvgHelper;
import org.telegram.messenger.Utilities;
import org.telegram.utils.proxy.WebProxyTransport;
import org.telegram.utils.proxy.ProxySettings;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.SimpleActionBarDoneButton;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeBind;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.RadioCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.OutlineTextContainerView;
import org.telegram.ui.Components.QRCodeBottomSheet;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SectionsScrollView;

import java.util.ArrayList;
import java.util.HashSet;

public class ProxySettingsActivity extends BaseFragment {

    private final static int TYPE_SOCKS5 = 0;
    private final static int TYPE_MTPROTO = 1;
    private final static int TYPE_WEB = 2;

    private final static int WEB_PROXY_PORT = 443;

    private final static int FIELD_NAME = 0;
    private final static int FIELD_IP = 1;
    private final static int FIELD_PORT = 2;
    private final static int FIELD_USER = 3;
    private final static int FIELD_PASSWORD = 4;
    private final static int FIELD_SECRET = 5;

    private EditTextBoldCursor[] inputFields;
    private OutlineTextContainerView[] inputFieldContainers;
    private SectionsScrollView scrollView;
    private LinearLayout linearLayout2;
    private FrameLayout inputFieldsSection;
    private LinearLayout inputFieldsContainer;
    private HeaderCell headerCell;
    private ShadowSectionCell[] sectionCell = new ShadowSectionCell[4];
    private TextInfoPrivacyCell sponsorInfoCell;
    private TextSettingsCell shareCell;
    private TextSettingsCell pasteCell;
    private SimpleActionBarDoneButton doneItem;
    private RadioCell[] typeCell = new RadioCell[3];
    private int currentType = -1;

    private int pasteType = -1;
    private String pasteString;
    private String[] pasteFields;

    private float shareDoneProgress = 1f;
    private float[] shareDoneProgressAnimValues = new float[2];
    private boolean shareDoneEnabled = true;
    private ValueAnimator shareDoneAnimator;

    private ClipboardManager clipboardManager;

    private boolean addingNewProxy;

    private SharedConfig.ProxyInfo currentProxyInfo;

    private boolean ignoreOnTextChange;

    private static final int done_button = 1;

    public static class TypeCell extends FrameLayout {

        private TextView textView;
        private ImageView checkImage;
        private boolean needDivider;

        public TypeCell(Context context) {
            super(context);

            setWillNotDraw(false);

            textView = new TextView(context);
            ThemeBind.textColor(textView, Theme.key_windowBackgroundWhiteBlackText);
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            textView.setLines(1);
            textView.setMaxLines(1);
            textView.setSingleLine(true);
            textView.setEllipsize(TextUtils.TruncateAt.END);
            textView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
            addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 23 + 48 : 21, 0, LocaleController.isRTL ? 21 : 23, 0));

            checkImage = new ImageView(context);
            ThemeBind.colorFilter(checkImage, Theme.key_featuredStickers_addedIcon, PorterDuff.Mode.MULTIPLY);
            checkImage.setImageResource(R.drawable.sticker_added);
            addView(checkImage, LayoutHelper.createFrame(19, 14, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.CENTER_VERTICAL, 21, 0, 21, 0));
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(50) + (needDivider ? 1 : 0), MeasureSpec.EXACTLY));
        }

        public void setValue(String name, boolean checked, boolean divider) {
            textView.setText(name);
            checkImage.setVisibility(checked ? VISIBLE : INVISIBLE);
            needDivider = divider;
        }

        public void setTypeChecked(boolean value) {
            checkImage.setVisibility(value ? VISIBLE : INVISIBLE);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (needDivider) {
                canvas.drawLine(LocaleController.isRTL ? 0 : AndroidUtilities.dp(20), getMeasuredHeight() - 1, getMeasuredWidth() - (LocaleController.isRTL ? AndroidUtilities.dp(20) : 0), getMeasuredHeight() - 1, Theme.dividerPaint);
            }
        }
    }

    public ProxySettingsActivity() {
        super();
        currentProxyInfo = new SharedConfig.ProxyInfo(ProxySettings.EMPTY);
        addingNewProxy = true;
    }

    public ProxySettingsActivity(SharedConfig.ProxyInfo proxyInfo) {
        super();
        currentProxyInfo = proxyInfo;
    }

    private ClipboardManager.OnPrimaryClipChangedListener clipChangedListener = this::updatePasteCell;

    @Override
    public void onResume() {
        super.onResume();
        AndroidUtilities.requestAdjustResize(getParentActivity(), classGuid);
        clipboardManager.addPrimaryClipChangedListener(clipChangedListener);
        updatePasteCell();
    }

    @Override
    public void onPause() {
        super.onPause();
        clipboardManager.removePrimaryClipChangedListener(clipChangedListener);
    }

    @Override
    protected boolean useSimpleActionBar() {
        return true;
    }

    @Override
    public View createView(Context context) {
        setHasOwnBackground(true);
        simpleActionBar.setTitle(LocaleController.getString(R.string.ProxyDetails));
        simpleActionBar.setBackButtonImage(R.drawable.ic_ab_back);
        if (parentLayout != null && parentLayout.isLayersLayout()) {
            simpleActionBar.setOccupyStatusBar(false);
        }

        simpleActionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == done_button) {
                    if (getParentActivity() == null) {
                        return;
                    }
                    String proxyName = inputFields[FIELD_NAME].getText().toString().trim();
                    String oldProxyLink = addingNewProxy ? null : currentProxyInfo.settings.getLink();
                    final boolean web = currentType == TYPE_WEB;
                    currentProxyInfo.settings = ProxySettings.builder()
                            .setType(ProxySettings.intToType(currentType))
                            .setAddress(inputFields[FIELD_IP].getText().toString())
                            .setPort(web ? 0 : Utilities.parseInt(inputFields[FIELD_PORT].getText().toString()))
                            .setUser(currentType == TYPE_SOCKS5 ? inputFields[FIELD_USER].getText().toString() : "")
                            .setPassword(currentType == TYPE_SOCKS5 ? inputFields[FIELD_PASSWORD].getText().toString() : "")
                            .setSecret(currentType == TYPE_SOCKS5 ? "" : inputFields[FIELD_SECRET].getText().toString())
                            .build();
                    ProxyController proxyController = ProxyController.getInstance();
                    SharedPreferences preferences = MessagesController.getGlobalMainSettings();
                    boolean enabled = addingNewProxy || preferences.getBoolean("proxy_enabled", false);
                    currentProxyInfo = proxyController.saveProxy(currentProxyInfo, oldProxyLink, proxyName);
                    if (addingNewProxy) {
                        proxyController.setCurrentProxy(currentProxyInfo);
                    }
                    if (addingNewProxy || proxyController.getCurrentProxy() == currentProxyInfo) {
                        SharedPreferences.Editor editor = preferences.edit();
                        editor.putBoolean("proxy_enabled", enabled);
                        currentProxyInfo.settings.toSharedPreferences(editor);
                        ConnectionsManager.setProxySettings(enabled, currentProxyInfo.settings);
                        editor.apply();
                    }

                    NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.proxySettingsChanged);

                    finishFragment();
                }
            }
        });

        doneItem = simpleActionBar.createDoneButton(done_button, LocaleController.getString(R.string.Save));

        fragmentView = new FrameLayout(context);
        FrameLayout frameLayout = (FrameLayout) fragmentView;

        linearLayout2 = new SectionsScrollView.SectionsLinearLayout(context);
        scrollView = new SectionsScrollView(context, linearLayout2, resourceProvider);
        scrollView.setDrawBackground(true);
        scrollView.setFillViewport(true);
        scrollView.setOverScrollMode(View.OVER_SCROLL_ALWAYS);
        AndroidUtilities.setScrollViewEdgeEffectColor(scrollView, Theme.getColor(Theme.key_actionBarDefault));
        frameLayout.addView(scrollView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        linearLayout2.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(linearLayout2, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        inputFields = new EditTextBoldCursor[6];

        final View.OnClickListener typeCellClickListener = view -> setProxyType((Integer) view.getTag(), true);

        for (int a = 0; a < typeCell.length; a++) {
            typeCell[a] = new RadioCell(context);
            ThemeBind.selectorBackground(typeCell[a], Theme.getSelectorDrawable(true), ThemeBind.of(Theme.key_windowBackgroundWhite), ThemeBind.of(Theme.key_listSelector));
            typeCell[a].setTag(a);
            final int title = a == TYPE_SOCKS5 ? R.string.UseProxySocks5
                    : a == TYPE_MTPROTO ? R.string.UseProxyTelegram
                    : R.string.UseProxyWeb;
            typeCell[a].setText(LocaleController.getString(title), a == currentType, a != typeCell.length - 1);
            linearLayout2.addView(typeCell[a], LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 50));
            typeCell[a].setOnClickListener(typeCellClickListener);
        }

        sectionCell[0] = new ShadowSectionCell(context);
        linearLayout2.addView(sectionCell[0], LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 14));

        inputFieldsSection = new FrameLayout(context);
        inputFieldsSection.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(8), AndroidUtilities.dp(12), 0);
        linearLayout2.addView(inputFieldsSection, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        inputFieldsContainer = new LinearLayout(context);
        inputFieldsContainer.setOrientation(LinearLayout.VERTICAL);
        inputFieldsContainer.setTag(RecyclerListView.TAG_NOT_SECTION);
        inputFieldsSection.addView(inputFieldsContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        sectionCell[3] = new ShadowSectionCell(context);
        linearLayout2.addView(sectionCell[3], LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 14));

        inputFieldContainers = new OutlineTextContainerView[6];
        for (int a = 0; a < 6; a++) {
            OutlineTextContainerView fieldContainer = new OutlineTextContainerView(context);
            inputFieldContainers[a] = fieldContainer;
            inputFields[a] = new EditTextBoldCursor(context);
            inputFields[a].setTag(a);
            inputFields[a].setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            ThemeBind.editHintColor(inputFields[a], Theme.key_windowBackgroundWhiteHintText);
            ThemeBind.textColor(inputFields[a], Theme.key_windowBackgroundWhiteBlackText);
            inputFields[a].setBackground(null);
            ThemeBind.cursorColor(inputFields[a], Theme.key_windowBackgroundWhiteBlackText);
            inputFields[a].setCursorSize(AndroidUtilities.dp(20));
            inputFields[a].setCursorWidth(1.5f);
            inputFields[a].setSingleLine(true);
            inputFields[a].setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);

            if (a == FIELD_NAME) {
                inputFields[a].setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
                inputFields[a].addTextChangedListener(new TextWatcher() {
                    @Override
                    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                    }

                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {
                    }

                    @Override
                    public void afterTextChanged(Editable s) {
                        updateActionBarTitle();
                        updateFieldContainerState(FIELD_NAME, inputFields[FIELD_NAME].hasFocus(), true);
                    }
                });
            } else if (a == FIELD_IP) {
                inputFields[a].setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS | InputType.TYPE_TEXT_VARIATION_URI);
                inputFields[a].addTextChangedListener(new TextWatcher() {
                    @Override
                    public void beforeTextChanged(CharSequence s, int start, int count, int after) {

                    }

                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {

                    }

                    @Override
                    public void afterTextChanged(Editable s) {
                        updateFieldContainerState(FIELD_IP, inputFields[FIELD_IP].hasFocus(), true);
                        checkShareDone(true);
                    }
                });
            } else if (a == FIELD_PORT) {
                inputFields[a].setInputType(InputType.TYPE_CLASS_NUMBER);
                inputFields[a].addTextChangedListener(new TextWatcher() {
                    @Override
                    public void beforeTextChanged(CharSequence s, int start, int count, int after) {

                    }

                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {

                    }

                    @Override
                    public void afterTextChanged(Editable s) {
                        if (ignoreOnTextChange) {
                            return;
                        }
                        EditText phoneField = inputFields[FIELD_PORT];
                        int start = phoneField.getSelectionStart();
                        String chars = "0123456789";
                        String str = phoneField.getText().toString();
                        StringBuilder builder = new StringBuilder(str.length());
                        for (int a = 0; a < str.length(); a++) {
                            String ch = str.substring(a, a + 1);
                            if (chars.contains(ch)) {
                                builder.append(ch);
                            }
                        }
                        ignoreOnTextChange = true;
                        boolean changed;
                        int port = Utilities.parseInt(builder.toString());
                        if (port < 0 || port > 65535 || !str.equals(builder.toString())) {
                            if (port < 0) {
                                phoneField.setText("0");
                            } else if (port > 65535) {
                                phoneField.setText("65535");
                            } else {
                                phoneField.setText(builder.toString());
                            }
                        } else {
                            if (start >= 0) {
                                phoneField.setSelection(Math.min(start, phoneField.length()));
                            }
                        }
                        ignoreOnTextChange = false;
                        updateFieldContainerState(FIELD_PORT, inputFields[FIELD_PORT].hasFocus(), true);
                        checkShareDone(true);
                    }
                });
            } else if (a == FIELD_PASSWORD) {
                inputFields[a].setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                inputFields[a].setTypeface(Typeface.DEFAULT);
                inputFields[a].setTransformationMethod(PasswordTransformationMethod.getInstance());
                inputFields[a].addTextChangedListener(new SimpleFieldTextWatcher(FIELD_PASSWORD));
            } else if (a == FIELD_SECRET) {
                inputFields[a].setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
                inputFields[a].setTypeface(Typeface.DEFAULT);
                inputFields[a].setTransformationMethod(PasswordTransformationMethod.getInstance());
                inputFields[a].addTextChangedListener(new SimpleFieldTextWatcher(FIELD_SECRET));
            } else {
                inputFields[a].setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
                inputFields[a].addTextChangedListener(new SimpleFieldTextWatcher(a));
            }
            inputFields[a].setImeOptions(EditorInfo.IME_ACTION_NEXT | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
            switch (a) {
                case FIELD_NAME:
                    fieldContainer.setText(LocaleController.getString(R.string.ProxyRename));
                    inputFields[a].setText(ProxyController.getInstance().getName(currentProxyInfo));
                    break;
                case FIELD_IP:
                    fieldContainer.setText(LocaleController.getString(R.string.UseProxyAddress));
                    inputFields[a].setText(currentProxyInfo.settings.getAddress());
                    break;
                case FIELD_PASSWORD:
                    fieldContainer.setText(LocaleController.getString(R.string.UseProxyPassword));
                    inputFields[a].setText(currentProxyInfo.settings.getPassword());
                    break;
                case FIELD_PORT:
                    fieldContainer.setText(LocaleController.getString(R.string.UseProxyPort));
                    inputFields[a].setText("" + currentProxyInfo.settings.getPort());
                    break;
                case FIELD_USER:
                    fieldContainer.setText(LocaleController.getString(R.string.UseProxyUsername));
                    inputFields[a].setText(currentProxyInfo.settings.getUser());
                    break;
                case FIELD_SECRET:
                    fieldContainer.setText(LocaleController.getString(R.string.UseProxySecret));
                    inputFields[a].setText(currentProxyInfo.settings.getSecret());
                    break;
            }
            inputFields[a].setSelection(inputFields[a].length());

            inputFields[a].setPadding(0, AndroidUtilities.dp(16), 0, AndroidUtilities.dp(16));
            fieldContainer.addView(inputFields[a], LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 16, 0, 16, 0));
            fieldContainer.attachEditText(inputFields[a]);
            inputFields[a].setOnFocusChangeListener((v, hasFocus) -> {
                int field = (Integer) v.getTag();
                updateFieldContainerState(field, hasFocus, true);
                if (field == FIELD_SECRET) {
                    updateSecretVisibility(hasFocus);
                }
            });
            updateFieldContainerState(a, false, false);

            inputFields[a].setOnEditorActionListener((textView, i, keyEvent) -> {
                if (i == EditorInfo.IME_ACTION_NEXT) {
                    int num = (Integer) textView.getTag();
                    if (num + 1 < inputFields.length) {
                        num++;
                        inputFields[num].requestFocus();
                    }
                    return true;
                } else if (i == EditorInfo.IME_ACTION_DONE) {
                    finishFragment();
                    return true;
                }
                return false;
            });
        }
        inputFieldsContainer.addView(inputFieldContainers[FIELD_NAME], LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 12));

        LinearLayout addressRow = new LinearLayout(context);
        addressRow.setOrientation(LinearLayout.HORIZONTAL);
        inputFieldsContainer.addView(addressRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 12));
        addressRow.addView(inputFieldContainers[FIELD_IP], LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, 0, 0, 8, 0));
        addressRow.addView(inputFieldContainers[FIELD_PORT], LayoutHelper.createLinear(112, LayoutHelper.WRAP_CONTENT, 8, 0, 0, 0));

        inputFieldsContainer.addView(inputFieldContainers[FIELD_USER], LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 12));
        inputFieldsContainer.addView(inputFieldContainers[FIELD_PASSWORD], LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 12));
        inputFieldsContainer.addView(inputFieldContainers[FIELD_SECRET], LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 12));
        for (int a = 0; a < inputFieldsContainer.getChildCount(); a++) {
            inputFieldsContainer.getChildAt(a).setTag(RecyclerListView.TAG_NOT_SECTION);
        }
        updateActionBarTitle();

        pasteCell = new TextSettingsCell(fragmentView.getContext());
        ThemeBind.selectorBackground(pasteCell, Theme.getSelectorDrawable(true), ThemeBind.of(Theme.key_windowBackgroundWhite), ThemeBind.of(Theme.key_listSelector));
        pasteCell.setText(LocaleController.getString(R.string.PasteFromClipboard), false);
        ThemeBind.textColor(pasteCell.getTextView(), Theme.key_windowBackgroundWhiteBlueText4);
        pasteCell.setOnClickListener(v -> {
            if (pasteType != -1) {
                for (int i = FIELD_NAME; i < pasteFields.length; i++) {
                    if (pasteType == TYPE_SOCKS5 && i == FIELD_SECRET) {
                        continue;
                    }
                    if (pasteType == TYPE_MTPROTO && (i == FIELD_USER || i == FIELD_PASSWORD)) {
                        continue;
                    }
                    if (pasteFields[i] != null) {
                        inputFields[i].setText(pasteFields[i]);
                    } else {
                        inputFields[i].setText(null);
                    }
                    updateFieldContainerState(i, inputFields[i].hasFocus(), false);
                }
                inputFields[FIELD_IP].setSelection(inputFields[FIELD_IP].length());
                setProxyType(pasteType, true, () -> {
                    AndroidUtilities.hideKeyboard(inputFieldsContainer.findFocus());
                    for (int i = FIELD_IP; i < pasteFields.length; i++) {
                        if (pasteType == TYPE_SOCKS5 && i != FIELD_SECRET) {
                            continue;
                        }
                        if (pasteType == TYPE_MTPROTO && i != FIELD_USER && i != FIELD_PASSWORD) {
                            continue;
                        }
                        inputFields[i].setText(null);
                        updateFieldContainerState(i, inputFields[i].hasFocus(), false);
                    }
                });
            }
        });
        linearLayout2.addView(pasteCell, 0, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        pasteCell.setVisibility(View.GONE);

        sectionCell[2] = new ShadowSectionCell(context);
        sectionCell[2].setVisibility(View.GONE);
        linearLayout2.addView(sectionCell[2], 1, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        shareCell = new TextSettingsCell(context);
        ThemeBind.selectorBackground(shareCell, Theme.getSelectorDrawable(true), ThemeBind.of(Theme.key_windowBackgroundWhite), ThemeBind.of(Theme.key_listSelector));
        shareCell.setText(LocaleController.getString(R.string.ShareFile), false);
        ThemeBind.textColor(shareCell.getTextView(), Theme.key_windowBackgroundWhiteBlueText4);
        linearLayout2.addView(shareCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        shareCell.setOnClickListener(v -> {
            String link = ProxyController.getInstance().buildShareLink(currentProxyInfo, inputFields[FIELD_NAME].getText().toString().trim());
            if (TextUtils.isEmpty(link)) {
                return;
            }
            QRCodeBottomSheet alert = new QRCodeBottomSheet(context, LocaleController.getString(R.string.ShareQrCode), link, LocaleController.getString(R.string.QRCodeLinkHelpProxy), true);
            Bitmap icon = SvgHelper.getBitmap(AndroidUtilities.readRes(R.raw.qr_dog), AndroidUtilities.dp(60), AndroidUtilities.dp(60), false);
            alert.setCenterImage(icon);
            showDialog(alert);
        });

        sponsorInfoCell = new TextInfoPrivacyCell(context);
        sponsorInfoCell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        sponsorInfoCell.setText(LocaleController.getString(R.string.UseProxyTelegramInfo2));
        sponsorInfoCell.setVisibility(View.GONE);
        linearLayout2.addView(sponsorInfoCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        sectionCell[1] = new ShadowSectionCell(context);
        sectionCell[1].setBackgroundDrawable(Theme.getThemedDrawableByKey(context, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
        linearLayout2.addView(sectionCell[1], LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        clipboardManager = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);

        shareDoneEnabled = true;
        shareDoneProgress = 1f;
        checkShareDone(false);

        currentType = -1;
        setProxyType(ProxySettings.typeToInt(currentProxyInfo.settings.getType()), false);

        pasteType = -1;
        pasteString = null;
        updatePasteCell();

        return fragmentView;
    }


    private void updateSecretVisibility(boolean visible) {
        if (inputFields == null || inputFields[FIELD_SECRET] == null) {
            return;
        }
        int selection = inputFields[FIELD_SECRET].getSelectionStart();
        inputFields[FIELD_SECRET].setTransformationMethod(visible ? null : PasswordTransformationMethod.getInstance());
        inputFields[FIELD_SECRET].setSelection(Math.max(0, Math.min(selection, inputFields[FIELD_SECRET].length())));
    }

    private void updateFieldContainerState(int field, boolean focused, boolean animated) {
        if (inputFieldContainers == null || inputFields == null || field < 0 || field >= inputFieldContainers.length || inputFieldContainers[field] == null || inputFields[field] == null) {
            return;
        }
        boolean hasText = inputFields[field].getText() != null && inputFields[field].length() > 0;
        inputFieldContainers[field].animateSelection(focused ? 1f : 0f, (focused || hasText) ? 1f : 0f, animated);
    }

    private class SimpleFieldTextWatcher implements TextWatcher {
        private final int field;

        private SimpleFieldTextWatcher(int field) {
            this.field = field;
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }

        @Override
        public void afterTextChanged(Editable s) {
            updateFieldContainerState(field, inputFields[field].hasFocus(), true);
        }
    }

    private void updatePasteCell() {
        final ClipData clip = clipboardManager.getPrimaryClip();

        String clipText;
        if (clip != null && clip.getItemCount() > 0) {
            try {
                clipText = clip.getItemAt(0).coerceToText(fragmentView.getContext()).toString();
            } catch (Exception e) {
                clipText = null;
            }
        } else {
            clipText = null;
        }

        if (TextUtils.equals(clipText, pasteString)) {
            return;
        }

        pasteType = -1;
        pasteString = clipText;
        pasteFields = new String[inputFields.length];
        if (clipText != null) {
            Uri proxyUri = getProxyUriFromText(clipText);
            if (proxyUri != null) {
                String path = proxyUri.getPath();
                String host = proxyUri.getHost();
                if (TextUtils.equals(host, "socks") || TextUtils.equals(path, "/socks")) {
                    pasteType = TYPE_SOCKS5;
                } else if (TextUtils.equals(host, "proxy") || TextUtils.equals(path, "/proxy")) {
                    pasteType = TYPE_MTPROTO;
                } else if (TextUtils.equals(host, "webproxy") || TextUtils.equals(path, "/webproxy")) {
                    pasteType = TYPE_WEB;
                }
                if (pasteType != -1) {
                    pasteFields[FIELD_NAME] = proxyUri.getQueryParameter("title");
                    pasteFields[FIELD_IP] = proxyUri.getQueryParameter("server");
                    pasteFields[FIELD_PORT] = proxyUri.getQueryParameter("port");
                    if (pasteType == TYPE_SOCKS5) {
                        pasteFields[FIELD_USER] = proxyUri.getQueryParameter("user");
                        pasteFields[FIELD_PASSWORD] = proxyUri.getQueryParameter("pass");
                    } else {
                        pasteFields[FIELD_SECRET] = proxyUri.getQueryParameter("secret");
                    }
                    if (pasteType == TYPE_WEB) {
                        pasteFields[FIELD_PORT] = String.valueOf(WEB_PROXY_PORT);
                    }
                }
            }
        }

        if (pasteType != -1) {
            if (pasteCell.getVisibility() != View.VISIBLE) {
                pasteCell.setVisibility(View.VISIBLE);
                sectionCell[2].setVisibility(View.VISIBLE);
            }
        } else {
            if (pasteCell.getVisibility() != View.GONE) {
                pasteCell.setVisibility(View.GONE);
                sectionCell[2].setVisibility(View.GONE);
            }
        }
    }

    private Uri getProxyUriFromText(String text) {
        String[] markers = {"https://t.me/socks?", "http://t.me/socks?", "t.me/socks?", "tg://socks?", "https://t.me/proxy?", "http://t.me/proxy?", "t.me/proxy?", "tg://proxy?"};
        for (String marker : markers) {
            int index = text.indexOf(marker);
            if (index >= 0) {
                String link = text.substring(index).trim();
                int spaceIndex = link.indexOf(' ');
                if (spaceIndex >= 0) {
                    link = link.substring(0, spaceIndex);
                }
                if (link.startsWith("t.me/")) {
                    link = "https://" + link;
                }
                return Uri.parse(link);
            }
        }
        return null;
    }

    private void setShareDoneEnabled(boolean enabled, boolean animated) {
        if (shareDoneEnabled != enabled) {
            if (shareDoneAnimator != null) {
                shareDoneAnimator.cancel();
            } else if (animated) {
                shareDoneAnimator = ValueAnimator.ofFloat(0f, 1f);
                shareDoneAnimator.setDuration(200);
                shareDoneAnimator.addUpdateListener(a -> {
                    shareDoneProgress = AndroidUtilities.lerp(shareDoneProgressAnimValues, a.getAnimatedFraction());
                    shareCell.setTextColor(ColorUtils.blendARGB(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2), Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4), shareDoneProgress));
                });
            }
            if (animated) {
                shareDoneProgressAnimValues[0] = shareDoneProgress;
                shareDoneProgressAnimValues[1] = enabled ? 1f : 0f;
                shareDoneAnimator.start();
            } else {
                shareDoneProgress = enabled ? 1f : 0f;
                ThemeBind.textColor(shareCell.getTextView(), enabled ? Theme.key_windowBackgroundWhiteBlueText4 : Theme.key_windowBackgroundWhiteGrayText2);
            }
            shareCell.setEnabled(enabled);
            doneItem.setActive(enabled, animated);
            shareDoneEnabled = enabled;
        }
    }

    private void checkShareDone(boolean animated) {
        if (shareCell == null || doneItem == null || inputFields[FIELD_IP] == null || inputFields[FIELD_PORT] == null) {
            return;
        }
        if (currentType == TYPE_WEB) {
            setShareDoneEnabled(
                    !TextUtils.isEmpty(WebProxyTransport.normalizeHost(inputFields[FIELD_IP].getText().toString()))
                            && WebProxyTransport.isValidSecret(inputFields[FIELD_SECRET].getText().toString()),
                    animated);
            return;
        }
        setShareDoneEnabled(inputFields[FIELD_IP].length() != 0 && Utilities.parseInt(inputFields[FIELD_PORT].getText().toString()) != 0, animated);
    }

    private void updateActionBarTitle() {
        if (actionBar == null) {
            return;
        }
        String name = null;
        if (inputFields != null && inputFields.length > FIELD_NAME && inputFields[FIELD_NAME] != null) {
            name = inputFields[FIELD_NAME].getText().toString().trim();
        } else if (currentProxyInfo != null) {
            name = ProxyController.getInstance().getName(currentProxyInfo);
        }
        simpleActionBar.setTitle(TextUtils.isEmpty(name) ? LocaleController.getString(R.string.ProxyDetails) : name);
    }

    private void setProxyType(int type, boolean animated) {
        setProxyType(type, animated, null);
    }

    private void setProxyType(int type, boolean animated, Runnable onTransitionEnd) {
        if (currentType != type) {
            currentType = type;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                TransitionManager.endTransitions(linearLayout2);
            }
            if (animated) {
                final TransitionSet transitionSet = new TransitionSet()
                        .addTransition(new Fade(Fade.OUT))
                        .addTransition(new ChangeBounds())
                        .addTransition(new Fade(Fade.IN))
                        .setInterpolator(CubicBezierInterpolator.DEFAULT)
                        .setDuration(250);

                if (onTransitionEnd != null) {
                    transitionSet.addListener(new Transition.TransitionListener() {
                        @Override
                        public void onTransitionStart(Transition transition) {
                        }

                        @Override
                        public void onTransitionEnd(Transition transition) {
                            onTransitionEnd.run();
                        }

                        @Override
                        public void onTransitionCancel(Transition transition) {
                        }

                        @Override
                        public void onTransitionPause(Transition transition) {
                        }

                        @Override
                        public void onTransitionResume(Transition transition) {
                        }
                    });
                }

                TransitionManager.beginDelayedTransition(linearLayout2, transitionSet);
            }
            final boolean web = currentType == TYPE_WEB;
            if (currentType == TYPE_SOCKS5) {
                sponsorInfoCell.setVisibility(View.GONE);
                inputFieldContainers[FIELD_SECRET].setVisibility(View.GONE);
                inputFieldContainers[FIELD_PASSWORD].setVisibility(View.VISIBLE);
                inputFieldContainers[FIELD_USER].setVisibility(View.VISIBLE);
            } else {
                sponsorInfoCell.setVisibility(web ? View.GONE : View.VISIBLE);
                inputFieldContainers[FIELD_SECRET].setVisibility(View.VISIBLE);
                inputFieldContainers[FIELD_PASSWORD].setVisibility(View.GONE);
                inputFieldContainers[FIELD_USER].setVisibility(View.GONE);
            }
            inputFieldContainers[FIELD_PORT].setVisibility(web ? View.GONE : View.VISIBLE);
            if (web) {
                inputFields[FIELD_PORT].setText(String.valueOf(WEB_PROXY_PORT));
            }
            for (int a = 0; a < typeCell.length; a++) {
                typeCell[a].setChecked(currentType == a, animated);
            }

            if (inputFieldContainers[FIELD_IP] != null) {
                LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) inputFieldContainers[FIELD_IP].getLayoutParams();
                if (lp != null) {
                    int rightMargin = web ? 0 : AndroidUtilities.dp(8);
                    if (lp.rightMargin != rightMargin) {
                        lp.rightMargin = rightMargin;
                        inputFieldContainers[FIELD_IP].setLayoutParams(lp);
                    }
                }
            }

            checkShareDone(animated);
        }
    }

    @Override
    public void onTransitionAnimationEnd(boolean isOpen, boolean backward) {
        if (isOpen && !backward && addingNewProxy) {
            inputFields[FIELD_NAME].requestFocus();
            AndroidUtilities.showKeyboard(inputFields[FIELD_NAME]);
        }
    }

    @Override
    public boolean isSupportEdgeToEdge() {
        return true;
    }

    @Nullable
    @Override
    protected SectionsScrollView getScrollViewForSimpleGlass() {
        return scrollView;
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        final ThemeDescription.ThemeDescriptionDelegate delegate = this::delegateThemeColors;
        ArrayList<ThemeDescription> arrayList = new ArrayList<>();
        arrayList.add(new ThemeDescription(scrollView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_actionBarDefault));




        for (int a = 0; a < typeCell.length; a++) {
        }

        if (inputFields != null) {
            for (int a = 0; a < inputFields.length; a++) {
                arrayList.add(new ThemeDescription(inputFields[a], ThemeDescription.FLAG_HINTTEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteHintText));
                arrayList.add(new ThemeDescription(inputFields[a], ThemeDescription.FLAG_HINTTEXTCOLOR | ThemeDescription.FLAG_PROGRESSBAR, null, null, null, null, Theme.key_windowBackgroundWhiteBlueHeader));
            }
            arrayList.add(new ThemeDescription(null, 0, null, null, null, delegate, Theme.key_windowBackgroundWhiteHintText));
            arrayList.add(new ThemeDescription(null, 0, null, null, null, delegate, Theme.key_windowBackgroundWhiteInputField));
            arrayList.add(new ThemeDescription(null, 0, null, null, null, delegate, Theme.key_windowBackgroundWhiteInputFieldActivated));
            arrayList.add(new ThemeDescription(null, 0, null, null, null, delegate, Theme.key_text_RedBold));
        } else {
            arrayList.add(new ThemeDescription(null, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
            arrayList.add(new ThemeDescription(null, ThemeDescription.FLAG_HINTTEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteHintText));
        }
        arrayList.add(new ThemeDescription(headerCell, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundWhite));
        for (int a = 0; a < sectionCell.length; a++) {
            if (sectionCell[a] != null) {
            }
        }


        return arrayList;
    }

    @Override
    public void onThemeColorsChanged() {
        try {
            super.onThemeColorsChanged();
            // line colors follow through their ThemeBind bindings (setLineColors would reset padding / load a drawable every frame)
            if (shareCell != null && (shareDoneAnimator == null || !shareDoneAnimator.isRunning())) {
                ThemeBind.textColor(shareCell.getTextView(), shareDoneEnabled ? Theme.key_windowBackgroundWhiteBlueText4 : Theme.key_windowBackgroundWhiteGrayText2);
            }
            updateThemedColors();
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private int themedGlowColor;

    private void updateThemedColors() {
        try {
            if (scrollView != null) {
                final int glowColor = Theme.getColor(Theme.key_actionBarDefault);
                if (glowColor != themedGlowColor) {
                    themedGlowColor = glowColor;
                    AndroidUtilities.setScrollViewEdgeEffectColor(scrollView, glowColor);
                }
            }
            if (inputFields != null) {
                final int hintColor = Theme.getColor(Theme.key_windowBackgroundWhiteHintText);
                final int cursorColor = Theme.getColor(Theme.key_windowBackgroundWhiteBlackText);
                final int headerHintColor = Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader);
                for (int i = 0; i < inputFields.length; i++) {
                    if (inputFields[i] == null) continue;
                    ThemeBind.editHintColor(inputFields[i], Theme.key_windowBackgroundWhiteHintText);
                    ThemeBind.cursorColor(inputFields[i], Theme.key_windowBackgroundWhiteBlackText);
                    ThemeBind.editHeaderHintColor(inputFields[i], Theme.key_windowBackgroundWhiteBlueHeader);
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private void delegateThemeColors() {
        if (shareCell != null && (shareDoneAnimator == null || !shareDoneAnimator.isRunning())) {
            ThemeBind.textColor(shareCell.getTextView(), shareDoneEnabled ? Theme.key_windowBackgroundWhiteBlueText4 : Theme.key_windowBackgroundWhiteGrayText2);
        }
        if (inputFieldContainers != null) {
            for (OutlineTextContainerView inputFieldContainer : inputFieldContainers) {
                if (inputFieldContainer != null) {
                    inputFieldContainer.updateColor();
                }
            }
        }
    }

    /** theme editor: colors this page shows (former ThemeDescription keys) */
    @Override
    public void addThemeColorKeys(HashSet<Integer> keys) {
        super.addThemeColorKeys(keys);
        keys.add(Theme.key_windowBackgroundWhiteLinkText);
        keys.add(Theme.key_windowBackgroundWhiteGrayText4);
        keys.add(Theme.key_windowBackgroundGrayShadow);
        keys.add(Theme.key_radioBackgroundChecked);
        keys.add(Theme.key_radioBackground);
        keys.add(Theme.key_divider);
        keys.add(Theme.key_actionBarDefaultSelector);
        keys.add(Theme.key_actionBarDefaultTitle);
        keys.add(Theme.key_actionBarDefaultIcon);
        keys.add(Theme.key_actionBarDefault);
    }
}
