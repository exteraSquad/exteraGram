/*
 * This is the source code of Telegram for Android v. 5.x.x
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui;

import android.Manifest;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.net.Uri;
import android.os.Build;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ImageSpan;
import android.util.Base64;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.DocumentObject;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.SvgHelper;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.CheckBoxCell;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.RadioColorCell;
import org.telegram.ui.Cells.SessionCell;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.ColoredImageSpan;
import org.telegram.ui.Components.EmptyTextProgressView;
import org.telegram.ui.Components.FlickerLoadingView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RLottieImageView;
import org.telegram.ui.Components.RecyclerItemsEnterAnimator;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.ReplaceableIconDrawable;
import org.telegram.ui.Components.SlideChooseView;
import org.telegram.ui.Components.URLSpanNoUnderline;
import org.telegram.ui.Components.UndoView;

import java.util.ArrayList;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class SessionsActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private ListAdapter listAdapter;
    private RecyclerListView listView;
    private ImageView imageView;
    private TextView textView1;
    private TextView textView2;
    private EmptyTextProgressView emptyView;
    private FlickerLoadingView globalFlickerLoadingView;

    private ArrayList<TLObject> sessions = new ArrayList<>();
    private ArrayList<TLObject> passwordSessions = new ArrayList<>();
    private TLRPC.TL_authorization currentSession;
    private boolean loading;
    private LinearLayout emptyLayout;
    private UndoView undoView;
    private RecyclerItemsEnterAnimator itemsEnterAnimator;
    private int ttlDays;

    private int currentType;

    private int currentSessionSectionRow;
    private int currentSessionRow;
    private int terminateAllSessionsRow;
    private int terminateAllSessionsDetailRow;
    private int passwordSessionsSectionRow;
    private int passwordSessionsStartRow;
    private int passwordSessionsEndRow;
    private int passwordSessionsDetailRow;
    private int otherSessionsSectionRow;
    private int otherSessionsStartRow;
    private int otherSessionsEndRow;
    private int otherSessionsTerminateDetail;
    private int noOtherSessionsRow;
    private int qrCodeRow;
    private int qrCodeDividerRow;
    private int rowCount;
    private int ttlHeaderRow;
    private int ttlRow;
    private int ttlDivideRow;

    public SessionsActivity(int type) {
        currentType = type;
    }

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        updateRows();
        loadSessions(false);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.newSessionReceived);
        return true;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.newSessionReceived);
    }

    @Override
    public View createView(Context context) {
        globalFlickerLoadingView = new FlickerLoadingView(context);
        globalFlickerLoadingView.setIsSingleCell(true);

        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        if (currentType == 0) {
            actionBar.setTitle(LocaleController.getString("Devices", R.string.Devices));
        } else {
            actionBar.setTitle(LocaleController.getString("WebSessionsTitle", R.string.WebSessionsTitle));
        }
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        listAdapter = new ListAdapter(context);

        fragmentView = new FrameLayout(context);
        FrameLayout frameLayout = (FrameLayout) fragmentView;
        frameLayout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        emptyLayout = new LinearLayout(context);
        emptyLayout.setOrientation(LinearLayout.VERTICAL);
        emptyLayout.setGravity(Gravity.CENTER);
        emptyLayout.setBackgroundDrawable(Theme.getThemedDrawable(context, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
        emptyLayout.setLayoutParams(new AbsListView.LayoutParams(AbsListView.LayoutParams.MATCH_PARENT, AndroidUtilities.displaySize.y - ActionBar.getCurrentActionBarHeight()));

        imageView = new ImageView(context);
        if (currentType == 0) {
            imageView.setImageResource(R.drawable.devices);
        } else {
            imageView.setImageResource(R.drawable.no_apps);
        }
        imageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_sessions_devicesImage), PorterDuff.Mode.MULTIPLY));
        emptyLayout.addView(imageView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        textView1 = new TextView(context);
        textView1.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
        textView1.setGravity(Gravity.CENTER);
        textView1.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
        textView1.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        if (currentType == 0) {
            textView1.setText(LocaleController.getString("NoOtherSessions", R.string.NoOtherSessions));
        } else {
            textView1.setText(LocaleController.getString("NoOtherWebSessions", R.string.NoOtherWebSessions));
        }
        emptyLayout.addView(textView1, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 0, 16, 0, 0));

        textView2 = new TextView(context);
        textView2.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
        textView2.setGravity(Gravity.CENTER);
        textView2.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
        textView2.setPadding(AndroidUtilities.dp(20), 0, AndroidUtilities.dp(20), 0);
        if (currentType == 0) {
            textView2.setText(LocaleController.getString("NoOtherSessionsInfo", R.string.NoOtherSessionsInfo));
        } else {
            textView2.setText(LocaleController.getString("NoOtherWebSessionsInfo", R.string.NoOtherWebSessionsInfo));
        }
        emptyLayout.addView(textView2, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 0, 14, 0, 0));

        emptyView = new EmptyTextProgressView(context);
        emptyView.showProgress();
        frameLayout.addView(emptyView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER));

        listView = new RecyclerListView(context);
        listView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        listView.setVerticalScrollBarEnabled(false);
        listView.setEmptyView(emptyView);
        listView.setAnimateEmptyView(true, 0);
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        listView.setAdapter(listAdapter);
        listView.setOnItemClickListener((view, position) -> {
            if (position == ttlRow) {
                if (getParentActivity() == null) {
                    return;
                }
                int selected;
                if (ttlDays <= 7) {
                    selected = 0;
                } else if (ttlDays <= 93) {
                    selected = 1;
                } else if (ttlDays <= 183) {
                    selected = 2;
                } else {
                    selected = 3;
                }
                AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                builder.setTitle(LocaleController.getString("SessionsSelfDestruct", R.string.SessionsSelfDestruct));
                String[] items = new String[]{
                        LocaleController.formatPluralString("Weeks", 1),
                        LocaleController.formatPluralString("Months", 3),
                        LocaleController.formatPluralString("Months", 6),
                        LocaleController.formatPluralString("Years", 1)
                };
                final LinearLayout linearLayout = new LinearLayout(getParentActivity());
                linearLayout.setOrientation(LinearLayout.VERTICAL);
                builder.setView(linearLayout);

                for (int a = 0; a < items.length; a++) {
                    RadioColorCell cell = new RadioColorCell(getParentActivity());
                    cell.setPadding(AndroidUtilities.dp(4), 0, AndroidUtilities.dp(4), 0);
                    cell.setTag(a);
                    cell.setCheckColor(Theme.getColor(Theme.key_radioBackground), Theme.getColor(Theme.key_dialogRadioBackgroundChecked));
                    cell.setTextAndValue(items[a], selected == a);
                    linearLayout.addView(cell);
                    cell.setOnClickListener(v -> {
                        builder.getDismissRunnable().run();
                        Integer which = (Integer) v.getTag();

                        int value = 0;
                        if (which == 0) {
                            value = 7;
                        } else if (which == 1) {
                            value = 90;
                        } else if (which == 2) {
                            value = 183;
                        } else if (which == 3) {
                            value = 365;
                        }

                        final TLRPC.TL_account_setAuthorizationTTL req = new TLRPC.TL_account_setAuthorizationTTL();
                        req.authorization_ttl_days = value;
                        ttlDays = value;
                        if (listAdapter != null) {
                            listAdapter.notifyDataSetChanged();
                        }
                        getConnectionsManager().sendRequest(req, (response, error) -> {

                        });
                    });
                }
                builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                showDialog(builder.create());
            } else if (position == terminateAllSessionsRow) {
                if (getParentActivity() == null) {
                    return;
                }
                AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                String buttonText;
                if (currentType == 0) {
                    builder.setMessage(LocaleController.getString("AreYouSureSessions", R.string.AreYouSureSessions));
                    builder.setTitle(LocaleController.getString("AreYouSureSessionsTitle", R.string.AreYouSureSessionsTitle));
                    buttonText = LocaleController.getString("Terminate", R.string.Terminate);
                } else {
                    builder.setMessage(LocaleController.getString("AreYouSureWebSessions", R.string.AreYouSureWebSessions));
                    builder.setTitle(LocaleController.getString("TerminateWebSessionsTitle", R.string.TerminateWebSessionsTitle));
                    buttonText = LocaleController.getString("Disconnect", R.string.Disconnect);
                }
                builder.setPositiveButton(buttonText, (dialogInterface, i) -> {
                    if (currentType == 0) {
                        TLRPC.TL_auth_resetAuthorizations req = new TLRPC.TL_auth_resetAuthorizations();
                        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {
                            AndroidUtilities.runOnUIThread(() -> {
                                if (getParentActivity() == null) {
                                    return;
                                }
                                if (error == null && response instanceof TLRPC.TL_boolTrue) {
                                    Toast toast = Toast.makeText(getParentActivity(), LocaleController.getString("TerminateAllSessions", R.string.TerminateAllSessions), Toast.LENGTH_SHORT);
                                    toast.show();
                                    finishFragment();
                                }
                            });

                            for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
                                UserConfig userConfig = UserConfig.getInstance(a);
                                if (!userConfig.isClientActivated()) {
                                    continue;
                                }
                                userConfig.registeredForPush = false;
                                userConfig.saveConfig(false);
                                MessagesController.getInstance(a).registerForPush(SharedConfig.pushString);
                                ConnectionsManager.getInstance(a).setUserId(userConfig.getClientUserId());
                            }
                        });
                    } else {
                        TLRPC.TL_account_resetWebAuthorizations req = new TLRPC.TL_account_resetWebAuthorizations();
                        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                            if (getParentActivity() == null) {
                                return;
                            }
                            if (error == null && response instanceof TLRPC.TL_boolTrue) {
                                Toast toast = Toast.makeText(getParentActivity(), LocaleController.getString("TerminateAllWebSessions", R.string.TerminateAllWebSessions), Toast.LENGTH_SHORT);
                                toast.show();
                            } else {
                                Toast toast = Toast.makeText(getParentActivity(), LocaleController.getString("UnknownError", R.string.UnknownError), Toast.LENGTH_SHORT);
                                toast.show();
                            }
                            finishFragment();
                        }));
                    }
                });
                builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                AlertDialog alertDialog = builder.create();
                showDialog(alertDialog);
                TextView button = (TextView) alertDialog.getButton(DialogInterface.BUTTON_POSITIVE);
                if (button != null) {
                    button.setTextColor(Theme.getColor(Theme.key_dialogTextRed2));
                }
            } else if (position >= otherSessionsStartRow && position < otherSessionsEndRow || position >= passwordSessionsStartRow && position < passwordSessionsEndRow || position == currentSessionRow) {
                if (getParentActivity() == null) {
                    return;
                }
                if (currentType == 0) {
                    final TLRPC.TL_authorization authorization;
                    boolean isCurrentSession = false;
                    if (position == currentSessionRow) {
                        authorization = currentSession;
                        isCurrentSession = true;
                    } else if (position >= otherSessionsStartRow && position < otherSessionsEndRow) {
                        authorization = (TLRPC.TL_authorization) sessions.get(position - otherSessionsStartRow);
                    } else {
                        authorization = (TLRPC.TL_authorization) passwordSessions.get(position - passwordSessionsStartRow);
                    }
                    showSessionBottomSheet(authorization, isCurrentSession);
                    return;
                }
                AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                final boolean[] param = new boolean[1];
                String buttonText;
                if (currentType == 0) {
                    builder.setMessage(LocaleController.getString("TerminateSessionText", R.string.TerminateSessionText));
                    builder.setTitle(LocaleController.getString("AreYouSureSessionTitle", R.string.AreYouSureSessionTitle));
                    buttonText = LocaleController.getString("Terminate", R.string.Terminate);
                } else {
                    final TLRPC.TL_webAuthorization authorization = (TLRPC.TL_webAuthorization) sessions.get(position - otherSessionsStartRow);

                    builder.setMessage(LocaleController.formatString("TerminateWebSessionText", R.string.TerminateWebSessionText, authorization.domain));
                    builder.setTitle(LocaleController.getString("TerminateWebSessionTitle", R.string.TerminateWebSessionTitle));
                    buttonText = LocaleController.getString("Disconnect", R.string.Disconnect);

                    FrameLayout frameLayout1 = new FrameLayout(getParentActivity());

                    TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(authorization.bot_id);
                    String name;
                    if (user != null) {
                        name = UserObject.getFirstName(user);
                    } else {
                        name = "";
                    }

                    CheckBoxCell cell = new CheckBoxCell(getParentActivity(), 1);
                    cell.setBackgroundDrawable(Theme.getSelectorDrawable(false));
                    cell.setText(LocaleController.formatString("TerminateWebSessionStop", R.string.TerminateWebSessionStop, name), "", false, false);
                    cell.setPadding(LocaleController.isRTL ? AndroidUtilities.dp(16) : AndroidUtilities.dp(8), 0, LocaleController.isRTL ? AndroidUtilities.dp(8) : AndroidUtilities.dp(16), 0);
                    frameLayout1.addView(cell, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.TOP | Gravity.LEFT, 0, 0, 0, 0));
                    cell.setOnClickListener(v -> {
                        if (!v.isEnabled()) {
                            return;
                        }
                        CheckBoxCell cell1 = (CheckBoxCell) v;
                        param[0] = !param[0];
                        cell1.setChecked(param[0], true);
                    });
                    builder.setCustomViewOffset(16);
                    builder.setView(frameLayout1);
                }
                builder.setPositiveButton(buttonText, (dialogInterface, option) -> {
                    if (getParentActivity() == null) {
                        return;
                    }
                    final AlertDialog progressDialog = new AlertDialog(getParentActivity(), 3);
                    progressDialog.setCanCacnel(false);
                    progressDialog.show();

                    if (currentType == 0) {
                        final TLRPC.TL_authorization authorization;
                        if (position >= otherSessionsStartRow && position < otherSessionsEndRow) {
                            authorization = (TLRPC.TL_authorization) sessions.get(position - otherSessionsStartRow);
                        } else {
                            authorization = (TLRPC.TL_authorization) passwordSessions.get(position - passwordSessionsStartRow);
                        }
                        TLRPC.TL_account_resetAuthorization req = new TLRPC.TL_account_resetAuthorization();
                        req.hash = authorization.hash;
                        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                            try {
                                progressDialog.dismiss();
                            } catch (Exception e) {
                                FileLog.e(e);
                            }
                            if (error == null) {
                                sessions.remove(authorization);
                                passwordSessions.remove(authorization);
                                updateRows();
                                if (listAdapter != null) {
                                    listAdapter.notifyDataSetChanged();
                                }
                            }
                        }));
                    } else {
                        final TLRPC.TL_webAuthorization authorization = (TLRPC.TL_webAuthorization) sessions.get(position - otherSessionsStartRow);
                        TLRPC.TL_account_resetWebAuthorization req = new TLRPC.TL_account_resetWebAuthorization();
                        req.hash = authorization.hash;
                        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                            try {
                                progressDialog.dismiss();
                            } catch (Exception e) {
                                FileLog.e(e);
                            }
                            if (error == null) {
                                sessions.remove(authorization);
                                updateRows();
                                if (listAdapter != null) {
                                    listAdapter.notifyDataSetChanged();
                                }
                            }
                        }));
                        if (param[0]) {
                            MessagesController.getInstance(currentAccount).blockPeer(authorization.bot_id);
                        }
                    }
                });
                builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                AlertDialog alertDialog = builder.create();
                showDialog(alertDialog);
                TextView button = (TextView) alertDialog.getButton(DialogInterface.BUTTON_POSITIVE);
                if (button != null) {
                    button.setTextColor(Theme.getColor(Theme.key_dialogTextRed2));
                }
            }
        });

        if (currentType == 0) {
            undoView = new UndoView(context) {
                @Override
                public void hide(boolean apply, int animated) {
                    if (!apply) {
                        TLRPC.TL_authorization authorization = (TLRPC.TL_authorization) getCurrentInfoObject();
                        TLRPC.TL_account_resetAuthorization req = new TLRPC.TL_account_resetAuthorization();
                        req.hash = authorization.hash;
                        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                            if (error == null) {
                                sessions.remove(authorization);
                                passwordSessions.remove(authorization);
                                updateRows();
                                if (listAdapter != null) {
                                    listAdapter.notifyDataSetChanged();
                                }
                                loadSessions(true);
                            }
                        }));
                    }
                    super.hide(apply, animated);
                }
            };
            frameLayout.addView(undoView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | Gravity.LEFT, 8, 0, 8, 8));
        }

        itemsEnterAnimator = new RecyclerItemsEnterAnimator(listView, true) {
            @Override
            public View getProgressView() {
                View progressView = null;
                for (int i = 0; i < listView.getChildCount(); i++) {
                    View child = listView.getChildAt(i);
                    if (listView.getChildAdapterPosition(child) >= 0 && child instanceof SessionCell && ((SessionCell) child).isStub()) {
                        progressView = child;
                    }
                }
                return progressView;
            }
        };
        itemsEnterAnimator.animateAlphaProgressView = false;

        updateRows();
        return fragmentView;
    }


    private void showSessionBottomSheet(TLRPC.TL_authorization authorization, boolean isCurrentSession) {
        if (authorization == null) {
            return;
        }
        SessionBottomSheet bottomSheet = new SessionBottomSheet(this, authorization, isCurrentSession, new SessionBottomSheet.Callback() {
            @Override
            public void onSessionTerminated(TLRPC.TL_authorization authorization) {
                sessions.remove(authorization);
                passwordSessions.remove(authorization);
                updateRows();
                if (listAdapter != null) {
                    listAdapter.notifyDataSetChanged();
                }
                TLRPC.TL_account_resetAuthorization req = new TLRPC.TL_account_resetAuthorization();
                req.hash = authorization.hash;
                ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {

                }));
            }
        });
        bottomSheet.show();

    }

    @Override
    public void onPause() {
        super.onPause();
        if (undoView != null) {
            undoView.hide(true, 0);
        }
    }

    @Override
    protected void onBecomeFullyHidden() {
        if (undoView != null) {
            undoView.hide(true, 0);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.newSessionReceived) {
            loadSessions(true);
        }
    }

    private void loadSessions(boolean silent) {
        if (loading) {
            return;
        }
        if (!silent) {
            loading = true;
        }
        if (currentType == 0) {
            TLRPC.TL_account_getAuthorizations req = new TLRPC.TL_account_getAuthorizations();
            int reqId = ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                loading = false;
                int oldItemsCount = listAdapter.getItemCount();
                if (error == null) {
                    sessions.clear();
                    passwordSessions.clear();
                    TLRPC.TL_account_authorizations res = (TLRPC.TL_account_authorizations) response;
                    for (int a = 0, N = res.authorizations.size(); a < N; a++) {
                        TLRPC.TL_authorization authorization = res.authorizations.get(a);
                        if ((authorization.flags & 1) != 0) {
                            currentSession = authorization;
                        } else if (authorization.password_pending) {
                            passwordSessions.add(authorization);
                        } else {
                            sessions.add(authorization);
                        }
                    }
                    ttlDays = res.authorization_ttl_days;
                    updateRows();
                }
                itemsEnterAnimator.showItemsAnimated(oldItemsCount + 1);
                if (listAdapter != null) {
                    listAdapter.notifyDataSetChanged();
                }
            }));
            ConnectionsManager.getInstance(currentAccount).bindRequestToGuid(reqId, classGuid);
        } else {
            TLRPC.TL_account_getWebAuthorizations req = new TLRPC.TL_account_getWebAuthorizations();
            int reqId = ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                loading = false;
                if (error == null) {
                    sessions.clear();
                    TLRPC.TL_account_webAuthorizations res = (TLRPC.TL_account_webAuthorizations) response;
                    MessagesController.getInstance(currentAccount).putUsers(res.users, false);
                    sessions.addAll(res.authorizations);
                    updateRows();
                }
                itemsEnterAnimator.showItemsAnimated(0);
                if (listAdapter != null) {
                    listAdapter.notifyDataSetChanged();
                }
            }));
            ConnectionsManager.getInstance(currentAccount).bindRequestToGuid(reqId, classGuid);
        }
    }

    private void updateRows() {
        rowCount = 0;
        currentSessionSectionRow = -1;
        currentSessionRow = -1;
        terminateAllSessionsRow = -1;
        terminateAllSessionsDetailRow = -1;
        passwordSessionsSectionRow = -1;
        passwordSessionsStartRow = -1;
        passwordSessionsEndRow = -1;
        passwordSessionsDetailRow = -1;
        otherSessionsSectionRow = -1;
        otherSessionsStartRow = -1;
        otherSessionsEndRow = -1;
        otherSessionsTerminateDetail = -1;
        noOtherSessionsRow = -1;
        qrCodeRow = -1;
        qrCodeDividerRow = -1;
        ttlHeaderRow = -1;
        ttlRow = -1;
        ttlDivideRow = -1;

        boolean hasQr = currentType == 0 && true;
        if (hasQr) {
            qrCodeRow = rowCount++;
            qrCodeDividerRow = rowCount++;
        }
        if (loading) {
            if (currentType == 0) {
                currentSessionSectionRow = rowCount++;
                currentSessionRow = rowCount++;
            }
            return;
        }
        if (currentSession != null) {
            currentSessionSectionRow = rowCount++;
            currentSessionRow = rowCount++;
        }


        if (!passwordSessions.isEmpty() || !sessions.isEmpty()) {
            terminateAllSessionsRow = rowCount++;
            terminateAllSessionsDetailRow = rowCount++;
            noOtherSessionsRow = -1;
        } else {
            terminateAllSessionsRow = -1;
            terminateAllSessionsDetailRow = -1;
            if (currentType == 1 || currentSession != null) {
                noOtherSessionsRow = rowCount++;
            } else {
                noOtherSessionsRow = -1;
            }
        }
        if (!passwordSessions.isEmpty()) {
            passwordSessionsSectionRow = rowCount++;
            passwordSessionsStartRow = rowCount;
            rowCount += passwordSessions.size();
            passwordSessionsEndRow = rowCount;
            passwordSessionsDetailRow = rowCount++;
        }
        if (!sessions.isEmpty()) {
            otherSessionsSectionRow = rowCount++;
            otherSessionsStartRow = rowCount;
            otherSessionsEndRow = rowCount + sessions.size();
            rowCount += sessions.size();
            otherSessionsTerminateDetail = rowCount++;
        }

        if (ttlDays > 0) {
            ttlHeaderRow = rowCount++;
            ttlRow = rowCount++;
            ttlDivideRow = rowCount++;
        }
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {

        private Context mContext;

        public ListAdapter(Context context) {
            mContext = context;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int position = holder.getAdapterPosition();
            return position == terminateAllSessionsRow || position >= otherSessionsStartRow && position < otherSessionsEndRow || position >= passwordSessionsStartRow && position < passwordSessionsEndRow || position == currentSessionRow || position == ttlRow;
        }

        @Override
        public int getItemCount() {
            return rowCount;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case 0:
                    view = new TextCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 1:
                    view = new TextInfoPrivacyCell(mContext);
                    break;
                case 2:
                    view = new HeaderCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 3:
                    view = emptyLayout;
                    break;
                case 5:
                    view = new ScanQRCodeView(mContext);
                    break;
                case 6:
                    view = new TextSettingsCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                default:
                    view = new SessionCell(mContext, currentType);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case 0:
                    TextCell textCell = (TextCell) holder.itemView;
                    if (position == terminateAllSessionsRow) {
                        textCell.setColors(Theme.key_windowBackgroundWhiteRedText2, Theme.key_windowBackgroundWhiteRedText2);
                        textCell.setTag(Theme.key_windowBackgroundWhiteRedText2);
                        if (currentType == 0) {
                            textCell.setTextAndIcon(LocaleController.getString("TerminateAllSessions", R.string.TerminateAllSessions), R.drawable.msg_block2, false);
                        } else {
                            textCell.setTextAndIcon(LocaleController.getString("TerminateAllWebSessions", R.string.TerminateAllWebSessions), R.drawable.msg_block2, false);
                        }
                    } else if (position == qrCodeRow) {
                        textCell.setColors(Theme.key_windowBackgroundWhiteBlueText4, Theme.key_windowBackgroundWhiteBlueText4);
                        textCell.setTag(Theme.key_windowBackgroundWhiteBlueText4);
                        textCell.setTextAndIcon(LocaleController.getString("AuthAnotherClient", R.string.AuthAnotherClient), R.drawable.msg_qrcode, !sessions.isEmpty());
                    }
                    break;
                case 1:
                    TextInfoPrivacyCell privacyCell = (TextInfoPrivacyCell) holder.itemView;
                    privacyCell.setFixedSize(0);
                    if (position == terminateAllSessionsDetailRow) {
                        if (currentType == 0) {
                            privacyCell.setText(LocaleController.getString("ClearOtherSessionsHelp", R.string.ClearOtherSessionsHelp));
                        } else {
                            privacyCell.setText(LocaleController.getString("ClearOtherWebSessionsHelp", R.string.ClearOtherWebSessionsHelp));
                        }
                        privacyCell.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
                    } else if (position == otherSessionsTerminateDetail) {
                        if (currentType == 0) {
                            if (sessions.isEmpty()) {
                                privacyCell.setText("");
                            } else {
                                privacyCell.setText(LocaleController.getString("SessionsListInfo", R.string.SessionsListInfo));
                            }
                        } else {
                            privacyCell.setText(LocaleController.getString("TerminateWebSessionInfo", R.string.TerminateWebSessionInfo));
                        }
                        privacyCell.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    } else if (position == passwordSessionsDetailRow) {
                        privacyCell.setText(LocaleController.getString("LoginAttemptsInfo", R.string.LoginAttemptsInfo));
                        if (otherSessionsTerminateDetail == -1) {
                            privacyCell.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                        } else {
                            privacyCell.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
                        }
                    } else if (position == qrCodeDividerRow || position == ttlDivideRow || position == noOtherSessionsRow) {
                        privacyCell.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
                        privacyCell.setText("");
                        privacyCell.setFixedSize(12);
                    }
                    break;
                case 2:
                    HeaderCell headerCell = (HeaderCell) holder.itemView;
                    if (position == currentSessionSectionRow) {
                        headerCell.setText(LocaleController.getString("CurrentSession", R.string.CurrentSession));
                    } else if (position == otherSessionsSectionRow) {
                        if (currentType == 0) {
                            headerCell.setText(LocaleController.getString("OtherSessions", R.string.OtherSessions));
                        } else {
                            headerCell.setText(LocaleController.getString("OtherWebSessions", R.string.OtherWebSessions));
                        }
                    } else if (position == passwordSessionsSectionRow) {
                        headerCell.setText(LocaleController.getString("LoginAttempts", R.string.LoginAttempts));
                    } else if (position == ttlHeaderRow) {
                        headerCell.setText(LocaleController.getString("TerminateOldSessionHeader", R.string.TerminateOldSessionHeader));
                    }
                    break;
                case 3:
                    ViewGroup.LayoutParams layoutParams = emptyLayout.getLayoutParams();
                    if (layoutParams != null) {
                        layoutParams.height = Math.max(AndroidUtilities.dp(220), AndroidUtilities.displaySize.y - ActionBar.getCurrentActionBarHeight() - AndroidUtilities.dp(128 + (qrCodeRow == -1 ? 0 : 30)) - (Build.VERSION.SDK_INT >= 21 ? AndroidUtilities.statusBarHeight : 0));
                        emptyLayout.setLayoutParams(layoutParams);
                    }
                    break;
                case 5:
                    break;
                case 6:
                    TextSettingsCell textSettingsCell = (TextSettingsCell) holder.itemView;
                    String value;
                    if (ttlDays > 30 && ttlDays <= 183) {
                        value = LocaleController.formatPluralString("Months", ttlDays / 30);
                    } else if (ttlDays == 365) {
                        value = LocaleController.formatPluralString("Years", ttlDays / 365);
                    } else {
                        value = LocaleController.formatPluralString("Weeks", ttlDays / 7);
                    }
                    textSettingsCell.setTextAndValue(LocaleController.getString("IfInactiveFor", R.string.IfInactiveFor), value, false);
                    break;
                default:
                    SessionCell sessionCell = (SessionCell) holder.itemView;
                    if (position == currentSessionRow) {
                        if (currentSession == null) {
                            sessionCell.showStub(globalFlickerLoadingView);
                        } else {
                            sessionCell.setSession(currentSession, !sessions.isEmpty() || !passwordSessions.isEmpty() || qrCodeRow != -1);
                        }
                    } else if (position >= otherSessionsStartRow && position < otherSessionsEndRow) {
                        sessionCell.setSession(sessions.get(position - otherSessionsStartRow), position != otherSessionsEndRow - 1);
                    } else if (position >= passwordSessionsStartRow && position < passwordSessionsEndRow) {
                        sessionCell.setSession(passwordSessions.get(position - passwordSessionsStartRow), position != passwordSessionsEndRow - 1);
                    }
                    break;
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == terminateAllSessionsRow) {
                return 0;
            } else if (position == terminateAllSessionsDetailRow || position == otherSessionsTerminateDetail || position == passwordSessionsDetailRow || position == qrCodeDividerRow || position == ttlDivideRow || position == noOtherSessionsRow) {
                return 1;
            } else if (position == currentSessionSectionRow || position == otherSessionsSectionRow || position == passwordSessionsSectionRow || position == ttlHeaderRow) {
                return 2;
            } else if (position == currentSessionRow || position >= otherSessionsStartRow && position < otherSessionsEndRow || position >= passwordSessionsStartRow && position < passwordSessionsEndRow) {
                return 4;
            } else if (position == qrCodeRow) {
                return 5;
            } else if (position == ttlRow) {
                return 6;
            }
            return 0;
        }
    }

    private class ScanQRCodeView extends FrameLayout implements NotificationCenter.NotificationCenterDelegate {

        BackupImageView imageView;
        TextView textView;

        public ScanQRCodeView(@NonNull Context context) {
            super(context);
            imageView = new BackupImageView(context);
            addView(imageView, LayoutHelper.createFrame(120, 120, Gravity.CENTER_HORIZONTAL, 0, 16, 0, 0));

            imageView.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (imageView.getImageReceiver().getLottieAnimation() != null && !imageView.getImageReceiver().getLottieAnimation().isRunning()) {
                        imageView.getImageReceiver().getLottieAnimation().setCurrentFrame(0, false);
                        imageView.getImageReceiver().getLottieAnimation().restart();
                    }
                }
            });
            int[] colors = new int[8];
            colors[0] = 0x333333;
            colors[1] = Theme.getColor(Theme.key_windowBackgroundWhiteBlackText);

            colors[2] = 0xffffff;
            colors[3] = Theme.getColor(Theme.key_windowBackgroundWhite);

            colors[4] = 0x50a7ea;
            colors[5] = Theme.getColor(Theme.key_featuredStickers_addButton);

            colors[6] = 0x212020;
            colors[7] = Theme.getColor(Theme.key_windowBackgroundWhite);

//            imageView.replaceColors(colors);
//            imageView.setAnimation(R.raw.qr_login, 230, 230, colors);
//            imageView.setScaleType(ImageView.ScaleType.CENTER);
//            imageView.playAnimation();

            textView = new TextView(context);
            addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 36, 152, 36, 0));
            textView.setGravity(Gravity.CENTER_HORIZONTAL);
            textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            textView.setLinkTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteLinkText));
            textView.setHighlightColor(Theme.getColor(Theme.key_windowBackgroundWhiteLinkSelection));
            setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));

            String text = LocaleController.getString("AuthAnotherClientInfo4", R.string.AuthAnotherClientInfo4);
            SpannableStringBuilder spanned = new SpannableStringBuilder(text);
            int index1 = text.indexOf('*');
            int index2 = text.indexOf('*', index1 + 1);

            if (index1 != -1 && index2 != -1 && index1 != index2) {
                textView.setMovementMethod(new AndroidUtilities.LinkMovementMethodMy());
                spanned.replace(index2, index2 + 1, "");
                spanned.replace(index1, index1 + 1, "");
                spanned.setSpan(new URLSpanNoUnderline(LocaleController.getString("AuthAnotherClientDownloadClientUrl", R.string.AuthAnotherClientDownloadClientUrl)), index1, index2 - 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }

            text = spanned.toString();
            index1 = text.indexOf('*');
            index2 = text.indexOf('*', index1 + 1);

            if (index1 != -1 && index2 != -1 && index1 != index2) {
                textView.setMovementMethod(new AndroidUtilities.LinkMovementMethodMy());
                spanned.replace(index2, index2 + 1, "");
                spanned.replace(index1, index1 + 1, "");
                spanned.setSpan(new URLSpanNoUnderline(LocaleController.getString("AuthAnotherWebClientUrl", R.string.AuthAnotherWebClientUrl)), index1, index2 - 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }

            textView.setText(spanned);

            TextView buttonTextView = new TextView(context);
            buttonTextView.setPadding(AndroidUtilities.dp(34), 0, AndroidUtilities.dp(34), 0);
            buttonTextView.setGravity(Gravity.CENTER);
            buttonTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            buttonTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));

            SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder();
            spannableStringBuilder.append(".  ").append(LocaleController.getString("LinkDesktopDevice", R.string.LinkDesktopDevice));
            spannableStringBuilder.setSpan(new ColoredImageSpan(ContextCompat.getDrawable(getContext(), R.drawable.msg_mini_qr)), 0, 1, 0);
            buttonTextView.setText(spannableStringBuilder);

            buttonTextView.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText));
            buttonTextView.setBackgroundDrawable(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(6), Theme.getColor(Theme.key_featuredStickers_addButton), Theme.getColor(Theme.key_featuredStickers_addButtonPressed)));

            buttonTextView.setOnClickListener(view -> {
                if (getParentActivity() == null) {
                    return;
                }
                if (Build.VERSION.SDK_INT >= 23 && getParentActivity().checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    getParentActivity().requestPermissions(new String[]{Manifest.permission.CAMERA}, ActionIntroActivity.CAMERA_PERMISSION_REQUEST_CODE);
                    return;
                }
                CameraScanActivity.showAsSheet(SessionsActivity.this, false, CameraScanActivity.TYPE_QR_LOGIN, new CameraScanActivity.CameraScanActivityDelegate() {
                    @Override
                    public void didFindQr(String text) {
                        proccessQrCode(text);
                    }
                });
            });
            addView(buttonTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.BOTTOM, 16, 15, 16, 16));

            setSticker();
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(276), MeasureSpec.EXACTLY));
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            setSticker();
            NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.diceStickersDidLoad);
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.diceStickersDidLoad);
        }

        @Override
        public void didReceivedNotification(int id, int account, Object... args) {
            if (id == NotificationCenter.diceStickersDidLoad) {
                String name = (String) args[0];
                if (AndroidUtilities.STICKERS_PLACEHOLDER_PACK_NAME.equals(name)) {
                    setSticker();
                }
            }
        }

        private void setSticker() {
            String imageFilter = null;
            TLRPC.Document document = null;
            TLRPC.TL_messages_stickerSet set = null;

            set = MediaDataController.getInstance(currentAccount).getStickerSetByName(AndroidUtilities.STICKERS_PLACEHOLDER_PACK_NAME);
            if (set == null) {
                set = MediaDataController.getInstance(currentAccount).getStickerSetByEmojiOrName(AndroidUtilities.STICKERS_PLACEHOLDER_PACK_NAME);
            }
            if (set != null && set.documents.size() > 6) {
                document = set.documents.get(6);
            }
            imageFilter = "130_130";

            SvgHelper.SvgDrawable svgThumb = null;
            if (document != null) {
                svgThumb = DocumentObject.getSvgThumb(document.thumbs, Theme.key_emptyListPlaceholder, 0.2f);
            }
            if (svgThumb != null) {
                svgThumb.overrideWidthAndHeight(512, 512);
            }

            if (document != null) {
                ImageLocation imageLocation = ImageLocation.getForDocument(document);
                imageView.setImage(imageLocation, imageFilter, "tgs", svgThumb, set);
                imageView.getImageReceiver().setAutoRepeat(2);
            } else {
                MediaDataController.getInstance(currentAccount).loadStickersByEmojiOrName(AndroidUtilities.STICKERS_PLACEHOLDER_PACK_NAME, false, set == null);
            }
        }
    }

    private void proccessQrCode(String code) {
        AlertDialog progressDialog = new AlertDialog(getParentActivity(), 3);
        progressDialog.setCanCacnel(false);
        progressDialog.show();
        byte[] token = Base64.decode(code.substring("tg://login?token=".length()), Base64.URL_SAFE);
        TLRPC.TL_auth_acceptLoginToken req = new TLRPC.TL_auth_acceptLoginToken();
        req.token = token;
        getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            try {
                progressDialog.dismiss();
            } catch (Exception ignore) {

            }
            if (response instanceof TLRPC.TL_authorization) {
                TLRPC.TL_authorization authorization = (TLRPC.TL_authorization) response;
                sessions.add(0, authorization);
                updateRows();
                listAdapter.notifyDataSetChanged();
                undoView.showWithAction(0, UndoView.ACTION_QR_SESSION_ACCEPTED, response);
            } else {
                AndroidUtilities.runOnUIThread(() -> {
                    final String text;
                    if (error.text.equals("AUTH_TOKEN_EXCEPTION")) {
                        text = LocaleController.getString("AccountAlreadyLoggedIn", R.string.AccountAlreadyLoggedIn);
                    } else {
                        text = LocaleController.getString("ErrorOccurred", R.string.ErrorOccurred) + "\n" + error.text;
                    }
                    AlertsCreator.showSimpleAlert(SessionsActivity.this, LocaleController.getString("AuthAnotherClient", R.string.AuthAnotherClient), text);
                });
            }
        }));
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> themeDescriptions = new ArrayList<>();

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{TextSettingsCell.class, HeaderCell.class, SessionCell.class}, null, null, null, Theme.key_windowBackgroundWhite));
        themeDescriptions.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundGray));

        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_actionBarDefault));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{View.class}, Theme.dividerPaint, null, null, Theme.key_divider));

        themeDescriptions.add(new ThemeDescription(imageView, ThemeDescription.FLAG_IMAGECOLOR, null, null, null, null, Theme.key_sessions_devicesImage));
        themeDescriptions.add(new ThemeDescription(textView1, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteGrayText2));
        themeDescriptions.add(new ThemeDescription(textView2, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteGrayText2));
        themeDescriptions.add(new ThemeDescription(emptyView, ThemeDescription.FLAG_PROGRESSBAR, null, null, null, null, Theme.key_progressCircle));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_TEXTCOLOR | ThemeDescription.FLAG_CHECKTAG, new Class[]{TextSettingsCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteRedText2));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_TEXTCOLOR | ThemeDescription.FLAG_CHECKTAG, new Class[]{TextSettingsCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueText4));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{TextInfoPrivacyCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextInfoPrivacyCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText4));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{HeaderCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueHeader));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{SessionCell.class}, new String[]{"nameTextView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CHECKTAG, new Class[]{SessionCell.class}, new String[]{"onlineTextView"}, null, null, null, Theme.key_windowBackgroundWhiteValueText));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CHECKTAG, new Class[]{SessionCell.class}, new String[]{"onlineTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText3));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{SessionCell.class}, new String[]{"detailTextView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{SessionCell.class}, new String[]{"detailExTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText3));

        themeDescriptions.add(new ThemeDescription(undoView, ThemeDescription.FLAG_BACKGROUNDFILTER, null, null, null, null, Theme.key_undo_background));
        themeDescriptions.add(new ThemeDescription(undoView, 0, new Class[]{UndoView.class}, new String[]{"undoImageView"}, null, null, null, Theme.key_windowBackgroundWhiteRedText2));
        themeDescriptions.add(new ThemeDescription(undoView, 0, new Class[]{UndoView.class}, new String[]{"undoTextView"}, null, null, null, Theme.key_windowBackgroundWhiteRedText2));
        themeDescriptions.add(new ThemeDescription(undoView, 0, new Class[]{UndoView.class}, new String[]{"infoTextView"}, null, null, null, Theme.key_undo_infoColor));
        themeDescriptions.add(new ThemeDescription(undoView, 0, new Class[]{UndoView.class}, new String[]{"textPaint"}, null, null, null, Theme.key_undo_infoColor));
        themeDescriptions.add(new ThemeDescription(undoView, 0, new Class[]{UndoView.class}, new String[]{"progressPaint"}, null, null, null, Theme.key_undo_infoColor));
        themeDescriptions.add(new ThemeDescription(undoView, ThemeDescription.FLAG_IMAGECOLOR, new Class[]{UndoView.class}, new String[]{"leftImageView"}, null, null, null, Theme.key_undo_infoColor));

        return themeDescriptions;
    }

    @Override
    public void onRequestPermissionsResultFragment(int requestCode, String[] permissions, int[] grantResults) {
        if (getParentActivity() == null) {
            return;
        }
        if (requestCode == ActionIntroActivity.CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                CameraScanActivity.showAsSheet(SessionsActivity.this, false, CameraScanActivity.TYPE_QR_LOGIN, new CameraScanActivity.CameraScanActivityDelegate() {
                    @Override
                    public void didFindQr(String text) {
                        proccessQrCode(text);
                    }
                });
            }
        }
    }
}
