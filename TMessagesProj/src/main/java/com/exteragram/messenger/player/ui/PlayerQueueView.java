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
import static org.telegram.messenger.LocaleController.getString;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.exteragram.messenger.player.PlayerColors;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;

@SuppressLint("ViewConstructor")
public class PlayerQueueView extends FrameLayout {

    public interface Delegate {
        void onShowOptions(PlayerQueueCell cell, MessageObject messageObject);

        void onReverseOrder();

        void onAddToList();

        void onSearchFocus(boolean focused);

        boolean isMyList();

        boolean isNoForwards();

        MessagesController.SavedMusicList getSavedMusicList();
    }

    private static final int HEADER_HEIGHT_DP = 40;
    private static final int LIST_GAP_DP = 8;
    private static final int FADE_DP = 16;
    private static final long REVERSE_DURATION = 420;

    private final int currentAccount;
    private final LinearLayout header;
    private final FrameLayout searchField;
    private final ImageView searchIcon;
    private final EditTextBoldCursor searchEditText;
    private final ImageView clearButton;
    private final ImageView reverseButton;
    private final ImageView addButton;
    private final ExpressiveStateDrawable reverseBackground;
    private final ExpressiveStateDrawable addBackground;
    private final ExpressiveStateDrawable clearBackground;
    private final RecyclerListView listView;
    private final LinearLayoutManager layoutManager;
    private final Adapter adapter;
    private final TextView emptyView;
    private final GradientDrawable fieldBackground = new GradientDrawable();
    private ItemTouchHelper itemTouchHelper;

    private Delegate delegate;
    private PlayerColors colors;
    private int sideInset = dp(PlayerDimens.SIDE_DP);
    private int selectorColor;
    private ArrayList<MessageObject> playlist = new ArrayList<>();
    private ArrayList<MessageObject> searchResult = new ArrayList<>();
    private String searchQuery;
    private boolean searchWas;
    private Runnable searchRunnable;

    @SuppressLint("ClickableViewAccessibility")
    public PlayerQueueView(Context context, int currentAccount) {
        super(context);
        this.currentAccount = currentAccount;

        header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        addView(header, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, HEADER_HEIGHT_DP, Gravity.TOP));

        searchField = new FrameLayout(context);
        header.addView(searchField, LayoutHelper.createLinear(0, HEADER_HEIGHT_DP, 1f));

        searchIcon = new ImageView(context);
        searchIcon.setScaleType(ImageView.ScaleType.CENTER);
        searchIcon.setImageResource(R.drawable.outline_header_search);
        searchField.addView(searchIcon, LayoutHelper.createFrame(PlayerDimens.ICON_BUTTON_DP, HEADER_HEIGHT_DP, Gravity.LEFT | Gravity.CENTER_VERTICAL));

