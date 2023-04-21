package com.exteragram.messenger.updater;

import static org.telegram.messenger.AndroidUtilities.dp;

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
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.exteragram.messenger.ExteraConfig;
import com.exteragram.messenger.ExteraUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Components.AnimatedTextView;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RLottieImageView;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.ContentPreviewViewer;

public class UpdaterBottomSheet2 extends BottomSheet {

    private final RecyclerListView listView;
    private final Drawable shadowDrawable;

    private int scrollOffsetY;
    private boolean ignoreLayout;

    private int rowCount = 0;
    private UpdaterHeaderCell headerCell;
    private TextInfoPrivacyCell changelogCell;

    private final int headerRow;
    private final int versionRow;

    private int sizeRow;
    private int changelogRow;
    private int changelogTextRow;

    private int buildTypeRow;
    private int checkOnLaunchRow;
    private int clearCacheRow;

    private final String[] args;
    private final boolean available;

    private boolean isTranslated = false;
    private CharSequence translatedC;

    @SuppressLint("NotifyDataSetChanged")
    public UpdaterBottomSheet2(Context context, BaseFragment fragment, boolean available, String... args) {
        super(context, false);
        fixNavigationBar();
        this.args = args;
        this.available = available;

        headerRow = rowCount++;
        versionRow = rowCount++;

        sizeRow = -1;
        changelogRow = -1;
        changelogTextRow = -1;

        buildTypeRow = -1;
        checkOnLaunchRow = -1;
        clearCacheRow = -1;

        if (available) {
            sizeRow = rowCount++;
            changelogRow = rowCount++;
            changelogTextRow = rowCount++;
        } else {
            buildTypeRow = rowCount++;
            checkOnLaunchRow = rowCount++;
            clearCacheRow = rowCount++;
        }

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

            @SuppressLint("ClickableViewAccessibility")
            @Override
            public boolean onTouchEvent(MotionEvent e) {
                return !isDismissed() && super.onTouchEvent(e);
            }

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int height = MeasureSpec.getSize(heightMeasureSpec);
                height -= AndroidUtilities.statusBarHeight;
                int contentSize = AndroidUtilities.dp(79) + backgroundPaddingTop + (headerCell != null ? headerCell.getHeight() : 0) - AndroidUtilities.dp(2) + (changelogCell != null ? changelogCell.getHeight() : 0);
                contentSize += (rowCount - (available ? 2 : 1)) * AndroidUtilities.dp(51);

                int padding = contentSize < (height / 5f * 3.2f) ? 0 : (height / 5 * 2);
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
        listView.setPadding(0, AndroidUtilities.statusBarHeight, 0, dp(80));
        listView.setClipToPadding(true);
        listView.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.VERTICAL, false));
        ListAdapter adapter;
        listView.setAdapter(adapter = new ListAdapter(context));
        listView.setVerticalScrollBarEnabled(false);
        DefaultItemAnimator itemAnimator = new DefaultItemAnimator() {
            @Override
            protected void onChangeAnimationUpdate(RecyclerView.ViewHolder holder) {
                updateLayout();
            }

            @Override
            protected void onMoveAnimationUpdate(RecyclerView.ViewHolder holder) {
                updateLayout();
            }
        };
        itemAnimator.setDurations(180);
        itemAnimator.setInterpolator(new LinearInterpolator());
        listView.setItemAnimator(itemAnimator);
        listView.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        listView.setClipToPadding(false);
        listView.setEnabled(true);
        listView.setGlowColor(Theme.getColor(Theme.key_dialogScrollGlow));
        listView.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                updateLayout();
            }
        });
        listView.setOnItemClickListener((view, position) -> {
            if (view instanceof TextCell) {
                TextCell cell = (TextCell) view;
                cell.setEnabled(true);
                if (position == versionRow || position == buildTypeRow || position == sizeRow) {
                    copyText(cell.getTextView().getText() + ": " + cell.getValueTextView().getText());
                } else if (position == checkOnLaunchRow) {
                    ExteraConfig.editor.putBoolean("checkUpdatesOnLaunch", ExteraConfig.checkUpdatesOnLaunch ^= true).apply();
                    cell.setChecked(!cell.getCheckBox().isChecked());
                } else if (position == clearCacheRow) {
                    if (UpdaterUtils.getOtaDirSize().replaceAll("\\D+", "").equals("0")) {
                        BulletinFactory.of(getContainer(), null).createErrorBulletin(LocaleController.getString("NothingToClear", R.string.NothingToClear)).show();
                    } else {
                        BulletinFactory.of(getContainer(), null).createErrorBulletin(LocaleController.formatString("ClearedUpdatesCache", R.string.ClearedUpdatesCache, UpdaterUtils.getOtaDirSize())).show();
                        UpdaterUtils.cleanOtaDir();
                    }
                } else if (position == changelogRow) {
                    copyText(cell.getTextView().getText() + "\n" + args[1]);
                }
            } else if (position == changelogTextRow) {
                TextInfoPrivacyCell cell = (TextInfoPrivacyCell) view;
                ExteraUtils.translate(args[1], LocaleController.getInstance().getCurrentLocale().getLanguage(), translated -> {
                    translatedC = translated;
                    cell.setText(UpdaterUtils.replaceTags(isTranslated ? args[1] : translatedC));
                    adapter.notifyItemChanged(changelogTextRow);
                    isTranslated ^= true;
                }, () -> BulletinFactory.of(getContainer(), null).createErrorBulletin(LocaleController.getString("TranslationFailedAlert1", R.string.TranslationFailedAlert1)).show());
            }

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
        containerView.addView(shadow, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 1, Gravity.BOTTOM | Gravity.LEFT, 0, 0, 0, 48 + 15 + 16));

        FrameLayout checkUpdatesBackground = new BottomSheet.BottomSheetCell(context, 0);
        checkUpdatesBackground.setBackground(Theme.AdaptiveRipple.filledRect(Theme.getColor(Theme.key_featuredStickers_addButton), 6));
        containerView.addView(checkUpdatesBackground, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.LEFT | Gravity.BOTTOM, 16, 15, 16, 16));

        AnimatedTextView checkUpdates = new AnimatedTextView(context, true, true, false);
        checkUpdates.setAnimationProperties(.5f, 0, 450, CubicBezierInterpolator.EASE_OUT_QUINT);
        checkUpdates.setGravity(Gravity.CENTER);
        checkUpdates.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText));
        checkUpdates.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
        checkUpdates.setTextSize(AndroidUtilities.dp(14));
        checkUpdates.setIgnoreRTL(!LocaleController.isRTL);
        checkUpdates.adaptWidth = false;
        checkUpdates.setText(LocaleController.getString("CheckForUpdates", R.string.CheckForUpdates));
        checkUpdates.setOnClickListener(v -> {
            checkUpdates.setText(LocaleController.getString("CheckingForUpdates", R.string.CheckingForUpdates));
            UpdaterUtils.checkUpdates(fragment, true, () -> {
                headerCell.getTimeView().setText(LocaleController.getString("LastCheck", R.string.LastCheck) + ": " + LocaleController.formatDateTime(ExteraConfig.lastUpdateCheckTime / 1000));
                checkUpdates.setText(LocaleController.getString("CheckForUpdates", R.string.CheckForUpdates));
                BulletinFactory.of(getContainer(), null).createErrorBulletin(LocaleController.getString("NoUpdates", R.string.NoUpdates)).show();
            }, this::dismiss);
        });
        checkUpdatesBackground.addView(checkUpdates, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER));

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
            } else if (position == changelogTextRow) {
                return  1;
            }
            return 0;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int type = holder.getItemViewType();
            return type == 0;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case 0:
                    view = new TextCell(context);
                    break;
                case 1:
                    view = changelogCell = new TextInfoPrivacyCell(context, 11);
                    break;
                case 2:
                    view = headerCell = new UpdaterHeaderCell(context);
                    break;
                default:
                    throw new IllegalStateException("Unexpected value: " + viewType);
            }
            view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case 0: {
                    TextCell cell = (TextCell) holder.itemView;
                    if (position == versionRow) {
                        cell.setTextAndValueAndIcon(LocaleController.getString("CurrentVersion", R.string.CurrentVersion), BuildVars.BUILD_VERSION_STRING, R.drawable.msg_info, true);
                    } else if (position == buildTypeRow) {
                        cell.setTextAndValueAndIcon(LocaleController.getString("BuildType", R.string.BuildType), BuildVars.isBetaApp() ? LocaleController.getString("BTBeta", R.string.BTBeta) : LocaleController.getString("BTRelease", R.string.BTRelease), R.drawable.msg_customize, true);
                    } else if (position == checkOnLaunchRow) {
                        cell.setTextAndCheckAndIcon(LocaleController.getString("CheckOnLaunch", R.string.CheckOnLaunch), ExteraConfig.checkUpdatesOnLaunch, R.drawable.msg_recent, true);
                    } else if (position == clearCacheRow) {
                        cell.setTextAndIcon(LocaleController.getString("ClearUpdatesCache", R.string.ClearUpdatesCache), R.drawable.msg_clear, false);
                    } else if (position == sizeRow) {
                        cell.setTextAndValueAndIcon(LocaleController.getString("UpdateSize", R.string.UpdateSize), args[2], R.drawable.msg_sendfile, true);
                    } else if (position == changelogRow) {
                        cell.setTextAndIcon(LocaleController.getString("Changelog", R.string.Changelog), R.drawable.msg_log, false);
                    }
                    break;
                }
                case 1: {
                    TextInfoPrivacyCell cell = (TextInfoPrivacyCell) holder.itemView;
                    if (position == changelogTextRow) {
                        cell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
                        cell.setText(UpdaterUtils.replaceTags(args[1]));
                    }
                    break;
                }
            }
        }
    }

    private class UpdaterHeaderCell extends FrameLayout {

        private AnimatedTextView timeView;
        private RLottieImageView imageView;

        private UpdaterHeaderCell(Context context) {
            super(context);

            FrameLayout frame = new FrameLayout(context);

            if (available) {
                imageView = new RLottieImageView(context);
                imageView.setOnClickListener(v -> {
                    if (!imageView.isPlaying() && imageView.getAnimatedDrawable() != null) {
                        imageView.getAnimatedDrawable().setCurrentFrame(0);
                        imageView.playAnimation();
                    }
                });
                imageView.setAnimation(R.raw.etg_raccoon, 60, 60, new int[]{0x000000, 0x000000});
                imageView.setScaleType(ImageView.ScaleType.CENTER);
                frame.addView(imageView, LayoutHelper.createFrame(60, 60, Gravity.LEFT | Gravity.CENTER_VERTICAL));
            }

            SimpleTextView nameView = new SimpleTextView(context);
            nameView.setTextSize(20);
            nameView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            nameView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            nameView.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
            nameView.setText(available ? LocaleController.getString("UpdateAvailable", R.string.UpdateAvailable) : ExteraUtils.getAppName());
            frame.addView(nameView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 30, Gravity.LEFT, available ? 75 : 0, 5, 0, 0));

            timeView = new AnimatedTextView(context, true, true, false);
            timeView.setAnimationProperties(0.7f, 0, 450, CubicBezierInterpolator.EASE_OUT_QUINT);
            timeView.setIgnoreRTL(!LocaleController.isRTL);
            timeView.adaptWidth = false;
            timeView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            timeView.setTextSize(AndroidUtilities.dp(13));
            timeView.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_REGULAR));
            timeView.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
            timeView.setText(available ? args[4] : LocaleController.getString("LastCheck", R.string.LastCheck) + ": " + LocaleController.formatDateTime(ExteraConfig.lastUpdateCheckTime / 1000));
            frame.addView(timeView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 20, Gravity.LEFT, available ? 75 : 0, 35, 0, 0));

            addView(frame, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, 0, 21, 10, 0, 10));
        }

        public RLottieImageView getImageView() {
            return imageView;
        }

        public AnimatedTextView getTimeView() {
            return timeView;
        }
    }

    private void copyText(CharSequence text) {
        AndroidUtilities.addToClipboard(text);
        BulletinFactory.of(getContainer(), null).createCopyBulletin(LocaleController.getString("TextCopied", R.string.TextCopied)).show();
    }

    @Override
    public void show() {
        super.show();
        if (headerCell.getImageView() != null) {
            headerCell.getImageView().playAnimation();
        }
    }
}
