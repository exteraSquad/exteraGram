/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.drawer;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import androidx.annotation.NonNull;
import androidx.core.content.res.ResourcesCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.exteragram.messenger.utils.ui.AccountsUiHelper;
import com.exteragram.messenger.ExteraConfig;
import com.exteragram.messenger.api.dto.BadgeDTO;
import com.exteragram.messenger.badges.BadgesController;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.utils.ViewOutlineProviderImpl;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.CombinedDrawable;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.Premium.PremiumGradient;

import java.util.ArrayList;
import java.util.Collections;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

public class DrawerAccountPickerView extends FrameLayout {
    private static final String SETTINGS_KEY_ACCOUNTS_SHOWN = "accountsShown";

    private static final int CONTAINER_SIDE_MARGIN_DP = 12;
    private static final int CONTAINER_CORNER_RADIUS_DP = 16;
    private static final int CONTAINER_HORIZONTAL_INSET_DP = CONTAINER_SIDE_MARGIN_DP * 2;
    private static final int ITEM_INSET_DP = 4;
    private static final int ITEM_RADIUS_DP = CONTAINER_CORNER_RADIUS_DP - ITEM_INSET_DP;
    private static final int ROW_HEIGHT_DP = 44;
    private static final int AVATAR_SIZE_DP = 34;
    private static final int AVATAR_TEXT_SIZE_DP = 20;
    private static final int ROW_TEXT_SIZE_DP = 15;
    private static final int ROW_SIDE_MARGIN_DP = 8;
    private static final int NAME_GAP_DP = 12;
    private static final int NAME_START_MARGIN_DP = ROW_SIDE_MARGIN_DP + AVATAR_SIZE_DP + NAME_GAP_DP;
    private static final int NAME_END_MARGIN_DP = 12;
    private static final int STATUS_DRAWABLE_SIZE_DP = 18;
    private static final float SELECTED_ACCOUNT_STROKE_WIDTH_DP = 1.67f;
    private static final float SELECTED_ACCOUNT_SCALE = 0.785f;
    private static final long ACTION_DELAY_MS = 150L;
    private static final long EXPAND_ANIMATION_DURATION_MS = 250L;
    private static final float MAX_VISIBLE_ITEMS = 5.5f;
    private static final int FADING_EDGE_LENGTH_DP = 16;

    private static final int COLOR_KEY_BACKGROUND = Theme.key_windowBackgroundGray;
    private static final int COLOR_KEY_SELECTOR = Theme.key_listSelector;
    private static final int COLOR_KEY_SURFACE = Theme.key_windowBackgroundWhite;
    private static final int COLOR_KEY_TEXT = Theme.key_windowBackgroundWhiteBlackText;
    private static final int COLOR_KEY_STATUS = Theme.key_profile_verifiedBackground;
    private static final int COLOR_KEY_ACCENT = Theme.key_featuredStickers_addButton;
    private static final int COLOR_KEY_ADD_ICON = Theme.key_featuredStickers_buttonText;

    private final FrameLayout clipWrapper;
    private final RecyclerView recyclerView;
    private final AccountAdapter adapter;
    private final ArrayList<Integer> accounts = new ArrayList<>();
    private boolean expanded;
    private ValueAnimator expandAnimator;
    private Runnable onAccountSelected;
    private OnAccountLongClick onAccountLongClick;
    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint clipMaskPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bgRect = new RectF();
    private final float cornerRadius = dp(CONTAINER_CORNER_RADIUS_DP);
    private final ItemTouchHelper itemTouchHelper;
    private View draggingItemView;
    private BadgeDTO badgeOverride;

    private final Paint topGradientPaint = new Paint();
    private final Paint bottomGradientPaint = new Paint();
    private LinearGradient topGradient;
    private LinearGradient bottomGradient;
    private int lastHeight;
    private int currentAnimatedHeight = -1;

    @FunctionalInterface
    public interface OnAccountLongClick {
        void onLongClick(int account, View view);
    }

