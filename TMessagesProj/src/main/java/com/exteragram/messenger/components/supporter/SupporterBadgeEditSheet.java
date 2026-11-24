/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.components.supporter;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.graphics.Canvas;
import android.graphics.Rect;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.math.MathUtils;

import com.exteragram.messenger.api.dto.BadgeDTO;
import com.exteragram.messenger.badges.BadgesController;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_stars;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.EditTextCell;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.ScaleStateListAnimator;
import org.telegram.ui.SelectAnimatedEmojiDialog;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;

public class SupporterBadgeEditSheet extends SupporterSheet {

    private static final int CAPTION_LIMIT = 200;

    private final long defaultId;
    private final long initialId;
    private final String initialText;
    private final EditTextCell captionCell;
    private final TextView captionPreview;
    private final ButtonWithCounterView resetButton;
    private final Runnable onSaved;
    private long documentId;
    private SelectAnimatedEmojiDialog.SelectAnimatedEmojiDialogWindow emojiDialog;

    public static SupporterBadgeEditSheet show(BaseFragment fragment, Runnable onSaved) {
        if (fragment.getParentActivity() == null) {
            return null;
        }
        SupporterBadgeEditSheet sheet = new SupporterBadgeEditSheet(fragment, fragment.getResourceProvider(), onSaved);
        return fragment.showDialog(sheet) != null ? sheet : null;
    }

    public SupporterBadgeEditSheet(BaseFragment fragment, Theme.ResourcesProvider resourcesProvider, Runnable onSaved) {
        super(fragment, true, resourcesProvider);
        this.onSaved = onSaved;

        final var context = fragment.getParentActivity();
        final TLRPC.User user = UserConfig.getInstance(currentAccount).getCurrentUser();
        final BadgeDTO defaultBadge = BadgesController.INSTANCE.getDefaultBadge(user);
        final BadgeDTO badge = BadgesController.INSTANCE.getBadge(user);
        defaultId = defaultBadge != null ? defaultBadge.getDocumentId() : 0;
        initialId = badge != null ? badge.getDocumentId() : defaultId;
        initialText = badge != null && badge.getText() != null ? badge.getText() : "";
        documentId = initialId;

        setBadge(documentId);
        final View iconView = getArrowView().getIconView();
        iconView.setOnClickListener(v -> openEmojiPicker());
        ScaleStateListAnimator.apply(iconView, .05f, 1.5f);

        setContent(getString(R.string.SupporterBadgeEditTitle), getString(R.string.SupporterBadgeEditInfo));
        addNumberPill(BadgesController.getNumber(user), true);
        final String userName = DialogObject.getName(user);
        captionPreview = addCaptionBubble(null);

        captionCell = new EditTextCell(context, getString(R.string.Caption), false, false, CAPTION_LIMIT, resourcesProvider);
        captionCell.setBackground(Theme.createRoundRectDrawable(dp(16), getThemedColor(Theme.key_windowBackgroundGray)));
        captionCell.setMultiline(true);
        captionCell.setText(initialText);
        captionCell.editText.setTypeface(AndroidUtilities.regular());
        FrameLayout captionContainer = new FrameLayout(context);
        captionContainer.addView(captionCell, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP, 12, 0, 12, 0));
        addView(captionContainer);

        TextView captionInfo = new TextView(context);
        captionInfo.setText(getString(R.string.SupporterBadgeCaptionInfo));
        captionInfo.setTypeface(AndroidUtilities.regular());
        captionInfo.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
        captionInfo.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        captionInfo.setPadding(dp(12 + 21), dp(8), dp(12 + 21), dp(8));
        addView(captionInfo);

        button.setText(getString(R.string.Save), false);
        button.setOnClickListener(v -> save());

