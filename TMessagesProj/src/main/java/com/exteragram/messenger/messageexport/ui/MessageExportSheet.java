/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.messageexport.ui;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.formatString;
import static org.telegram.messenger.LocaleController.getString;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.collection.LongSparseArray;
import androidx.core.graphics.ColorUtils;

import com.exteragram.messenger.ExteraConfig;
import com.exteragram.messenger.messageexport.appearance.MessageExportAppearance;
import com.exteragram.messenger.messageexport.model.MessageExportChatContext;
import com.exteragram.messenger.messageexport.model.MessageExportDocument;
import com.exteragram.messenger.messageexport.model.MessageExportSession;
import com.exteragram.messenger.messageexport.render.MessageExportListView;
import com.exteragram.messenger.messageexport.render.MessageExportWatermark;
import com.exteragram.messenger.messageexport.render.MessageExporter;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.messenger.SavedMessagesController;
import org.telegram.messenger.utils.ViewOutlineProviderImpl;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.EmojiThemes;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.Cells.TextSelectionHelper;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.Easings;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RLottieDrawable;
import org.telegram.ui.Components.RLottieImageView;
import org.telegram.ui.Components.ScaleStateListAnimator;
import org.telegram.ui.Components.ThanosEffect;
import org.telegram.ui.Components.ThemeRevealView;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;

import java.io.File;
import java.util.ArrayList;

public class MessageExportSheet extends BottomSheet implements MessageExportSession.Listener {

    private static final int ACTION_SEND_FILE = 0;
    private static final int ACTION_SEND_PHOTO = 1;
    private static final int ACTION_COPY = 2;
    private static final int ACTION_SAVE = 3;
    private static final int ACTION_SEND_STICKER = 4;

    private static final int NAV_DIM_ALPHA = 0x66;

    private final ChatActivity fragment;
    private final MessageExportSession session;
    private final MessageExportAppearance appearance;
    private final MessageExporter exporter;
    private final MessageExportWatermark watermark;
    private final FrameLayout card;
    private final View backgroundView;
    private final TextView subtitleView;
    private final RLottieImageView dayNightButton;
    private final RLottieDrawable dayNightDrawable;
    private final ThemePresetsView presetsView;
    private final ButtonWithCounterView shareButton;
    private final HiddenTextSelectionHelper selectionHelper;
    private final TextSelectionHelper<ChatMessageCell>.TextSelectionOverlay selectionOverlay;
    private final int[] location = new int[2];
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private MessageExportListView listView;
    private int listWidth;
    private int cardHeight;
    private int ensureAttempts;
    private boolean swipeAllowed = true;
    private ValueAnimator themeAnimator;
    private ValueAnimator navDimAnimator;
    private ThanosEffect thanosEffect;

    public MessageExportSheet(Context context, ChatActivity fragment, MessageExportSession session) {
        super(context, false, null);
        this.fragment = fragment;
        this.session = session;
        this.appearance = session.appearance;
        fixNavigationBar();
        session.setListener(this);
        exporter = new MessageExporter(context, session);
        watermark = new MessageExportWatermark(context);

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);

        int iconColor = getThemedColor(Theme.key_dialogTextBlack);
        FrameLayout header = new FrameLayout(context);
        ImageView closeButton = new ImageView(context);
        closeButton.setScaleType(ImageView.ScaleType.CENTER);
        closeButton.setImageResource(R.drawable.ic_close_white);
        closeButton.setColorFilter(iconColor);
        closeButton.setBackground(Theme.createSimpleSelectorCircleDrawable(dp(40), Theme.multAlpha(iconColor, 0.07f), Theme.multAlpha(iconColor, 0.15f)));
        closeButton.setContentDescription(getString(R.string.Close));
        closeButton.setOnClickListener(v -> dismiss());
        ScaleStateListAnimator.apply(closeButton, .1f, 1.5f);
        header.addView(closeButton, LayoutHelper.createFrame(40, 40, Gravity.LEFT | Gravity.CENTER_VERTICAL, 12, 0, 0, 0));

