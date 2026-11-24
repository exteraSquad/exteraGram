/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.pdf.ui;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.Manifest;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.os.Build;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;
import androidx.core.view.WindowInsetsCompat;

import com.exteragram.messenger.pdf.PdfDocumentRenderer;
import com.exteragram.messenger.utils.chats.ChatUtils;
import com.exteragram.messenger.utils.ui.ChatHeaderUiHelper;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.FlagSecureReason;
import org.telegram.messenger.LiteMode;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.AnimatedTextView;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.TranslateAlert2;
import org.telegram.ui.Components.blur3.BlurredBackgroundDrawableViewFactory;
import org.telegram.ui.Components.blur3.DownscaleScrollableNoiseSuppressor;
import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable;
import org.telegram.ui.Components.blur3.drawable.color.BlurredBackgroundColorProvider;
import org.telegram.ui.Components.blur3.drawable.color.impl.BlurredBackgroundProviderImpl;
import org.telegram.ui.Components.chat.ViewPositionWatcher;
import org.telegram.ui.Components.chat.layouts.ChatActivityFadeView;
import org.telegram.ui.Components.chat.layouts.ChatActivitySideControlsButtonsLayout;
import org.telegram.ui.Components.inset.WindowAnimatedInsetsProvider;
import org.telegram.ui.Components.inset.WindowInsetsStateHolder;
import org.telegram.ui.LaunchActivity;
import org.telegram.utils.glass.source.GlassSourceColor;
import org.telegram.utils.glass.source.GlassSourceRenderNode;

import java.io.File;
import java.util.ArrayList;
import java.util.Locale;

public class PdfViewerActivity extends BaseFragment {

    private static final int MENU_SEARCH = 1;
    private static final int MENU_OTHER = 2;
    private static final int MENU_OPEN_IN = 3;
    private static final int MENU_SHARE = 4;
    private static final int MENU_SAVE_TO_DOWNLOADS = 5;
    private static final long SEARCH_DELAY = 300;
    private static final int SEARCH_PANEL_HEIGHT_DP = 64;

    private final MessageObject messageObject;
    private final File file;
    private final PdfDocumentRenderer renderer;
    private final boolean noforwards;

    private PdfPagesView pagesView;
    private ChatActivityFadeView fadeView;
    private ActionBarMenuItem otherItem;
    private SearchCounterView searchCounterView;
    private ChatActivitySideControlsButtonsLayout searchButtonsLayout;
    private FlagSecureReason flagSecure;

    private boolean searching;
    private boolean searchPanelShown;
    private String pendingQuery;
    private final Runnable searchRunnable = () -> {
        if (pagesView != null) {
            pagesView.search(pendingQuery);
        }
    };
    private int topInset;
    private int bottomInset;
    private final WindowInsetsStateHolder windowInsetsStateHolder = new WindowInsetsStateHolder(this::updateBottomControlsPosition);
    private WindowAnimatedInsetsProvider animatedInsetsProvider;

    private final DownscaleScrollableNoiseSuppressor scrollableViewNoiseSuppressor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? new DownscaleScrollableNoiseSuppressor() : null;
    private final GlassSourceColor iBlur3SourceColor = new GlassSourceColor();
    private final GlassSourceRenderNode iBlur3SourceGlass = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? new GlassSourceRenderNode(iBlur3SourceColor) : null;
    private final ArrayList<RectF> iBlur3Positions = new ArrayList<>();
    private final RectF iBlur3PositionActionBar = new RectF();
    private final RectF iBlur3PositionBottom = new RectF();

    private ValueAnimator headerFadeAnimator;
    private boolean headerFadeShown;
    private float headerFadeAlpha;

    public static boolean isPdf(MessageObject message) {
        if (message == null || message.getDocument() == null) {
            return false;
        }
        final String mime = message.getMimeType();
        return mime != null && mime.toLowerCase(Locale.ROOT).startsWith("application/pdf") || "PDF".equals(message.getExtension());
    }

    public static boolean openWebPageDocument(BaseFragment fragment, MessageObject message) {
        if (fragment == null || !isPdf(message)) {
            return false;
        }
        if (!TextUtils.isEmpty(ChatUtils.getInstance().getPathToMessage(message))) {
            return open(fragment, message);
        }
        FileLoader.getInstance(message.currentAccount).loadFile(message.getDocument(), message, FileLoader.PRIORITY_NORMAL, 0);
        return true;
    }

