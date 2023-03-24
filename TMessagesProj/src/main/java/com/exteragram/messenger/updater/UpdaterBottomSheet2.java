package com.exteragram.messenger.updater;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.exteragram.messenger.ExteraConfig;
import com.exteragram.messenger.ExteraUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.CheckBoxUserCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Components.AnimatedTextView;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RLottieImageView;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.ContentPreviewViewer;

public class UpdaterBottomSheet2 extends BottomSheet {

    private RecyclerListView listView;
    private ListAdapter adapter;
    private final Drawable shadowDrawable;
    private BottomSheet.BottomSheetCell saveButton;

    private int scrollOffsetY;
    private boolean ignoreLayout;

    private int rowCount;
    private int headerRow;

    private int versionRow;
    private int sizeRow;
    private int changelogRow;
    private int changelogTextRow;

    private int currentVersionRow;
    private int buildTypeRow;
    private int checkOnLaunchRow;
    private int clearCacheRow;

    private String[] args;
    private boolean available;
    private BaseFragment fragment;

    public UpdaterBottomSheet2(Context context, BaseFragment fragment, boolean available, String... args) {
        super(context, false);
        fixNavigationBar();
        this.args = args;
        this.available = available;
        this.fragment = fragment;

        rowCount = 1;
        // rows++

        shadowDrawable = context.getResources().getDrawable(R.drawable.sheet_shadow_round).mutate();
        shadowDrawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_dialogBackground), PorterDuff.Mode.MULTIPLY));

        containerView = new FrameLayout(context) {

            @Override
            public boolean onInterceptTouchEvent(MotionEvent ev) {
                if (ev.getAction() == MotionEvent.ACTION_DOWN && scrollOffsetY != 0 && ev.getY() < scrollOffsetY) {
                    dismiss();
                    return true;
                }
                return super.onInterceptTouchEvent(ev);
            }

            @Override
            public boolean onTouchEvent(MotionEvent e) {
                return !isDismissed() && super.onTouchEvent(e);
            }

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int height = MeasureSpec.getSize(heightMeasureSpec);
                height -= AndroidUtilities.statusBarHeight;
                int contentSize = AndroidUtilities.dp(48) + backgroundPaddingTop + AndroidUtilities.dp(17);
                contentSize += (rowCount + 1) * AndroidUtilities.dp(48) + AndroidUtilities.dp(20);

                int padding = contentSize < (height / 5 * 3.2f) ? 0 : (height / 5 * 2);
                if (padding != 0 && contentSize < height) {
                    padding -= (height - contentSize);
                }
                if (padding == 0) {
                    padding = backgroundPaddingTop;
                }
                if (listView.getPaddingTop() != padding) {
                    ignoreLayout = true;
                    listView.setPadding(0, padding, 0, 0);
                    ignoreLayout = false;
                }
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(Math.min(contentSize, height), MeasureSpec.EXACTLY));
            }

            @Override
            protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                super.onLayout(changed, left, top, right, bottom);
                updateLayout();
            }

            @Override
            public void requestLayout() {
                if (ignoreLayout) {
                    return;
                }
                super.requestLayout();
            }

            @Override
            protected void onDraw(Canvas canvas) {
                shadowDrawable.setBounds(0, scrollOffsetY - backgroundPaddingTop, getMeasuredWidth(), getMeasuredHeight());
                shadowDrawable.draw(canvas);
            }
        };
        containerView.setWillNotDraw(false);
        containerView.setPadding(backgroundPaddingLeft, 0, backgroundPaddingLeft, 0);

        listView = new RecyclerListView(context) {
            @Override
            public boolean onInterceptTouchEvent(MotionEvent event) {
                boolean result = ContentPreviewViewer.getInstance().onInterceptTouchEvent(event, listView, 0, null, resourcesProvider);
                return super.onInterceptTouchEvent(event) || result;
            }

            @Override
            public void requestLayout() {
                if (ignoreLayout) {
                    return;
                }
                super.requestLayout();
            }
        };
        listView.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.VERTICAL, false));
        listView.setAdapter(adapter = new ListAdapter(context));
        listView.setVerticalScrollBarEnabled(false);
        listView.setClipToPadding(false);
        listView.setEnabled(true);
        listView.setGlowColor(Theme.getColor(Theme.key_dialogScrollGlow));
        listView.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                updateLayout();
            }
        });
        listView.setOnItemClickListener((view, position) -> {
            //
        });
        containerView.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT, 0, 0, 0, 48));

        View shadow = new View(context) {
            @Override
            protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                if (!ExteraConfig.disableDividers)
                    canvas.drawLine(0, getMeasuredHeight() - 1, getMeasuredWidth(), getMeasuredHeight() - 1, Theme.dividerPaint);
            }
        };
        containerView.addView(shadow, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 1, Gravity.BOTTOM | Gravity.LEFT, 0, 0, 0, 48));

        saveButton = new BottomSheet.BottomSheetCell(context, 1);
        saveButton.setBackgroundDrawable(Theme.getSelectorDrawable(false));
        saveButton.setTextAndIcon(LocaleController.getString("Save", R.string.Save).toUpperCase(), 0);
        saveButton.setTextColor(Theme.getColor(Theme.key_dialogTextBlue2));
        saveButton.setOnClickListener(v -> {
            dismiss();
        });
        containerView.addView(saveButton, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.LEFT | Gravity.BOTTOM));

        adapter.notifyDataSetChanged();
    }

    @Override
    protected boolean canDismissWithSwipe() {
        return false;
    }

    @SuppressLint("NewApi")
    private void updateLayout() {
        if (listView.getChildCount() <= 0) {
            listView.setTopGlowOffset(scrollOffsetY = listView.getPaddingTop());
            containerView.invalidate();
            return;
        }
        View child = listView.getChildAt(0);
        RecyclerListView.Holder holder = (RecyclerListView.Holder) listView.findContainingViewHolder(child);
        int top = child.getTop() - AndroidUtilities.dp(8);
        int newOffset = top > 0 && holder != null && holder.getAdapterPosition() == 0 ? top : 0;
        if (scrollOffsetY != newOffset) {
            listView.setTopGlowOffset(scrollOffsetY = newOffset);
            containerView.invalidate();
        }
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {

        private final Context context;

        public ListAdapter(Context context) {
            this.context = context;
        }

        @Override
        public int getItemCount() {
            return rowCount;
        }

        @Override
        public int getItemViewType(int position) {
            if (position == headerRow) {
                return 2;
            } else if (position == versionRow || position == sizeRow || position == currentVersionRow || position == buildTypeRow) {
                return 1;
            } else {
                return 0;
            }
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return holder.getItemViewType() != 1;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            FrameLayout view = null;
            switch (viewType) {
                case 0:
                    view = new TextCell(context, 23, false, true, resourcesProvider);
                    break;
                case 1:
                    ShadowSectionCell shadowSectionCell = new ShadowSectionCell(context, 18);
                    view = new FrameLayout(context);
                    view.addView(shadowSectionCell, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
                    view.setBackgroundColor(Theme.getColor(Theme.key_dialogBackgroundGray));
                    break;
                case 2:
                    UpdaterHeaderCell updaterHeaderCell = new UpdaterHeaderCell(context);
                    view = new FrameLayout(context);
                    view.addView(updaterHeaderCell, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
                    break;
            }

            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onViewAttachedToWindow(RecyclerView.ViewHolder holder) {
            int position = holder.getAdapterPosition();
            switch (holder.getItemViewType()) {
                case 2: {
                    CheckBoxUserCell userCell = (CheckBoxUserCell) holder.itemView;
                    break;
                }
            }
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case 0: {
                    TextCell cell = (TextCell) holder.itemView;

                    break;
                }
                case 2: {
                    CheckBoxUserCell userCell = (CheckBoxUserCell) holder.itemView;
                    break;
                }
            }
        }
    }

    private class UpdaterHeaderCell extends FrameLayout {
        private UpdaterHeaderCell(Context context) {
            super(context);

            if (available) {
                RLottieImageView imageView = new RLottieImageView(context);
                imageView.setOnClickListener(v -> {
                    if (!imageView.isPlaying() && imageView.getAnimatedDrawable() != null) {
                        imageView.getAnimatedDrawable().setCurrentFrame(0);
                        imageView.playAnimation();
                    }
                });
                imageView.setAnimation(R.raw.etg_raccoon, 60, 60, new int[]{0x000000, 0x000000});
                imageView.setScaleType(ImageView.ScaleType.CENTER);
                addView(imageView, LayoutHelper.createFrame(60, 60, Gravity.LEFT | Gravity.CENTER_VERTICAL));
            }

            SimpleTextView nameView = new SimpleTextView(context);
            nameView.setTextSize(20);
            nameView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            nameView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            nameView.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
            nameView.setText(available ? LocaleController.getString("UpdateAvailable", R.string.UpdateAvailable) : ExteraUtils.getAppName());
            addView(nameView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 30, Gravity.LEFT, available ? 75 : 0, 5, 0, 0));

            AnimatedTextView timeView = new AnimatedTextView(context, true, true, false);
            timeView.setAnimationProperties(0.7f, 0, 450, CubicBezierInterpolator.EASE_OUT_QUINT);
            timeView.setIgnoreRTL(!LocaleController.isRTL);
            timeView.adaptWidth = false;
            timeView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            timeView.setTextSize(AndroidUtilities.dp(13));
            timeView.setTypeface(AndroidUtilities.getTypeface("fonts/rregular.ttf"));
            timeView.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
            timeView.setText(available ? args[4] : LocaleController.getString("LastCheck", R.string.LastCheck) + ": " + LocaleController.formatDateTime(ExteraConfig.lastUpdateCheckTime / 1000));
            addView(timeView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 20, Gravity.LEFT, available ? 75 : 0, 35, 0, 0));

        }
    }
}
