/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.preferences.chats.components;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.MotionEvent;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.exteragram.messenger.ExteraConfig;
import com.exteragram.messenger.preferences.components.CustomPreferenceCell;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.INavigationLayout;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.BackgroundGradientDrawable;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.MotionBackgroundDrawable;

@SuppressLint("ViewConstructor")
public class MessagesPreviewCell extends LinearLayout implements CustomPreferenceCell {

    public static final int TYPE_DEFAULT = 0;
    public static final int TYPE_SINGLE = 1;

    private ChatMessageCell[] cells;
    private final MessageObject[] messageObjects;
    private final Drawable shadowDrawable;
    private final INavigationLayout parentLayout;
    private final int type;
    private final Drawable monetBackgroundDrawable;
    private BackgroundGradientDrawable.Disposable backgroundGradientDisposable;
    private BackgroundGradientDrawable.Disposable oldBackgroundGradientDisposable;
    private Drawable backgroundDrawable;
    private Drawable oldBackgroundDrawable;
    private int progress = -1;
    private final Runnable cancelProgress = () -> {
        progress = -1;
        for (ChatMessageCell cell : cells) {
            if (cell != null) {
                cell.invalidate();
            }
        }
    };

    public MessagesPreviewCell(Context context, INavigationLayout layout) {
        this(context, layout, TYPE_DEFAULT);
    }

