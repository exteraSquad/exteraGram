/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.utils.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.SpannableStringBuilder;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;

import androidx.core.content.ContextCompat;

import com.exteragram.messenger.ExteraConfig;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.BaseCell;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.Cells.ChatMessageTransitionParams;

public final class ChatMessageCellUiHelper {

    private final Drawable[] drawables = new Drawable[2];
    private final PorterDuffColorFilter[] colorFilters = new PorterDuffColorFilter[2];
    private final int[] colors = new int[2];
    private StaticLayout layout;
    private StaticLayout lastLayout;
    private StaticLayout animateLayout;
    private int count;
    private int width;
    private float textSize;
    private Typeface typeface;
    private float lastX;
    private float animateFromX;
    private boolean animating;
    private boolean allowed = true;
    private int account = -1;
    private long dialogId;
    private int messageId;

    private static int getCount(MessageObject messageObject, MessageObject.GroupedMessages group) {
        if (!ExteraConfig.getShowForwardsCount() || messageObject == null || messageObject.messageOwner == null
                || (messageObject.messageOwner.flags & TLRPC.MESSAGE_FLAG_HAS_VIEWS) == 0
                || messageObject.scheduled || messageObject.notime || messageObject.isSponsored()
                || messageObject.isQuickReply() || messageObject.isWelcomeMessage()) {
            return 0;
        }
        int forwards = messageObject.messageOwner.forwards;
        if (group != null) {
            for (int i = 0; i < group.messages.size(); i++) {
                MessageObject groupMessage = group.messages.get(i);
                if (groupMessage.messageOwner != null) {
                    forwards = Math.max(forwards, groupMessage.messageOwner.forwards);
                }
            }
        }
        return Math.max(0, forwards);
    }

    public static MessageObject findCounterMessage(MessageObject.GroupedMessages group) {
        if (group == null) {
            return null;
        }
        for (int i = 0; i < group.messages.size(); i++) {
            MessageObject groupMessage = group.messages.get(i);
            MessageObject.GroupedMessagePosition position = group.getPosition(groupMessage);
            if (position != null && position.last) {
                return groupMessage;
            }
        }
        return null;
    }

    public boolean isChanged(MessageObject messageObject, MessageObject.GroupedMessages group) {
        return count != (allowed ? getCount(messageObject, group) : 0);
    }

    public void bind(MessageObject messageObject) {
        if (messageObject != null && (account != messageObject.currentAccount
                || dialogId != messageObject.getDialogId() || messageId != messageObject.getId())) {
            account = messageObject.currentAccount;
            dialogId = messageObject.getDialogId();
            messageId = messageObject.getId();
            layout = null;
            lastLayout = null;
            count = 0;
            width = 0;
            lastX = 0;
            allowed = true;
            resetAnimation();
        }
    }

    public int measure(Context context, MessageObject messageObject, MessageObject.GroupedMessages group, boolean allowed) {
        this.allowed = allowed;
        count = allowed ? getCount(messageObject, group) : 0;
        if (count == 0) {
            layout = null;
            width = 0;
            return 0;
        }
        if (drawables[0] == null) {
            for (int i = 0; i < drawables.length; i++) {
                drawables[i] = ContextCompat.getDrawable(context, R.drawable.msg_reply_small).mutate();
            }
        }
        Drawable drawable = drawables[0];
        TextPaint paint = Theme.chat_timePaint;
        String text = LocaleController.formatShortNumber(count, null);
        if (layout == null || !TextUtils.equals(layout.getText(), text)
                || textSize != paint.getTextSize() || typeface != paint.getTypeface()) {
            textSize = paint.getTextSize();
            typeface = paint.getTypeface();
            int textWidth = Math.max(1, (int) Math.ceil(paint.measureText(text)));
            layout = StaticLayout.Builder.obtain(text, 0, text.length(), paint, textWidth).setIncludePad(false).build();
        }
        float iconWidth = drawable.getIntrinsicWidth() * paint.getTextSize() / drawable.getIntrinsicHeight();
        width = (int) Math.ceil(layout.getWidth() + iconWidth + dp(10));
        return width;
    }

    public void recordDrawingState() {
        lastLayout = layout;
    }

    public boolean animateChange() {
        animateFromX = lastX;
        animating = layout != lastLayout;
        animateLayout = animating ? lastLayout : null;
        return animating;
    }

    public void resetAnimation() {
        animating = false;
        animateLayout = null;
    }

