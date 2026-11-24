package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.SpannableStringBuilder;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.exteragram.messenger.updater.UpdaterUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.browser.Browser;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.spoilers.SpoilersTextView;
import org.telegram.ui.ActionBar.ThemeBind;
import org.telegram.ui.Components.voip.CellFlickerDrawable;

import java.util.Locale;

@SuppressLint("ViewConstructor")
public class BlockingUpdateView extends FrameLayout implements NotificationCenter.NotificationCenterDelegate, Theme.Colorable {

    private final TextView textView;
    private final TextView acceptTextView;
    private final FrameLayout radialProgressView;
    private final FrameLayout acceptButton;
    private final RadialProgress radialProgress;
    private final ScrollView scrollView;

    private AnimatorSet progressAnimation;

    private final TLRPC.TL_help_appUpdate appUpdate;
    private String fileName;
    private int accountNum;
    private int pressCount;

    public BlockingUpdateView(final Context context, TLRPC.TL_help_appUpdate update) {
        super(context);
        this.appUpdate = update;

        ThemeBind.backgroundColor(this, Theme.key_windowBackgroundWhite);

        int top = (int) (AndroidUtilities.statusBarHeight / AndroidUtilities.density);

        FrameLayout view = new FrameLayout(context);
        addView(view, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(236) + AndroidUtilities.statusBarHeight));

        RLottieImageView imageView = new RLottieImageView(context);
        imageView.setAutoRepeat(true);
        imageView.setAnimation(R.raw.giveaway_results, 120, 120);
        imageView.playAnimation();
        imageView.setOnClickListener(v -> {
            pressCount++;
            if (pressCount >= 10) {
                setVisibility(GONE);
                SharedConfig.pendingAppUpdate = null;
                SharedConfig.saveConfig();
            }
        });
        view.addView(imageView, LayoutHelper.createFrame(120, 120, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, top + 30, 0, 0));

