/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @immat0x1, 2023

*/

package com.exteragram.messenger.preferences.updater;

import android.content.Context;
import android.graphics.Canvas;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.exteragram.messenger.ExteraConfig;
import com.exteragram.messenger.utils.AppUtils;
import com.exteragram.messenger.utils.UpdaterUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Components.AnimatedTextView;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RLottieImageView;

public class UpdaterBottomSheet extends BottomSheet {

    private RLottieImageView imageView;

    public UpdaterBottomSheet(Context context, BaseFragment fragment, boolean available, String... args) {
        // args = {version, changelog, size, downloadUrl, uploadDate}
        super(context, false);
        setOpenNoDelay(true);
        fixNavigationBar();

        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.VERTICAL);

        FrameLayout header = new FrameLayout(context);
        linearLayout.addView(header, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 21, 10, 0, 10));

        if (available) {
            imageView = new RLottieImageView(context);
            imageView.setOnClickListener(v -> {
                if (!imageView.isPlaying() && imageView.getAnimatedDrawable() != null) {
                    imageView.getAnimatedDrawable().setCurrentFrame(0);
                    imageView.playAnimation();
                }
            });
            imageView.setAnimation(R.raw.etg_raccoon, 60, 60, new int[]{0x000000, 0x000000});
            imageView.setScaleType(ImageView.ScaleType.CENTER);
            header.addView(imageView, LayoutHelper.createFrame(60, 60, Gravity.LEFT | Gravity.CENTER_VERTICAL));
        }

        SimpleTextView nameView = new SimpleTextView(context);
        nameView.setTextSize(20);
        nameView.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
        nameView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        nameView.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        nameView.setText(available ? LocaleController.getString("UpdateAvailable", R.string.UpdateAvailable) : AppUtils.getAppName());
        header.addView(nameView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 30, Gravity.LEFT, available ? 75 : 0, 5, 0, 0));

        AnimatedTextView timeView = new AnimatedTextView(context, true, true, false);
        timeView.setAnimationProperties(0.7f, 0, 450, CubicBezierInterpolator.EASE_OUT_QUINT);
        timeView.setIgnoreRTL(!LocaleController.isRTL);
        timeView.adaptWidth = false;
        timeView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        timeView.setTextSize(AndroidUtilities.dp(13));
        timeView.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_REGULAR));
        timeView.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        timeView.setText(available ? args[4] : LocaleController.getString("LastCheck", R.string.LastCheck) + ": " + LocaleController.formatDateTime(ExteraConfig.lastUpdateCheckTime / 1000));
        header.addView(timeView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 20, Gravity.LEFT, available ? 75 : 0, 35, 0, 0));

        TextCell version = new TextCell(context);
        version.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), 100, 0));
        if (available) {
            version.setTextAndValueAndIcon(LocaleController.getString("Version", R.string.Version), args[0].replaceAll("v|-beta", ""), R.drawable.msg_info, true);
        } else {
            version.setTextAndValueAndIcon(LocaleController.getString("CurrentVersion", R.string.CurrentVersion), BuildVars.BUILD_VERSION_STRING, R.drawable.msg_info, true);
        }
        version.setOnClickListener(v -> copyText(version.getTextView().getText() + ": " + version.getValueTextView().getText()));
        linearLayout.addView(version);

        View divider = new View(context) {
            @Override
            protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                if (!ExteraConfig.disableDividers)
                    canvas.drawLine(0, AndroidUtilities.dp(1), getMeasuredWidth(), AndroidUtilities.dp(1), Theme.dividerPaint);
            }
        };

        if (available) {
            TextCell size = new TextCell(context);
            size.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), 100, 0));
            size.setTextAndValueAndIcon(LocaleController.getString("UpdateSize", R.string.UpdateSize), args[2], R.drawable.msg_sendfile, true);
            size.setOnClickListener(v -> copyText(size.getTextView().getText() + ": " + size.getValueTextView().getText()));
            linearLayout.addView(size);

            TextCell changelog = new TextCell(context);
            changelog.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), 100, 0));
            changelog.setTextAndIcon(LocaleController.getString("Changelog", R.string.Changelog), R.drawable.msg_log, false);
            changelog.setOnClickListener(v -> copyText(changelog.getTextView().getText() + "\n"));
            linearLayout.addView(changelog);

            TextInfoPrivacyCell changelogTextView = new TextInfoPrivacyCell(context);
            changelogTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            changelogTextView.setText(UpdaterUtils.replaceTags(args[1]));
            linearLayout.addView(changelogTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            linearLayout.addView(divider, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, AndroidUtilities.dp(1)));

            TextView doneButton = new TextView(context);
            doneButton.setLines(1);
            doneButton.setSingleLine(true);
            doneButton.setEllipsize(TextUtils.TruncateAt.END);
            doneButton.setGravity(Gravity.CENTER);
            doneButton.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText));
            doneButton.setBackground(Theme.AdaptiveRipple.filledRect(Theme.getColor(Theme.key_featuredStickers_addButton), 6));
            doneButton.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
            doneButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            doneButton.setText(LocaleController.getString("AppUpdateDownloadNow", R.string.AppUpdateDownloadNow));
            doneButton.setOnClickListener(v -> {
                UpdaterUtils.downloadApk(fragment.getContext(), args[3], "exteraGram " + args[0]);
                dismiss();
            });
            linearLayout.addView(doneButton, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, 0, 16, 15, 16, 5));

            TextView scheduleButton = new TextView(context);
            scheduleButton.setLines(1);
            scheduleButton.setSingleLine(true);
            scheduleButton.setEllipsize(TextUtils.TruncateAt.END);
            scheduleButton.setGravity(Gravity.CENTER);
            scheduleButton.setTextColor(Theme.getColor(Theme.key_featuredStickers_addButton));
            scheduleButton.setBackground(null);
            scheduleButton.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
            scheduleButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            scheduleButton.setText(LocaleController.getString("AppUpdateRemindMeLater", R.string.AppUpdateRemindMeLater));
            scheduleButton.setOnClickListener(v -> {
                ExteraConfig.editor.putLong("updateScheduleTimestamp", ExteraConfig.updateScheduleTimestamp = System.currentTimeMillis()).apply();
                dismiss();
            });
            linearLayout.addView(scheduleButton, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, 0, 16, 0, 16, 0));
        } else {
            final String btype = BuildVars.isBetaApp() ? LocaleController.getString("BTBeta", R.string.BTBeta) : LocaleController.getString("BTRelease", R.string.BTRelease);
            TextCell buildType = new TextCell(context);
            buildType.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), 100, 0));
            buildType.setTextAndValueAndIcon(LocaleController.getString("BuildType", R.string.BuildType), btype, R.drawable.msg_customize, true);
            buildType.setOnClickListener(v -> copyText(buildType.getTextView().getText() + ": " + buildType.getValueTextView().getText()));
            linearLayout.addView(buildType);

            TextCell checkOnLaunch = new TextCell(context);
            checkOnLaunch.setEnabled(true);
            checkOnLaunch.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), 100, 0));
            checkOnLaunch.setTextAndCheckAndIcon(LocaleController.getString("CheckOnLaunch", R.string.CheckOnLaunch), ExteraConfig.checkUpdatesOnLaunch, R.drawable.msg_recent, true);
            checkOnLaunch.setOnClickListener(v -> {
                ExteraConfig.editor.putBoolean("checkUpdatesOnLaunch", ExteraConfig.checkUpdatesOnLaunch ^= true).apply();
                checkOnLaunch.setChecked(!checkOnLaunch.getCheckBox().isChecked());
            });
            linearLayout.addView(checkOnLaunch);

            TextCell clearUpdates = new TextCell(context);
            clearUpdates.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), 100, 0));
            clearUpdates.setTextAndIcon(LocaleController.getString("ClearUpdatesCache", R.string.ClearUpdatesCache), R.drawable.msg_clear, false);
            clearUpdates.setOnClickListener(v -> {
                if (UpdaterUtils.getOtaDirSize().replaceAll("\\D+", "").equals("0")) {
                    BulletinFactory.of(getContainer(), null).createErrorBulletin(LocaleController.getString("NothingToClear", R.string.NothingToClear)).show();
                } else {
                    BulletinFactory.of(getContainer(), null).createErrorBulletin(LocaleController.formatString("ClearedUpdatesCache", R.string.ClearedUpdatesCache, UpdaterUtils.getOtaDirSize())).show();
                    UpdaterUtils.cleanOtaDir();
                }
            });
            linearLayout.addView(clearUpdates);

            linearLayout.addView(divider, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, AndroidUtilities.dp(1)));

            FrameLayout checkUpdatesBackground = new FrameLayout(context);
            checkUpdatesBackground.setBackground(Theme.AdaptiveRipple.filledRect(Theme.getColor(Theme.key_featuredStickers_addButton), 6));
            linearLayout.addView(checkUpdatesBackground, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, 0, 16, 15, 16, 16));

            AnimatedTextView checkUpdates = new AnimatedTextView(context, true, true, false);
            checkUpdates.setAnimationProperties(.5f, 0, 450, CubicBezierInterpolator.EASE_OUT_QUINT);
            checkUpdates.setGravity(Gravity.CENTER);
            checkUpdates.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText));
            checkUpdates.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
            checkUpdates.setTextSize(AndroidUtilities.dp(14));
            checkUpdates.setIgnoreRTL(!LocaleController.isRTL);
            checkUpdates.adaptWidth = false;
            checkUpdates.setText(LocaleController.getString("CheckForUpdates", R.string.CheckForUpdates));
            checkUpdates.setOnClickListener(v -> {
                checkUpdates.setText(LocaleController.getString("CheckingForUpdates", R.string.CheckingForUpdates));
                UpdaterUtils.checkUpdates(fragment, true, () -> {
                    timeView.setText(LocaleController.getString("LastCheck", R.string.LastCheck) + ": " + LocaleController.formatDateTime(ExteraConfig.lastUpdateCheckTime / 1000));
                    checkUpdates.setText(LocaleController.getString("CheckForUpdates", R.string.CheckForUpdates));
                    BulletinFactory.of(getContainer(), null).createErrorBulletin(LocaleController.getString("NoUpdates", R.string.NoUpdates)).show();
                }, this::dismiss);
            });
            checkUpdatesBackground.addView(checkUpdates, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER));
        }

        ScrollView scrollView = new ScrollView(context);
        scrollView.addView(linearLayout);
        setCustomView(scrollView);
    }

    private void copyText(CharSequence text) {
        AndroidUtilities.addToClipboard(text);
        BulletinFactory.of(getContainer(), null).createCopyBulletin(LocaleController.getString("TextCopied", R.string.TextCopied)).show();
    }

    @Override
    public void show() {
        super.show();
        if (imageView != null) {
            imageView.playAnimation();
        }
    }
}
