/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.updater;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.widget.NestedScrollView;

import com.exteragram.messenger.ExteraConfig;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.DocumentObject;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.SvgHelper;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.spoilers.SpoilersTextView;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;

import java.io.File;

public class UpdateAppAlertDialog extends BottomSheet {

    protected final TLRPC.TL_help_appUpdate appUpdate;
    private final int accountNum;

    private final Drawable shadowDrawable;
    private final int bottomContentHeight;
    private final NestedScrollView scrollView;
    private final View shadow;
    protected final LinearLayout linearLayout;
    private final int[] location = new int[2];
    protected final TextView textView;
    protected final TextView messageTextView;
    protected final TextView changelogTextView;
    protected final ButtonWithCounterView doneButton;
    private AnimatorSet shadowAnimation;
    private int scrollOffsetY;

    public UpdateAppAlertDialog(Activity context, TLRPC.TL_help_appUpdate update, int account) {
        super(context, false);
        this.appUpdate = update;
        this.accountNum = account;
        this.bottomContentHeight = (hasSecondaryButton() ? 130 : 72) + getExtraBottomContentHeight();

        fixNavigationBar();

        setApplyTopPadding(false);
        setApplyBottomPadding(false);

        shadowDrawable = ContextCompat.getDrawable(context, R.drawable.sheet_shadow_round).mutate();
        shadowDrawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_dialogBackground), PorterDuff.Mode.MULTIPLY));

        FrameLayout container = new FrameLayout(context) {
            @Override
            public void setTranslationY(float translationY) {
                super.setTranslationY(translationY);
                updateLayout();
            }

            @Override
            public boolean onInterceptTouchEvent(MotionEvent ev) {
                if (ev.getAction() == MotionEvent.ACTION_DOWN && scrollOffsetY != 0 && ev.getY() < scrollOffsetY && canDismissWithTouchOutside()) {
                    dismiss();
                    return true;
                }
                return super.onInterceptTouchEvent(ev);
            }

            @SuppressLint("ClickableViewAccessibility")
            @Override
            public boolean onTouchEvent(MotionEvent e) {
                return !isDismissed() && super.onTouchEvent(e);
            }

            @Override
            protected void onDraw(@NonNull Canvas canvas) {
                int top = (int) (scrollOffsetY - backgroundPaddingTop - getTranslationY());
                shadowDrawable.setBounds(0, top, getMeasuredWidth(), getMeasuredHeight());
                shadowDrawable.draw(canvas);
            }
        };
        container.setWillNotDraw(false);
        containerView = container;

        scrollView = new NestedScrollView(context) {

            private boolean ignoreLayout;

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int height = MeasureSpec.getSize(heightMeasureSpec);
                measureChildWithMargins(linearLayout, widthMeasureSpec, 0, heightMeasureSpec, 0);
                int contentHeight = linearLayout.getMeasuredHeight();
                int padding = (height / 5 * 2);
                int visiblePart = height - padding;
                if (contentHeight - visiblePart < AndroidUtilities.dp(90) || contentHeight < height / 2 + AndroidUtilities.dp(90)) {
                    padding = height - contentHeight;
                }
                if (padding < 0) {
                    padding = 0;
                }
                if (getPaddingTop() != padding) {
                    ignoreLayout = true;
                    setPadding(0, padding, 0, 0);
                    ignoreLayout = false;
                }
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
            }

            @Override
            protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                super.onLayout(changed, left, top, right, bottom);
                updateLayout();
            }

            @Override
            public void requestLayout() {
                if (ignoreLayout) {
                    return;
                }
                super.requestLayout();
            }

            @Override
            protected void onScrollChanged(int l, int t, int oldl, int oldt) {
                super.onScrollChanged(l, t, oldl, oldt);
                updateLayout();
            }
        };
        scrollView.setFillViewport(true);
        scrollView.setWillNotDraw(false);
        scrollView.setClipToPadding(false);
        scrollView.setVerticalScrollBarEnabled(false);
        container.addView(scrollView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.TOP, 0, 0, 0, bottomContentHeight));

        linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(linearLayout, LayoutHelper.createScroll(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP));

        if (appUpdate.sticker != null) {
            BackupImageView imageView = new BackupImageView(context);
            SvgHelper.SvgDrawable svgThumb = DocumentObject.getSvgThumb(appUpdate.sticker.thumbs, Theme.key_windowBackgroundGray, 1.0f);
            TLRPC.PhotoSize thumb = FileLoader.getClosestPhotoSizeWithSize(appUpdate.sticker.thumbs, 90);
            ImageLocation imageLocation = ImageLocation.getForDocument(thumb, appUpdate.sticker);

            if (svgThumb != null) {
                imageView.setImage(ImageLocation.getForDocument(appUpdate.sticker), "250_250", svgThumb, 0, "update");
            } else {
                imageView.setImage(ImageLocation.getForDocument(appUpdate.sticker), "250_250", imageLocation, null, 0, "update");
            }
            linearLayout.addView(imageView, LayoutHelper.createLinear(160, 160, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 17, 8, 17, 0));
        }

        textView = new TextView(context);
        textView.setTypeface(AndroidUtilities.bold());
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        textView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        textView.setSingleLine(true);
        textView.setEllipsize(TextUtils.TruncateAt.END);
        textView.setText(getTitleText());
        linearLayout.addView(textView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 23, 16, 23, 0));

        messageTextView = new TextView(getContext());
        messageTextView.setTextColor(Theme.getColor(Theme.key_dialogTextGray3));
        messageTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        messageTextView.setMovementMethod(new AndroidUtilities.LinkMovementMethodMy());
        messageTextView.setLinkTextColor(Theme.getColor(Theme.key_dialogTextLink));
        messageTextView.setText(LocaleController.formatString(R.string.AppUpdateVersionAndSize, appUpdate.version, AndroidUtilities.formatFileSize(appUpdate.document.size)));
        messageTextView.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.TOP);
        linearLayout.addView(messageTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 23, 0, 23, 5));

        changelogTextView = new SpoilersTextView(getContext());
        changelogTextView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        changelogTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        changelogTextView.setMovementMethod(new AndroidUtilities.LinkMovementMethodMy());
        changelogTextView.setLinksClickable(false);
        changelogTextView.setLinkTextColor(Theme.getColor(Theme.key_dialogTextLink));
        if (TextUtils.isEmpty(appUpdate.text)) {
            changelogTextView.setText(AndroidUtilities.replaceTags(LocaleController.getString(R.string.AppUpdateChangelogEmpty)));
        } else {
            SpannableStringBuilder builder = new SpannableStringBuilder(appUpdate.text);
            MessageObject.addEntitiesToText(builder, update.entities, false, false, false, false);
            MessageObject.replaceAnimatedEmoji(builder, update.entities, changelogTextView.getPaint().getFontMetricsInt());
            changelogTextView.setText(builder);
        }
        changelogTextView.setGravity(Gravity.LEFT | Gravity.TOP);
        linearLayout.addView(changelogTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 23, 15, 23, 0));

        FrameLayout.LayoutParams frameLayoutParams = new FrameLayout.LayoutParams(LayoutHelper.MATCH_PARENT, AndroidUtilities.getShadowHeight(), Gravity.BOTTOM | Gravity.LEFT);
        frameLayoutParams.bottomMargin = AndroidUtilities.dp(bottomContentHeight - 3);
        shadow = new View(context);
        shadow.setBackgroundColor(Theme.getColor(Theme.key_dialogShadowLine));
        shadow.setAlpha(0.0f);
        shadow.setTag(1);
        container.addView(shadow, frameLayoutParams);

        addContentBeforeDoneButton(container);

        doneButton = new ButtonWithCounterView(context, true, null);
        doneButton.setRound();
        doneButton.setText(getDoneButtonText(), false);
        doneButton.setOnClickListener(v -> onDone());
        container.addView(doneButton, createButtonLayoutParams((hasSecondaryButton() ? 48 + 8 + 8 : 8) + getExtraBottomContentHeight()));

        addContentAfterDoneButton(container);
    }

    protected boolean hasSecondaryButton() {
        return true;
    }

    protected int getExtraBottomContentHeight() {
        return 0;
    }

    protected void addContentBeforeDoneButton(FrameLayout container) {}
    protected void addContentAfterDoneButton(FrameLayout container) {
        if (!hasSecondaryButton()) {
            return;
        }
        addRemindLaterButton(container, () -> {
            ExteraConfig.setUpdateScheduleTimestamp(System.currentTimeMillis());
            dismiss();
        });
    }

    protected void addRemindLaterButton(FrameLayout container, Runnable onClick) {
        addRemindLaterButton(container, onClick, false);
    }

    protected void addRemindLaterButton(FrameLayout container, Runnable onClick, boolean neutral) {
        ButtonWithCounterView scheduleButton = new ButtonWithCounterView(getContext(), false, null);
        scheduleButton.setRound();
        if (neutral) {
            scheduleButton.setNeutral();
        }
        scheduleButton.setText(LocaleController.getString(R.string.AppUpdateRemindMeLater), false);
        scheduleButton.setOnClickListener(v -> onClick.run());
        container.addView(scheduleButton, createButtonLayoutParams(8));
    }

    protected FrameLayout.LayoutParams createButtonLayoutParams(int bottomMargin) {
        return LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.LEFT | Gravity.BOTTOM, 22, 14, 22, bottomMargin);
    }

    protected String getDoneButtonText() {
        File path = FileLoader.getInstance(currentAccount).getPathToAttach(SharedConfig.pendingAppUpdate.document, true);
        return path.exists() ? LocaleController.getString(R.string.AppUpdateNow) : LocaleController.getString(R.string.AppUpdateDownloadNow);
    }

    protected String getTitleText() {
        return LocaleController.getString(R.string.UpdateAvailable);
    }

    protected void onDone() {
        File path = FileLoader.getInstance(currentAccount).getPathToAttach(SharedConfig.pendingAppUpdate.document, true);
        if (path.exists()) {
            ApplicationLoader.applicationLoaderInstance.openApkInstall((Activity) getContext(), SharedConfig.pendingAppUpdate.document);
        } else {
            FileLoader.getInstance(accountNum).loadFile(appUpdate.document, "update", FileLoader.PRIORITY_NORMAL, 1);
        }
        dismiss();
    }

    private void runShadowAnimation(final boolean show) {
        if (show && shadow.getTag() != null || !show && shadow.getTag() == null) {
            shadow.setTag(show ? null : 1);
            if (show) {
                shadow.setVisibility(View.VISIBLE);
            }
            if (shadowAnimation != null) {
                shadowAnimation.cancel();
            }
            shadowAnimation = new AnimatorSet();
            shadowAnimation.playTogether(ObjectAnimator.ofFloat(shadow, View.ALPHA, show ? 1.0f : 0.0f));
            shadowAnimation.setDuration(150);
            shadowAnimation.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (shadowAnimation != null && shadowAnimation.equals(animation)) {
                        if (!show) {
                            shadow.setVisibility(View.INVISIBLE);
                        }
                        shadowAnimation = null;
                    }
                }

                @Override
                public void onAnimationCancel(Animator animation) {
                    if (shadowAnimation != null && shadowAnimation.equals(animation)) {
                        shadowAnimation = null;
                    }
                }
            });
            shadowAnimation.start();
        }
    }

    private void updateLayout() {
        View child = linearLayout.getChildAt(0);
        child.getLocationInWindow(location);
        int headerTop = location[1] - child.getTop() + AndroidUtilities.dp(appUpdate.sticker != null ? 8 : 16);
        int top = headerTop - AndroidUtilities.dp(24);
        int newOffset = Math.max(top, 0);
        runShadowAnimation(headerTop + linearLayout.getMeasuredHeight() > container.getMeasuredHeight() - AndroidUtilities.dp(bottomContentHeight - 17) + containerView.getTranslationY());
        if (scrollOffsetY != newOffset) {
            scrollOffsetY = newOffset;
            scrollView.invalidate();
        }
    }
}
