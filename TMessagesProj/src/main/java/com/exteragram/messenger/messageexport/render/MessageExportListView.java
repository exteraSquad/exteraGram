/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.messageexport.render;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.GridLayoutManagerFixed;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.exteragram.messenger.messageexport.model.MessageExportChatContext;
import com.exteragram.messenger.messageexport.model.MessageExportDocument;
import com.exteragram.messenger.messageexport.model.MessageExportSession;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LiteMode;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ChatActionCell;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.Cells.TextSelectionHelper;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.AvatarsDrawable;
import org.telegram.ui.Components.RLottieDrawable;
import org.telegram.ui.Components.Reactions.ReactionsLayoutInBubble;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.spoilers.SpoilerEffect;
import org.telegram.ui.Components.ThanosEffect;
import org.telegram.ui.recyclerview.ChatListItemAnimator;

import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;

@SuppressLint("ViewConstructor")
public final class MessageExportListView extends RecyclerListView {

    public interface TouchInterceptor {
        boolean onInterceptTouchEvent(MotionEvent e);

        boolean onTouchEvent(MotionEvent e);
    }

    private static final int VIEW_TYPE_MESSAGE = 0;
    private static final int VIEW_TYPE_ACTION = 1;

    private static final class Item {
        final MessageObject message;
        final MessageExportDocument.Block block;

        Item(MessageObject message, MessageExportDocument.Block block) {
            this.message = message;
            this.block = block;
        }
    }

    private final MessageExportSession session;
    private final Theme.ResourcesProvider provider;
    private final boolean interactive;
    private final TextSelectionHelper.ChatListTextSelectionHelper selectionHelper;
    private final ArrayList<Item> items = new ArrayList<>();
    private final ArrayList<MessageObject.GroupedMessages> drawingGroups = new ArrayList<>(2);
    private final IdentityHashMap<MessageObject, Boolean> pendingSnaps = new IdentityHashMap<>();
    private final IdentityHashMap<ChatMessageCell, Rect> ownBackgroundBounds = new IdentityHashMap<>();
    private final ChatListItemAnimator itemAnimator;

    private List<MessageExportDocument.Block> blocks;
    private List<MessageExportDocument.Block> pendingBlocks;
    private int boundRevision = -1;
    private int[] pendingAnchor;
    private boolean drawingToBitmap;
    private TouchInterceptor touchInterceptor;
    private Utilities.Callback0Return<ThanosEffect> thanosEffectProvider;
    private Utilities.Callback2<View, MessageObject> dateClickListener;
    private Utilities.Callback2<View, MessageObject> actionClickListener;
    private MessageExportWatermark watermark;
    private float serviceOrigin;
    private boolean touchLocked;
    private Utilities.Callback<RectF> watermarkClickListener;
    private boolean watermarkVisible;
    private boolean watermarkPressed;
    private float watermarkDownX;
    private float watermarkDownY;
    private final ArrayList<AvatarHit> avatarHits = new ArrayList<>();
    private final IdentityHashMap<ImageReceiver, BlurredAvatar> blurredAvatars = new IdentityHashMap<>();
    private final IdentityHashMap<ImageReceiver, SpoilerEffect> avatarSpoilers = new IdentityHashMap<>();
    private final Paint avatarBitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Path avatarClipPath = new Path();
    private final float[] avatarRadii = new float[8];
    private long pressedAvatarPeer;
    private float avatarDownX;
    private float avatarDownY;

    private static final class AvatarHit {
        final RectF rect = new RectF();
        long peerId;
    }

    private static final class BlurredAvatar {
        Object source;
        Bitmap bitmap;
    }