        TextView titleTextView = new TextView(context);
        ThemeBind.textColor(titleTextView, Theme.key_windowBackgroundWhiteBlackText);
        titleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        titleTextView.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.TOP);
        titleTextView.setTypeface(AndroidUtilities.bold());
        titleTextView.setText(LocaleController.getString(R.string.UpdateAvailable));
        view.addView(titleTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, top + 150 + 25, 0, 0));

        TextView messageTextView = new TextView(getContext());
        messageTextView.setTextColor(Theme.getColor(Theme.key_dialogTextGray3));
        messageTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        messageTextView.setMovementMethod(new AndroidUtilities.LinkMovementMethodMy());
        messageTextView.setLinkTextColor(Theme.getColor(Theme.key_dialogTextLink));
        messageTextView.setText(LocaleController.formatString("AppUpdateVersionAndSize", R.string.AppUpdateVersionAndSize, appUpdate.version, AndroidUtilities.formatFileSize(appUpdate.document.size)));
        messageTextView.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.TOP);
        view.addView(messageTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 23, top + 150 + 25 + 30, 23, 0));

        FrameLayout container = new FrameLayout(context);
        scrollView = new ScrollView(context);
        AndroidUtilities.setScrollViewEdgeEffectColor(scrollView, Theme.getColor(Theme.key_actionBarDefault));
        scrollView.setPadding(0, AndroidUtilities.dp(16), 0, AndroidUtilities.dp(16));
        scrollView.setClipToPadding(false);
        addView(scrollView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.TOP, 27, 178 + top + 60, 27, 130));

        scrollView.addView(container);

        textView = new SpoilersTextView(context);
        ThemeBind.textColor(textView, Theme.key_windowBackgroundWhiteBlackText);
        ThemeBind.linkTextColor(textView, Theme.key_windowBackgroundWhiteLinkText);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        textView.setMovementMethod(new AndroidUtilities.LinkMovementMethodMy());
        textView.setGravity(Gravity.LEFT | Gravity.TOP);
        textView.setLineSpacing(AndroidUtilities.dp(2), 1.0f);
        container.addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 0, 0, 0, 0));

        acceptButton = new FrameLayout(context) {
            CellFlickerDrawable cellFlickerDrawable;

            @Override
            protected void onDraw(@NonNull Canvas canvas) {
                super.onDraw(canvas);
                if (cellFlickerDrawable == null) {
                    cellFlickerDrawable = new CellFlickerDrawable();
                    cellFlickerDrawable.drawFrame = false;
                    cellFlickerDrawable.repeatProgress = 2f;
                }
                cellFlickerDrawable.setParentWidth(getMeasuredWidth());
                AndroidUtilities.rectTmp.set(0, 0, getMeasuredWidth(), getMeasuredHeight());
                cellFlickerDrawable.draw(canvas, AndroidUtilities.rectTmp, AndroidUtilities.dp(8), null);
                invalidate();
            }
        };
        ScaleStateListAnimator.apply(acceptButton, .02f, 1.2f);
        acceptButton.setBackground(Theme.AdaptiveRipple.filledRectByKey(Theme.key_featuredStickers_addButton, 8));
        acceptButton.setPadding(AndroidUtilities.dp(16), 0, AndroidUtilities.dp(16), 0);
        addView(acceptButton, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM, 16, 0, 16, 45));
        acceptButton.setOnClickListener(view1 -> {
            if (!ApplicationLoader.applicationLoaderInstance.checkApkInstallPermissions(getContext())) {
                return;
            }
            if (appUpdate.document instanceof TLRPC.TL_document) {
                if (!ApplicationLoader.applicationLoaderInstance.openApkInstall((Activity) getContext(), appUpdate.document)) {
                    FileLoader.getInstance(accountNum).loadFile(appUpdate.document, "update", FileLoader.PRIORITY_HIGH, 1);
                    showProgress(true);
                }
            } else if (appUpdate.url != null) {
                Browser.openUrl(getContext(), appUpdate.url);
            }
        });

        acceptTextView = new TextView(context);
        acceptTextView.setGravity(Gravity.CENTER);
        acceptTextView.setTypeface(AndroidUtilities.bold());
        acceptTextView.setTextColor(0xffffffff);
        acceptTextView.setText(LocaleController.getString(R.string.Update));
        acceptTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        acceptButton.addView(acceptTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));

        radialProgressView = new FrameLayout(context) {
            @Override
            protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                super.onLayout(changed, left, top, right, bottom);
                int width = right - left;
                int height = bottom - top;
                int w = AndroidUtilities.dp(36);
                int l = (width - w) / 2;
                int t = (height - w) / 2;
                radialProgress.setProgressRect(l, t, l + w, t + w);
            }

            @Override
            protected void onDraw(@NonNull Canvas canvas) {
                radialProgress.draw(canvas);
            }
        };
        radialProgressView.setWillNotDraw(false);
        radialProgressView.setAlpha(0.0f);
        radialProgressView.setScaleX(0.1f);
        radialProgressView.setScaleY(0.1f);
        radialProgressView.setVisibility(View.INVISIBLE);
        radialProgress = new RadialProgress(radialProgressView);
        radialProgress.setBackground(null, true, false);
        radialProgress.setProgressColor(0xffffffff);
        acceptButton.addView(radialProgressView, LayoutHelper.createFrame(36, 36, Gravity.CENTER));
    }

    @Override
    public void setVisibility(int visibility) {
        super.setVisibility(visibility);
        if (visibility == GONE) {
            NotificationCenter.getInstance(accountNum).removeObserver(this, NotificationCenter.fileLoaded);
            NotificationCenter.getInstance(accountNum).removeObserver(this, NotificationCenter.fileLoadFailed);
            NotificationCenter.getInstance(accountNum).removeObserver(this, NotificationCenter.fileLoadProgressChanged);
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.fileLoaded) {
            String location = (String) args[0];
            if (fileName != null && fileName.equals(location)) {
                showProgress(false);
                ApplicationLoader.applicationLoaderInstance.openApkInstall((Activity) getContext(), appUpdate.document);
            }
        } else if (id == NotificationCenter.fileLoadFailed) {
            String location = (String) args[0];
            if (fileName != null && fileName.equals(location)) {
                showProgress(false);
            }
        } else if (id == NotificationCenter.fileLoadProgressChanged) {
            String location = (String) args[0];
            if (fileName != null && fileName.equals(location)) {
                Long loadedSize = (Long) args[1];
                Long totalSize = (Long) args[2];
                float progress = Math.min(1f, loadedSize / (float) totalSize);
                radialProgress.setProgress(progress, true);
            }
        }
    }

    private void showProgress(final boolean show) {
        if (progressAnimation != null) {
            progressAnimation.cancel();
        }
        progressAnimation = new AnimatorSet();
        if (show) {
            radialProgressView.setVisibility(View.VISIBLE);
            acceptButton.setEnabled(false);
            progressAnimation.playTogether(
                    ObjectAnimator.ofFloat(acceptTextView, View.SCALE_X, 0.1f),
                    ObjectAnimator.ofFloat(acceptTextView, View.SCALE_Y, 0.1f),
                    ObjectAnimator.ofFloat(acceptTextView, View.ALPHA, 0.0f),
                    ObjectAnimator.ofFloat(radialProgressView, View.SCALE_X, 1.0f),
                    ObjectAnimator.ofFloat(radialProgressView, View.SCALE_Y, 1.0f),
                    ObjectAnimator.ofFloat(radialProgressView, View.ALPHA, 1.0f));
        } else {
            acceptTextView.setVisibility(View.VISIBLE);
            acceptButton.setEnabled(true);
            progressAnimation.playTogether(
                    ObjectAnimator.ofFloat(radialProgressView, View.SCALE_X, 0.1f),
                    ObjectAnimator.ofFloat(radialProgressView, View.SCALE_Y, 0.1f),
                    ObjectAnimator.ofFloat(radialProgressView, View.ALPHA, 0.0f),
                    ObjectAnimator.ofFloat(acceptTextView, View.SCALE_X, 1.0f),
                    ObjectAnimator.ofFloat(acceptTextView, View.SCALE_Y, 1.0f),
                    ObjectAnimator.ofFloat(acceptTextView, View.ALPHA, 1.0f));

        }
        progressAnimation.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (progressAnimation != null && progressAnimation.equals(animation)) {
                    if (!show) {
                        radialProgressView.setVisibility(View.INVISIBLE);
                    } else {
                        acceptTextView.setVisibility(View.INVISIBLE);
                    }
                }
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                if (progressAnimation != null && progressAnimation.equals(animation)) {
                    progressAnimation = null;
                }
            }
        });
        progressAnimation.setDuration(150);
        progressAnimation.start();
    }

    public void show(int account, boolean check) {
        pressCount = 0;
        accountNum = account;
        if (appUpdate.document instanceof TLRPC.TL_document) {
            fileName = FileLoader.getAttachFileName(appUpdate.document);
        }
        if (getVisibility() != VISIBLE) {
            setVisibility(VISIBLE);
        }
        SpannableStringBuilder builder = new SpannableStringBuilder(appUpdate.text);
        MessageObject.addEntitiesToText(builder, appUpdate.entities, false, false, false, false);
        MessageObject.replaceAnimatedEmoji(builder, appUpdate.entities, textView.getPaint().getFontMetricsInt());
        textView.setText(builder);
        NotificationCenter.getInstance(accountNum).addWeakObserver(this, NotificationCenter.fileLoaded);
        NotificationCenter.getInstance(accountNum).addWeakObserver(this, NotificationCenter.fileLoadFailed);
        NotificationCenter.getInstance(accountNum).addWeakObserver(this, NotificationCenter.fileLoadProgressChanged);
        if (check) {
            UpdaterUtils.getAppUpdate((response, error) -> {
                if (response != null) {
                    if (!response.can_not_skip) {
                        AndroidUtilities.runOnUIThread(() -> {
                            setVisibility(GONE);
                            SharedConfig.pendingAppUpdate = null;
                            SharedConfig.saveConfig();
                        });
                    }
                }
            });
        }
    }

    Drawable gradientDrawableTop = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{Theme.getColor(Theme.key_windowBackgroundWhite), Color.TRANSPARENT});
    Drawable gradientDrawableBottom = new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, new int[]{Theme.getColor(Theme.key_windowBackgroundWhite), Color.TRANSPARENT});

    private boolean themeColorsApplied;
    private int lastGradientColor, lastEdgeEffectColor;

    @Override
    public void updateColors() {
        try {
            final int color = Theme.getColor(Theme.key_windowBackgroundWhite);
            if (!themeColorsApplied || color != lastGradientColor) {
                lastGradientColor = color;
                if (gradientDrawableTop instanceof GradientDrawable) {
                    ((GradientDrawable) gradientDrawableTop).setColors(new int[] {color, Color.TRANSPARENT });
                }
                if (gradientDrawableBottom instanceof GradientDrawable) {
                    ((GradientDrawable) gradientDrawableBottom).setColors(new int[] {color, Color.TRANSPARENT });
                }
            }
            final int edgeColor = Theme.getColor(Theme.key_actionBarDefault);
            if (scrollView != null && (!themeColorsApplied || edgeColor != lastEdgeEffectColor)) {
                lastEdgeEffectColor = edgeColor;
                AndroidUtilities.setScrollViewEdgeEffectColor(scrollView, edgeColor);
            }
            themeColorsApplied = true;
            invalidate();
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    @Override
    protected void dispatchDraw(@NonNull Canvas canvas) {
        super.dispatchDraw(canvas);
        gradientDrawableTop.setBounds(scrollView.getLeft(), scrollView.getTop(), scrollView.getRight(), scrollView.getTop() + AndroidUtilities.dp(16));
        gradientDrawableTop.draw(canvas);

        gradientDrawableBottom.setBounds(scrollView.getLeft(), scrollView.getBottom() - AndroidUtilities.dp(18), scrollView.getRight(), scrollView.getBottom());
        gradientDrawableBottom.draw(canvas);
    }

}