    public static boolean open(BaseFragment fragment, MessageObject message) {
        final Activity activity = fragment == null ? null : fragment.getParentActivity();
        if (activity == null) {
            return false;
        }
        final String path = ChatUtils.getInstance().getPathToMessage(message);
        if (TextUtils.isEmpty(path)) {
            return false;
        }
        final File file = new File(path);
        final AlertDialog progress = new AlertDialog(activity, AlertDialog.ALERT_TYPE_SPINNER, fragment.getResourceProvider());
        progress.setCanceledOnTouchOutside(false);
        final boolean[] cancelled = {false};
        progress.setOnCancelListener(d -> cancelled[0] = true);
        progress.showDelayed(150);
        PdfDocumentRenderer.open(file, (renderer, error) -> {
            try {
                progress.dismiss();
            } catch (Throwable ignore) {

            }
            if (renderer == null) {
                FileLog.e(error);
                if (!cancelled[0] && fragment.getParentActivity() != null) {
                    openExternally(fragment, message);
                }
                return;
            }
            if (cancelled[0] || fragment.getParentActivity() == null) {
                renderer.close();
                return;
            }
            final PdfViewerActivity viewer = new PdfViewerActivity(message, file, renderer);
            if (!fragment.presentFragment(viewer) && viewer.getParentLayout() == null) {
                renderer.close();
            }
        });
        return true;
    }

