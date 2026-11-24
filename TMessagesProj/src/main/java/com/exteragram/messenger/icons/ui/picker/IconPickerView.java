/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.icons.ui.picker;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Parcelable;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.Window;
import android.view.WindowInsets;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.graphics.ColorUtils;
import androidx.core.math.MathUtils;
import androidx.dynamicanimation.animation.FloatValueHolder;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.exteragram.messenger.ExteraConfig;
import com.exteragram.messenger.icons.IconManager;
import com.exteragram.messenger.icons.IconPack;
import com.exteragram.messenger.icons.ui.IconPacksEditorActivity;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.utils.ViewOutlineProviderImpl;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.CombinedDrawable;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;
import org.telegram.ui.LaunchActivity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Locale;
import java.util.Objects;

public class IconPickerView extends FrameLayout implements NotificationCenter.NotificationCenterDelegate {
    private final FrameLayout floatingButtonContainer;
    private Drawable floatingButtonBackground;

    private SpringAnimation fabXSpring, fabYSpring;
    private final SharedPreferences mPrefs;

    private boolean isScrolling;
    private boolean isScrollDisallowed;
    private boolean isFromFling;

    private boolean inLongPress;
    private final Runnable onLongPress = () -> {
        inLongPress = true;
        try {
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        } catch (Exception ignored) {}
    };

    private boolean isBigMenuShown;
    private int wasStatusBar;
    private final LinearLayout bigLayout;
    private final ActionBar actionBar;
    private final UniversalRecyclerView listView;
    private final ActionBarMenuItem otherButton;
    private final ActionBarMenuSubItem saveItem;
    private final ImageView actionIcon;

    private boolean searching;
    private String query;

    private final int touchSlop;

    private int systemTopInset = 0;
    private int systemBottomInset = 0;

