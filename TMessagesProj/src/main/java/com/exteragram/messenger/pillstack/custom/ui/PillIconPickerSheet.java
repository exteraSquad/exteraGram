/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.pillstack.custom.ui;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.lerp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.text.Editable;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.SparseIntArray;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.exteragram.messenger.ExteraConfig;
import com.exteragram.messenger.icons.IconManager;
import com.exteragram.messenger.pillstack.custom.CustomPillSpec;
import com.exteragram.messenger.pillstack.custom.PillIconView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.EmojiData;
import org.telegram.messenger.LiteMode;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AdjustPanLayoutHelper;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.CloseProgressDrawable2;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.EmojiTabsStrip;
import org.telegram.ui.Components.EmojiView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.ScaleStateListAnimator;
import org.telegram.ui.Components.SearchStateDrawable;
import org.telegram.ui.Components.ViewPagerFixed;
import org.telegram.ui.Components.blur3.BlurredBackgroundDrawableViewFactory;
import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable;
import org.telegram.ui.Components.blur3.drawable.color.impl.BlurredBackgroundProviderImpl;
import org.telegram.utils.glass.source.GlassSourceColor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class PillIconPickerSheet extends BottomSheet implements NotificationCenter.NotificationCenterDelegate {

    private static final int PAGE_EMOJI = 0;
    private static final int PAGE_ICONS = 1;
    private static final int CARD_MARGIN = 12;
    private static final int CARD_PADDING = 6;
    private static final int CELL_INSET = 3;
    private static final float START_PADDING = .45f;
    private static final int PAD_TAG = 34;
    private static final int[] EMOJI_TITLES = {
            R.string.Emoji1, R.string.Emoji2, R.string.Emoji3, R.string.Emoji4,
            R.string.Emoji5, R.string.Emoji6, R.string.Emoji7, R.string.Emoji8
    };

    private static ArrayList<String> filledIcons;
    private static ArrayList<String> otherIcons;

    @Nullable
    private final CustomPillSpec.Icon current;
    @Nullable
    private final CustomPillSpec.Icon keepIcon;
    private final Paint cardPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint selectedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF cardRect = new RectF();
    private final Utilities.Callback<CustomPillSpec.Icon> onSelected;
    private final ViewPagerFixed viewPager;
    @Nullable
    private TabsView tabsView;
    private final NotificationCenter.ObserversGroup observers;
    private final BlurredBackgroundDrawableViewFactory glassFactory;
    private float maxPadding = -1;
    private float top;
    private float expandProgress;
    private boolean wasKeyboardVisible;
    private boolean loadingIcons;

    public PillIconPickerSheet(BaseFragment fragment, @Nullable CustomPillSpec.Icon current, @Nullable CustomPillSpec.Icon keepIcon, Utilities.Callback<CustomPillSpec.Icon> onSelected) {
        super(fragment.getParentActivity(), true, fragment.getResourceProvider());
        this.current = current;
        this.keepIcon = keepIcon;
        this.onSelected = onSelected;
        Context context = getContext();

        useSmoothKeyboard = true;
        occupyNavigationBar = true;
        drawNavigationBar = false;
        AndroidUtilities.enableEdgeToEdge(getWindow());
        AndroidUtilities.setLightNavigationBar(this, AndroidUtilities.computePerceivedBrightness(getThemedColor(Theme.key_windowBackgroundGray)) >= .721f);

        containerView = new ContainerView(context);
        viewPager = new ViewPagerFixed(context) {
            @Override
            public void onTabAnimationUpdate(boolean manual) {
                if (tabsView != null) {
                    tabsView.setType(viewPager.getPositionAnimated());
                }
                containerView.invalidate();
            }
        };
        int startPage = current != null && (current.isEmoji() || current.isCustomEmoji()) ? PAGE_EMOJI : PAGE_ICONS;
        viewPager.currentPosition = startPage;
        viewPager.setAdapter(new ViewPagerFixed.Adapter() {
            @Override
            public int getItemCount() {
                return 2;
            }

            @Override
            public View createView(int viewType) {
                return viewType == PAGE_EMOJI ? new EmojiPage(context) : new IconsPage(context);
            }

            @Override
            public int getItemViewType(int position) {
                return position;
            }

            @Override
            public void bindView(View view, int position, int viewType) {
                ((Page) view).update();
            }
        });
        containerView.addView(viewPager, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.BOTTOM | Gravity.FILL_HORIZONTAL));

        GlassSourceColor glassSource = new GlassSourceColor();
        glassSource.setColor(getThemedColor(Theme.key_windowBackgroundGray));
        glassFactory = new BlurredBackgroundDrawableViewFactory(glassSource);
        tabsView = new TabsView(context, LocaleController.getString(R.string.Emoji), LocaleController.getString(R.string.CustomPillIconTabIcons));
        tabsView.setOnTabSelected(page -> {
            if (!viewPager.isManualScrolling() && viewPager.getCurrentPosition() != page) {
                viewPager.scrollToPosition(page);
                if (tabsView != null) {
                    tabsView.setType(page);
                }
            }
        });
        tabsView.setType(startPage);
        containerView.addView(tabsView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | Gravity.FILL_HORIZONTAL));

        observers = NotificationCenter.getInstance(currentAccount).createObserversGroup(this).add(NotificationCenter.stickersDidLoad);
        MediaDataController.getInstance(currentAccount).checkStickers(MediaDataController.TYPE_EMOJIPACKS);
        Emoji.loadRecentEmoji();
        loadIcons();
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.stickersDidLoad && (int) args[0] == MediaDataController.TYPE_EMOJIPACKS) {
            for (View view : viewPager.getViewPages()) {
                if (view instanceof EmojiPage page) {
                    page.update();
                }
            }
        }
    }

    private void loadIcons() {
        if (filledIcons != null || loadingIcons) {
            return;
        }
        loadingIcons = true;
        Utilities.globalQueue.postRunnable(() -> {
            ArrayList<String> names = new ArrayList<>(IconManager.INSTANCE.getSystemIcons().keySet());
            Collections.sort(names);
            ArrayList<String> filled = new ArrayList<>();
            ArrayList<String> other = new ArrayList<>();
            for (String name : names) {
                (name.contains("filled") ? filled : other).add(name);
            }
            AndroidUtilities.runOnUIThread(() -> {
                loadingIcons = false;
                if (names.isEmpty()) {
                    IconManager.INSTANCE.initialize(true);
                    return;
                }
                filledIcons = filled;
                otherIcons = other;
                for (View view : viewPager.getViewPages()) {
                    if (view instanceof IconsPage page) {
                        page.update();
                    }
                }
            });
        });
    }

    private int getNavigationInset() {
        return Math.max(0, getBottomInset());
    }

    private void select(@Nullable CustomPillSpec.Icon icon) {
        onSelected.run(icon);
        dismiss();
    }

    private void closeKeyboard() {
        keyboardVisible = false;
        container.animate().translationY(0).setDuration(AdjustPanLayoutHelper.keyboardDuration).setInterpolator(AdjustPanLayoutHelper.keyboardInterpolator).start();
        for (View view : viewPager.getViewPages()) {
            if (view instanceof Page page) {
                AndroidUtilities.hideKeyboard(page.searchField.editText);
            }
        }
    }

    @Override
    public void dismiss() {
        observers.removeAllObservers();
        closeKeyboard();
        super.dismiss();
    }

    @Override
    public int getContainerViewHeight() {
        if (containerView.getMeasuredHeight() <= 0) {
            return AndroidUtilities.displaySize.y;
        }
        return (int) (containerView.getMeasuredHeight() - viewPager.getY());
    }

    @Override
    protected boolean canDismissWithSwipe() {
        return false;
    }

    private class ContainerView extends FrameLayout {

        private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint handlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final AnimatedFloat isActionBarT = new AnimatedFloat(this, 0, 250, CubicBezierInterpolator.EASE_OUT_QUINT);
        private final RectF backgroundRect = new RectF();
        private final RectF handleRect = new RectF();
        @Nullable
        private Boolean overStatusBar;

        ContainerView(Context context) {
            super(context);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            int height = MeasureSpec.getSize(heightMeasureSpec);
            if (wasKeyboardVisible != keyboardVisible) {
                wasKeyboardVisible = keyboardVisible;
                float translationY = keyboardVisible ? Math.min(0, Math.max((AndroidUtilities.displaySize.y - keyboardHeight) * .3f - top, -keyboardHeight / 3f)) : 0;
                post(() -> {
                    container.clearAnimation();
                    container.animate().translationY(translationY).setDuration(AdjustPanLayoutHelper.keyboardDuration).setInterpolator(AdjustPanLayoutHelper.keyboardInterpolator).start();
                });
            }
            maxPadding = Math.min(height * START_PADDING, dp(350) / (1f - START_PADDING) * START_PADDING);
            viewPager.setPadding(0, getStatusBarHeight(), 0, 0);
            viewPager.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
            if (tabsView != null) {
                tabsView.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), 0);
            }
            setMeasuredDimension(width, height);
        }

        @Override
        protected void dispatchDraw(@NonNull Canvas canvas) {
            backgroundPaint.setColor(getThemedColor(Theme.key_windowBackgroundGray));
            top = 0;
            for (View view : viewPager.getViewPages()) {
                if (view instanceof Page page) {
                    top += page.top() * Utilities.clamp(1f - Math.abs(page.getTranslationX() / (float) page.getMeasuredWidth()), 1, 0);
                }
            }
            float statusBar = isActionBarT.set(top <= 0 ? 1 : 0);
            expandProgress = statusBar;
            for (View view : viewPager.getViewPages()) {
                if (view instanceof Page page && page.getVisibility() == View.VISIBLE) {
                    page.updateTops();
                }
            }
            float y = top + viewPager.getPaddingTop() - lerp(dp(8), viewPager.getPaddingTop(), statusBar);
            backgroundRect.set(backgroundPaddingLeft, y, getWidth() - backgroundPaddingLeft, getHeight() + dp(8));

            boolean isOverStatusBar = backgroundRect.top < getStatusBarHeight();
            if (overStatusBar == null || overStatusBar != isOverStatusBar) {
                overStatusBar = isOverStatusBar;
                AndroidUtilities.setLightStatusBar(getWindow(), isOverStatusBar ? AndroidUtilities.computePerceivedBrightness(backgroundPaint.getColor()) >= .721f : !Theme.isCurrentThemeDark());
            }
            canvas.drawRoundRect(backgroundRect, (1f - statusBar) * dp(14), (1f - statusBar) * dp(14), backgroundPaint);
            handleRect.set((getWidth() - dp(36)) / 2f, y + dp(9.66f), (getWidth() + dp(36)) / 2f, y + dp(9.66f + 4));
            handlePaint.setColor(Theme.multAlpha(getThemedColor(Theme.key_dialogTextBlack), .2f * (1f - statusBar)));
            canvas.drawRoundRect(handleRect, dp(4), dp(4), handlePaint);
            canvas.save();
            canvas.clipRect(backgroundRect);
            super.dispatchDraw(canvas);
            canvas.restore();
        }

        @Override
        public boolean dispatchTouchEvent(MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_DOWN && event.getY() < top) {
                dismiss();
                return true;
            }
            return super.dispatchTouchEvent(event);
        }
    }

    private record Item(int type, @Nullable CharSequence text, @Nullable List<CustomPillSpec.Icon> icons, boolean first, boolean last) {
    }

    private static int getCellRadius() {
        return Math.max(4, ExteraConfig.getSectionRadiusDp() - CARD_PADDING - CELL_INSET);
    }

    private void drawCard(Canvas canvas, View view, boolean first, boolean last) {
        float radius = dp(ExteraConfig.getSectionRadiusDp());
        cardPaint.setColor(getThemedColor(Theme.key_windowBackgroundWhite));
        cardRect.set(dp(CARD_MARGIN), first ? 0 : -radius, view.getWidth() - dp(CARD_MARGIN), last ? view.getHeight() : view.getHeight() + radius);
        canvas.drawRoundRect(cardRect, radius, radius, cardPaint);
    }

    private abstract class Page extends FrameLayout {

        static final int TYPE_PAD = 0;
        static final int TYPE_HEADER = 1;
        static final int TYPE_ROW = 2;
        static final int TYPE_KEEP = 3;
        static final int TYPE_EMPTY = 4;
        static final int TYPE_GAP = 5;

        final RecyclerListView listView;
        final LinearLayoutManager layoutManager;
        final SearchField searchField;
        final ArrayList<Item> items = new ArrayList<>();
        final SparseIntArray sectionPositions = new SparseIntArray();
        String query = "";

        Page(Context context, String searchHint) {
            super(context);
            listView = new RecyclerListView(context) {
                @Override
                protected void dispatchDraw(@NonNull Canvas canvas) {
                    canvas.save();
                    canvas.clipRect(0, Math.max(0, Page.this.top()) + getHeaderOffset() + getStripHeight() + dp(50), getWidth(), getHeight());
                    super.dispatchDraw(canvas);
                    canvas.restore();
                }
            };
            listView.setAdapter(new Adapter());
            listView.setLayoutManager(layoutManager = new LinearLayoutManager(context));
            listView.setClipToPadding(false);
            listView.setVerticalScrollBarEnabled(false);
            listView.setSelectorType(100);
            listView.setSelectorDrawableColor(0);
            listView.setItemSelectorColorProvider(p -> 0);
            listView.setOnScrollListener(new RecyclerView.OnScrollListener() {
                @Override
                public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                    containerView.invalidate();
                    onListScrolled();
                    if (keyboardVisible && listView.scrollingByUser) {
                        closeKeyboard();
                    }
                }
            });
            addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

            searchField = new SearchField(context, searchHint, newQuery -> {
                query = newQuery.trim().toLowerCase(Locale.ROOT);
                onQueryChanged();
            });
            addView(searchField, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 50, Gravity.TOP));
        }

        abstract int getColumns();

        int getStripHeight() {
            return 0;
        }

        int getHeaderHeight() {
            return dp(16) + getStripHeight() + dp(50);
        }

        int getHeaderOffset() {
            return lerp(dp(16), dp(6), expandProgress);
        }

        float top() {
            for (int i = 0; i < listView.getChildCount(); ++i) {
                View child = listView.getChildAt(i);
                if (child.getTag() instanceof Integer tag && tag == PAD_TAG) {
                    return Math.max(0, child.getBottom() - getHeaderHeight());
                }
            }
            return 0;
        }

        void updateTops() {
            float pageTop = Math.max(0, top());
            searchField.setTranslationY(getHeaderOffset() + getStripHeight() + pageTop);
            listView.invalidate();
        }

        void onListScrolled() {
        }

        void onQueryChanged() {
            update();
        }

        abstract void fillItems(ArrayList<Item> items);

        void update() {
            items.clear();
            sectionPositions.clear();
            items.add(new Item(TYPE_PAD, null, null, false, false));
            if (keepIcon != null && TextUtils.isEmpty(query)) {
                items.add(new Item(TYPE_GAP, null, null, true, false));
                items.add(new Item(TYPE_KEEP, LocaleController.getString(R.string.CustomPillIconKeep), null, true, true));
            }
            fillItems(items);
            RecyclerView.Adapter<?> adapter = listView.getAdapter();
            if (adapter != null) {
                adapter.notifyDataSetChanged();
            }
        }

        void addSection(ArrayList<Item> items, int section, CharSequence title, List<CustomPillSpec.Icon> icons) {
            if (icons.isEmpty()) {
                return;
            }
            boolean separated = ExteraConfig.getSectionsSeparatedHeaders();
            items.add(new Item(TYPE_GAP, null, null, separated || items.size() <= 1, false));
            if (section >= 0) {
                sectionPositions.put(section, items.size());
            }
            items.add(new Item(TYPE_HEADER, title, null, !separated, false));
            int columns = getColumns();
            for (int i = 0; i < icons.size(); i += columns) {
                items.add(new Item(TYPE_ROW, null, icons.subList(i, Math.min(icons.size(), i + columns)), separated && i == 0, i + columns >= icons.size()));
            }
        }

        void scrollToSection(int section) {
            int position = sectionPositions.get(section, -1);
            if (position >= 0) {
                listView.stopScroll();
                layoutManager.scrollToPositionWithOffset(position, 0);
            }
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            setPadding(backgroundPaddingLeft, 0, backgroundPaddingLeft, 0);
            searchField.setTranslationY(getHeaderOffset() + getStripHeight());
            listView.setPadding(0, getHeaderHeight(), 0, getNavigationInset() + dp(56));
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }

        private class Adapter extends RecyclerListView.SelectionAdapter {

            @Override
            public boolean isEnabled(RecyclerView.ViewHolder holder) {
                return false;
            }

            @Override
            public int getItemCount() {
                return items.size();
            }

            @Override
            public int getItemViewType(int position) {
                return items.get(position).type();
            }

            @NonNull
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View view;
                if (viewType == TYPE_HEADER) {
                    view = new HeaderRow(getContext());
                } else if (viewType == TYPE_ROW) {
                    view = new IconRow(getContext(), getColumns());
                } else if (viewType == TYPE_KEEP) {
                    view = new KeepRow(getContext());
                } else if (viewType == TYPE_EMPTY) {
                    TextView textView = new TextView(getContext());
                    textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
                    textView.setTypeface(AndroidUtilities.regular());
                    textView.setGravity(Gravity.CENTER);
                    textView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
                    textView.setPadding(dp(16), dp(48), dp(16), dp(48));
                    view = textView;
                } else {
                    view = new View(getContext());
                }
                if (viewType == TYPE_HEADER || viewType == TYPE_ROW || viewType == TYPE_KEEP) {
                    view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                }
                return new RecyclerListView.Holder(view);
            }

            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                Item item = items.get(position);
                switch (holder.getItemViewType()) {
                    case TYPE_PAD -> {
                        holder.itemView.setTag(PAD_TAG);
                        holder.itemView.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) maxPadding));
                    }
                    case TYPE_HEADER -> ((HeaderRow) holder.itemView).set(item.text(), item.first());
                    case TYPE_ROW -> ((IconRow) holder.itemView).set(item.icons(), item.first(), item.last());
                    case TYPE_KEEP -> ((KeepRow) holder.itemView).set(item.text());
                    case TYPE_GAP -> holder.itemView.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(item.first() ? 8 : 16)));
                    case TYPE_EMPTY -> {
                        ((TextView) holder.itemView).setText(item.text());
                        holder.itemView.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                    }
                    default -> {
                    }
                }
            }
        }
    }

    private class EmojiPage extends Page {

        private final EmojiTabsStrip tabsStrip;
        private final ArrayList<TLRPC.TL_messages_stickerSet> emojiSets = new ArrayList<>();
        private final ArrayList<CustomPillSpec.Icon> searchResults = new ArrayList<>();
        private int searchId;

        EmojiPage(Context context) {
            super(context, LocaleController.getString(R.string.SearchEmojiHint));
            tabsStrip = new EmojiTabsStrip(context, resourcesProvider, true, false, true, true, 0, null) {
                @Override
                protected boolean onTabClick(int index) {
                    if (!TextUtils.isEmpty(query)) {
                        searchField.clear();
                    }
                    scrollToSection(index);
                    return true;
                }
            };
            addView(tabsStrip, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 36));
        }

        @Override
        int getStripHeight() {
            return dp(36);
        }

        @Override
        int getColumns() {
            return 7;
        }

        @Override
        void updateTops() {
            super.updateTops();
            tabsStrip.setTranslationY(getHeaderOffset() + Math.max(0, top()));
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            tabsStrip.setTranslationY(getHeaderOffset());
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }

        @Override
        void onListScrolled() {
            int first = layoutManager.findFirstVisibleItemPosition();
            int section = -1;
            for (int i = 0; i < sectionPositions.size(); i++) {
                if (sectionPositions.valueAt(i) <= first && (section < 0 || sectionPositions.valueAt(i) >= sectionPositions.get(section))) {
                    section = sectionPositions.keyAt(i);
                }
            }
            if (section >= 0) {
                tabsStrip.select(section);
            }
        }

        @Override
        void onQueryChanged() {
            int id = ++searchId;
            searchResults.clear();
            if (TextUtils.isEmpty(query)) {
                update();
                return;
            }
            MediaDataController.getInstance(currentAccount).getEmojiSuggestions(AndroidUtilities.getCurrentKeyboardLanguage(), query, false, (result, alias) -> {
                if (id != searchId) {
                    return;
                }
                LinkedHashSet<CustomPillSpec.Icon> found = new LinkedHashSet<>();
                for (MediaDataController.KeywordResult keyword : result) {
                    if (keyword.emoji == null) {
                        continue;
                    }
                    if (keyword.emoji.startsWith("animated_")) {
                        long documentId = Utilities.parseLong(keyword.emoji.substring("animated_".length()));
                        if (documentId != 0) {
                            found.add(new CustomPillSpec.Icon(CustomPillSpec.ICON_CUSTOM_EMOJI, String.valueOf(documentId)));
                        }
                    } else {
                        String emoji = Emoji.fixEmoji(keyword.emoji);
                        if (Emoji.getEmojiDrawable(emoji) != null) {
                            found.add(new CustomPillSpec.Icon(CustomPillSpec.ICON_EMOJI, emoji));
                        }
                    }
                }
                searchResults.clear();
                searchResults.addAll(found);
                update();
            }, true);
        }

        @Override
        void fillItems(ArrayList<Item> items) {
            if (!TextUtils.isEmpty(query)) {
                if (searchResults.isEmpty()) {
                    items.add(new Item(TYPE_EMPTY, LocaleController.getString(R.string.CustomPillEmojiNothingFound), null, false, false));
                } else {
                    addSection(items, -1, LocaleController.getString(R.string.CustomPillIconResults), searchResults);
                }
                return;
            }
            addSection(items, 0, LocaleController.getString(R.string.RecentlyUsed), toEmojiIcons(Emoji.recentEmoji));
            for (int i = 0; i < EmojiData.dataColored.length && i < EMOJI_TITLES.length; i++) {
                ArrayList<String> category = new ArrayList<>();
                Collections.addAll(category, EmojiData.dataColored[i]);
                addSection(items, i + 1, LocaleController.getString(EMOJI_TITLES[i]), toEmojiIcons(category));
            }
            emojiSets.clear();
            emojiSets.addAll(MediaDataController.getInstance(currentAccount).getStickerSets(MediaDataController.TYPE_EMOJIPACKS));
            ArrayList<EmojiView.EmojiPack> packs = new ArrayList<>();
            for (int i = 0; i < emojiSets.size(); i++) {
                TLRPC.TL_messages_stickerSet set = emojiSets.get(i);
                if (set == null || set.set == null || set.documents == null || set.documents.isEmpty()) {
                    continue;
                }
                ArrayList<CustomPillSpec.Icon> icons = new ArrayList<>();
                for (TLRPC.Document document : set.documents) {
                    icons.add(new CustomPillSpec.Icon(CustomPillSpec.ICON_CUSTOM_EMOJI, String.valueOf(document.id)));
                }
                addSection(items, 1 + EmojiData.dataColored.length + packs.size(), set.set.title, icons);
                EmojiView.EmojiPack pack = new EmojiView.EmojiPack();
                pack.set = set.set;
                pack.documents = set.documents;
                pack.installed = true;
                pack.free = true;
                pack.expanded = true;
                packs.add(pack);
            }
            tabsStrip.updateEmojiPacks(packs);
        }

        private List<CustomPillSpec.Icon> toEmojiIcons(List<String> emojis) {
            ArrayList<CustomPillSpec.Icon> icons = new ArrayList<>(emojis.size());
            for (String emoji : emojis) {
                if (emoji.startsWith("animated_")) {
                    long documentId = Utilities.parseLong(emoji.substring("animated_".length()));
                    if (documentId != 0) {
                        icons.add(new CustomPillSpec.Icon(CustomPillSpec.ICON_CUSTOM_EMOJI, String.valueOf(documentId)));
                    }
                } else {
                    icons.add(new CustomPillSpec.Icon(CustomPillSpec.ICON_EMOJI, emoji));
                }
            }
            return icons;
        }
    }

    private class IconsPage extends Page {

        IconsPage(Context context) {
            super(context, LocaleController.getString(R.string.CustomPillIconSearch));
        }

        @Override
        int getColumns() {
            return 7;
        }

        @Override
        void fillItems(ArrayList<Item> items) {
            if (filledIcons == null || otherIcons == null) {
                return;
            }
            if (TextUtils.isEmpty(query)) {
                addSection(items, -1, LocaleController.getString(R.string.CustomPillIconFilled), toIcons(filledIcons, null));
                addSection(items, -1, LocaleController.getString(R.string.IconPickerAllIcons), toIcons(otherIcons, null));
                return;
            }
            ArrayList<CustomPillSpec.Icon> results = new ArrayList<>(toIcons(filledIcons, query));
            results.addAll(toIcons(otherIcons, query));
            if (results.isEmpty()) {
                items.add(new Item(TYPE_EMPTY, LocaleController.getString(R.string.CustomPillIconNothingFound), null, false, false));
            } else {
                addSection(items, -1, LocaleController.getString(R.string.CustomPillIconResults), results);
            }
        }

        private List<CustomPillSpec.Icon> toIcons(List<String> names, @Nullable String filter) {
            ArrayList<CustomPillSpec.Icon> icons = new ArrayList<>();
            for (String name : names) {
                if (filter == null || name.contains(filter)) {
                    icons.add(new CustomPillSpec.Icon(CustomPillSpec.ICON_RES, name));
                }
            }
            return icons;
        }
    }

    private class HeaderRow extends FrameLayout {

        private final HeaderCell headerCell;
        private boolean inCard;

        HeaderRow(Context context) {
            super(context);
            setWillNotDraw(false);
            headerCell = new HeaderCell(context, resourcesProvider);
            addView(headerCell, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        }

        void set(CharSequence text, boolean inCard) {
            this.inCard = inCard;
            headerCell.setText(text);
            invalidate();
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            if (inCard) {
                drawCard(canvas, this, true, false);
            }
        }
    }

    private class IconRow extends ViewGroup {

        private final IconCell[] cells;
        private boolean first;
        private boolean last;

        IconRow(Context context, int columns) {
            super(context);
            setWillNotDraw(false);
            cells = new IconCell[columns];
            for (int i = 0; i < columns; i++) {
                cells[i] = new IconCell(context);
                addView(cells[i]);
            }
        }

        void set(List<CustomPillSpec.Icon> icons, boolean first, boolean last) {
            this.first = first;
            this.last = last;
            for (int i = 0; i < cells.length; i++) {
                IconCell cell = cells[i];
                if (i < icons.size()) {
                    CustomPillSpec.Icon icon = icons.get(i);
                    cell.setVisibility(VISIBLE);
                    cell.set(icon, Objects.equals(icon, current));
                    cell.setOnClickListener(v -> select(icon));
                } else {
                    cell.setVisibility(INVISIBLE);
                    cell.setOnClickListener(null);
                }
            }
            requestLayout();
            invalidate();
        }

        private int getCellSize(int width) {
            return (width - dp(CARD_MARGIN + CARD_PADDING) * 2) / cells.length;
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            int size = getCellSize(width);
            int spec = MeasureSpec.makeMeasureSpec(size - dp(CELL_INSET) * 2, MeasureSpec.EXACTLY);
            for (IconCell cell : cells) {
                cell.measure(spec, spec);
            }
            setMeasuredDimension(width, size + (first ? dp(CARD_PADDING) : 0) + (last ? dp(CARD_PADDING) : 0));
        }

        @Override
        protected void onLayout(boolean changed, int l, int t, int r, int b) {
            int size = getCellSize(r - l);
            int top = first ? dp(CARD_PADDING) : 0;
            for (int i = 0; i < cells.length; i++) {
                int index = LocaleController.isRTL ? cells.length - 1 - i : i;
                int left = dp(CARD_MARGIN + CARD_PADDING) + index * size + dp(CELL_INSET);
                cells[i].layout(left, top + dp(CELL_INSET), left + size - dp(CELL_INSET) * 2, top + size - dp(CELL_INSET));
            }
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            drawCard(canvas, this, first, last);
        }
    }

    private class IconCell extends FrameLayout {

        private final PillIconView iconView;
        private boolean selected;

        IconCell(Context context) {
            super(context);
            setWillNotDraw(false);
            setBackground(Theme.createRadSelectorDrawable(getThemedColor(Theme.key_listSelector), getCellRadius(), getCellRadius()));
            iconView = new PillIconView(context, 34);
            iconView.setCustomEmojiCacheType(LiteMode.isEnabled(LiteMode.FLAG_ANIMATED_EMOJI_KEYBOARD) ? AnimatedEmojiDrawable.CACHE_TYPE_ALERT_PREVIEW : AnimatedEmojiDrawable.CACHE_TYPE_ALERT_PREVIEW_STATIC);
            addView(iconView, LayoutHelper.createFrame(34, 34, Gravity.CENTER));
            ScaleStateListAnimator.apply(this, .1f, 1.5f);
        }

        void set(@Nullable CustomPillSpec.Icon icon, boolean selected) {
            this.selected = selected;
            iconView.setIcon(icon);
            boolean res = icon != null && icon.isRes();
            int size = dp(res ? 28 : 34);
            LayoutParams params = (LayoutParams) iconView.getLayoutParams();
            if (params.width != size) {
                params.width = params.height = size;
                iconView.setLayoutParams(params);
            }
            iconView.setColor(selected ? getThemedColor(Theme.key_featuredStickers_addButton) : Theme.multAlpha(getThemedColor(Theme.key_windowBackgroundWhiteBlackText), .75f));
            invalidate();
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            if (selected) {
                AndroidUtilities.rectTmp.set(0, 0, getWidth(), getHeight());
                selectedPaint.setColor(Theme.multAlpha(getThemedColor(Theme.key_featuredStickers_addButton), .15f));
                canvas.drawRoundRect(AndroidUtilities.rectTmp, dp(getCellRadius()), dp(getCellRadius()), selectedPaint);
            }
        }
    }

    private class KeepRow extends FrameLayout {

        private final PillIconView iconView;
        private final TextView textView;
        private final ImageView checkView;

        KeepRow(Context context) {
            super(context);
            setWillNotDraw(false);
            FrameLayout content = new FrameLayout(context);
            content.setBackground(Theme.createRadSelectorDrawable(getThemedColor(Theme.key_listSelector), ExteraConfig.getSectionRadiusDp(), ExteraConfig.getSectionRadiusDp()));
            content.setOnClickListener(v -> select(null));
            addView(content, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL, CARD_MARGIN, 0, CARD_MARGIN, 0));
            iconView = new PillIconView(context, 24);
            content.addView(iconView, LayoutHelper.createFrame(24, 24, Gravity.CENTER_VERTICAL | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT), 21, 0, 21, 0));
            textView = new TextView(context);
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            textView.setTypeface(AndroidUtilities.regular());
            textView.setSingleLine();
            textView.setEllipsize(TextUtils.TruncateAt.END);
            content.addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, LocaleController.isRTL ? 56 : 71, 0, LocaleController.isRTL ? 71 : 56, 0));
            checkView = new ImageView(context);
            checkView.setScaleType(ImageView.ScaleType.CENTER);
            checkView.setImageResource(R.drawable.checkbig);
            content.addView(checkView, LayoutHelper.createFrame(24, 24, Gravity.CENTER_VERTICAL | (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT), 21, 0, 21, 0));
        }

        void set(CharSequence text) {
            boolean selected = current == null;
            iconView.setIcon(keepIcon);
            iconView.setColor(Theme.multAlpha(getThemedColor(Theme.key_windowBackgroundWhiteBlackText), .75f));
            textView.setText(text);
            textView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
            checkView.setColorFilter(getThemedColor(Theme.key_featuredStickers_addButton));
            checkView.setVisibility(selected ? VISIBLE : GONE);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(dp(50), MeasureSpec.EXACTLY));
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            drawCard(canvas, this, true, true);
        }
    }

    private class SearchField extends FrameLayout {

        final EditTextBoldCursor editText;
        private final ImageView clearView;
        private boolean clearVisible;

        SearchField(Context context, String hint, Utilities.Callback<String> onQuery) {
            super(context);

            FrameLayout box = new FrameLayout(context);
            box.setBackground(Theme.createRoundRectDrawable(dp(18), getThemedColor(Theme.key_windowBackgroundWhite)));
            addView(box, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 36, Gravity.FILL, 10, 6, 10, 8));

            ImageView searchView = new ImageView(context);
            searchView.setScaleType(ImageView.ScaleType.CENTER);
            SearchStateDrawable searchDrawable = new SearchStateDrawable();
            searchDrawable.setIconState(SearchStateDrawable.State.STATE_SEARCH, false);
            searchDrawable.setColor(getThemedColor(Theme.key_chat_emojiSearchIcon));
            searchView.setImageDrawable(searchDrawable);
            box.addView(searchView, LayoutHelper.createFrame(36, 36, Gravity.LEFT | Gravity.TOP));

            editText = new EditTextBoldCursor(context) {
                @Override
                public boolean onTouchEvent(MotionEvent event) {
                    if (isEnabled() && event.getAction() == MotionEvent.ACTION_DOWN) {
                        requestFocus();
                        AndroidUtilities.showKeyboard(this);
                    }
                    return super.onTouchEvent(event);
                }

                @Override
                protected void onFocusChanged(boolean focused, int direction, @Nullable Rect previouslyFocusedRect) {
                    super.onFocusChanged(focused, direction, previouslyFocusedRect);
                    if (!focused) {
                        AndroidUtilities.hideKeyboard(this);
                    }
                }
            };
            editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            editText.setTypeface(AndroidUtilities.regular());
            editText.setHintTextColor(getThemedColor(Theme.key_chat_emojiSearchIcon));
            editText.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
            editText.setBackground(null);
            editText.setPadding(0, 0, 0, 0);
            editText.setSingleLine(true);
            editText.setImeOptions(EditorInfo.IME_ACTION_SEARCH | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
            editText.setHint(hint);
            editText.setCursorColor(getThemedColor(Theme.key_featuredStickers_addedIcon));
            editText.setHandlesColor(getThemedColor(Theme.key_featuredStickers_addedIcon));
            editText.setCursorSize(dp(20));
            editText.setCursorWidth(1.5f);
            editText.setTranslationY(dp(-2));
            editText.setOnEditorActionListener((v, actionId, event) -> {
                AndroidUtilities.hideKeyboard(v);
                return true;
            });
            editText.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                }

                @Override
                public void afterTextChanged(Editable s) {
                    updateClear();
                    onQuery.run(s.toString());
                }
            });
            box.addView(editText, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 40, Gravity.LEFT | Gravity.TOP, 38, 0, 28, 0));

            clearView = new ImageView(context);
            clearView.setScaleType(ImageView.ScaleType.CENTER);
            clearView.setImageDrawable(new CloseProgressDrawable2(1.25f) {
                {
                    setSide(dp(7));
                }

                @Override
                protected int getCurrentColor() {
                    return getThemedColor(Theme.key_chat_emojiSearchIcon);
                }
            });
            clearView.setBackground(Theme.createSelectorDrawable(getThemedColor(Theme.key_listSelector), Theme.RIPPLE_MASK_CIRCLE_20DP, dp(15)));
            clearView.setAlpha(0f);
            clearView.setScaleX(.7f);
            clearView.setScaleY(.7f);
            clearView.setVisibility(View.GONE);
            clearView.setOnClickListener(v -> clear());
            box.addView(clearView, LayoutHelper.createFrame(36, 36, Gravity.RIGHT | Gravity.TOP));
        }

        void clear() {
            editText.setText("");
        }

        private void updateClear() {
            boolean visible = editText.length() > 0;
            if (clearVisible == visible) {
                return;
            }
            clearVisible = visible;
            clearView.animate().cancel();
            if (visible) {
                clearView.setVisibility(View.VISIBLE);
            }
            clearView.animate()
                    .scaleX(visible ? 1f : .7f)
                    .scaleY(visible ? 1f : .7f)
                    .alpha(visible ? 1f : 0f)
                    .withEndAction(() -> {
                        if (!clearVisible) {
                            clearView.setVisibility(View.GONE);
                        }
                    })
                    .setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT)
                    .setDuration(320)
                    .start();
        }
    }

    private class TabsView extends View {

        private static final int TAB_PADDING_DP = 18;
        private static final int INDICATOR_OUTSET_DP = 11;
        private static final int STRIP_PADDING_DP = 4;
        private static final int STRIP_HEIGHT_DP = 48;
        private static final int STRIP_TOP_DP = 4;

        private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final Paint indicatorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final BlurredBackgroundDrawable glass;
        private final CharSequence[] titles;
        private final StaticLayout[] layouts;
        private final float[] layoutLefts;
        private final RectF[] tabRects;
        private final RectF[] indicatorRects;
        private final RectF stripRect = new RectF();
        private final RectF selectRect = new RectF();
        private float type;
        private int lastWidth;
        @Nullable
        private Utilities.Callback<Integer> onTabSelected;

        TabsView(Context context, CharSequence... titles) {
            super(context);
            this.titles = titles;
            layouts = new StaticLayout[titles.length];
            layoutLefts = new float[titles.length];
            tabRects = new RectF[titles.length];
            indicatorRects = new RectF[titles.length];
            for (int i = 0; i < titles.length; i++) {
                tabRects[i] = new RectF();
                indicatorRects[i] = new RectF();
            }
            glass = glassFactory.create(this)
                    .setColorProvider(BlurredBackgroundProviderImpl.emojiViewButton(resourcesProvider))
                    .setRadius(dp(18))
                    .setPadding(dp(6));
        }

        void setType(float type) {
            this.type = type;
            invalidate();
        }

        void setOnTabSelected(Utilities.Callback<Integer> onTabSelected) {
            this.onTabSelected = onTabSelected;
        }

        private int getGlassIconColor(float alpha) {
            return ColorUtils.setAlphaComponent(getThemedColor(Theme.key_glass_defaultIcon), (int) (255 * alpha));
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            setMeasuredDimension(width, dp(STRIP_TOP_DP + STRIP_HEIGHT_DP + STRIP_TOP_DP) + getNavigationInset());
            if (width != lastWidth || layouts[0] == null) {
                updateLayouts(width);
            }
            lastWidth = width;
        }

        private void updateLayouts(int width) {
            textPaint.setTextSize(dp(14));
            textPaint.setTypeface(AndroidUtilities.bold());
            float[] textWidths = new float[titles.length];
            float total = dp(STRIP_PADDING_DP * 2);
            for (int i = 0; i < titles.length; i++) {
                layouts[i] = StaticLayout.Builder.obtain(titles[i], 0, titles[i].length(), textPaint, width).setIncludePad(false).build();
                textWidths[i] = layouts[i].getLineCount() >= 1 ? layouts[i].getLineWidth(0) : 0;
                layoutLefts[i] = layouts[i].getLineCount() >= 1 ? layouts[i].getLineLeft(0) : 0;
                total += textWidths[i] + dp(TAB_PADDING_DP * 2);
            }
            float left = (width - total) / 2f;
            stripRect.set(left, dp(STRIP_TOP_DP), left + total, dp(STRIP_TOP_DP + STRIP_HEIGHT_DP));
            float x = left + dp(STRIP_PADDING_DP);
            for (int i = 0; i < titles.length; i++) {
                float tabWidth = textWidths[i] + dp(TAB_PADDING_DP * 2);
                tabRects[i].set(x, stripRect.top, x + tabWidth, stripRect.bottom);
                indicatorRects[i].set(
                        x + dp(TAB_PADDING_DP - INDICATOR_OUTSET_DP),
                        stripRect.top + dp(INDICATOR_OUTSET_DP),
                        x + tabWidth - dp(TAB_PADDING_DP - INDICATOR_OUTSET_DP),
                        stripRect.bottom - dp(INDICATOR_OUTSET_DP)
                );
                x += tabWidth;
            }
        }

        @Override
        protected void dispatchDraw(@NonNull Canvas canvas) {
            glass.setBounds((int) stripRect.left, (int) stripRect.top, (int) stripRect.right, (int) stripRect.bottom);
            glass.draw(canvas);

            int from = Utilities.clamp((int) type, indicatorRects.length - 1, 0);
            int to = Utilities.clamp((int) Math.ceil(type), indicatorRects.length - 1, 0);
            lerp(indicatorRects[from], indicatorRects[to], type - (int) type, selectRect);
            indicatorPaint.setColor(getGlassIconColor(.065f));
            canvas.drawRoundRect(selectRect, selectRect.height() / 2f, selectRect.height() / 2f, indicatorPaint);

            for (int i = 0; i < layouts.length; i++) {
                if (layouts[i] == null) {
                    continue;
                }
                canvas.save();
                canvas.translate(tabRects[i].left + dp(TAB_PADDING_DP) - layoutLefts[i], tabRects[i].top + (tabRects[i].height() - layouts[i].getHeight()) / 2f);
                textPaint.setColor(getGlassIconColor(lerp(.4f, .8f, Utilities.clamp(1f - Math.abs(type - i), 1, 0))));
                layouts[i].draw(canvas);
                canvas.restore();
            }
        }

        @Override
        protected boolean verifyDrawable(@NonNull Drawable who) {
            return who == glass || super.verifyDrawable(who);
        }

        @Override
        @SuppressLint("ClickableViewAccessibility")
        public boolean onTouchEvent(MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                return stripRect.contains(event.getX(), event.getY());
            }
            if (event.getAction() == MotionEvent.ACTION_UP && onTabSelected != null) {
                for (int i = 0; i < tabRects.length; i++) {
                    if (tabRects[i].contains(event.getX(), event.getY())) {
                        onTabSelected.run(i);
                        break;
                    }
                }
                return true;
            }
            return super.onTouchEvent(event);
        }
    }
}