    private static void openExternally(BaseFragment fragment, MessageObject message) {
        try {
            if (AndroidUtilities.openForView(message, fragment.getParentActivity(), fragment.getResourceProvider(), false)) {
                return;
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        BulletinFactory.of(fragment).createErrorBulletin(LocaleController.formatString(R.string.NoHandleAppInstalled, message.getMimeType())).show();
    }

    private PdfViewerActivity(MessageObject messageObject, File file, PdfDocumentRenderer renderer) {
        this.messageObject = messageObject;
        this.file = file;
        this.renderer = renderer;
        setCurrentAccount(messageObject.currentAccount);
        final long dialogId = messageObject.getDialogId();
        noforwards = messageObject.messageOwner.noforwards || DialogObject.isEncryptedDialog(dialogId) || MessagesController.getInstance(currentAccount).isPeerNoForwards(dialogId);
        iBlur3Positions.add(iBlur3PositionActionBar);
        iBlur3Positions.add(iBlur3PositionBottom);
    }

    @Override
    public View createView(Context context) {
        final String title = messageObject.getDocumentName();
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(false);
        actionBar.setTitle(TextUtils.isEmpty(title) ? file.getName() : title);
        actionBar.setForceDisableCenterTitle(true);
        actionBar.setCastShadows(false);
        actionBar.setAddToContainer(false);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == MENU_OPEN_IN) {
                    openExternally(PdfViewerActivity.this, messageObject);
                } else if (id == MENU_SAVE_TO_DOWNLOADS) {
                    saveToDownloads();
                } else if (id == MENU_SHARE) {
                    share();
                }
            }
        });

        final ContentView contentView = new ContentView(context);
        contentView.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundGray));

        final ActionBarMenu menu = actionBar.createMenu();

        iBlur3SourceColor.setColor(getThemedColor(Theme.key_windowBackgroundGray));
        final BlurredBackgroundDrawableViewFactory glassFactory;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && iBlur3SourceGlass != null) {
            glassFactory = new BlurredBackgroundDrawableViewFactory(iBlur3SourceGlass);
            glassFactory.setLiquidGlassEffectAllowed(LiteMode.isEnabled(LiteMode.FLAG_LIQUID_GLASS));
        } else {
            glassFactory = new BlurredBackgroundDrawableViewFactory(iBlur3SourceColor);
        }
        glassFactory.setSourceRootView(new ViewPositionWatcher(contentView), contentView);
        final BlurredBackgroundColorProvider glassColorProvider = BlurredBackgroundProviderImpl.topPanelChatActivity(getResourceProvider());

        actionBar.setGlassPadding(dp(12));
        actionBar.setGlassTitleTextSize(20);
        actionBar.setupGlass(glassFactory, glassColorProvider);
        actionBar.setMenuGlass(new BlurredBackgroundDrawableViewFactory(iBlur3SourceColor), null);
        actionBar.setMenuRise(dp(8));
        actionBar.setMenuOverlayProvider(this::getSimpleMenuOverlay);
        ChatHeaderUiHelper.applyChatHeaderGlassStyle(actionBar, true);
        actionBar.setGlassSearchMorph(true);
        actionBar.setTitleColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        actionBar.setItemsColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText), false);
        actionBar.setItemsBackgroundColor(getThemedColor(Theme.key_listSelector), false);

        if (renderer.supportsSearch) {
            final ActionBarMenuItem searchItem = menu.addItem(MENU_SEARCH, R.drawable.outline_header_search).setIsSearchField(true).setActionBarMenuItemSearchListener(new ActionBarMenuItem.ActionBarMenuItemSearchListener() {
                @Override
                public void onSearchExpand() {
                    searching = true;
                    if (otherItem != null) {
                        otherItem.setVisibility(View.GONE);
                    }
                }

                @Override
                public void onSearchCollapse() {
                    searching = false;
                    pendingQuery = null;
                    AndroidUtilities.cancelRunOnUIThread(searchRunnable);
                    if (otherItem != null) {
                        otherItem.setVisibility(View.VISIBLE);
                    }
                    if (pagesView != null) {
                        pagesView.search(null);
                    }
                }

                @Override
                public void onTextChanged(EditText editText) {
                    pendingQuery = editText.getText().toString();
                    AndroidUtilities.cancelRunOnUIThread(searchRunnable);
                    AndroidUtilities.runOnUIThread(searchRunnable, SEARCH_DELAY);
                }

                @Override
                public void onSearchPressed(EditText editText) {
                    pendingQuery = editText.getText().toString();
                    AndroidUtilities.cancelRunOnUIThread(searchRunnable);
                    searchRunnable.run();
                    AndroidUtilities.hideKeyboard(editText);
                }
            });
            searchItem.setSearchPaddingStart(7);
            searchItem.setSearchFieldHint(getString(R.string.Search));
            searchItem.setContentDescription(getString(R.string.Search));
        }
        if (!noforwards) {
            otherItem = menu.addItem(MENU_OTHER, R.drawable.ic_ab_other);
            otherItem.setContentDescription(getString(R.string.AccDescrMoreOptions));
            otherItem.addSubItem(MENU_OPEN_IN, R.drawable.msg_openin, getString(R.string.OpenInExternalApp));
            otherItem.addSubItem(MENU_SAVE_TO_DOWNLOADS, R.drawable.msg_download, getString(R.string.SaveToDownloads));
            otherItem.addSubItem(MENU_SHARE, R.drawable.msg_shareout, getString(R.string.ShareFile));
        } else {
            flagSecure = new FlagSecureReason(getParentActivity().getWindow(), () -> true);
        }

        pagesView = new PdfPagesView(context, renderer, new PdfPagesView.Delegate() {
            @Override
            public void onLinkClick(String url) {
                AlertsCreator.showOpenUrlAlert(PdfViewerActivity.this, url, true, true);
            }

            @Override
            public void onViewportChanged(float dx, float dy) {
                updateHeaderFade(true);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && scrollableViewNoiseSuppressor != null) {
                    scrollableViewNoiseSuppressor.onScrolled(dx, dy);
                    blur3_InvalidateBlur();
                }
            }

            @Override
            public void onSearchUpdated(int current, int total, boolean finished) {
                updateSearchPanel(current, total, finished);
            }

            @Override
            public void onTextCopied() {
                BulletinFactory.of(PdfViewerActivity.this).createCopyBulletin(getString(R.string.TextCopied)).show();
            }

            @Override
            public void onTranslateText(CharSequence text, String fromLanguage, Runnable onDismiss) {
                TranslateAlert2.showAlert(getParentActivity(), PdfViewerActivity.this, currentAccount, fromLanguage, TranslateAlert2.getToLanguage(), text, null, false, null, onDismiss);
            }

            @Override
            public void onShareText(String text) {
                shareText(text);
            }
        }, getResourceProvider());
        pagesView.setTextSelectionEnabled(!noforwards);
        contentView.addView(pagesView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        fadeView = new ChatActivityFadeView(context);
        fadeView.setupColorKey(Theme.key_windowBackgroundGray);
        fadeView.setFadeTopAlpha(0);
        contentView.addView(fadeView, LayoutHelper.createFrameMatchParent());
        contentView.addView(pagesView.createOverlayView(context), LayoutHelper.createFrameMatchParent());

        if (renderer.supportsSearch) {
            searchCounterView = new SearchCounterView(context, glassFactory, glassColorProvider);
            searchCounterView.setVisibility(View.GONE);
            contentView.addView(searchCounterView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.LEFT | Gravity.BOTTOM, 7, 0, 70, 0));

            searchButtonsLayout = new ChatActivitySideControlsButtonsLayout(context, getResourceProvider(), glassColorProvider, glassFactory);
            searchButtonsLayout.setOnClickListener((buttonId, v) -> {
                if (pagesView != null) {
                    pagesView.searchNavigate(buttonId == ChatActivitySideControlsButtonsLayout.BUTTON_SEARCH_DOWN);
                }
            });
            contentView.addView(searchButtonsLayout, LayoutHelper.createFrame(57, 300, Gravity.RIGHT | Gravity.BOTTOM, 0, 0, 2, 0));
            updateBottomControlsPosition();
        }

        contentView.addView(actionBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP));

        if (context instanceof LaunchActivity) {
            animatedInsetsProvider = ((LaunchActivity) context).getRootAnimatedInsetsListener();
            windowInsetsStateHolder.setupAnimatedInsetsProvider(animatedInsetsProvider, contentView);
        }
        return fragmentView = contentView;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (flagSecure != null) {
            flagSecure.attach();
        }
        Bulletin.addDelegate(this, new Bulletin.Delegate() {
            @Override
            public int getTopOffset(int tag) {
                return topInset;
            }

            @Override
            public int getBottomOffset(int tag) {
                return getPagesBottomInset();
            }
        });
    }

    @Override
    public void onPause() {
        super.onPause();
        Bulletin.removeDelegate(this);
    }

    @Override
    public void onBecomeFullyHidden() {
        super.onBecomeFullyHidden();
        if (flagSecure != null) {
            flagSecure.detach();
        }
    }

    @Override
    public void onFragmentDestroy() {
        AndroidUtilities.cancelRunOnUIThread(searchRunnable);
        if (animatedInsetsProvider != null) {
            animatedInsetsProvider.unsubscribeFromWindowInsetsAnimation(windowInsetsStateHolder);
            animatedInsetsProvider = null;
        }
        if (headerFadeAnimator != null) {
            headerFadeAnimator.cancel();
            headerFadeAnimator = null;
        }
        if (flagSecure != null) {
            flagSecure.detach();
        }
        if (pagesView != null) {
            pagesView.destroy();
        }
        renderer.close();
        super.onFragmentDestroy();
    }

    @Override
    public boolean onBackPressed(boolean invoked) {
        if (pagesView != null && pagesView.hasTextSelection()) {
            if (invoked) {
                pagesView.clearTextSelection();
            }
            return false;
        }
        if (searching) {
            if (invoked) {
                actionBar.closeSearchField();
            }
            return false;
        }
        return super.onBackPressed(invoked);
    }

    @Override
    public boolean isSwipeBackEnabled(MotionEvent event) {
        return pagesView == null || !pagesView.isZoomed();
    }

    @Override
    public boolean isLightStatusBar() {
        if (actionBar == null) {
            return super.isLightStatusBar();
        }
        return ChatHeaderUiHelper.isLightChatStatusBar(actionBar, getThemedColor(Theme.key_windowBackgroundGray), true);
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean isSupportEdgeToEdge() {
        return true;
    }

    @Override
    public WindowInsetsCompat onInsetsInternal(@NonNull View view, @NonNull WindowInsetsCompat windowInsets) {
        final WindowInsetsCompat result = super.onInsetsInternal(view, windowInsets);
        windowInsetsStateHolder.setInsets(windowInsets);
        return result;
    }

    @Override
    public void onInsets(int left, int top, int right, int bottom) {
        topInset = top + ActionBar.getCurrentActionBarHeight();
        bottomInset = bottom;
        updateBottomControlsPosition();
    }

    private int getKeyboardAwareBottomInset() {
        return Math.max(bottomInset, Math.round(windowInsetsStateHolder.getAnimatedMaxBottomInset()));
    }

    private int getPagesBottomInset() {
        return getKeyboardAwareBottomInset() + (searchPanelShown ? dp(SEARCH_PANEL_HEIGHT_DP) : 0);
    }

    private void updateBottomControlsPosition() {
        final int bottom = getKeyboardAwareBottomInset();
        if (searchButtonsLayout != null) {
            searchButtonsLayout.setTranslationY(-bottom - dp(4));
        }
        if (searchCounterView != null) {
            searchCounterView.setTranslationY(-bottom - dp(8));
        }
        if (pagesView != null) {
            pagesView.setInsets(topInset, getPagesBottomInset());
        }
    }

    private void updateSearchPanel(int current, int total, boolean finished) {
        if (searchCounterView == null || searchButtonsLayout == null) {
            return;
        }
        final boolean show = searching && !TextUtils.isEmpty(pendingQuery == null ? null : pendingQuery.trim());
        if (show) {
            final CharSequence text;
            if (total <= 0) {
                text = getString(finished ? R.string.NoResult : R.string.Loading);
            } else {
                text = LocaleController.formatString(R.string.Of, current + 1, total);
            }
            searchCounterView.textView.setText(text, searchPanelShown);
        }
        searchButtonsLayout.showButton(ChatActivitySideControlsButtonsLayout.BUTTON_SEARCH_DOWN, show && total > 0, true);
        searchButtonsLayout.showButton(ChatActivitySideControlsButtonsLayout.BUTTON_SEARCH_UP, show && total > 0, true);
        searchButtonsLayout.setButtonEnabled(ChatActivitySideControlsButtonsLayout.BUTTON_SEARCH_DOWN, total > 1, true);
        searchButtonsLayout.setButtonEnabled(ChatActivitySideControlsButtonsLayout.BUTTON_SEARCH_UP, total > 1, true);
        if (searchPanelShown != show) {
            searchPanelShown = show;
            searchCounterView.animate().cancel();
            if (show) {
                searchCounterView.setVisibility(View.VISIBLE);
                searchCounterView.setAlpha(0f);
                searchCounterView.setScaleX(.7f);
                searchCounterView.setScaleY(.7f);
            }
            searchCounterView.animate()
                    .alpha(show ? 1f : 0f)
                    .scaleX(show ? 1f : .7f)
                    .scaleY(show ? 1f : .7f)
                    .setDuration(280)
                    .setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT)
                    .withEndAction(() -> {
                        if (!searchPanelShown) {
                            searchCounterView.setVisibility(View.GONE);
                        }
                    })
                    .start();
            updateBottomControlsPosition();
        }
    }

    private void blur3_InvalidateBlur() {
        if (fragmentView == null || pagesView == null) {
            return;
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || scrollableViewNoiseSuppressor == null || !SharedConfig.chatBlurEnabled()) {
            return;
        }
        final int width = fragmentView.getMeasuredWidth();
        final int height = fragmentView.getMeasuredHeight();
        final int additional = dp(48);
        iBlur3PositionActionBar.set(0, -additional, width, actionBar.getMeasuredHeight() + additional);
        iBlur3PositionBottom.set(0, height - getKeyboardAwareBottomInset() - dp(160), width, height);
        scrollableViewNoiseSuppressor.setupRenderNodes(iBlur3Positions, searching ? 2 : 1);
        scrollableViewNoiseSuppressor.invalidateResultRenderNodes(pagesView, width, height);
    }

    private void updateTopFade() {
        if (fadeView == null) {
            return;
        }
        ChatHeaderUiHelper.setupChatTopFade(fadeView, actionBar, getThemedColor(Theme.key_windowBackgroundGray), actionBar.getMeasuredHeight(), true);
        fadeView.setFadeTopAlpha(Math.round(headerFadeAlpha * 255));
    }

    private void updateHeaderFade(boolean animated) {
        if (fadeView == null || pagesView == null) {
            return;
        }
        final boolean shown = pagesView.canScrollVertically(-1);
        if (headerFadeShown == shown) {
            return;
        }
        headerFadeShown = shown;
        if (headerFadeAnimator != null) {
            headerFadeAnimator.cancel();
            headerFadeAnimator = null;
        }
        if (!animated) {
            setHeaderFadeAlpha(shown ? 1f : 0f);
            return;
        }
        headerFadeAnimator = ValueAnimator.ofFloat(headerFadeAlpha, shown ? 1f : 0f);
        headerFadeAnimator.addUpdateListener(animation -> setHeaderFadeAlpha((float) animation.getAnimatedValue()));
        headerFadeAnimator.setDuration(320);
        headerFadeAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        headerFadeAnimator.start();
    }

    private void setHeaderFadeAlpha(float alpha) {
        headerFadeAlpha = alpha;
        if (fadeView != null) {
            fadeView.setFadeTopAlpha(Math.round(alpha * 255));
        }
    }

    private void shareText(String text) {
        final Activity activity = getParentActivity();
        if (activity == null) {
            return;
        }
        try {
            final Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_TEXT, text);
            activity.startActivityForResult(Intent.createChooser(intent, getString(R.string.ShareFile)), 500);
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private void saveToDownloads() {
        final Activity activity = getParentActivity();
        if (activity == null) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P || BuildVars.NO_SCOPED_STORAGE) && activity.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            activity.requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 4);
            return;
        }
        final ArrayList<MessageObject> messageObjects = new ArrayList<>();
        messageObjects.add(messageObject);
        MediaController.saveFilesFromMessages(activity, getAccountInstance(), messageObjects, count -> {
            if (getParentActivity() == null || fragmentView == null) {
                return;
            }
            if (count > 0) {
                BulletinFactory.of(this).createDownloadBulletin(BulletinFactory.FileType.UNKNOWN, getResourceProvider()).show();
            }
        });
    }

    private void share() {
        final Activity activity = getParentActivity();
        if (activity == null) {
            return;
        }
        try {
            final Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("application/pdf");
            intent.putExtra(Intent.EXTRA_STREAM, FileProvider.getUriForFile(activity, ApplicationLoader.getApplicationId() + ".provider", file));
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            activity.startActivityForResult(Intent.createChooser(intent, getString(R.string.ShareFile)), 500);
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private class ContentView extends FrameLayout implements Theme.Colorable {

        ContentView(Context context) {
            super(context);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            updateTopFade();
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            super.onLayout(changed, left, top, right, bottom);
            updateHeaderFade(false);
        }

        @Override
        protected void dispatchDraw(@NonNull Canvas canvas) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && scrollableViewNoiseSuppressor != null && iBlur3SourceGlass != null) {
                blur3_InvalidateBlur();
                if (!iBlur3SourceGlass.inRecording()) {
                    final Canvas c = iBlur3SourceGlass.beginRecording(getMeasuredWidth(), getMeasuredHeight());
                    c.drawColor(getThemedColor(Theme.key_windowBackgroundGray));
                    if (SharedConfig.chatBlurEnabled()) {
                        scrollableViewNoiseSuppressor.draw(c, DownscaleScrollableNoiseSuppressor.DRAW_GLASS);
                    }
                    iBlur3SourceGlass.endRecording();
                }
            }
            super.dispatchDraw(canvas);
        }

        @Override
        public void updateColors() {
            final int backgroundColor = getThemedColor(Theme.key_windowBackgroundGray);
            setBackgroundColor(backgroundColor);
            iBlur3SourceColor.setColor(backgroundColor);
            updateTopFade();
            if (pagesView != null) {
                pagesView.updateColors();
            }
            if (searchCounterView != null) {
                searchCounterView.updateColors();
            }
            if (searchButtonsLayout != null) {
                searchButtonsLayout.updateColors();
            }
        }
    }

    @SuppressLint("ViewConstructor")
    private class SearchCounterView extends FrameLayout {

        private final BlurredBackgroundDrawable backgroundDrawable;
        private final AnimatedTextView textView;

        SearchCounterView(Context context, BlurredBackgroundDrawableViewFactory factory, BlurredBackgroundColorProvider colorProvider) {
            super(context);
            setWillNotDraw(false);
            backgroundDrawable = factory.create(this, colorProvider);
            backgroundDrawable.setRadius(dp(24));

            textView = new AnimatedTextView(context, true, true, true) {
                @Override
                protected void onDraw(@NonNull Canvas canvas) {
                    super.onDraw(canvas);
                    SearchCounterView.this.invalidate();
                }
            };
            textView.setAnimationProperties(.25f, 0, 280, CubicBezierInterpolator.EASE_OUT_QUINT);
            textView.setTextSize(dp(15));
            textView.setTypeface(AndroidUtilities.bold());
            textView.setGravity(Gravity.LEFT);
            addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 30, Gravity.CENTER_VERTICAL, 18, 0, 18, 0));
            updateColors();
        }

        void updateColors() {
            textView.setTextColor(getThemedColor(Theme.key_glass_defaultText));
            backgroundDrawable.updateColors();
            invalidate();
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            setPivotX(0);
            setPivotY(h);
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            final int width = Math.min(getWidth(), dp(36) + (int) Math.ceil(textView.getDrawable().getCurrentWidth()));
            backgroundDrawable.setBounds(0, 0, width, getHeight());
            backgroundDrawable.draw(canvas);
            super.draw(canvas);
        }
    }
}