    public IconPickerView(@NonNull Context context) {
        super(context);

        mPrefs = context.getSharedPreferences("icon_picker", 0);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();

        GestureDetector.OnGestureListener gestureListener = new GestureDetector.SimpleOnGestureListener() {
            private float startX, startY;

            @Override
            public boolean onDown(@NonNull MotionEvent e) {
                return true;
            }

            @Override
            public boolean onSingleTapUp(@NonNull MotionEvent e) {
                if (inLongPress) return false;
                if (!isBigMenuShown) {
                    showIconList(true);
                    return true;
                }
                return false;
            }

            @Override
            public boolean onFling(MotionEvent e1, @NonNull MotionEvent e2, float velocityX, float velocityY) {
                if (isScrolling && !inLongPress) {
                    fabXSpring.getSpring().setFinalPosition(fabXSpring.getSpring().getFinalPosition() + velocityX / 7f >= getWidth() / 2f ? clampX(getResources().getDisplayMetrics(), Integer.MAX_VALUE) : clampX(getResources().getDisplayMetrics(), Integer.MIN_VALUE));
                    fabYSpring.getSpring().setFinalPosition(clampY(getResources().getDisplayMetrics(), fabYSpring.getSpring().getFinalPosition() + velocityY / 10f));
                    fabXSpring.start();
                    fabYSpring.start();
                    return isFromFling = true;
                }
                return false;
            }

            @Override
            public boolean onScroll(MotionEvent e1, @NonNull MotionEvent e2, float distanceX, float distanceY) {
                if (!inLongPress) {
                    AndroidUtilities.cancelRunOnUIThread(onLongPress);
                }
                if (!isScrolling && !isScrollDisallowed) {
                    if (Math.abs(distanceX) >= touchSlop || Math.abs(distanceY) >= touchSlop) {
                        startX = fabXSpring.getSpring().getFinalPosition();
                        startY = fabYSpring.getSpring().getFinalPosition();
                        isScrolling = true;
                    } else {
                        isScrollDisallowed = false;
                    }
                }
                if (isScrolling) {
                    if (!inLongPress) {
                        fabXSpring.getSpring().setFinalPosition(startX + e2.getRawX() - e1.getRawX());
                        fabYSpring.getSpring().setFinalPosition(startY + e2.getRawY() - e1.getRawY());
                        fabXSpring.start();
                        fabYSpring.start();
                    }
                }
                return isScrolling;
            }
        };

        GestureDetector gestureDetector = new GestureDetector(context, gestureListener);
        gestureDetector.setIsLongpressEnabled(false);

        floatingButtonContainer = new FrameLayout(context) {
            @Override
            public void invalidate() {
                super.invalidate();
                IconPickerView.this.invalidate();
            }

            @Override
            public void setTranslationX(float translationX) {
                super.setTranslationX(translationX);
                IconPickerView.this.invalidate();
            }

            @Override
            public void setTranslationY(float translationY) {
                super.setTranslationY(translationY);
                IconPickerView.this.invalidate();
            }

            @SuppressLint("ClickableViewAccessibility")
            @Override
            public boolean onTouchEvent(MotionEvent event) {
                boolean detector = gestureDetector.onTouchEvent(event);
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    AndroidUtilities.runOnUIThread(onLongPress, 200);
                } else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                    AndroidUtilities.cancelRunOnUIThread(onLongPress);
                    if (!isFromFling) {
                        updateSpringPositions();
                    }
                    inLongPress = false;
                    isScrolling = false;
                    isScrollDisallowed = false;
                    isFromFling = false;
                }
                return detector;
            }
        };

        actionIcon = new ImageView(context);
        actionIcon.setImageResource(R.drawable.msg_palette);
        floatingButtonContainer.addView(actionIcon, LayoutHelper.createFrame(24, 24, Gravity.CENTER));
        floatingButtonContainer.setVisibility(GONE);
        addView(floatingButtonContainer, LayoutHelper.createFrame(56, 56));

        bigLayout = new LinearLayout(context);
        bigLayout.setOrientation(LinearLayout.VERTICAL);
        bigLayout.setVisibility(GONE);

        actionBar = new ActionBar(context);
        actionBar.setOccupyStatusBar(false);
        updateTitle();
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(false);

        ActionBarMenu menu = actionBar.createMenu();
        menu.addItem(0, R.drawable.outline_header_search).setIsSearchField(true).setActionBarMenuItemSearchListener(new ActionBarMenuItem.ActionBarMenuItemSearchListener() {
            @Override
            public void onSearchExpand() {
                searching = true;
                if (otherButton != null) {
                    otherButton.setVisibility(GONE);
                }
            }

            @Override
            public void onSearchCollapse() {
                searching = false;
                query = null;
                if (otherButton != null) {
                    otherButton.setVisibility(VISIBLE);
                }
                listView.adapter.update(true);
            }

            @Override
            public void onTextChanged(EditText editText) {
                query = editText.getText().toString();
                listView.adapter.update(true);
            }
        });
        otherButton = menu.addItem(1, R.drawable.ic_ab_other);
        otherButton.addSubItem(2, R.drawable.msg_media, LocaleController.getString(R.string.IconPickerAllIcons));
        saveItem = otherButton.addSubItem(3, R.drawable.ic_ab_done, LocaleController.getString(R.string.IconPickerSaveAndExit));
        saveItem.setColors(Theme.getColor(Theme.key_featuredStickers_addButtonPressed), Theme.getColor(Theme.key_featuredStickers_addButtonPressed));

        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    showIconList(false);
                } else if (id == 3) {
                    ExteraConfig.setEditingIconPackId(null);
                    if (context instanceof LaunchActivity) {
                        IconPickerController.setActive((LaunchActivity) context, false);
                    }
                } else if (id == 2) {
                    if (ExteraConfig.getEditingIconPackId() != null) {
                        IconPack pack = IconManager.INSTANCE.findPackById(ExteraConfig.getEditingIconPackId());
                        if (pack != null && context instanceof LaunchActivity) {
                            ((LaunchActivity) context).presentFragment(new IconPacksEditorActivity(pack));
                            showIconList(false);
                        }
                    }
                }
            }
        });

        bigLayout.addView(actionBar, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        listView = new UniversalRecyclerView(context, UserConfig.selectedAccount, 0, this::fillItems, this::onClick, null, null);
        listView.setLayoutManager(new LinearLayoutManager(context));
        bigLayout.addView(listView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 0, 1f));

        addView(bigLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.NO_GRAVITY, 8, 8, 8, 8));

        bigLayout.setClipToOutline(true);
        bigLayout.setOutlineProvider(ViewOutlineProviderImpl.boundsWithRoundRect(dp(10)));

        setOnApplyWindowInsetsListener((v, insets) -> {
            systemTopInset = insets.getSystemWindowInsetTop();
            systemBottomInset = insets.getSystemWindowInsetBottom();

            LayoutParams lp = (LayoutParams) bigLayout.getLayoutParams();
            if (lp != null) {
                lp.topMargin = dp(8) + systemTopInset;
                lp.bottomMargin = dp(8) + systemBottomInset;
                bigLayout.setLayoutParams(lp);
            }

            if (!isScrolling && !isBigMenuShown) {
                updateSpringPositions();
            }
            return insets;
        });

        updateDrawables();
        setWillNotDraw(false);
    }

    private void updateSpringPositions() {
        fabXSpring.getSpring().setFinalPosition(fabXSpring.getSpring().getFinalPosition() >= getWidth() / 2f ? clampX(getResources().getDisplayMetrics(), Integer.MAX_VALUE) : clampX(getResources().getDisplayMetrics(), Integer.MIN_VALUE));
        fabYSpring.getSpring().setFinalPosition(clampY(getResources().getDisplayMetrics(), fabYSpring.getSpring().getFinalPosition()));
        fabXSpring.start();
        fabYSpring.start();
    }

    private void onClick(UItem item, View view, int position, float x, float y) {
        IconManager.INSTANCE.showReplaceAlert(getContext(), item.id);
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        final String lowerQuery = searching && !TextUtils.isEmpty(query) ? query.toLowerCase(Locale.ROOT) : null;
        final IconPack pack = ExteraConfig.getEditingIconPackId() != null
                ? IconManager.INSTANCE.findPackById(ExteraConfig.getEditingIconPackId())
                : null;

        IconObserver.INSTANCE.getUsedIcons().stream()
                .map(resId -> {
                    try {
                        String resourceName = getResources().getResourceEntryName(resId);
                        if (lowerQuery != null && !resourceName.toLowerCase(Locale.ROOT).contains(lowerQuery)) {
                            return null;
                        }
                        if (IconManager.INSTANCE.isBlacklisted(resourceName)) {
                            return null;
                        }
                        UItem item = IconPacksEditorActivity.EditorIconCell.Factory.asIcon(resId, resourceName, pack);
                        item.transparent = true;
                        return item;
                    } catch (Exception e) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(item -> item.text.toString()))
                .forEach(items::add);
    }

    @Override
    protected boolean drawChild(@NonNull Canvas canvas, View child, long drawingTime) {
        if (child == bigLayout) {
            canvas.drawColor(Color.argb((int)(0x7A * bigLayout.getAlpha()), 0, 0, 0));
        }
        return super.drawChild(canvas, child, drawingTime);
    }

    public boolean onBackPressed(boolean invoked) {
        if (isBigMenuShown) {
            if (invoked) {
                if (searching) {
                    actionBar.closeSearchField();
                    return true;
                }
                showIconList(false);
            }
            return true;
        }
        return false;
    }

    @SuppressLint("ApplySharedPref")
    public void saveConfig() {
        mPrefs.edit()
                .putFloat("x", fabXSpring.getSpring().getFinalPosition())
                .putFloat("y", fabYSpring.getSpring().getFinalPosition())
                .apply();
    }

    private void updateTitle() {
        if (actionBar == null) return;
        if (ExteraConfig.getEditingIconPackId() != null) {
            IconPack pack = IconManager.INSTANCE.findPackById(ExteraConfig.getEditingIconPackId());
            if (pack != null) {
                actionBar.setTitle(pack.getName());
            }
        }
    }

    private void updateDrawables() {
        Drawable drawable = Theme.createSimpleSelectorCircleDrawable(dp(56), Theme.getColor(Theme.key_chats_actionBackground), Theme.getColor(Theme.key_chats_actionPressedBackground));
        Drawable shadowDrawable = ResourcesCompat.getDrawable(getResources(), R.drawable.floating_shadow, getContext().getTheme()).mutate();
        shadowDrawable.setColorFilter(new PorterDuffColorFilter(0xff000000, PorterDuff.Mode.MULTIPLY));
        CombinedDrawable combinedDrawable = new CombinedDrawable(shadowDrawable, drawable, 0, 0);
        combinedDrawable.setIconSize(dp(56), dp(56));
        drawable = combinedDrawable;
        floatingButtonBackground = drawable;

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Theme.getColor(Theme.key_dialogBackground));
        bg.setCornerRadius(dp(10));
        bigLayout.setBackground(bg);
        bigLayout.setElevation(dp(4));

        if (actionIcon != null) {
            actionIcon.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chats_actionIcon), PorterDuff.Mode.SRC_IN));
        }

        if (actionBar != null) {
            actionBar.setBackgroundColor(Theme.getColor(Theme.key_dialogBackground));
            actionBar.setTitleColor(Theme.getColor(Theme.key_dialogTextBlack));
            actionBar.setItemsColor(Theme.getColor(Theme.key_dialogTextBlack), false);
            actionBar.setItemsBackgroundColor(Theme.getColor(Theme.key_dialogButtonSelector), false);
        }

        if (otherButton != null) {
            otherButton.setPopupItemsColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuItem), false);
            otherButton.setPopupItemsColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuItemIcon), true);
            otherButton.redrawPopup(Theme.getColor(Theme.key_actionBarDefaultSubmenuBackground));

            if (saveItem != null) {
                saveItem.setColors(Theme.getColor(Theme.key_featuredStickers_addButtonPressed), Theme.getColor(Theme.key_featuredStickers_addButtonPressed));
            }
        }

        invalidate();
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return isBigMenuShown;
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        canvas.save();
        canvas.translate(floatingButtonContainer.getTranslationX(), floatingButtonContainer.getTranslationY());
        canvas.scale(floatingButtonContainer.getScaleX(), floatingButtonContainer.getScaleY(), floatingButtonContainer.getPivotX(), floatingButtonContainer.getPivotY());
        floatingButtonBackground.setAlpha((int) (floatingButtonContainer.getAlpha() * 0xFF));
        floatingButtonBackground.setBounds(floatingButtonContainer.getLeft(), floatingButtonContainer.getTop(),
                floatingButtonContainer.getRight(), floatingButtonContainer.getBottom());
        floatingButtonBackground.draw(canvas);
        canvas.restore();
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.iconPackUpdated) {
            updateTitle();
            Parcelable state = null;
            if (listView.getLayoutManager() != null) {
                state = listView.getLayoutManager().onSaveInstanceState();
            }
            listView.adapter.update(true);
            if (state != null) {
                listView.getLayoutManager().onRestoreInstanceState(state);
            }
        } else if (id == NotificationCenter.didSetNewTheme) {
            updateDrawables();
            listView.adapter.update(false);
        }
    }

    private float clampX(DisplayMetrics displayMetrics, float x) {
        return MathUtils.clamp(x, dp(16), displayMetrics.widthPixels - dp(56 + 16));
    }

    private float clampY(DisplayMetrics displayMetrics, float y) {
        return MathUtils.clamp(y, systemTopInset + dp(16), displayMetrics.heightPixels - systemBottomInset - dp(56 + 16));
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        WindowInsets insets = getRootWindowInsets();
        if (insets != null) {
            systemTopInset = insets.getSystemWindowInsetTop();
            systemBottomInset = insets.getSystemWindowInsetBottom();

            LayoutParams lp = (LayoutParams) bigLayout.getLayoutParams();
            if (lp != null) {
                lp.topMargin = dp(8) + systemTopInset;
                lp.bottomMargin = dp(8) + systemBottomInset;
                bigLayout.setLayoutParams(lp);
            }
        }

        float savedX = mPrefs.getFloat("x", -1), savedY = mPrefs.getFloat("y", -1);
        DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
        floatingButtonContainer.setTranslationX(savedX == -1 || savedX >= displayMetrics.widthPixels / 2f ? clampX(displayMetrics, Integer.MAX_VALUE) : clampX(displayMetrics, Integer.MIN_VALUE));
        floatingButtonContainer.setTranslationY(clampY(displayMetrics, savedY == -1 ? (displayMetrics.heightPixels / 2f) : savedY));

        fabXSpring = new SpringAnimation(floatingButtonContainer, SpringAnimation.TRANSLATION_X, floatingButtonContainer.getTranslationX())
                .setSpring(new SpringForce(floatingButtonContainer.getTranslationX())
                        .setStiffness(650f)
                        .setDampingRatio(SpringForce.DAMPING_RATIO_LOW_BOUNCY));
        fabYSpring = new SpringAnimation(floatingButtonContainer, SpringAnimation.TRANSLATION_Y, floatingButtonContainer.getTranslationY())
                .setSpring(new SpringForce(floatingButtonContainer.getTranslationY())
                        .setStiffness(650f)
                        .setDampingRatio(SpringForce.DAMPING_RATIO_LOW_BOUNCY));

        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.iconPackUpdated);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.didSetNewTheme);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        fabXSpring.cancel();
        fabYSpring.cancel();
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.iconPackUpdated);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.didSetNewTheme);
    }

    public void showIconList(boolean show) {
        if (isBigMenuShown == show) {
            return;
        }
        isBigMenuShown = show;

        if (show) {
            bigLayout.setVisibility(VISIBLE);
            listView.adapter.update(true);
        }

        Window window = ((Activity) getContext()).getWindow();
        if (show) {
            wasStatusBar = window.getStatusBarColor();
        }

        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) bigLayout.getLayoutParams();
        float topMargin = layoutParams.topMargin;
        float leftMargin = layoutParams.leftMargin;

        float startX = floatingButtonContainer.getTranslationX() - leftMargin;
        float startY = floatingButtonContainer.getTranslationY() - topMargin;

        new SpringAnimation(new FloatValueHolder(show ? 0f : 1000f))
                .setSpring(new SpringForce(1000f)
                        .setStiffness(900)
                        .setDampingRatio(SpringForce.DAMPING_RATIO_NO_BOUNCY)
                        .setFinalPosition(show ? 1000f : 0f))
                .addUpdateListener((animation, value, velocity) -> {
                    value /= 1000f;

                    bigLayout.setAlpha(value);

                    bigLayout.setTranslationX(AndroidUtilities.lerp(startX, 0, value));
                    bigLayout.setTranslationY(AndroidUtilities.lerp(startY, 0, value));

                    bigLayout.setPivotX(floatingButtonContainer.getTranslationX() - leftMargin + dp(28));
                    bigLayout.setPivotY(floatingButtonContainer.getTranslationY() - topMargin + dp(28));

                    if (bigLayout.getWidth() != 0) {
                        bigLayout.setScaleX(AndroidUtilities.lerp(floatingButtonContainer.getWidth() / (float) bigLayout.getWidth(), 1f, value));
                    }
                    if (bigLayout.getHeight() != 0) {
                        bigLayout.setScaleY(AndroidUtilities.lerp(floatingButtonContainer.getHeight() / (float) bigLayout.getHeight(), 1f, value));
                    }

                    floatingButtonContainer.setTranslationX(AndroidUtilities.lerp(startX + leftMargin, getWidth() / 2f - dp(28), value));
                    floatingButtonContainer.setTranslationY(AndroidUtilities.lerp(startY + topMargin, getHeight() / 2f - dp(28), value));
                    floatingButtonContainer.setAlpha(1f - value);

                    window.setStatusBarColor(ColorUtils.blendARGB(wasStatusBar, 0x7A000000, value));

                    invalidate();
                })
                .addEndListener((animation, canceled, value, velocity) -> {
                    floatingButtonContainer.setTranslationX(startX + leftMargin);
                    floatingButtonContainer.setTranslationY(startY + topMargin);

                    if (!show) {
                        bigLayout.setVisibility(GONE);
                    }
                })
                .start();
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        fabXSpring.cancel();
        fabYSpring.cancel();
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        floatingButtonContainer.setTranslationX(floatingButtonContainer.getTranslationX() >= metrics.widthPixels / 2f ? clampX(metrics, Integer.MAX_VALUE) : clampX(metrics, Integer.MIN_VALUE));
        floatingButtonContainer.setTranslationY(clampY(metrics, floatingButtonContainer.getTranslationY()));
        fabXSpring.getSpring().setFinalPosition(floatingButtonContainer.getTranslationX());
        fabYSpring.getSpring().setFinalPosition(floatingButtonContainer.getTranslationY());
    }

    public void showFab() {
        floatingButtonContainer.setVisibility(VISIBLE);
        new SpringAnimation(new FloatValueHolder(0))
                .setSpring(new SpringForce(1000f)
                        .setStiffness(750f)
                        .setDampingRatio(SpringForce.DAMPING_RATIO_LOW_BOUNCY))
                .addUpdateListener((animation, value, velocity) -> {
                    value /= 1000;
                    floatingButtonContainer.setPivotX(dp(28));
                    floatingButtonContainer.setPivotY(dp(28));
                    floatingButtonContainer.setScaleX(value);
                    floatingButtonContainer.setScaleY(value);
                    floatingButtonContainer.setAlpha(MathUtils.clamp(value, 0, 1));
                    invalidate();
                })
                .start();
    }

    public void dismiss(Runnable callback) {
        callback.run();
    }
}