/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.preferences.appearance.components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Region;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.exteragram.messenger.ExteraConfig;
import com.exteragram.messenger.preferences.components.AltSeekbar;
import com.exteragram.messenger.preferences.components.CustomPreferenceCell;
import com.exteragram.messenger.preferences.components.PreviewBackgroundDrawable;
import com.exteragram.messenger.preferences.components.PreviewColors;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.DialogCell;
import org.telegram.ui.Cells.ProfileChannelCell;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.LoadingDrawable;
import org.telegram.ui.Components.ScaleStateListAnimator;

import java.util.Objects;

@SuppressLint("ViewConstructor")
public class AvatarCornersPreviewCell extends FrameLayout implements CustomPreferenceCell {

    public enum Mode {
        REAL,
        MOCK
    }

    private final Mode currentMode;
    private final FrameLayout preview;
    private DialogCell dialogCell = null;

    private final AltSeekbar seekBar;
    private ProfileChannelCell.ChannelMessageFetcher fetcher;
    private int lastWidth;

    private boolean loadingChannel;
    private boolean loadingMessages;

    private AnimatedFloat channelLoadingAlpha;
    private AnimatedFloat messagesLoadingAlpha;
    private LoadingDrawable loadingDrawable;
    private Paint mockPaint;
    private Paint brightMockPaint;

    private static final long EXTERAGRAM_ID = 1571726392;
    private static final String EXTERAGRAM_USERNAME = "exteraGram";