    public MessageExportListView(Context context, MessageExportSession session, Theme.ResourcesProvider provider, TextSelectionHelper.ChatListTextSelectionHelper selectionHelper) {
        super(context, provider);
        this.session = session;
        this.provider = provider;
        this.selectionHelper = selectionHelper;
        this.interactive = selectionHelper != null;
        setLayoutAnimation(null);
        setOverScrollMode(OVER_SCROLL_NEVER);
        setVerticalScrollBarEnabled(false);
        setClipChildren(false);
        setClipToPadding(false);
        setSelectorDrawableColor(0);
        setDrawSelection(false);
        setNestedScrollingEnabled(false);
        if (interactive) {
            itemAnimator = new ChatListItemAnimator(null, this, provider);
            itemAnimator.setShouldAnimateEnterFromBottom(false);
            itemAnimator.setOnSnapMessage(this::supportsThanos, () -> thanosEffectProvider != null ? thanosEffectProvider.run() : null);
            setItemAnimator(itemAnimator);
        } else {
            itemAnimator = null;
            setItemAnimator(null);
        }

        GridLayoutManagerFixed layoutManager = new GridLayoutManagerFixed(context, 1000, LinearLayoutManager.VERTICAL, true) {
            @Override
            public boolean supportsPredictiveItemAnimations() {
                return interactive;
            }

            @Override
            public boolean canScrollVertically() {
                return interactive;
            }

            @Override
            public boolean shouldLayoutChildFromOpositeSide(View child) {
                return child instanceof ChatMessageCell && !((ChatMessageCell) child).getMessageObject().isOutOwner();
            }

            @Override
            protected boolean hasSiblingChild(int position) {
                Item item = itemAt(position);
                MessageObject.GroupedMessages group = item != null ? item.block.group : null;
                if (group == null) {
                    return false;
                }
                MessageObject.GroupedMessagePosition pos = group.getPosition(item.message);
                if (pos == null || pos.minX == pos.maxX || pos.minY != pos.maxY || pos.minY == 0) {
                    return false;
                }
                for (int a = 0, count = group.posArray.size(); a < count; a++) {
                    MessageObject.GroupedMessagePosition p = group.posArray.get(a);
                    if (p != pos && p.minY <= pos.minY && p.maxY >= pos.minY) {
                        return true;
                    }
                }
                return false;
            }
        };
        layoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                Item item = itemAt(position);
                if (item != null && item.block.group != null) {
                    MessageObject.GroupedMessagePosition groupedPosition = item.block.group.getPosition(item.message);
                    if (groupedPosition != null) {
                        return groupedPosition.spanSize;
                    }
                }
                return 1000;
            }
        });
        setLayoutManager(layoutManager);
        addItemDecoration(new ItemDecoration() {
            @Override
            public void getItemOffsets(@NonNull android.graphics.Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull State state) {
                outRect.bottom = 0;
                if (!(view instanceof ChatMessageCell cell)) {
                    return;
                }
                MessageObject.GroupedMessages group = cell.getCurrentMessagesGroup();
                MessageObject.GroupedMessagePosition position = cell.getCurrentPosition();
                if (group == null || position == null || position.siblingHeights == null) {
                    return;
                }
                float maxHeight = Math.max(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) * 0.5f;
                int h = cell.getExtraInsetHeight();
                for (int a = 0; a < position.siblingHeights.length; a++) {
                    h += (int) Math.ceil(maxHeight * position.siblingHeights[a]);
                }
                h += (position.maxY - position.minY) * Math.round(7 * AndroidUtilities.density);
                for (int a = 0, count = group.posArray.size(); a < count; a++) {
                    MessageObject.GroupedMessagePosition pos = group.posArray.get(a);
                    if (pos.minY != position.minY || pos.minX == position.minX && pos.maxX == position.maxX && pos.minY == position.minY && pos.maxY == position.maxY) {
                        continue;
                    }
                    if (pos.minY == position.minY) {
                        h -= (int) Math.ceil(maxHeight * pos.ph) - dp(4);
                        break;
                    }
                }
                outRect.bottom = -h;
            }
        });
        Adapter adapter = new Adapter();
        adapter.setHasStableIds(true);
        setAdapter(adapter);
        if (interactive) {
            setOnItemClickListener((view, position) -> {
                Item item = itemAt(position);
                if (item != null && item.block.type == MessageExportDocument.BLOCK_DATE && dateClickListener != null) {
                    dateClickListener.run(view, item.message);
                } else if (item != null && item.block.type == MessageExportDocument.BLOCK_ACTION && actionClickListener != null) {
                    actionClickListener.run(view, item.message);
                }
            });
        }
    }

    public void setWatermark(MessageExportWatermark watermark) {
        this.watermark = watermark;
        invalidate();
    }

    public void setTouchLocked(boolean locked) {
        if (touchLocked == locked) {
            return;
        }
        touchLocked = locked;
        if (locked) {
            stopScroll();
            long now = SystemClock.uptimeMillis();
            MotionEvent cancel = MotionEvent.obtain(now, now, MotionEvent.ACTION_CANCEL, 0, 0, 0);
            super.dispatchTouchEvent(cancel);
            cancel.recycle();
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (touchLocked) {
            return true;
        }
        if (interactive && checkAvatarTap(ev)) {
            MotionEvent cancel = MotionEvent.obtain(ev);
            cancel.setAction(MotionEvent.ACTION_CANCEL);
            super.dispatchTouchEvent(cancel);
            cancel.recycle();
            return true;
        }
        return super.dispatchTouchEvent(ev);
    }

    private boolean checkAvatarTap(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                pressedAvatarPeer = findAvatarPeer(e.getX(), e.getY());
                avatarDownX = e.getX();
                avatarDownY = e.getY();
                return false;
            case MotionEvent.ACTION_MOVE:
                if (pressedAvatarPeer != 0 && Math.hypot(e.getX() - avatarDownX, e.getY() - avatarDownY) > AndroidUtilities.touchSlop) {
                    pressedAvatarPeer = 0;
                }
                return false;
            case MotionEvent.ACTION_UP:
                long peer = pressedAvatarPeer;
                pressedAvatarPeer = 0;
                if (peer != 0 && findAvatarPeer(e.getX(), e.getY()) == peer) {
                    session.toggleAvatarHidden(peer);
                    invalidate();
                    return true;
                }
                return false;
            default:
                pressedAvatarPeer = 0;
                return false;
        }
    }

    private long findAvatarPeer(float x, float y) {
        for (int i = avatarHits.size() - 1; i >= 0; i--) {
            AvatarHit hit = avatarHits.get(i);
            if (hit.rect.contains(x, y)) {
                return hit.peerId;
            }
        }
        return 0;
    }

    private static long avatarPeer(ChatMessageCell cell) {
        if (cell.getCurrentUser() != null) {
            return cell.getCurrentUser().id;
        }
        if (cell.getCurrentChat() != null) {
            return -cell.getCurrentChat().id;
        }
        return 0;
    }

    public void setServiceOrigin(float origin) {
        serviceOrigin = origin;
    }

    public void setWatermarkClickListener(Utilities.Callback<RectF> listener) {
        watermarkClickListener = listener;
    }

    public void setActionClickListener(Utilities.Callback2<View, MessageObject> listener) {
        actionClickListener = listener;
    }

    public void setDateClickListener(Utilities.Callback2<View, MessageObject> listener) {
        dateClickListener = listener;
    }

    public void setTouchInterceptor(TouchInterceptor interceptor) {
        touchInterceptor = interceptor;
    }

    public void setThanosEffectProvider(Utilities.Callback0Return<ThanosEffect> provider) {
        thanosEffectProvider = provider;
    }

    public void snapOnNextUpdate(Collection<MessageObject> messages) {
        for (MessageObject message : messages) {
            pendingSnaps.put(message, Boolean.TRUE);
        }
    }

    private boolean supportsThanos() {
        return thanosEffectProvider != null && ThanosEffect.supports() && LiteMode.isEnabled(LiteMode.FLAG_CHAT_THANOS);
    }

    private void prepareSnaps() {
        if (pendingSnaps.isEmpty()) {
            return;
        }
        IdentityHashMap<MessageObject.GroupedMessages, ArrayList<ChatMessageCell>> albums = new IdentityHashMap<>();
        for (int i = 0; i < getChildCount(); i++) {
            if (!(getChildAt(i) instanceof ChatMessageCell cell) || !pendingSnaps.containsKey(cell.getMessageObject())) {
                continue;
            }
            MessageObject.GroupedMessages group = cell.getCurrentMessagesGroup();
            if (group == null) {
                itemAnimator.prepareThanos(getChildViewHolder(cell));
            } else {
                ArrayList<ChatMessageCell> cells = albums.get(group);
                if (cells == null) {
                    albums.put(group, cells = new ArrayList<>());
                }
                cells.add(cell);
            }
        }
        pendingSnaps.clear();
        if (!albums.isEmpty() && supportsThanos()) {
            ThanosEffect effect = thanosEffectProvider.run();
            if (effect != null) {
                for (ArrayList<ChatMessageCell> cells : albums.values()) {
                    snapAlbum(cells, effect);
                }
            }
        }
    }

    private void snapAlbum(ArrayList<ChatMessageCell> cells, ThanosEffect effect) {
        Rect background = new Rect();
        Rect area = new Rect();
        boolean pinnedTop = false;
        boolean pinnedBottom = false;
        for (int i = 0; i < cells.size(); i++) {
            ChatMessageCell cell = cells.get(i);
            int x = (int) cell.getX();
            int y = (int) cell.getY();
            int top = y + cell.getPaddingTop() + cell.getBackgroundDrawableTop();
            int bottom = y + cell.getPaddingTop() + cell.getBackgroundDrawableBottom();
            MessageObject.GroupedMessagePosition position = cell.getCurrentPosition();
            if (position != null && (position.flags & MessageObject.POSITION_FLAG_TOP) == 0) {
                top -= dp(10);
            }
            if (position != null && (position.flags & MessageObject.POSITION_FLAG_BOTTOM) == 0) {
                bottom += dp(10);
            }
            background.union(x + cell.getBackgroundDrawableLeft(), top, x + cell.getBackgroundDrawableRight(), bottom);
            area.union(x, y, x + cell.getWidth(), y + cell.getHeight());
            if (position == null || (position.flags & MessageObject.POSITION_FLAG_TOP) != 0) {
                pinnedTop = cell.isPinnedTop();
            }
            if (position == null || (position.flags & MessageObject.POSITION_FLAG_BOTTOM) != 0) {
                pinnedBottom = cell.isPinnedBottom();
            }
        }
        area.union(background);
        if (area.isEmpty()) {
            return;
        }
        Bitmap bitmap;
        try {
            bitmap = Bitmap.createBitmap(area.width(), area.height(), Bitmap.Config.ARGB_8888);
        } catch (Throwable e) {
            return;
        }
        Canvas canvas = new Canvas(bitmap);
        canvas.translate(-area.left, -area.top);
        cells.get(0).drawBackground(canvas, background.left, background.top, background.right, background.bottom, pinnedTop, pinnedBottom, false, 0);
        for (int i = 0; i < cells.size(); i++) {
            ChatMessageCell cell = cells.get(i);
            canvas.save();
            canvas.translate(cell.getX(), cell.getY());
            cell.drawingToBitmap = true;
            cell.draw(canvas);
            cell.drawingToBitmap = false;
            canvas.restore();
            cell.setAlpha(0f);
        }
        canvas.setBitmap(null);
        View snapshot = new View(getContext()) {
            @Override
            protected void onDraw(@NonNull Canvas canvas) {
                if (!bitmap.isRecycled()) {
                    canvas.drawBitmap(bitmap, 0, 0, null);
                }
            }
        };
        snapshot.layout(area.left, area.top, area.right, area.bottom);
        effect.animate(snapshot, bitmap::recycle);
    }

    public void reclaimSharedLayouts() {
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child instanceof ChatMessageCell) {
                MessageObject message = ((ChatMessageCell) child).getMessageObject();
                if (message != null && message.richLayout != null) {
                    message.richLayout.attach(child);
                }
                child.invalidate();
            }
        }
    }

    public Theme.ResourcesProvider getProvider() {
        return provider;
    }

    List<MessageExportDocument.Block> getBlocks() {
        return blocks;
    }

    public void setBlocks(List<MessageExportDocument.Block> newBlocks) {
        if (interactive && !isAttachedToWindow()) {
            pendingBlocks = newBlocks;
            return;
        }
        pendingBlocks = null;
        int revision = session.document.getRevision();
        if (sameBlocks(blocks, newBlocks) && boundRevision == revision) {
            return;
        }
        ArrayList<Item> newItems = new ArrayList<>();
        for (int b = 0; b < newBlocks.size(); b++) {
            MessageExportDocument.Block block = newBlocks.get(b);
            for (int i = 0; i < block.messages.size(); i++) {
                newItems.add(new Item(block.messages.get(i), block));
            }
        }
        boolean animate = interactive && blocks != null && getAdapter() != null && isAttachedToWindow();
        blocks = newBlocks;
        boundRevision = revision;
        ArrayList<MessageObject.GroupedMessages> changedGroups = interactive ? session.document.consumeChangedGroups() : new ArrayList<>();
        if (!animate) {
            pendingSnaps.clear();
            items.clear();
            items.addAll(newItems);
            getAdapter().notifyDataSetChanged();
            requestLayout();
            return;
        }
        ArrayList<Item> oldItems = new ArrayList<>(items);
        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return oldItems.size();
            }

            @Override
            public int getNewListSize() {
                return newItems.size();
            }

            @Override
            public boolean areItemsTheSame(int oldPosition, int newPosition) {
                Item a = oldItems.get(oldItems.size() - 1 - oldPosition);
                Item b = newItems.get(newItems.size() - 1 - newPosition);
                return a.message == b.message;
            }

            @Override
            public boolean areContentsTheSame(int oldPosition, int newPosition) {
                Item a = oldItems.get(oldItems.size() - 1 - oldPosition);
                Item b = newItems.get(newItems.size() - 1 - newPosition);
                return a.message == b.message && a.block.group == null && b.block.group == null && a.block.pinnedTop == b.block.pinnedTop && a.block.pinnedBottom == b.block.pinnedBottom && !a.message.forceUpdate;
            }
        }, false);
        for (int i = 0; i < changedGroups.size(); i++) {
            MessageObject.GroupedMessages group = changedGroups.get(i);
            if (group.messages.size() == 1) {
                itemAnimator.groupWillTransformToSingleMessage(group);
            } else {
                itemAnimator.groupWillChanged(group);
            }
        }
        prepareSnaps();
        items.clear();
        items.addAll(newItems);
        diff.dispatchUpdatesTo(getAdapter());
    }

    private static boolean sameBlocks(List<MessageExportDocument.Block> a, List<MessageExportDocument.Block> b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null || a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            if (a.get(i) != b.get(i)) {
                return false;
            }
        }
        return true;
    }

    public int[] getScrollAnchor() {
        LayoutManager layoutManager = getLayoutManager();
        if (layoutManager == null) {
            return null;
        }
        View anchor = null;
        int anchorPosition = NO_POSITION;
        boolean anchorGrouped = true;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            int position = getChildAdapterPosition(child);
            if (position == NO_POSITION) {
                continue;
            }
            boolean grouped = child instanceof ChatMessageCell && ((ChatMessageCell) child).getCurrentMessagesGroup() != null;
            if (anchor == null || anchorGrouped && !grouped || anchorGrouped == grouped && position < anchorPosition) {
                anchor = child;
                anchorPosition = position;
                anchorGrouped = grouped;
            }
        }
        if (anchor == null) {
            return null;
        }
        return new int[]{anchorPosition, getHeight() - getPaddingBottom() - layoutManager.getDecoratedBottom(anchor)};
    }

    public void restoreScrollAnchor(int[] anchor) {
        if (anchor != null && anchor[0] < items.size() && getLayoutManager() instanceof LinearLayoutManager layoutManager) {
            pendingAnchor = anchor;
            layoutManager.scrollToPositionWithOffset(anchor[0], anchor[1]);
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        if (pendingAnchor == null || getChildCount() == 0) {
            return;
        }
        int[] anchor = pendingAnchor;
        pendingAnchor = null;
        LayoutManager layoutManager = getLayoutManager();
        View view = layoutManager != null ? layoutManager.findViewByPosition(anchor[0]) : null;
        if (view != null) {
            int delta = layoutManager.getDecoratedBottom(view) - (getHeight() - getPaddingBottom() - anchor[1]);
            if (delta != 0) {
                scrollBy(0, delta);
            }
        }
    }

    public boolean needsRebind() {
        return isAttachedToWindow() && (items.isEmpty() || getChildCount() == 0) && getMeasuredHeight() > 0;
    }

    public void rebindAll(List<MessageExportDocument.Block> newBlocks) {
        blocks = null;
        setBlocks(newBlocks);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (pendingBlocks != null) {
            List<MessageExportDocument.Block> pending = pendingBlocks;
            post(() -> {
                if (pendingBlocks == pending) {
                    blocks = null;
                    setBlocks(pending);
                }
            });
        }
    }

    void setDrawingToBitmap(boolean value) {
        if (drawingToBitmap == value) {
            return;
        }
        drawingToBitmap = value;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child instanceof ChatMessageCell) {
                ((ChatMessageCell) child).drawingToBitmap = value;
            }
        }
    }

    void measureAndLayout(int width) {
        if (getMeasuredWidth() == width && !isLayoutRequested()) {
            return;
        }
        measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        layout(0, 0, getMeasuredWidth(), getMeasuredHeight());
    }

    boolean isContentReady() {
        if (getChildCount() != items.size()) {
            return false;
        }
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child instanceof ChatActionCell actionCell && (isLoading(actionCell.getPhotoImage()) || !hasFirstFrame(actionCell.getPhotoImage()))) {
                return false;
            }
            if (child instanceof ChatMessageCell cell) {
                if (cell.getMessageObject() == null || isLoading(cell.getPhotoImage()) || isLoading(cell.getAvatarImage())) {
                    return false;
                }
                if (!hasFirstFrame(cell.getPhotoImage()) || !cell.isTranscribeIconReady()) {
                    return false;
                }
                if (cell.animatedEmojiStack != null) {
                    for (int j = 0; j < cell.animatedEmojiStack.holders.size(); j++) {
                        AnimatedEmojiDrawable emoji = cell.animatedEmojiStack.holders.get(j).drawable;
                        if (emoji != null && !isEmojiReady(emoji)) {
                            return false;
                        }
                    }
                }
                PreviewTapHandler.ensureSpoilerBlur(cell);
                if (PreviewTapHandler.isSpoilerBlurMissing(cell)) {
                    return false;
                }
            }
        }
        return true;
    }

    private void prepareImage(ImageReceiver receiver) {
        receiver.setLayerNum(Integer.MAX_VALUE);
        if (!interactive) {
            receiver.setUniqKeyPrefix("export_");
            receiver.setAllowStartLottieAnimation(false);
            receiver.setCrossfadeAlpha((byte) 0);
        }
    }

    private static boolean isEmojiReady(AnimatedEmojiDrawable emoji) {
        ImageReceiver receiver = emoji.getImageReceiver();
        if (emoji.getDocument() == null || receiver == null || !receiver.hasNotThumb() || !hasFirstFrame(receiver)) {
            return false;
        }
        receiver.setCurrentAlpha(1f);
        return true;
    }

    private static boolean hasFirstFrame(ImageReceiver receiver) {
        RLottieDrawable lottie = receiver != null ? receiver.getLottieAnimation() : null;
        if (lottie != null && !lottie.hasBitmap()) {
            lottie.setAllowDecodeSingleFrame(true);
            return false;
        }
        return true;
    }

    private static boolean isLoading(ImageReceiver receiver) {
        return receiver != null && receiver.hasImageSet() && !receiver.hasNotThumbOrOnlyStaticThumb();
    }

    private Item itemAt(int adapterPosition) {
        int index = items.size() - 1 - adapterPosition;
        return index >= 0 && index < items.size() ? items.get(index) : null;
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        if (interactive) {
            super.onMeasure(widthSpec, heightSpec);
        } else {
            super.onMeasure(widthSpec, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        }
    }

    @Override
    public void onScrolled(int dx, int dy) {
        super.onScrolled(dx, dy);
        if (selectionHelper != null) {
            selectionHelper.onParentScrolled();
        }
    }

    @Override
    public void onScrollStateChanged(int state) {
        if (state == SCROLL_STATE_IDLE && selectionHelper != null) {
            selectionHelper.stopScrolling();
        }
        super.onScrollStateChanged(state);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent e) {
        if (!interactive) {
            return false;
        }
        if (touchInterceptor != null && touchInterceptor.onInterceptTouchEvent(e)) {
            return true;
        }
        return super.onInterceptTouchEvent(e);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (!interactive) {
            return false;
        }
        if (touchInterceptor != null && touchInterceptor.onTouchEvent(e)) {
            return true;
        }
        checkWatermarkTap(e);
        return super.onTouchEvent(e);
    }

    private void checkWatermarkTap(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                watermarkPressed = watermarkClickListener != null && watermarkVisible && watermark.getBounds().contains(e.getX(), e.getY());
                watermarkDownX = e.getX();
                watermarkDownY = e.getY();
                break;
            case MotionEvent.ACTION_MOVE:
                if (watermarkPressed && Math.hypot(e.getX() - watermarkDownX, e.getY() - watermarkDownY) > AndroidUtilities.touchSlop) {
                    watermarkPressed = false;
                }
                break;
            case MotionEvent.ACTION_UP:
                if (watermarkPressed) {
                    watermarkPressed = false;
                    watermarkClickListener.run(new RectF(watermark.getBounds()));
                }
                break;
            case MotionEvent.ACTION_CANCEL:
                watermarkPressed = false;
                break;
        }
    }

    @Override
    protected void dispatchDraw(@NonNull Canvas canvas) {
        avatarHits.clear();
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            float top = serviceOrigin + child.getY();
            if (child instanceof ChatMessageCell cell) {
                cell.setParentViewSize(getMeasuredWidth() + MessageExportChatContext.getWidthCompensation(cell.getMessageObject()), Math.max(getMeasuredHeight(), AndroidUtilities.displaySize.y));
                cell.viewTop = top;
            } else if (child instanceof ChatActionCell actionCell) {
                actionCell.setVisiblePart(top, getMeasuredHeight());
            }
        }
        drawGroupBackgrounds(canvas);
        super.dispatchDraw(canvas);
        watermarkVisible = watermark != null && MessageExportWatermark.isEnabled() && findViewHolderForAdapterPosition(0) != null;
        if (watermarkVisible) {
            float contentBottom = 0;
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                float bottom = child.getY() + child.getHeight();
                if (child instanceof ChatMessageCell cell) {
                    bottom += cell.getDeltaBottom();
                }
                contentBottom = Math.max(contentBottom, bottom);
            }
            watermark.draw(canvas, getWidth(), contentBottom, provider);
        }
    }

    private void drawGroupBackgrounds(Canvas canvas) {
        for (int pass = 0; pass < 2; pass++) {
            drawingGroups.clear();
            for (int i = 0, count = getChildCount(); i < count; i++) {
                View child = getChildAt(i);
                if (!(child instanceof ChatMessageCell cell)) {
                    continue;
                }
                MessageObject.GroupedMessages group = cell.getCurrentMessagesGroup();
                if (group == null || cell.getCurrentPosition() == null || cell.willRemovedAfterAnimation()) {
                    continue;
                }
                if (pass == 0 ? group.messages.size() == 1 || cell.getMessageObject().deleted : !group.transitionParams.drawBackgroundForDeletedItems || !cell.getMessageObject().deleted) {
                    continue;
                }
                if (!drawingGroups.contains(group)) {
                    group.transitionParams.left = 0;
                    group.transitionParams.top = 0;
                    group.transitionParams.right = 0;
                    group.transitionParams.bottom = 0;
                    group.transitionParams.pinnedBotton = false;
                    group.transitionParams.pinnedTop = false;
                    group.transitionParams.cell = cell;
                    drawingGroups.add(group);
                }
                if ((cell.getCurrentPosition().flags & MessageObject.POSITION_FLAG_TOP) != 0) {
                    group.transitionParams.pinnedTop = cell.isPinnedTop();
                }
                if ((cell.getCurrentPosition().flags & MessageObject.POSITION_FLAG_BOTTOM) != 0) {
                    group.transitionParams.pinnedBotton = cell.isPinnedBottom();
                }
                int left = cell.getLeft() + cell.getBackgroundDrawableLeft();
                int right = cell.getLeft() + cell.getBackgroundDrawableRight();
                int top = cell.getTop() + cell.getPaddingTop() + cell.getBackgroundDrawableTop();
                int bottom = cell.getTop() + cell.getPaddingTop() + cell.getBackgroundDrawableBottom();
                if ((cell.getCurrentPosition().flags & MessageObject.POSITION_FLAG_TOP) == 0) {
                    top -= dp(10);
                }
                if ((cell.getCurrentPosition().flags & MessageObject.POSITION_FLAG_BOTTOM) == 0) {
                    bottom += dp(10);
                }
                if (group.transitionParams.top == 0 || top < group.transitionParams.top) {
                    group.transitionParams.top = top;
                }
                if (group.transitionParams.bottom == 0 || bottom > group.transitionParams.bottom) {
                    group.transitionParams.bottom = bottom;
                }
                if (group.transitionParams.left == 0 || left < group.transitionParams.left) {
                    group.transitionParams.left = left;
                }
                if (group.transitionParams.right == 0 || right > group.transitionParams.right) {
                    group.transitionParams.right = right;
                }
            }
            for (int i = 0; i < drawingGroups.size(); i++) {
                MessageObject.GroupedMessages group = drawingGroups.get(i);
                ChatMessageCell cell = group.transitionParams.cell;
                float x = cell.getNonAnimationTranslationX(true);
                float l = group.transitionParams.left + x + group.transitionParams.offsetLeft;
                float t = group.transitionParams.top + group.transitionParams.offsetTop;
                float r = group.transitionParams.right + x + group.transitionParams.offsetRight;
                float b = group.transitionParams.bottom + group.transitionParams.offsetBottom;
                if (!group.transitionParams.backgroundChangeBounds) {
                    t += cell.getTranslationY();
                    b += cell.getTranslationY();
                }
                boolean useScale = cell.getScaleX() != 1f || cell.getScaleY() != 1f;
                if (useScale) {
                    canvas.save();
                    canvas.scale(cell.getScaleX(), cell.getScaleY(), l + (r - l) / 2, t + (b - t) / 2);
                }
                cell.drawBackground(canvas, (int) l, (int) t, (int) r, (int) b, group.transitionParams.pinnedTop, group.transitionParams.pinnedBotton, false, 0);
                if (useScale) {
                    canvas.restore();
                }
                group.transitionParams.cell = null;
                group.transitionParams.drawCaptionLayout = group.hasCaption;
            }
        }
        drawingGroups.clear();
    }

    @Override
    public boolean drawChild(@NonNull Canvas canvas, View child, long drawingTime) {
        if (!(child instanceof ChatMessageCell cell)) {
            return super.drawChild(canvas, child, drawingTime);
        }
        boolean result = super.drawChild(canvas, child, drawingTime);
        Rect ownBounds = ownBackgroundBounds.get(cell);
        if (ownBounds != null && cell.currentBackgroundDrawable != null) {
            cell.currentBackgroundDrawable.setBounds(ownBounds);
        }
        canvas.save();
        canvas.translate(cell.getX(), cell.getY());
        canvas.save();
        canvas.scale(cell.getScaleX(), cell.getScaleY(), cell.getPivotX(), cell.getPivotY());
        installReactionAvatarDrawer(cell);
        drawCellLayers(canvas, cell, itemAnimator != null && itemAnimator.isRunning());
        if (ownBounds != null) {
            cell.getTransitionParams().lastDrawingBackgroundRect.set(ownBounds);
        }
        canvas.restore();
        cell.getTransitionParams().recordDrawingStatePreview();
        canvas.restore();
        drawAvatar(canvas, cell);
        return result;
    }

    private static boolean needsParentTextLayout(MessageObject message) {
        return message != null && message.type != MessageObject.TYPE_EMOJIS;
    }

    private static void drawCellLayers(Canvas canvas, ChatMessageCell cell, boolean animating) {
        cell.drawContent(canvas, true);
        cell.layoutTextXY(needsParentTextLayout(cell.getMessageObject()));
        cell.setupTextColors();
        cell.drawMessageText(canvas);
        MessageObject.GroupedMessagePosition position = cell.getCurrentPosition();
        MessageObject.GroupedMessages group = cell.getCurrentMessagesGroup();
        boolean animatingBounds = animating && cell.getTransitionParams().animateBackgroundBoundsInner;
        if (group == null || position != null && ((position.flags & cell.captionFlag()) != 0 && (position.flags & MessageObject.POSITION_FLAG_LEFT) != 0 || group.isDocuments) || animatingBounds) {
            cell.drawCaptionLayout(canvas, false, cell.getAlpha());
            cell.drawReactionsLayout(canvas, cell.getAlpha(), null);
            cell.drawCommentLayout(canvas, cell.getAlpha());
        }
        if (animatingBounds || group != null && (position == null || position.minX == 0 && position.minY == 0)) {
            cell.drawNamesLayout(canvas, cell.getAlpha());
        }
        if (position != null && position.last || animatingBounds) {
            cell.drawTime(canvas, cell.getAlpha(), true);
        }
        cell.drawOverlays(canvas);
        if (group == null && cell.shouldDrawTimeOnMedia()) {
            cell.drawTime(canvas, cell.getAlpha(), true);
        }
    }

    private void drawHiddenAvatar(Canvas canvas, ImageReceiver avatar, float alpha) {
        int[] radius = avatar.getRoundRadius();
        for (int i = 0; i < 4; i++) {
            avatarRadii[i * 2] = avatarRadii[i * 2 + 1] = radius[i];
        }
        RectF rect = AndroidUtilities.rectTmp;
        rect.set(avatar.getImageX(), avatar.getImageY(), avatar.getImageX2(), avatar.getImageY2());
        avatarClipPath.rewind();
        avatarClipPath.addRoundRect(rect, avatarRadii, Path.Direction.CW);
        canvas.save();
        canvas.clipPath(avatarClipPath);
        Bitmap blurred = obtainBlurredAvatar(avatar);
        if (blurred != null) {
            avatarBitmapPaint.setAlpha((int) (255 * alpha));
            canvas.drawBitmap(blurred, null, rect, avatarBitmapPaint);
        }
        SpoilerEffect spoiler = avatarSpoilers.get(avatar);
        if (spoiler == null) {
            spoiler = new SpoilerEffect();
            avatarSpoilers.put(avatar, spoiler);
        }
        spoiler.setColor(Color.argb((int) (255 * 0.325f * alpha), 255, 255, 255));
        spoiler.setBounds((int) rect.left, (int) rect.top, (int) rect.right, (int) rect.bottom);
        spoiler.draw(canvas);
        canvas.restore();
        if (interactive) {
            invalidate();
        }
    }

    private Bitmap obtainBlurredAvatar(ImageReceiver avatar) {
        Object source = avatar.getDrawable();
        BlurredAvatar cached = blurredAvatars.get(avatar);
        if (cached != null && cached.source == source && cached.bitmap != null) {
            return cached.bitmap;
        }
        int size = 24;
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas bitmapCanvas = new Canvas(bitmap);
        float x = avatar.getImageX();
        float y = avatar.getImageY();
        float w = avatar.getImageWidth();
        float h = avatar.getImageHeight();
        float alpha = avatar.getAlpha();
        int[] radius = avatar.getRoundRadius().clone();
        avatar.setRoundRadius(0);
        avatar.setAlpha(1f);
        avatar.setImageCoords(0, 0, size, size);
        avatar.draw(bitmapCanvas);
        avatar.setImageCoords(x, y, w, h);
        avatar.setAlpha(alpha);
        avatar.setRoundRadius(radius);
        Utilities.stackBlurBitmap(bitmap, 5);
        if (cached == null) {
            cached = new BlurredAvatar();
            blurredAvatars.put(avatar, cached);
        }
        cached.source = source;
        cached.bitmap = bitmap;
        return bitmap;
    }

    private final AvatarsDrawable.ImageDrawer reactionAvatarDrawer = this::drawReactionAvatar;

    private boolean drawReactionAvatar(Canvas canvas, ImageReceiver imageReceiver, long id) {
        if (id == 0 || !session.isAvatarHidden(id)) {
            return false;
        }
        drawHiddenAvatar(canvas, imageReceiver, imageReceiver.getAlpha());
        return true;
    }

    private void installReactionAvatarDrawer(ChatMessageCell cell) {
        ArrayList<ReactionsLayoutInBubble.ReactionButton> buttons = cell.reactionsLayoutInBubble.reactionButtons;
        for (int i = 0; i < buttons.size(); i++) {
            AvatarsDrawable avatars = buttons.get(i).getAvatarsDrawable();
            if (avatars != null) {
                avatars.imageDrawer = reactionAvatarDrawer;
            }
        }
    }

    private void drawAvatar(Canvas canvas, ChatMessageCell cell) {
        ImageReceiver avatar = cell.getAvatarImage();
        if (avatar == null) {
            return;
        }
        if (cell.drawPinnedBottom()) {
            avatar.setVisible(false, false);
            return;
        }
        MessageObject.GroupedMessages group = cell.getCurrentMessagesGroup();
        boolean replaceAnimation = group != null && group.transitionParams.backgroundChangeBounds;
        int cellTop = replaceAnimation ? cell.getTop() : (int) cell.getY();
        int y = cellTop;
        y += cell.getPaddingTopAnimated();
        y += cell.getLayoutHeight() + cell.getTransitionParams().deltaBottom;
        int maxY = getMeasuredHeight() - getPaddingBottom();
        if (y > maxY) {
            y = maxY;
        }
        float tx = cell.getSlidingOffsetX() + cell.getCheckBoxTranslation();
        int top = findRunTop(cell, cellTop + cell.getPaddingTop());
        if (y - dp(48) < top) {
            y = top + dp(48);
        }
        int cellBottom = replaceAnimation ? cell.getBottom() : (int) (cell.getY() + cell.getMeasuredHeight() + cell.getDeltaBottom());
        if (y > cellBottom) {
            y = cellBottom;
        }
        if (replaceAnimation) {
            y -= cell.getTranslationY();
        }
        canvas.save();
        if (tx != 0) {
            canvas.translate(tx, 0);
        }
        avatar.setImageY(y - dp(44));
        avatar.setAlpha(cell.getAlpha());
        avatar.setVisible(true, false);
        long peer = avatarPeer(cell);
        if (peer != 0 && session.isAvatarHidden(peer)) {
            drawHiddenAvatar(canvas, avatar, cell.getAlpha());
        } else {
            avatar.draw(canvas);
        }
        canvas.restore();
        if (interactive && peer != 0) {
            AvatarHit hit = new AvatarHit();
            hit.rect.set(avatar.getImageX() + tx, avatar.getImageY(), avatar.getImageX2() + tx, avatar.getImageY2());
            hit.peerId = peer;
            avatarHits.add(hit);
        }
    }

    private int findRunTop(ChatMessageCell cell, int top) {
        int position = getChildAdapterPosition(cell);
        for (int tries = 0; position != NO_POSITION && cell.drawPinnedTop() && tries < 20; tries++) {
            MessageObject.GroupedMessages group = cell.getCurrentMessagesGroup();
            MessageObject.GroupedMessagePosition groupPosition = cell.getCurrentPosition();
            int prevPosition = position + 1;
            if (group != null && groupPosition != null) {
                int index = group.posArray.indexOf(groupPosition);
                if (index < 0) {
                    break;
                }
                if ((groupPosition.flags & MessageObject.POSITION_FLAG_TOP) != 0) {
                    prevPosition = position + index + 1;
                } else {
                    for (int a = index - 1; a >= 0; a--) {
                        if (group.posArray.get(a).maxY < groupPosition.minY) {
                            break;
                        }
                        prevPosition++;
                    }
                }
            }
            RecyclerView.ViewHolder holder = findViewHolderForAdapterPosition(prevPosition);
            if (holder == null || !(holder.itemView instanceof ChatMessageCell)) {
                break;
            }
            cell = (ChatMessageCell) holder.itemView;
            top = cell.getTop() + cell.getPaddingTop();
            position = prevPosition;
        }
        return top;
    }

    private final class Adapter extends RecyclerListView.SelectionAdapter {

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return interactive && holder.itemView instanceof ChatActionCell;
        }

        @Override
        public long getItemId(int position) {
            Item item = itemAt(position);
            return item != null ? item.message.stableId : RecyclerView.NO_ID;
        }

        @Override
        public int getItemViewType(int position) {
            Item item = itemAt(position);
            return item != null && item.block.type != MessageExportDocument.BLOCK_MESSAGES ? VIEW_TYPE_ACTION : VIEW_TYPE_MESSAGE;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (viewType == VIEW_TYPE_ACTION) {
                ChatActionCell actionCell = new ChatActionCell(parent.getContext(), false, provider) {
                    @SuppressLint("ClickableViewAccessibility")
                    @Override
                    public boolean onTouchEvent(MotionEvent event) {
                        return false;
                    }
                };
                prepareImage(actionCell.getPhotoImage());
                return new Holder(actionCell);
            }
            ChatMessageCell cell = new ChatMessageCell(parent.getContext(), session.document.context.account, false, session.sharedResources, provider) {
                @Override
                public void setPressed(boolean pressed) {
                    super.setPressed(false);
                }

                private final PreviewTapHandler tapHandler = interactive ? new PreviewTapHandler(this, session) : null;

                @SuppressLint("ClickableViewAccessibility")
                @Override
                public boolean onTouchEvent(MotionEvent event) {
                    boolean tapped = tapHandler != null && tapHandler.onTouchEvent(event);
                    boolean handled = super.onTouchEvent(event);
                    return tapped || handled;
                }

                @Override
                public void drawOutboundsContent(Canvas canvas) {
                    if (getMessageObject() != null) {
                        layoutTextXY(needsParentTextLayout(getMessageObject()));
                    }
                    super.drawOutboundsContent(canvas);
                }

                @Override
                protected int getAnimatedEmojiCacheType() {
                    return interactive ? super.getAnimatedEmojiCacheType() : AnimatedEmojiDrawable.CACHE_TYPE_NOANIMATE_FOLDER;
                }

                @Override
                public void drawSideButton(Canvas canvas, boolean fromQuickShare) {
                }

                @Override
                public void setVisiblePart(int position, int height, int parent, float parentOffset, float visibleTop, int parentW, int parentH, int blurredViewTopOffset, int blurredViewBottomOffset, int position2) {
                    super.setVisiblePart(0, Math.max(height, getMeasuredHeight()), parent, parentOffset, visibleTop, parentW, parentH, blurredViewTopOffset, blurredViewBottomOffset, 0);
                }

                @Override
                public void drawTime(Canvas canvas, float alpha, boolean fromParent) {
                    MessageObject message = getMessageObject();
                    if (!fromParent && getCurrentMessagesGroup() != null || message != null && message.notime && timeWidth <= 0) {
                        return;
                    }
                    super.drawTime(canvas, alpha, fromParent);
                }

                @Override
                public void drawSummarizeButton(Canvas canvas) {
                }

                @Override
                protected boolean checkNeedDrawShareButton(MessageObject messageObject) {
                    return false;
                }

                @Override
                protected int getBotButtonsParentWidth() {
                    return MessageExportListView.this.getMeasuredWidth();
                }

                @Override
                protected boolean isNeedAuthorName() {
                    MessageObject message = getMessageObject();
                    if (message != null && message.forceAvatar) {
                        return !isPinnedTop();
                    }
                    return super.isNeedAuthorName();
                }

                @Override
                protected void onDraw(@NonNull Canvas canvas) {
                    super.onDraw(canvas);
                    if (currentBackgroundDrawable != null) {
                        Rect bounds = ownBackgroundBounds.get(this);
                        if (bounds == null) {
                            ownBackgroundBounds.put(this, bounds = new Rect());
                        }
                        bounds.set(currentBackgroundDrawable.getBounds());
                    }
                }

                @Override
                public void draw(@NonNull Canvas canvas) {
                    super.draw(canvas);
                    if (interactive && drawingToBitmap) {
                        installReactionAvatarDrawer(this);
                        drawCellLayers(canvas, this, false);
                    }
                }

                @Override
                public void invalidate() {
                    super.invalidate();
                    if (interactive) {
                        MessageExportListView.this.invalidate();
                    }
                }

                @Override
                public void invalidateLite() {
                    super.invalidateLite();
                    if (interactive) {
                        MessageExportListView.this.invalidate();
                    }
                }

                @Override
                public void invalidate(int l, int t, int r, int b) {
                    super.invalidate(l, t, r, b);
                    if (interactive) {
                        MessageExportListView.this.invalidate();
                    }
                }
            };
            prepareImage(cell.getPhotoImage());
            if (!interactive) {
                cell.replyImageReceiver.setCrossfadeAlpha((byte) 0);
            }
            cell.setClipChildren(false);
            cell.setClipToPadding(false);
            cell.setFullyDraw(true);
            cell.setInvalidateSpoilersParent(interactive);
            if (interactive) {
                cell.setDelegate(new ChatMessageCell.ChatMessageCellDelegate() {
                    @Override
                    public TextSelectionHelper.ChatListTextSelectionHelper getTextSelectionHelper() {
                        return selectionHelper;
                    }

                    @Override
                    public boolean hasSelectedMessages() {
                        return true;
                    }

                    @Override
                    public boolean canPerformActions() {
                        return false;
                    }

                    @Override
                    public boolean isAdmin(long uid) {
                        return session.document.context.isAdmin(uid);
                    }

                    @Override
                    public boolean isOwner(long uid) {
                        return session.document.context.isOwner(uid);
                    }

                    @Override
                    public String getAdminRank(long uid) {
                        return session.document.context.getAdminRank(uid);
                    }

                    @Override
                    public void forceUpdate(ChatMessageCell cell, boolean anchorScroll) {
                        MessageObject message = cell.getPrimaryMessageObject();
                        if (message != null) {
                            session.invalidateLayout(message);
                        }
                    }
                });
            } else {
                cell.setDelegate(new ChatMessageCell.ChatMessageCellDelegate() {
                    @Override
                    public boolean hasSelectedMessages() {
                        return true;
                    }

                    @Override
                    public boolean canPerformActions() {
                        return false;
                    }

                    @Override
                    public boolean isAdmin(long uid) {
                        return session.document.context.isAdmin(uid);
                    }

                    @Override
                    public boolean isOwner(long uid) {
                        return session.document.context.isOwner(uid);
                    }

                    @Override
                    public String getAdminRank(long uid) {
                        return session.document.context.getAdminRank(uid);
                    }
                });
            }
            return new Holder(cell);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            Item item = itemAt(position);
            if (item == null) {
                return;
            }
            if (holder.itemView instanceof ChatActionCell actionCell) {
                MessageExportChatContext context = session.document.context;
                actionCell.isForum = context.isForum();
                actionCell.isMonoForum = context.isMonoForum();
                actionCell.setMessageObject(item.message);
                return;
            }
            ChatMessageCell cell = (ChatMessageCell) holder.itemView;
            MessageExportDocument.Block block = item.block;
            session.document.context.apply(cell, item.message);
            session.ensureHiddenSpans(item.message);
            boolean pinnedTop = block.pinnedTop;
            boolean pinnedBottom = block.pinnedBottom;
            if (block.group != null) {
                MessageObject.GroupedMessagePosition p = block.group.getPosition(item.message);
                if (p != null) {
                    if ((p.flags & MessageObject.POSITION_FLAG_TOP) == 0) {
                        pinnedTop = true;
                    }
                    if ((p.flags & MessageObject.POSITION_FLAG_BOTTOM) == 0) {
                        pinnedBottom = true;
                    }
                }
            }
            cell.setParentViewSize(getMeasuredWidth() + MessageExportChatContext.getWidthCompensation(item.message), Math.max(getMeasuredHeight(), AndroidUtilities.displaySize.y));
            if (itemAnimator == null || !itemAnimator.isRunning()) {
                cell.getTransitionParams().resetAnimation();
            }
            cell.setSlidingOffset(0);
            cell.setAlpha(1f);
            cell.setMessageObject(item.message, block.group, pinnedBottom, pinnedTop, false, false);
            session.ensureHiddenSpans(item.message);
            if (session.isReplyAuthorHidden(item.message)) {
                if (cell.replyNameSpoiler == null) {
                    cell.replyNameSpoiler = new SpoilerEffect();
                    cell.replyNameSpoiler.setParentView(cell);
                }
            } else {
                cell.replyNameSpoiler = null;
            }
            if (interactive) {
                cell.setSpoilersSuppressed(false);
            }
            cell.drawingToBitmap = drawingToBitmap;
            if (!interactive && item.message.isAnyKindOfSticker()) {
                cell.getPhotoImage().setAllowStartAnimation(false);
            }
            PreviewTapHandler.ensureSpoilerBlur(cell);
            if (interactive) {
                cell.setDrawSelectionBackground(block.group == null);
                cell.setChecked(true, block.group == null, false);
            }
        }

        @Override
        public int getItemCount() {
            return items.size();
        }
    }
}
