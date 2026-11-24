/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.export.ui;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.exteragram.messenger.ExteraConfig;
import com.exteragram.messenger.export.ExportController;
import com.exteragram.messenger.export.ExportState;
import com.exteragram.messenger.utils.ui.material.ColorTones;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedTextView;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.LineProgressView;
import org.telegram.ui.Components.RLottieImageView;
import org.telegram.ui.Components.TableView;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;

public class ExportProgressSheet extends BottomSheet implements NotificationCenter.NotificationCenterDelegate {

    private static final int MODE_PROGRESS = 0;
    private static final int MODE_FINISHED = 1;
    private static final int MODE_ERROR = 2;

    private static final int HERO_ERROR = 1;
    private static final int HERO_DELAY = 2;

    private static final int HERO_SIZE_DP = 80;
    private static final int HERO_SMALL_SIZE_DP = 56;
    private static final float HERO_LIGHT_TONE = 72f;
    private static final long FILE_HIDE_DELAY = 1200;
    private static final long FILE_HIDE_DELAY_NEXT = 300;

    private final int account;
    private final NotificationCenter.ObserversGroup observers;

    private final FrameLayout heroView;
    private final FrameLayout heroSlot;
    private final FrameLayout centerHeroSlot;
    private final GradientDrawable heroBackground;
    private final LinearLayout headerView;
    private final LinearLayout headerText;
    private final AnimatedTextView subtitleView;
    private final RLottieImageView heroAnimationView;
    private final TextView titleView;
    private final TextView descriptionView;
    private final AnimatedTextView percentView;
    private final LineProgressView progressView;
    private final ExportProgressCell progressCell;
    private final TableView tableView;
    private final ButtonWithCounterView primaryButton;
    private final ButtonWithCounterView secondaryButton;
    private final ButtonWithCounterView stopButton;

    private int mode = -1;
    private boolean dismissing;
    private boolean hideScheduled;
    private String fileEntity;
    private int heroKind;
    private int heroColor;
    private ValueAnimator heroColorAnimator;
    private final Runnable hideFile = this::hideFile;

    public static void show(BaseFragment fragment, int account) {
        if (fragment == null || fragment.getParentActivity() == null || fragment.getVisibleDialog() instanceof ExportProgressSheet) {
            return;
        }
        if (ExportUiHelper.hasVisibleState(account)) {
            fragment.showDialog(new ExportProgressSheet(fragment, account));
        }
    }