        resetButton = addSecondaryButton(getString(R.string.SupporterBadgeReset), v -> {
            setDocumentId(defaultId);
            captionCell.setText("");
        });
        if (SupporterTransfer.canTransfer(currentAccount)) {
            addSecondaryButton(getString(R.string.Gift2TransferDo), v -> SupporterTransfer.start(fragment, container, v, resourcesProvider, this::dismiss));
        }
        captionCell.editText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                updateResetButton();
                updateCaptionPreview(userName);
            }
        });
        updateResetButton();
        updateCaptionPreview(userName);

        behindKeyboardColorKey = Theme.key_dialogBackground;
        commit();
    }

    private void setDocumentId(long id) {
        documentId = id;
        setBadge(id);
        updateResetButton();
    }

    private void updateCaptionPreview(String userName) {
        final String text = captionCell.getText().toString().trim();
        captionPreview.setVisibility(text.isEmpty() ? View.GONE : View.VISIBLE);
        captionPreview.setText(text.isEmpty() ? null : BadgesController.formatBadgeCaption(text, userName, null, null));
    }

    private void updateResetButton() {
        resetButton.setEnabled(documentId != defaultId || captionCell.getText().length() > 0);
    }

    private void openEmojiPicker() {
        if (emojiDialog != null) {
            return;
        }
        final View anchor = getArrowView().getIconView();
        final int[] location = new int[2];
        anchor.getLocationOnScreen(location);
        final int popupWidth = (int) Math.min(dp(340 - 16), AndroidUtilities.displaySize.x * .95f);
        final int center = location[0] + anchor.getWidth() / 2;
        final int x = MathUtils.clamp(center - popupWidth / 2, 0, AndroidUtilities.displaySize.x - popupWidth);
        final int topMarginDp = Math.round(anchor.getHeight() / AndroidUtilities.density);

        final SelectAnimatedEmojiDialog.SelectAnimatedEmojiDialogWindow[] popup = new SelectAnimatedEmojiDialog.SelectAnimatedEmojiDialogWindow[1];
        SelectAnimatedEmojiDialog layout = new SelectAnimatedEmojiDialog(fragment, getContext(), true, center - x, SelectAnimatedEmojiDialog.TYPE_EMOJI_STATUS, true, resourcesProvider, topMarginDp, getThemedColor(Theme.key_windowBackgroundWhiteBlueIcon), true) {
            @Override
            protected void onEmojiSelected(View emojiView, Long id, TLRPC.Document document, TL_stars.TL_starGiftUnique gift, Integer until) {
                setDocumentId(id == null ? defaultId : id);
                if (popup[0] != null) {
                    popup[0].dismiss();
                }
            }

            @Override
            protected float getScrimDrawableTranslationY() {
                return anchor.getHeight() / 2f - dp(topMarginDp);
            }

            @Override
            public boolean dispatchTouchEvent(MotionEvent ev) {
                if (ev.getAction() == MotionEvent.ACTION_DOWN && ev.getY() < dp(topMarginDp)) {
                    if (popup[0] != null) {
                        popup[0].dismiss();
                    }
                    return true;
                }
                return super.dispatchTouchEvent(ev);
            }
        };
        final AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable scrim = new AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable(layout, anchor.getWidth(), AnimatedEmojiDrawable.CACHE_TYPE_ALERT_PREVIEW_LARGE_140) {
            private int alpha = 255;

            @Override
            public void setAlpha(int alpha) {
                this.alpha = alpha;
            }

            @Override
            public void draw(@NonNull Canvas canvas) {
                final Rect bounds = getBounds();
                canvas.saveLayerAlpha(bounds.left, bounds.top, bounds.right, bounds.bottom, alpha);
                canvas.translate(bounds.left, bounds.top);
                canvas.scale(bounds.width() / (float) anchor.getWidth(), bounds.height() / (float) anchor.getHeight());
                anchor.draw(canvas);
                canvas.restore();
            }
        };
        scrim.setCurrentAccount(currentAccount);
        scrim.setColor(getThemedColor(Theme.key_featuredStickers_addButton));
        scrim.setBounds(0, 0, anchor.getWidth(), anchor.getHeight());
        scrim.set(documentId, false);
        scrim.attach();
        layout.setScrimDrawable(scrim, null);
        if (defaultId != 0) {
            layout.setDefaultBadge(defaultId);
        }
        layout.useAccentForPlus = true;
        layout.setSelected(documentId == defaultId ? 0L : documentId);

        popup[0] = emojiDialog = new SelectAnimatedEmojiDialog.SelectAnimatedEmojiDialogWindow(layout, LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT) {
            @Override
            public void dismiss() {
                super.dismiss();
                scrim.detach();
                emojiDialog = null;
            }
        };
        popup[0].showAsDropDown(anchor, x - location[0], -dp(topMarginDp), Gravity.TOP | Gravity.LEFT);
        popup[0].dimBehind();
    }

    private void save() {
        final String text = captionCell.getText().toString().trim();
        if (documentId == initialId && text.equals(initialText)) {
            dismiss();
            return;
        }
        AndroidUtilities.hideKeyboard(captionCell.editText);
        button.setLoading(true);
        BadgesController.INSTANCE.updateBadge(new BadgeDTO(documentId, text.isEmpty() ? null : text), response -> AndroidUtilities.runOnUIThread(() -> {
            button.setLoading(false);
            if ("ok".equals(response)) {
                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.updateInterfaces, MessagesController.UPDATE_MASK_EMOJI_STATUS);
                if (onSaved != null) {
                    onSaved.run();
                }
                dismiss();
            } else {
                BulletinFactory.of(container, resourcesProvider).createErrorBulletin(getString(R.string.UnknownError)).show();
            }
        }));
    }

    @Override
    public void dismiss() {
        if (emojiDialog != null) {
            emojiDialog.dismiss();
        }
        super.dismiss();
    }
}
