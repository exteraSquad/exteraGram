/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @immat0x1, 2023

*/

package com.exteragram.messenger.preferences;

import android.content.Context;
import android.os.CountDownTimer;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.exteragram.messenger.ExteraConfig;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.LaunchActivity;

import java.util.Locale;

public class OtherPreferencesActivity extends BasePreferencesActivity {

    private int analyticsHeaderRow;
    private int crashlyticsRow;
    private int analyticsRow;
    private int analyticsDividerRow;

    private int deleteAccountRow;
    private int resetSettingsRow;
    private int deleteAccountDividerRow;

    @Override
    protected void updateRowsId() {
        super.updateRowsId();

        analyticsHeaderRow = newRow();
        crashlyticsRow = newRow();
        analyticsRow = newRow();
        analyticsDividerRow = newRow();
        
        resetSettingsRow = newRow();
        deleteAccountRow = newRow();
        deleteAccountDividerRow = newRow();
    }

    @Override
    protected void onItemClick(View view, int position, float x, float y) {
        if (position == crashlyticsRow) {
            ExteraConfig.editor.putBoolean("useGoogleCrashlytics", ExteraConfig.useGoogleCrashlytics ^= true).apply();
            ((TextCell) view).setChecked(ExteraConfig.useGoogleCrashlytics);
            if (ApplicationLoader.getFirebaseCrashlytics() != null) {
                ApplicationLoader.getFirebaseCrashlytics().setCrashlyticsCollectionEnabled(ExteraConfig.useGoogleCrashlytics);
            }
        } else if (position == analyticsRow) {
            ExteraConfig.editor.putBoolean("useGoogleAnalytics", ExteraConfig.useGoogleAnalytics ^= true).apply();
            ((TextCell) view).setChecked(ExteraConfig.useGoogleAnalytics);
            if (ApplicationLoader.getFirebaseAnalytics() != null) {
                ApplicationLoader.getFirebaseAnalytics().setAnalyticsCollectionEnabled(ExteraConfig.useGoogleAnalytics);
                if (!ExteraConfig.useGoogleAnalytics) {
                    ApplicationLoader.getFirebaseAnalytics().resetAnalyticsData();
                }
            }
        } else if (position == resetSettingsRow) {
            ExteraConfig.clearPreferences();
            parentLayout.rebuildAllFragmentViews(false, false);
            getNotificationCenter().postNotificationName(NotificationCenter.mainUserInfoChanged);
            getNotificationCenter().postNotificationName(NotificationCenter.dialogFiltersUpdated);
            LocaleController.getInstance().recreateFormatters();
            ((LaunchActivity) getParentActivity()).reloadIcons();
            Theme.reloadAllResources(getParentActivity());
            BulletinFactory.of(this).createErrorBulletin(LocaleController.getString("ResetSettingsBulletin", R.string.ResetSettingsBulletin), resourcesProvider).show();
        } else if (position == deleteAccountRow) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
            builder.setMessage(LocaleController.getString("TosDeclineDeleteAccount", R.string.TosDeclineDeleteAccount));
            builder.setTitle(LocaleController.getString("DeleteAccount", R.string.DeleteAccount));
            builder.setPositiveButton(LocaleController.getString("Deactivate", R.string.Deactivate), (dialog, which) -> {
                final AlertDialog progressDialog = new AlertDialog(getParentActivity(), AlertDialog.ALERT_TYPE_SPINNER);
                progressDialog.setCanCancel(false);

                Utilities.globalQueue.postRunnable(() -> {
                    TLRPC.TL_account_deleteAccount req = new TLRPC.TL_account_deleteAccount();
                    req.reason = "ЭКСТЕРАГРАМ";
                    getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                        try {
                            progressDialog.dismiss();
                        } catch (Exception e) {
                            FileLog.e(e);
                        }
                        if (response instanceof TLRPC.TL_boolTrue) {
                            getMessagesController().performLogout(0);
                        } else if (error == null || error.code != -1000) {
                            String errorText = LocaleController.getString("ErrorOccurred", R.string.ErrorOccurred);
                            if (error != null) {
                                errorText += "\n" + error.text;
                            }
                            AlertDialog.Builder builder1 = new AlertDialog.Builder(getParentActivity());
                            builder1.setTitle(LocaleController.getString("AppName", R.string.AppName));
                            builder1.setMessage(errorText);
                            builder1.setPositiveButton(LocaleController.getString("OK", R.string.OK), null);
                            builder1.show();
                        }
                    }));
                }, 500);
                progressDialog.show();
            });
            builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
            AlertDialog dialog = builder.create();
            dialog.setOnShowListener(dialog1 -> {
                var button = (TextView) dialog.getButton(AlertDialog.BUTTON_POSITIVE);
                button.setTextColor(Theme.getColor(Theme.key_dialogTextRed));
                button.setEnabled(false);
                var buttonText = button.getText();
                new CountDownTimer(30000, 100) {
                    @Override
                    public void onTick(long millisUntilFinished) {
                        button.setText(String.format(Locale.getDefault(), "%s (%d)", buttonText, millisUntilFinished / 1000 + 1));
                    }

                    @Override
                    public void onFinish() {
                        button.setText(buttonText);
                        button.setEnabled(true);
                    }
                }.start();
            });
            showDialog(dialog);
        }
    }

    @Override
    protected String getTitle() {
        return LocaleController.getString("LocalOther", R.string.LocalOther);
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

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case 1:
                    holder.itemView.setBackground(Theme.getThemedDrawable(mContext, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
                    break;
                case 2:
                    TextCell textCell = (TextCell) holder.itemView;
                    if (position == crashlyticsRow) {
                        textCell.setTextAndCheckAndIcon("Crashlytics", ExteraConfig.useGoogleCrashlytics, R.drawable.msg_report, true);
                    } else if (position == analyticsRow) {
                        textCell.setTextAndCheckAndIcon("Analytics", ExteraConfig.useGoogleAnalytics, R.drawable.msg_data, false);
                    } else if (position == deleteAccountRow) {
                        textCell.setTextAndIcon(LocaleController.getString("DeleteAccount", R.string.DeleteAccount), R.drawable.msg_clearcache, false);
                        textCell.setColors(Theme.key_windowBackgroundWhiteRedText, Theme.key_windowBackgroundWhiteRedText);
                    } else if (position == resetSettingsRow) {
                        textCell.setTextAndIcon(LocaleController.getString("ResetSettings", R.string.ResetSettings), R.drawable.msg_reset, true);
                    }
                    break;
                case 3:
                    HeaderCell headerCell = (HeaderCell) holder.itemView;
                    if (position == analyticsHeaderRow) {
                        headerCell.setText("Google");
                    }
                    break;
                case 8:
                    TextInfoPrivacyCell textInfoPrivacyCell = (TextInfoPrivacyCell) holder.itemView;
                    if (position == analyticsDividerRow) {
                        textInfoPrivacyCell.setText(LocaleController.getString("YandexAppMetricaInfo", R.string.AnalyticsInfo));
                    }
                    break;
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == deleteAccountDividerRow) {
                return 1;
            } else if (position == analyticsHeaderRow) {
                return 3;
            } else if (position == analyticsDividerRow) {
                return 8;
            }
            return 2;
        }
    }
}