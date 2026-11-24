/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.export.ui;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.exteragram.messenger.ExteraConfig;
import com.exteragram.messenger.export.ExportState;
import com.exteragram.messenger.utils.ui.GlyphBounds;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.tgnet.TLObject;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedTextView;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.IconBackgroundColors;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.LineProgressView;

import java.util.Objects;

public class ExportProgressCell extends LinearLayout {

    private static final int ROW_HEIGHT_DP = 60;
    private static final int ICON_SIZE_DP = 40;
    private static final int TEXT_LEFT_DP = 72;
    private static final int GLYPH_BOX_DP = 24;
    private static final float GLYPH_TARGET_DP = 20f;

    private final SparseArray<float[]> glyphFits = new SparseArray<>();

    private final Theme.ResourcesProvider resourcesProvider;
    private final IconSwitcher iconView;
    private final AnimatedTextView titleView;
    private final AnimatedTextView counterView;
    private final LinearLayout fileSection;
    private final IconSwitcher fileIconView;
    private final FileNameView fileNameView;
    private final AnimatedTextView fileCounterView;
    private final LineProgressView fileProgressView;

    private boolean fileVisible;
    private float fileFraction;
    private ValueAnimator fileAnimator;
    private boolean titleCentered;
    private boolean fileProgressResetting;

    public ExportProgressCell(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        setOrientation(VERTICAL);
        setBackground(Theme.createRoundRectDrawable(dp(ExteraConfig.getSectionRadiusDp()), color(Theme.key_windowBackgroundWhite)));

        FrameLayout header = new FrameLayout(context);
        addView(header, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, ROW_HEIGHT_DP));

        iconView = new IconSwitcher(context);
        header.addView(iconView, LayoutHelper.createFrame(ICON_SIZE_DP, ICON_SIZE_DP, Gravity.LEFT | Gravity.CENTER_VERTICAL, 16, 0, 0, 0));