        LinearLayout titles = new LinearLayout(context);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView titleView = new TextView(context);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setTextColor(iconColor);
        titleView.setSingleLine();
        titleView.setGravity(Gravity.CENTER);
        titleView.setText(getString(R.string.MessageExportTitle));
        titles.addView(titleView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL));
        subtitleView = new TextView(context);
        subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        subtitleView.setTypeface(AndroidUtilities.regular());
        subtitleView.setTextColor(getThemedColor(Theme.key_dialogTextGray3));
        subtitleView.setSingleLine();
        subtitleView.setGravity(Gravity.CENTER);
        titles.addView(subtitleView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL));
        header.addView(titles, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 64, 0, 64, 0));

        dayNightDrawable = new RLottieDrawable(R.raw.sun_outline, dp(24), dp(24), false, null);
        dayNightDrawable.setAllowDecodeSingleFrame(true);
        dayNightDrawable.setPlayInDirectionOfCustomEndFrame(true);
        dayNightDrawable.setColorFilter(new PorterDuffColorFilter(iconColor, PorterDuff.Mode.MULTIPLY));
        int frame = appearance.isDark() ? dayNightDrawable.getFramesCount() - 1 : 0;
        dayNightDrawable.setCurrentFrame(frame, false, true);
        dayNightDrawable.setCustomEndFrame(frame);
        dayNightButton = new RLottieImageView(context);
        dayNightButton.setScaleType(ImageView.ScaleType.CENTER);
        dayNightButton.setAnimation(dayNightDrawable);
        dayNightButton.setBackground(Theme.createSimpleSelectorCircleDrawable(dp(40), Theme.multAlpha(iconColor, 0.07f), Theme.multAlpha(iconColor, 0.15f)));
        dayNightButton.setOnClickListener(v -> toggleDark());
        ScaleStateListAnimator.apply(dayNightButton, .1f, 1.5f);
        header.addView(dayNightButton, LayoutHelper.createFrame(40, 40, Gravity.RIGHT | Gravity.CENTER_VERTICAL, 0, 0, 12, 0));
        root.addView(header, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 60, 0, 6, 0, 6));

        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(Math.max(1, dp(0.66f)));
        strokePaint.setColor(Theme.multAlpha(iconColor, 0.1f));

        selectionHelper = new HiddenTextSelectionHelper(session, new HiddenTextSelectionHelper.Delegate() {
            @Override
            public boolean isBusy() {
                return exporter.isBusy();
            }

            @Override
            public void onHideText(MessageObject message, boolean caption, int start, int end) {
                MessageObject target = session.hideText(message, caption, start, end);
                if (target != null) {
                    BulletinFactory.of(container, resourcesProvider).createUndoBulletin(getString(R.string.MessageExportTextHidden), () -> session.undoLastHide(target), null).show();
                }
            }
        });

        card = new FrameLayout(context) {
            @Override
            public boolean dispatchTouchEvent(MotionEvent ev) {
                if (selectionOverlay != null) {
                    if (selectionHelper.isInSelectionMode() && selectionOverlay.onTouchEvent(ev)) {
                        return true;
                    }
                    if (selectionOverlay.checkOnTap(ev)) {
                        ev.setAction(MotionEvent.ACTION_CANCEL);
                    }
                }
                return super.dispatchTouchEvent(ev);
            }

            @Override
            protected void dispatchDraw(@NonNull Canvas canvas) {
                super.dispatchDraw(canvas);
                float inset = strokePaint.getStrokeWidth() / 2f;
                AndroidUtilities.rectTmp.set(inset, inset, getWidth() - inset, getHeight() - inset);
                canvas.drawRoundRect(AndroidUtilities.rectTmp, dp(16) - inset, dp(16) - inset, strokePaint);
            }

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int width = MeasureSpec.getSize(widthMeasureSpec);
                cardHeight = Math.max(dp(240), (int) Math.min(AndroidUtilities.displaySize.y * 0.6f, AndroidUtilities.displaySize.y - dp(250)));
                appearance.setServicePeriod(cardHeight);
                if (width > 0 && width != listWidth) {
                    listWidth = width;
                    session.document.setParentWidth(width);
                    listView.rebindAll(session.document.blocks);
                    post(MessageExportSheet.this::ensureListBound);
                }
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(cardHeight, MeasureSpec.EXACTLY));
            }
        };
        card.setOutlineProvider(ViewOutlineProviderImpl.boundsWithRoundRect(dp(16)));
        card.setClipToOutline(true);
        backgroundView = new View(context) {
            @Override
            protected void onDraw(@NonNull Canvas canvas) {
                appearance.getBackground().draw(canvas, getWidth(), 0, getHeight(), getHeight());
            }
        };
        card.addView(backgroundView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        appearance.getBackground().setOnUpdate(backgroundView::invalidate);
        selectionOverlay = selectionHelper.getOverlayView(context);
        createListView(context);
        if (selectionOverlay != null) {
            card.addView(selectionOverlay, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        }
        card.addView(exporter.getHostView(), new FrameLayout.LayoutParams(0, 0));
        root.addView(card, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, MessageExportChatContext.CARD_MARGIN, 0, MessageExportChatContext.CARD_MARGIN, 0));

        presetsView = new ThemePresetsView(context, session.document.context.account, appearance, resourcesProvider, this::selectPreset);
        root.addView(presetsView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 64, 0, 10, 0, 0));

        shareButton = new ButtonWithCounterView(context, true, resourcesProvider);
        shareButton.setRound();
        shareButton.setText(getString(R.string.ShareFile), false);
        shareButton.setOnClickListener(v -> showShareOptions());
        root.addView(shareButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, Gravity.FILL_HORIZONTAL, 16, 10, 16, 10));

        setCustomView(root);
        updateHeader();
    }

    private void createListView(Context context) {
        int index = -1;
        int[] scrollAnchor = null;
        if (listView != null) {
            scrollAnchor = listView.getScrollAnchor();
            index = card.indexOfChild(listView);
            card.removeView(listView);
        }
        selectionHelper.clear(true);
        if (thanosEffect != null) {
            thanosEffect.kill();
        }
        listView = new MessageExportListView(context, session, appearance.getProvider(), selectionHelper);
        listView.setPadding(0, dp(8), 0, MessageExportWatermark.getBottomInset());
        listView.setThanosEffectProvider(this::getThanosEffect);
        listView.setDateClickListener(this::showDateOptions);
        listView.setActionClickListener(this::showActionOptions);
        listView.setWatermarkClickListener(this::showWatermarkOptions);
        listView.setWatermark(watermark);
        SwipeToHideHelper.attach(listView, selectionHelper, new SwipeToHideHelper.Listener() {
            @Override
            public boolean canHide(ArrayList<MessageObject> messages) {
                return session.document.getIncludedCount() > messages.size();
            }

            @Override
            public void onHide(ArrayList<MessageObject> messages) {
                if (exporter.isBusy()) {
                    return;
                }
                listView.snapOnNextUpdate(messages);
                session.setExcluded(messages, true);
                BulletinFactory.of(container, resourcesProvider).createUndoBulletin(getString(R.string.MessageExportMessageHidden), () -> session.setExcluded(messages, false), null).show();
            }
        });
        card.addView(listView, index >= 0 ? index : 1, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        selectionHelper.setParentView(listView);
        if (listWidth > 0) {
            listView.rebindAll(session.document.blocks);
            listView.restoreScrollAnchor(scrollAnchor);
        }
    }

    private ThanosEffect getThanosEffect() {
        if (thanosEffect == null || thanosEffect.destroyed) {
            if (thanosEffect != null) {
                AndroidUtilities.removeFromParent(thanosEffect);
            }
            ThanosEffect[] current = new ThanosEffect[1];
            current[0] = thanosEffect = new ThanosEffect(getContext(), () -> {
                ThanosEffect effect = current[0];
                if (effect == null) {
                    return;
                }
                current[0] = null;
                if (thanosEffect == effect) {
                    thanosEffect = null;
                }
                AndroidUtilities.removeFromParent(effect);
            });
            card.addView(thanosEffect, card.indexOfChild(listView) + 1, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        }
        return thanosEffect;
    }

    private void ensureListBound() {
        if (listWidth > 0 && !session.document.blocks.isEmpty() && listView.needsRebind()) {
            listView.rebindAll(session.document.blocks);
            listView.requestLayout();
        }
        if (ensureAttempts++ < 10 && (listView.getChildCount() == 0 || !listView.isAttachedToWindow())) {
            listView.postOnAnimation(this::ensureListBound);
        }
    }

    private void updateHeader() {
        int count = session.document.getIncludedCount();
        subtitleView.setText(LocaleController.formatPluralString("messages", count));
        boolean toggleEnabled = canToggleDark();
        dayNightButton.setEnabled(toggleEnabled);
        dayNightButton.setAlpha(toggleEnabled ? 1f : 0.4f);
        dayNightButton.setContentDescription(getString(appearance.isDark() ? R.string.MessageExportLightMode : R.string.MessageExportDarkMode));
        shareButton.setEnabled(count > 0);
        shareButton.setAlpha(count > 0 ? 1f : 0.5f);
    }

    @Override
    public void onBeforeChange() {
        if (exporter.isBusy()) {
            exporter.cancel();
            shareButton.setLoading(false);
            listView.reclaimSharedLayouts();
        }
    }

    @Override
    public void onContentChanged() {
        updateHeader();
        if (listView.getProvider() != appearance.getProvider()) {
            createListView(getContext());
            appearance.getBackground().setOnUpdate(backgroundView::invalidate);
            presetsView.refresh();
        } else if (listWidth > 0) {
            listView.setBlocks(session.document.blocks);
        }
        backgroundView.invalidate();
    }

    private boolean canChangeAppearance() {
        return !exporter.isBusy() && themeAnimator == null;
    }

    private EmojiThemes getDarkTogglePreset() {
        EmojiThemes preset = appearance.getPreset() != null ? appearance.getPreset() : appearance.getChatAlias();
        return preset != null && preset.showAsDefaultStub ? null : preset;
    }

    private boolean canToggleDark() {
        EmojiThemes preset = getDarkTogglePreset();
        if (preset == null) {
            return false;
        }
        boolean dark = !appearance.isDark();
        return isBackToChat(preset, dark) || appearance.canRender(preset, dark);
    }

    private boolean isBackToChat(EmojiThemes preset, boolean dark) {
        return preset == appearance.getChatAlias() && dark == appearance.isChatDark();
    }

    private void toggleDark() {
        EmojiThemes preset = getDarkTogglePreset();
        if (!canChangeAppearance() || !canToggleDark()) {
            return;
        }
        Bitmap snapshot = snapshotCard();
        boolean dark = !appearance.isDark();
        if (isBackToChat(preset, dark)) {
            preset = null;
        }
        playDayNight(dark);
        session.setAppearance(preset, dark);
        animateThemeChange(snapshot, true, dark);
    }

    private void selectPreset(EmojiThemes preset) {
        if (!canChangeAppearance()) {
            return;
        }
        boolean dark = appearance.isDark();
        boolean resetDark = false;
        if (preset == null && dark != appearance.isChatDark()) {
            EmojiThemes alias = appearance.getChatAlias();
            if (alias != null && appearance.canRender(alias, dark)) {
                preset = alias;
            } else {
                dark = appearance.isChatDark();
                resetDark = true;
            }
        } else if (preset != null && !appearance.canRender(preset, dark) && appearance.canRender(preset, !dark)) {
            dark = !dark;
            resetDark = true;
        }
        if (appearance.getPreset() == preset && appearance.isDark() == dark) {
            return;
        }
        Bitmap snapshot = snapshotCard();
        if (resetDark) {
            playDayNight(dark);
        }
        session.setAppearance(preset, dark);
        presetsView.updateSelection();
        animateThemeChange(snapshot, false, false);
    }

    private void playDayNight(boolean dark) {
        dayNightDrawable.setCustomEndFrame(dark ? dayNightDrawable.getFramesCount() : 0);
        dayNightButton.playAnimation();
    }

    private void showShareOptions() {
        if (exporter.isBusy() || listWidth <= 0 || session.document.getIncludedCount() == 0) {
            return;
        }
        selectionHelper.clear(true);
        ItemOptions options = ItemOptions.makeOptions(container, resourcesProvider, shareButton, true);
        ItemOptions sendOptions = options.makeSwipeback()
            .add(R.drawable.ic_ab_back, getString(R.string.Back), options::closeSwipeback)
            .addGap()
            .add(R.drawable.msg_photos, getString(R.string.MessageExportAsPhoto), () -> share(ACTION_SEND_PHOTO))
            .add(R.drawable.msg_filehq, getString(R.string.MessageExportAsDocument), () -> share(ACTION_SEND_FILE))
            .add(R.drawable.msg_sticker, getString(R.string.MessageExportAsSticker), () -> share(ACTION_SEND_STICKER));
        options.add(R.drawable.msg_send, getString(R.string.MessageExportSendAs), () -> options.openSwipeback(sendOptions));
        if (options.getLastView() instanceof ActionBarMenuSubItem item) {
            item.setRightIcon(R.drawable.msg_arrowright);
        }
        options
            .add(R.drawable.msg_copy_photo, getString(R.string.CopyPhoto), () -> share(ACTION_COPY))
            .add(R.drawable.msg_download, getString(R.string.SaveToGallery), () -> share(ACTION_SAVE))
            .addGap()
            .addChecked(MessageExportWatermark.isEnabled(), R.drawable.extera_outline, getString(R.string.MessageExportWatermark), null);
        if (options.getLastView() instanceof ActionBarMenuSubItem item) {
            item.setOnClickListener(v -> {
                boolean enabled = !MessageExportWatermark.isEnabled();
                setWatermarkEnabled(enabled);
                item.setChecked(enabled);
            });
        }
        if (session.document.context.isPrivate()) {
            options.addChecked(ExteraConfig.getMessageExportAuthors(), R.drawable.msg_contacts, getString(R.string.MessageExportAuthors), null);
            if (options.getLastView() instanceof ActionBarMenuSubItem item) {
                item.setOnClickListener(v -> {
                    boolean enabled = !ExteraConfig.getMessageExportAuthors();
                    ExteraConfig.setMessageExportAuthors(enabled);
                    item.setChecked(enabled);
                    session.invalidateAll();
                });
            }
        }
        options.addChecked(ExteraConfig.getMessageExportTime(), R.drawable.msg_recent, getString(R.string.MessageExportTime), null);
        if (options.getLastView() instanceof ActionBarMenuSubItem item) {
            item.setOnClickListener(v -> {
                boolean enabled = !ExteraConfig.getMessageExportTime();
                ExteraConfig.setMessageExportTime(enabled);
                item.setChecked(enabled);
                session.invalidateAll();
            });
        }
        if (hasChannelPosts()) {
            options.addChecked(ExteraConfig.getMessageExportSource(), R.drawable.msg_channel, getString(R.string.MessageExportSource), null);
            if (options.getLastView() instanceof ActionBarMenuSubItem item) {
                item.setOnClickListener(v -> {
                    boolean enabled = !ExteraConfig.getMessageExportSource();
                    ExteraConfig.setMessageExportSource(enabled);
                    item.setChecked(enabled);
                    session.invalidateAll();
                });
            }
        }
        options
            .setGravity(Gravity.CENTER_HORIZONTAL)
            .setSwipebackCenterHorizontal(true);
        if (options.getLayout() instanceof ActionBarPopupWindow.ActionBarPopupWindowLayout layout) {
            layout.swipeBackGravityBottom = true;
        }
        showWithNavDim(options, null);
    }

    private void showWithNavDim(ItemOptions options, Runnable onDismiss) {
        int baseNavColor = getThemedColor(Theme.key_dialogBackground);
        listView.setTouchLocked(true);
        options
            .setDimAlpha(NAV_DIM_ALPHA)
            .setOnDismiss(() -> {
                listView.setTouchLocked(false);
                animateNavBarDim(baseNavColor, false);
                if (onDismiss != null) {
                    onDismiss.run();
                }
            })
            .show();
        animateNavBarDim(baseNavColor, true);
    }

    private boolean hasChannelPosts() {
        for (int i = 0; i < session.document.blocks.size(); i++) {
            MessageExportDocument.Block block = session.document.blocks.get(i);
            for (int j = 0; j < block.messages.size(); j++) {
                MessageObject message = block.messages.get(j);
                if (message.messageOwner != null && message.messageOwner.post && !message.isOutOwner()) {
                    return true;
                }
            }
        }
        return false;
    }

    private void showActionOptions(View view, MessageObject message) {
        if (exporter.isBusy()) {
            return;
        }
        selectionHelper.clear(true);
        ArrayList<MessageObject> messages = new ArrayList<>();
        messages.add(message);
        boolean canHide = session.document.getIncludedCount() > 1;
        ItemOptions options = ItemOptions.makeOptions(container, resourcesProvider, view)
            .add(R.drawable.msg_cancel, getString(R.string.MessageExportHideMessage), () -> {
                if (!canHide) {
                    return;
                }
                session.setExcluded(messages, true);
                BulletinFactory.of(container, resourcesProvider).createUndoBulletin(getString(R.string.MessageExportMessageHidden), () -> session.setExcluded(messages, false), null).show();
            })
            .setGravity(Gravity.CENTER_HORIZONTAL);
        if (!canHide && options.getLastView() != null) {
            options.getLastView().setEnabled(false);
            options.getLastView().setAlpha(0.5f);
        }
        showWithNavDim(options, null);
    }

    private void showDateOptions(View view, MessageObject dateObject) {
        if (exporter.isBusy()) {
            return;
        }
        selectionHelper.clear(true);
        showWithNavDim(ItemOptions.makeOptions(container, resourcesProvider, view)
            .add(R.drawable.msg_calendar2, getString(R.string.MessageExportHideDate), () -> {
                session.setDateHidden(dateObject, true);
                BulletinFactory.of(container, resourcesProvider).createUndoBulletin(getString(R.string.MessageExportDateHidden), () -> session.setDateHidden(dateObject, false), null).show();
            })
            .setGravity(Gravity.CENTER_HORIZONTAL), null);
    }

    private void setWatermarkEnabled(boolean enabled) {
        ExteraConfig.setMessageExportWatermark(enabled);
        listView.setPadding(0, dp(8), 0, MessageExportWatermark.getBottomInset());
        listView.invalidate();
    }

    private void showWatermarkOptions(RectF bounds) {
        if (exporter.isBusy()) {
            return;
        }
        selectionHelper.clear(true);
        Theme.ResourcesProvider provider = appearance.getProvider();
        View anchor = new View(getContext()) {
            @Override
            protected void onDraw(@NonNull Canvas canvas) {
                watermark.drawPill(canvas, 0, 0, provider);
            }
        };
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(Math.round(bounds.width()), Math.round(bounds.height()));
        params.leftMargin = Math.round(listView.getLeft() + bounds.left);
        params.topMargin = Math.round(listView.getTop() + bounds.top);
        card.addView(anchor, params);
        anchor.post(() -> showWithNavDim(ItemOptions.makeOptions(container, resourcesProvider, anchor)
            .add(R.drawable.extera_outline, getString(R.string.MessageExportHideWatermark), () -> {
                setWatermarkEnabled(false);
                BulletinFactory.of(container, resourcesProvider).createUndoBulletin(getString(R.string.MessageExportWatermarkHidden), () -> setWatermarkEnabled(true), null).show();
            })
            .setGravity(Gravity.CENTER_HORIZONTAL), () -> card.removeView(anchor)));
    }

    private void animateNavBarDim(int baseColor, boolean show) {
        if (navDimAnimator != null) {
            navDimAnimator.cancel();
        }
        int dimmed = ColorUtils.compositeColors(Theme.multAlpha(0xff000000, NAV_DIM_ALPHA / 255f), baseColor);
        int from = show ? baseColor : dimmed;
        int to = show ? dimmed : baseColor;
        navDimAnimator = ValueAnimator.ofFloat(0f, 1f);
        navDimAnimator.addUpdateListener(animation -> {
            fixNavigationBar(ColorUtils.blendARGB(from, to, (float) animation.getAnimatedValue()));
            container.invalidate();
        });
        if (!show) {
            navDimAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    fixNavigationBar();
                    container.invalidate();
                }
            });
        }
        navDimAnimator.setDuration(150);
        navDimAnimator.start();
    }

    private void share(int action) {
        if (exporter.isBusy() || listWidth <= 0 || session.document.getIncludedCount() == 0) {
            return;
        }
        if (action == ACTION_SAVE && !MessageExportDelivery.ensureSavePermission(fragment.getParentActivity())) {
            return;
        }
        shareButton.setLoading(true);
        exporter.export(listWidth, cardHeight, action == ACTION_SEND_STICKER, new MessageExporter.Callback() {
            @Override
            public void onExported(File file) {
                listView.reclaimSharedLayouts();
                if (action == ACTION_SEND_STICKER) {
                    MessageExportDelivery.createSticker(file, sticker -> {
                        shareButton.setLoading(false);
                        if (sticker == null) {
                            BulletinFactory.of(container, resourcesProvider).createErrorBulletin(getString(R.string.MessageExportFailed)).show();
                        } else if (isShowing()) {
                            MessageExportDelivery.shareInApp(fragment, sticker, false, true, dialogs -> onSent(dialogs, true));
                        }
                    });
                    return;
                }
                shareButton.setLoading(false);
                deliver(file, action);
            }

            @Override
            public void onFailed(boolean noSpace) {
                shareButton.setLoading(false);
                listView.reclaimSharedLayouts();
                BulletinFactory.of(container, resourcesProvider).createErrorBulletin(getString(noSpace ? R.string.MessageExportNoSpace : R.string.MessageExportFailed)).show();
            }
        });
    }

    private void deliver(File file, int action) {
        if (action == ACTION_SAVE) {
            MessageExportDelivery.saveToGallery(getContext(), file, () -> BulletinFactory.of(container, resourcesProvider).createDownloadBulletin(BulletinFactory.FileType.PHOTO, resourcesProvider).show());
        } else if (action == ACTION_COPY) {
            if (MessageExportDelivery.copyToClipboard(getContext(), file)) {
                if (AndroidUtilities.shouldShowClipboardToast()) {
                    BulletinFactory.of(container, resourcesProvider).createSimpleBulletin(R.raw.copy, getString(R.string.MessageExportCopied)).show();
                }
            } else {
                BulletinFactory.of(container, resourcesProvider).createErrorBulletin(getString(R.string.MessageExportFailed)).show();
            }
        } else {
            MessageExportDelivery.shareInApp(fragment, file, action == ACTION_SEND_PHOTO, false, dialogs -> onSent(dialogs, false));
        }
    }

    private void onSent(LongSparseArray<TLRPC.Dialog> dialogs, boolean sticker) {
        dismiss();
        if (dialogs == null || dialogs.size() == 0) {
            return;
        }
        CharSequence text;
        if (dialogs.size() == 1) {
            long dialogId = dialogs.keyAt(0);
            if (dialogId == 0 || dialogId == fragment.getUserConfig().getClientUserId()) {
                text = AndroidUtilities.replaceSingleTag(getString(sticker ? R.string.MessageExportStickerToSavedMessages : R.string.MessageExportToSavedMessages), -1, AndroidUtilities.REPLACING_TAG_TYPE_LINKBOLD, SavedMessagesController::openSavedMessages);
            } else {
                text = AndroidUtilities.replaceSingleTag(formatString(sticker ? R.string.MessageExportStickerToUser : R.string.MessageExportToUser, fragment.getMessagesController().getPeerName(dialogId, true)), -1, AndroidUtilities.REPLACING_TAG_TYPE_LINKBOLD, () -> fragment.presentFragment(ChatActivity.of(dialogId)));
            }
        } else {
            text = AndroidUtilities.replaceTags(formatString(sticker ? R.string.MessageExportStickerToChats : R.string.MessageExportToChats, LocaleController.formatPluralString("Chats", dialogs.size())));
        }
        showSentBulletin(fragment, text, 0);
    }

    private static void showSentBulletin(BaseFragment fragment, CharSequence text, int attempt) {
        if (fragment.getParentActivity() == null || attempt > 40) {
            return;
        }
        if (fragment.getVisibleDialog() != null || !BulletinFactory.canShowBulletin(fragment)) {
            AndroidUtilities.runOnUIThread(() -> showSentBulletin(fragment, text, attempt + 1), 50);
            return;
        }
        BulletinFactory.of(fragment).createSimpleBulletin(R.raw.forward, text, 2).show();
    }

    private Bitmap snapshotCard() {
        if (card.getWidth() <= 0 || card.getHeight() <= 0) {
            return null;
        }
        try {
            Bitmap bitmap = Bitmap.createBitmap(card.getWidth(), card.getHeight(), Bitmap.Config.ARGB_8888);
            card.draw(new Canvas(bitmap));
            return bitmap;
        } catch (Throwable e) {
            FileLog.e(e);
            return null;
        }
    }

    private void animateThemeChange(Bitmap snapshot, boolean reveal, boolean toDark) {
        if (snapshot == null) {
            return;
        }
        ThemeRevealView overlay = new ThemeRevealView(getContext());
        overlay.setScaleType(ImageView.ScaleType.MATRIX);
        overlay.setImageBitmap(snapshot);
        card.addView(overlay, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        float radius;
        if (reveal) {
            int[] cardPosition = new int[2];
            int[] buttonPosition = new int[2];
            card.getLocationInWindow(cardPosition);
            dayNightButton.getLocationInWindow(buttonPosition);
            float cx = buttonPosition[0] - cardPosition[0] + dayNightButton.getWidth() / 2f;
            float cy = buttonPosition[1] - cardPosition[1] + dayNightButton.getHeight() / 2f;
            radius = ThemeRevealView.coverRadius(cx, cy, card.getWidth(), card.getHeight());
            overlay.setMask(cx, cy, toDark);
            overlay.setRadius(toDark ? 0 : radius);
        } else {
            radius = 0;
        }
        themeAnimator = ValueAnimator.ofFloat(0f, 1f);
        themeAnimator.addUpdateListener(animation -> {
            float progress = (float) animation.getAnimatedValue();
            if (reveal) {
                overlay.setRadius(radius * (toDark ? progress : 1f - progress));
            } else {
                overlay.setAlpha(1f - progress);
            }
        });
        themeAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                card.removeView(overlay);
                snapshot.recycle();
                themeAnimator = null;
            }
        });
        themeAnimator.setDuration(reveal ? 400 : 250);
        themeAnimator.setInterpolator(reveal ? Easings.easeInOutQuad : CubicBezierInterpolator.DEFAULT);
        startWhenListReady(themeAnimator, 0);
    }

    private void startWhenListReady(ValueAnimator animator, int attempt) {
        if (animator != themeAnimator) {
            return;
        }
        if ((listView.getChildCount() == 0 || listView.isLayoutRequested()) && attempt < 12) {
            card.postOnAnimation(() -> startWhenListReady(animator, attempt + 1));
            return;
        }
        card.postOnAnimation(animator::start);
    }

    @Override
    public boolean dispatchTouchEvent(@NonNull MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            card.getLocationOnScreen(location);
            swipeAllowed = event.getRawY() < location[1] && !exporter.isExporting() && !selectionHelper.isInSelectionMode();
        }
        return super.dispatchTouchEvent(event);
    }

    @Override
    protected boolean canDismissWithSwipe() {
        return swipeAllowed;
    }

    @Override
    public void dismiss() {
        if (themeAnimator != null) {
            themeAnimator.cancel();
        }
        exporter.cancel();
        if (thanosEffect != null) {
            thanosEffect.kill();
        }
        selectionHelper.clear(true);
        super.dismiss();
    }
}
