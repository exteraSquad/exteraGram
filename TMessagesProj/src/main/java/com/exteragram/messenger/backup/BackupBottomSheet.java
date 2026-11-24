/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.backup;

import android.app.Activity;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.exteragram.messenger.ExteraConfig;
import com.exteragram.messenger.utils.chats.ChatUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.StickerImageView;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;

import java.io.File;

public class BackupBottomSheet extends BottomSheet {

    private final int difference;
    private final BaseFragment fragment;

    public BackupBottomSheet(BaseFragment fragment, MessageObject messageObject) {
        this(fragment, new File(ChatUtils.getInstance().getPathToMessage(messageObject)));
    }

    public BackupBottomSheet(BaseFragment fragment, File file) {
        super(fragment.getParentActivity(), false, fragment.getResourceProvider());
        this.fragment = fragment;

        Activity activity = fragment.getParentActivity();
        difference = PreferencesUtils.getInstance().getDiff(file);

        fixNavigationBar();

        FrameLayout frameLayout = new FrameLayout(activity);
        LinearLayout linearLayout = new LinearLayout(activity);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        frameLayout.addView(linearLayout);

        StickerImageView imageView = new StickerImageView(activity, currentAccount);
        imageView.setStickerPackName(ExteraConfig.PLACEHOLDERS);
        imageView.setStickerNum(6);
        imageView.getImageReceiver().setAutoRepeat(1);
        imageView.getImageReceiver().setAutoRepeatCount(1);
        linearLayout.addView(imageView, LayoutHelper.createLinear(144, 144, Gravity.CENTER_HORIZONTAL, 0, 16, 0, 0));

        TextView title = new TextView(activity);
        title.setGravity(Gravity.CENTER_HORIZONTAL);
        title.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        title.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        title.setText(LocaleController.getString(R.string.ImportTitle));
        linearLayout.addView(title, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 40, 20, 40, 0));

        TextView description = new TextView(activity);
        description.setGravity(Gravity.CENTER_HORIZONTAL);
        description.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        description.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
        description.setText(AndroidUtilities.replaceTags(LocaleController.formatPluralString("ImportChanges", difference)));
        linearLayout.addView(description, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 21, 15, 21, 8));

        ButtonWithCounterView button = new ButtonWithCounterView(activity, true, resourcesProvider);
        button.setRound();
        button.setText(LocaleController.getString(R.string.ImportConfirm), false);
        button.setOnClickListener(view -> {
            dismiss();
            PreferencesUtils.getInstance().importSettings(file, fragment.getParentActivity(), fragment.getParentLayout());
            BulletinFactory.of(fragment).createSimpleBulletin(R.raw.contact_check, LocaleController.getString(R.string.SettingsImported)).show();
        });
        linearLayout.addView(button, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, 0, 16, 15, 16, 8));

        ButtonWithCounterView cancel = new ButtonWithCounterView(activity, false, resourcesProvider);
        cancel.setRound();
        cancel.setNeutral();
        cancel.setText(LocaleController.getString(R.string.CancelConfirm), false);
        cancel.setOnClickListener(view -> dismiss());
        linearLayout.addView(cancel, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, 0, 16, 0, 16, 0));

        ScrollView scrollView = new ScrollView(activity);
        scrollView.addView(frameLayout);
        setCustomView(scrollView);
    }

    public void showIfPossible() {
        if (difference > 0) {
            show();
        } else {
            AndroidUtilities.runOnUIThread(() -> BulletinFactory.of(fragment).createSimpleBulletin(R.raw.error, LocaleController.getString(R.string.SameSettings)).show());
        }
    }
}