        titleView = createText(context, 16, Theme.key_windowBackgroundWhiteBlackText, true);
        NotificationCenter.listenEmojiLoading(titleView);
        header.addView(titleView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 21, Gravity.LEFT | Gravity.TOP, TEXT_LEFT_DP, 10, 16, 0));

        counterView = createText(context, 14, Theme.key_windowBackgroundWhiteGrayText2, false);
        header.addView(counterView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 19, Gravity.LEFT | Gravity.TOP, TEXT_LEFT_DP, 31, 16, 0));

        fileSection = new LinearLayout(context);
        fileSection.setOrientation(VERTICAL);
        addView(fileSection, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        View dividerView = new View(context);
        dividerView.setBackgroundColor(color(Theme.key_divider));
        LayoutParams dividerParams = new LayoutParams(LayoutHelper.MATCH_PARENT, 1);
        dividerParams.leftMargin = dp(TEXT_LEFT_DP);
        fileSection.addView(dividerView, dividerParams);

        FrameLayout fileRow = new FrameLayout(context);
        fileSection.addView(fileRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, ROW_HEIGHT_DP));

        fileIconView = new IconSwitcher(context);
        fileRow.addView(fileIconView, LayoutHelper.createFrame(ICON_SIZE_DP, ICON_SIZE_DP, Gravity.LEFT | Gravity.CENTER_VERTICAL, 16, 0, 0, 0));

        fileNameView = new FileNameView(context);
        fileRow.addView(fileNameView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 21, Gravity.LEFT | Gravity.TOP, TEXT_LEFT_DP, 10, 16, 0));

        fileCounterView = createText(context, 13, Theme.key_windowBackgroundWhiteGrayText2, false);
        fileProgressView = new LineProgressView(context);
        fileProgressView.setTrackThickness(dp(3));
        fileProgressView.setTrackStopIndicatorSize(0);
        fileProgressView.setProgressColor(color(Theme.key_featuredStickers_addButton));
        fileProgressView.setBackColor(color(Theme.key_dialogLineProgressBackground));

        FrameLayout fileLine = new FrameLayout(context) {
            @Override
            protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                super.onLayout(changed, left, top, right, bottom);
                layoutFileProgress();
            }
        };
        fileLine.addView(fileCounterView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 19, Gravity.LEFT | Gravity.CENTER_VERTICAL));
        fileLine.addView(fileProgressView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 3, Gravity.LEFT | Gravity.CENTER_VERTICAL));
        fileCounterView.setOnWidthUpdatedListener(this::layoutFileProgress);
        fileRow.addView(fileLine, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 19, Gravity.LEFT | Gravity.TOP, TEXT_LEFT_DP, 31, 16, 0));

        fileSection.setVisibility(GONE);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if (fileSection.getVisibility() == VISIBLE && fileFraction < 1f) {
            int height = getMeasuredHeight() - Math.round(fileSection.getMeasuredHeight() * (1f - fileFraction));
            setMeasuredDimension(getMeasuredWidth(), height);
        }
    }

    private void layoutFileProgress() {
        View line = (View) fileProgressView.getParent();
        if (line == null || line.getWidth() <= 0) {
            return;
        }
        int left = (int) Math.ceil(fileCounterView.getDrawable().getCurrentWidth()) + dp(10);
        int right = line.getWidth();
        if (left >= right) {
            left = right;
        }
        fileProgressView.layout(left, fileProgressView.getTop(), right, fileProgressView.getBottom());
    }

    private AnimatedTextView createText(Context context, int size, int colorKey, boolean bold) {
        AnimatedTextView view = new AnimatedTextView(context, bold, !bold, !bold);
        view.setAnimationProperties(.35f, 0, bold ? 320 : 220, CubicBezierInterpolator.EASE_OUT_QUINT);
        view.setTextColor(color(colorKey));
        view.setTextSize(dp(size));
        view.setTypeface(bold ? AndroidUtilities.bold() : AndroidUtilities.regular());
        view.setGravity(Gravity.LEFT);
        view.setEllipsizeByGradient(true);
        return view;
    }

    private int color(int key) {
        return Theme.getColor(key, resourcesProvider);
    }

    private View createGlyph(int iconRes, IconBackgroundColors colors) {
        boolean monet = Theme.isCurrentThemeMonet();
        FrameLayout circle = new FrameLayout(getContext());
        GradientDrawable background = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, monet
                ? new int[]{color(Theme.key_chats_actionBackground), color(Theme.key_chats_actionBackground)}
                : new int[]{colors.top, colors.bottom});
        background.setShape(GradientDrawable.OVAL);
        circle.setBackground(background);
        ImageView image = new ImageView(getContext());
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setColorFilter(new PorterDuffColorFilter(monet ? color(Theme.key_chats_actionIcon) : 0xffffffff, PorterDuff.Mode.SRC_IN));
        image.setImageResource(iconRes);
        float[] fit = glyphFit(getContext(), iconRes);
        image.setScaleX(fit[0]);
        image.setScaleY(fit[0]);
        image.setTranslationX(fit[1]);
        image.setTranslationY(fit[2]);
        circle.addView(image, LayoutHelper.createFrame(GLYPH_BOX_DP, GLYPH_BOX_DP, Gravity.CENTER));
        return circle;
    }

    private float[] glyphFit(Context context, int iconRes) {
        float[] cached = glyphFits.get(iconRes);
        if (cached != null) {
            return cached;
        }
        float[] fit = {1f, 0f, 0f};
        Drawable drawable = context.getDrawable(iconRes);
        RectF glyph = GlyphBounds.measure(drawable);
        if (drawable != null && glyph != null) {
            float box = dpf2(GLYPH_BOX_DP);
            int width = drawable.getIntrinsicWidth();
            int height = drawable.getIntrinsicHeight();
            float drawWidth = box;
            float drawHeight = box;
            if (width > 0 && height > 0) {
                float k = Math.min(box / width, box / height);
                drawWidth = width * k;
                drawHeight = height * k;
            }
            float glyphSize = Math.max(glyph.width() * drawWidth, glyph.height() * drawHeight);
            float max = Math.min(1.5f, GlyphBounds.maxUpscale(drawable, drawWidth));
            float scale = Math.max(.75f, Math.min(max, dpf2(GLYPH_TARGET_DP) / glyphSize));
            fit[0] = scale;
            fit[1] = (.5f - glyph.centerX()) * drawWidth * scale;
            fit[2] = (.5f - glyph.centerY()) * drawHeight * scale;
        }
        glyphFits.put(iconRes, fit);
        return fit;
    }

    private View createAvatar(int account, ExportState.Processing state, CharSequence title) {
        BackupImageView view = new BackupImageView(getContext());
        view.setRoundRadius(dp(ICON_SIZE_DP / 2f));
        AvatarDrawable drawable = new AvatarDrawable();
        if (state.entityType == ExportState.Processing.EntityType.SavedMessages) {
            drawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_SAVED);
            view.setImageDrawable(drawable);
            return view;
        }
        if (state.entityType == ExportState.Processing.EntityType.RepliesMessages) {
            drawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_REPLIES);
            view.setImageDrawable(drawable);
            return view;
        }
        long dialogId = state.entityDialogId;
        MessagesController controller = MessagesController.getInstance(account);
        TLObject peer = dialogId > 0 ? controller.getUser(dialogId) : dialogId < 0 ? controller.getChat(-dialogId) : null;
        if (peer != null) {
            drawable.setInfo(account, peer);
            view.setForUserOrChat(peer, drawable);
        } else {
            drawable.setInfo(dialogId, title.toString(), null);
            view.setImageDrawable(drawable);
        }
        return view;
    }

    public void setStep(int account, ExportState.Processing state, CharSequence title, CharSequence counter, boolean animated) {
        if (!TextUtils.equals(titleView.getText(), title)) {
            titleView.setText(Emoji.replaceEmoji(title, titleView.getPaint().getFontMetricsInt(), false), animated);
        }
        if (!TextUtils.equals(counterView.getText(), counter)) {
            counterView.setText(counter, animated);
        }
        setTitleCentered(TextUtils.isEmpty(counter), animated);
        boolean chat = state.step == ExportState.Processing.Step.Dialogs || state.step == ExportState.Processing.Step.Topic;
        if (!chat || state.entityType == ExportState.Processing.EntityType.Topic || state.entityType == ExportState.Processing.EntityType.VerifyCodes || state.entityType == ExportState.Processing.EntityType.Other) {
            int iconRes = ExportUiHelper.stepIcon(state);
            if (!iconView.hasKey(iconRes)) {
                iconView.set(iconRes, createGlyph(iconRes, ExportUiHelper.stepColor(state)), animated);
            }
            return;
        }
        String key = state.entityType == ExportState.Processing.EntityType.Chat ? "chat" + state.entityDialogId : state.entityType.name();
        if (!iconView.hasKey(key)) {
            iconView.set(key, createAvatar(account, state, title), animated);
        }
    }

    private void setTitleCentered(boolean centered, boolean animated) {
        if (titleCentered == centered) {
            return;
        }
        titleCentered = centered;
        float translation = centered ? dp(10) : 0;
        titleView.animate().cancel();
        if (animated) {
            titleView.animate().translationY(translation).setDuration(320).setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT).start();
        } else {
            titleView.setTranslationY(translation);
        }
    }

    public void setFile(String name, CharSequence counter, float progress, boolean animated) {
        boolean appearing = !fileVisible;
        boolean newFile = !TextUtils.equals(fileNameView.getText(), name);
        setFileVisible(true, animated);
        int iconRes = ExportUiHelper.fileIcon(name);
        if (!fileIconView.hasKey(iconRes)) {
            fileIconView.set(iconRes, createGlyph(iconRes, ExportUiHelper.fileColor(name)), animated && !appearing);
        }
        fileNameView.setText(name, animated && !appearing);
        if (!TextUtils.equals(fileCounterView.getText(), counter)) {
            fileCounterView.setText(counter, animated && !newFile && !appearing);
        }
        float value = Math.max(0f, Math.min(1f, progress));
        if (newFile && animated && !appearing) {
            resetFileProgress(value);
        } else if (!fileProgressResetting) {
            fileProgressView.setProgress(value, animated && !appearing);
        }
    }

    private void resetFileProgress(float value) {
        fileProgressResetting = true;
        fileProgressView.animate().cancel();
        fileProgressView.animate().alpha(0f).setDuration(120).withEndAction(() -> {
            fileProgressView.setProgress(value, false);
            fileProgressResetting = false;
            fileProgressView.animate().alpha(1f).setDuration(160).start();
        }).start();
    }

    public void hideFile(boolean animated) {
        setFileVisible(false, animated);
    }

    public boolean isFileVisible() {
        return fileVisible;
    }

    private void setFileVisible(boolean visible, boolean animated) {
        if (fileVisible == visible) {
            return;
        }
        fileVisible = visible;
        if (fileAnimator != null) {
            fileAnimator.cancel();
            fileAnimator = null;
        }
        float target = visible ? 1f : 0f;
        if (!animated) {
            applyFileFraction(target);
            return;
        }
        fileSection.setVisibility(VISIBLE);
        fileAnimator = ValueAnimator.ofFloat(fileFraction, target);
        fileAnimator.addUpdateListener(animation -> applyFileFraction((float) animation.getAnimatedValue()));
        fileAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (fileAnimator == animation) {
                    fileAnimator = null;
                    applyFileFraction(target);
                }
            }
        });
        fileAnimator.setDuration(350);
        fileAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        fileAnimator.start();
    }

    private void applyFileFraction(float fraction) {
        fileFraction = fraction;
        fileSection.setAlpha(fraction);
        fileSection.setVisibility(fraction > 0f ? VISIBLE : GONE);
        requestLayout();
    }

    private static final class IconSwitcher extends FrameLayout {

        private View current;
        private Object key;

        IconSwitcher(Context context) {
            super(context);
        }

        boolean hasKey(Object key) {
            return Objects.equals(this.key, key);
        }

        void set(Object key, View view, boolean animated) {
            this.key = key;
            View old = current;
            current = view;
            addView(view, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
            if (old == null) {
                return;
            }
            if (!animated) {
                old.animate().cancel();
                removeView(old);
                return;
            }
            old.animate().cancel();
            old.animate().alpha(0f).scaleX(.6f).scaleY(.6f).setDuration(220).setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT).withEndAction(() -> removeView(old)).start();
            view.setAlpha(0f);
            view.setScaleX(.6f);
            view.setScaleY(.6f);
            view.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(300).setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT).start();
        }
    }

    private final class FileNameView extends FrameLayout {

        private TextView current;
        private TextView other;

        FileNameView(Context context) {
            super(context);
            current = createLabel(context);
            other = createLabel(context);
            other.setVisibility(GONE);
            addView(current, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
            addView(other, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        }

        private TextView createLabel(Context context) {
            TextView view = new TextView(context);
            view.setTextColor(color(Theme.key_windowBackgroundWhiteBlackText));
            view.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            view.setTypeface(AndroidUtilities.regular());
            view.setSingleLine(true);
            view.setEllipsize(TextUtils.TruncateAt.MIDDLE);
            view.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
            view.setIncludeFontPadding(false);
            NotificationCenter.listenEmojiLoading(view);
            return view;
        }

        CharSequence getText() {
            return current.getText();
        }

        void setText(CharSequence text, boolean animated) {
            if (TextUtils.equals(current.getText(), text)) {
                return;
            }
            if (!animated || TextUtils.isEmpty(current.getText())) {
                current.animate().cancel();
                other.animate().cancel();
                other.setVisibility(GONE);
                current.setAlpha(1f);
                current.setTranslationY(0);
                current.setText(Emoji.replaceEmoji(text, current.getPaint().getFontMetricsInt(), false));
                return;
            }
            TextView old = current;
            current = other;
            other = old;
            old.animate().cancel();
            old.animate().alpha(0f).translationY(-dp(8)).setDuration(220).setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT).withEndAction(() -> old.setVisibility(GONE)).start();
            current.animate().cancel();
            current.setText(Emoji.replaceEmoji(text, current.getPaint().getFontMetricsInt(), false));
            current.setVisibility(VISIBLE);
            current.setAlpha(0f);
            current.setTranslationY(dp(8));
            current.animate().alpha(1f).translationY(0).setDuration(320).setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT).start();
        }
    }
}
