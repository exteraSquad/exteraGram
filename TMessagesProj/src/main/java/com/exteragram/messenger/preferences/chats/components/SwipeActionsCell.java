/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.preferences.chats.components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.MotionEvent;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.exteragram.messenger.ExteraConfig;
import com.exteragram.messenger.preferences.components.CustomPreferenceCell;
import com.exteragram.messenger.utils.chats.SwipeAction;
import com.exteragram.messenger.utils.chats.SwipeActionsHelper;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.Components.BackgroundGradientDrawable;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.MotionBackgroundDrawable;
import org.telegram.ui.Components.Reactions.ReactionsLayoutInBubble;

import java.util.ArrayList;
import java.util.List;

public class SwipeActionsCell extends FrameLayout implements CustomPreferenceCell {

    private static final float SLIDE = 72f;
    private static final long BEAT = 1100L;

    private final ChatMessageCell messageCell;
    private final SwipeActionsHelper helper;
    private final List<SwipeAction> actions = new ArrayList<>();
    private final Drawable shadowDrawable;
    private final Drawable monetBackgroundDrawable;

    private BackgroundGradientDrawable.Disposable backgroundGradientDisposable;
    private Drawable backgroundDrawable;

    private ValueAnimator cycle;
    private List<SwipeAction> pending;
    private float slide;
    private int selected;
    private boolean looped;

    public SwipeActionsCell(Context context) {
        super(context);
        setWillNotDraw(false);

        monetBackgroundDrawable = new ColorDrawable(Theme.getColor(Theme.key_windowBackgroundGray));
        shadowDrawable = Theme.getThemedDrawable(context, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow);
        helper = new SwipeActionsHelper(this, null);

        final TLRPC.TL_message message = new TLRPC.TL_message();
        message.message = LocaleController.getString(R.string.SwipeActionsPreviewMessage);
        message.date = (int) (System.currentTimeMillis() / 1000) - 60 * 60;
        message.dialog_id = 1;
        message.flags = 259;
        message.from_id = new TLRPC.TL_peerUser();
        message.from_id.user_id = UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId();
        message.id = 1;
        message.out = false;
        message.peer_id = new TLRPC.TL_peerUser();
        message.peer_id.user_id = 0;

        final TLRPC.TL_message replied = new TLRPC.TL_message();
        replied.message = LocaleController.getString(R.string.SwipeActionsPreviewReply);
        replied.date = message.date;
        replied.dialog_id = 1;
        replied.flags = 259;
        replied.from_id = new TLRPC.TL_peerUser();
        replied.from_id.user_id = message.from_id.user_id;
        replied.id = 2;
        replied.media = new TLRPC.TL_messageMediaEmpty();
        replied.out = true;
        replied.peer_id = new TLRPC.TL_peerUser();
        replied.peer_id.user_id = 1;

        message.reply_to = new TLRPC.TL_messageReplyHeader();
        message.reply_to.flags |= 16;
        message.reply_to.reply_to_msg_id = replied.id;
        message.flags |= 8;

        final MessageObject messageObject = new MessageObject(UserConfig.selectedAccount, message, true, false);
        messageObject.replyMessageObject = new MessageObject(UserConfig.selectedAccount, replied, true, false);
        messageObject.customReplyName = LocaleController.getString(R.string.FromYou);
        messageObject.resetLayout();
        messageObject.eventId = 1;

        messageCell = new ChatMessageCell(context, UserConfig.selectedAccount);
        messageCell.isChat = false;
        messageCell.setFullyDraw(true);
        messageCell.setMessageObject(messageObject, null, false, false, false);
        addView(messageCell, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, android.view.Gravity.CENTER_VERTICAL));

