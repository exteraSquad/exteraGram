/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @immat0x1, 2023

*/

package com.exteragram.messenger.preferences;

import android.animation.ValueAnimator;
import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.exteragram.messenger.ExteraConfig;
import com.exteragram.messenger.ExteraUtils;
import com.exteragram.messenger.components.AltSeekbar;
import com.exteragram.messenger.components.DoubleTapCell;
import com.exteragram.messenger.components.StickerShapeCell;
import com.exteragram.messenger.components.StickerSizePreviewCell;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SlideChooseView;

public class ChatsPreferencesActivity extends BasePreferencesActivity implements NotificationCenter.NotificationCenterDelegate {

    private ActionBarMenuItem resetItem;
    private StickerSizeCell stickerSizeCell;
    private DoubleTapCell doubleTapCell;

    private final CharSequence[] doubleTapActions = new CharSequence[]{
            LocaleController.getString("Disable", R.string.Disable),
            LocaleController.getString("Reactions", R.string.Reactions),
            LocaleController.getString("Reply", R.string.Reply),
            LocaleController.getString("Copy", R.string.Copy),
            LocaleController.getString("Forward", R.string.Forward),
            LocaleController.getString("Edit", R.string.Edit),
            LocaleController.getString("Save", R.string.Save),
            LocaleController.getString("Delete", R.string.Delete)
    };
    private final int[] doubleTapIcons = new int[]{
            R.drawable.msg_block,
            ExteraConfig.useSolarIcons ? R.drawable.msg_reactions : R.drawable.msg_saved_14,
            R.drawable.msg_reply,
            R.drawable.msg_copy,
            R.drawable.msg_forward,
            R.drawable.msg_edit,
            R.drawable.msg_saved,
            R.drawable.msg_delete
    };

    private int stickerSizeRow;

    private int stickerShapeHeaderRow;
    private int stickerShapeRow;
    private int stickerShapeDividerRow;

    private int stickersHeaderRow;
    private int hideStickerTimeRow;
    private int unlimitedRecentStickersRow;
    private int hideCategoriesRow;
    private int stickersDividerRow;

    private int doubleTapHeaderRow;
    private int doubleTapRow;
    private int doubleTapActionOutOwnerRow;
    private int doubleTapActionRow;
    private int doubleTapReactionRow;
    private int doubleTapDividerRow;

    private int chatHeaderRow;
    private int hideKeyboardOnScrollRow;
    private int hideMuteUnmuteButtonRow;
    private int disableJumpToNextChannelRow;
    private int showActionTimestampsRow;
    private int hideShareButtonRow;
    private int dateOfForwardedMsgRow;
    private int showMessageIDRow;
    private int addCommaAfterMentionRow;
    private int chatDividerRow;

    private int photosHeaderRow;
    private int photosQualityChooserRow;
    private int disableEdgeActionRow;
    private int hideCameraTileRow;
    private int photosDividerRow;

    private int videosHeaderRow;
    private int staticZoomRow;
    private int rememberLastUsedCameraRow;
    private int pauseOnMinimizeRow;
    private int disablePlaybackRow;
    private int videosDividerRow;

    private class StickerSizeCell extends FrameLayout {

        private final StickerSizePreviewCell messagesCell;
        private final AltSeekbar seekBar;
        private final int startStickerSize = 4;
        private final int endStickerSize = 20;

        private int lastWidth;

        public StickerSizeCell(Context context) {
            super(context);
            setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            setWillNotDraw(false);

            seekBar = new AltSeekbar(context, (float p) -> {
                ExteraConfig.editor.putFloat("stickerSize", ExteraConfig.stickerSize = p).apply();
                StickerSizeCell.this.invalidate();
                if (resetItem.getVisibility() != VISIBLE) {
                    AndroidUtilities.updateViewVisibilityAnimated(resetItem, true, 0.5f, true);
                }
            }, false, startStickerSize, endStickerSize, LocaleController.getString("StickerSize", R.string.StickerSize), LocaleController.getString("StickerSizeLeft", R.string.StickerSizeLeft), LocaleController.getString("StickerSizeRight", R.string.StickerSizeRight));
            seekBar.setProgress((ExteraConfig.stickerSize - startStickerSize) / (float) (endStickerSize - startStickerSize));
            addView(seekBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            
            messagesCell = new StickerSizePreviewCell(context, ChatsPreferencesActivity.this, parentLayout);
            messagesCell.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            addView(messagesCell, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 0, 112, 0, 0));
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            int width = MeasureSpec.getSize(widthMeasureSpec);
            if (lastWidth != width) {
                lastWidth = width;
            }
        }

