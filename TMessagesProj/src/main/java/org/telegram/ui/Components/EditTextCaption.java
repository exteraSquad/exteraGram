/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.CharacterStyle;
import android.text.style.URLSpan;
import android.util.TypedValue;
import android.view.ActionMode;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputConnectionWrapper;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.core.view.inputmethod.EditorInfoCompat;

import com.exteragram.messenger.inline.InlineMathController;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.utils.code.highlight.CodeHighlighting;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.R;
import org.telegram.messenger.utils.CopyUtilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.AlertDialogDecor;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.FloatingToolbar;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeBind;
import org.telegram.ui.LaunchActivity;

import java.io.InputStream;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

public class EditTextCaption extends EditTextBoldCursor implements FloatingToolbar.StyleDelegate {

    private static final int ACCESSIBILITY_ACTION_SHARE = 0x10000000;

    private String caption;
    private StaticLayout captionLayout;
    private Text rightText;
    private int userNameLength;
    private int xOffset;
    private int yOffset;
    private boolean copyPasteShowed;
    private int hintColor;
    private EditTextCaptionDelegate delegate;
    private int selectionStart = -1;
    private int selectionEnd = -1;
    private boolean allowTextEntitiesIntersection;
    private int lineCount;
    private boolean isInitLineCount;
    private final Theme.ResourcesProvider resourcesProvider;
    private int themedQuoteColor;
    private AlertDialog creationLinkDialog;
    public boolean adaptiveCreateLinkDialog;

    public interface EditTextCaptionDelegate {
        void onSpansChanged();
    }

