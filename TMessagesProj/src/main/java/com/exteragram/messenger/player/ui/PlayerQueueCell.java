/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.player.ui;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;
import static org.telegram.messenger.AndroidUtilities.lerp;
import static org.telegram.messenger.LocaleController.formatString;
import static org.telegram.messenger.LocaleController.getString;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.style.CharacterStyle;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;
import androidx.dynamicanimation.animation.SpringAnimation;

import com.exteragram.messenger.player.PlayerActions;
import com.exteragram.messenger.player.PlayerColors;
import com.exteragram.messenger.player.PlayerCovers;
import com.exteragram.messenger.utils.ui.material.Material3Tokens;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.DownloadController;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.Utilities;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.AnimatedEmojiSpan;
import org.telegram.ui.Components.DotDividerSpan;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.MediaActionDrawable;
import org.telegram.ui.FilteredSearchView;

import java.util.ArrayList;
import java.util.Locale;

@SuppressLint("ViewConstructor")
public class PlayerQueueCell extends FrameLayout implements DownloadController.FileDownloadProgressListener, ItemOptions.ScrimView {

    public static final int HEIGHT_DP = 56;
    private static final int COVER_DP = 44;
    private static final int TEXT_GAP_DP = 16;
    private static final int TITLE_Y_DP = 9;
    private static final int SUBTITLE_Y_DP = 29;
    private static final float CURRENT_RADIUS_DP = 12f;
    private static final float LIFT_RADIUS_DP = 16f;
    private static final int LIFT_INSET_DP = 8;
    private static final int MINI_OFFSET_DP = 16;
    private static final int MINI_RADIUS_DP = 10;
    private static final int MINI_RING_DP = 12;
    private static final int MINI_TOUCH_DP = 18;
    private static final int OVERLAY_ALPHA = 0x4D;

    private final int currentAccount;
    private final int observerTag;
    private final ImageReceiver imageReceiver;
    private final MediaActionDrawable iconDrawable;
    private final MediaActionDrawable miniDrawable;
    private final ImageView optionsButton;
    private final ExpressiveStateDrawable optionsBackground;
    private final TextPaint titlePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint subtitlePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF coverRect = new RectF();
    private final RectF rect = new RectF();
    private final int[] coverRadii = new int[4];
    private final SpringAnimation currentSpring;
    private final SpringAnimation liftSpring;

    private SpannableStringBuilder dotSpan;
    private StaticLayout titleLayout;
    private StaticLayout subtitleLayout;
    private AnimatedEmojiSpan.EmojiGroupedSpans titleEmojis;
    private AnimatedEmojiSpan.EmojiGroupedSpans subtitleEmojis;

    private MessageObject messageObject;
    private boolean globalSearch;
    private int buttonState;
    private int miniButtonState = -1;
    private int hasMiniProgress;
    private boolean miniButtonPressed;
    private boolean drawMini;
    private boolean current;
    private float currentProgress;
    private boolean lifted;
    private float liftProgress;
    private int sideInset = dp(PlayerDimens.SIDE_DP);

    private int textColor;
    private int secondaryColor;
    private int accentColor;
    private int onAccentColor;
    private int tonalColor;
    private int onTonalColor;
    private int backgroundColor;
    private int liftColor;

    public PlayerQueueCell(Context context, int currentAccount) {
        super(context);
        setWillNotDraw(false);
        setFocusable(true);
        this.currentAccount = currentAccount;
        observerTag = DownloadController.getInstance(currentAccount).generateObserverTag();

        imageReceiver = new ImageReceiver(this);
        iconDrawable = new MediaActionDrawable();
        iconDrawable.setDelegate(this::invalidate);
        miniDrawable = new MediaActionDrawable();
        miniDrawable.setMini(true);
        miniDrawable.setIcon(MediaActionDrawable.ICON_NONE, false);
        miniDrawable.setDelegate(this::invalidate);

        titlePaint.setTextSize(dp(15));
        titlePaint.setTypeface(AndroidUtilities.bold());
        subtitlePaint.setTextSize(dp(13));
        subtitlePaint.setTypeface(AndroidUtilities.regular());

        optionsButton = new ImageView(context);
        optionsButton.setScaleType(ImageView.ScaleType.CENTER);
        optionsButton.setBackground(optionsBackground = new ExpressiveStateDrawable(12));
        addView(optionsButton);

        currentSpring = Material3Tokens.createSpring(Material3Tokens.FAST_SPATIAL_DAMPING, Material3Tokens.FAST_SPATIAL_STIFFNESS, Material3Tokens.MIN_VISIBLE_CHANGE, value -> {
            currentProgress = value;
            invalidate();
        });
        liftSpring = Material3Tokens.createSpring(Material3Tokens.EFFECTS_DAMPING, Material3Tokens.EFFECTS_STIFFNESS, Material3Tokens.MIN_VISIBLE_CHANGE, value -> {
            liftProgress = Utilities.clamp01(value);
            invalidate();
        });
    }

