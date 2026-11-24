/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.components.supporter;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.lerp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.view.HapticFeedbackConstants;
import android.view.TextureView;
import android.view.View;

import androidx.annotation.NonNull;

import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.CubicBezierInterpolator;

import java.lang.ref.WeakReference;
import java.util.function.Supplier;

public final class SupporterBadgeLanding {

    private static final long DURATION = 800;
    private static final long PENDING_TIMEOUT = 2000;

    private static Pending pending;

    private SupporterBadgeLanding() {
    }

    private static final class Pending {
        final long userId;
        final Bitmap bitmap;
        final float screenX, screenY;
        final float contentSize;
        final WeakReference<View> cover;
        final long time = SystemClock.elapsedRealtime();

        Pending(long userId, Bitmap bitmap, float screenX, float screenY, float contentSize, View cover) {
            this.userId = userId;
            this.bitmap = bitmap;
            this.screenX = screenX;
            this.screenY = screenY;
            this.contentSize = contentSize;
            this.cover = new WeakReference<>(cover);
        }
    }

    public static boolean prepare(long userId, View icon, float contentScale) {
        if (icon.getWidth() <= 0 || icon.getHeight() <= 0) {
            return false;
        }
        Bitmap bitmap = icon instanceof TextureView textureView ? textureView.getBitmap() : null;
        if (bitmap == null) {
            bitmap = Bitmap.createBitmap(icon.getWidth(), icon.getHeight(), Bitmap.Config.ARGB_8888);
            icon.draw(new Canvas(bitmap));
        }
        final int[] location = new int[2];
        icon.getLocationOnScreen(location);
        clear();
        pending = new Pending(userId, bitmap, location[0] + icon.getWidth() / 2f, location[1] + icon.getHeight() / 2f, icon.getHeight() * contentScale, icon.getRootView());
        return true;
    }

    private static void clear() {
        if (pending != null) {
            pending.bitmap.recycle();
            pending = null;
        }
    }

    public static void start(BaseFragment fragment, long userId, Supplier<SupporterEnterFrom> target, SupporterBadgeHint hint) {
        final Pending from = pending;
        if (from == null || from.userId != userId) {
            return;
        }
        pending = null;
        final Activity activity = fragment.getParentActivity();
        if (activity == null || SystemClock.elapsedRealtime() - from.time > PENDING_TIMEOUT) {
            from.bitmap.recycle();
            return;
        }
        final View decor = activity.getWindow().getDecorView();
        final View cover = from.cover.get();
        final Flight flight = new Flight(from, target);
        final View.OnAttachStateChangeListener coverListener = new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(@NonNull View v) {
            }

            @Override
            public void onViewDetachedFromWindow(@NonNull View v) {
                v.removeOnAttachStateChangeListener(this);
                flight.moveTo(decor);
            }
        };
        if (cover != null && cover != decor && cover.isAttachedToWindow()) {
            flight.moveTo(cover);
            cover.addOnAttachStateChangeListener(coverListener);
        } else {
            flight.moveTo(decor);
        }
        hint.hold();

        final ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.addUpdateListener(a -> {
            flight.progress = (float) a.getAnimatedValue();
            flight.invalidateSelf();
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (cover != null) {
                    cover.removeOnAttachStateChangeListener(coverListener);
                }
                flight.moveTo(null);
                flight.land();
                from.bitmap.recycle();
                try {
                    decor.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                } catch (Exception ignored) {
                }
                hint.release();
            }
        });
        animator.setDuration(DURATION);
        animator.setInterpolator(null);
        animator.start();
    }

    private static final class Flight extends Drawable {

        private final Pending from;
        private final Supplier<SupporterEnterFrom> target;
        private final Paint bitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
        private final int[] location = new int[2];
        private final RectF rect = new RectF();
        private final Rect savedBounds = new Rect();
        private View host;
        private SupporterEnterFrom lastTarget;
        float progress;

        Flight(Pending from, Supplier<SupporterEnterFrom> target) {
            this.from = from;
            this.target = target;
        }

        void moveTo(View newHost) {
            if (host == newHost) {
                return;
            }
            if (host != null) {
                host.getOverlay().remove(this);
            }
            host = newHost;
            if (host != null) {
                setBounds(0, 0, host.getWidth(), host.getHeight());
                host.getOverlay().add(this);
            }
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            if (host == null) {
                return;
            }
            final SupporterEnterFrom to = target.get();
            if (to != null) {
                lastTarget = to;
            }
            host.getLocationOnScreen(location);
            final float fromX = from.screenX - location[0];
            final float fromY = from.screenY - location[1];
            final float toX, toY, toSize;
            final Drawable badge = lastTarget != null ? lastTarget.drawable : null;
            if (badge != null) {
                toX = lastTarget.screenX - location[0];
                toY = lastTarget.screenY - location[1];
                toSize = lastTarget.scale * badge.getIntrinsicWidth();
            } else {
                toX = fromX;
                toY = dp(120);
                toSize = dp(24);
            }

            final float eased = CubicBezierInterpolator.EASE_OUT_QUINT.getInterpolation(progress);
            final float x = lerp(fromX, toX, CubicBezierInterpolator.EASE_BOTH.getInterpolation(progress));
            final float y = lerp(fromY, toY, eased);
            final float size = lerp(from.contentSize, toSize, eased);
            final float cross = Utilities.clamp((progress - .35f) / .45f, 1f, 0f);

            if (cross < 1f) {
                final float scale = size / from.contentSize;
                final float w = from.bitmap.getWidth() * scale / 2f;
                final float h = from.bitmap.getHeight() * scale / 2f;
                rect.set(x - w, y - h, x + w, y + h);
                bitmapPaint.setAlpha((int) (255 * (1f - cross)));
                canvas.drawBitmap(from.bitmap, null, rect, bitmapPaint);
            }
            if (badge != null) {
                if (cross > 0f) {
                    final int iw = badge.getIntrinsicWidth();
                    final int ih = badge.getIntrinsicHeight();
                    canvas.save();
                    final float s = size / iw;
                    canvas.scale(s, s, x, y);
                    badge.copyBounds(savedBounds);
                    badge.setBounds((int) x - iw / 2, (int) y - ih / 2, (int) x + iw / 2, (int) y + ih / 2);
                    badge.setAlpha((int) (255 * cross));
                    badge.draw(canvas);
                    badge.setBounds(savedBounds);
                    canvas.restore();
                }
                badge.setAlpha(0);
            }
        }

        void land() {
            if (lastTarget != null) {
                lastTarget.drawable.setAlpha(255);
                lastTarget.invalidate();
            }
        }

        @Override
        public void setAlpha(int alpha) {
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {
        }

        @Override
        @SuppressWarnings("deprecation")
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}