    public EditTextCaption(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        quoteColor = themedQuoteColor = Theme.getColor(Theme.key_chat_inQuote, resourcesProvider);
        addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                if (lineCount != getLineCount()) {
                    if (!isInitLineCount && getMeasuredWidth() > 0) {
                        onLineCountChanged(lineCount, getLineCount());
                    }
                    lineCount = getLineCount();
                }
            }
        });
        setClipToPadding(true);
    }

    protected void onLineCountChanged(int oldLineCount, int newLineCount) {

    }

    public void setCaption(String value) {
        if ((caption == null || caption.length() == 0) && (value == null || value.length() == 0) || caption != null && caption.equals(value)) {
            return;
        }
        caption = value;
        if (caption != null) {
            caption = caption.replace('\n', ' ');
        }
        requestLayout();
    }

    public void setDelegate(EditTextCaptionDelegate editTextCaptionDelegate) {
        delegate = editTextCaptionDelegate;
    }

    protected void notifySpansChanged() {
        if (delegate != null) {
            delegate.onSpansChanged();
        }
    }

    public void setAllowTextEntitiesIntersection(boolean value) {
        allowTextEntitiesIntersection = value;
    }

    public boolean getAllowTextEntitiesIntersection() {
        return allowTextEntitiesIntersection;
    }

    public void makeSelectedBold() {
        TextStyleSpan.TextStyleRun run = new TextStyleSpan.TextStyleRun();
        run.flags |= TextStyleSpan.FLAG_STYLE_BOLD;
        applyTextStyleToSelection(new TextStyleSpan(run));
    }

    public void makeSelectedSpoiler() {
        TextStyleSpan.TextStyleRun run = new TextStyleSpan.TextStyleRun();
        run.flags |= TextStyleSpan.FLAG_STYLE_SPOILER;
        applyTextStyleToSelection(new TextStyleSpan(run));
        invalidateSpoilers();
    }

    public void makeSelectedItalic() {
        TextStyleSpan.TextStyleRun run = new TextStyleSpan.TextStyleRun();
        run.flags |= TextStyleSpan.FLAG_STYLE_ITALIC;
        applyTextStyleToSelection(new TextStyleSpan(run));
    }

    public void makeSelectedMono() {
        TextStyleSpan.TextStyleRun run = new TextStyleSpan.TextStyleRun();
        run.flags |= TextStyleSpan.FLAG_STYLE_MONO;
        applyTextStyleToSelection(new TextStyleSpan(run));
    }

    public void makeSelectedCode() {
        final int start;
        final int end;
        if (selectionStart >= 0 && selectionEnd >= 0) {
            start = selectionStart;
            end = selectionEnd;
            selectionStart = selectionEnd = -1;
        } else {
            start = getSelectionStart();
            end = getSelectionEnd();
        }
        if (start >= end) {
            return;
        }

        final EditTextBoldCursor editText = createCodeLanguageEditText();
        fillSelectedCodeLanguage(editText, start, end);

        AlertDialog.Builder builder = null;
        if (adaptiveCreateLinkDialog) {
            builder = new AlertDialogDecor.Builder(getContext(), resourcesProvider);
        } else {
            builder = new AlertDialog.Builder(getContext(), resourcesProvider);
        }
        builder.setTitle(LocaleController.getString(R.string.CreateCode));
        var container = new FrameLayout(getContext());
        container.addView(editText, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 36, Gravity.CENTER_HORIZONTAL, 24, 0, 24, 0));
        builder.setView(container);
        builder.setWidth(dp(292));
        builder.setPositiveButton(LocaleController.getString(R.string.OK), (dialogInterface, i) -> applyCodeBlock(start, end, editText.getText().toString()));
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);

        var dialog = builder.create();
        dialog.setOnShowListener(d -> {
            editText.requestFocus();
            AndroidUtilities.showKeyboard(editText);
        });

        if (adaptiveCreateLinkDialog) {
            creationLinkDialog = dialog;
            dialog.setOnDismissListener(d -> {
                creationLinkDialog = null;
                requestFocus();
            });
            dialog.showDelayed(250);
        } else {
            dialog.show();
        }

        editText.setSelection(0, editText.getText().length());
    }


    private EditTextBoldCursor createCodeLanguageEditText() {
        EditTextBoldCursor editText = new EditTextBoldCursor(getContext()) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(64), MeasureSpec.EXACTLY));
            }
        };
        editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        editText.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
        editText.setCursorColor(getThemedColor(Theme.key_windowBackgroundWhiteInputFieldActivated));
        editText.setHintText(LocaleController.getString(R.string.Language));
        editText.setHeaderHintColor(getThemedColor(Theme.key_windowBackgroundWhiteBlueHeader));
        editText.setSingleLine(true);
        editText.setFocusable(true);
        editText.setTransformHintToHeader(true);
        editText.setLineColors(getThemedColor(Theme.key_windowBackgroundWhiteInputField), getThemedColor(Theme.key_windowBackgroundWhiteInputFieldActivated), getThemedColor(Theme.key_text_RedRegular));
        editText.setImeOptions(EditorInfo.IME_ACTION_DONE);
        editText.setBackground(null);
        editText.requestFocus();
        editText.setPadding(0, 0, 0, 0);
        return editText;
    }

    private void fillSelectedCodeLanguage(EditTextBoldCursor editText, int start, int end) {
        CodeHighlighting.Span[] codeSpans = getText().getSpans(start, end, CodeHighlighting.Span.class);
        if (codeSpans == null) {
            return;
        }
        for (CodeHighlighting.Span codeSpan : codeSpans) {
            if (codeSpan != null && !TextUtils.isEmpty(codeSpan.lng)) {
                editText.setText(codeSpan.lng);
                return;
            }
        }
    }

    private void applyCodeBlock(int start, int end, String language) {
        Editable editable = getText();
        try {
            CharacterStyle[] spans = editable.getSpans(start, end, CharacterStyle.class);
            if (spans != null) {
                for (CharacterStyle oldSpan : spans) {
                    int spanStart = editable.getSpanStart(oldSpan);
                    int spanEnd = editable.getSpanEnd(oldSpan);
                    editable.removeSpan(oldSpan);
                    if (spanStart < start) {
                        editable.setSpan(oldSpan, spanStart, start, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }
                    if (spanEnd > end) {
                        editable.setSpan(oldSpan, end, spanEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }
                }
            }
            editable.setSpan(new CodeHighlighting.Span(true, 0, null, CodeHighlighting.normalizeLanguage(language), editable.subSequence(start, end).toString()), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        } catch (Exception e) {
            FileLog.e(e);
        }
        if (delegate != null) {
            delegate.onSpansChanged();
        }
    }

    public void makeSelectedStrike() {
        TextStyleSpan.TextStyleRun run = new TextStyleSpan.TextStyleRun();
        run.flags |= TextStyleSpan.FLAG_STYLE_STRIKE;
        applyTextStyleToSelection(new TextStyleSpan(run));
    }

    public void makeSelectedUnderline() {
        TextStyleSpan.TextStyleRun run = new TextStyleSpan.TextStyleRun();
        run.flags |= TextStyleSpan.FLAG_STYLE_UNDERLINE;
        applyTextStyleToSelection(new TextStyleSpan(run));
    }

    // Toggle an inline style over the current selection, mirroring a formatting button: if the style
    // already fully covers the selection it's removed, otherwise it's added. Mono is kept mutually
    // exclusive with every other inline style, and sub/superscript exclude each other (same rules the
    // rich editor uses), so there's no conflict between mono and the rest.
    public void toggleStyleForSelection(int flag) {
        final Editable editable = getText();
        if (editable == null) {
            return;
        }
        int start = getSelectionStart();
        int end = getSelectionEnd();
        if (start < 0 || end < 0) {
            return;
        }
        if (start > end) {
            final int tmp = start;
            start = end;
            end = tmp;
        }
        if (start >= end) {
            return;
        }
        final boolean add = (getCurrentStyle(start, end) & flag) == 0;
        if (add) {
            int clearMask;
            if (flag == TextStyleSpan.FLAG_STYLE_MONO) {
                clearMask = TextStyleSpan.FLAG_STYLE_BOLD | TextStyleSpan.FLAG_STYLE_ITALIC | TextStyleSpan.FLAG_STYLE_UNDERLINE
                    | TextStyleSpan.FLAG_STYLE_STRIKE | TextStyleSpan.FLAG_STYLE_SPOILER
                    | TextStyleSpan.FLAG_STYLE_SUBSCRIPT | TextStyleSpan.FLAG_STYLE_SUPERSCRIPT;
            } else {
                clearMask = TextStyleSpan.FLAG_STYLE_MONO;
                if (flag == TextStyleSpan.FLAG_STYLE_SUBSCRIPT) clearMask |= TextStyleSpan.FLAG_STYLE_SUPERSCRIPT;
                else if (flag == TextStyleSpan.FLAG_STYLE_SUPERSCRIPT) clearMask |= TextStyleSpan.FLAG_STYLE_SUBSCRIPT;
            }
            removeStyle(clearMask, start, end);
            addStyle(flag, start, end);
        } else {
            removeStyle(flag, start, end);
        }
    }

    public void makeSelectedQuote() {
        makeSelectedQuote(false);
    }

    public void makeSelectedQuote(boolean collapse) {
        int start, end;
        if (selectionStart >= 0 && selectionEnd >= 0) {
            start = selectionStart;
            end = selectionEnd;
            selectionStart = selectionEnd = -1;
        } else {
            start = getSelectionStart();
            end = getSelectionEnd();
        }
        final int setSelection = QuoteSpan.putQuoteToEditable(getText(), start, end, collapse);
        if (setSelection >= 0) {
            setSelection(setSelection);
            resetFontMetricsCache();
        }
        invalidateQuotes(true);
        invalidateSpoilers();
    }

    public void makeSelectedDate() {
        int start, end;
        if (selectionStart >= 0 && selectionEnd >= 0) {
            start = selectionStart;
            end = selectionEnd;
            selectionStart = selectionEnd = -1;
        } else {
            start = getSelectionStart();
            end = getSelectionEnd();
        }

        AlertsCreator.createFormattedDatePickerDialog(getContext(), (scheduleDate, flags) -> {
            Editable editable = getText();

            TextStyleSpan.TextStyleRun run = new TextStyleSpan.TextStyleRun();
            run.flags |= TextStyleSpan.FLAG_STYLE_URL;
            run.start = start;
            run.end = end;

            TLRPC.TL_messageEntityFormattedDate entity = new TLRPC.TL_messageEntityFormattedDate();
            entity.date = scheduleDate;
            entity.flags = flags;
            entity.applyFlags();

            try {
                editable.setSpan(new FormattedDateSpan(editable.subSequence(start, end).toString(), run, entity), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } catch (Exception ignore) {

            }
            if (delegate != null) {
                delegate.onSpansChanged();
            }
        }, () -> {}, resourcesProvider);
    }

    public void translateSelected() {
        int start, end;
        if (selectionStart >= 0 && selectionEnd >= 0) {
            start = selectionStart;
            end = selectionEnd;
            selectionStart = selectionEnd = -1;
        } else {
            start = getSelectionStart();
            end = getSelectionEnd();
        }

        final CharSequence text = getText().subSequence(start, end);

        final BaseFragment lastFragment = LaunchActivity.getSafeLastFragment();

        new TranslateAlert3(getContext(), lastFragment != null ? lastFragment.getResourceProvider() : null)
            .setText(text)
            .setOnUse(translatedText -> {
                getText().replace(start, end, translatedText);
                setSelection(start, start + translatedText.length());
            })
            .show();

        setSelection(start, end);
    }

    public void makeSelectedUrl() {
        makeSelectedUrl(null);
    }

    public void makeSelectedUrl(Runnable onApplied) {
        final int start;
        final int end;
        if (selectionStart >= 0 && selectionEnd >= 0) {
            start = selectionStart;
            end = selectionEnd;
            selectionStart = selectionEnd = -1;
        } else {
            start = getSelectionStart();
            end = getSelectionEnd();
        }
        String existingUrl = null;
        if (start >= 0 && end > start) {
            final Editable text = getText();
            final URLSpan[] urlSpans = text.getSpans(start, end, URLSpan.class);
            for (URLSpan span : urlSpans) {
                if (text.getSpanStart(span) <= start && text.getSpanEnd(span) >= end) {
                    existingUrl = span.getURL();
                    break;
                }
            }
            if (existingUrl == null && urlSpans.length == 1) {
                existingUrl = urlSpans[0].getURL();
            }
        }
        final boolean hasExistingUrl = !TextUtils.isEmpty(existingUrl);

        showInputDialog(
            LocaleController.getString(R.string.CreateLink),
            LocaleController.getString(R.string.URL),
            hasExistingUrl ? existingUrl : "https://",
            !hasExistingUrl,
            url -> {
                Editable editable = getText();
                CharacterStyle[] spans = editable.getSpans(start, end, CharacterStyle.class);
                if (spans != null && spans.length > 0) {
                    for (CharacterStyle oldSpan : spans) {
                        if (!(oldSpan instanceof AnimatedEmojiSpan) && !(oldSpan instanceof QuoteSpan.QuoteStyleSpan)) {
                            int spanStart = editable.getSpanStart(oldSpan);
                            int spanEnd = editable.getSpanEnd(oldSpan);
                            editable.removeSpan(oldSpan);
                            if (spanStart < start) {
                                editable.setSpan(oldSpan, spanStart, start, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                            }
                            if (spanEnd > end) {
                                editable.setSpan(oldSpan, end, spanEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                            }
                        }
                    }
                }
                try {
                    editable.setSpan(createUrlSpan(url), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                } catch (Exception ignore) {}
                if (delegate != null) {
                    delegate.onSpansChanged();
                }
                if (onApplied != null) {
                    onApplied.run();
                }
            }
        );
    }

    public interface InputDialogCallback {
        void run(String value);
    }

    protected URLSpanReplacement createUrlSpan(String url) {
        return new URLSpanReplacement(url);
    }

    /** Shared single-line editor that keeps an already-open keyboard in place when adaptive mode is enabled. */
    public void showInputDialog(String title, String hint, String initial, boolean showPaste,
                                InputDialogCallback callback) {
        showInputDialog(title, hint, initial, showPaste, adaptiveCreateLinkDialog, callback);
    }

    public void showInputDialog(String title, String hint, String initial, boolean showPaste,
                                boolean adaptive, InputDialogCallback callback) {
        AlertDialog.Builder builder;
        if (adaptive) {
            builder = new AlertDialogDecor.Builder(getContext(), resourcesProvider);
        } else {
            builder = new AlertDialog.Builder(getContext(), resourcesProvider);
        }
        builder.setTitle(title);

        FrameLayout container = new FrameLayout(getContext());
        final EditTextBoldCursor editText = new EditTextBoldCursor(getContext()) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(64), MeasureSpec.EXACTLY));
            }
        };
        final String def = initial == null ? "" : initial;
        editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        editText.setText(def);
        ThemeBind.textColor(editText, Theme.key_dialogTextBlack, resourcesProvider);
        editText.setHintText(hint);
        ThemeBind.editHeaderHintColor(editText, Theme.key_windowBackgroundWhiteBlueHeader, resourcesProvider);
        editText.setSingleLine(true);
        editText.setFocusable(true);
        editText.setTransformHintToHeader(true);
        ThemeBind.lineColors(editText, Theme.key_windowBackgroundWhiteInputField, Theme.key_windowBackgroundWhiteInputFieldActivated, Theme.key_text_RedRegular, resourcesProvider);
        editText.setImeOptions(EditorInfo.IME_ACTION_DONE);
        editText.setBackgroundDrawable(null);
        editText.requestFocus();
        editText.setPadding(0, 0, 0, 0);
        ThemeBind.highlightColor(editText, Theme.key_chat_inTextSelectionHighlight, resourcesProvider);
        ThemeBind.editHandlesColor(editText, Theme.key_chat_TextSelectionCursor, resourcesProvider);
        container.addView(editText, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));

        TextView pasteTextView = new TextView(getContext());
        pasteTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        pasteTextView.setTypeface(AndroidUtilities.bold());
        pasteTextView.setText(getString(R.string.Paste));
        pasteTextView.setPadding(dp(10), 0, dp(10), 0);
        pasteTextView.setGravity(Gravity.CENTER);
        int textColor = getThemedColor(Theme.key_windowBackgroundWhiteBlueText2);
        ThemeBind.textColor(pasteTextView, Theme.key_windowBackgroundWhiteBlueText2, resourcesProvider);
        ThemeBind.selectorBackground(pasteTextView, Theme.createSimpleSelectorRoundRectDrawable(dp(6), Theme.multAlpha(textColor, .12f), Theme.multAlpha(textColor, .15f)), ThemeBind.multAlpha(Theme.key_windowBackgroundWhiteBlueText2, .12f), ThemeBind.multAlpha(Theme.key_windowBackgroundWhiteBlueText2, .15f), resourcesProvider);
        ScaleStateListAnimator.apply(pasteTextView, .1f, 1.5f);
        container.addView(pasteTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 26, Gravity.RIGHT | Gravity.CENTER_VERTICAL, 0, 0, 24, 3));
        pasteTextView.setVisibility(showPaste ? VISIBLE : GONE);

        Runnable checkPaste = () -> {
            ClipboardManager clipboardManager = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
            final boolean show = showPaste && (TextUtils.isEmpty(editText.getText()) || TextUtils.equals(editText.getText().toString(), def)) && clipboardManager != null && clipboardManager.hasPrimaryClip();
            pasteTextView.animate()
                .alpha(show ? 1f : 0f)
                .scaleX(show ? 1f : .7f)
                .scaleY(show ? 1f : .7f)
                .setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT)
                .setDuration(300)
                .start();
        };
        pasteTextView.setOnClickListener(v -> {
            ClipboardManager clipboardManager = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
            CharSequence text = null;
            try {
                text = clipboardManager.getPrimaryClip().getItemAt(0).coerceToText(getContext());
            } catch (Exception e) {
                FileLog.e(e);
            }
            if (text != null) {
                editText.setText(text);
                editText.setSelection(0, editText.getText().length());
            }
            checkPaste.run();
        });
        editText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                checkPaste.run();
            }
        });

        ClipboardManager clipboardManager = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
        if (showPaste && (TextUtils.equals(def, "https://") || TextUtils.equals(def, "http://")) && clipboardManager != null && clipboardManager.hasPrimaryClip()) {
            CharSequence text = null;
            try {
                text = clipboardManager.getPrimaryClip().getItemAt(0).coerceToText(getContext());
            } catch (Exception e) {
                FileLog.e(e);
            }
            if (text != null) {
                editText.setText(text);
                editText.setSelection(0, editText.getText().length());
            }
        }

        checkPaste.run();

        builder.setView(container);

        builder.setPositiveButton(LocaleController.getString(R.string.OK), (dialogInterface, i) -> {
            callback.run(editText.getText().toString().trim());
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        if (adaptive) {
            creationLinkDialog = builder.create();
            creationLinkDialog.setOnDismissListener(dialog -> {
                creationLinkDialog = null;
                requestFocus();
            });
            creationLinkDialog.setOnShowListener(dialog -> {
                editText.requestFocus();
                AndroidUtilities.showKeyboard(editText);
            });
            creationLinkDialog.showDelayed(250);
        } else {
            builder.show().setOnShowListener(dialog -> {
                editText.requestFocus();
                AndroidUtilities.showKeyboard(editText);
            });
        }
        if (editText != null) {
            ViewGroup.MarginLayoutParams layoutParams = (ViewGroup.MarginLayoutParams) editText.getLayoutParams();
            if (layoutParams != null) {
                if (layoutParams instanceof FrameLayout.LayoutParams) {
                    ((FrameLayout.LayoutParams) layoutParams).gravity = Gravity.CENTER_HORIZONTAL;
                }
                layoutParams.rightMargin = layoutParams.leftMargin = AndroidUtilities.dp(24);
                layoutParams.height = AndroidUtilities.dp(36);
                editText.setLayoutParams(layoutParams);
            }
            editText.setSelection(0, editText.getText().length());
        }
    }

    public boolean closeCreationLinkDialog(boolean invoked) {
        if (creationLinkDialog != null && creationLinkDialog.isShowing()) {
            if (invoked) creationLinkDialog.dismiss();
            return true;
        }
        return false;
    }

    public void makeSelectedRegular() {
        applyTextStyleToSelection(null);
    }

    public void setSelectionOverride(int start, int end) {
        selectionStart = start;
        selectionEnd = end;
    }

    private static final int[] STYLE_FLAGS = {
        TextStyleSpan.FLAG_STYLE_BOLD,
        TextStyleSpan.FLAG_STYLE_ITALIC,
        TextStyleSpan.FLAG_STYLE_MONO,
        TextStyleSpan.FLAG_STYLE_STRIKE,
        TextStyleSpan.FLAG_STYLE_UNDERLINE,
        TextStyleSpan.FLAG_STYLE_SPOILER,
        TextStyleSpan.FLAG_STYLE_SUBSCRIPT,
        TextStyleSpan.FLAG_STYLE_SUPERSCRIPT,
    };

    private static int spanStyleFlags(TextStyleSpan span) {
        int flags = span.getStyleFlags();
        if ((flags & TextStyleSpan.FLAG_STYLE_SPOILER_REVEALED) != 0) {
            flags |= TextStyleSpan.FLAG_STYLE_SPOILER;
        }
        return flags;
    }

    @Override
    public int getCurrentStyle(int start, int end) {
        Editable editable = getText();
        if (editable == null) {
            return 0;
        }
        start = Math.max(0, start);
        end = Math.min(end, editable.length());
        if (start < 0 || end < 0 || start >= end) {
            return 0;
        }
        TextStyleSpan[] spans = editable.getSpans(start, end, TextStyleSpan.class);
        int result = 0;
        for (int flag : STYLE_FLAGS) {
            // spans aren't returned in position order, so loop to a fixpoint extending
            // the covered range by any span of this flag that touches it
            int covered = start;
            boolean advanced = true;
            while (advanced && covered < end) {
                advanced = false;
                for (int a = 0; a < spans.length; ++a) {
                    if ((spanStyleFlags(spans[a]) & flag) == 0) {
                        continue;
                    }
                    int spanStart = editable.getSpanStart(spans[a]);
                    int spanEnd = editable.getSpanEnd(spans[a]);
                    if (spanStart <= covered && spanEnd > covered) {
                        covered = spanEnd;
                        advanced = true;
                    }
                }
            }
            if (covered >= end) {
                result |= flag;
            }
        }
        return result;
    }

    @Override
    public void addStyle(int flag, int start, int end) {
        Editable editable = getText();
        if (editable == null || start < 0 || end < 0 || start >= end) {
            return;
        }
        end = Math.min(end, editable.length());
        if (start >= end) {
            return;
        }
        TextStyleSpan.TextStyleRun run = new TextStyleSpan.TextStyleRun();
        run.flags = flag;
        MediaDataController.addStyleToText(new TextStyleSpan(run), start, end, editable, true);
        if ((flag & TextStyleSpan.FLAG_STYLE_SPOILER) != 0) {
            invalidateSpoilers();
        }
        if (delegate != null) {
            delegate.onSpansChanged();
        }
    }

    @Override
    public void removeStyle(int flag, int start, int end) {
        Editable editable = getText();
        if (editable == null || start < 0 || end < 0 || start >= end) {
            return;
        }
        end = Math.min(end, editable.length());
        int removeMask = flag;
        if ((flag & TextStyleSpan.FLAG_STYLE_SPOILER) != 0) {
            removeMask |= TextStyleSpan.FLAG_STYLE_SPOILER_REVEALED;
        }
        TextStyleSpan[] spans = editable.getSpans(start, end, TextStyleSpan.class);
        for (int a = 0; a < spans.length; ++a) {
            TextStyleSpan span = spans[a];
            int spanFlags = span.getStyleFlags();
            if ((spanFlags & removeMask) == 0) {
                continue;
            }
            int spanStart = editable.getSpanStart(span);
            int spanEnd = editable.getSpanEnd(span);
            editable.removeSpan(span);
            if (spanStart < start) {
                TextStyleSpan.TextStyleRun run = new TextStyleSpan.TextStyleRun(span.getTextStyleRun());
                editable.setSpan(new TextStyleSpan(run), spanStart, start, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            if (spanEnd > end) {
                TextStyleSpan.TextStyleRun run = new TextStyleSpan.TextStyleRun(span.getTextStyleRun());
                editable.setSpan(new TextStyleSpan(run), end, spanEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            int interStart = Math.max(spanStart, start);
            int interEnd = Math.min(spanEnd, end);
            int newFlags = spanFlags & ~removeMask;
            if (newFlags != 0 && interStart < interEnd) {
                TextStyleSpan.TextStyleRun run = new TextStyleSpan.TextStyleRun(span.getTextStyleRun());
                run.flags = newFlags;
                editable.setSpan(new TextStyleSpan(run), interStart, interEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        if ((flag & TextStyleSpan.FLAG_STYLE_SPOILER) != 0) {
            invalidateSpoilers();
        }
        if (delegate != null) {
            delegate.onSpansChanged();
        }
    }

    private void applyTextStyleToSelection(TextStyleSpan span) {
        int start;
        int end;
        if (selectionStart >= 0 && selectionEnd >= 0) {
            start = selectionStart;
            end = selectionEnd;
            selectionStart = selectionEnd = -1;
        } else {
            start = getSelectionStart();
            end = getSelectionEnd();
        }
        MediaDataController.addStyleToText(span, start, end, getText(), allowTextEntitiesIntersection);

        if (span == null) {
            Editable editable = getText();
            CodeHighlighting.Span[] code = editable.getSpans(start, end, CodeHighlighting.Span.class);
            for (int i = 0; i < code.length; ++i)
                editable.removeSpan(code[i]);
            QuoteSpan[] quotes = editable.getSpans(start, end, QuoteSpan.class);
            for (int i = 0; i < quotes.length; ++i) {
                editable.removeSpan(quotes[i]);
                editable.removeSpan(quotes[i].styleSpan);
                if (quotes[i].collapsedSpan != null) {
                    editable.removeSpan(quotes[i].collapsedSpan);
                }
            }
            if (quotes.length > 0) {
                invalidateQuotes(true);
            }
        }

        if (delegate != null) {
            delegate.onSpansChanged();
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        if (Build.VERSION.SDK_INT < 23 && !hasWindowFocus && copyPasteShowed) {
            return;
        }
        try {
            super.onWindowFocusChanged(hasWindowFocus);
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    protected void onContextMenuOpen() {

    }

    protected void onContextMenuClose() {

    }

    private ActionMode.Callback overrideCallback(final ActionMode.Callback callback) {
        ActionMode.Callback wrap = new ActionMode.Callback() {
            @Override
            public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                copyPasteShowed = true;
                onContextMenuOpen();
                return callback.onCreateActionMode(mode, menu);
            }

            @Override
            public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                return callback.onPrepareActionMode(mode, menu);
            }

            @Override
            public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                if (performMenuAction(item.getItemId())) {
                    mode.finish();
                    return true;
                }
                try {
                    return callback.onActionItemClicked(mode, item);
                } catch (Exception ignore) {

                }
                return true;
            }

            @Override
            public void onDestroyActionMode(ActionMode mode) {
                copyPasteShowed = false;
                onContextMenuClose();
                callback.onDestroyActionMode(mode);
            }
        };
        if (Build.VERSION.SDK_INT >= 23) {
            return new ActionMode.Callback2() {
                @Override
                public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                    return wrap.onCreateActionMode(mode, menu);
                }

                @Override
                public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                    return wrap.onPrepareActionMode(mode, menu);
                }

                @Override
                public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                    return wrap.onActionItemClicked(mode, item);
                }

                @Override
                public void onDestroyActionMode(ActionMode mode) {
                    wrap.onDestroyActionMode(mode);
                }

                @Override
                public void onGetContentRect(ActionMode mode, View view, Rect outRect) {
                    if (callback instanceof ActionMode.Callback2) {
                        ((ActionMode.Callback2) callback).onGetContentRect(mode, view, outRect);
                    } else {
                        super.onGetContentRect(mode, view, outRect);
                    }
                }
            };
        } else {
            return wrap;
        }
    }

    public boolean performMenuAction(int itemId) {
        if (itemId == R.id.menu_regular) {
            makeSelectedRegular();
            return true;
        } else if (itemId == R.id.menu_bold) {
            makeSelectedBold();
            return true;
        } else if (itemId == R.id.menu_italic) {
            makeSelectedItalic();
            return true;
        } else if (itemId == R.id.menu_bold_italic) {
            makeSelectedBold();
            makeSelectedItalic();
            return true;
        } else if (itemId == R.id.menu_mono) {
            makeSelectedMono();
            return true;
        } else if (itemId == R.id.menu_code) {
            makeSelectedCode();
            return true;
        } else if (itemId == R.id.menu_link) {
            makeSelectedUrl();
            return true;
        } else if (itemId == R.id.menu_strike) {
            makeSelectedStrike();
            return true;
        } else if (itemId == R.id.menu_underline) {
            makeSelectedUnderline();
            return true;
        } else if (itemId == R.id.menu_spoiler) {
            makeSelectedSpoiler();
            return true;
        } else if (itemId == R.id.menu_quote) {
            makeSelectedQuote();
            return true;
        } else if (itemId == R.id.menu_date) {
            makeSelectedDate();
            return true;
        } else if (itemId == R.id.menu_translate) {
            translateSelected();
            return true;
        }
        return false;
    }

    @Override
    public ActionMode startActionMode(final ActionMode.Callback callback, int type) {
        return super.startActionMode(overrideCallback(callback), type);
    }

    @Override
    public ActionMode startActionMode(final ActionMode.Callback callback) {
        return super.startActionMode(overrideCallback(callback));
    }

    private int lastLayoutWidth;
    private CharSequence lastLayoutText;

    @SuppressLint("DrawAllocation")
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        try {
            isInitLineCount = getMeasuredWidth() == 0 && getMeasuredHeight() == 0;
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            if (isInitLineCount) {
                lineCount = getLineCount();
            }
            isInitLineCount = false;
        } catch (Exception e) {
            setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), AndroidUtilities.dp(51));
            FileLog.e(e);
        }

        int availableWidth = getMeasuredWidth() - getPaddingLeft() - getPaddingRight();
        CharSequence text = getText();

        captionLayout = null;
        lastLayoutText = caption;
        lastLayoutWidth = availableWidth;

        if (caption != null && caption.length() > 0) {
            if (text.length() > 1 && text.charAt(0) == '@') {
                int index = TextUtils.indexOf(text, ' ');
                if (index != -1) {
                    TextPaint paint = getPaint();
                    paint.setTypeface(AndroidUtilities.regular());
                    CharSequence str = text.subSequence(0, index + 1);
                    int size = (int) Math.ceil(paint.measureText(text, 0, index + 1));
                    int layoutWidth = Math.max(0, availableWidth - size);
                    userNameLength = str.length();
                    CharSequence captionFinal = TextUtils.ellipsize(caption, paint, layoutWidth, TextUtils.TruncateAt.END);
                    xOffset = size;
                    try {
                        captionLayout = new StaticLayout(captionFinal, paint, layoutWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
                        if (captionLayout.getLineCount() > 0) {
                            xOffset += -captionLayout.getLineLeft(0);
                        }
                        yOffset = (getMeasuredHeight() - captionLayout.getLineBottom(0)) / 2 + AndroidUtilities.dp(0.5f);
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                }
            }
        }
    }

    public boolean isNearRightCaption(int rightMargin) {
        final Layout layout = getLayout();
        if (layout == null || layout.getLineCount() <= 0) return false;
        if (layout.getLineCount() > 1) return true;
        final int limit = getWidth() - getPaddingLeft() - getPaddingRight() - rightMargin;
        final InlineMathController inlineMath = getInlineMath();
        if (inlineMath != null && inlineMath.extendsPast(limit)) return true;
        return layout.getLineRight(0) >= limit;
    }

    public String getCaption() {
        return caption;
    }

    @Override
    public void setHintColor(int value) {
        super.setHintColor(value);
        hintColor = value;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (quoteColor == themedQuoteColor) {
            // follow theme changes unless a custom quote color was set
            quoteColor = themedQuoteColor = Theme.getColor(Theme.key_chat_inQuote, resourcesProvider);
        }
        canvas.save();
        canvas.translate(0, offsetY);
        super.onDraw(canvas);
        try {
            if (captionLayout != null && userNameLength == length()) {
                Paint paint = getPaint();
                int oldColor = getPaint().getColor();
                paint.setColor(hintColor);
                canvas.save();
                canvas.translate(xOffset, yOffset);
                captionLayout.draw(canvas);
                canvas.restore();
                paint.setColor(oldColor);
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        if (rightText != null && length() != 0) {
            final Layout layout = getLayout();
            if (layout != null && layout.getLineCount() > 0) {
                final float right = layout.getLineRight(0);
                rightText.draw(canvas, right, getHeight() / 2f + dp(1), hintColor, 1.0f);
            }
        }
        canvas.restore();
    }

    public void setRightText(CharSequence text) {
        this.rightText = new Text(text, 16, getTypeface());
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        final AccessibilityNodeInfoCompat infoCompat = AccessibilityNodeInfoCompat.wrap(info);
        if (!TextUtils.isEmpty(caption)) {
            infoCompat.setHintText(caption);
        }
        final List<AccessibilityNodeInfoCompat.AccessibilityActionCompat> actions = infoCompat.getActionList();
        for (int i = 0, size = actions.size(); i < size; i++) {
            final AccessibilityNodeInfoCompat.AccessibilityActionCompat action = actions.get(i);
            if (action.getId() == ACCESSIBILITY_ACTION_SHARE) {
                infoCompat.removeAction(action);
                break;
            }
        }
        if (hasSelection()) {
            infoCompat.addAction(new AccessibilityNodeInfoCompat.AccessibilityActionCompat(R.id.menu_spoiler, LocaleController.getString(R.string.Spoiler)));
            infoCompat.addAction(new AccessibilityNodeInfoCompat.AccessibilityActionCompat(R.id.menu_bold, LocaleController.getString(R.string.Bold)));
            infoCompat.addAction(new AccessibilityNodeInfoCompat.AccessibilityActionCompat(R.id.menu_italic, LocaleController.getString(R.string.Italic)));
            infoCompat.addAction(new AccessibilityNodeInfoCompat.AccessibilityActionCompat(R.id.menu_bold_italic, LocaleController.getString(R.string.BoldItalic)));
            infoCompat.addAction(new AccessibilityNodeInfoCompat.AccessibilityActionCompat(R.id.menu_mono, LocaleController.getString(R.string.Mono)));
            infoCompat.addAction(new AccessibilityNodeInfoCompat.AccessibilityActionCompat(R.id.menu_code, LocaleController.getString(R.string.CodeBlock)));
            infoCompat.addAction(new AccessibilityNodeInfoCompat.AccessibilityActionCompat(R.id.menu_strike, LocaleController.getString(R.string.Strike)));
            infoCompat.addAction(new AccessibilityNodeInfoCompat.AccessibilityActionCompat(R.id.menu_underline, LocaleController.getString(R.string.Underline)));
            infoCompat.addAction(new AccessibilityNodeInfoCompat.AccessibilityActionCompat(R.id.menu_link, LocaleController.getString(R.string.CreateLink)));
            infoCompat.addAction(new AccessibilityNodeInfoCompat.AccessibilityActionCompat(R.id.menu_regular, LocaleController.getString(R.string.Regular)));
            infoCompat.addAction(new AccessibilityNodeInfoCompat.AccessibilityActionCompat(R.id.menu_date, LocaleController.getString(R.string.FormattedDate)));
        }
    }

    @Override
    public boolean performAccessibilityAction(int action, Bundle arguments) {
        return performMenuAction(action) || super.performAccessibilityAction(action, arguments);
    }

    private int getThemedColor(int key) {
        return Theme.getColor(key, resourcesProvider);
    }

    @Override
    public boolean onTextContextMenuItem(int id) {
        if (id == android.R.id.paste) {
            ClipboardManager clipboard = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clipData = clipboard.getPrimaryClip();
            if (handleRichPaste(clipData)) {
                return true;
            }
        } else if (id == android.R.id.copy) {
            int start = Math.max(0, getSelectionStart());
            int end = Math.min(getText().length(), getSelectionEnd());
            try {
                AndroidUtilities.addToClipboard(getText().subSequence(start, end));
                Activity activity = AndroidUtilities.findActivity(getContext());
                activity.closeContextMenu();
                if (floatingActionMode != null) {
                    floatingActionMode.finish();
                }
                setSelection(start, end);
                return true;
            } catch (Exception e) {}
        } else if (id == android.R.id.cut) {
            int start = Math.max(0, getSelectionStart());
            int end = Math.min(getText().length(), getSelectionEnd());
            try {
                AndroidUtilities.addToClipboard(getText().subSequence(start, end));
                SpannableStringBuilder stringBuilder = new SpannableStringBuilder();
                if (start != 0) {
                    stringBuilder.append(getText().subSequence(0, start));
                }
                if (end != getText().length()) {
                    stringBuilder.append(getText().subSequence(end, getText().length()));
                }
                setText(stringBuilder);
                setSelection(start);
                return true;
            } catch (Exception e) {}
        }
        return super.onTextContextMenuItem(id);
    }

    @Override
    public InputConnection onCreateInputConnection(@NonNull EditorInfo outAttrs) {
        InputConnection ic = super.onCreateInputConnection(outAttrs);
        if (ic == null) {
            return null;
        }

        String[] currentMimeTypes = EditorInfoCompat.getContentMimeTypes(outAttrs);
        LinkedHashSet<String> mimeTypes = new LinkedHashSet<>();
        Collections.addAll(mimeTypes, currentMimeTypes);
        mimeTypes.add("text/html");
        mimeTypes.add("text/plain");
        EditorInfoCompat.setContentMimeTypes(outAttrs, mimeTypes.toArray(new String[0]));

        return new InputConnectionWrapper(ic, true) {
            @Override
            public boolean commitText(CharSequence text, int newCursorPosition) {
                if (text != null && text.length() > 1) {
                    try {
                        ClipboardManager clipboard = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
                        if (clipboard != null && clipboard.hasPrimaryClip() &&
                                clipboard.getPrimaryClipDescription().hasMimeType("text/html")) {

                            ClipData primaryClip = clipboard.getPrimaryClip();
                            if (primaryClip != null && primaryClip.getItemCount() > 0) {
                                CharSequence clipText = primaryClip.getItemAt(0).coerceToText(getContext());
                                if (clipText != null && TextUtils.equals(text, clipText)) {
                                    if (handleRichPaste(primaryClip)) {
                                        return true;
                                    }
                                }
                            }
                        }
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                }
                return super.commitText(text, newCursorPosition);
            }
        };
    }

    private boolean handleRichPaste(ClipData clipData) {
        if (clipData == null || clipData.getItemCount() == 0) return false;

        ClipDescription description = clipData.getDescription();

        if (description.hasMimeType("image/*") ||
                description.hasMimeType("video/*") ||
                description.hasMimeType("audio/*")) {
            return false;
        }

        boolean isHtml = clipData.getDescription().hasMimeType("text/html");
        boolean hasUri = clipData.getItemAt(0).getUri() != null;

        if (isHtml || hasUri) {
            try {
                ClipData.Item item = clipData.getItemAt(0);
                String html = item.getHtmlText();

                if (html == null && item.getUri() != null) {
                    html = readHtmlFromUri(item.getUri());
                }

                if (html == null) return false;

                SpannableStringBuilder pasted = new SpannableStringBuilder(CopyUtilities.fromHTML(html));
                Emoji.replaceEmoji(pasted, getPaint().getFontMetricsInt(), false, null);
                AnimatedEmojiSpan[] spans = pasted.getSpans(0, pasted.length(), AnimatedEmojiSpan.class);
                if (spans != null) {
                    for (AnimatedEmojiSpan span : spans) {
                        span.applyFontMetrics(getPaint().getFontMetricsInt(), AnimatedEmojiDrawable.getCacheTypeForEnterView());
                    }
                }
                int start = Math.max(0, getSelectionStart());
                int end = Math.min(getText().length(), getSelectionEnd());
                QuoteSpan.QuoteStyleSpan[] quotesInSelection = getText().getSpans(start, end, QuoteSpan.QuoteStyleSpan.class);
                if (quotesInSelection != null && quotesInSelection.length > 0) {
                    QuoteSpan.QuoteStyleSpan[] quotesToDelete = pasted.getSpans(0, pasted.length(), QuoteSpan.QuoteStyleSpan.class);
                    for (QuoteSpan.QuoteStyleSpan quoteStyleSpan : quotesToDelete) {
                        pasted.removeSpan(quoteStyleSpan);
                        pasted.removeSpan(quoteStyleSpan.span);
                    }
                } else {
                    QuoteSpan.normalizeQuotes(pasted);
                }
                setText(getText().replace(start, end, pasted));
                setSelection(start + pasted.length(), start + pasted.length());
                return true;
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        return false;
    }

    private String readHtmlFromUri(android.net.Uri uri) {
        try (InputStream stream = getContext().getContentResolver().openInputStream(uri)) {
            if (stream == null) return null;

            StringBuilder sb = new StringBuilder();
            byte[] buffer = new byte[4096];
            int bytesRead;
            int totalRead = 0;

            final int MAX_BYTES = 1024 * 32;

            while ((bytesRead = stream.read(buffer)) != -1) {
                sb.append(new String(buffer, 0, bytesRead));
                totalRead += bytesRead;
                if (totalRead >= MAX_BYTES) {
                    break;
                }
            }
            return sb.toString();
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        }
    }
}
