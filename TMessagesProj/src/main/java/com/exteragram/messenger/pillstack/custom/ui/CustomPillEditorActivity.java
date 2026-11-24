/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.pillstack.custom.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.core.graphics.Insets;
import androidx.core.view.WindowInsetsCompat;

import com.exteragram.messenger.pillstack.custom.CustomPill;
import com.exteragram.messenger.pillstack.custom.CustomPillFetcher;
import com.exteragram.messenger.pillstack.custom.CustomPillResult;
import com.exteragram.messenger.pillstack.custom.CustomPillSpec;
import com.exteragram.messenger.pillstack.custom.CustomPillStore;
import com.exteragram.messenger.pillstack.custom.JsonPath;
import com.exteragram.messenger.pillstack.custom.PillColors;
import com.exteragram.messenger.pillstack.custom.PillIconBadgeView;
import com.exteragram.messenger.pillstack.custom.PillTemplate;
import com.exteragram.messenger.preferences.BasePreferencesActivity;
import com.exteragram.messenger.utils.ui.AddButtonDrawable;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BotWebViewVibrationEffect;
import org.telegram.messenger.LiteMode;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.EditTextCell;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.FragmentSearchField;
import org.telegram.ui.Components.IconBackgroundColors;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.ScaleStateListAnimator;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.blur3.BlurredBackgroundDrawableViewFactory;
import org.telegram.ui.Components.blur3.drawable.color.BlurredBackgroundProvider;
import org.telegram.ui.Components.blur3.drawable.color.BlurredBackgroundProviderBuilder;
import org.telegram.ui.Components.blur3.drawable.color.impl.BlurredBackgroundProviderImpl;
import org.telegram.ui.Stories.recorder.Weather;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CustomPillEditorActivity extends BasePreferencesActivity {

    private static final int MENU_DONE = 1;

    private static final int ITEM_SOURCE_NONE = 2;
    private static final int ITEM_SOURCE_HTTP = 3;
    private static final int ITEM_METHOD = 4;
    private static final int ITEM_HEADERS = 5;
    private static final int ITEM_SECRETS = 6;
    private static final int ITEM_REFRESH = 7;
    private static final int ITEM_RUN = 8;
    private static final int ITEM_ADD_RULE = 9;
    private static final int ITEM_BROWSE = 10;
    private static final int ITEM_DELETE = 12;
    private static final int ITEM_TAP = 20;
    private static final int ITEM_RULE = 100;

    private static final int MAX_AUTO_EXPAND = 20;
    private static final Pattern TOKEN = Pattern.compile("\\{[^{}]+\\}");

    private final int pillId;
    private final boolean creatingNew;
    private final CustomPillSpec spec;
    private final HashMap<String, String> secrets;
    private final String initialState;
    private boolean openSecretsOnStart;
    private boolean autoRun;

    @Nullable
    private CustomPillFetcher.Response response;
    @Nullable
    private String responseKey;
    private boolean running;
    private int requestId;
    private final HashSet<String> expanded = new HashSet<>();

    private CustomPill previewPill;
    private boolean responseOutdated;
    private EditTextCell nameCell;
    private EditTextCell urlCell;
    private EditTextCell bodyCell;
    private EditTextCell textCell;
    private EditTextCell linkCell;
    private PillIconBadgeView nameIconView;
    private PillColorGrid colorGrid;
    private PillFlowLayout chipsView;
    private LinearLayout textContainer;
    private boolean textContainerDivider;
    private boolean browseAfterRun;
    @Nullable
    private AddButtonDrawable browseDrawable;
    @Nullable
    private AddButtonDrawable addRuleDrawable;
    private boolean chipsHttp;
    private int keyboardInset;
    private int shift = -4;
    private final Runnable updateListRunnable = () -> {
        if (listView != null && listView.adapter != null) {
            listView.adapter.update(true);
        }
    };

    public CustomPillEditorActivity(int pillId) {
        this.pillId = pillId;
        this.creatingNew = false;
        CustomPillSpec stored = CustomPillStore.get(pillId);
        this.spec = stored != null ? stored.copy() : new CustomPillSpec();
        this.secrets = CustomPillStore.getSecrets(pillId);
        this.response = CustomPillStore.getResponse(pillId);
        if (response != null) {
            responseKey = requestKey();
            autoExpand(response.json);
        }
        this.initialState = state();
    }

    public CustomPillEditorActivity(CustomPillSpec template) {
        this.pillId = 0;
        this.creatingNew = true;
        this.spec = template.copy();
        this.spec.id = 0;
        this.secrets = new HashMap<>();
        this.initialState = state();
        this.openSecretsOnStart = spec.http && !spec.secrets.isEmpty();
        this.autoRun = spec.http && spec.secrets.isEmpty() && !CustomPillFetcher.needsLocation(spec) && CustomPillSpec.isValidUrl(spec.url);
    }

    public void setOpenSecretsOnStart(boolean open) {
        openSecretsOnStart = open;
    }

    private String state() {
        return spec.toJson(false).toString() + "|" + new TreeMap<>(secrets);
    }

    private String requestKey() {
        return CustomPillStore.requestSignature(spec) + "|" + new TreeMap<>(secrets);
    }

    private boolean hasChanges() {
        return !state().equals(initialState);
    }

    @Override
    public String getTitle() {
        return LocaleController.getString(creatingNew ? R.string.CustomPillNew : R.string.CustomPillEditTitle);
    }

    @Override
    public WindowInsetsCompat onInsetsInternal(@NonNull View view, @NonNull WindowInsetsCompat windowInsets) {
        Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars() | WindowInsetsCompat.Type.statusBars());
        keyboardInset = Math.max(0, windowInsets.getInsets(WindowInsetsCompat.Type.ime()).bottom - bars.bottom);
        return super.onInsetsInternal(view, windowInsets);
    }

    @Override
    public void onResume() {
        super.onResume();
        Bulletin.addDelegate(this, new Bulletin.Delegate() {
            @Override
            public int getTopOffset(int tag) {
                return AndroidUtilities.statusBarHeight + ActionBar.getCurrentActionBarHeight() + getPinnedHeaderOffset();
            }

            @Override
            public int getBottomOffset(int tag) {
                return getBottomInset() + keyboardInset;
            }
        });
    }

    @Override
    public void onInsets(int left, int top, int right, int bottom) {
        super.onInsets(left, top, right, bottom + keyboardInset);
        Bulletin.updateCurrentPosition();
        if (keyboardInset > 0 && listView != null) {
            listView.post(() -> {
                View focused = listView.findFocus();
                if (focused != null) {
                    focused.requestRectangleOnScreen(new Rect(0, 0, focused.getWidth(), focused.getHeight()), false);
                }
            });
        }
    }

    @Override
    protected View createPinnedHeader(@NonNull Context context, @NonNull BlurredBackgroundDrawableViewFactory glassFactory) {
        FragmentSearchField searchField = new FragmentSearchField(context, getResourceProvider());
        searchField.setPadding(dp(4), dp(4), dp(4), dp(4));
        BlurredBackgroundProvider searchBackground = new BlurredBackgroundProviderBuilder(getResourceProvider())
                .setBackgroundColor((r, isDark) -> {
                    int fieldColor = ColorUtils.compositeColors(Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, r), isDark ? 0.07f : 0.05f), Theme.getColor(Theme.key_windowBackgroundWhite, r));
                    float alpha = LiteMode.isEnabled(LiteMode.FLAG_LIQUID_GLASS) ? 0.85f : 0.76f;
                    return BlurredBackgroundProviderImpl.solveSrcColor(Theme.getColor(Theme.key_windowBackgroundGray, r), fieldColor, alpha);
                })
                .setStrokeColorTop(0x11000000, 0x06FFFFFF)
                .setStrokeColorBottom(0x20000000, 0x11FFFFFF)
                .setShadowColor(0x20000000, 0x04FFFFFF)
                .setShadowLayer(AndroidUtilities.dpf2(2.667f), 0, AndroidUtilities.dpf2(0.85f))
                .setStrokeWidth(AndroidUtilities.dpf2(0.4f), AndroidUtilities.dpf2(0.4f))
                .build();
        searchField.setupBlurredBackground(glassFactory.create(searchField, searchBackground));
        searchField.setBlurredBackgroundVisibility(1f);
        searchField.setClickable(true);
        searchField.editText.setEnabled(false);
        searchField.editText.setHint(LocaleController.getString(R.string.SearchChats));
        FrameLayout pillContainer = new FrameLayout(context);
        pillContainer.setClipChildren(false);
        searchField.addView(pillContainer, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT, 6, 0, 6, 0));
        previewPill = new CustomPill(context, getResourceProvider(), spec);
        pillContainer.addView(previewPill);
        searchField.setPillHost(pillContainer);
        return searchField;
    }

    @Override
    public View createView(Context context) {
        createFields(context);
        View view = super.createView(context);
        actionBar.setAllowOverlayTitle(true);
        actionBar.createMenu().addItem(MENU_DONE, R.drawable.ic_ab_done).setContentDescription(LocaleController.getString(R.string.Save));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    if (checkDiscard(true)) {
                        finishFragment();
                    }
                } else if (id == MENU_DONE) {
                    save();
                }
            }
        });
        updatePreview(false);
        return view;
    }

    @Override
    public void onTransitionAnimationEnd(boolean isOpen, boolean backward) {
        super.onTransitionAnimationEnd(isOpen, backward);
        if (!isOpen || backward) {
            return;
        }
        if (openSecretsOnStart) {
            openSecretsOnStart = false;
            openKeyValueSheet(PillKeyValueSheet.MODE_SECRETS);
        } else if (autoRun) {
            autoRun = false;
            runRequest();
        }
    }

    @Override
    public void onFragmentDestroy() {
        requestId++;
        AndroidUtilities.cancelRunOnUIThread(updateListRunnable);
        super.onFragmentDestroy();
    }

    @Override
    public boolean onBackPressed(boolean invoked) {
        return checkDiscard(invoked);
    }

    @Override
    public boolean canBeginSlide() {
        return !hasChanges();
    }

    private boolean checkDiscard(boolean invoked) {
        if (!hasChanges()) {
            return true;
        }
        if (invoked && getParentActivity() != null) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity(), getResourceProvider());
            builder.setTitle(LocaleController.getString(creatingNew ? R.string.CustomPillDiscardNewTitle : R.string.UnsavedChanges));
            builder.setMessage(LocaleController.getString(creatingNew ? R.string.CustomPillDiscardNewText : R.string.CustomPillDiscardText));
            builder.setPositiveButton(LocaleController.getString(R.string.Save), (dialog, which) -> save());
            builder.setNegativeButton(LocaleController.getString(R.string.Discard), (dialog, which) -> finishFragment());
            showDialog(builder.create());
        }
        return false;
    }

    private void createFields(Context context) {
        nameCell = new PillEditTextCell(context, LocaleController.getString(R.string.CustomPillNameHint), false, CustomPillSpec.MAX_NAME, 8, getResourceProvider()) {
            @Override
            protected void onTextChanged(CharSequence newText) {
                spec.name = newText.toString().trim();
            }
        };
        nameCell.setText(spec.name);
        nameCell.hideKeyboardOnEnter();
        nameCell.editText.setLayoutParams(LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP, LocaleController.isRTL ? 0 : 44, 0, LocaleController.isRTL ? 44 : 0, 0));

        FrameLayout nameIconButton = new FrameLayout(context);
        nameIconButton.setBackground(Theme.createSelectorDrawable(getThemedColor(Theme.key_listSelector)));
        nameIconButton.setContentDescription(LocaleController.getString(R.string.CustomPillIcon));
        nameIconButton.setOnClickListener(v -> new PillIconPickerSheet(this, spec.icon, null, icon -> {
            if (icon != null) {
                spec.icon = icon;
                updateNameIcon();
                updatePreview(true);
            }
        }).show());
        nameCell.addView(nameIconButton, LayoutHelper.createFrame(48, 48, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL, 8, 0, 8, 0));
        nameIconView = new PillIconBadgeView(context, getResourceProvider(), false);
        nameIconButton.addView(nameIconView, LayoutHelper.createFrame(28, 28, Gravity.CENTER));
        updateNameIcon();

        colorGrid = new PillColorGrid(context, getResourceProvider(), false, color -> {
            spec.color = color;
            updateNameIcon();
            updatePreview(true);
        });
        colorGrid.setSelectedColor(spec.color);

        urlCell = new PillEditTextCell(context, LocaleController.getString(R.string.CustomPillUrlHint), true, CustomPillSpec.MAX_URL, 200, getResourceProvider()) {
            @Override
            protected void onTextChanged(CharSequence newText) {
                spec.url = newText.toString().trim();
                updatePreview(false);
            }
        };
        urlCell.editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        urlCell.editText.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MONO));
        urlCell.editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        disallowNewLines(urlCell);
        urlCell.setText(spec.url);

        bodyCell = new PillEditTextCell(context, LocaleController.getString(R.string.CustomPillBodyHint), true, CustomPillSpec.MAX_BODY, 400, getResourceProvider()) {
            @Override
            protected void onTextChanged(CharSequence newText) {
                spec.body = newText.toString();
            }
        };
        bodyCell.editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        bodyCell.editText.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MONO));
        bodyCell.editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        bodyCell.setText(spec.body);

        textCell = new PillEditTextCell(context, LocaleController.getString(R.string.CustomPillTextHint), true, CustomPillSpec.MAX_TEXT, 60, getResourceProvider()) {
            @Override
            protected void onTextChanged(CharSequence newText) {
                spec.text = newText.toString();
                if (newText instanceof Editable editable) {
                    highlightTokens(editable);
                }
                updatePreview(false);
                scheduleListUpdate();
            }
        };
        textCell.editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        disallowNewLines(textCell);
        textCell.setText(spec.text);
        highlightTokens(textCell.editText.getText());

        linkCell = new PillEditTextCell(context, LocaleController.getString(R.string.CustomPillLinkHint), false, CustomPillSpec.MAX_URL, 200, getResourceProvider()) {
            @Override
            protected void onTextChanged(CharSequence newText) {
                spec.link = newText.toString().trim();
            }
        };
        linkCell.editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        linkCell.editText.setImeOptions(EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        linkCell.hideKeyboardOnEnter();
        linkCell.setText(spec.link);

        chipsView = new PillFlowLayout(context, dp(8), dp(8));
        chipsView.setPadding(dp(21), dp(2), dp(21), dp(14));
        rebuildChips();

        textContainer = new LinearLayout(context) {
            @Override
            protected void dispatchDraw(@NonNull Canvas canvas) {
                super.dispatchDraw(canvas);
                if (textContainerDivider) {
                    canvas.drawLine(LocaleController.isRTL ? 0 : dp(21), getMeasuredHeight() - 1, getMeasuredWidth() - (LocaleController.isRTL ? dp(21) : 0), getMeasuredHeight() - 1, Theme.getThemePaint(Theme.key_paint_divider, getResourceProvider()));
                }
            }
        };
        textContainer.setOrientation(LinearLayout.VERTICAL);
        textContainer.addView(textCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        textContainer.addView(chipsView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
    }

    private void updateNameIcon() {
        PillColors colors = PillColors.get(spec.color);
        nameIconView.set(spec.icon, 0, colors.top(), colors.bottom());
    }

    private static void disallowNewLines(EditTextCell cell) {
        InputFilter[] filters = cell.editText.getFilters();
        InputFilter[] newFilters = Arrays.copyOf(filters, filters.length + 1);
        newFilters[filters.length] = (source, start, end, dest, dstart, dend) -> {
            for (int i = start; i < end; i++) {
                if (source.charAt(i) == '\n') {
                    return source.subSequence(start, end).toString().replace("\n", "");
                }
            }
            return null;
        };
        cell.editText.setFilters(newFilters);
        cell.editText.setImeOptions(EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
    }

    private void rebuildChips() {
        if (chipsView == null || chipsHttp == spec.http && chipsView.getChildCount() > 0) {
            return;
        }
        chipsHttp = spec.http;
        chipsView.removeAllViews();
        if (spec.http) {
            addChip(PillTemplate.META_STATUS, "{" + PillTemplate.META_STATUS + "}");
            addChip(PillTemplate.META_MS, "{" + PillTemplate.META_MS + "}");
            addChip(PillTemplate.META_UPDATED, "{" + PillTemplate.META_UPDATED + "|time}");
            addChip(PillTemplate.META_BODY, "{" + PillTemplate.META_BODY + "}");
        } else {
            addChip(PillTemplate.META_NOW, "{" + PillTemplate.META_NOW + "|time}");
        }
    }

    private void addChip(String meta, String token) {
        TextView chip = new TextView(getContext());
        chip.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        chip.setTypeface(AndroidUtilities.regular());
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(8), 0, dp(12), 0);
        chip.setText(PillTemplate.label(meta));
        int accent = getThemedColor(Theme.key_windowBackgroundWhiteBlueText4);
        chip.setTextColor(accent);
        Drawable plus = ContextCompat.getDrawable(getContext(), R.drawable.msg_add);
        if (plus != null) {
            plus = plus.mutate();
            plus.setBounds(0, 0, dp(18), dp(18));
            plus.setColorFilter(new PorterDuffColorFilter(accent, PorterDuff.Mode.SRC_IN));
            chip.setCompoundDrawablesRelative(plus, null, null, null);
            chip.setCompoundDrawablePadding(dp(4));
        }
        chip.setBackground(Theme.createSimpleSelectorRoundRectDrawable(dp(15), Theme.multAlpha(accent, 0.1f), Theme.multAlpha(accent, 0.2f)));
        chip.setOnClickListener(v -> insertToken(token));
        ScaleStateListAnimator.apply(chip, .05f, 1.2f);
        chipsView.addView(chip, new PillFlowLayout.LayoutParams(PillFlowLayout.LayoutParams.WRAP_CONTENT, dp(30)));
    }

    private void highlightTokens(Editable editable) {
        if (editable == null) {
            return;
        }
        for (TokenSpan span : editable.getSpans(0, editable.length(), TokenSpan.class)) {
            editable.removeSpan(span);
        }
        int color = getThemedColor(Theme.key_windowBackgroundWhiteBlueText4);
        Matcher matcher = TOKEN.matcher(editable);
        while (matcher.find()) {
            editable.setSpan(new TokenSpan(color), matcher.start(), matcher.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

    private static class TokenSpan extends ForegroundColorSpan {
        TokenSpan(int color) {
            super(color);
        }
    }

    private void scheduleListUpdate() {
        AndroidUtilities.cancelRunOnUIThread(updateListRunnable);
        AndroidUtilities.runOnUIThread(updateListRunnable, 150);
    }

    private void updateList() {
        AndroidUtilities.cancelRunOnUIThread(updateListRunnable);
        updateListRunnable.run();
    }

    private PillTemplate.Context currentContext() {
        return CustomPillResult.createContext(spec, response);
    }

    private void updatePreview(boolean animated) {
        if (previewPill == null) {
            return;
        }
        previewPill.setPreviewSpec(spec);
        if (running) {
            previewPill.setPreview(null, true, false);
            return;
        }
        previewPill.setPreview(CustomPillResult.buildPreview(spec, response), false, animated);
        boolean outdated = isResponseOutdated();
        if (responseOutdated != outdated) {
            responseOutdated = outdated;
            scheduleListUpdate();
        }
    }

    private boolean isResponseOutdated() {
        return spec.http && response != null && responseKey != null && !responseKey.equals(requestKey());
    }

    public static String getErrorText(CustomPillFetcher.Response response) {
        return switch (response.error) {
            case CustomPillFetcher.ERROR_TOO_LARGE -> LocaleController.getString(R.string.CustomPillErrorTooLarge);
            case CustomPillFetcher.ERROR_BAD_URL -> LocaleController.getString(R.string.CustomPillErrorBadUrl);
            case CustomPillFetcher.ERROR_LOCATION -> LocaleController.getString(R.string.CustomPillErrorLocation);
            case CustomPillFetcher.ERROR_NONE -> "HTTP " + response.status;
            default -> LocaleController.getString(R.string.CustomPillErrorNetwork);
        };
    }

    public static String getRefreshName(int seconds) {
        if (seconds <= 0) {
            return LocaleController.getString(R.string.CustomPillRefreshManual);
        }
        if (seconds < 3600) {
            return LocaleController.formatString(R.string.CustomPillRefreshMinutes, seconds / 60);
        }
        return LocaleController.formatString(R.string.CustomPillRefreshHours, seconds / 3600);
    }

    private static int getTapIcon(String tap) {
        return switch (tap) {
            case CustomPillSpec.TAP_LINK -> R.drawable.msg_link;
            case CustomPillSpec.TAP_COPY -> R.drawable.msg_copy;
            case CustomPillSpec.TAP_DETAILS -> R.drawable.msg_info;
            case CustomPillSpec.TAP_NONE -> R.drawable.msg_block;
            default -> R.drawable.msg_retry;
        };
    }

    private static String getTapName(String tap) {
        return switch (tap) {
            case CustomPillSpec.TAP_LINK -> LocaleController.getString(R.string.OpenLink);
            case CustomPillSpec.TAP_COPY -> LocaleController.getString(R.string.CustomPillTapCopy);
            case CustomPillSpec.TAP_DETAILS -> LocaleController.getString(R.string.CustomPillTapDetails);
            case CustomPillSpec.TAP_NONE -> LocaleController.getString(R.string.CustomPillTapNone);
            default -> LocaleController.getString(R.string.Refresh);
        };
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asHeader(LocaleController.getString(R.string.CustomPillAppearance)));
        items.add(UItem.asCustom(nameCell));
        items.add(UItem.asCustom(colorGrid));
        items.add(UItem.asShadow(LocaleController.getString(R.string.CustomPillAppearanceInfo)));

        items.add(UItem.asHeader(LocaleController.getString(R.string.CustomPillSource)));
        items.add(UItem.asRadio(ITEM_SOURCE_NONE, LocaleController.getString(R.string.CustomPillSourceNone)).setChecked(!spec.http));
        items.add(UItem.asRadio(ITEM_SOURCE_HTTP, LocaleController.getString(R.string.CustomPillSourceHttp)).setChecked(spec.http));
        if (spec.http) {
            items.add(UItem.asCustom(urlCell));
            items.add(UItem.asButton(ITEM_METHOD, R.drawable.msg_sendfile, LocaleController.getString(R.string.CustomPillMethod), spec.method));
            if (CustomPillSpec.METHOD_POST.equals(spec.method)) {
                items.add(UItem.asCustom(bodyCell));
            }
            items.add(UItem.asButton(ITEM_HEADERS, R.drawable.msg_list, LocaleController.getString(R.string.CustomPillHeaders), spec.headers.isEmpty() ? LocaleController.getString(R.string.CustomPillNone) : String.valueOf(spec.headers.size())));
            boolean missing = hasMissingSecrets();
            items.add(UItem.asButton(ITEM_SECRETS, R.drawable.msg_secret, LocaleController.getString(R.string.CustomPillSecrets),
                    missing ? LocaleController.getString(R.string.CustomPillSecretsMissing) : spec.secrets.isEmpty() ? LocaleController.getString(R.string.CustomPillNone) : String.valueOf(spec.secrets.size())));
            items.add(UItem.asButton(ITEM_REFRESH, R.drawable.msg_recent, LocaleController.getString(R.string.CustomPillRefresh), getRefreshName(spec.refresh)));
            String runValue = null;
            if (!running && response != null) {
                runValue = response.error == CustomPillFetcher.ERROR_NONE ? response.status + " · " + LocaleController.formatString(R.string.CustomPillMs, response.ms) : getErrorText(response);
                if (isResponseOutdated()) {
                    runValue = LocaleController.getString(R.string.CustomPillRequestChanged);
                }
            }
            items.add(UItem.asButton(ITEM_RUN, R.drawable.msg_retry, LocaleController.getString(running ? R.string.CustomPillRunning : response != null ? R.string.CustomPillRunAgain : R.string.CustomPillRun), runValue).accent().setEnabled(!running));
            items.add(UItem.asShadow(LocaleController.getString(R.string.CustomPillSourceInfo)));
        } else {
            items.add(UItem.asShadow(LocaleController.getString(R.string.CustomPillSourceNoneInfo)));
        }

        items.add(UItem.asHeader(LocaleController.getString(R.string.CustomPillText)));
        rebuildChips();
        ArrayList<PillTemplate.Token> tokens = PillTemplate.parse(spec.text);
        boolean browse = spec.http;
        boolean containerDivider = !tokens.isEmpty() || browse;
        if (textContainerDivider != containerDivider) {
            textContainerDivider = containerDivider;
            textContainer.invalidate();
        }
        items.add(UItem.asCustom(textContainer));
        PillTemplate.Context context = currentContext();
        for (int i = 0; i < tokens.size(); i++) {
            PillTemplate.Token token = tokens.get(i);
            boolean divider = i < tokens.size() - 1 || browse;
            items.add(CustomPillCells.TokenCell.Factory.of(new CustomPillCells.TokenRow(i, PillTemplate.label(token.path), getFilterSummary(token), PillTemplate.evaluate(token, context), divider)));
        }
        if (browse) {
            if (browseDrawable == null) {
                browseDrawable = new AddButtonDrawable(getContext());
            }
            browseDrawable.updateColors(getResourceProvider());
            items.add(UItem.asButton(ITEM_BROWSE, browseDrawable, LocaleController.getString(R.string.CustomPillResponseBrowse)).accent().setEnabled(!running).onBind(view -> {
                if (view instanceof TextCell cell) {
                    cell.setImageLeft(19);
                }
            }));
        }
        items.add(UItem.asShadow(LocaleController.getString(R.string.CustomPillTextInfo)));

        items.add(UItem.asHeader(LocaleController.getString(R.string.CustomPillRules)));
        for (int i = 0; i < spec.rules.size(); i++) {
            CustomPillSpec.Rule rule = spec.rules.get(i);
            items.add(CustomPillCells.SquareCell.Factory.of(ITEM_RULE + i, new CustomPillCells.SquareRow(
                    rule.icon != null ? rule.icon : spec.icon,
                    rule.color != null ? rule.color : spec.color,
                    getRuleTitle(rule),
                    getRuleSubtitle(rule)
            )));
        }
        if (spec.rules.size() < CustomPillSpec.MAX_RULES) {
            if (addRuleDrawable == null) {
                addRuleDrawable = new AddButtonDrawable(getContext());
            }
            addRuleDrawable.updateColors(getResourceProvider());
            items.add(UItem.asButton(ITEM_ADD_RULE, addRuleDrawable, LocaleController.getString(R.string.CustomPillRuleAdd)).accent().onBind(view -> {
                if (view instanceof TextCell cell) {
                    cell.setImageLeft(19);
                }
            }));
        }
        items.add(UItem.asShadow(LocaleController.getString(R.string.CustomPillRulesInfo)));

        items.add(UItem.asHeader(LocaleController.getString(R.string.CustomPillTap)));
        items.add(UItem.asButton(ITEM_TAP, getTapIcon(spec.tap), LocaleController.getString(R.string.CustomPillTapAction), getTapName(spec.tap)));
        if (CustomPillSpec.TAP_LINK.equals(spec.tap)) {
            items.add(UItem.asCustom(linkCell));
        }
        items.add(UItem.asShadow(LocaleController.getString(CustomPillSpec.TAP_LINK.equals(spec.tap) ? R.string.CustomPillTapLinkInfo : R.string.CustomPillTapInfo)));

        if (!creatingNew) {
            items.add(UItem.asButton(ITEM_DELETE, R.drawable.msg_delete, LocaleController.getString(R.string.CustomPillDelete)).red());
            items.add(UItem.asShadow(null));
        }
    }

    private CharSequence getRuleTitle(CustomPillSpec.Rule rule) {
        String value = CustomPillSpec.OP_EMPTY.equals(rule.op) ? "" : " " + rule.value;
        return LocaleController.formatString(R.string.CustomPillRuleTitle, PillTemplate.label(rule.path), PillRuleSheet.getOperatorSymbol(rule.op) + value);
    }

    private CharSequence getRuleSubtitle(CustomPillSpec.Rule rule) {
        ArrayList<String> effects = new ArrayList<>();
        if (rule.color != null) {
            effects.add(LocaleController.getString(R.string.CustomPillRuleEffectColor));
        }
        if (rule.icon != null) {
            effects.add(LocaleController.getString(R.string.CustomPillRuleEffectIcon));
        }
        if (!TextUtils.isEmpty(rule.text)) {
            effects.add(LocaleController.formatString(R.string.CustomPillRuleEffectText, rule.text));
        }
        if (effects.isEmpty()) {
            return LocaleController.getString(R.string.CustomPillRuleEffectNone);
        }
        return TextUtils.join(", ", effects);
    }

    private String getFilterSummary(PillTemplate.Token token) {
        ArrayList<String> parts = new ArrayList<>();
        for (PillTemplate.Filter filter : token.filters) {
            String arg = filter.arg != null ? filter.arg : "";
            switch (filter.name) {
                case "fixed" -> parts.add(LocaleController.formatString(R.string.CustomPillSummaryDigits, arg));
                case "round" -> parts.add(LocaleController.formatString(R.string.CustomPillSummaryDigits, "0"));
                case "compact" -> parts.add(LocaleController.getString(R.string.CustomPillSummaryCompact));
                case "sign" -> parts.add(LocaleController.getString(R.string.CustomPillSummarySign));
                case "percent" -> parts.add(LocaleController.getString(R.string.CustomPillSummaryPercent));
                case "div" -> parts.add("÷ " + arg);
                case "mul" -> parts.add("× " + arg);
                case "add" -> parts.add("+ " + arg);
                case "sub" -> parts.add("− " + arg);
                case "ago" -> parts.add(LocaleController.getString(R.string.CustomPillSummaryAgo));
                case "time" -> parts.add(LocaleController.getString(R.string.CustomPillSummaryTime));
                case "until" -> parts.add(LocaleController.formatString(R.string.CustomPillSummaryUntil, arg));
                case "default" -> parts.add(LocaleController.formatString(R.string.CustomPillSummaryDefault, arg));
                default -> parts.add(filter.toString());
            }
        }
        if (parts.isEmpty()) {
            return LocaleController.getString(R.string.CustomPillSummaryAsIs);
        }
        return TextUtils.join(" · ", parts);
    }

    private boolean hasBrowsableResponse() {
        if (response == null || response.error != CustomPillFetcher.ERROR_NONE) {
            return false;
        }
        if (response.json != null && (response.json.isJsonObject() || response.json.isJsonArray())) {
            return response.json.isJsonObject() ? response.json.getAsJsonObject().size() > 0 : response.json.getAsJsonArray().size() > 0;
        }
        return !TextUtils.isEmpty(response.body);
    }

    private void autoExpand(@Nullable JsonElement json) {
        expanded.clear();
        if (json == null) {
            return;
        }
        expandLevel(json, "", 0);
    }

    private void expandLevel(JsonElement element, String base, int depth) {
        if (depth > 1) {
            return;
        }
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                JsonElement value = entry.getValue();
                if (isSmallContainer(value)) {
                    String path = JsonPath.child(base, entry.getKey());
                    expanded.add(path);
                    expandLevel(value, path, depth + 1);
                }
            }
        } else if (element.isJsonArray() && element.getAsJsonArray().size() > 0 && isSmallContainer(element.getAsJsonArray().get(0))) {
            String path = JsonPath.index(base, 0);
            expanded.add(path);
            expandLevel(element.getAsJsonArray().get(0), path, depth + 1);
        }
    }

    private static boolean isSmallContainer(JsonElement element) {
        if (element.isJsonObject()) {
            return element.getAsJsonObject().size() <= MAX_AUTO_EXPAND;
        }
        return element.isJsonArray() && element.getAsJsonArray().size() <= MAX_AUTO_EXPAND;
    }

    private List<String> getRulePaths() {
        ArrayList<String> paths = new ArrayList<>();
        for (PillTemplate.Token token : PillTemplate.parse(spec.text)) {
            if (!paths.contains(token.path)) {
                paths.add(token.path);
            }
        }
        if (spec.http) {
            if (!paths.contains(PillTemplate.META_STATUS)) {
                paths.add(PillTemplate.META_STATUS);
            }
            if (!paths.contains(PillTemplate.META_MS)) {
                paths.add(PillTemplate.META_MS);
            }
            if (response != null && response.json != null) {
                collectLeaves(response.json, "", paths);
            }
        }
        return paths;
    }

    private void collectLeaves(JsonElement element, String base, List<String> paths) {
        if (paths.size() >= 60) {
            return;
        }
        if (element.isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
                collectLeaves(entry.getValue(), JsonPath.child(base, entry.getKey()), paths);
            }
        } else if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            for (int i = 0; i < Math.min(array.size(), 5); i++) {
                collectLeaves(array.get(i), JsonPath.index(base, i), paths);
            }
        } else if (!TextUtils.isEmpty(base) && !paths.contains(base)) {
            paths.add(base);
        }
    }

    private boolean hasMissingSecrets() {
        for (String name : spec.secrets) {
            if (TextUtils.isEmpty(secrets.get(name))) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        int id = item.id;
        if (item.object instanceof CustomPillCells.TokenRow row) {
            openFormatSheet(row.index());
            return;
        }
        if (id == ITEM_SOURCE_NONE || id == ITEM_SOURCE_HTTP) {
            boolean http = id == ITEM_SOURCE_HTTP;
            if (spec.http != http) {
                spec.http = http;
                if (http && TextUtils.isEmpty(spec.url)) {
                    urlCell.setText("https://");
                    spec.url = "https://";
                }
                updatePreview(true);
                updateList();
            }
        } else if (id == ITEM_METHOD) {
            String[] methods = {CustomPillSpec.METHOD_GET, CustomPillSpec.METHOD_POST};
            showListDialog(item, methods, LocaleController.getString(R.string.CustomPillMethod), CustomPillSpec.METHOD_POST.equals(spec.method) ? 1 : 0, index -> {
                spec.method = methods[index];
                updatePreview(false);
            });
        } else if (id == ITEM_HEADERS) {
            openKeyValueSheet(PillKeyValueSheet.MODE_HEADERS);
        } else if (id == ITEM_SECRETS) {
            openKeyValueSheet(PillKeyValueSheet.MODE_SECRETS);
        } else if (id == ITEM_REFRESH) {
            CharSequence[] names = new CharSequence[CustomPillSpec.REFRESH_INTERVALS.length];
            int selected = 0;
            for (int i = 0; i < names.length; i++) {
                names[i] = getRefreshName(CustomPillSpec.REFRESH_INTERVALS[i]);
                if (CustomPillSpec.REFRESH_INTERVALS[i] == spec.refresh) {
                    selected = i;
                }
            }
            showListDialog(item, names, LocaleController.getString(R.string.CustomPillRefresh), selected, index -> spec.refresh = CustomPillSpec.REFRESH_INTERVALS[index]);
        } else if (id == ITEM_RUN) {
            runRequest();
        } else if (id == ITEM_BROWSE) {
            if (running) {
                return;
            }
            if (hasBrowsableResponse() && !isResponseOutdated()) {
                openResponseSheet();
            } else {
                browseAfterRun = true;
                runRequest();
            }
        } else if (id == ITEM_ADD_RULE) {
            openRuleSheet(-1);
        } else if (id >= ITEM_RULE && id < ITEM_RULE + spec.rules.size()) {
            openRuleSheet(id - ITEM_RULE);
        } else if (id == ITEM_TAP) {
            CharSequence[] names = new CharSequence[CustomPillSpec.TAPS.length];
            int[] icons = new int[CustomPillSpec.TAPS.length];
            int selected = 0;
            for (int i = 0; i < names.length; i++) {
                names[i] = getTapName(CustomPillSpec.TAPS[i]);
                icons[i] = getTapIcon(CustomPillSpec.TAPS[i]);
                if (CustomPillSpec.TAPS[i].equals(spec.tap)) {
                    selected = i;
                }
            }
            showListDialog(item, names, icons, LocaleController.getString(R.string.CustomPillTapAction), selected, index -> {
                spec.tap = CustomPillSpec.TAPS[index];
                if (CustomPillSpec.TAP_LINK.equals(spec.tap) && TextUtils.isEmpty(spec.link)) {
                    linkCell.setText("https://");
                    spec.link = "https://";
                }
            });
        } else if (id == ITEM_DELETE) {
            confirmDelete();
        }
    }

    private void insertToken(String token) {
        Editable editable = textCell.editText.getText();
        if (editable == null) {
            return;
        }
        int start = textCell.editText.hasFocus() ? textCell.editText.getSelectionStart() : editable.length();
        int end = textCell.editText.hasFocus() ? textCell.editText.getSelectionEnd() : editable.length();
        if (start < 0 || end < 0) {
            start = end = editable.length();
        }
        if (start > end) {
            int t = start;
            start = end;
            end = t;
        }
        String insert = (start > 0 && !Character.isWhitespace(editable.charAt(start - 1)) ? " " : "") + token;
        if (editable.length() - (end - start) + insert.length() > CustomPillSpec.MAX_TEXT) {
            showFieldError(textCell);
            return;
        }
        editable.replace(start, end, insert);
        int cursor = start + insert.length();
        if (textCell.editText.hasFocus()) {
            textCell.editText.setSelection(Math.min(cursor, editable.length()));
        }
        updateList();
    }

    private void openResponseSheet() {
        new PillResponseSheet(this, response, expanded, token -> {
            insertToken(token);
            scrollToText();
        }).show();
    }

    private void scrollToText() {
        if (listView == null) {
            return;
        }
        int position = -1;
        for (int i = 0; i < listView.adapter.getItemCount(); i++) {
            UItem item = listView.adapter.getItem(i);
            if (item != null && item.view == textContainer) {
                position = i;
                break;
            }
        }
        if (position >= 0) {
            listView.smoothScrollToPosition(position);
        }
    }

    private void openFormatSheet(int index) {
        ArrayList<PillTemplate.Token> tokens = PillTemplate.parse(spec.text);
        if (index < 0 || index >= tokens.size()) {
            return;
        }
        PillTemplate.Token token = tokens.get(index);
        new PillValueFormatSheet(this, token, currentContext(), newToken -> {
            Editable editable = textCell.editText.getText();
            if (editable != null && token.end <= editable.length()) {
                editable.replace(token.start, token.end, newToken);
            }
            updateList();
        }, () -> {
            Editable editable = textCell.editText.getText();
            if (editable != null && token.end <= editable.length()) {
                int start = token.start;
                int end = token.end;
                if (start > 0 && editable.charAt(start - 1) == ' ') {
                    start--;
                }
                editable.delete(start, end);
            }
            updateList();
        }).show();
    }

    private void openRuleSheet(int index) {
        boolean isNew = index < 0;
        CustomPillSpec.Rule rule;
        if (isNew) {
            rule = new CustomPillSpec.Rule();
            rule.color = IconBackgroundColors.RED.name();
        } else {
            rule = spec.rules.get(index);
        }
        List<String> paths = getRulePaths();
        if (isNew && paths.isEmpty()) {
            BulletinFactory.of(this).createSimpleBulletin(R.raw.chats_infotip, LocaleController.getString(R.string.CustomPillRuleNoValues)).show();
            return;
        }
        new PillRuleSheet(this, rule, spec.icon, spec.color, paths, isNew, saved -> {
            if (isNew) {
                spec.rules.add(saved);
            } else if (index < spec.rules.size()) {
                spec.rules.set(index, saved);
            }
            updatePreview(true);
            updateList();
        }, isNew ? null : () -> {
            if (index < spec.rules.size()) {
                spec.rules.remove(index);
            }
            updatePreview(true);
            updateList();
        }).show();
    }

    private void openKeyValueSheet(int mode) {
        ArrayList<String[]> entries = new ArrayList<>();
        if (mode == PillKeyValueSheet.MODE_SECRETS) {
            for (String name : spec.secrets) {
                String value = secrets.get(name);
                entries.add(new String[]{name, value != null ? value : ""});
            }
        } else {
            for (CustomPillSpec.Header header : spec.headers) {
                entries.add(new String[]{header.name, header.value});
            }
        }
        new PillKeyValueSheet(this, mode, entries, changed -> {
            if (mode == PillKeyValueSheet.MODE_SECRETS) {
                spec.secrets.clear();
                secrets.clear();
                for (String[] entry : changed) {
                    spec.secrets.add(entry[0]);
                    if (!TextUtils.isEmpty(entry[1])) {
                        secrets.put(entry[0], entry[1]);
                    }
                }
            } else {
                spec.headers.clear();
                for (String[] entry : changed) {
                    spec.headers.add(new CustomPillSpec.Header(entry[0], entry[1]));
                }
            }
            updatePreview(false);
            updateList();
        }).show();
    }

    private void runRequest() {
        if (running) {
            return;
        }
        if (!CustomPillSpec.isValidUrl(spec.url)) {
            browseAfterRun = false;
            showFieldError(urlCell);
            BulletinFactory.of(this).createErrorBulletin(LocaleController.getString(R.string.CustomPillErrorUrl)).show();
            return;
        }
        if (spec.hasInsecureSecrets()) {
            browseAfterRun = false;
            showFieldError(urlCell);
            BulletinFactory.of(this).createErrorBulletin(LocaleController.getString(R.string.CustomPillErrorInsecureSecrets)).show();
            return;
        }
        if (hasMissingSecrets()) {
            openKeyValueSheet(PillKeyValueSheet.MODE_SECRETS);
            return;
        }
        if (CustomPillFetcher.needsLocation(spec) && !Weather.isLocationPermissionGranted()) {
            Weather.getUserLocation(true, location -> {
                if (location != null && fragmentView != null) {
                    runRequest();
                }
            });
            return;
        }
        AndroidUtilities.hideKeyboard(fragmentView != null ? fragmentView.findFocus() : null);
        running = true;
        final int currentRequest = ++requestId;
        final String key = requestKey();
        CustomPillSpec requestSpec = spec.copy();
        requestSpec.id = 0;
        updatePreview(false);
        updateList();
        CustomPillFetcher.fetch(requestSpec, new HashMap<>(secrets), result -> {
            if (currentRequest != requestId) {
                return;
            }
            running = false;
            response = result;
            responseKey = key;
            boolean openSheet = browseAfterRun;
            browseAfterRun = false;
            if (result.error == CustomPillFetcher.ERROR_NONE) {
                autoExpand(result.json);
            } else {
                BulletinFactory.of(this).createErrorBulletin(getErrorText(result)).show();
            }
            updatePreview(true);
            updateList();
            if (openSheet && hasBrowsableResponse()) {
                openResponseSheet();
            }
        });
    }

    private boolean validate() {
        if (TextUtils.isEmpty(spec.name)) {
            showFieldError(nameCell);
            return false;
        }
        if (spec.http && !CustomPillSpec.isValidUrl(spec.url)) {
            showFieldError(urlCell);
            BulletinFactory.of(this).createErrorBulletin(LocaleController.getString(R.string.CustomPillErrorUrl)).show();
            return false;
        }
        if (spec.http && spec.hasInsecureSecrets()) {
            showFieldError(urlCell);
            BulletinFactory.of(this).createErrorBulletin(LocaleController.getString(R.string.CustomPillErrorInsecureSecrets)).show();
            return false;
        }
        if (CustomPillSpec.TAP_LINK.equals(spec.tap) && !CustomPillSpec.isValidLink(spec.link)) {
            showFieldError(linkCell);
            return false;
        }
        return true;
    }

    private void save() {
        if (!validate()) {
            return;
        }
        int id = pillId;
        if (creatingNew) {
            id = CustomPillStore.allocateId();
            if (id == 0) {
                BulletinFactory.of(this).createErrorBulletin(LocaleController.formatString(R.string.CustomPillLimitReached, CustomPillStore.MAX_PILLS)).show();
                return;
            }
        } else if (CustomPillStore.get(pillId) == null) {
            finishFragment();
            return;
        }
        spec.id = id;
        CustomPillFetcher.Response validResponse = response != null && response.error == CustomPillFetcher.ERROR_NONE && requestKey().equals(responseKey) ? response : null;
        CustomPillStore.save(spec, creatingNew, secrets, validResponse);
        finishFragment();
    }

    private void confirmDelete() {
        if (getParentActivity() == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity(), getResourceProvider());
        builder.setTitle(LocaleController.getString(R.string.CustomPillDeleteTitle));
        builder.setMessage(AndroidUtilities.replaceTags(LocaleController.formatString(R.string.CustomPillDeleteText, spec.name)));
        builder.setPositiveButton(LocaleController.getString(R.string.Delete), (dialog, which) -> {
            CustomPillStore.delete(pillId);
            finishFragment();
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        AlertDialog dialog = builder.create();
        showDialog(dialog);
        TextView button = (TextView) dialog.getButton(DialogInterface.BUTTON_POSITIVE);
        if (button != null) {
            button.setTextColor(getThemedColor(Theme.key_text_RedBold));
        }
    }

    private void showFieldError(EditTextCell cell) {
        if (cell == null) {
            return;
        }
        BotWebViewVibrationEffect.APP_ERROR.vibrate();
        AndroidUtilities.shakeViewSpring(cell, shift = -shift);
        cell.editText.requestFocus();
        AndroidUtilities.showKeyboard(cell.editText);
        if (listView != null) {
            listView.post(() -> cell.requestRectangleOnScreen(new Rect(0, 0, cell.getWidth(), cell.getHeight()), false));
        }
    }
}
