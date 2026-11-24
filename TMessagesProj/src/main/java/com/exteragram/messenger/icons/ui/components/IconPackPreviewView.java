/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.icons.ui.components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.util.SparseIntArray;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.core.content.res.ResourcesCompat;

import com.exteragram.messenger.icons.ExteraResources;
import com.exteragram.messenger.icons.IconManager;
import com.exteragram.messenger.icons.IconPack;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.CubicBezierInterpolator;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public class IconPackPreviewView extends View {

    private final List<Integer> availableIconIds = new ArrayList<>();
    private final Drawable[] currentIcons = new Drawable[20];
    private final Drawable[] nextIcons = new Drawable[20];
    private final int[] currentIconIds = new int[20];
    private final Random random = new Random();
    private IconPack currentPack;

    private final int rows = 2;
    private final int cols = 2;
    private int count = 4;
    private final int iconSize = AndroidUtilities.dp(20);
    private final int gap = AndroidUtilities.dp(4);

    private boolean isCircularMode = false;

    private final int circInnerRadius = AndroidUtilities.dp(46);
    private final int circInnerIconSize = AndroidUtilities.dp(24);
    private final int circOuterRadius = AndroidUtilities.dp(72);
    private final int circOuterIconSize = AndroidUtilities.dp(20);
    private final int circCenterSize = AndroidUtilities.dp(36);

    private static final int[] DEFAULT_ICONS = {
            R.drawable.msg_sticker,
            R.drawable.msg_link2,
            R.drawable.msg_pin,
            R.drawable.msg_photos,
    };

    private ValueAnimator animator;
    private float animationProgress = 0f;

    private int refreshTimeMilliseconds = 5000;

    private boolean attached;
    private boolean shouldAnimate;

    private final Runnable changeRunnable = this::animateToNext;

    public IconPackPreviewView(Context context) {
        super(context);
    }

    public void setCircularMode(boolean enable) {
        this.isCircularMode = enable;
        if (enable) {
            this.count = 13;
        }
        requestLayout();
        if (currentPack != null) {
            setIconPack(currentPack);
        }
    }

    public void setRefreshTime(int refreshTimeMilliseconds) {
        this.refreshTimeMilliseconds = refreshTimeMilliseconds;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (isCircularMode) {
            int maxDistFromCenter = circOuterRadius + (circOuterIconSize / 2);
            int requiredHeight = (maxDistFromCenter * 2) + AndroidUtilities.dp(8);
            setMeasuredDimension(
                    getDefaultSize(getSuggestedMinimumWidth(), widthMeasureSpec),
                    resolveSize(requiredHeight, heightMeasureSpec)
            );
        } else {
            int width = cols * iconSize + (cols - 1) * gap + getPaddingLeft() + getPaddingRight();
            int height = rows * iconSize + (rows - 1) * gap + getPaddingTop() + getPaddingBottom();
            setMeasuredDimension(resolveSize(width, widthMeasureSpec), resolveSize(height, heightMeasureSpec));
        }
    }

    public void setIconPack(IconPack pack) {
        if (currentPack != null && sameIconSource(currentPack, pack)
                && !availableIconIds.isEmpty() && availableIconIds.size() >= count)
            return;
        this.currentPack = pack;
        availableIconIds.clear();

        if (pack.isBase()) {
            for (int id : DEFAULT_ICONS) {
                availableIconIds.add(id);
            }
            shouldAnimate = false;
        } else {
            List<String> distinctNames = new ArrayList<>();
            Set<String> distinctFiles = new HashSet<>();

            for (Map.Entry<String, String> entry : pack.getIcons().entrySet()) {
                String name = entry.getKey();
                String fileName = entry.getValue();
                if (!distinctFiles.contains(fileName)) {
                    distinctFiles.add(fileName);
                    distinctNames.add(name);
                }
            }

            if (distinctNames.isEmpty()) {
                availableIconIds.add(R.drawable.msg_media);
            } else {
                Map<String, Integer> systemMap = IconManager.INSTANCE.getSystemIcons();
                if (!systemMap.isEmpty()) {
                    for (String name : distinctNames) {
                        Integer id = systemMap.get(name);
                        if (id != null) {
                            availableIconIds.add(id);
                        }
                    }
                } else {
                    int c = 0;
                    for (String name : distinctNames) {
                        int resId = getResources().getIdentifier(name, "drawable", getContext().getPackageName());
                        if (resId != 0) {
                            availableIconIds.add(resId);
                        }
                        c++;
                        if (c > 30) break;
                    }
                }
            }
            shouldAnimate = distinctFiles.size() >= count * 2;
        }

        loadIcons(false);
    }

    private static boolean sameIconSource(IconPack a, IconPack b) {
        if (!a.getId().equals(b.getId())) {
            return false;
        }
        Map<String, String> left = a.getIcons(), right = b.getIcons();
        return left == right || (left.isEmpty() && right.isEmpty());
    }

    private void loadIcons(boolean next) {
        final IconPack pack = currentPack;
        final List<Integer> available = new ArrayList<>(availableIconIds);
        final int[] exclude = next ? currentIconIds.clone() : null;
        final int countToPick;
        if (!pack.isBase()) {
            countToPick = Math.min(count, available.size());
        } else {
            countToPick = count;
        }

        Utilities.globalQueue.postRunnable(() -> {
            List<Integer> ids = pickRandomIconIdsInternal(available, countToPick, exclude);
            Drawable[] drawables = new Drawable[currentIconIds.length];
            int[] pickedIds = new int[currentIconIds.length];

            for (int i = 0; i < currentIconIds.length; i++) {
                if (i < ids.size()) {
                    pickedIds[i] = ids.get(i);
                    drawables[i] = getIconDrawableInternal(pack, pickedIds[i]);
                } else {
                    if (!ids.isEmpty() && isCircularMode) {
                        pickedIds[i] = ids.get(i % ids.size());
                        drawables[i] = getIconDrawableInternal(pack, pickedIds[i]);
                    } else {
                        pickedIds[i] = 0;
                        drawables[i] = null;
                    }
                }
            }

            AndroidUtilities.runOnUIThread(() -> {
                if (currentPack != pack) return;

                if (next) {
                    System.arraycopy(drawables, 0, nextIcons, 0, nextIcons.length);
                    startTransition(ids);
                } else {
                    for (int i = 0; i < currentIcons.length; i++) {
                        currentIconIds[i] = pickedIds[i];
                        currentIcons[i] = drawables[i];
                    }
                    invalidate();
                    if (attached && shouldAnimate) {
                        scheduleNext();
                    } else {
                        removeCallbacks(changeRunnable);
                    }
                }
            });
        });
    }

    private List<Integer> pickRandomIconIdsInternal(List<Integer> available, int count, int[] exclude) {
        List<Integer> result = new ArrayList<>();
        if (available.isEmpty()) return result;

        List<Integer> candidates = new ArrayList<>(available);
        if (exclude != null) {
            for (int id : exclude) {
                candidates.remove((Integer) id);
            }
        }

        if (candidates.isEmpty()) {
            candidates.addAll(available);
        }

        Set<Integer> selectedIndices = new HashSet<>();
        int max = candidates.size();

        int attempts = 0;
        while (selectedIndices.size() < count && selectedIndices.size() < max && attempts < count * 4) {
            selectedIndices.add(random.nextInt(max));
            attempts++;
        }

        if (selectedIndices.size() < count && selectedIndices.size() < max) {
            for (int i = 0; i < max && selectedIndices.size() < count; i++) {
                selectedIndices.add(i);
            }
        }

        for (int idx : selectedIndices) {
            result.add(candidates.get(idx));
        }

        return result;
    }

    private Drawable getIconDrawableInternal(IconPack pack, int resId) {
        if (resId == 0) return null;
        Drawable d = null;
        if (pack.isBase()) {
            SparseIntArray map = pack.getPreinstalledMap();
            int mapped = map != null ? map.get(resId, -1) : -1;
            if (mapped != -1) {
                d = ResourcesCompat.getDrawable(getResources(), mapped, null);
            }
        } else {
            d = IconManager.INSTANCE.getPackIconDrawable(pack, resId);
        }

        if (d == null) {
            if (getResources() instanceof ExteraResources) {
                try {
                    d = ((ExteraResources) getResources()).getOriginalDrawable(resId);
                } catch (Exception e) {
                    d = ResourcesCompat.getDrawable(getResources(), resId, null);
                }
            } else {
                d = ResourcesCompat.getDrawable(getResources(), resId, null);
            }
        }

        if (d != null) {
            d = d.mutate();
            d.setColorFilter(new PorterDuffColorFilter(
                    Theme.getColor(Theme.key_windowBackgroundWhiteGrayIcon),
                    PorterDuff.Mode.MULTIPLY
            ));
        }
        return d;
    }

    private void scheduleNext() {
        removeCallbacks(changeRunnable);
        if (attached && shouldAnimate) {
            postDelayed(changeRunnable, refreshTimeMilliseconds);
        }
    }

    private void animateToNext() {
        if (!attached || !shouldAnimate) return;
        loadIcons(true);
    }

    private void startTransition(List<Integer> nextIds) {
        if (!attached) {
            return;
        }
        if (animator != null) {
            animator.cancel();
        }

        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(300);
        animator.setInterpolator(CubicBezierInterpolator.DEFAULT);
        animator.addUpdateListener(a -> {
            animationProgress = (float) a.getAnimatedValue();
            invalidate();
        });
        animator.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled;

            @Override
            public void onAnimationCancel(Animator animation) {
                cancelled = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (animator == animation) {
                    animator = null;
                }
                if (cancelled || !attached) {
                    return;
                }
                for (int i = 0; i < currentIcons.length; i++) {
                    if (i < nextIds.size()) {
                        currentIconIds[i] = nextIds.get(i);
                    } else {
                        currentIconIds[i] = 0;
                    }
                    currentIcons[i] = nextIcons[i];
                    nextIcons[i] = null;
                }
                animationProgress = 0f;
                invalidate();
                scheduleNext();
            }
        });
        animator.start();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        attached = true;
        if (shouldAnimate) {
            scheduleNext();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        attached = false;
        removeCallbacks(changeRunnable);
        if (animator != null) {
            var oldAnimator = animator;
            animator = null;
            oldAnimator.cancel();
        }
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        if (isCircularMode) {
            drawCircularLayout(canvas);
            return;
        }

        if (cols == 2 && rows == 2 && count == 4) {
            int count = 0;
            for (Drawable d : currentIcons) {
                if (d != null) count++;
            }

            if (count == 1) {
                int iconSize = AndroidUtilities.dp(24);
                int x = (getWidth() - iconSize) / 2;
                int y = (getHeight() - iconSize) / 2;
                drawIcon(canvas, 0, x, y, iconSize, 1.0f);
            } else if (count == 2) {
                int iconSize = AndroidUtilities.dp(20);
                int gap = AndroidUtilities.dp(4);
                int y = (getHeight() - iconSize) / 2;
                drawIcon(canvas, 0, 0, y, iconSize, 1.0f);
                drawIcon(canvas, 1, iconSize + gap, y, iconSize, 1.0f);
            } else if (count == 3) {
                int iconSize = AndroidUtilities.dp(20);
                int gap = AndroidUtilities.dp(4);
                drawIcon(canvas, 0, 0, 0, iconSize, 1.0f);
                drawIcon(canvas, 1, iconSize + gap, 0, iconSize, 1.0f);
                int x = (getWidth() - iconSize) / 2;
                drawIcon(canvas, 2, x, iconSize + gap, iconSize, 1.0f);
            } else {
                int iconSize = AndroidUtilities.dp(20);
                int gap = AndroidUtilities.dp(4);
                int offset = iconSize + gap;

                drawIcon(canvas, 0, 0, 0, iconSize, 1.0f);
                drawIcon(canvas, 1, offset, 0, iconSize, 1.0f);
                drawIcon(canvas, 2, 0, offset, iconSize, 1.0f);
                drawIcon(canvas, 3, offset, offset, iconSize, 1.0f);
            }
            return;
        }

        int drawn = 0;
        int startX = getPaddingLeft();
        int startY = getPaddingTop();

        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (drawn >= currentIcons.length) break;

                int x = startX + c * (iconSize + gap);
                int y = startY + r * (iconSize + gap);
                drawIcon(canvas, drawn, x, y, iconSize, 1.0f);
                drawn++;
            }
        }
    }

    private void drawCircularLayout(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        int cx = w / 2;
        int cy = h / 2;

        drawIcon(canvas, 0, cx - circCenterSize / 2, cy - circCenterSize / 2, circCenterSize, 1.0f);

        int itemsPerOrbit = 6;
        double angleStep = 360.0 / itemsPerOrbit;

        int innerStartIdx = 1;

        for (int i = 0; i < itemsPerOrbit; i++) {
            int idx = innerStartIdx + i;
            if (idx >= currentIcons.length) break;

            double angle = Math.toRadians(i * angleStep);

            int ix = (int) (cx + circInnerRadius * Math.cos(angle)) - circInnerIconSize / 2;
            int iy = (int) (cy + circInnerRadius * Math.sin(angle)) - circInnerIconSize / 2;

            drawIcon(canvas, idx, ix, iy, circInnerIconSize, 0.7f);
        }

        int outerStartIdx = innerStartIdx + itemsPerOrbit;

        for (int i = 0; i < itemsPerOrbit; i++) {
            int idx = outerStartIdx + i;
            if (idx >= currentIcons.length) break;

            double angle = Math.toRadians((i * angleStep) + (angleStep / 2.0));

            int ox = (int) (cx + circOuterRadius * Math.cos(angle)) - circOuterIconSize / 2;
            int oy = (int) (cy + circOuterRadius * Math.sin(angle)) - circOuterIconSize / 2;

            drawIcon(canvas, idx, ox, oy, circOuterIconSize, 0.3f);
        }
    }

    private void drawIcon(Canvas canvas, int index, int x, int y, int size, float maxAlpha) {
        if (index >= currentIcons.length) return;

        canvas.save();
        canvas.translate(x, y);

        Drawable current = currentIcons[index];
        Drawable next = nextIcons[index];

        if (current != null) {
            current.setBounds(0, 0, size, size);
            float alpha = (1f - animationProgress) * maxAlpha;
            current.setAlpha((int) (255 * alpha));
            current.draw(canvas);
        }

        if (next != null) {
            next.setBounds(0, 0, size, size);
            float alpha = animationProgress * maxAlpha;
            next.setAlpha((int) (255 * alpha));
            next.draw(canvas);
        }

        canvas.restore();
    }
}