        @Override
        public void invalidate() {
            super.invalidate();
            lastWidth = -1;
            messagesCell.invalidate();
            seekBar.invalidate();
        }
    }

    @Override
    public View createView(Context context) {
        View fragmentView = super.createView(context);

        ActionBarMenu menu = actionBar.createMenu();
        resetItem = menu.addItem(0, R.drawable.msg_reset);
        resetItem.setContentDescription(LocaleController.getString("Reset", R.string.Reset));
        resetItem.setVisibility(ExteraConfig.stickerSize != 14.0f ? View.VISIBLE : View.GONE);
        resetItem.setTag(null);
        resetItem.setOnClickListener(v -> {
            AndroidUtilities.updateViewVisibilityAnimated(resetItem, false, 0.5f, true);
            ValueAnimator animator = ValueAnimator.ofFloat(ExteraConfig.stickerSize, 14.0f);
            animator.setDuration(200);
            animator.addUpdateListener(valueAnimator -> {
                ExteraConfig.editor.putFloat("stickerSize", ExteraConfig.stickerSize = (Float) valueAnimator.getAnimatedValue()).apply();
                stickerSizeCell.invalidate();
            });
            animator.start();
        });

        return fragmentView;
    }

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.emojiLoaded);
        return true;
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.emojiLoaded) {
            if (getListView() != null) {
                getListView().invalidateViews();
            }
        }
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.emojiLoaded);
    }

    @Override
    protected void updateRowsId() {
        super.updateRowsId();

        stickerSizeRow = newRow();

        stickerShapeHeaderRow = newRow();
        stickerShapeRow = newRow();
        stickerShapeDividerRow = newRow();

        stickersHeaderRow = newRow();
        hideStickerTimeRow = newRow();
        unlimitedRecentStickersRow = newRow();
        hideCategoriesRow = newRow();
        stickersDividerRow = newRow();

        doubleTapHeaderRow = newRow();
        doubleTapRow = newRow();
        doubleTapActionRow = newRow();
        doubleTapActionOutOwnerRow = newRow();
        doubleTapReactionRow = ExteraConfig.doubleTapAction == 1 || ExteraConfig.doubleTapActionOutOwner == 1 ? newRow() : -1;
        doubleTapDividerRow = newRow();

        chatHeaderRow = newRow();
        hideMuteUnmuteButtonRow = newRow();
        hideKeyboardOnScrollRow = newRow();
        disableJumpToNextChannelRow = newRow();
        showActionTimestampsRow = newRow();
        hideShareButtonRow = newRow();
        dateOfForwardedMsgRow = newRow();
        showMessageIDRow = newRow();
        addCommaAfterMentionRow = newRow();
        chatDividerRow = newRow();

        photosHeaderRow = newRow();
        photosQualityChooserRow = newRow();
        disableEdgeActionRow = newRow();
        hideCameraTileRow = newRow();
        photosDividerRow = newRow();

        videosHeaderRow = newRow();
        staticZoomRow = newRow();
        rememberLastUsedCameraRow = newRow();
        pauseOnMinimizeRow = newRow();
        disablePlaybackRow = newRow();
        videosDividerRow = newRow();
    }

    @Override
    protected void onItemClick(View view, int position, float x, float y) {
        if (position == hideStickerTimeRow) {
            ExteraConfig.editor.putBoolean("hideStickerTime", ExteraConfig.hideStickerTime ^= true).apply();
            ((TextCheckCell) view).setChecked(ExteraConfig.hideStickerTime);
            stickerSizeCell.invalidate();
        } else if (position == unlimitedRecentStickersRow) {
            ExteraConfig.editor.putBoolean("unlimitedRecentStickers", ExteraConfig.unlimitedRecentStickers ^= true).apply();
            ((TextCheckCell) view).setChecked(ExteraConfig.unlimitedRecentStickers);
        } else if (position == hideCategoriesRow) {
            ExteraConfig.editor.putBoolean("hideCategories", ExteraConfig.hideCategories ^= true).apply();
            ((TextCheckCell) view).setChecked(ExteraConfig.hideCategories);
        } else if (position == addCommaAfterMentionRow) {
            ExteraConfig.editor.putBoolean("addCommaAfterMention", ExteraConfig.addCommaAfterMention ^= true).apply();
            ((TextCheckCell) view).setChecked(ExteraConfig.addCommaAfterMention);
        } else if (position == hideKeyboardOnScrollRow) {
            ExteraConfig.editor.putBoolean("hideKeyboardOnScroll", ExteraConfig.hideKeyboardOnScroll ^= true).apply();
            ((TextCheckCell) view).setChecked(ExteraConfig.hideKeyboardOnScroll);
        } else if (position == hideShareButtonRow) {
            ExteraConfig.editor.putBoolean("hideShareButton", ExteraConfig.hideShareButton ^= true).apply();
            ((TextCheckCell) view).setChecked(ExteraConfig.hideShareButton);
        } else if (position == hideMuteUnmuteButtonRow) {
            ExteraConfig.editor.putBoolean("hideMuteUnmuteButton", ExteraConfig.hideMuteUnmuteButton ^= true).apply();
            ((TextCheckCell) view).setChecked(ExteraConfig.hideMuteUnmuteButton);
        } else if (position == disableJumpToNextChannelRow) {
            ExteraConfig.editor.putBoolean("disableJumpToNextChannel", ExteraConfig.disableJumpToNextChannel ^= true).apply();
            ((TextCheckCell) view).setChecked(ExteraConfig.disableJumpToNextChannel);
            parentLayout.rebuildAllFragmentViews(false, false);
        } else if (position == dateOfForwardedMsgRow) {
            ExteraConfig.editor.putBoolean("dateOfForwardedMsg", ExteraConfig.dateOfForwardedMsg ^= true).apply();
            ((TextCheckCell) view).setChecked(ExteraConfig.dateOfForwardedMsg);
        } else if (position == showMessageIDRow) {
            ExteraConfig.editor.putBoolean("showMessageID", ExteraConfig.showMessageID ^= true).apply();
            ((TextCheckCell) view).setChecked(ExteraConfig.showMessageID);
        } else if (position == showActionTimestampsRow) {
            ExteraConfig.editor.putBoolean("showActionTimestamps", ExteraConfig.showActionTimestamps ^= true).apply();
            ((TextCheckCell) view).setChecked(ExteraConfig.showActionTimestamps);
            parentLayout.rebuildAllFragmentViews(false, false);
        } else if (position == staticZoomRow) {
            ExteraConfig.editor.putBoolean("staticZoom", ExteraConfig.staticZoom ^= true).apply();
            ((TextCheckCell) view).setChecked(ExteraConfig.staticZoom);
        } else if (position == rememberLastUsedCameraRow) {
            ExteraConfig.editor.putBoolean("rememberLastUsedCamera", ExteraConfig.rememberLastUsedCamera ^= true).apply();
            ((TextCheckCell) view).setChecked(ExteraConfig.rememberLastUsedCamera);
        } else if (position == hideCameraTileRow) {
            ExteraConfig.editor.putBoolean("hideCameraTile", ExteraConfig.hideCameraTile ^= true).apply();
            ((TextCheckCell) view).setChecked(ExteraConfig.hideCameraTile);
        } else if (position == pauseOnMinimizeRow) {
            ExteraConfig.editor.putBoolean("pauseOnMinimize", ExteraConfig.pauseOnMinimize ^= true).apply();
            ((TextCheckCell) view).setChecked(ExteraConfig.pauseOnMinimize);
        } else if (position == disablePlaybackRow) {
            ExteraConfig.editor.putBoolean("disablePlayback", ExteraConfig.disablePlayback ^= true).apply();
            ((TextCheckCell) view).setChecked(ExteraConfig.disablePlayback);
            showBulletin();
        } else if (position == disableEdgeActionRow) {
            ExteraConfig.editor.putBoolean("disableEdgeAction", ExteraConfig.disableEdgeAction ^= true).apply();
            ((TextCheckCell) view).setChecked(ExteraConfig.disableEdgeAction);
        } else if (position == doubleTapActionRow || position == doubleTapActionOutOwnerRow) {
            if (getParentActivity() == null) {
                return;
            }
            ExteraUtils.showDialog(doubleTapActions, doubleTapIcons, LocaleController.getString("DoubleTap", R.string.DoubleTap), position == doubleTapActionRow ? ExteraConfig.doubleTapAction : ExteraConfig.doubleTapActionOutOwner, getContext(), i -> {
                if (position == doubleTapActionOutOwnerRow) {
                    int old = ExteraConfig.doubleTapActionOutOwner;
                    if (old == i) return;
                    doubleTapCell.updateIcons(2, true);
                    ExteraConfig.editor.putInt("doubleTapActionOutOwner", ExteraConfig.doubleTapActionOutOwner = i).apply();
                    if (old == 1 && ExteraConfig.doubleTapAction != 1) {
                        listAdapter.notifyItemRemoved(doubleTapReactionRow);
                        updateRowsId();
                    } else if (i == 1 && ExteraConfig.doubleTapAction != 1) {
                        updateRowsId();
                        listAdapter.notifyItemInserted(doubleTapReactionRow);
                    }
                    listAdapter.notifyItemChanged(doubleTapActionOutOwnerRow, payload);
                } else {
                    int old = ExteraConfig.doubleTapAction;
                    if (old == i) return;
                    doubleTapCell.updateIcons(1, true);
                    ExteraConfig.editor.putInt("doubleTapAction", ExteraConfig.doubleTapAction = i).apply();
                    if (old == 1 && ExteraConfig.doubleTapActionOutOwner != 1) {
                        listAdapter.notifyItemRemoved(doubleTapReactionRow);
                        listAdapter.notifyItemChanged(doubleTapActionOutOwnerRow);
                        updateRowsId();
                    } else if (i == 1 && ExteraConfig.doubleTapActionOutOwner != 1) {
                        updateRowsId();
                        listAdapter.notifyItemChanged(doubleTapActionOutOwnerRow);
                        listAdapter.notifyItemInserted(doubleTapReactionRow);
                    }
                    listAdapter.notifyItemChanged(doubleTapActionRow, payload);
                }
            });
        } else if (position == doubleTapReactionRow) {
            if (view.getY() >= listView.getBottom() / 3) {
                listView.smoothScrollBy(0, (int) Math.abs(view.getY()));
            }
            DoubleTapCell.SetReactionCell.showSelectStatusDialog((DoubleTapCell.SetReactionCell) view, this);
        }
    }

    @Override
    protected String getTitle() {
        return LocaleController.getString("SearchAllChatsShort", R.string.SearchAllChatsShort);
    }

    @Override
    protected BaseListAdapter createAdapter(Context context) {
        return new ListAdapter(context);
    }

    private class ListAdapter extends BaseListAdapter {

        public ListAdapter(Context context) {
            super(context);
        }

        @Override
        public int getItemCount() {
            return rowCount;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            switch (type) {
                case 10:
                    StickerShapeCell stickerShapeCell = new StickerShapeCell(mContext) {
                        @Override
                        protected void updateStickerPreview() {
                            parentLayout.rebuildAllFragmentViews(false, false);
                            stickerSizeCell.invalidate();
                        }
                    };
                    stickerShapeCell.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
                    return new RecyclerListView.Holder(stickerShapeCell);
                case 11:
                    stickerSizeCell = new StickerSizeCell(mContext);
                    stickerSizeCell.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
                    return new RecyclerListView.Holder(stickerSizeCell);
                case 15:
                    doubleTapCell = new DoubleTapCell(mContext);
                    doubleTapCell.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
                    return new RecyclerListView.Holder(doubleTapCell);
                case 16:
                    DoubleTapCell.SetReactionCell reactionCell = new DoubleTapCell.SetReactionCell(mContext);
                    reactionCell.update(false);
                    reactionCell.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
                    return new RecyclerListView.Holder(reactionCell);
                default:
                    return super.onCreateViewHolder(parent, type);
            }
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position, boolean payload) {
            switch (holder.getItemViewType()) {
                case 1:
                    holder.itemView.setBackground(Theme.getThemedDrawable(mContext, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
                    break;
                case 3:
                    HeaderCell headerCell = (HeaderCell) holder.itemView;
                    if (position == stickersHeaderRow) {
                        headerCell.setText(LocaleController.getString(R.string.StickersName));
                    } else if (position == chatHeaderRow) {
                        headerCell.setText(LocaleController.getString("SearchAllChatsShort", R.string.SearchAllChatsShort));
                    } else if (position == videosHeaderRow) {
                        headerCell.setText(LocaleController.getString("AutoDownloadVideos", R.string.AutoDownloadVideos));
                    } else if (position == stickerShapeHeaderRow) {
                        headerCell.setText(LocaleController.getString("StickerShape", R.string.StickerShape));
                    } else if (position == doubleTapHeaderRow) {
                        headerCell.setText(LocaleController.getString("DoubleTap", R.string.DoubleTap));
                    } else if (position == photosHeaderRow) {
                        headerCell.setText(LocaleController.getString("AutoDownloadPhotos", R.string.AutoDownloadPhotos));
                    }
                    break;
                case 5:
                    TextCheckCell textCheckCell = (TextCheckCell) holder.itemView;
                    textCheckCell.setEnabled(true, null);
                    if (position == hideStickerTimeRow) {
                        textCheckCell.setTextAndCheck(LocaleController.getString("StickerTime", R.string.StickerTime), ExteraConfig.hideStickerTime, true);
                    } else if (position == unlimitedRecentStickersRow) {
                        textCheckCell.setTextAndCheck(LocaleController.getString("UnlimitedRecentStickers", R.string.UnlimitedRecentStickers), ExteraConfig.unlimitedRecentStickers, true);
                    } else if (position == hideCategoriesRow) {
                        textCheckCell.setTextAndCheck(LocaleController.getString("HideCategories", R.string.HideCategories), ExteraConfig.hideCategories, false);
                    } else if (position == addCommaAfterMentionRow) {
                        textCheckCell.setTextAndCheck(LocaleController.getString("AddCommaAfterMention", R.string.AddCommaAfterMention), ExteraConfig.addCommaAfterMention, false);
                    } else if (position == hideKeyboardOnScrollRow) {
                        textCheckCell.setTextAndCheck(LocaleController.getString("HideKeyboardOnScroll", R.string.HideKeyboardOnScroll), ExteraConfig.hideKeyboardOnScroll, true);
                    } else if (position == hideShareButtonRow) {
                        textCheckCell.setTextAndCheck(LocaleController.formatString("HideShareButton", R.string.HideShareButton, LocaleController.getString("ShareFile", R.string.ShareFile)), ExteraConfig.hideShareButton, true);
                    } else if (position == hideMuteUnmuteButtonRow) {
                        textCheckCell.setTextAndValueAndCheck(LocaleController.formatString("HideMuteUnmuteButton", R.string.HideMuteUnmuteButton, LocaleController.getString("ChannelMute", R.string.ChannelMute)), LocaleController.getString("HideMuteUnmuteButtonInfo", R.string.HideMuteUnmuteButtonInfo), ExteraConfig.hideMuteUnmuteButton, true, true);
                    } else if (position == disableJumpToNextChannelRow) {
                        textCheckCell.setTextAndCheck(LocaleController.getString("DisableJumpToNextChannel", R.string.DisableJumpToNextChannel), ExteraConfig.disableJumpToNextChannel, true);
                    } else if (position == dateOfForwardedMsgRow) {
                        textCheckCell.setTextAndCheck(LocaleController.getString("DateOfForwardedMsg", R.string.DateOfForwardedMsg), ExteraConfig.dateOfForwardedMsg, true);
                    } else if (position == showMessageIDRow) {
                        textCheckCell.setTextAndCheck(LocaleController.getString("ShowMessageID", R.string.ShowMessageID), ExteraConfig.showMessageID, true);
                    } else if (position == showActionTimestampsRow) {
                        textCheckCell.setTextAndCheck(LocaleController.getString("ShowActionTimestamps", R.string.ShowActionTimestamps), ExteraConfig.showActionTimestamps, true);
                    } else if (position == staticZoomRow) {
                        textCheckCell.setTextAndValueAndCheck(LocaleController.getString("StaticZoom", R.string.StaticZoom), LocaleController.getString("StaticZoomInfo", R.string.StaticZoomInfo), ExteraConfig.staticZoom, true, true);
                    } else if (position == rememberLastUsedCameraRow) {
                        textCheckCell.setTextAndValueAndCheck(LocaleController.getString("RememberLastUsedCamera", R.string.RememberLastUsedCamera), LocaleController.getString("RememberLastUsedCameraInfo", R.string.RememberLastUsedCameraInfo), ExteraConfig.rememberLastUsedCamera, true, true);
                    } else if (position == hideCameraTileRow) {
                        textCheckCell.setTextAndCheck(LocaleController.getString("HideCameraTile", R.string.HideCameraTile), ExteraConfig.hideCameraTile,false);
                    } else if (position == pauseOnMinimizeRow) {
                        textCheckCell.setTextAndValueAndCheck(LocaleController.getString("PauseOnMinimize", R.string.PauseOnMinimize), LocaleController.getString("PauseOnMinimizeInfo", R.string.PauseOnMinimizeInfo), ExteraConfig.pauseOnMinimize, true, true);
                    } else if (position == disablePlaybackRow) {
                        textCheckCell.setTextAndCheck(LocaleController.getString("DisablePlayback", R.string.DisablePlayback), ExteraConfig.disablePlayback, false);
                    } else if (position == disableEdgeActionRow) {
                        textCheckCell.setTextAndValueAndCheck(LocaleController.getString("DisableEdgeAction", R.string.DisableEdgeAction), LocaleController.getString("DisableEdgeActionInfo", R.string.DisableEdgeActionInfo), ExteraConfig.disableEdgeAction, true, true);
                    }
                    break;
                case 7:
                    TextSettingsCell textSettingsCell = (TextSettingsCell) holder.itemView;
                    if (position == doubleTapActionOutOwnerRow) {
                        textSettingsCell.setTextAndValue(LocaleController.getString("DoubleTapOutgoing", R.string.DoubleTapOutgoing), doubleTapActions[ExteraConfig.doubleTapActionOutOwner], payload, doubleTapReactionRow != -1);
                    } else if (position == doubleTapActionRow) {
                        textSettingsCell.setTextAndValue(LocaleController.getString("DoubleTapIncoming", R.string.DoubleTapIncoming), doubleTapActions[ExteraConfig.doubleTapAction], payload, true);
                    }
                    break;
                case 8:
                    TextInfoPrivacyCell cell = (TextInfoPrivacyCell) holder.itemView;
                    if (position == doubleTapDividerRow) {
                        cell.setText(LocaleController.getString("DoubleTapInfo", R.string.DoubleTapInfo));
                    } else if (position == photosDividerRow) {
                        cell.setText(LocaleController.getString("HideCameraTileInfo", R.string.HideCameraTileInfo));
                    } else if (position == chatDividerRow) {
                        cell.setText(ExteraUtils.formatWithUsernames(LocaleController.getString("AddCommaAfterMentionInfo", R.string.AddCommaAfterMentionInfo), ChatsPreferencesActivity.this));
                    } else if (position == stickersDividerRow) {
                        cell.setText(LocaleController.getString("HideCategoriesInfo", R.string.HideCategoriesInfo));
                    } else if (position == videosDividerRow) {
                        cell.setText(LocaleController.getString("DisablePlaybackInfo", R.string.DisablePlaybackInfo));
                    }
                    break;
                case 13:
                    SlideChooseView slide = (SlideChooseView) holder.itemView;
                    if (position == photosQualityChooserRow) {
                        slide.setNeedDivider(true);
                        slide.setCallback(index -> ExteraConfig.editor.putInt("sendPhotosQuality", ExteraConfig.sendPhotosQuality = index).apply());
                        slide.setOptions(ExteraConfig.sendPhotosQuality, "800px", "1280px", "2560px");
                    }
                    break;
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == stickerShapeDividerRow) {
                return 1;
            } else if (position == stickersHeaderRow || position == chatHeaderRow || position == videosHeaderRow || position == stickerShapeHeaderRow ||
                    position == doubleTapHeaderRow || position == photosHeaderRow) {
                return 3;
            } else if (position == doubleTapActionRow || position == doubleTapActionOutOwnerRow) {
                return 7;
            } else if (position == doubleTapDividerRow || position == photosDividerRow || position == chatDividerRow || position == stickersDividerRow || position == videosDividerRow) {
                return 8;
            } else if (position == stickerShapeRow) {
                return 10;
            } else if (position == stickerSizeRow) {
                return 11;
            } else if (position == photosQualityChooserRow) {
                return 13;
            } else if (position == doubleTapRow) {
                return 15;
            } else if (position == doubleTapReactionRow) {
                return 16;
            }
            return 5;
        }
    }
}