    public DrawerAccountPickerView(Context context) {
        super(context);

        expanded = MessagesController.getGlobalMainSettings().getBoolean(SETTINGS_KEY_ACCOUNTS_SHOWN, true);
        clipMaskPaint.setStyle(Paint.Style.FILL);
        clipMaskPaint.setColor(0xFF000000);
        clipMaskPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));

        topGradientPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
        bottomGradientPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));

        clipWrapper = new FrameLayout(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int itemCount = adapter.getItemCount();
                float visibleItems = itemCount <= 6 ? itemCount : MAX_VISIBLE_ITEMS;
                int maxHeight = (int) (dp(ROW_HEIGHT_DP + ITEM_INSET_DP) * visibleItems + dp(ITEM_INSET_DP) * 2);
                int widthSize = MeasureSpec.getSize(widthMeasureSpec);
                measureChildren(MeasureSpec.makeMeasureSpec(widthSize, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(maxHeight, MeasureSpec.EXACTLY));

                if (currentAnimatedHeight >= 0) {
                    setMeasuredDimension(widthSize, currentAnimatedHeight);
                } else {
                    int size = MeasureSpec.getSize(heightMeasureSpec);
                    if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED || size > maxHeight) {
                        heightMeasureSpec = MeasureSpec.makeMeasureSpec(maxHeight, MeasureSpec.AT_MOST);
                    }
                    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                }
            }

            @Override
            protected void dispatchDraw(@NonNull Canvas canvas) {
                bgPaint.setColor(Theme.getColor(COLOR_KEY_BACKGROUND));
                bgRect.set(0, 0, getWidth(), getHeight());
                canvas.drawRoundRect(bgRect, cornerRadius, cornerRadius, bgPaint);

                int saveCount = canvas.saveLayer(0, 0, getWidth(), getHeight(), null);
                super.dispatchDraw(canvas);

                if (topGradient == null || getHeight() != lastHeight) {
                    lastHeight = getHeight();
                    topGradient = new LinearGradient(0, 0, 0, dp(FADING_EDGE_LENGTH_DP), new int[]{0xFF000000, 0x00000000}, null, Shader.TileMode.CLAMP);
                    topGradientPaint.setShader(topGradient);

                    bottomGradient = new LinearGradient(0, getHeight(), 0, getHeight() - dp(FADING_EDGE_LENGTH_DP), new int[]{0xFF000000, 0x00000000}, null, Shader.TileMode.CLAMP);
                    bottomGradientPaint.setShader(bottomGradient);
                }

                int fadeLength = dp(FADING_EDGE_LENGTH_DP);
                int offset = recyclerView.computeVerticalScrollOffset();
                int remaining = Math.max(0, recyclerView.computeVerticalScrollRange() - recyclerView.computeVerticalScrollExtent() - offset);

                float topStrength = Math.min(1f, Math.max(0f, offset / (float) fadeLength));
                float bottomStrength = Math.min(1f, remaining / (float) fadeLength);

                if (topStrength > 0f) {
                    topGradientPaint.setAlpha((int) (255 * topStrength));
                    canvas.drawRect(0, 0, getWidth(), dp(FADING_EDGE_LENGTH_DP), topGradientPaint);
                }
                if (bottomStrength > 0f) {
                    bottomGradientPaint.setAlpha((int) (255 * bottomStrength));
                    canvas.drawRect(0, getHeight() - dp(FADING_EDGE_LENGTH_DP), getWidth(), getHeight(), bottomGradientPaint);
                }

                canvas.drawRoundRect(bgRect, cornerRadius, cornerRadius, clipMaskPaint);

                canvas.restoreToCount(saveCount);
            }
        };
        clipWrapper.setOutlineProvider(ViewOutlineProviderImpl.boundsWithRoundRect(cornerRadius));
        clipWrapper.setClipToOutline(true);
        addView(clipWrapper, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 0, Gravity.TOP, CONTAINER_SIDE_MARGIN_DP, 0, CONTAINER_SIDE_MARGIN_DP, 0));

        adapter = new AccountAdapter();
        recyclerView = new RecyclerView(context);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        recyclerView.setAdapter(adapter);
        var itemInset = dp(ITEM_INSET_DP);
        recyclerView.setPadding(itemInset, itemInset, itemInset, itemInset);
        recyclerView.setClipToPadding(false);
        recyclerView.addItemDecoration(new RecyclerView.ItemDecoration() {
            @Override
            public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
                int position = parent.getChildAdapterPosition(view);
                int itemCount = state.getItemCount();
                if (position >= 0 && position < itemCount - 1) {
                    outRect.bottom = itemInset;
                }
            }
        });
        recyclerView.setOverScrollMode(OVER_SCROLL_NEVER);
        recyclerView.setVerticalScrollBarEnabled(false);
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                clipWrapper.invalidate();
            }
        });
        clipWrapper.addView(recyclerView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        RecyclerView.ChildDrawingOrderCallback dragDrawingOrderCallback = (childCount, drawingPosition) -> {
            if (draggingItemView == null) {
                return drawingPosition;
            }
            int draggedChildPosition = recyclerView.indexOfChild(draggingItemView);
            if (draggedChildPosition < 0) {
                return drawingPosition;
            }
            if (drawingPosition == childCount - 1) {
                return draggedChildPosition;
            }
            if (drawingPosition < draggedChildPosition) {
                return drawingPosition;
            }
            return drawingPosition + 1;
        };

        ItemTouchHelper.Callback touchCallback = new ItemTouchHelper.Callback() {
            @Override
            public int getMovementFlags(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
                if (viewHolder.getAdapterPosition() >= accounts.size()) return 0;
                return makeMovementFlags(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0);
            }

            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder source, @NonNull RecyclerView.ViewHolder target) {
                int from = source.getAdapterPosition();
                int to = target.getAdapterPosition();
                if (from >= accounts.size() || to >= accounts.size()) return false;
                adapter.swapElements(from, to);
                return true;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
            }

            @Override
            public boolean isLongPressDragEnabled() {
                return false;
            }

            @Override
            public void onSelectedChanged(RecyclerView.ViewHolder viewHolder, int actionState) {
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG && viewHolder != null) {
                    draggingItemView = viewHolder.itemView;
                    draggingItemView.setPressed(false);
                    draggingItemView.jumpDrawablesToCurrentState();
                    recyclerView.setChildDrawingOrderCallback(dragDrawingOrderCallback);
                    recyclerView.invalidate();
                }
            }

            @Override
            public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, float dX, float dY, int actionState, boolean isCurrentlyActive) {
                viewHolder.itemView.setTranslationX(dX);
                viewHolder.itemView.setTranslationY(dY);
            }

            @Override
            public void clearView(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
                viewHolder.itemView.setTranslationX(0f);
                viewHolder.itemView.setTranslationY(0f);
                viewHolder.itemView.setPressed(false);
                if (draggingItemView == viewHolder.itemView) {
                    draggingItemView = null;
                }
                recyclerView.setChildDrawingOrderCallback(null);
                recyclerView.invalidate();
            }
        };
        itemTouchHelper = new ItemTouchHelper(touchCallback);
        itemTouchHelper.attachToRecyclerView(recyclerView);

        if (expanded) {
            loadAccounts();
            setVisibility(VISIBLE);
            ViewGroup.LayoutParams layoutParams = clipWrapper.getLayoutParams();
            layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            clipWrapper.setLayoutParams(layoutParams);
        } else {
            setVisibility(GONE);
        }
    }

    public void setOnAccountSelected(Runnable callback) {
        this.onAccountSelected = callback;
    }

    public void setOnAccountLongClick(OnAccountLongClick callback) {
        this.onAccountLongClick = callback;
    }


    public void loadAccounts() {
        loadAccounts(null);
    }

    public void loadAccounts(BadgeDTO override) {
        this.badgeOverride = override;
        accounts.clear();
        accounts.addAll(AccountsUiHelper.activated());
        adapter.notifyDataSetChanged();
    }

    public void toggleExpand() {
        setExpanded(!expanded);
    }

    public boolean isExpanded() {
        return expanded;
    }

    public void setExpanded(boolean expand) {
        if (expanded == expand) {
            return;
        }
        expanded = expand;
        MessagesController.getGlobalMainSettings().edit().putBoolean(SETTINGS_KEY_ACCOUNTS_SHOWN, expand).apply();

        if (expand) {
            loadAccounts();
            setVisibility(VISIBLE);
        }

        if (expandAnimator != null) {
            expandAnimator.cancel();
        }

        int currentHeight;
        if (currentAnimatedHeight >= 0) {
            currentHeight = currentAnimatedHeight;
        } else {
            currentHeight = clipWrapper.getLayoutParams().height;
            if (currentHeight < 0) {
                currentHeight = clipWrapper.getHeight();
            }
            if (currentHeight < 0) {
                currentHeight = expand ? 0 : clipWrapper.getMeasuredHeight();
            }
        }

        currentAnimatedHeight = -1;

        int itemCount = adapter.getItemCount();
        float visibleItems = itemCount <= 6 ? itemCount : MAX_VISIBLE_ITEMS;
        int maxHeight = (int) (dp(ROW_HEIGHT_DP + ITEM_INSET_DP) * visibleItems + dp(ITEM_INSET_DP) * 2);

        clipWrapper.measure(
                MeasureSpec.makeMeasureSpec(((View) getParent()).getMeasuredWidth() - dp(CONTAINER_HORIZONTAL_INSET_DP), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(maxHeight, MeasureSpec.AT_MOST)
        );
        int targetHeight = expand ? clipWrapper.getMeasuredHeight() : 0;

        currentAnimatedHeight = currentHeight;

        var animator = ValueAnimator.ofInt(currentHeight, targetHeight);
        expandAnimator = animator;
        animator.setDuration(EXPAND_ANIMATION_DURATION_MS);
        animator.setInterpolator(CubicBezierInterpolator.DEFAULT);
        animator.addUpdateListener(anim -> {
            currentAnimatedHeight = (int) anim.getAnimatedValue();
            clipWrapper.requestLayout();
        });
        animator.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled;

            @Override
            public void onAnimationCancel(Animator animation) {
                cancelled = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (cancelled || expandAnimator != animation) {
                    return;
                }
                expandAnimator = null;
                currentAnimatedHeight = -1;
                if (!expanded) {
                    setVisibility(GONE);
                } else {
                    var layoutParams = clipWrapper.getLayoutParams();
                    layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                    clipWrapper.setLayoutParams(layoutParams);
                }
            }
        });
        animator.start();
    }

    public void updateColors() {
        bgPaint.setColor(Theme.getColor(COLOR_KEY_BACKGROUND));
        invalidate();
        adapter.notifyDataSetChanged();
    }

    public void updateUnreadCounters() {
        if (recyclerView.getChildCount() == 0) {
            return;
        }
        for (int i = 0; i < recyclerView.getChildCount(); i++) {
            var child = recyclerView.getChildAt(i);
            if (child instanceof AccountRowView rowView) {
                rowView.updateUnreadCounter();
            }
        }
    }

    public void dispose() {
        if (expandAnimator != null) {
            expandAnimator.cancel();
            expandAnimator = null;
        }
        draggingItemView = null;
        recyclerView.setChildDrawingOrderCallback(null);
        recyclerView.stopScroll();
    }

    private boolean canAddAccount() {
        return AccountsUiHelper.freeSlotWithinLimit() != null;
    }

    private static Drawable createAccountItemRippleDrawable() {
        return Theme.createRadSelectorDrawable(
                Theme.getColor(COLOR_KEY_SELECTOR),
                ITEM_RADIUS_DP,
                ITEM_RADIUS_DP
        );
    }

    private static Drawable createSelectedAccountBackgroundDrawable() {
        return Theme.createSimpleSelectorRoundRectDrawable(
                dp(ITEM_RADIUS_DP),
                Theme.getColor(COLOR_KEY_SURFACE),
                Theme.getColor(COLOR_KEY_SELECTOR)
        );
    }

    private class AccountAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private static final int TYPE_ACCOUNT = 0;
        private static final int TYPE_ADD = 1;

        @Override
        public int getItemViewType(int position) {
            return position < accounts.size() ? TYPE_ACCOUNT : TYPE_ADD;
        }

        @Override
        public int getItemCount() {
            return accounts.size() + (canAddAccount() ? 1 : 0);
        }

        public void swapElements(int fromIndex, int toIndex) {
            if (fromIndex < 0 || toIndex < 0 || fromIndex >= accounts.size() || toIndex >= accounts.size()) {
                return;
            }
            var userConfig1 = UserConfig.getInstance(accounts.get(fromIndex));
            var userConfig2 = UserConfig.getInstance(accounts.get(toIndex));
            var tempLoginTime = userConfig1.loginTime;
            userConfig1.loginTime = userConfig2.loginTime;
            userConfig2.loginTime = tempLoginTime;
            userConfig1.saveConfig(false);
            userConfig2.saveConfig(false);
            Collections.swap(accounts, fromIndex, toIndex);
            notifyItemMoved(fromIndex, toIndex);
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return switch (viewType) {
                case TYPE_ADD -> new RecyclerView.ViewHolder(new AddAccountView(parent.getContext())) {
                };
                default -> new RecyclerView.ViewHolder(new AccountRowView(parent.getContext())) {
                };
            };
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            switch (getItemViewType(position)) {
                case TYPE_ACCOUNT -> bindAccountViewHolder(holder, position);
                case TYPE_ADD -> bindAddAccountViewHolder(holder);
            }
        }

        private void bindAccountViewHolder(RecyclerView.ViewHolder holder, int position) {
            if (!(holder.itemView instanceof AccountRowView view)) {
                return;
            }
            var account = accounts.get(position);
            view.bind(account, account == UserConfig.selectedAccount ? badgeOverride : null);
            view.setOnClickListener(v -> {
                if (account != UserConfig.selectedAccount) {
                    if (onAccountSelected != null) {
                        onAccountSelected.run();
                    }
                    AccountsUiHelper.switchTo(account);
                }
            });
            view.setOnLongClickListener(v -> {
                if (account == UserConfig.selectedAccount) {
                    itemTouchHelper.startDrag(holder);
                } else if (onAccountLongClick != null) {
                    onAccountLongClick.onLongClick(account, v);
                }
                return true;
            });
        }

        private void bindAddAccountViewHolder(RecyclerView.ViewHolder holder) {
            if (holder.itemView instanceof AddAccountView addAccountView) {
                addAccountView.updateColors();
            }
            holder.itemView.setOnClickListener(v -> {
                if (onAccountSelected != null) {
                    onAccountSelected.run();
                }
                AndroidUtilities.runOnUIThread(AccountsUiHelper::add, ACTION_DELAY_MS);
            });
        }
    }

    private static class AccountRowView extends FrameLayout {
        private final BackupImageView avatarView;
        private final SimpleTextView nameView;
        private final AvatarDrawable avatarDrawable;
        private final AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable premiumStatusDrawable;
        private final AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable exteraBadgeDrawable;
        private final DrawerAccountUnreadBadge unreadBadge;
        private final Paint checkPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF avatarRect = new RectF();
        private boolean selected;

        AccountRowView(Context context) {
            super(context);
            setWillNotDraw(false);
            setLayoutParams(new RecyclerView.LayoutParams(LayoutParams.MATCH_PARENT, dp(ROW_HEIGHT_DP)));
            setBackground(createAccountItemRippleDrawable());

            avatarDrawable = new AvatarDrawable();
            avatarDrawable.setTextSize(dp(AVATAR_TEXT_SIZE_DP));

            avatarView = new BackupImageView(context);
            updateAvatarRadius();
            addView(avatarView, LayoutHelper.createFrame(AVATAR_SIZE_DP, AVATAR_SIZE_DP, Gravity.CENTER_VERTICAL | Gravity.LEFT, ROW_SIDE_MARGIN_DP, 0, 0, 0));

            nameView = new SimpleTextView(context);
            nameView.setTextSize(ROW_TEXT_SIZE_DP);
            nameView.setTypeface(AndroidUtilities.bold());
            nameView.setTextColor(Theme.getColor(COLOR_KEY_TEXT));
            nameView.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
            nameView.setEllipsizeByGradient(true);
            nameView.setCanHideRightDrawable(false);
            nameView.setRightDrawableOutside(true);
            addView(nameView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT, NAME_START_MARGIN_DP, 0, NAME_END_MARGIN_DP, 0));

            premiumStatusDrawable = new AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable(nameView, dp(STATUS_DRAWABLE_SIZE_DP));
            exteraBadgeDrawable = new AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable(nameView, dp(STATUS_DRAWABLE_SIZE_DP));
            unreadBadge = new DrawerAccountUnreadBadge();

            checkPaint.setStyle(Paint.Style.STROKE);
            checkPaint.setStrokeWidth(dp(SELECTED_ACCOUNT_STROKE_WIDTH_DP));
            checkPaint.setStrokeCap(Paint.Cap.ROUND);
            checkPaint.setStrokeJoin(Paint.Join.ROUND);
        }

        void bind(int account, BadgeDTO badgeOverride) {
            var user = UserConfig.getInstance(account).getCurrentUser();
            if (user == null) {
                return;
            }
            updateAvatarRadius();
            avatarDrawable.setInfo(account, user);
            nameView.setTextColor(Theme.getColor(COLOR_KEY_TEXT));
            nameView.setText(ContactsController.formatName(user.first_name, user.last_name));
            avatarView.getImageReceiver().setCurrentAccount(account);
            avatarView.setForUserOrChat(user, avatarDrawable);
            premiumStatusDrawable.setCurrentAccount(account);
            exteraBadgeDrawable.setCurrentAccount(account);

            var statusColor = Theme.getColor(COLOR_KEY_STATUS);
            var emojiStatusId = DialogObject.getEmojiStatusDocumentId(user.emoji_status);
            var isPremium = MessagesController.getInstance(account).isPremiumUser(user);
            var badge = badgeOverride != null ? badgeOverride : BadgesController.INSTANCE.getBadge(user);

            Drawable primaryDrawable = null;
            Drawable secondaryDrawable = null;
            if (emojiStatusId != 0) {
                premiumStatusDrawable.set(emojiStatusId, false);
                primaryDrawable = premiumStatusDrawable;
            } else if (isPremium) {
                premiumStatusDrawable.set(PremiumGradient.getInstance().premiumStarDrawableMini, false);
                primaryDrawable = premiumStatusDrawable;
            } else {
                premiumStatusDrawable.set((Drawable) null, false);
            }
            premiumStatusDrawable.setColor(statusColor);
            premiumStatusDrawable.setParticles(DialogObject.isEmojiStatusCollectible(user.emoji_status), false);

            Drawable badgeDrawable = updateBadgeDrawable(badge, statusColor);
            if (badgeDrawable != null) {
                if (primaryDrawable != null) {
                    secondaryDrawable = badgeDrawable;
                } else {
                    primaryDrawable = badgeDrawable;
                }
            }

            applyNameDrawables(primaryDrawable, secondaryDrawable);
            unreadBadge.bind(account, nameView);

            checkPaint.setColor(Theme.getColor(COLOR_KEY_ACCENT));

            selected = account == UserConfig.selectedAccount;

            float scale = selected ? SELECTED_ACCOUNT_SCALE : 1.0f;
            avatarView.setScaleX(scale);
            avatarView.setScaleY(scale);

            setBackground(selected ? createSelectedAccountBackgroundDrawable() : createAccountItemRippleDrawable());
            setPadding(0, 0, 0, 0);
            invalidate();
        }

        private void updateAvatarRadius() {
            avatarView.setRoundRadius(ExteraConfig.getAvatarCorners(AVATAR_SIZE_DP));
        }

        void updateUnreadCounter() {
            unreadBadge.update(nameView);
            invalidate();
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            premiumStatusDrawable.attach();
            exteraBadgeDrawable.attach();
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            premiumStatusDrawable.detach();
            exteraBadgeDrawable.detach();
        }

        private Drawable updateBadgeDrawable(BadgeDTO badge, int statusColor) {
            if (badge == null) {
                clearBadgeDrawables();
                return null;
            }

            exteraBadgeDrawable.set(badge.getDocumentId(), false);
            exteraBadgeDrawable.setParticles(true, false);
            exteraBadgeDrawable.setColor(statusColor);
            return exteraBadgeDrawable;
        }

        private void clearBadgeDrawables() {
            exteraBadgeDrawable.set((Drawable) null, false);
            exteraBadgeDrawable.setParticles(false, false);
            exteraBadgeDrawable.setColor(null);
        }

        private void applyNameDrawables(Drawable primaryDrawable, Drawable secondaryDrawable) {
            if (primaryDrawable != null && primaryDrawable == nameView.getRightDrawable2()) {
                nameView.setRightDrawable2(null);
            }
            nameView.setRightDrawable(primaryDrawable);
            nameView.setRightDrawable2(secondaryDrawable);
        }

        @Override
        protected void dispatchDraw(@NonNull Canvas canvas) {
            super.dispatchDraw(canvas);
            unreadBadge.draw(this, canvas);
            if (selected) {
                float halfStroke = checkPaint.getStrokeWidth() / 2f;

                avatarRect.set(
                        avatarView.getLeft() + halfStroke,
                        avatarView.getTop() + halfStroke,
                        avatarView.getRight() - halfStroke,
                        avatarView.getBottom() - halfStroke
                );

                float radius = ExteraConfig.getAvatarCorners(AVATAR_SIZE_DP);

                canvas.drawRoundRect(avatarRect, radius, radius, checkPaint);
            }
        }
    }

    private static class AddAccountView extends LinearLayout {
        private final SimpleTextView textView;

        private final Drawable circleDrawable;
        private final Drawable plusDrawable;

        AddAccountView(Context context) {
            super(context);
            setOrientation(HORIZONTAL);
            setGravity(Gravity.CENTER_VERTICAL);
            setLayoutParams(new RecyclerView.LayoutParams(LayoutParams.MATCH_PARENT, dp(ROW_HEIGHT_DP)));

            ImageView iconView = new ImageView(context);
            iconView.setScaleType(ImageView.ScaleType.CENTER);

            circleDrawable = ResourcesCompat.getDrawable(context.getResources(), R.drawable.poll_add_circle, null);
            plusDrawable = ResourcesCompat.getDrawable(context.getResources(), R.drawable.poll_add_plus, null);

            if (circleDrawable != null) circleDrawable.mutate();
            if (plusDrawable != null) plusDrawable.mutate();

            var combinedDrawable = new CombinedDrawable(circleDrawable, plusDrawable) {
                @Override
                public void setColorFilter(ColorFilter colorFilter) {
                }
            };
            combinedDrawable.setCustomSize(dp(24), dp(24));
            iconView.setImageDrawable(combinedDrawable);

            addView(iconView, LayoutHelper.createLinear(AVATAR_SIZE_DP, AVATAR_SIZE_DP, Gravity.CENTER_VERTICAL, ROW_SIDE_MARGIN_DP, 0, 0, 0));

            textView = new SimpleTextView(context);
            textView.setTextSize(ROW_TEXT_SIZE_DP);
            textView.setTypeface(AndroidUtilities.bold());
            textView.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
            textView.setText(getString(R.string.AddAccount));
            addView(textView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER_VERTICAL, NAME_GAP_DP, 0, NAME_END_MARGIN_DP, 0));

            updateColors();
        }

        void updateColors() {
            setBackground(createAccountItemRippleDrawable());

            if (circleDrawable != null) {
                circleDrawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(COLOR_KEY_ACCENT), PorterDuff.Mode.SRC_IN));
            }
            if (plusDrawable != null) {
                plusDrawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(COLOR_KEY_ADD_ICON), PorterDuff.Mode.SRC_IN));
            }

            textView.setTextColor(Theme.getColor(COLOR_KEY_TEXT));
        }
    }
}
