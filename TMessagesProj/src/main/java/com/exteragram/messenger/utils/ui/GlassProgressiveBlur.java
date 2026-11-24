/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.utils.ui;

import android.graphics.BlendMode;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.RecordingCanvas;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.Shader;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

@RequiresApi(Build.VERSION_CODES.S)
public final class GlassProgressiveBlur {

    public static final float[] HEADER_SIGMAS_DP = {1.5f, 3.5f, 6.5f, 10f};

    private static final int MAX_LEVEL_DOWNSAMPLE = 4;

    private final RenderNode sourceNode = new RenderNode("progressive-source");
    private final float[] sigmas;
    private final RenderNode[] levels;
    private final int[] downsamples;
    private final int[] nodeStarts;
    private final int[] visibleStarts;
    private final int[] visibleEnds;
    private final Matrix matrix = new Matrix();

    private int width = -1, height = -1;
    private boolean vertical, edgeAtEnd;

    public GlassProgressiveBlur(@NonNull float[] sigmasPx) {
        sigmas = sigmasPx;
        final int count = sigmas.length + 1;
        levels = new RenderNode[count];
        downsamples = new int[count];
        nodeStarts = new int[count];
        visibleStarts = new int[count];
        visibleEnds = new int[count];
        for (int i = 1; i < count; i++) {
            levels[i] = new RenderNode("progressive-" + i);
            downsamples[i] = Math.max(1, Math.min(MAX_LEVEL_DOWNSAMPLE, (int) (sigmas[i - 1] / 3f)));
        }
    }

    public void setLayout(int width, int height, boolean vertical, boolean edgeAtEnd) {
        if (this.width == width && this.height == height && this.vertical == vertical && this.edgeAtEnd == edgeAtEnd) {
            return;
        }
        this.width = width;
        this.height = height;
        this.vertical = vertical;
        this.edgeAtEnd = edgeAtEnd;
        final int axis = vertical ? height : width;
        final int cross = vertical ? width : height;
        final int last = levels.length - 1;
        for (int i = 0; i <= last; i++) {
            final float a = axisPosition(axis, Math.max(0, i - 1) / (float) last);
            final float b = axisPosition(axis, Math.min(last, i + 1) / (float) last);
            final int overlap = i < last ? 2 * downsamples[i + 1] + 2 : 0;
            visibleStarts[i] = Math.max(0, (int) Math.floor(Math.min(a, b)) - 1 - (edgeAtEnd ? overlap : 0));
            visibleEnds[i] = Math.min(axis, (int) Math.ceil(Math.max(a, b)) + 1 + (edgeAtEnd ? 0 : overlap));
            if (i == 0) {
                continue;
            }

            final int ds = downsamples[i];
            final int margin = (int) Math.ceil(sigmas[i - 1] * 3f);
            final int start = Math.max(0, visibleStarts[i] - margin) / ds * ds;
            final int end = Math.min(axis, visibleEnds[i] + margin);
            nodeStarts[i] = start;
            final int along = (int) Math.ceil((end - start) / (float) ds);
            final int across = (int) Math.ceil(cross / (float) ds);

            final RenderNode level = levels[i];
            level.setPosition(0, 0, vertical ? across : along, vertical ? along : across);
            level.setPivotX(0f);
            level.setPivotY(0f);
            level.setScaleX(ds);
            level.setScaleY(ds);
            level.setTranslationX(vertical ? 0 : start);
            level.setTranslationY(vertical ? start : 0);

            final float from = axisPosition(axis, (i - 1) / (float) last);
            final float to = axisPosition(axis, i / (float) last);
            final LinearGradient mask = vertical
                    ? new LinearGradient(0f, from, 0f, to, Color.TRANSPARENT, Color.WHITE, Shader.TileMode.CLAMP)
                    : new LinearGradient(from, 0f, to, 0f, Color.TRANSPARENT, Color.WHITE, Shader.TileMode.CLAMP);
            matrix.setTranslate(vertical ? 0 : -start, vertical ? -start : 0);
            matrix.postScale(1f / ds, 1f / ds);
            mask.setLocalMatrix(matrix);

            final float sigma = sigmas[i - 1] / ds;
            final float radius = Math.max(0f, (sigma - .5f) / .57735f);
            level.setRenderEffect(RenderEffect.createBlendModeEffect(
                    RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.MIRROR),
                    RenderEffect.createShaderEffect(mask),
                    BlendMode.DST_IN));
        }
    }

    private float axisPosition(float axis, float fraction) {
        return edgeAtEnd ? axis * (1f - fraction) : axis * fraction;
    }

    @NonNull
    public RecordingCanvas beginRecording(int backgroundColor) {
        sourceNode.setPosition(0, 0, width, height);
        final RecordingCanvas canvas = sourceNode.beginRecording();
        canvas.drawColor(backgroundColor | 0xFF000000);
        return canvas;
    }

    public void endRecording() {
        sourceNode.endRecording();
        for (int i = 1; i < levels.length; i++) {
            final RecordingCanvas canvas = levels[i].beginRecording();
            final float scale = 1f / downsamples[i];
            canvas.scale(scale, scale);
            canvas.translate(vertical ? 0 : -nodeStarts[i], vertical ? -nodeStarts[i] : 0);
            canvas.drawRenderNode(sourceNode);
            levels[i].endRecording();
        }
    }

    public void draw(@NonNull Canvas canvas) {
        for (int i = 0; i < levels.length; i++) {
            canvas.save();
            if (vertical) {
                canvas.clipRect(0, visibleStarts[i], width, visibleEnds[i]);
            } else {
                canvas.clipRect(visibleStarts[i], 0, visibleEnds[i], height);
            }
            canvas.drawRenderNode(i == 0 ? sourceNode : levels[i]);
            canvas.restore();
        }
    }

    public void discard() {
        sourceNode.discardDisplayList();
        for (int i = 1; i < levels.length; i++) {
            levels[i].discardDisplayList();
        }
        width = height = -1;
    }
}