        searchEditText = new EditTextBoldCursor(context);
        searchEditText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        searchEditText.setTypeface(AndroidUtilities.regular());
        searchEditText.setBackground(null);
        searchEditText.setSingleLine(true);
        searchEditText.setImeOptions(EditorInfo.IME_ACTION_SEARCH | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        searchEditText.setHint(getString(R.string.PlayerQueueSearch));
        searchEditText.setPadding(0, 0, 0, 0);
        searchEditText.setCursorSize(dp(18));
        searchEditText.setCursorWidth(1.5f);
        searchEditText.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
        searchField.addView(searchEditText, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, HEADER_HEIGHT_DP, Gravity.LEFT | Gravity.CENTER_VERTICAL, PlayerDimens.ICON_BUTTON_DP, 0, PlayerDimens.ICON_BUTTON_DP, 0));
        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                final String query = s.toString();
                clearButton.setVisibility(query.isEmpty() ? GONE : VISIBLE);
                search(query.trim().isEmpty() ? null : query);
            }
        });
        searchEditText.setOnFocusChangeListener((v, hasFocus) -> {
            if (delegate != null) {
                delegate.onSearchFocus(hasFocus);
            }
        });

        clearButton = new ImageView(context);
        clearButton.setScaleType(ImageView.ScaleType.CENTER);
        clearButton.setImageResource(R.drawable.ic_close_white);
        clearButton.setBackground(clearBackground = new ExpressiveStateDrawable(10));
        clearButton.setContentDescription(getString(R.string.ClearButton));
        clearButton.setVisibility(GONE);
        clearButton.setOnClickListener(v -> searchEditText.setText(""));
        searchField.addView(clearButton, LayoutHelper.createFrame(32, 32, Gravity.RIGHT | Gravity.CENTER_VERTICAL, 0, 0, 4, 0));

        addButton = new ImageView(context);
        addButton.setScaleType(ImageView.ScaleType.CENTER);
        addButton.setImageResource(R.drawable.msg_add);
        addButton.setBackground(addBackground = new ExpressiveStateDrawable(8).setSelectedRadius(12));
        addButton.setContentDescription(getString(R.string.PlayerQueueAdd));
        addButton.setOnClickListener(v -> {
            if (delegate != null) {
                delegate.onAddToList();
            }
        });
        addButton.setVisibility(GONE);
        header.addView(addButton, LayoutHelper.createLinear(PlayerDimens.ICON_BUTTON_DP, PlayerDimens.ICON_BUTTON_DP, Gravity.CENTER_VERTICAL, PlayerDimens.ICON_GAP_DP, 0, 0, 0));

        reverseButton = new ImageView(context);
        reverseButton.setScaleType(ImageView.ScaleType.CENTER);
        reverseButton.setImageResource(R.drawable.player_new_order);
        reverseButton.setBackground(reverseBackground = new ExpressiveStateDrawable(8).setSelectedRadius(12));
        reverseButton.setContentDescription(getString(R.string.ReverseOrder));
        reverseButton.setOnClickListener(v -> {
            if (delegate != null) {
                delegate.onReverseOrder();
            }
        });
        header.addView(reverseButton, LayoutHelper.createLinear(PlayerDimens.ICON_BUTTON_DP, PlayerDimens.ICON_BUTTON_DP, Gravity.CENTER_VERTICAL, PlayerDimens.ICON_GAP_DP, 0, 0, 0));

        listView = new RecyclerListView(context);
        listView.setVerticalScrollBarEnabled(false);
        listView.setVerticalFadingEdgeEnabled(true);
        listView.setFadingEdgeLength(dp(FADE_DP));
        listView.setItemSelectorColorProvider(position -> selectorColor);
        listView.setLayoutManager(layoutManager = new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        listView.setAdapter(adapter = new Adapter(context));
        listView.setOnItemClickListener((view, position) -> {
            if (view instanceof PlayerQueueCell) {
                ((PlayerQueueCell) view).didPressedButton();
            }
        });
        listView.setOnItemLongClickListener((view, position) -> {
            if (view instanceof PlayerQueueCell && delegate != null && !delegate.isMyList()) {
                final MessageObject messageObject = ((PlayerQueueCell) view).getMessageObject();
                if (messageObject != null && messageObject.getId() > 0 && !messageObject.isVoice()) {
                    delegate.onShowOptions((PlayerQueueCell) view, messageObject);
                    return true;
                }
            }
            return false;
        });
        listView.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    AndroidUtilities.hideKeyboard(searchEditText);
                }
            }

            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (searchWas) {
                    return;
                }
                final int first = layoutManager.findFirstVisibleItemPosition();
                final int last = layoutManager.findLastVisibleItemPosition();
                final int count = adapter.getItemCount();
                if (first == RecyclerView.NO_POSITION) {
                    return;
                }
                if (SharedConfig.playOrderReversed) {
                    if (first < 10) {
                        MediaController.getInstance().loadMoreMusic();
                    }
                } else if (last > count - 10) {
                    MediaController.getInstance().loadMoreMusic();
                }
            }
        });
        addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP, 0, HEADER_HEIGHT_DP + LIST_GAP_DP, 0, 0));

        emptyView = new TextView(context);
        emptyView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        emptyView.setTypeface(AndroidUtilities.regular());
        emptyView.setGravity(Gravity.CENTER);
        emptyView.setVisibility(GONE);
        addView(emptyView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP, 0, HEADER_HEIGHT_DP + LIST_GAP_DP, 0, 0));

        applySideInset();
    }

    public void setDelegate(Delegate delegate) {
        this.delegate = delegate;
        setupReorder();
    }

    private void applySideInset() {
        final FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) header.getLayoutParams();
        lp.leftMargin = sideInset;
        lp.rightMargin = sideInset;
        header.setLayoutParams(lp);
        emptyView.setPadding(sideInset, dp(14), sideInset, dp(14));
        for (int i = 0, n = listView.getChildCount(); i < n; i++) {
            final View child = listView.getChildAt(i);
            if (child instanceof PlayerQueueCell) {
                ((PlayerQueueCell) child).setSideInset(sideInset);
            }
        }
    }

    private void setupReorder() {
        if (itemTouchHelper != null) {
            itemTouchHelper.attachToRecyclerView(null);
            itemTouchHelper = null;
        }
        if (delegate == null || !delegate.isMyList()) {
            addButton.setVisibility(GONE);
            return;
        }
        addButton.setVisibility(VISIBLE);
        itemTouchHelper = new ItemTouchHelper(new ItemTouchHelper.Callback() {
            @Override
            public int getMovementFlags(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
                if (searchWas) {
                    return 0;
                }
                return makeMovementFlags(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0);
            }

            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                final MessagesController.SavedMusicList savedMusicList = delegate != null ? delegate.getSavedMusicList() : null;
                if (savedMusicList == null) {
                    return false;
                }
                final int from = viewHolder.getAdapterPosition();
                final int to = target.getAdapterPosition();
                final int size = savedMusicList.list.size();
                if (from < 0 || to < 0 || from >= size || to >= size) {
                    return false;
                }
                final boolean direct = isDirectOrder();
                savedMusicList.move(direct ? from : size - from - 1, direct ? to : size - to - 1);
                final ArrayList<MessageObject> list = MediaController.getInstance().getPlaylist();
                list.clear();
                list.addAll(savedMusicList.list);
                adapter.notifyItemMoved(from, to);
                return true;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
            }

            @Override
            public void onSelectedChanged(RecyclerView.ViewHolder viewHolder, int actionState) {
                if (viewHolder != null) {
                    listView.hideSelector(false);
                }
                if (actionState != ItemTouchHelper.ACTION_STATE_IDLE) {
                    listView.cancelClickRunnables(false);
                    if (viewHolder != null && viewHolder.itemView instanceof PlayerQueueCell) {
                        ((PlayerQueueCell) viewHolder.itemView).setLifted(true);
                    }
                }
                super.onSelectedChanged(viewHolder, actionState);
            }

            @Override
            public void clearView(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
                super.clearView(recyclerView, viewHolder);
                if (viewHolder.itemView instanceof PlayerQueueCell) {
                    ((PlayerQueueCell) viewHolder.itemView).setLifted(false);
                }
            }
        });
        itemTouchHelper.attachToRecyclerView(listView);
    }

    public void setColors(PlayerColors colors) {
        this.colors = colors;
        selectorColor = ColorUtils.setAlphaComponent(colors.text, 0x14);
        fieldBackground.setCornerRadius(dp(HEADER_HEIGHT_DP / 2f));
        fieldBackground.setColor(colors.surfaceContainer);
        if (searchField.getBackground() != fieldBackground) {
            searchField.setBackground(fieldBackground);
        }
        searchIcon.setColorFilter(new PorterDuffColorFilter(colors.textSecondary, PorterDuff.Mode.SRC_IN));
        clearButton.setColorFilter(new PorterDuffColorFilter(colors.textSecondary, PorterDuff.Mode.SRC_IN));
        clearBackground.setColors(colors.textSecondary, 0);
        searchEditText.setTextColor(colors.text);
        searchEditText.setHintTextColor(colors.textSecondary);
        searchEditText.setCursorColor(colors.text);
        updateReverseColors();
        addBackground.setColors(colors.icon, colors.tonal);
        addButton.setColorFilter(new PorterDuffColorFilter(colors.icon, PorterDuff.Mode.SRC_IN));
        emptyView.setTextColor(colors.textSecondary);
        listView.setGlowColor(colors.textSecondary);
        for (int i = 0, n = listView.getChildCount(); i < n; i++) {
            final View child = listView.getChildAt(i);
            if (child instanceof PlayerQueueCell) {
                ((PlayerQueueCell) child).setColors(colors);
            }
        }
    }

    private void updateReverseColors() {
        if (colors == null) {
            return;
        }
        final int content = reverseBackground.isSelected() ? colors.onTonal : colors.icon;
        reverseBackground.setColors(content, colors.tonal);
        reverseButton.setColorFilter(new PorterDuffColorFilter(content, PorterDuff.Mode.SRC_IN));
    }

    public void setReversed(boolean reversed, boolean animated) {
        reverseBackground.setSelected(reversed, animated);
        reverseButton.setSelected(reversed);
        updateReverseColors();
    }

    private boolean isDirectOrder() {
        final boolean myList = delegate != null && delegate.getSavedMusicList() != null;
        return myList != SharedConfig.playOrderReversed;
    }

    @SuppressLint("NotifyDataSetChanged")
    public void update() {
        playlist = MediaController.getInstance().getPlaylist();
        adapter.notifyDataSetChanged();
        updateEmpty();
    }

    public void reverse() {
        if (searchWas || listView.getChildCount() == 0 || !listView.isLaidOut()) {
            update();
            scrollToCurrent(false);
            return;
        }
        final MessageObject playing = MediaController.getInstance().getPlayingMessageObject();
        final HashMap<MessageObject, Float> oldY = new HashMap<>();
        final float center = listView.getHeight() / 2f;
        MessageObject anchor = null;
        int anchorTop = 0;
        boolean anchorIsCurrent = false;
        float anchorDistance = Float.MAX_VALUE;
        for (int i = 0, n = listView.getChildCount(); i < n; i++) {
            final View child = listView.getChildAt(i);
            if (!(child instanceof PlayerQueueCell cell) || cell.getMessageObject() == null) {
                continue;
            }
            final MessageObject messageObject = cell.getMessageObject();
            cell.animate().cancel();
            oldY.put(messageObject, cell.getY());
            if (anchorIsCurrent) {
                continue;
            }
            if (isCurrent(messageObject, playing)) {
                anchor = messageObject;
                anchorTop = cell.getTop();
                anchorIsCurrent = true;
            } else {
                final float distance = Math.abs(cell.getTop() + cell.getHeight() / 2f - center);
                if (distance < anchorDistance) {
                    anchorDistance = distance;
                    anchor = messageObject;
                    anchorTop = cell.getTop();
                }
            }
        }
        update();
        final int position = anchor != null ? adapter.positionOf(anchor) : -1;
        if (position < 0) {
            scrollToCurrent(false);
            return;
        }
        layoutManager.scrollToPositionWithOffset(position, anchorTop - listView.getPaddingTop());
        listView.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                listView.getViewTreeObserver().removeOnPreDrawListener(this);
                for (int i = 0, n = listView.getChildCount(); i < n; i++) {
                    final View child = listView.getChildAt(i);
                    if (!(child instanceof PlayerQueueCell cell) || cell.getMessageObject() == null) {
                        continue;
                    }
                    final Float y = oldY.get(cell.getMessageObject());
                    if (y != null) {
                        cell.setTranslationY(y - cell.getTop());
                        cell.animate().translationY(0).setDuration(REVERSE_DURATION).setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT).start();
                    } else {
                        cell.setAlpha(0f);
                        cell.animate().alpha(1f).setDuration(REVERSE_DURATION).setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT).start();
                    }
                }
                return true;
            }
        });
    }

    public void updateCells() {
        final MessageObject playing = MediaController.getInstance().getPlayingMessageObject();
        for (int i = 0, n = listView.getChildCount(); i < n; i++) {
            final View child = listView.getChildAt(i);
            if (child instanceof PlayerQueueCell cell) {
                final MessageObject messageObject = cell.getMessageObject();
                if (messageObject != null && (messageObject.isVoice() || messageObject.isMusic())) {
                    cell.updateButtonState(false, true);
                }
                cell.setCurrent(isCurrent(messageObject, playing), true);
            }
        }
    }

    private static boolean isCurrent(MessageObject messageObject, MessageObject playing) {
        return messageObject != null && playing != null && (messageObject == playing || messageObject.getId() == playing.getId() && messageObject.getDialogId() == playing.getDialogId());
    }

    public void scrollToCurrent(boolean smooth) {
        final MessageObject playing = MediaController.getInstance().getPlayingMessageObject();
        if (playing == null) {
            return;
        }
        final int position = adapter.positionOf(playing);
        if (position < 0) {
            return;
        }
        if (smooth) {
            listView.smoothScrollToPosition(position);
        } else {
            layoutManager.scrollToPositionWithOffset(position, dp(PlayerQueueCell.HEIGHT_DP) * 2);
        }
    }

    public void clearSearch() {
        if (searchEditText.length() > 0) {
            searchEditText.setText("");
        }
        searchEditText.clearFocus();
        AndroidUtilities.hideKeyboard(searchEditText);
    }

    public boolean isSearchFocused() {
        return searchEditText.isFocused();
    }

    private void updateEmpty() {
        final boolean empty = searchWas && searchResult.isEmpty();
        emptyView.setVisibility(empty ? VISIBLE : GONE);
        if (empty) {
            emptyView.setText(AndroidUtilities.replaceTags(LocaleController.formatString(R.string.NoAudioFoundPlayerInfo, searchQuery)));
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private void search(String query) {
        if (searchRunnable != null) {
            Utilities.searchQueue.cancelRunnable(searchRunnable);
            searchRunnable = null;
        }
        if (query == null) {
            searchWas = false;
            searchQuery = null;
            searchResult = new ArrayList<>();
            adapter.notifyDataSetChanged();
            updateEmpty();
            return;
        }
        final ArrayList<MessageObject> copy = new ArrayList<>(playlist);
        final Runnable runnable = new Runnable() {
            @Override
            public void run() {
                final String search1 = query.trim().toLowerCase(Locale.getDefault());
                String search2 = LocaleController.getInstance().getTranslitString(search1);
                if (search1.equals(search2) || search2 == null || search2.isEmpty()) {
                    search2 = null;
                }
                final String[] terms = search2 != null ? new String[]{search1, search2} : new String[]{search1};
                final ArrayList<MessageObject> result = new ArrayList<>();
                for (MessageObject messageObject : copy) {
                    if (matches(messageObject, terms)) {
                        result.add(messageObject);
                    }
                }
                final Runnable self = this;
                AndroidUtilities.runOnUIThread(() -> {
                    if (searchRunnable != self) {
                        return;
                    }
                    searchRunnable = null;
                    searchWas = true;
                    searchQuery = query;
                    searchResult = result;
                    adapter.notifyDataSetChanged();
                    layoutManager.scrollToPosition(0);
                    updateEmpty();
                });
            }
        };
        searchRunnable = runnable;
        Utilities.searchQueue.postRunnable(runnable, 300);
    }

    private static boolean matches(MessageObject messageObject, String[] terms) {
        for (String q : terms) {
            final String name = messageObject.getDocumentName();
            if (!TextUtils.isEmpty(name) && name.toLowerCase(Locale.getDefault()).contains(q)) {
                return true;
            }
            final TLRPC.Document document = messageObject.getDocument();
            if (document == null) {
                continue;
            }
            for (int c = 0; c < document.attributes.size(); c++) {
                final TLRPC.DocumentAttribute attribute = document.attributes.get(c);
                if (attribute instanceof TLRPC.TL_documentAttributeAudio) {
                    if (attribute.performer != null && attribute.performer.toLowerCase().contains(q)
                            || attribute.title != null && attribute.title.toLowerCase().contains(q)) {
                        return true;
                    }
                    break;
                }
            }
        }
        return false;
    }

    private class Adapter extends RecyclerListView.SelectionAdapter {

        private final Context context;

        Adapter(Context context) {
            this.context = context;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return true;
        }

        @Override
        public int getItemCount() {
            return searchWas ? searchResult.size() : playlist.size();
        }

        int positionOf(MessageObject messageObject) {
            if (searchWas) {
                return searchResult.indexOf(messageObject);
            }
            final int index = playlist.indexOf(messageObject);
            if (index < 0) {
                return -1;
            }
            return isDirectOrder() ? index : playlist.size() - index - 1;
        }

        MessageObject getItem(int position) {
            if (searchWas) {
                return position >= 0 && position < searchResult.size() ? searchResult.get(position) : null;
            }
            if (position < 0 || position >= playlist.size()) {
                return null;
            }
            return isDirectOrder() ? playlist.get(position) : playlist.get(playlist.size() - position - 1);
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            final PlayerQueueCell cell = new PlayerQueueCell(context, currentAccount);
            cell.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(cell);
        }

        @SuppressLint("ClickableViewAccessibility")
        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            final PlayerQueueCell cell = (PlayerQueueCell) holder.itemView;
            final MessageObject messageObject = getItem(position);
            if (messageObject == null) {
                return;
            }
            cell.animate().cancel();
            cell.setTranslationY(0);
            cell.setAlpha(1f);
            messageObject.setQuery(searchQuery);
            final boolean myList = delegate != null && delegate.isMyList();
            final boolean noForwards = delegate != null && delegate.isNoForwards();
            final boolean reorder = myList && !searchWas;
            final View.OnTouchListener onReorderTouch = reorder ? (v, e) -> {
                if (e.getAction() == MotionEvent.ACTION_DOWN && itemTouchHelper != null) {
                    itemTouchHelper.startDrag(listView.getChildViewHolder(cell));
                }
                return false;
            } : null;
            final boolean optionsAllowed = !myList && !noForwards && !messageObject.isVoice() && messageObject.getId() > 0;
            cell.setSideInset(sideInset);
            if (colors != null) {
                cell.setColors(colors);
            }
            cell.setGlobalSearch(MediaController.getInstance().currentPlaylistIsGlobalSearch());
            cell.setMessageObject(messageObject, reorder, optionsAllowed ? v -> {
                if (delegate != null) {
                    delegate.onShowOptions(cell, messageObject);
                }
            } : null, onReorderTouch);
            cell.setCurrent(isCurrent(messageObject, MediaController.getInstance().getPlayingMessageObject()), false);
        }

        @Override
        public void onViewAttachedToWindow(@NonNull RecyclerView.ViewHolder holder) {
            if (holder.itemView instanceof PlayerQueueCell cell && cell.getMessageObject() != null) {
                if (colors != null) {
                    cell.setColors(colors);
                }
                cell.updateButtonState(false, false);
                cell.setCurrent(isCurrent(cell.getMessageObject(), MediaController.getInstance().getPlayingMessageObject()), false);
            }
        }
    }
}
