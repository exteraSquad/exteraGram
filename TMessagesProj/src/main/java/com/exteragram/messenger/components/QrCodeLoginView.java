/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.components;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.PorterDuffXfermode;
import android.graphics.Shader;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.TextView;

import com.google.zxing.EncodeHintType;
import org.telegram.messenger.TelegramQRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.messenger.SvgHelper;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RLottieDrawable;
import org.telegram.ui.Components.SlideView;

import java.util.HashMap;

public class QrCodeLoginView extends SlideView {

    private final TextView titleView;
    private final TextView subtitleView;
    private final QrRenderView qrRenderView;

    public QrCodeLoginView(Context context) {
        super(context);

        setOrientation(VERTICAL);
        setGravity(Gravity.CENTER);

        titleView = new TextView(context);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setText(getString(R.string.LoginQrTitle));
        titleView.setGravity(Gravity.CENTER);
        titleView.setLineSpacing(dp(2), 1.0f);
        addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 32, 16, 32, 0));

        subtitleView = new TextView(context);
        subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        subtitleView.setGravity(Gravity.CENTER_HORIZONTAL);
        subtitleView.setLineSpacing(dp(2), 1.0f);
        subtitleView.setText(getString(R.string.LoginQrSubtitle));
        addView(subtitleView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 12, 8, 12, 0));

        qrRenderView = new QrRenderView(context);
        addView(qrRenderView, LayoutHelper.createLinear(280, 280, Gravity.CENTER_HORIZONTAL, 30, 30, 30, 30));
    }

    @Override
    public String getHeaderName() {
        return getString(R.string.LoginQrTitle);
    }

    @Override
    public boolean needBackButton() {
        return true;
    }

    public void setData(String link) {
        qrRenderView.setData(link);
    }

    public void clear() {
        qrRenderView.clear();
    }

    public void clear(boolean showLoading) {
        qrRenderView.clear(showLoading);
    }

    @Override
    public void updateColors() {
        titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        subtitleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText6));
        qrRenderView.updateColors();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        qrRenderView.dispose();
    }

    private static class QrRenderView extends View {

        private static final int TRANSITION_DIRECTION_UP = 0;
        private static final int TRANSITION_DIRECTION_DOWN = 1;

        private final int crossfadeWidthDp = 140;

        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final AnimatedFloat contentBitmapAlpha = new AnimatedFloat(1f, this, 0, 2000, CubicBezierInterpolator.EASE_OUT_QUINT);
        private final Paint crossfadeFromPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint crossfadeToPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path clipPath = new Path();
        private final Path qrAreaClipPath = new Path();

        private RLottieDrawable loadingMatrix;
        private Bitmap contentBitmap;
        private Bitmap oldContentBitmap;
        private Bitmap qrLogo;
        private int qrLogoSize;
        private String link;
        private Integer hadWidth;
        private Integer hadHeight;
        private String hadLink;
        private boolean firstPrepare = true;
        private boolean loadingVisible = true;
        private int transitionDirection = TRANSITION_DIRECTION_UP;

        QrRenderView(Context context) {
            super(context);

            crossfadeFromPaint.setShader(new LinearGradient(0, 0, 0, dp(crossfadeWidthDp), new int[]{0xffffffff, 0}, new float[]{0f, 1f}, Shader.TileMode.CLAMP));
            crossfadeFromPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));

            crossfadeToPaint.setShader(new LinearGradient(0, 0, 0, dp(crossfadeWidthDp), new int[]{0, 0xffffffff}, new float[]{0f, 1f}, Shader.TileMode.CLAMP));
            crossfadeToPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));

            clear();
        }

        void setData(String link) {
            this.link = link;
            if (!TextUtils.isEmpty(link) && contentBitmap == null) {
                loadingVisible = true;
            }
            final int w = getWidth();
            final int h = getHeight();
            final String requestedLink = link;
            Utilities.themeQueue.postRunnable(() -> prepareContent(w, h, requestedLink));
            invalidate();
        }

        void clear() {
            clear(true);
        }

        void clear(boolean showLoading) {
            link = null;
            hadLink = null;
            hadWidth = null;
            hadHeight = null;
            firstPrepare = true;

            if (oldContentBitmap != null) {
                oldContentBitmap.recycle();
                oldContentBitmap = null;
            }

            if (contentBitmap != null && showLoading) {
                oldContentBitmap = contentBitmap;
                contentBitmap = null;
                loadingVisible = true;
                transitionDirection = TRANSITION_DIRECTION_DOWN;
                contentBitmapAlpha.set(0f, true);
                contentBitmapAlpha.set(1f);
            } else {
                if (contentBitmap != null) {
                    contentBitmap.recycle();
                    contentBitmap = null;
                }
                loadingVisible = showLoading;
                contentBitmapAlpha.set(1f, true);
            }

            if (!showLoading && loadingMatrix != null && loadingMatrix.isRunning()) {
                loadingMatrix.stop();
            }

            invalidate();
        }

        void updateColors() {
            invalidate();
        }

        private void prepareContent(int w, int h, String requestedLink) {
            if (w == 0 || h == 0) {
                return;
            }

            if (TextUtils.isEmpty(requestedLink)) {
                AndroidUtilities.runOnUIThread(() -> {
                    if (!TextUtils.equals(link, requestedLink)) {
                        return;
                    }
                    firstPrepare = false;
                    if (contentBitmap != null) {
                        Bitmap oldBitmap = contentBitmap;
                        contentBitmap = null;
                        contentBitmapAlpha.set(0, true);
                        if (oldContentBitmap != null) {
                            oldContentBitmap.recycle();
                        }
                        oldContentBitmap = oldBitmap;
                        invalidate();
                    }
                });
                return;
            }

            if (TextUtils.equals(requestedLink, hadLink) && hadWidth != null && hadHeight != null && hadWidth == w && hadHeight == h) {
                return;
            }

            Bitmap qrBitmap = null;
            HashMap<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
            hints.put(EncodeHintType.MARGIN, 0);

            int qrSize = 37;
            int multiple = Math.max(1, w / qrSize);
            int size = multiple * qrSize + 32;
            TelegramQRCodeWriter writer = new TelegramQRCodeWriter();
            try {
                qrBitmap = writer.encode(requestedLink, size, size, hints, null, 0.75f, 0, Color.BLACK);
            } catch (Exception e) {
                FileLog.e(e);
            }
            if (qrBitmap == null) {
                return;
            }

            Bitmap bitmap = qrBitmap;

            AndroidUtilities.runOnUIThread(() -> {
                if (!TextUtils.equals(link, requestedLink)) {
                    if (bitmap != null && !bitmap.isRecycled()) {
                        bitmap.recycle();
                    }
                    return;
                }

                hadWidth = w;
                hadHeight = h;
                hadLink = requestedLink;

                Runnable applyBitmap = () -> {
                    final boolean animateFromLoading = firstPrepare && loadingVisible;
                    Bitmap oldBitmap = contentBitmap;
                    contentBitmap = bitmap;
                    if (!firstPrepare || animateFromLoading) {
                        transitionDirection = TRANSITION_DIRECTION_UP;
                        contentBitmapAlpha.set(0, true);
                    }
                    firstPrepare = false;
                    if (oldContentBitmap != null) {
                        oldContentBitmap.recycle();
                    }
                    oldContentBitmap = oldBitmap;
                    loadingVisible = animateFromLoading;
                    invalidate();
                };
                applyBitmap.run();
            });
        }

        private void drawLoading(Canvas canvas, int multiple, int size, float scale) {
            if (loadingMatrix == null) {
                loadingMatrix = new RLottieDrawable(R.raw.qr_matrix, dp(200), dp(200));
                loadingMatrix.setMasterParent(this);
                loadingMatrix.setAutoRepeat(1);
                loadingMatrix.setColorFilter(new PorterDuffColorFilter(Color.BLACK, PorterDuff.Mode.MULTIPLY));
                loadingMatrix.start();
            } else if (!loadingMatrix.isRunning()) {
                loadingMatrix.start();
            }

            int width = getWidth();
            int qrInset = Math.round(16f * scale);
            loadingMatrix.setBounds(qrInset, qrInset, width - qrInset, width - qrInset);
            loadingMatrix.setAlpha(255);
            loadingMatrix.draw(canvas);

            int imageBlocks = Math.round((size - 32) / 4.65f / multiple);
            if (imageBlocks % 2 != 37 % 2) {
                imageBlocks++;
            }
            int imageFrameSize = imageBlocks * multiple;
            int imageSize = imageFrameSize - 24;
            int imageFrameX = (size - imageFrameSize) / 2;
            int imageX = (size - imageSize) / 2;

            canvas.save();
            canvas.scale(scale, scale);
            drawFinderPatterns(canvas, size, multiple);
            paint.setColor(Color.WHITE);
            float frameRadius = (7f * multiple) / 4.0f * 0.75f;
            canvas.drawRoundRect(imageFrameX, imageFrameX, imageFrameX + imageFrameSize, imageFrameX + imageFrameSize, frameRadius, frameRadius, paint);
            if (qrLogo == null) {
                String svg = AndroidUtilities.readRes(null, R.raw.qr_logo);
                qrLogo = SvgHelper.getBitmap(svg, imageSize, imageSize, false);
                qrLogoSize = imageSize;
            } else if (qrLogoSize != imageSize) {
                qrLogo.recycle();
                String svg = AndroidUtilities.readRes(null, R.raw.qr_logo);
                qrLogo = SvgHelper.getBitmap(svg, imageSize, imageSize, false);
                qrLogoSize = imageSize;
            }
            if (qrLogo != null) {
                canvas.drawBitmap(qrLogo, imageX, imageX, null);
            }
            canvas.restore();
        }

        private void drawFinderPatterns(Canvas canvas, int size, int multiple) {
            final float sideSize = 7f;
            final float padding = 16f;
            final float innerBlackInset = multiple * 2f;
            final float outerRadius = (sideSize * multiple) / 3.0f * 0.75f;
            final float middleRadius = (sideSize * multiple) / 4.0f * 0.75f;
            final float innerRadius = ((sideSize - 2f) * multiple) / 4.0f * 0.75f;
            final float finderSize = sideSize * multiple;

            for (int i = 0; i < 3; i++) {
                float x;
                float y;
                if (i == 0) {
                    x = padding;
                    y = padding;
                } else if (i == 1) {
                    x = size - finderSize - padding;
                    y = padding;
                } else {
                    x = padding;
                    y = size - finderSize - padding;
                }

                paint.setColor(Color.BLACK);
                canvas.drawRoundRect(x, y, x + finderSize, y + finderSize, outerRadius, outerRadius, paint);
                paint.setColor(Color.WHITE);
                canvas.drawRoundRect(x + multiple, y + multiple, x + (sideSize - 1f) * multiple, y + (sideSize - 1f) * multiple, middleRadius, middleRadius, paint);
                paint.setColor(Color.BLACK);
                canvas.drawRoundRect(x + innerBlackInset, y + innerBlackInset, x + finderSize - innerBlackInset, y + finderSize - innerBlackInset, innerRadius, innerRadius, paint);
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            paint.setColor(Color.WHITE);
            canvas.drawRoundRect(0, 0, getMeasuredWidth(), getMeasuredHeight(), dp(14), dp(14), paint);

            clipPath.reset();
            clipPath.addRoundRect(0, 0, getMeasuredWidth(), getMeasuredHeight(), dp(14), dp(14), Path.Direction.CW);
            canvas.save();
            canvas.clipPath(clipPath);

            int width = getWidth();
            int qrSize = 37;
            int multiple = Math.max(1, width / qrSize);
            int size = multiple * qrSize + 32;
            float scale = width / (float) size;
            float qrOuterRadius = ((7f * multiple) / 4.0f * 0.75f) * scale;

            qrAreaClipPath.reset();
            qrAreaClipPath.addRoundRect(0, 0, width, width, qrOuterRadius, qrOuterRadius, Path.Direction.CW);
            canvas.save();
            canvas.clipPath(qrAreaClipPath);

            float crossfadeAlpha = contentBitmapAlpha.set(1f);
            boolean crossfading = crossfadeAlpha > 0 && crossfadeAlpha < 1;
            boolean transitionDown = transitionDirection == TRANSITION_DIRECTION_DOWN;

            if (crossfadeAlpha < 1f) {
                if (crossfading) {
                    AndroidUtilities.rectTmp.set(0, 0, width, width);
                    canvas.saveLayerAlpha(AndroidUtilities.rectTmp, 255);
                }
                if (oldContentBitmap != null) {
                    canvas.save();
                    canvas.scale(scale, scale);
                    canvas.drawBitmap(oldContentBitmap, 0, 0, null);
                    canvas.restore();
                } else if (loadingVisible) {
                    drawLoading(canvas, multiple, size, scale);
                }
                if (crossfading) {
                    float h = dp(crossfadeWidthDp);
                    canvas.save();
                    canvas.translate(0, getScanLineY(crossfadeAlpha, width, h));
                    float oldTop = transitionDown ? -h - width : 0;
                    canvas.drawRect(0, oldTop, width, width + h, getOldLayerMaskPaint());
                    canvas.restore();
                    canvas.restore();
                }
            }

            if (crossfadeAlpha > 0f) {
                if (crossfading) {
                    AndroidUtilities.rectTmp.set(0, 0, width, width);
                    canvas.saveLayerAlpha(AndroidUtilities.rectTmp, 255);
                }
                if (contentBitmap != null) {
                    canvas.save();
                    canvas.scale(scale, scale);
                    canvas.drawBitmap(contentBitmap, 0f, 0f, null);
                    canvas.restore();
                } else if (loadingVisible) {
                    drawLoading(canvas, multiple, size, scale);
                }
                if (crossfading) {
                    float h = dp(crossfadeWidthDp);
                    canvas.save();
                    canvas.translate(0, getScanLineY(crossfadeAlpha, width, h));
                    float newTop = transitionDown ? 0 : -h - width;
                    canvas.drawRect(0, newTop, width, width + h, getNewLayerMaskPaint());
                    canvas.restore();
                    canvas.restore();
                }
            }

            canvas.restore();

            if (loadingVisible && contentBitmap != null && !contentBitmapAlpha.isInProgress() && crossfadeAlpha >= 1f) {
                loadingVisible = false;
                invalidate();
            }

            if (oldContentBitmap != null && contentBitmap == null && !contentBitmapAlpha.isInProgress() && crossfadeAlpha >= 1f) {
                oldContentBitmap.recycle();
                oldContentBitmap = null;
                invalidate();
            }

            if (!loadingVisible && loadingMatrix != null && loadingMatrix.isRunning()) {
                loadingMatrix.stop();
            }

            canvas.restore();
        }

        private float getScanLineY(float alpha, int height, float h) {
            if (transitionDirection == TRANSITION_DIRECTION_DOWN) {
                return -h + (height + h) * alpha;
            }
            return -h + (height + h) * (1f - alpha);
        }

        private Paint getOldLayerMaskPaint() {
            return transitionDirection == TRANSITION_DIRECTION_DOWN ? crossfadeFromPaint : crossfadeToPaint;
        }

        private Paint getNewLayerMaskPaint() {
            return transitionDirection == TRANSITION_DIRECTION_DOWN ? crossfadeToPaint : crossfadeFromPaint;
        }

        void dispose() {
            if (loadingMatrix != null) {
                loadingMatrix.stop();
                loadingMatrix.recycle(false);
                loadingMatrix = null;
            }
            if (qrLogo != null) {
                qrLogo.recycle();
                qrLogo = null;
                qrLogoSize = 0;
            }
            if (contentBitmap != null) {
                contentBitmap.recycle();
                contentBitmap = null;
            }
            if (oldContentBitmap != null) {
                oldContentBitmap.recycle();
                oldContentBitmap = null;
            }
        }
    }
}