    public void setColors(PlayerColors colors) {
        textColor = colors.text;
        secondaryColor = colors.textSecondary;
        accentColor = colors.accent;
        onAccentColor = colors.onAccent;
        tonalColor = colors.tonal;
        onTonalColor = colors.onTonal;
        backgroundColor = colors.background;
        liftColor = colors.surfaceContainer;
        optionsButton.setColorFilter(new PorterDuffColorFilter(colors.textSecondary, PorterDuff.Mode.SRC_IN));
        optionsBackground.setColors(colors.icon, 0);
        invalidate();
    }

    public void setSideInset(int sideInset) {
        if (this.sideInset != sideInset) {
            this.sideInset = sideInset;
            requestLayout();
        }
    }

    public void setGlobalSearch(boolean globalSearch) {
        this.globalSearch = globalSearch;
        if (globalSearch && dotSpan == null) {
            dotSpan = new SpannableStringBuilder(".");
            dotSpan.setSpan(new DotDividerSpan(), 0, 1, 0);
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    public void setMessageObject(MessageObject messageObject, boolean reorder, View.OnClickListener onOptions, View.OnTouchListener onReorderTouch) {
        this.messageObject = messageObject;
        optionsButton.setImageResource(reorder ? R.drawable.list_reorder : R.drawable.ic_ab_other);
        optionsButton.setContentDescription(reorder ? null : getString(R.string.AccDescrMoreOptions));
        optionsButton.setVisibility(reorder || onOptions != null ? View.VISIBLE : View.GONE);
        optionsButton.setOnClickListener(onOptions);
        optionsButton.setOnTouchListener(onReorderTouch);
        PlayerCovers.setCover(imageReceiver, messageObject, COVER_DP, false);
        requestLayout();
        updateButtonState(false, false);
        invalidate();
    }

    public MessageObject getMessageObject() {
        return messageObject;
    }

    public void setCurrent(boolean current, boolean animated) {
        if (this.current == current && (animated || currentProgress == (current ? 1f : 0f))) {
            return;
        }
        this.current = current;
        if (animated && isAttachedToWindow()) {
            Material3Tokens.animate(currentSpring, currentProgress, current ? 1f : 0f);
        } else {
            currentSpring.cancel();
            currentProgress = current ? 1f : 0f;
            invalidate();
        }
    }

    public void setLifted(boolean lifted) {
        if (this.lifted != lifted) {
            this.lifted = lifted;
            Material3Tokens.animate(liftSpring, liftProgress, lifted ? 1f : 0f);
        }
    }

    private int getTextLeft() {
        return sideInset + dp(COVER_DP + TEXT_GAP_DP);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int width = MeasureSpec.getSize(widthMeasureSpec);
        final int buttonSpec = MeasureSpec.makeMeasureSpec(dp(PlayerDimens.ICON_BUTTON_DP), MeasureSpec.EXACTLY);
        optionsButton.measure(buttonSpec, buttonSpec);
        final int textRight = width - sideInset - (optionsButton.getVisibility() != GONE ? dp(PlayerDimens.ICON_BUTTON_DP + PlayerDimens.ICON_GAP_DP) : 0);
        buildLayouts(Math.max(dp(24), textRight - getTextLeft()));
        setMeasuredDimension(width, dp(HEIGHT_DP));
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        final int size = optionsButton.getMeasuredWidth();
        final int x = getMeasuredWidth() - sideInset - size;
        final int y = (getMeasuredHeight() - optionsButton.getMeasuredHeight()) / 2;
        optionsButton.layout(x, y, x + size, y + optionsButton.getMeasuredHeight());
    }

    private void buildLayouts(int maxWidth) {
        titleLayout = null;
        subtitleLayout = null;
        if (messageObject == null) {
            return;
        }
        try {
            final CharSequence title = TextUtils.ellipsize(messageObject.getMusicTitle().replace('\n', ' '), titlePaint, maxWidth, TextUtils.TruncateAt.END);
            titleLayout = makeLayout(highlight(title), titlePaint, maxWidth + dp(4));
            titleEmojis = AnimatedEmojiSpan.update(AnimatedEmojiDrawable.CACHE_TYPE_MESSAGES, this, titleEmojis, titleLayout);
        } catch (Exception e) {
            FileLog.e(e);
        }
        try {
            CharSequence author = messageObject.getMusicAuthor().replace('\n', ' ');
            if (globalSearch && dotSpan != null) {
                author = new SpannableStringBuilder(author).append(' ').append(dotSpan).append(' ').append(FilteredSearchView.createFromInfoString(messageObject, 2));
            }
            final CharSequence subtitle = TextUtils.ellipsize(author, subtitlePaint, maxWidth, TextUtils.TruncateAt.END);
            subtitleLayout = makeLayout(highlight(subtitle), subtitlePaint, maxWidth + dp(4));
            subtitleEmojis = AnimatedEmojiSpan.update(AnimatedEmojiDrawable.CACHE_TYPE_MESSAGES, this, subtitleEmojis, subtitleLayout);
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private static StaticLayout makeLayout(CharSequence text, TextPaint paint, int width) {
        return StaticLayout.Builder.obtain(text, 0, text.length(), paint, width)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, 1f)
                .setIncludePad(false)
                .build();
    }

    private CharSequence highlight(CharSequence text) {
        final ArrayList<String> words = messageObject.highlightedWords;
        if (words == null || words.isEmpty() || TextUtils.isEmpty(text)) {
            return text;
        }
        final String lower = text.toString().toLowerCase(Locale.getDefault());
        SpannableStringBuilder builder = null;
        for (int i = 0; i < words.size(); i++) {
            final String word = words.get(i);
            if (TextUtils.isEmpty(word)) {
                continue;
            }
            int index = lower.indexOf(word);
            while (index >= 0) {
                if (builder == null) {
                    builder = SpannableStringBuilder.valueOf(text);
                }
                builder.setSpan(new AccentSpan(), index, Math.min(index + word.length(), text.length()), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                index = lower.indexOf(word, index + 1);
            }
        }
        return builder != null ? builder : text;
    }

    private class AccentSpan extends CharacterStyle {
        @Override
        public void updateDrawState(TextPaint textPaint) {
            textPaint.setColor(accentColor);
        }
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        if (liftProgress > 0f) {
            drawLift(canvas, liftProgress);
        }
        drawContent(canvas, liftProgress);
    }

    @Override
    public void drawScrim(Canvas canvas, float progress) {
        final float lift = Math.max(progress, liftProgress);
        drawLift(canvas, lift);
        drawContent(canvas, lift);
        canvas.save();
        canvas.translate(optionsButton.getLeft(), optionsButton.getTop());
        optionsButton.draw(canvas);
        canvas.restore();
    }

    @Override
    public void getBounds(RectF bounds) {
        bounds.set(sideInset - dp(LIFT_INSET_DP), 0, getWidth() - sideInset + dp(LIFT_INSET_DP), getHeight());
    }

    private void drawLift(Canvas canvas, float progress) {
        if (progress <= 0f) {
            return;
        }
        rect.set(sideInset - dp(LIFT_INSET_DP), dp(2), getWidth() - sideInset + dp(LIFT_INSET_DP), getHeight() - dp(2));
        paint.setColor(liftColor);
        paint.setAlpha((int) (Color.alpha(liftColor) * Utilities.clamp01(progress)));
        canvas.drawRoundRect(rect, dpf2(LIFT_RADIUS_DP), dpf2(LIFT_RADIUS_DP), paint);
    }

    private void drawContent(Canvas canvas, float lift) {
        final float size = dp(COVER_DP);
        final float left = sideInset;
        final float top = (getHeight() - size) / 2f;
        coverRect.set(left, top, left + size, top + size);
        final float radius = Math.max(0f, Math.min(size / 2f, lerp(size / 2f, dpf2(CURRENT_RADIUS_DP), currentProgress)));
        final boolean hasImage = imageReceiver.hasBitmapImage();
        final float imageAlpha = hasImage ? imageReceiver.getCurrentAlpha() : 0f;
        if (imageAlpha < 1f) {
            paint.setColor(tonalColor);
            canvas.drawRoundRect(coverRect, radius, radius, paint);
        }
        if (hasImage) {
            imageReceiver.setImageCoords(coverRect.left, coverRect.top, size, size);
            if (coverRadii[0] != (int) radius) {
                coverRadii[0] = coverRadii[1] = coverRadii[2] = coverRadii[3] = (int) radius;
                imageReceiver.setRoundRadius(coverRadii);
            }
            imageReceiver.draw(canvas);
            paint.setColor(Color.BLACK);
            paint.setAlpha((int) (OVERLAY_ALPHA * imageAlpha));
            canvas.drawRoundRect(coverRect, radius, radius, paint);
        }
        iconDrawable.setColor(ColorUtils.blendARGB(onTonalColor, Color.WHITE, imageAlpha));
        iconDrawable.setBackColor(tonalColor);
        iconDrawable.setHasOverlayImage(hasImage);
        iconDrawable.setBounds((int) coverRect.left, (int) coverRect.top, (int) coverRect.right, (int) coverRect.bottom);
        iconDrawable.draw(canvas);
        if (drawMini) {
            final float alpha = miniDrawable.getCurrentIcon() != MediaActionDrawable.ICON_NONE ? 1f : 1f - miniDrawable.getTransitionProgress();
            if (alpha <= 0f) {
                drawMini = false;
            } else {
                final float cx = coverRect.centerX() + dp(MINI_OFFSET_DP);
                final float cy = coverRect.centerY() + dp(MINI_OFFSET_DP);
                paint.setColor(ColorUtils.blendARGB(backgroundColor, ColorUtils.compositeColors(liftColor, backgroundColor), Utilities.clamp01(lift)));
                canvas.drawCircle(cx, cy, dp(MINI_RING_DP) * alpha, paint);
                paint.setColor(accentColor);
                canvas.drawCircle(cx, cy, dp(MINI_RADIUS_DP) * alpha, paint);
                final int r = (int) (dp(MINI_RADIUS_DP) * alpha);
                miniDrawable.setColor(onAccentColor);
                miniDrawable.setBounds((int) cx - r, (int) cy - r, (int) cx + r, (int) cy + r);
                miniDrawable.draw(canvas);
            }
        }
        final float textLeft = getTextLeft();
        if (titleLayout != null) {
            titlePaint.setColor(textColor);
            canvas.save();
            canvas.translate(textLeft, dp(TITLE_Y_DP));
            titleLayout.draw(canvas);
            AnimatedEmojiSpan.drawAnimatedEmojis(canvas, titleLayout, titleEmojis, 0, null, 0, 0, 0, 1f);
            canvas.restore();
        }
        if (subtitleLayout != null) {
            subtitlePaint.setColor(secondaryColor);
            canvas.save();
            canvas.translate(textLeft, dp(SUBTITLE_Y_DP));
            subtitleLayout.draw(canvas);
            AnimatedEmojiSpan.drawAnimatedEmojis(canvas, subtitleLayout, subtitleEmojis, 0, null, 0, 0, 0, 1f);
            canvas.restore();
        }
    }

    private void setIcon(int icon, boolean ifSame, boolean animated) {
        if (ifSame && icon == iconDrawable.getCurrentIcon()) {
            return;
        }
        iconDrawable.setIcon(icon, animated);
        invalidate();
    }

    private void setMiniIcon(int icon, boolean ifSame, boolean animated) {
        if (ifSame && icon == miniDrawable.getCurrentIcon()) {
            return;
        }
        miniDrawable.setIcon(icon, animated);
        drawMini = icon != MediaActionDrawable.ICON_NONE || miniDrawable.getTransitionProgress() < 1f;
        invalidate();
    }

    private void setProgress(float progress, boolean animated) {
        if (drawMini) {
            miniDrawable.setProgress(progress, animated);
        } else {
            iconDrawable.setProgress(progress, animated);
        }
        invalidate();
    }

    private int getIconForCurrentState() {
        if (buttonState == 1) {
            return MediaActionDrawable.ICON_PAUSE;
        } else if (buttonState == 2) {
            return MediaActionDrawable.ICON_DOWNLOAD;
        } else if (buttonState == 4) {
            return MediaActionDrawable.ICON_CANCEL;
        }
        return MediaActionDrawable.ICON_PLAY;
    }

    private int getMiniIconForCurrentState() {
        if (miniButtonState < 0) {
            return MediaActionDrawable.ICON_NONE;
        }
        return miniButtonState == 0 ? MediaActionDrawable.ICON_DOWNLOAD : MediaActionDrawable.ICON_CANCEL;
    }

    public void updateButtonState(boolean ifSame, boolean animated) {
        if (messageObject == null) {
            return;
        }
        final String fileName = messageObject.getFileName();
        if (TextUtils.isEmpty(fileName)) {
            return;
        }
        boolean fileExists = PlayerActions.getLocalFile(messageObject).exists();
        if (SharedConfig.streamMedia && messageObject.getDialogId() != 0 && !DialogObject.isEncryptedDialog(messageObject.getDialogId())) {
            hasMiniProgress = fileExists ? 1 : 2;
            fileExists = true;
        } else {
            hasMiniProgress = 0;
            miniButtonState = -1;
        }
        final MediaController controller = MediaController.getInstance();
        final boolean playing = controller.isPlayingMessage(messageObject) && !controller.isMessagePaused();
        if (hasMiniProgress != 0) {
            buttonState = playing ? 1 : 0;
            setIcon(getIconForCurrentState(), ifSame, animated);
            if (hasMiniProgress == 1) {
                DownloadController.getInstance(currentAccount).removeLoadingFileObserver(this);
                miniButtonState = -1;
                setMiniIcon(getMiniIconForCurrentState(), ifSame, animated);
            } else {
                DownloadController.getInstance(currentAccount).addLoadingFileObserver(fileName, messageObject, this);
                if (!FileLoader.getInstance(currentAccount).isLoadingFile(fileName)) {
                    miniButtonState = 0;
                    setMiniIcon(getMiniIconForCurrentState(), ifSame, animated);
                } else {
                    miniButtonState = 1;
                    setMiniIcon(getMiniIconForCurrentState(), ifSame, animated);
                    final Float progress = ImageLoader.getInstance().getFileProgress(fileName);
                    setProgress(progress != null ? progress : 0f, animated);
                }
            }
        } else if (fileExists) {
            DownloadController.getInstance(currentAccount).removeLoadingFileObserver(this);
            buttonState = playing ? 1 : 0;
            setProgress(1f, animated);
            setIcon(getIconForCurrentState(), ifSame, animated);
        } else {
            DownloadController.getInstance(currentAccount).addLoadingFileObserver(fileName, this);
            if (!FileLoader.getInstance(currentAccount).isLoadingFile(fileName)) {
                buttonState = 2;
            } else {
                buttonState = 4;
                final Float progress = ImageLoader.getInstance().getFileProgress(fileName);
                setProgress(progress != null ? progress : 0f, animated);
            }
            setIcon(getIconForCurrentState(), ifSame, animated);
        }
    }

    public void didPressedButton() {
        if (messageObject == null) {
            return;
        }
        if (buttonState == 0) {
            if (miniButtonState == 0) {
                FileLoader.getInstance(currentAccount).loadFile(messageObject.getDocument(), messageObject, FileLoader.PRIORITY_NORMAL, 0);
            }
            if (MediaController.getInstance().findMessageInPlaylistAndPlay(messageObject)) {
                if (hasMiniProgress == 2 && miniButtonState != 1) {
                    miniButtonState = 1;
                    setProgress(0f, false);
                    setMiniIcon(getMiniIconForCurrentState(), false, true);
                }
                buttonState = 1;
                setIcon(getIconForCurrentState(), false, true);
            }
        } else if (buttonState == 1) {
            if (MediaController.getInstance().pauseMessage(messageObject)) {
                buttonState = 0;
                setIcon(getIconForCurrentState(), false, true);
            }
        } else if (buttonState == 2) {
            setProgress(0f, false);
            FileLoader.getInstance(currentAccount).loadFile(messageObject.getDocument(), messageObject, FileLoader.PRIORITY_NORMAL, 0);
            buttonState = 4;
            setIcon(getIconForCurrentState(), false, true);
        } else if (buttonState == 4) {
            FileLoader.getInstance(currentAccount).cancelLoadFile(messageObject.getDocument());
            buttonState = 2;
            setIcon(getIconForCurrentState(), false, true);
        }
    }

    private void didPressedMiniButton() {
        if (miniButtonState == 0) {
            miniButtonState = 1;
            setProgress(0f, false);
            FileLoader.getInstance(currentAccount).loadFile(messageObject.getDocument(), messageObject, FileLoader.PRIORITY_HIGH, 0);
            setMiniIcon(getMiniIconForCurrentState(), false, true);
        } else if (miniButtonState == 1) {
            if (MediaController.getInstance().isPlayingMessage(messageObject)) {
                MediaController.getInstance().pauseAndStopLoading(messageObject);
            }
            miniButtonState = 0;
            FileLoader.getInstance(currentAccount).cancelLoadFile(messageObject.getDocument());
            setMiniIcon(getMiniIconForCurrentState(), false, true);
        }
    }

    private boolean isInMiniButton(float x, float y) {
        if (miniButtonState < 0) {
            return false;
        }
        final float size = dp(COVER_DP);
        final float cx = sideInset + size / 2f + dp(MINI_OFFSET_DP);
        final float cy = getHeight() / 2f + dp(MINI_OFFSET_DP);
        return Math.abs(x - cx) <= dp(MINI_TOUCH_DP) && Math.abs(y - cy) <= dp(MINI_TOUCH_DP);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (messageObject == null) {
            return super.onTouchEvent(event);
        }
        final int action = event.getAction();
        final boolean inside = isInMiniButton(event.getX(), event.getY());
        boolean result = false;
        if (action == MotionEvent.ACTION_DOWN) {
            if (inside) {
                miniButtonPressed = true;
                result = true;
            }
        } else if (miniButtonPressed) {
            if (action == MotionEvent.ACTION_UP) {
                miniButtonPressed = false;
                playSoundEffect(SoundEffectConstants.CLICK);
                didPressedMiniButton();
            } else if (action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_MOVE && !inside) {
                miniButtonPressed = false;
            }
            result = miniButtonPressed || action == MotionEvent.ACTION_UP;
        }
        return result;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        imageReceiver.onAttachedToWindow();
        titleEmojis = AnimatedEmojiSpan.update(AnimatedEmojiDrawable.CACHE_TYPE_MESSAGES, this, titleEmojis, titleLayout);
        subtitleEmojis = AnimatedEmojiSpan.update(AnimatedEmojiDrawable.CACHE_TYPE_MESSAGES, this, subtitleEmojis, subtitleLayout);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        imageReceiver.onDetachedFromWindow();
        DownloadController.getInstance(currentAccount).removeLoadingFileObserver(this);
        AnimatedEmojiSpan.release(this, titleEmojis);
        AnimatedEmojiSpan.release(this, subtitleEmojis);
        currentSpring.cancel();
        liftSpring.cancel();
        currentProgress = current ? 1f : 0f;
        liftProgress = lifted ? 1f : 0f;
    }

    @Override
    public void onFailedDownload(String fileName, boolean canceled) {
        updateButtonState(true, canceled);
    }

    @Override
    public void onSuccessDownload(String fileName) {
        setProgress(1f, true);
        updateButtonState(false, true);
    }

    @Override
    public void onProgressDownload(String fileName, long downloadedSize, long totalSize) {
        setProgress(totalSize > 0 ? Math.min(1f, downloadedSize / (float) totalSize) : 0f, true);
        if (hasMiniProgress != 0) {
            if (miniButtonState != 1) {
                updateButtonState(false, true);
            }
        } else if (buttonState != 4) {
            updateButtonState(false, true);
        }
    }

    @Override
    public void onProgressUpload(String fileName, long uploadedSize, long totalSize, boolean isEncrypted) {
    }

    @Override
    public int getObserverTag() {
        return observerTag;
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        if (messageObject == null) {
            return;
        }
        if (messageObject.isMusic()) {
            info.setText(formatString(R.string.AccDescrMusicInfo, messageObject.getMusicAuthor(), messageObject.getMusicTitle()));
        } else if (titleLayout != null && subtitleLayout != null) {
            info.setText(titleLayout.getText() + ", " + subtitleLayout.getText());
        }
        info.setSelected(current);
    }
}