    public float draw(Canvas canvas, ChatMessageCell cell, float timeX, float timeY, float offsetX, float alpha, float progress, boolean selected) {
        if (layout == null && animateLayout == null) {
            return 0;
        }
        ChatMessageTransitionParams transition = cell.transitionParams;
        float changeProgress = transition.animateChangeProgress;
        boolean appearing = animating && animateLayout == null;
        boolean disappearing = animating && layout == null;
        float x = (transition.shouldAnimateTimeX ? cell.timeX : timeX) + offsetX;
        if (transition.shouldAnimateTimeX && !appearing
                && !(cell.getMessageObject().isRoundVideo() && transition.animateDrawBackground)) {
            x = disappearing ? animateFromX : AndroidUtilities.lerp(animateFromX, x, changeProgress);
        }
        MessageObject.GroupedMessages group = cell.getCurrentMessagesGroup();
        if (group != null && group.transitionParams.backgroundChangeBounds) {
            x += group.transitionParams.offsetRight;
        }
        if (transition.animateBackgroundBoundsInner) {
            x += cell.getAnimationOffsetX();
        }
        lastX = x;

        TextPaint paint = Theme.chat_timePaint;
        int originalAlpha = paint.getAlpha();
        int textAlpha = (int) (originalAlpha * progress);
        int colorKey;
        if (cell.shouldDrawTimeOnMedia()) {
            colorKey = cell.getMessageObject().shouldDrawWithoutBackground() ? Theme.key_chat_serviceText : Theme.key_chat_mediaViews;
        } else if (cell.getMessageObject().isOutOwner()) {
            colorKey = selected ? Theme.key_chat_outViewsSelected : Theme.key_chat_outViews;
        } else {
            colorKey = selected ? Theme.key_chat_inViewsSelected : Theme.key_chat_inViews;
        }
        int color = cell.getThemedColor(colorKey);
        int index = selected ? 1 : 0;
        if (colorFilters[index] == null || colors[index] != color) {
            colors[index] = color;
            colorFilters[index] = new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN);
        }
        Drawable drawable = drawables[index];
        drawable.setColorFilter(colorFilters[index]);
        float iconWidth = BaseCell.setDrawableBounds(drawable, x, timeY, paint.getTextSize());
        canvas.save();
        if (progress != 1f) {
            float scale = 0.5f + 0.5f * progress;
            int textWidth = layout != null ? layout.getWidth() : animateLayout.getWidth();
            canvas.scale(scale, scale, x + (iconWidth + dp(3) + textWidth) / 2f, drawable.getBounds().centerY());
        }
        float iconAlpha = appearing ? changeProgress : disappearing ? 1f - changeProgress : 1f;
        drawable.setAlpha((int) (255 * alpha * iconAlpha));
        canvas.save();
        canvas.scale(-1f, 1f, drawable.getBounds().exactCenterX(), 0);
        drawable.draw(canvas);
        canvas.restore();
        canvas.translate(x + iconWidth + dp(3), timeY);
        if (animateLayout != null) {
            paint.setAlpha((int) (textAlpha * (1f - changeProgress)));
            animateLayout.draw(canvas);
        }
        if (layout != null) {
            paint.setAlpha((int) (textAlpha * (animating ? changeProgress : 1f)));
            layout.draw(canvas);
        }
        canvas.restore();
        paint.setAlpha(originalAlpha);
        return width;
    }

    public void appendAccessibilityText(SpannableStringBuilder text) {
        if (count > 0) {
            text.append("\n").append(String.format(LocaleController.getPluralString("Shares", count), AndroidUtilities.formatCount(count)));
        }
    }

    private static PorterDuffColorFilter timeIconFilter;
    private static int timeIconFilterColor;

    public static void drawTimeIcon(Canvas canvas, Drawable drawable, ChatMessageCell cell) {
        int key;
        if (drawable == Theme.chat_msgInViewsDrawable || drawable == Theme.chat_msgInRepliesDrawable || drawable == Theme.chat_msgInPinnedDrawable) {
            key = Theme.key_chat_inViews;
        } else if (drawable == Theme.chat_msgInViewsSelectedDrawable || drawable == Theme.chat_msgInRepliesSelectedDrawable || drawable == Theme.chat_msgInPinnedSelectedDrawable) {
            key = Theme.key_chat_inViewsSelected;
        } else {
            drawable.draw(canvas);
            return;
        }
        int color = cell.getThemedColor(key);
        if (color == Theme.getColor(key)) {
            drawable.draw(canvas);
            return;
        }
        ColorFilter previous = drawable.getColorFilter();
        if (timeIconFilter == null || timeIconFilterColor != color) {
            timeIconFilter = new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN);
            timeIconFilterColor = color;
        }
        drawable.setColorFilter(timeIconFilter);
        drawable.draw(canvas);
        drawable.setColorFilter(previous);
    }
}
