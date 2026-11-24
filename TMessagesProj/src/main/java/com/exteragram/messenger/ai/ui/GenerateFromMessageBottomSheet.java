/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.ai.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.exteragram.messenger.ai.AiConfig;
import com.exteragram.messenger.ai.AiController;
import com.exteragram.messenger.ai.network.backend.OnDeviceAvailability;
import com.exteragram.messenger.components.CheckBoxRow;
import com.exteragram.messenger.utils.chats.ChatUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.EditTextCell;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.ScaleStateListAnimator;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;

import java.util.Objects;

import kotlin.Unit;

public class GenerateFromMessageBottomSheet extends BottomSheet {

    private final EditTextCell promptCell;
    BaseFragment parentFragment;
    private boolean useHistory;
    private boolean includeImage;

    public GenerateFromMessageBottomSheet(MessageObject messageObject, MessageObject.GroupedMessages groupedMessages, BaseFragment parentFragment, Context context, Utilities.Callback<GenerationData> onDonePress) {
        this(Objects.toString(ChatUtils.getInstance().getMessageText(messageObject, groupedMessages), ""), ChatUtils.getInstance().getPathToMessage(messageObject), parentFragment, context, onDonePress);
    }

    public GenerateFromMessageBottomSheet(BaseFragment parentFragment, Context context, Utilities.Callback<GenerationData> onDonePress, boolean canOverrideHistory) {
        this("", "", parentFragment, context, onDonePress, canOverrideHistory);
    }

    public GenerateFromMessageBottomSheet(String prompt, String imagePath, BaseFragment parentFragment, Context context, Utilities.Callback<GenerationData> onDonePress) {
        this(prompt, imagePath, parentFragment, context, onDonePress, AiConfig.getSaveHistory());
    }

    @SuppressLint("ClickableViewAccessibility")
    public GenerateFromMessageBottomSheet(String prompt, String imagePath, BaseFragment parentFragment, Context context, Utilities.Callback<GenerationData> onDonePress, boolean canOverrideHistory) {
        super(context, true, parentFragment.getResourceProvider());
        setBackgroundColor(getThemedColor(Theme.key_windowBackgroundGray));
        fixNavigationBar(getThemedColor(Theme.key_windowBackgroundGray));
        setApplyTopPadding(false);
        smoothKeyboardAnimationEnabled = true;
        this.parentFragment = parentFragment;
        this.useHistory = AiConfig.getSaveHistory();
        this.includeImage = AiController.canSendImage(imagePath);
        OnDeviceAvailability.warmup();

        ScrollView fragmentView = new ScrollView(context);

        LinearLayout contentLayout = new LinearLayout(context);
        contentLayout.setOrientation(LinearLayout.VERTICAL);
        contentLayout.setClipChildren(false);
        contentLayout.setClipToPadding(false);
        fragmentView.addView(contentLayout, LayoutHelper.createScroll(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP));

        FrameLayout topView = new FrameLayout(context);

        TextView titleView = new TextView(context);
        titleView.setText(LocaleController.getString(R.string.Generate));
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        titleView.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        topView.addView(titleView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 56, Gravity.LEFT | Gravity.TOP, 22, 6, 56, 0));

        ImageView closeButton = new ImageView(context);
        closeButton.setImageResource(R.drawable.ic_close_white);
        closeButton.setScaleType(ImageView.ScaleType.CENTER);
        closeButton.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_windowBackgroundWhiteBlackText), PorterDuff.Mode.SRC_IN));
        ScaleStateListAnimator.apply(closeButton);
        closeButton.setOnClickListener(v -> dismiss());
        topView.addView(closeButton, LayoutHelper.createFrame(48, 48, Gravity.RIGHT | Gravity.TOP, 0, 10, 12, 0));

        contentLayout.addView(topView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 70));

        promptCell = new EditTextCell(context, LocaleController.getString(R.string.RolePrompt), true, false, -1, resourcesProvider);
        promptCell.editText.setImeOptions(EditorInfo.IME_ACTION_DONE);
        promptCell.editText.setMaxLines(8);
        promptCell.setBackground(Theme.createRoundRectDrawable(dp(20), getThemedColor(Theme.key_windowBackgroundWhite)));
        if (!TextUtils.isEmpty(prompt)) {
            promptCell.setText(prompt);
        }
        promptCell.editText.setOnTouchListener((v, event) -> {
            v.getParent().requestDisallowInterceptTouchEvent(true);
            if ((event.getAction() & MotionEvent.ACTION_MASK) == MotionEvent.ACTION_UP) {
                v.getParent().requestDisallowInterceptTouchEvent(false);
            }
            return false;
        });
        contentLayout.addView(promptCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 12, 0, 12, 0));

        boolean canOverride = AiConfig.getSaveHistory() && canOverrideHistory;
        boolean canSendImage = AiController.canSendImage(imagePath);
        LinearLayout checkboxesLayout = null;
        if (canOverride || canSendImage) {
            checkboxesLayout = new LinearLayout(context);
            checkboxesLayout.setOrientation(LinearLayout.HORIZONTAL);
            checkboxesLayout.setGravity(Gravity.CENTER_HORIZONTAL);

            if (canOverride) {
                CheckBoxRow historyRow = new CheckBoxRow(context, LocaleController.getString(R.string.GenerateWithHistory), useHistory, resourcesProvider);
                historyRow.setOnCheckedChange(checked -> {
                    useHistory = checked;
                    return Unit.INSTANCE;
                });
                checkboxesLayout.addView(historyRow, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 4, 0, 4, 0));
            }
            if (canSendImage) {
                CheckBoxRow includeImageRow = new CheckBoxRow(context, LocaleController.getString(R.string.GenerateWithPhoto), includeImage, resourcesProvider);
                includeImageRow.setOnCheckedChange(checked -> {
                    includeImage = checked;
                    return Unit.INSTANCE;
                });
                BackupImageView previewImage = new BackupImageView(context);
                previewImage.getImageReceiver().setImage(imagePath, "24_24", null, null, 0);
                previewImage.setRoundRadius(dp(4));
                includeImageRow.addView(previewImage, LayoutHelper.createLinear(24, 24, Gravity.CENTER_VERTICAL, 9, 0, 0, 0));
                checkboxesLayout.addView(includeImageRow, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 4, 0, 4, 0));
            }
        }

        ButtonWithCounterView doneButton = new ButtonWithCounterView(context, resourcesProvider).setRound();
        doneButton.setText(LocaleController.getString(R.string.Proceed), false);
        doneButton.setOnClickListener(e -> {
            String newPrompt = promptCell.editText.getText().toString();

            if (TextUtils.isEmpty(newPrompt)) {
                if (!includeImage) {
                    AndroidUtilities.shakeViewSpring(promptCell);
                    e.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                    return;
                } else {
                    newPrompt = LocaleController.getString(R.string.AttachPhoto);
                }
            }

            dismiss();
            String imageToSend = includeImage ? imagePath : null;
            var generationData = new GenerationData(newPrompt, useHistory, imageToSend);
            onDonePress.run(generationData);
        });
        contentLayout.addView(doneButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 0, 12, 12, 12, checkboxesLayout != null ? 16 : 12));
        if (checkboxesLayout != null) {
            contentLayout.addView(checkboxesLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));
        }

        setCustomView(fragmentView);
    }

    @Override
    public void show() {
        super.show();
        AndroidUtilities.runOnUIThread(() -> {
            promptCell.editText.setSelection(promptCell.editText.length());
            AndroidUtilities.showKeyboard(promptCell.editText);
        }, 200);
    }

    public record GenerationData(String prompt, boolean useHistory, String imagePath) {

    }
}