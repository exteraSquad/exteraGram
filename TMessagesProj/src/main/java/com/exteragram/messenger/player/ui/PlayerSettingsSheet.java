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

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.exteragram.messenger.ExteraConfig;
import com.exteragram.messenger.player.lyrics.LyricsProvider;
import com.exteragram.messenger.player.motion.CanvasRepository;
import com.exteragram.messenger.player.motion.SpotifyAuth;
import com.exteragram.messenger.player.motion.SpotifyCanvasRepository;
import com.exteragram.messenger.player.motion.YandexCanvasRepository;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BotWebViewVibrationEffect;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Components.BottomSheetWithRecyclerListView;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class PlayerSettingsSheet extends BottomSheetWithRecyclerListView {

    public interface Delegate {
        default void onCoverColorsChanged() {
        }

        default void onMotionCoversChanged() {
        }

        default void onCanvasChanged() {
        }

        default void onRhythmBackgroundChanged() {
        }

        default void onLyricsOnlineChanged() {
        }

        default void onLyricsProvidersChanged() {
        }

        default void onLyricsProgressChanged() {
        }

        default void onLyricsSubtitleChanged() {
        }
    }

    private static final int ID_COVER_COLORS = 1;
    private static final int ID_MOTION_COVERS = 2;
    private static final int ID_LYRICS_ONLINE = 3;
    private static final int ID_RHYTHM_BACKGROUND = 5;
    private static final int ID_COVER_COLORS_FULL = 6;
    private static final int ID_COVER_COLORS_MINI = 7;
    private static final int ID_PARTICLES = 8;
    private static final int ID_LYRICS_PROGRESS = 9;
    private static final int ID_SPOTIFY_CANVAS = 10;
    private static final int ID_SPOTIFY_LOGOUT = 11;
    private static final int ID_LYRICS_SUBTITLE = 12;
    private static final int ID_LYRICS_PROVIDER = 100;
    private static final int ID_YANDEX_CANVAS = 13;
    private static final int ID_YANDEX_LOGOUT = 14;
    private static final long EXPAND_DURATION_MS = 350;
    private static final long COLORS_DURATION_MS = 350;

    private final Delegate delegate;
    private final SwitchingResourcesProvider colors;
    private final Paint snapshotPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private UniversalAdapter adapter;
    private boolean coverColorsExpanded;
    private boolean lyricsProvidersExpanded;
    private boolean spotifyLoginPending;
    private Bitmap snapshot;
    private int snapshotTop;
    private boolean capturing;
    private ValueAnimator snapshotAnimator;

    public PlayerSettingsSheet(Context context, BaseFragment fragment, Theme.ResourcesProvider resourcesProvider, Delegate delegate) {
        this(context, fragment, new SwitchingResourcesProvider(resourcesProvider), delegate);
    }

    private PlayerSettingsSheet(Context context, BaseFragment fragment, SwitchingResourcesProvider colors, Delegate delegate) {
        super(context, fragment, false, false, false, false, ActionBarType.SLIDING, colors);
        this.colors = colors;
        this.delegate = delegate;
        takeTranslationIntoAccount = true;
        occupyNavigationBar = true;
        applyColors();
        recyclerListView.setPadding(backgroundPaddingLeft, 0, backgroundPaddingLeft, dp(12) + AndroidUtilities.navigationBarHeight);
        recyclerListView.setClipToPadding(false);
        final DefaultItemAnimator itemAnimator = new DefaultItemAnimator() {
            @Override
            protected void onMoveAnimationUpdate(RecyclerView.ViewHolder holder) {
                super.onMoveAnimationUpdate(holder);
                containerView.invalidate();
            }
        };
        itemAnimator.setSupportsChangeAnimations(false);
        itemAnimator.setDelayAnimations(false);
        itemAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        itemAnimator.setDurations(EXPAND_DURATION_MS);
        recyclerListView.setItemAnimator(itemAnimator);
        recyclerListView.setSections();
        recyclerListView.setOnItemClickListener((view, position) -> {
            final UItem item = adapter.getItem(position - 1);
            if (item != null) {
                onItemClick(item, view);
            }
        });
        adapter.update(false);
    }

    public void updateColors(Theme.ResourcesProvider provider) {
        if (colors.current == provider) {
            return;
        }
        captureSnapshot();
        colors.current = provider;
        applyColors();
        final LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerListView.getLayoutManager();
        final int position = layoutManager != null ? layoutManager.findFirstVisibleItemPosition() : RecyclerView.NO_POSITION;
        final View first = position != RecyclerView.NO_POSITION ? layoutManager.findViewByPosition(position) : null;
        final int offset = first != null ? first.getTop() - recyclerListView.getPaddingTop() : 0;
        resetAdapter(getContext());
        if (first != null) {
            layoutManager.scrollToPositionWithOffset(position, offset);
        }
        fadeSnapshot();
    }

    private void applyColors() {
        final int background = getThemedColor(Theme.key_windowBackgroundGray);
        setBackgroundColor(background);
        fixNavigationBar(background);
        recyclerListView.setSelectorDrawableColor(getThemedColor(Theme.key_settings_listSelector));
        if (actionBar != null) {
            actionBar.setBackgroundColor(getThemedColor(Theme.key_dialogBackground));
            actionBar.setTitleColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
            actionBar.setItemsBackgroundColor(getThemedColor(Theme.key_actionBarActionModeDefaultSelector), false);
            actionBar.setItemsColor(getThemedColor(Theme.key_actionBarActionModeDefaultIcon), false);
        }
        containerView.invalidate();
    }

    private void captureSnapshot() {
        final int width = containerView.getWidth();
        final int top = Math.max(0, shadowDrawable.getBounds().top);
        final int height = containerView.getHeight() - top;
        if (!containerView.isAttachedToWindow() || width <= 0 || height <= 0) {
            return;
        }
        final Bitmap bitmap;
        try {
            bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        } catch (OutOfMemoryError e) {
            return;
        }
        final Canvas canvas = new Canvas(bitmap);
        canvas.translate(0, -top);
        capturing = true;
        containerView.draw(canvas);
        capturing = false;
        releaseSnapshot();
        snapshot = bitmap;
        snapshotTop = top;
        snapshotPaint.setAlpha(0xFF);
    }

    private void fadeSnapshot() {
        if (snapshot == null) {
            return;
        }
        snapshotAnimator = ValueAnimator.ofFloat(1f, 0f);
        snapshotAnimator.setDuration(COLORS_DURATION_MS);
        snapshotAnimator.setInterpolator(CubicBezierInterpolator.DEFAULT);
        snapshotAnimator.addUpdateListener(a -> {
            snapshotPaint.setAlpha((int) (0xFF * (float) a.getAnimatedValue()));
            containerView.invalidate();
        });
        snapshotAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (snapshotAnimator == animation) {
                    snapshotAnimator = null;
                    releaseSnapshot();
                }
            }
        });
        snapshotAnimator.start();
    }

    private void releaseSnapshot() {
        if (snapshotAnimator != null) {
            final ValueAnimator animator = snapshotAnimator;
            snapshotAnimator = null;
            animator.cancel();
        }
        if (snapshot != null) {
            snapshot.recycle();
            snapshot = null;
            containerView.invalidate();
        }
    }

    @Override
    protected void postDrawInternal(Canvas canvas, View parentView) {
        if (snapshot != null && !capturing && parentView == containerView) {
            canvas.drawBitmap(snapshot, 0, snapshotTop, snapshotPaint);
        }
        super.postDrawInternal(canvas, parentView);
    }

    @Override
    public void dismissInternal() {
        super.dismissInternal();
        releaseSnapshot();
        if (spotifyLoginPending) {
            spotifyLoginPending = false;
            SpotifyAuth.cancel();
        }
    }

    @Override
    protected CharSequence getTitle() {
        return getString(R.string.PlayerSettings);
    }

    @Override
    protected RecyclerListView.SelectionAdapter createAdapter(RecyclerListView listView) {
        adapter = new UniversalAdapter(listView, getContext(), currentAccount, 0, true, this::fillItems, resourcesProvider);
        adapter.setApplyBackground(false);
        return adapter;
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        if (delegate == null) {
            return;
        }
        items.add(UItem.asShadow(null));
        items.add(UItem.asHeader(getString(R.string.Appearance)));
        final boolean full = ExteraConfig.getPlayerCoverColors();
        final boolean mini = ExteraConfig.getPlayerMiniCoverColors();
        final int enabled = (full ? 1 : 0) + (mini ? 1 : 0);
        items.add(UItem.asExteraExpandableSwitch(ID_COVER_COLORS, getString(R.string.PlayerCoverColors), enabled + "/2", v -> setCoverColors(enabled == 0, enabled == 0))
                .setChecked(enabled > 0)
                .setCollapsed(!coverColorsExpanded));
        if (coverColorsExpanded) {
            items.add(UItem.asRoundCheckbox(ID_COVER_COLORS_FULL, getString(R.string.PlayerCoverColorsFull)).setChecked(full).pad());
            items.add(UItem.asRoundCheckbox(ID_COVER_COLORS_MINI, getString(R.string.PlayerCoverColorsMini)).setChecked(mini).pad());
        }
        items.add(UItem.asCheck(ID_RHYTHM_BACKGROUND, getString(R.string.PlayerRhythmBackground), getString(R.string.PlayerRhythmBackgroundInfo), true).setChecked(ExteraConfig.getPlayerRhythmBackground()));
        items.add(UItem.asCheck(ID_PARTICLES, getString(R.string.PlayerParticles), getString(R.string.PlayerParticlesInfo), true).setChecked(ExteraConfig.getPlayerParticles()));
        items.add(UItem.asCheck(ID_MOTION_COVERS, getString(R.string.PlayerMotionCovers), getString(R.string.PlayerMotionCoversEnabled), true).setChecked(ExteraConfig.getPlayerMotionCovers()));
        if (CanvasRepository.isSupported()) {
            final boolean spotifyLoggedIn = SpotifyCanvasRepository.isLoggedIn();
            items.add(UItem.asCheck(ID_SPOTIFY_CANVAS, getString(R.string.PlayerSpotifyCanvas), getString(R.string.PlayerSpotifyCanvasInfo), true).setChecked(spotifyLoggedIn && ExteraConfig.getPlayerSpotifyCanvas()));
            if (spotifyLoggedIn) {
                items.add(UItem.asButton(ID_SPOTIFY_LOGOUT, getString(R.string.PlayerSpotifyLogout)).red());
            }
            final boolean yandexLoggedIn = YandexCanvasRepository.isLoggedIn();
            items.add(UItem.asCheck(ID_YANDEX_CANVAS, getString(R.string.PlayerYandexCanvas), getString(R.string.PlayerYandexCanvasInfo), true).setChecked(yandexLoggedIn && ExteraConfig.getPlayerYandexCanvas()));
            if (yandexLoggedIn) {
                items.add(UItem.asButton(ID_YANDEX_LOGOUT, getString(R.string.PlayerYandexLogout)).red());
            }
        }
        items.add(UItem.asShadow(null));
        items.add(UItem.asHeader(getString(R.string.PlayerLyrics)));
        final LyricsProvider[] providers = LyricsProvider.values();
        final Set<String> enabledProviders = ExteraConfig.getLyricsProviders();
        int enabledCount = 0;
        for (LyricsProvider provider : providers) {
            if (enabledProviders.contains(provider.id)) {
                enabledCount++;
            }
        }
        items.add(UItem.asExteraExpandableSwitch(ID_LYRICS_ONLINE, getString(R.string.PlayerLyricsOnline), enabledCount + "/" + providers.length, v -> setLyricsOnline(!ExteraConfig.getLyricsOnline()))
                .setChecked(ExteraConfig.getLyricsOnline())
                .setCollapsed(!lyricsProvidersExpanded));
        if (lyricsProvidersExpanded) {
            for (LyricsProvider provider : providers) {
                items.add(UItem.asRoundCheckbox(ID_LYRICS_PROVIDER + provider.ordinal(), provider.title).setChecked(enabledProviders.contains(provider.id)).pad());
            }
        }
        items.add(UItem.asShadow(getString(R.string.PlayerLyricsOnlineInfo)));
        items.add(UItem.asHeader(getString(R.string.PlayerLyricsProgress)));
        items.add(UItem.asSlideView(ID_LYRICS_PROGRESS, new String[]{
                getString(R.string.BlurOff),
                getString(R.string.PlayerLyricsProgressHorizontal),
                getString(R.string.PlayerLyricsProgressVertical)
        }, ExteraConfig.getLyricsProgress(), index -> {
            ExteraConfig.setLyricsProgress(index);
            delegate.onLyricsProgressChanged();
        }));
        items.add(UItem.asShadow(getString(R.string.PlayerLyricsProgressInfo)));
        items.add(UItem.asHeader(getString(R.string.PlayerLyricsSubtitle)));
        items.add(UItem.asSlideView(ID_LYRICS_SUBTITLE, new String[]{
                getString(R.string.BlurOff),
                getString(R.string.PlayerLyricsSubtitleTranslation),
                getString(R.string.PlayerLyricsSubtitleRomanization)
        }, ExteraConfig.getLyricsSubtitle(), index -> {
            ExteraConfig.setLyricsSubtitle(index);
            delegate.onLyricsSubtitleChanged();
        }));
        items.add(UItem.asShadow(getString(R.string.PlayerLyricsSubtitleInfo)));
    }

    private void onItemClick(UItem item, View view) {
        switch (item.id) {
            case ID_COVER_COLORS:
                coverColorsExpanded = !coverColorsExpanded;
                adapter.update(true);
                break;
            case ID_COVER_COLORS_FULL:
                setCoverColors(!ExteraConfig.getPlayerCoverColors(), ExteraConfig.getPlayerMiniCoverColors());
                break;
            case ID_COVER_COLORS_MINI:
                setCoverColors(ExteraConfig.getPlayerCoverColors(), !ExteraConfig.getPlayerMiniCoverColors());
                break;
            case ID_MOTION_COVERS: {
                final boolean enabled = !ExteraConfig.getPlayerMotionCovers();
                ExteraConfig.setPlayerMotionCovers(enabled);
                setChecked(view, enabled);
                delegate.onMotionCoversChanged();
                break;
            }
            case ID_SPOTIFY_CANVAS: {
                if (!SpotifyCanvasRepository.isLoggedIn()) {
                    spotifyLoginPending = true;
                    SpotifyAuth.login(getContext(), success -> {
                        spotifyLoginPending = false;
                        if (isDismissed()) {
                            if (success) {
                                ExteraConfig.setPlayerSpotifyCanvas(true);
                            }
                        } else if (success) {
                            setSpotifyCanvas(true);
                        } else {
                            BulletinFactory.of(container, resourcesProvider).createErrorBulletin(getString(R.string.PlayerSpotifyLoginFailed)).show();
                        }
                    });
                    break;
                }
                setSpotifyCanvas(!ExteraConfig.getPlayerSpotifyCanvas());
                break;
            }
            case ID_SPOTIFY_LOGOUT:
                new AlertDialog.Builder(getContext(), resourcesProvider)
                        .setTitle(getString(R.string.PlayerSpotifyLogout))
                        .setMessage(getString(R.string.PlayerSpotifyLogoutInfo))
                        .setPositiveButton(getString(R.string.LogOut), (dialog, which) -> {
                            SpotifyCanvasRepository.logout();
                            setSpotifyCanvas(false);
                        })
                        .setNegativeButton(getString(R.string.Cancel), null)
                        .makeRed(AlertDialog.BUTTON_POSITIVE)
                        .show();
                break;
            case ID_YANDEX_CANVAS: {
                if (!YandexCanvasRepository.isLoggedIn()) {
                    YandexLoginDialog.show(getContext(), resourcesProvider, success -> {
                        if (isDismissed()) {
                            if (success) {
                                ExteraConfig.setPlayerYandexCanvas(true);
                            }
                        } else if (success) {
                            setYandexCanvas(true);
                        } else {
                            BulletinFactory.of(container, resourcesProvider).createErrorBulletin(getString(R.string.PlayerYandexLoginFailed)).show();
                        }
                    });
                    break;
                }
                setYandexCanvas(!ExteraConfig.getPlayerYandexCanvas());
                break;
            }
            case ID_YANDEX_LOGOUT:
                new AlertDialog.Builder(getContext(), resourcesProvider)
                        .setTitle(getString(R.string.PlayerYandexLogout))
                        .setMessage(getString(R.string.PlayerYandexLogoutInfo))
                        .setPositiveButton(getString(R.string.LogOut), (dialog, which) -> {
                            YandexCanvasRepository.logout();
                            setYandexCanvas(false);
                        })
                        .setNegativeButton(getString(R.string.Cancel), null)
                        .makeRed(AlertDialog.BUTTON_POSITIVE)
                        .show();
                break;
            case ID_RHYTHM_BACKGROUND: {
                final boolean enabled = !ExteraConfig.getPlayerRhythmBackground();
                ExteraConfig.setPlayerRhythmBackground(enabled);
                setChecked(view, enabled);
                delegate.onRhythmBackgroundChanged();
                break;
            }
            case ID_PARTICLES: {
                final boolean enabled = !ExteraConfig.getPlayerParticles();
                ExteraConfig.setPlayerParticles(enabled);
                setChecked(view, enabled);
                delegate.onRhythmBackgroundChanged();
                break;
            }
            case ID_LYRICS_ONLINE:
                lyricsProvidersExpanded = !lyricsProvidersExpanded;
                adapter.update(true);
                break;
            default: {
                final int index = item.id - ID_LYRICS_PROVIDER;
                final LyricsProvider[] providers = LyricsProvider.values();
                if (index >= 0 && index < providers.length) {
                    toggleLyricsProvider(providers[index], view);
                }
                break;
            }
        }
    }

    private void setLyricsOnline(boolean enabled) {
        ExteraConfig.setLyricsOnline(enabled);
        adapter.update(true);
        delegate.onLyricsOnlineChanged();
    }

    private void toggleLyricsProvider(LyricsProvider provider, View view) {
        final Set<String> enabled = new HashSet<>(ExteraConfig.getLyricsProviders());
        if (enabled.contains(provider.id)) {
            if (enabled.size() <= 1) {
                AndroidUtilities.shakeViewSpring(view, -3);
                BotWebViewVibrationEffect.APP_ERROR.vibrate();
                return;
            }
            enabled.remove(provider.id);
        } else {
            enabled.add(provider.id);
        }
        ExteraConfig.setLyricsProviders(enabled);
        adapter.update(true);
        delegate.onLyricsProvidersChanged();
    }

    private void setCoverColors(boolean full, boolean mini) {
        ExteraConfig.setPlayerCoverColors(full);
        ExteraConfig.setPlayerMiniCoverColors(mini);
        adapter.update(true);
        delegate.onCoverColorsChanged();
    }

    private void setSpotifyCanvas(boolean enabled) {
        ExteraConfig.setPlayerSpotifyCanvas(enabled);
        adapter.update(true);
        delegate.onCanvasChanged();
    }

    private void setYandexCanvas(boolean enabled) {
        ExteraConfig.setPlayerYandexCanvas(enabled);
        adapter.update(true);
        delegate.onCanvasChanged();
    }

    private static void setChecked(View view, boolean checked) {
        if (view instanceof TextCheckCell) {
            ((TextCheckCell) view).setChecked(checked);
        }
    }

    private static final class SwitchingResourcesProvider implements Theme.ResourcesProvider {

        Theme.ResourcesProvider current;

        SwitchingResourcesProvider(Theme.ResourcesProvider current) {
            this.current = current;
        }

        @Override
        public int getColor(int key) {
            return current != null ? current.getColor(key) : Theme.getColor(key);
        }

        @Override
        public int getColorOrDefault(int key) {
            return current != null ? current.getColorOrDefault(key) : Theme.getColor(key);
        }

        @Override
        public int getCurrentColor(int key) {
            return current != null ? current.getCurrentColor(key) : Theme.getColor(key);
        }

        @Override
        public void setAnimatedColor(int key, int color) {
            if (current != null) {
                current.setAnimatedColor(key, color);
            }
        }

        @Override
        public Drawable getDrawable(String drawableKey) {
            return current != null ? current.getDrawable(drawableKey) : Theme.getThemeDrawable(drawableKey);
        }

        @Override
        public Paint getPaint(String paintKey) {
            return current != null ? current.getPaint(paintKey) : Theme.getThemePaint(paintKey);
        }

        @Override
        public boolean hasGradientService() {
            return current != null ? current.hasGradientService() : Theme.hasGradientService();
        }

        @Override
        public boolean isDark() {
            return current != null ? current.isDark() : Theme.isCurrentThemeDark();
        }

        @Override
        public boolean isMonet() {
            return current != null ? current.isMonet() : Theme.isCurrentThemeMonet();
        }

        @Override
        public void applyServiceShaderMatrix(int w, int h, float translationX, float translationY) {
            if (current != null) {
                current.applyServiceShaderMatrix(w, h, translationX, translationY);
            } else {
                Theme.applyServiceShaderMatrix(w, h, translationX, translationY);
            }
        }

        @Override
        public ColorFilter getAnimatedEmojiColorFilter() {
            return current != null ? current.getAnimatedEmojiColorFilter() : Theme.getAnimatedEmojiColorFilter(null);
        }
    }
}