    public AvatarCornersPreviewCell(Context context, BaseFragment fragment, Theme.ResourcesProvider resourcesProvider, int mode) {
        super(context);
        this.currentMode = Mode.values()[mode];
        setWillNotDraw(false);
        setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));

        int currentAccount = UserConfig.selectedAccount;
        var messagesController = MessagesController.getInstance(currentAccount);

        preview = new FrameLayout(context) {
            @Override
            protected void dispatchDraw(@NonNull Canvas canvas) {
                super.dispatchDraw(canvas);
                if (currentMode == Mode.REAL) {
                    drawLoading(canvas);
                } else {
                    drawMock(canvas);
                }
            }
        };
        if (currentMode == Mode.REAL) {
            ScaleStateListAnimator.apply(preview, 0.03f, 1.5f);
            preview.setOnClickListener(v -> messagesController.openByUserName(EXTERAGRAM_USERNAME, fragment, 1));
        }
        preview.setWillNotDraw(false);
        preview.setBackground(new PreviewBackgroundDrawable());

        int startCornersSize = 0, endCornersSize = 28;
        seekBar = new AltSeekbar(context, (float p) -> {
            ExteraConfig.setAvatarCorners(p);
            invalidate();
            preview.invalidate();
            if (dialogCell != null) {
                dialogCell.update(0);
            }
            fragment.getParentLayout().rebuildFragments(0);
        }, startCornersSize, endCornersSize, LocaleController.getString(R.string.AvatarCorners), LocaleController.getString(R.string.CropSquare), LocaleController.getString(R.string.PaintCircle));
        seekBar.setProgress(ExteraConfig.getAvatarCorners());
        addView(seekBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        if (currentMode == Mode.REAL) {
            initRealMode(context, resourcesProvider, currentAccount, messagesController);
            preview.addView(dialogCell, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 0, 3, 0, 4));
        } else {
            initMockMode();
            preview.setMinimumHeight(dp(83));
        }

        addView(preview, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER, 21, 114, 21, 21));
    }

    public AvatarCornersPreviewCell(Context context, BaseFragment fragment, Theme.ResourcesProvider resourcesProvider) {
        this(context, fragment, resourcesProvider, 0);
    }

    private void initRealMode(Context context, Theme.ResourcesProvider resourcesProvider, int currentAccount, MessagesController messagesController) {
        fetcher = new ProfileChannelCell.ChannelMessageFetcher(currentAccount);
        channelLoadingAlpha = new AnimatedFloat(this, 320, CubicBezierInterpolator.EASE_OUT_QUINT);
        messagesLoadingAlpha = new AnimatedFloat(this, 320, CubicBezierInterpolator.EASE_OUT_QUINT);

        loadingDrawable = new LoadingDrawable();
        loadingDrawable.setColors(
                Theme.multAlpha(Theme.getColor(Theme.key_listSelector), 1.3f),
                Theme.multAlpha(Theme.getColor(Theme.key_listSelector), .85f)
        );
        loadingDrawable.setRadiiDp(8);

        dialogCell = new DialogCell(null, context, false, true, currentAccount, resourcesProvider);
        dialogCell.isForChannelSubscriberCell = true;
        dialogCell.setDialogCellDelegate(new DialogCell.DialogCellDelegate() {
            @Override public boolean canClickButtonInside() { return false; }
            @Override public void onButtonClicked(DialogCell dialogCell) {}
            @Override public void onButtonLongPress(DialogCell dialogCell) {}
            @Override public void openStory(DialogCell dialogCell, Runnable onDone) {}
            @Override public void showChatPreview(DialogCell dialogCell) {}
            @Override public void openHiddenStories() {}
        });
        dialogCell.avatarStart = 15;
        dialogCell.messagePaddingStart = 83;

        if (messagesController.getChat(EXTERAGRAM_ID) != null) {
            setDialogId(EXTERAGRAM_ID);
        } else {
            loadingChannel = true;
            loadingMessages = true;
            messagesController.getUserNameResolver().resolve(EXTERAGRAM_USERNAME, id -> {
                loadingChannel = false;
                if (id != null) setDialogId(-id);
                invalidate();
            });
        }
    }

    private void initMockMode() {
        mockPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mockPaint.setColor(PreviewColors.getMockColor(false));

        brightMockPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        brightMockPaint.setColor(PreviewColors.getMockColor(true));
    }

    @SuppressWarnings("deprecation")
    private void drawMock(Canvas canvas) {
        float cellY = dp(1);
        float avatarStart = dp(15);
        float messageStart = dp(83);
        int avatarSize = dp(56);
        float previewWidth = preview.getWidth();

        RectF rect = AndroidUtilities.rectTmp;

        float avatarTop = cellY + dp(12);
        float avatarRight = avatarStart + avatarSize;
        float avatarBottom = avatarTop + avatarSize;

        rect.set(avatarStart, avatarTop, avatarRight, avatarBottom);

        float t = 1.0f - (ExteraConfig.getAvatarCorners() / 28.0f);
        t = Math.max(0, Math.min(1.0f, t));

        float outerR = AndroidUtilities.dpf2(7.0f + 2.0f * t);
        float innerR = AndroidUtilities.dpf2(5.0f + t);

        float ox = ExteraConfig.getOnlineDotOffset(AndroidUtilities.dpf2(8.0f), outerR);
        float oy = ExteraConfig.getOnlineDotOffset(AndroidUtilities.dpf2(7.5f), outerR);

        float dotX = avatarRight - ox;
        float dotY = avatarBottom - oy;

        canvas.save();

        @SuppressLint("DrawAllocation")
        Path online = new Path();
        online.addCircle(dotX, dotY, outerR, Path.Direction.CCW);
        canvas.clipPath(online, Region.Op.DIFFERENCE);

        canvas.drawRoundRect(rect, ExteraConfig.getAvatarCorners(56), ExteraConfig.getAvatarCorners(56), brightMockPaint);

        canvas.restore();

        Theme.dialogs_onlineCirclePaint.setColor(Theme.getColor(Theme.key_chats_onlineCircle));
        canvas.drawCircle(dotX, dotY, innerR, Theme.dialogs_onlineCirclePaint);

        float textRadius = dp(4);

        rect.set(
                messageStart + dp(6),
                cellY + dp(16),
                messageStart + dp(6) + previewWidth * 0.4f,
                cellY + dp(16 + 8.33f)
        );
        canvas.drawRoundRect(rect, textRadius, textRadius, brightMockPaint);

        rect.set(
                messageStart + dp(6),
                cellY + dp(38),
                messageStart + dp(6) + previewWidth * 0.5f,
                cellY + dp(38 + 8.33f)
        );
        canvas.drawRoundRect(rect, textRadius, textRadius, mockPaint);

        rect.set(
                messageStart + dp(6),
                cellY + dp(38 + 18),
                messageStart + dp(6) + previewWidth * 0.36f,
                cellY + dp(38 + 18 + 8.33f)
        );
        canvas.drawRoundRect(rect, textRadius, textRadius, mockPaint);

        rect.set(
                previewWidth - dp(16) - dp(43),
                cellY + dp(16),
                previewWidth - dp(16),
                cellY + dp(16 + 8.33f)
        );
        canvas.drawRoundRect(rect, textRadius, textRadius, mockPaint);
    }

    private void drawLoading(Canvas canvas) {
        if (loadingDrawable == null) return;

        float channelAlpha = channelLoadingAlpha.set(this.loadingChannel);
        float messagesAlpha = messagesLoadingAlpha.set(this.loadingMessages);

        boolean needsInvalidate = false;

        if (channelAlpha > 0) {
            loadingDrawable.setAlpha((int) (0xFF * channelAlpha));

            int avatarSize = dp(56);
            float avatarX = dialogCell.getX() + dp(dialogCell.avatarStart);
            float avatarY = dialogCell.getY() + dp(12);

            loadingDrawable.setRadiiDp((int) ExteraConfig.getAvatarCorners());

            AndroidUtilities.rectTmp.set(
                    avatarX,
                    avatarY,
                    avatarX + avatarSize,
                    avatarY + avatarSize
            );
            loadingDrawable.setBounds(AndroidUtilities.rectTmp);
            loadingDrawable.draw(canvas);
            loadingDrawable.setRadiiDp(4);

            AndroidUtilities.rectTmp.set(
                    dialogCell.getX() + dp(dialogCell.messagePaddingStart + 6),
                    dialogCell.getY() + dp(16),
                    dialogCell.getX() + dp(dialogCell.messagePaddingStart + 6) + getWidth() * .4f,
                    dialogCell.getY() + dp(16 + 8.33f)
            );
            loadingDrawable.setBounds(AndroidUtilities.rectTmp);
            loadingDrawable.draw(canvas);

            needsInvalidate = true;
        }

        if (messagesAlpha > 0) {
            loadingDrawable.setAlpha((int) (0xFF * messagesAlpha));

            AndroidUtilities.rectTmp.set(
                    dialogCell.getX() + dp(dialogCell.messagePaddingStart + 6),
                    dialogCell.getY() + dp(38),
                    dialogCell.getX() + dp(dialogCell.messagePaddingStart + 6) + getWidth() * .5f,
                    dialogCell.getY() + dp(38 + 8.33f)
            );
            loadingDrawable.setBounds(AndroidUtilities.rectTmp);
            loadingDrawable.draw(canvas);

            AndroidUtilities.rectTmp.set(
                    dialogCell.getX() + dp(dialogCell.messagePaddingStart + 6),
                    dialogCell.getY() + dp(38 + 18),
                    dialogCell.getX() + dp(dialogCell.messagePaddingStart + 6) + getWidth() * .36f,
                    dialogCell.getY() + dp(38 + 18 + 8.33f)
            );
            loadingDrawable.setBounds(AndroidUtilities.rectTmp);
            loadingDrawable.draw(canvas);

            AndroidUtilities.rectTmp.set(
                    dialogCell.getX() + dialogCell.getWidth() - dp(16) - dp(43),
                    dialogCell.getY() + dp(12),
                    dialogCell.getX() + dialogCell.getWidth() - dp(16),
                    dialogCell.getY() + dp(12 + 8.33f)
            );
            loadingDrawable.setBounds(AndroidUtilities.rectTmp);
            loadingDrawable.draw(canvas);

            needsInvalidate = true;
        }

        if (needsInvalidate) {
            invalidate();
        }
    }

    private boolean set = false;
    private long currentDialogId;

    public void setDialogId(long dialogId) {
        if (currentMode != Mode.REAL) return;

        final boolean firstSet = !set;
        currentDialogId = dialogId;

        messagesLoadingAlpha.set(1f, true);

        fetcher.fetch(dialogId, 0);
        fetcher.subscribe(() -> {
            if (dialogCell != null && currentDialogId == dialogId) {
                if (fetcher.messageObjects == null || fetcher.messageObjects.isEmpty()) {
                    if (!firstSet) {
                        dialogCell.setDialog(-dialogId, null, 0, false, true);
                    }
                    loadingMessages = true;
                } else {
                    final MessageObject lastMessage = fetcher.messageObjects.get(fetcher.messageObjects.size() - 1);
                    dialogCell.setDialog(-dialogId, lastMessage, fetcher.messageObjects, lastMessage.messageOwner.date, false, !firstSet);
                    loadingMessages = false;
                }
                dialogCell.invalidate();
            }

            if (!firstSet) {
                messagesLoadingAlpha.set(loadingMessages, true);
            }
            invalidate();

            set = true;
        });
    }

    @Override
    protected boolean verifyDrawable(@NonNull Drawable who) {
        return loadingDrawable == who || super.verifyDrawable(who);
    }

    @Override
    public void invalidate() {
        super.invalidate();
        preview.invalidate();
        seekBar.invalidate();
        if (dialogCell != null) {
            dialogCell.invalidate();
        }
        lastWidth = -1;
    }

    public void updateSliderStyle() {
        seekBar.updateStyle();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawLine(0, getMeasuredHeight() - 1, getMeasuredWidth(), getMeasuredHeight() - 1, Theme.dividerPaint);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        setMeasuredDimension(width, getMeasuredHeight());
        if (lastWidth != width) {
            lastWidth = width;
        }
    }

    @Override
    public boolean isSameCell(@Nullable Object object) {
        if (this == object) return true;
        if (!(object instanceof AvatarCornersPreviewCell other)) return false;
        return Objects.equals(seekBar, other.seekBar);
    }

    public interface onDrag {
        void run(float progress);
    }
}