    private ExportProgressSheet(BaseFragment fragment, int account) {
        super(fragment.getParentActivity(), false, fragment.getResourceProvider());
        this.account = account;
        Activity context = fragment.getParentActivity();

        setBackgroundColorKey(Theme.key_windowBackgroundGray);
        fixNavigationBarKey(Theme.key_windowBackgroundGray);

        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setClipChildren(false);

        centerHeroSlot = new FrameLayout(context);
        layout.addView(centerHeroSlot, LayoutHelper.createLinear(HERO_SIZE_DP, HERO_SIZE_DP, Gravity.CENTER_HORIZONTAL, 0, 28, 0, 0));

        heroColor = getThemedColor(Theme.key_featuredStickers_addButton);
        heroBackground = new GradientDrawable();
        heroBackground.setShape(GradientDrawable.OVAL);
        heroBackground.setColor(heroColor);
        heroView = new FrameLayout(context);
        heroView.setBackground(heroBackground);

        heroAnimationView = new RLottieImageView(context);
        heroAnimationView.setScaleType(android.widget.ImageView.ScaleType.CENTER);
        heroAnimationView.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_featuredStickers_buttonText), PorterDuff.Mode.SRC_IN));
        heroView.addView(heroAnimationView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        titleView = new TextView(context);
        titleView.setGravity(Gravity.CENTER_HORIZONTAL);
        titleView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        titleView.setTypeface(AndroidUtilities.bold());
        layout.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 24, 16, 24, 0));

        descriptionView = new TextView(context);
        descriptionView.setGravity(Gravity.CENTER_HORIZONTAL);
        descriptionView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText2));
        descriptionView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        descriptionView.setTypeface(AndroidUtilities.regular());
        descriptionView.setLineSpacing(AndroidUtilities.dp(2), 1f);
        layout.addView(descriptionView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 32, 4, 32, 0));

        headerView = new LinearLayout(context);
        headerView.setOrientation(LinearLayout.HORIZONTAL);
        headerView.setGravity(Gravity.CENTER_VERTICAL);
        headerView.setClipChildren(false);
        layout.addView(headerView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, HERO_SMALL_SIZE_DP, 0, 24, 20, 24, 0));

        heroSlot = new FrameLayout(context);
        headerView.addView(heroSlot, LayoutHelper.createLinear(HERO_SMALL_SIZE_DP, HERO_SMALL_SIZE_DP));

        headerText = new LinearLayout(context);
        headerText.setOrientation(LinearLayout.VERTICAL);
        headerView.addView(headerText, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, Gravity.CENTER_VERTICAL, 16, 0, 0, 0));

        percentView = new AnimatedTextView(context, false, true, true);
        percentView.setAnimationProperties(.35f, 0, 220, CubicBezierInterpolator.EASE_OUT_QUINT);
        percentView.setTextSize(AndroidUtilities.dp(28));
        percentView.setTypeface(AndroidUtilities.bold());
        percentView.setGravity(Gravity.LEFT);
        percentView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        headerText.addView(percentView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 34));

        subtitleView = new AnimatedTextView(context, false, true, true);
        subtitleView.setAnimationProperties(.35f, 0, 220, CubicBezierInterpolator.EASE_OUT_QUINT);
        subtitleView.setTextSize(AndroidUtilities.dp(14));
        subtitleView.setTypeface(AndroidUtilities.regular());
        subtitleView.setGravity(Gravity.LEFT);
        subtitleView.setEllipsizeByGradient(true);
        subtitleView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText2));
        headerText.addView(subtitleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 20));

        boolean wavy = ExteraConfig.getNewLoadingStyle();
        progressView = new LineProgressView(context);
        progressView.setTrackThickness(AndroidUtilities.dp(4));
        progressView.setTrackStopIndicatorSize(AndroidUtilities.dp(4));
        progressView.setIndicatorTrackGapSize(AndroidUtilities.dp(4));
        progressView.setProgressColor(getThemedColor(Theme.key_featuredStickers_addButton));
        progressView.setBackColor(getThemedColor(Theme.key_dialogLineProgressBackground));
        layout.addView(progressView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, wavy ? 12 : 4, 0, 24, wavy ? 14 : 18, 24, 0));

        progressCell = new ExportProgressCell(context, resourcesProvider);
        layout.addView(progressCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 12, 16, 12, 0));

        tableView = new TableView(context, resourcesProvider);
        tableView.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(10), getThemedColor(Theme.key_windowBackgroundWhite)));
        layout.addView(tableView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 12, 20, 12, 0));

        primaryButton = new ButtonWithCounterView(context, true, resourcesProvider);
        primaryButton.setRound();
        primaryButton.setOnClickListener(v -> onPrimaryClick());
        layout.addView(primaryButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 0, 12, 20, 12, 0));

        secondaryButton = createSecondaryButton(context);
        secondaryButton.setOnClickListener(v -> onSecondaryClick());
        layout.addView(secondaryButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 0, 12, 8, 12, 12));

        stopButton = createSecondaryButton(context);
        stopButton.setText(LocaleController.getString(R.string.ExportStop), false);
        stopButton.setOnClickListener(v -> ExportUiHelper.showStopDialog(null, getContext(), resourcesProvider, account));
        layout.addView(stopButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 0, 12, 8, 12, 12));

        ScrollView scrollView = new ScrollView(context);
        scrollView.addView(layout);
        setCustomView(scrollView);

        observers = NotificationCenter.getInstance(account).createObserversGroup(this).add(NotificationCenter.exportStateChanged);
        bind();
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id != NotificationCenter.exportStateChanged || dismissing) {
            return;
        }
        if (ExportUiHelper.hasVisibleState(this.account)) {
            bind();
        } else {
            dismiss();
        }
    }

    private void bind() {
        ExportState state = ExportController.getInstance(account).getState();
        if (state instanceof ExportState.Processing processing) {
            setMode(MODE_PROGRESS);
            boolean animated = isShowing();
            String entity = ExportUiHelper.entityTitle(processing);
            progressCell.setStep(account, processing, entity.isEmpty() ? ExportUiHelper.stepTitle(processing) : entity, ExportUiHelper.progressCounter(processing), animated);
            subtitleView.setText(ExportUiHelper.overallCounter(processing), animated);
            String entityKey = processing.step + ":" + processing.entityIndex;
            String bytes = ExportUiHelper.bytesCounter(processing);
            if (!bytes.isEmpty()) {
                cancelFileHide();
                fileEntity = entityKey;
                progressCell.setFile(processing.bytesName, bytes, processing.bytesLoaded / (float) processing.bytesCount, animated);
            } else if (progressCell.isFileVisible() && !hideScheduled) {
                if (animated) {
                    hideScheduled = true;
                    AndroidUtilities.runOnUIThread(hideFile, entityKey.equals(fileEntity) ? FILE_HIDE_DELAY : FILE_HIDE_DELAY_NEXT);
                } else {
                    progressCell.hideFile(false);
                }
            }
            progressView.setProgress(processing.progress(), true);
            percentView.setText(ExportUiHelper.percent(processing) + "%", animated);
        } else if (state instanceof ExportState.Finished finished) {
            setMode(MODE_FINISHED);
            if (finished.durationSeconds > 0) {
                descriptionView.setText(LocaleController.formatString(R.string.ExportFinishedIn, LocaleController.formatDuration(finished.durationSeconds)));
                descriptionView.setVisibility(View.VISIBLE);
            } else {
                descriptionView.setVisibility(View.GONE);
            }
            fillTable(finished);
        } else if (state != null) {
            setMode(MODE_ERROR);
            boolean animated = isShowing();
            descriptionView.setText(ExportUiHelper.errorText(state));
            boolean delay = ExportUiHelper.isDelayError(state);
            boolean retry = !delay && ExportController.getInstance(account).canRetry();
            int kind = delay ? HERO_DELAY : HERO_ERROR;
            if (heroKind != kind) {
                heroKind = kind;
                setHeroColor(getThemedColor(delay ? Theme.key_featuredStickers_addButton : Theme.key_text_RedBold), animated);
                if (delay) {
                    setHeroAnimation(R.raw.timer_3, 50, 50, false);
                } else {
                    setHeroAnimation(R.raw.error, 72, 72, false);
                }
            }
            titleView.setText(LocaleController.getString(delay ? R.string.ExportDelayTitle : R.string.ExportFailed));
            primaryButton.setText(LocaleController.getString(delay ? R.string.GotIt : retry ? R.string.Retry : R.string.Close), animated);
            secondaryButton.setVisibility(retry ? View.VISIBLE : View.GONE);
        }
    }

    private ButtonWithCounterView createSecondaryButton(Activity context) {
        ButtonWithCounterView button = new ButtonWithCounterView(context, true, resourcesProvider);
        button.setRound();
        button.setNeutral();
        button.setColor(getThemedColor(Theme.key_windowBackgroundWhite));
        return button;
    }

    private void hideFile() {
        hideScheduled = false;
        progressCell.hideFile(isShowing());
    }

    private void cancelFileHide() {
        if (hideScheduled) {
            hideScheduled = false;
            AndroidUtilities.cancelRunOnUIThread(hideFile);
        }
    }

    private void fillTable(ExportState.Finished finished) {
        tableView.clear();
        if (finished.chatsCount > 0) {
            tableView.addRow(LocaleController.getString(R.string.ExportSectionChats), LocaleController.formatNumber(finished.chatsCount, ' '));
        }
        if (finished.messagesCount > 0) {
            tableView.addRow(LocaleController.getString(R.string.ExportSummaryMessages), LocaleController.formatNumber(finished.messagesCount, ' '));
        }
        tableView.addRow(LocaleController.getString(R.string.ExportSummaryFiles), LocaleController.formatNumber(finished.filesCount, ' '));
        tableView.addRow(LocaleController.getString(R.string.ExportSummarySize), AndroidUtilities.formatFileSize(finished.bytesCount));
        tableView.addRowLink(LocaleController.getString(R.string.ExportFolder), ExportUiHelper.displayPath(finished.path), this::openFolder);
    }

    private void setMode(int newMode) {
        if (mode == newMode) {
            return;
        }
        boolean animated = mode != -1 && isShowing();
        boolean flyHero = animated && (mode == MODE_PROGRESS) != (newMode == MODE_PROGRESS) && heroView.isAttachedToWindow() && heroView.getWidth() > 0;
        int[] heroFrom = new int[2];
        int heroFromSize = heroView.getWidth();
        if (flyHero) {
            heroView.getLocationOnScreen(heroFrom);
        }
        if (animated) {
            smoothContainerViewLayout();
        }
        mode = newMode;
        if (mode != MODE_PROGRESS) {
            cancelFileHide();
            fileEntity = null;
        }
        if (mode != MODE_ERROR) {
            heroKind = 0;
        }
        boolean progress = mode == MODE_PROGRESS;
        boolean finished = mode == MODE_FINISHED;
        boolean error = mode == MODE_ERROR;
        moveHero(progress ? heroSlot : centerHeroSlot);
        centerHeroSlot.setVisibility(progress ? View.GONE : View.VISIBLE);
        headerView.setVisibility(progress ? View.VISIBLE : View.GONE);
        titleView.setVisibility(progress ? View.GONE : View.VISIBLE);
        descriptionView.setVisibility(progress ? View.GONE : View.VISIBLE);
        progressView.setVisibility(progress ? View.VISIBLE : View.GONE);
        progressView.setProgressType(progress ? LineProgressView.TYPE_WAVE : LineProgressView.TYPE_FLAT);
        progressCell.setVisibility(progress ? View.VISIBLE : View.GONE);
        tableView.setVisibility(finished ? View.VISIBLE : View.GONE);
        secondaryButton.setVisibility(progress ? View.GONE : View.VISIBLE);
        stopButton.setVisibility(progress ? View.VISIBLE : View.GONE);
        switch (mode) {
            case MODE_PROGRESS -> {
                setHeroColor(getThemedColor(Theme.key_featuredStickers_addButton), animated);
                setHeroAnimation(R.raw.export_loop, 36, 36, true);
                primaryButton.setText(LocaleController.getString(R.string.ExportContinueInBackground), animated);
            }
            case MODE_FINISHED -> {
                setHeroColor(getThemedColor(Theme.key_featuredStickers_addButton), animated);
                setHeroAnimation(R.raw.import_check, 84, 84, false);
                titleView.setText(LocaleController.getString(R.string.ExportFinished));
                boolean viewable = ExportController.getInstance(account).getState() instanceof ExportState.Finished state && ExportBrowser.canOpen(state.path);
                primaryButton.setText(LocaleController.getString(viewable ? R.string.Open : R.string.ExportOpenFolder), animated);
                secondaryButton.setText(LocaleController.getString(R.string.Done), false);
            }
            default -> secondaryButton.setText(LocaleController.getString(R.string.Close), false);
        }
        if (flyHero) {
            flyHero(heroFrom, heroFromSize);
        }
        if (animated) {
            revealContent(error ? new View[]{titleView, descriptionView, primaryButton, secondaryButton}
                    : finished ? new View[]{titleView, descriptionView, tableView, primaryButton, secondaryButton}
                    : new View[]{headerText, progressView, progressCell, primaryButton, stopButton});
        }
    }

    private void moveHero(FrameLayout slot) {
        if (heroView.getParent() == slot) {
            return;
        }
        heroView.animate().cancel();
        heroView.setTranslationX(0);
        heroView.setTranslationY(0);
        heroView.setScaleX(1f);
        heroView.setScaleY(1f);
        if (heroView.getParent() instanceof ViewGroup parent) {
            parent.removeView(heroView);
        }
        slot.addView(heroView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
    }

    private void flyHero(int[] from, int fromSize) {
        heroView.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                heroView.getViewTreeObserver().removeOnPreDrawListener(this);
                int size = heroView.getWidth();
                if (size <= 0) {
                    return true;
                }
                int[] to = new int[2];
                heroView.getLocationOnScreen(to);
                float scale = fromSize / (float) size;
                heroView.setPivotX(size / 2f);
                heroView.setPivotY(size / 2f);
                heroView.setTranslationX(from[0] + fromSize / 2f - (to[0] + size / 2f));
                heroView.setTranslationY(from[1] + fromSize / 2f - (to[1] + size / 2f));
                heroView.setScaleX(scale);
                heroView.setScaleY(scale);
                heroView.animate().translationX(0).translationY(0).scaleX(1f).scaleY(1f).setDuration(480).setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT).start();
                return true;
            }
        });
    }

    private void revealContent(View[] views) {
        int index = 0;
        for (View view : views) {
            if (view.getVisibility() != View.VISIBLE) {
                continue;
            }
            view.animate().cancel();
            view.setAlpha(0f);
            view.setTranslationY(AndroidUtilities.dp(10));
            view.animate().alpha(1f).translationY(0).setStartDelay(140 + index * 40L).setDuration(360).setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT).start();
            index++;
        }
    }

    private void setHeroAnimation(int res, int width, int height, boolean loop) {
        heroAnimationView.setAutoRepeat(loop);
        heroAnimationView.setAnimation(res, width, height);
        heroAnimationView.playAnimation();
    }

    private void setHeroColor(int color, boolean animated) {
        if (heroColorAnimator != null) {
            heroColorAnimator.cancel();
            heroColorAnimator = null;
        }
        boolean accent = color == getThemedColor(Theme.key_featuredStickers_addButton);
        int glyph = getThemedColor(Theme.key_featuredStickers_buttonText);
        if (!accent) {
            glyph = ColorTones.getTone(color) > HERO_LIGHT_TONE ? ColorTones.withTone(color, 20f, 64.0) : 0xffffffff;
        }
        heroAnimationView.setColorFilter(new PorterDuffColorFilter(glyph, PorterDuff.Mode.SRC_IN));
        if (heroColor == color) {
            return;
        }
        if (!animated) {
            heroColor = color;
            heroBackground.setColor(color);
            return;
        }
        heroColorAnimator = ValueAnimator.ofObject(new ArgbEvaluator(), heroColor, color);
        heroColorAnimator.addUpdateListener(animation -> {
            heroColor = (int) animation.getAnimatedValue();
            heroBackground.setColor(heroColor);
        });
        heroColorAnimator.setDuration(320);
        heroColorAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        heroColorAnimator.start();
    }

    private void onPrimaryClick() {
        if (mode == MODE_FINISHED && ExportController.getInstance(account).getState() instanceof ExportState.Finished finished) {
            Activity activity = AndroidUtilities.findActivity(getContext());
            String path = finished.path;
            if (activity != null && ExportBrowser.canOpen(path)) {
                dismiss();
                ExportBrowser.open(activity, path);
                return;
            }
            if (activity == null || !ExportUiHelper.openFolder(activity, path)) {
                copyPath();
                return;
            }
        }
        if (mode == MODE_ERROR) {
            ExportController controller = ExportController.getInstance(account);
            if (!ExportUiHelper.isDelayError(controller.getState()) && controller.canRetry()) {
                controller.retry();
                return;
            }
        }
        dismiss();
    }

    private void onSecondaryClick() {
        dismiss();
    }

    private void openFolder() {
        Activity activity = AndroidUtilities.findActivity(getContext());
        if (activity == null || !(ExportController.getInstance(account).getState() instanceof ExportState.Finished finished) || !ExportUiHelper.openFolder(activity, finished.path)) {
            copyPath();
        }
    }

    private void copyPath() {
        if (!(ExportController.getInstance(account).getState() instanceof ExportState.Finished finished)) {
            return;
        }
        AndroidUtilities.addToClipboard(finished.path);
        BulletinFactory.of(container, resourcesProvider).createCopyBulletin(LocaleController.getString(R.string.ExportPathCopied)).show();
    }

    @Override
    public void dismiss() {
        if (dismissing) {
            return;
        }
        dismissing = true;
        super.dismiss();
        cancelFileHide();
        observers.removeAllObservers();
        ExportController.getInstance(account).clearState();
    }
}