        updateActions();
    }

    public void updateActions() {
        final List<SwipeAction> next = SwipeAction.enabled();
        if (next.equals(actions) && looped == ExteraConfig.getSwipeActionsLoop()) {
            return;
        }
        if (cycle == null || actions.isEmpty() || next.isEmpty()) {
            applyActions(next);
        } else {
            pending = next;
        }
    }

    private void applyActions(List<SwipeAction> next) {
        actions.clear();
        actions.addAll(next);
        looped = ExteraConfig.getSwipeActionsLoop();
        selected = 0;
        helper.start(actions);
        helper.select(0);
        if (actions.contains(SwipeAction.REACTION)) {
            final String emoticon = SwipeAction.quickReactionEmoticon(UserConfig.selectedAccount);
            if (emoticon != null) {
                final ReactionsLayoutInBubble.VisibleReaction reaction = ReactionsLayoutInBubble.VisibleReaction.fromEmojicon(emoticon);
                helper.setReaction(UserConfig.selectedAccount, reaction.emojicon, reaction.documentId);
            }
        }
        restartCycle();
    }

    private void restartCycle() {
        if (cycle != null) {
            cycle.cancel();
            cycle = null;
        }
        slide = 0;
        messageCell.setSlidingOffset(0);
        if (actions.isEmpty() || !isAttachedToWindow()) {
            invalidate();
            return;
        }
        final int steps = actions.size() + (looped && actions.size() > 1 ? 1 : 0);
        cycle = ValueAnimator.ofFloat(0f, 1f);
        cycle.setDuration(BEAT * (steps + 2));
        cycle.setInterpolator(new LinearInterpolator());
        cycle.setRepeatCount(ValueAnimator.INFINITE);
        cycle.addUpdateListener(animation -> {
            final float t = (float) animation.getAnimatedValue();
            final float span = 1f / (steps + 2);
            if (t < span) {
                slide = CubicBezierInterpolator.EASE_OUT_QUINT.getInterpolation(t / span);
            } else if (t > 1f - span) {
                slide = 1f - CubicBezierInterpolator.EASE_OUT_QUINT.getInterpolation((t - (1f - span)) / span);
            } else {
                slide = 1f;
            }
            if (t >= span && t <= 1f - span) {
                final int target = Math.max(0, Math.min(steps - 1, (int) ((t - span) / span))) % actions.size();
                if (target != selected) {
                    selected = target;
                    helper.select(target);
                }
            }
            messageCell.setSlidingOffset(-slide * dp(SLIDE));
            invalidate();
        });
        cycle.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationRepeat(Animator animation) {
                if (pending != null) {
                    final List<SwipeAction> next = pending;
                    pending = null;
                    AndroidUtilities.runOnUIThread(() -> applyActions(next));
                } else {
                    selected = 0;
                    helper.start(actions);
                    helper.select(0);
                }
            }
        });
        cycle.start();
    }

    @Override
    protected void dispatchDraw(@NonNull Canvas canvas) {
        super.dispatchDraw(canvas);
        if (actions.isEmpty()) {
            return;
        }
        final float translationX = -slide * dp(SLIDE);
        helper.draw(canvas, translationX, false,
                messageCell.getTop() + messageCell.getMeasuredHeight() / 2f,
                messageCell.getBackgroundDrawableRight() + translationX);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        final Drawable wallpaper = Theme.isCurrentThemeMonet() ? monetBackgroundDrawable : Theme.getCachedWallpaperNonBlocking();
        if (wallpaper != backgroundDrawable && wallpaper != null) {
            if (backgroundGradientDisposable != null) {
                backgroundGradientDisposable.dispose();
                backgroundGradientDisposable = null;
            }
            backgroundDrawable = wallpaper;
        }
        if (backgroundDrawable != null) {
            backgroundDrawable.setAlpha(backgroundDrawable == monetBackgroundDrawable ? 150 : 255);
            drawWallpaper(canvas, backgroundDrawable);
        }
        shadowDrawable.setBounds(0, 0, getMeasuredWidth(), getMeasuredHeight());
        shadowDrawable.draw(canvas);
    }

    private void drawWallpaper(Canvas canvas, Drawable drawable) {
        if (drawable instanceof ColorDrawable || drawable instanceof GradientDrawable || drawable instanceof MotionBackgroundDrawable) {
            drawable.setBounds(0, 0, getMeasuredWidth(), getMeasuredHeight());
            if (drawable instanceof BackgroundGradientDrawable gradient) {
                backgroundGradientDisposable = gradient.drawExactBoundsSize(canvas, this);
            } else {
                drawable.draw(canvas);
            }
        } else if (drawable instanceof BitmapDrawable bitmap) {
            canvas.save();
            if (bitmap.getTileModeX() == Shader.TileMode.REPEAT) {
                final float scale = 2.0f / AndroidUtilities.density;
                canvas.scale(scale, scale);
                drawable.setBounds(0, 0, (int) Math.ceil(getMeasuredWidth() / scale), (int) Math.ceil(getMeasuredHeight() / scale));
            } else {
                final float scale = Math.max((float) getMeasuredWidth() / drawable.getIntrinsicWidth(), (float) getMeasuredHeight() / drawable.getIntrinsicHeight());
                final int width = (int) Math.ceil(drawable.getIntrinsicWidth() * scale);
                final int height = (int) Math.ceil(drawable.getIntrinsicHeight() * scale);
                canvas.clipRect(0, 0, width, getMeasuredHeight());
                drawable.setBounds((getMeasuredWidth() - width) / 2, (getMeasuredHeight() - height) / 2,
                        (getMeasuredWidth() - width) / 2 + width, (getMeasuredHeight() - height) / 2 + height);
            }
            drawable.draw(canvas);
            canvas.restore();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(dp(150), MeasureSpec.EXACTLY));
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (cycle == null) {
            restartCycle();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (cycle != null) {
            cycle.cancel();
            cycle = null;
        }
        helper.detach();
        if (backgroundGradientDisposable != null) {
            backgroundGradientDisposable.dispose();
            backgroundGradientDisposable = null;
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        return false;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        return false;
    }

    @Override
    protected void dispatchSetPressed(boolean pressed) {
    }

    @Override
    public boolean isSameCell(@Nullable Object object) {
        if (this == object) return true;
        if (!(object instanceof SwipeActionsCell other)) return false;
        return actions.equals(other.actions);
    }
}
