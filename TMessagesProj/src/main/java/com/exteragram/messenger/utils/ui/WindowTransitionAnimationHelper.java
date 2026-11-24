/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.utils.ui;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.Utilities.clamp01;

import android.graphics.Canvas;
import android.graphics.RecordingCanvas;
import android.graphics.RenderNode;
import android.provider.Settings;
import android.view.View;

import androidx.annotation.RequiresApi;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.ui.Components.CubicBezierInterpolator;

public final class WindowTransitionAnimationHelper {

    private static final float DURATION = 450f;
    private static final int SLIDE_DISTANCE_DP = 96;
    private static final float ENTER_FADE_IN_OFFSET = 50f;
    private static final float ENTER_FADE_IN_DURATION = 83f;
    private static final float EXIT_FADE_OUT_OFFSET = 35f;
    private static final float EXIT_FADE_OUT_DURATION = 83f;

    private WindowTransitionAnimationHelper() {
    }

    public static float getDuration() {
        float scale;
        try {
            scale = Settings.Global.getFloat(
                    ApplicationLoader.applicationContext.getContentResolver(),
                    Settings.Global.TRANSITION_ANIMATION_SCALE,
                    1.0f
            );
        } catch (Exception e) {
            scale = 1.0f;
        }
        return Math.max(1f, DURATION * scale);
    }

    public static float getSlideDistance() {
        return dp(SLIDE_DISTANCE_DP);
    }

    public static void apply(View entering, View exiting, boolean open, float progress) {
        final float clamped = clamp01(progress);
        final float distance = dp(SLIDE_DISTANCE_DP);
        final float slide = Math.round(distance * CubicBezierInterpolator.Emphasized.getInterpolation(clamped));
        if (entering != null) {
            entering.setTranslationX(open ? distance - slide : slide - distance);
            entering.setAlpha(open ? fade(clamped, ENTER_FADE_IN_OFFSET, ENTER_FADE_IN_DURATION) : 1f);
        }
        if (exiting != null) {
            exiting.setTranslationX(open ? -slide : slide);
            exiting.setAlpha(open ? 1f : 1f - fade(clamped, EXIT_FADE_OUT_OFFSET, EXIT_FADE_OUT_DURATION));
        }
    }

    private static float fade(float progress, float offset, float duration) {
        return clamp01((progress * DURATION - offset) / duration);
    }

    @RequiresApi(29)
    public static final class WindowEdgeExtension {

        private static final int STRIP_PX = 1;

        private final RenderNode node = new RenderNode("windowEdgeExtension");
        private boolean captured;

        public boolean isCaptured() {
            return captured;
        }

        public boolean capture(View source, boolean leftEdge) {
            final int width = source.getWidth();
            final int height = source.getHeight();
            if (width <= 0 || height <= 0) {
                return false;
            }
            node.setPosition(0, 0, STRIP_PX, height);
            node.setPivotX(0);
            node.setPivotY(0);
            final RecordingCanvas canvas = node.beginRecording(STRIP_PX, height);
            try {
                canvas.translate(leftEdge ? 0 : -(width - STRIP_PX), 0);
                source.draw(canvas);
            } finally {
                node.endRecording();
            }
            node.setUseCompositingLayer(true, null);
            captured = true;
            return true;
        }

        public void draw(Canvas canvas, float left, float width, float alpha) {
            if (!captured || width <= 0 || alpha <= 0 || !(canvas instanceof RecordingCanvas)) {
                return;
            }
            node.setScaleX(width / STRIP_PX);
            node.setTranslationX(left);
            node.setAlpha(alpha);
            canvas.drawRenderNode(node);
        }

        public void release() {
            if (!captured) {
                return;
            }
            captured = false;
            node.setUseCompositingLayer(false, null);
            node.discardDisplayList();
        }
    }
}