    public MessagesPreviewCell(Context context, INavigationLayout layout, int type) {
        super(context);

        parentLayout = layout;
        this.type = type;

        int cellsCount = type == TYPE_SINGLE ? 1 : 2;
        cells = new ChatMessageCell[cellsCount];
        messageObjects = new MessageObject[cellsCount];

        setWillNotDraw(false);
        setOrientation(LinearLayout.VERTICAL);
        setPadding(0, AndroidUtilities.dp(11), 0, AndroidUtilities.dp(11));

        monetBackgroundDrawable = new ColorDrawable(Theme.getColor(Theme.key_windowBackgroundGray));
        shadowDrawable = Theme.getThemedDrawable(context, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow);

        TLRPC.TL_message message = new TLRPC.TL_message();
        int date = (int) (System.currentTimeMillis() / 1000) - 60 * 60;

        if (type == TYPE_DEFAULT) {
            message.date = date + 10;
            message.dialog_id = 1;
            message.flags = 257;
            message.from_id = new TLRPC.TL_peerUser();
            message.from_id.user_id = UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId();
            message.id = 1;
            message.media = new TLRPC.TL_messageMediaDocument();
            message.media.flags = 1;
            message.media.document = new TLRPC.TL_document();
            message.media.document.mime_type = "image/webp";
            message.media.document.file_reference = new byte[0];
            message.media.document.access_hash = 0;
            message.media.document.date = date;
            TLRPC.TL_documentAttributeSticker attributeSticker = new TLRPC.TL_documentAttributeSticker();
            attributeSticker.alt = "\uD83D\uDC08\u200D\u2B1B";
            message.media.document.attributes.add(attributeSticker);
            TLRPC.TL_documentAttributeImageSize attributeImageSize = new TLRPC.TL_documentAttributeImageSize();
            attributeImageSize.h = 512;
            attributeImageSize.w = 512;
            message.media.document.attributes.add(attributeImageSize);
            message.message = "";
            message.out = true;
            message.peer_id = new TLRPC.TL_peerUser();
            message.peer_id.user_id = 0;
            messageObjects[0] = new MessageObject(UserConfig.selectedAccount, message, true, false);
            messageObjects[0].useCustomPhoto = true;

            message = new TLRPC.TL_message();
            message.message = LocaleController.getString(R.string.StickerSizeDialogMessageReplyTo);
            message.date = date + 10;
            message.dialog_id = -1;
            message.flags = 259;
            message.id = 2;
            message.media = new TLRPC.TL_messageMediaEmpty();
            message.out = false;
            message.peer_id = new TLRPC.TL_peerUser();
            message.peer_id.user_id = 1;
            messageObjects[0].customReplyName = "immat0x1";
            messageObjects[0].replyMessageObject = new MessageObject(UserConfig.selectedAccount, message, true, false);

            message = new TLRPC.TL_message();
            message.message = LocaleController.getString(R.string.StickerSizeDialogMessage);
            message.date = date + 120;
            message.dialog_id = -1;
            message.flags = 257 + 8;
            message.id = 2;
            message.media = new TLRPC.TL_messageMediaEmpty();
            message.out = false;
            message.peer_id = new TLRPC.TL_peerUser();
            message.peer_id.user_id = 1;
            message.from_id = new TLRPC.TL_peerUser();
            message.reply_to = new TLRPC.TL_messageReplyHeader();
            message.reply_to.flags |= 16;
            message.reply_to.reply_to_msg_id = 5;
            messageObjects[1] = new MessageObject(UserConfig.selectedAccount, message, true, false);
            messageObjects[1].customReplyName = "8055";
            messageObjects[1].replyMessageObject = messageObjects[0];
        } else if (type == TYPE_SINGLE) {
            message = new TLRPC.TL_message();
            message.message = LocaleController.getString(R.string.MessagePreviewDialogMessage);
            String thinking = "\uD83E\uDD14";
            int index1 = message.message.indexOf(thinking);
            if (index1 >= 0) {
                TLRPC.TL_messageEntityCustomEmoji entity = new TLRPC.TL_messageEntityCustomEmoji();
                entity.offset = index1;
                entity.length = thinking.length();
                entity.document_id = 5330328892811018097L;
                message.entities.add(entity);
            }
            message.date = date + 60;
            message.dialog_id = 1;
            message.flags = 259 | TLRPC.MESSAGE_FLAG_EDITED | TLRPC.MESSAGE_FLAG_HAS_VIEWS;
            message.forwards = 67;
            message.edit_date = date + 120;
            message.from_id = new TLRPC.TL_peerUser();
            message.from_id.user_id = UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId();
            message.id = 1;
            message.out = false;
            message.peer_id = new TLRPC.TL_peerUser();
            message.peer_id.user_id = 0;

            messageObjects[0] = new MessageObject(UserConfig.selectedAccount, message, true, false);
            messageObjects[0].viewsReloaded = true;
            messageObjects[0].forceAvatar = true;
            messageObjects[0].resetLayout();
            messageObjects[0].eventId = 1;
        }

        for (int a = 0; a < cells.length; a++) {
            cells[a] = new ChatMessageCell(context, UserConfig.selectedAccount) {
                @Override
                protected boolean isUserOnline(TLRPC.User user) {
                    if (type == TYPE_SINGLE) {
                        return true;
                    }
                    return super.isUserOnline(user);
                }

                @Override
                protected void dispatchDraw(@NonNull Canvas canvas) {
                    if (getAvatarImage() != null && getAvatarImage().getImageHeight() != 0) {
                        getAvatarImage().setImageCoords(getAvatarImage().getImageX(), getMeasuredHeight() - getAvatarImage().getImageHeight() - AndroidUtilities.dp(4), getAvatarImage().getImageWidth(), getAvatarImage().getImageHeight());
                        getAvatarImage().setRoundRadius(ExteraConfig.getAvatarCorners(getAvatarImage().getImageHeight(), true));
                        drawAvatarWithOnlineStatus(canvas, getAvatarImage());
                    }
                    super.dispatchDraw(canvas);
                }

                @Override
                protected boolean checkNeedDrawShareButton(MessageObject messageObject) {
                    if (messageObject != null && !messageObject.isOutOwner() && type == TYPE_SINGLE) {
                        return true;
                    }
                    return super.checkNeedDrawShareButton(messageObject);
                }

                @Override
                protected boolean shouldHideShareButton(MessageObject messageObject, boolean drawAvatar) {
                    if (messageObject != null && !messageObject.isOutOwner() && type == TYPE_SINGLE) {
                        return ExteraConfig.getHideShareButton();
                    }
                    return super.shouldHideShareButton(messageObject, drawAvatar);
                }
            };
            cells[a].setDelegate(new ChatMessageCell.ChatMessageCellDelegate() {

                @Override
                public boolean canPerformActions() {
                    return true;
                }

                @Override
                public void didPressReplyMessage(ChatMessageCell cell, int id, float x, float y, boolean longpress) {
                    progress = ChatActivity.PROGRESS_REPLY;
                    cell.invalidate();

                    AndroidUtilities.cancelRunOnUIThread(cancelProgress);
                    AndroidUtilities.runOnUIThread(cancelProgress, 5000);
                }

                @Override
                public boolean isProgressLoading(ChatMessageCell cell, int type) {
                    return type == progress;
                }
            });
            cells[a].isChat = false;
            cells[a].hideViews = type == TYPE_SINGLE;
            cells[a].setFullyDraw(true);
            cells[a].setMessageObject(messageObjects[a], null, false, false, false);
            addView(cells[a], LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        }
    }

    @Override
    public void invalidate() {
        super.invalidate();
        for (ChatMessageCell cell : cells) {
            if (cell != null) {
                cell.invalidate();
            }
        }
    }

    public void refreshMessages() {
        for (int a = 0; a < cells.length; a++) {
            if (messageObjects[a] != null) {
                messageObjects[a].forceUpdate = true;
            }
            cells[a].setMessageObject(messageObjects[a], null, false, false, false);
            cells[a].invalidate();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        Drawable newDrawable = Theme.isCurrentThemeMonet() ? monetBackgroundDrawable : Theme.getCachedWallpaperNonBlocking();
        if (newDrawable != backgroundDrawable && newDrawable != null) {
            if (Theme.isAnimatingColor()) {
                oldBackgroundDrawable = backgroundDrawable;
                oldBackgroundGradientDisposable = backgroundGradientDisposable;
            } else if (backgroundGradientDisposable != null) {
                backgroundGradientDisposable.dispose();
                backgroundGradientDisposable = null;
            }
            backgroundDrawable = newDrawable;
        }
        float themeAnimationValue = parentLayout.getThemeAnimationValue();
        for (int a = 0; a < 2; a++) {
            Drawable drawable = a == 0 ? oldBackgroundDrawable : backgroundDrawable;
            int baseAlpha = 255;
            if (drawable == monetBackgroundDrawable) {
                baseAlpha = 150;
            }
            if (drawable == null) {
                continue;
            }
            if (a == 1 && oldBackgroundDrawable != null) {
                drawable.setAlpha((int) (baseAlpha * themeAnimationValue));
            } else {
                drawable.setAlpha(baseAlpha);
            }
            if (drawable instanceof ColorDrawable || drawable instanceof GradientDrawable || drawable instanceof MotionBackgroundDrawable) {
                drawable.setBounds(0, 0, getMeasuredWidth(), getMeasuredHeight());
                if (drawable instanceof BackgroundGradientDrawable backgroundGradientDrawable) {
                    backgroundGradientDisposable = backgroundGradientDrawable.drawExactBoundsSize(canvas, this);
                } else {
                    drawable.draw(canvas);
                }
            } else if (drawable instanceof BitmapDrawable bitmapDrawable) {
                if (bitmapDrawable.getTileModeX() == Shader.TileMode.REPEAT) {
                    canvas.save();
                    float scale = 2.0f / AndroidUtilities.density;
                    canvas.scale(scale, scale);
                    drawable.setBounds(0, 0, (int) Math.ceil(getMeasuredWidth() / scale), (int) Math.ceil(getMeasuredHeight() / scale));
                } else {
                    int viewHeight = getMeasuredHeight();
                    float scaleX = (float) getMeasuredWidth() / (float) drawable.getIntrinsicWidth();
                    float scaleY = (float) (viewHeight) / (float) drawable.getIntrinsicHeight();
                    float scale = Math.max(scaleX, scaleY);
                    int width = (int) Math.ceil(drawable.getIntrinsicWidth() * scale);
                    int height = (int) Math.ceil(drawable.getIntrinsicHeight() * scale);
                    int x = (getMeasuredWidth() - width) / 2;
                    int y = (viewHeight - height) / 2;
                    canvas.save();
                    canvas.clipRect(0, 0, width, getMeasuredHeight());
                    drawable.setBounds(x, y, x + width, y + height);
                }
                drawable.draw(canvas);
                canvas.restore();
            }
            if (a == 0 && oldBackgroundDrawable != null && themeAnimationValue >= 1.0f) {
                if (oldBackgroundGradientDisposable != null) {
                    oldBackgroundGradientDisposable.dispose();
                    oldBackgroundGradientDisposable = null;
                }
                oldBackgroundDrawable = null;
                invalidate();
            }
        }
        shadowDrawable.setBounds(0, 0, getMeasuredWidth(), getMeasuredHeight());
        shadowDrawable.draw(canvas);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (backgroundGradientDisposable != null) {
            backgroundGradientDisposable.dispose();
            backgroundGradientDisposable = null;
        }
        if (oldBackgroundGradientDisposable != null) {
            oldBackgroundGradientDisposable.dispose();
            oldBackgroundGradientDisposable = null;
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (type == TYPE_SINGLE) {
            return false;
        }
        return super.onInterceptTouchEvent(ev);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (type == TYPE_SINGLE) {
            return false;
        }
        return super.dispatchTouchEvent(ev);
    }

    @Override
    protected void dispatchSetPressed(boolean pressed) {

    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (type == TYPE_SINGLE) {
            return false;
        }
        return super.onTouchEvent(event);
    }

    @Override
    public boolean isSameCell(@Nullable Object object) {
        if (this == object) return true;
        if (!(object instanceof MessagesPreviewCell other)) return false;
        return type == other.type && progress == other.progress;
    }
}
