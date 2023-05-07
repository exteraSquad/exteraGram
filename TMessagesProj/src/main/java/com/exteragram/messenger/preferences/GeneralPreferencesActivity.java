/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @immat0x1, 2023

*/

package com.exteragram.messenger.preferences;

import android.content.Context;
import android.os.Build;
import android.text.Html;
import android.text.Spannable;
import android.text.SpannableString;
import android.util.Size;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.camera.video.Quality;
import androidx.recyclerview.widget.RecyclerView;

import com.exteragram.messenger.ExteraConfig;
import com.exteragram.messenger.camera.CameraXUtils;
import com.exteragram.messenger.preferences.components.CameraTypeSelector;
import com.exteragram.messenger.utils.LocaleUtils;
import com.exteragram.messenger.utils.PopupUtils;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SlideChooseView;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class GeneralPreferencesActivity extends BasePreferencesActivity {

    private final CharSequence[] tabletMode = new CharSequence[]{
            LocaleController.getString("DistanceUnitsAutomatic", R.string.DistanceUnitsAutomatic),
            LocaleController.getString("PasswordOn", R.string.PasswordOn),
            LocaleController.getString("PasswordOff", R.string.PasswordOff)
    }, id = new CharSequence[]{
            LocaleController.getString("Hide", R.string.Hide),
            "Telegram API",
            "Bot API"
    };

    private int cameraTypeHeaderRow;
    private int cameraTypeSelectorRow;
    private int cameraXOptimizeRow;
    private int cameraXQualityRow;
    private int cameraTypeDividerRow;

    private int speedBoostersHeaderRow;
    private int downloadSpeedChooserRow;
    private int uploadSpeedBoostRow;
    private int speedBoostersDividerRow;

    private int generalHeaderRow;
    private int formatTimeWithSecondsRow;
    private int disableNumberRoundingRow;
    private int disableProximitySensorRow;
    private int tabletModeRow;
    private int generalDividerRow;

    private int profileHeaderRow;
    private int showIdAndDcRow;
    private int hidePhoneNumberRow;
    private int profileDividerRow;

    private int archiveHeaderRow;
    private int archiveOnPullRow;
    private int disableUnarchiveSwipeRow;
    private int archiveDividerRow;

    @Override
    protected void updateRowsId() {
        super.updateRowsId();

        cameraTypeHeaderRow = -1;
        cameraTypeSelectorRow = -1;
        cameraXOptimizeRow = -1;
        cameraXQualityRow = -1;
        cameraTypeDividerRow = -1;

        if (CameraXUtils.isCameraXSupported()) {
            cameraTypeHeaderRow = newRow();
            cameraTypeSelectorRow = newRow();
            if (ExteraConfig.cameraType == 1) {
                cameraXOptimizeRow = newRow();
                cameraXQualityRow = newRow();
            }
            cameraTypeDividerRow = newRow();
        }

        generalHeaderRow = newRow();
        disableNumberRoundingRow = newRow();
        formatTimeWithSecondsRow = newRow();
        disableProximitySensorRow = newRow();
        tabletModeRow = newRow();
        generalDividerRow = newRow();

        speedBoostersHeaderRow = newRow();
        downloadSpeedChooserRow = newRow();
        uploadSpeedBoostRow = newRow();
        speedBoostersDividerRow = newRow();

        profileHeaderRow = newRow();
        hidePhoneNumberRow = newRow();
        showIdAndDcRow = newRow();
        profileDividerRow = newRow();

        archiveHeaderRow = newRow();
        archiveOnPullRow = newRow();
        disableUnarchiveSwipeRow = newRow();
        archiveDividerRow = newRow();
    }

    @Override
    protected void onItemClick(View view, int position, float x, float y) {
        if (position == disableNumberRoundingRow) {
            ExteraConfig.editor.putBoolean("disableNumberRounding", ExteraConfig.disableNumberRounding ^= true).apply();
            ((TextCheckCell) view).setChecked(ExteraConfig.disableNumberRounding);
            parentLayout.rebuildAllFragmentViews(false, false);
        } else if (position == formatTimeWithSecondsRow) {
            ExteraConfig.editor.putBoolean("formatTimeWithSeconds", ExteraConfig.formatTimeWithSeconds ^= true).apply();
            ((TextCheckCell) view).setChecked(ExteraConfig.formatTimeWithSeconds);
            LocaleController.getInstance().recreateFormatters();
            parentLayout.rebuildAllFragmentViews(false, false);
        } else if (position == disableProximitySensorRow) {
            ExteraConfig.editor.putBoolean("disableProximitySensor", ExteraConfig.disableProximitySensor ^= true).apply();
            ((TextCheckCell) view).setChecked(ExteraConfig.disableProximitySensor);
        } else if (position == tabletModeRow) {
            if (getParentActivity() == null) {
                return;
            }
            PopupUtils.showDialog(tabletMode, LocaleController.getString("TabletMode", R.string.TabletMode), ExteraConfig.tabletMode, getContext(), i -> {
                ExteraConfig.editor.putInt("tabletMode", ExteraConfig.tabletMode = i).apply();
                listAdapter.notifyItemChanged(tabletModeRow, payload);
                showBulletin();
            });
        } else if (position == archiveOnPullRow) {
            ExteraConfig.editor.putBoolean("archiveOnPull", ExteraConfig.archiveOnPull ^= true).apply();
            ((TextCheckCell) view).setChecked(ExteraConfig.archiveOnPull);
        } else if (position == disableUnarchiveSwipeRow) {
            ExteraConfig.editor.putBoolean("disableUnarchiveSwipe", ExteraConfig.disableUnarchiveSwipe ^= true).apply();
            ((TextCheckCell) view).setChecked(ExteraConfig.disableUnarchiveSwipe);
        } else if (position == hidePhoneNumberRow) {
            ExteraConfig.editor.putBoolean("hidePhoneNumber", ExteraConfig.hidePhoneNumber ^= true).apply();
            ((TextCheckCell) view).setChecked(ExteraConfig.hidePhoneNumber);
            parentLayout.rebuildAllFragmentViews(false, false);
            getNotificationCenter().postNotificationName(NotificationCenter.mainUserInfoChanged);
        } else if (position == showIdAndDcRow) {
            if (getParentActivity() == null) {
                return;
            }
            PopupUtils.showDialog(id, LocaleController.getString("ShowIdAndDc", R.string.ShowIdAndDc), ExteraConfig.showIdAndDc, getContext(), i -> {
                ExteraConfig.editor.putInt("showIdAndDc", ExteraConfig.showIdAndDc = i).apply();
                parentLayout.rebuildAllFragmentViews(false, false);
                listAdapter.notifyItemChanged(showIdAndDcRow, payload);
            });
            parentLayout.rebuildAllFragmentViews(false, false);
        } else if (position == uploadSpeedBoostRow) {
            ExteraConfig.editor.putBoolean("uploadSpeedBoost", ExteraConfig.uploadSpeedBoost ^= true).apply();
            ((TextCheckCell) view).setChecked(ExteraConfig.uploadSpeedBoost);
        } else if (position == cameraXOptimizeRow) {
            ExteraConfig.editor.putBoolean("useCameraXOptimizedMode", ExteraConfig.useCameraXOptimizedMode ^= true).apply();
            ((TextCheckCell) view).setChecked(ExteraConfig.useCameraXOptimizedMode);
        } else if (position == cameraXQualityRow) {
            Map<Quality, Size> availableSizes = CameraXUtils.getAvailableVideoSizes();
            Stream<Integer> tmp = availableSizes.values().stream().sorted(Comparator.comparingInt(Size::getWidth).reversed()).map(Size::getHeight);
            ArrayList<Integer> types = tmp.collect(Collectors.toCollection(ArrayList::new));
            ArrayList<String> arrayList = types.stream().map(p -> p + "p").collect(Collectors.toCollection(ArrayList::new));
            PopupUtils.showDialog(arrayList, LocaleController.getString("CameraQuality", R.string.CameraQuality), types.indexOf(ExteraConfig.cameraResolution), getContext(), i -> {
                ExteraConfig.editor.putInt("cameraResolution", ExteraConfig.cameraResolution = types.get(i)).apply();
                listAdapter.notifyItemChanged(cameraXQualityRow, payload);
            });
        }
    }

    @Override
    protected String getTitle() {
        return LocaleController.getString("General", R.string.General);
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
            if (type == 17) {
                CameraTypeSelector cameraTypeSelector = new CameraTypeSelector(mContext) {
                    @Override
                    protected void onSelectedCamera(int cameraSelected) {
                        super.onSelectedCamera(cameraSelected);
                        int oldValue = ExteraConfig.cameraType;
                        ExteraConfig.editor.putInt("cameraType", ExteraConfig.cameraType = cameraSelected).apply();
                        if (cameraSelected == 1) {
                            updateRowsId();
                            listAdapter.notifyItemRangeInserted(cameraXOptimizeRow, 2);
                            listAdapter.notifyItemChanged(cameraTypeDividerRow);
                        } else if (oldValue == 1) {
                            listAdapter.notifyItemRangeRemoved(cameraXOptimizeRow, 2);
                            listAdapter.notifyItemChanged(cameraTypeDividerRow - 2);
                            updateRowsId();
                        } else {
                            listAdapter.notifyItemChanged(cameraTypeDividerRow);
                        }
                    }
                };
                cameraTypeSelector.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                return new RecyclerListView.Holder(cameraTypeSelector);
            }
            return super.onCreateViewHolder(parent, type);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position, boolean payload) {
            switch (holder.getItemViewType()) {
                case 1:
                    holder.itemView.setBackground(Theme.getThemedDrawable(mContext, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
                    break;
                case 3:
                    HeaderCell headerCell = (HeaderCell) holder.itemView;
                    if (position == generalHeaderRow) {
                        headerCell.setText(LocaleController.getString("General", R.string.General));
                    } else if (position == archiveHeaderRow) {
                        headerCell.setText(LocaleController.getString("ArchivedChats", R.string.ArchivedChats));
                    } else if (position == profileHeaderRow) {
                        headerCell.setText(LocaleController.getString("Profile", R.string.Profile));
                    } else if (position == speedBoostersHeaderRow) {
                        headerCell.setText(LocaleController.getString("DownloadSpeedBoost", R.string.DownloadSpeedBoost));
                    } else if (position == cameraTypeHeaderRow) {
                        headerCell.setText(LocaleController.getString("CameraType", R.string.CameraType));
                    }
                    break;
                case 5:
                    TextCheckCell textCheckCell = (TextCheckCell) holder.itemView;
                    textCheckCell.setEnabled(true, null);
                    if (position == disableNumberRoundingRow) {
                        textCheckCell.setTextAndValueAndCheck(LocaleController.getString("DisableNumberRounding", R.string.DisableNumberRounding), "1.23K -> 1,234", ExteraConfig.disableNumberRounding, true, true);
                    } else if (position == formatTimeWithSecondsRow) {
                        textCheckCell.setTextAndValueAndCheck(LocaleController.getString("FormatTimeWithSeconds", R.string.FormatTimeWithSeconds), "12:34 -> 12:34:56", ExteraConfig.formatTimeWithSeconds, true, true);
                    } else if (position == disableProximitySensorRow) {
                        textCheckCell.setTextAndCheck(LocaleController.getString("DisableProximitySensor", R.string.DisableProximitySensor), ExteraConfig.disableProximitySensor, true);
                    } else if (position == disableUnarchiveSwipeRow) {
                        textCheckCell.setTextAndCheck(LocaleController.getString("DisableUnarchiveSwipe", R.string.DisableUnarchiveSwipe), ExteraConfig.disableUnarchiveSwipe, false);
                    } else if (position == archiveOnPullRow) {
                        textCheckCell.setTextAndCheck(LocaleController.getString("ArchiveOnPull", R.string.ArchiveOnPull), ExteraConfig.archiveOnPull, true);
                    } else if (position == hidePhoneNumberRow) {
                        textCheckCell.setTextAndCheck(LocaleController.getString("HidePhoneNumber", R.string.HidePhoneNumber), ExteraConfig.hidePhoneNumber, true);
                    } else if (position == uploadSpeedBoostRow) {
                        textCheckCell.setTextAndCheck(LocaleController.getString("UploadSpeedBoost", R.string.UploadSpeedBoost), ExteraConfig.uploadSpeedBoost, false);
                    } else if (position == cameraXOptimizeRow) {
                        textCheckCell.setTextAndValueAndCheck(LocaleController.getString("PerformanceMode", R.string.PerformanceMode), LocaleController.getString("PerformanceModeInfo", R.string.PerformanceModeInfo), ExteraConfig.useCameraXOptimizedMode, true, true);
                    }
                    break;
                case 7:
                    TextSettingsCell textSettingsCell = (TextSettingsCell) holder.itemView;
                    if (position == cameraXQualityRow) {
                        textSettingsCell.setTextAndValue(LocaleController.getString("CameraQuality", R.string.CameraQuality), ExteraConfig.cameraResolution + "p", payload, false);
                    } else if (position == tabletModeRow) {
                        textSettingsCell.setTextAndValue(LocaleController.getString("TabletMode", R.string.TabletMode), tabletMode[ExteraConfig.tabletMode], payload, false);
                    } else if (position == showIdAndDcRow) {
                        textSettingsCell.setTextAndValue(LocaleController.getString("ShowIdAndDc", R.string.ShowIdAndDc), id[ExteraConfig.showIdAndDc], payload, false);
                    }
                    break;
                case 8:
                    TextInfoPrivacyCell textInfoPrivacyCell = (TextInfoPrivacyCell) holder.itemView;
                    if (position == cameraTypeDividerRow) {
                        String advise;
                        switch (ExteraConfig.cameraType) {
                            case 0:
                                advise = LocaleController.getString("DefaultCameraInfo", R.string.DefaultCameraInfo);
                                break;
                            case 1:
                                advise = LocaleController.getString("CameraXInfo", R.string.CameraXInfo);
                                break;
                            case 2:
                            default:
                                advise = LocaleController.getString("SystemCameraInfo", R.string.SystemCameraInfo);
                                break;
                        }
                        Spannable htmlParsed;
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            htmlParsed = new SpannableString(Html.fromHtml(advise, Html.FROM_HTML_MODE_LEGACY));
                        } else {
                            htmlParsed = new SpannableString(Html.fromHtml(advise));
                        }
                        textInfoPrivacyCell.setText(LocaleUtils.formatWithURLs(htmlParsed));
                    } else if (position == speedBoostersDividerRow) {
                        textInfoPrivacyCell.setText(LocaleController.getString("SpeedBoostInfo", R.string.SpeedBoostInfo));
                    } else if (position == profileDividerRow) {
                        textInfoPrivacyCell.setText(LocaleController.getString("ShowIdAndDcInfo", R.string.ShowIdAndDcInfo));
                    } else if (position == archiveDividerRow) {
                        textInfoPrivacyCell.setText(LocaleController.getString("DisableUnarchiveSwipeInfo", R.string.DisableUnarchiveSwipeInfo));
                    }
                    break;
                case 13:
                    SlideChooseView slide = (SlideChooseView) holder.itemView;
                    if (position == downloadSpeedChooserRow) {
                        slide.setNeedDivider(true);
                        slide.setCallback(index -> ExteraConfig.editor.putInt("downloadSpeedBoost", ExteraConfig.downloadSpeedBoost = index).apply());
                        slide.setOptions(ExteraConfig.downloadSpeedBoost, LocaleController.getString("BlurOff", R.string.BlurOff), LocaleController.getString("SpeedFast", R.string.SpeedFast), LocaleController.getString("Ultra", R.string.Ultra));
                    }
                    break;
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == generalDividerRow) {
                return 1;
            } else if (position == generalHeaderRow || position == archiveHeaderRow || position == profileHeaderRow ||
                    position == speedBoostersHeaderRow || position == cameraTypeHeaderRow) {
                return 3;
            } else if (position == cameraXQualityRow || position == tabletModeRow || position == showIdAndDcRow) {
                return 7;
            } else if (position == cameraTypeDividerRow || position == speedBoostersDividerRow || position == profileDividerRow  || position == archiveDividerRow) {
                return 8;
            } else if (position == downloadSpeedChooserRow) {
                return 13;
            } else if (position == cameraTypeSelectorRow) {
                return 17;
            }
            return 5;
        }
    }
}
