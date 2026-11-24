/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.pillstack.custom.ui;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;
import static org.telegram.messenger.AndroidUtilities.lerp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.exteragram.messenger.pillstack.custom.CustomPillSpec;
import com.exteragram.messenger.pillstack.custom.PillColors;

import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.ButtonBounce;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.Paint.ColorPickerBottomSheet;

import java.util.ArrayList;
import java.util.Objects;

@SuppressLint("ViewConstructor")
public class PillColorGrid extends View {

    private static final int COLUMNS = 9;
    private static final int TYPE_KEEP = 0;
    private static final int TYPE_NEUTRAL = 1;
    private static final int TYPE_PRESET = 2;
    private static final int TYPE_CUSTOM = 3;
    private static final int[] RAINBOW = {0xFFFF4D4D, 0xFFFFD43B, 0xFF51CF66, 0xFF22D3EE, 0xFF4C6EF5, 0xFFCC5DE8, 0xFFFF4D4D};

    private final Theme.ResourcesProvider resourcesProvider;
    private final Utilities.Callback<String> onSelected;
    private final ArrayList<Cell> cells = new ArrayList<>();
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint closePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path circlePath = new Path();
    private final Path closePath = new Path();
    @Nullable
    private String selected;
    @Nullable
    private Cell pressedCell;

    public PillColorGrid(Context context, Theme.ResourcesProvider resourcesProvider, boolean allowKeep, Utilities.Callback<String> onSelected) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        this.onSelected = onSelected;
        ringPaint.setStyle(Paint.Style.STROKE);
        closePaint.setStyle(Paint.Style.STROKE);
        closePaint.setStrokeCap(Paint.Cap.ROUND);

        cells.add(new Cell(allowKeep ? TYPE_KEEP : TYPE_NEUTRAL, allowKeep ? null : CustomPillSpec.COLOR_NEUTRAL));
        for (String color : PillColors.getPalette()) {
            cells.add(new Cell(TYPE_PRESET, color));
        }
        cells.add(new Cell(TYPE_CUSTOM, null));
    }

    public void setSelectedColor(@Nullable String color) {
        selected = color;
        for (Cell cell : cells) {
            cell.setSelected(cell.matches(color), isLaidOut());
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        float iconSize = Math.min(dp(54), width / (COLUMNS + (COLUMNS + 1) * .28947f));
        float horizontalSeparator = Math.min(iconSize * .28947f, dp(8));
        float verticalSeparator = Math.min(iconSize * .315789474f, dp(11.33f));
        int rows = (cells.size() + COLUMNS - 1) / COLUMNS;
        setMeasuredDimension(width, (int) (iconSize * rows + verticalSeparator * (rows + 1)));

        float itemsWidth = iconSize * COLUMNS + horizontalSeparator * (COLUMNS + 1);
        float startX = (width - itemsWidth) / 2f + horizontalSeparator;
        for (int i = 0; i < cells.size(); i++) {
            float x = startX + (i % COLUMNS) * (iconSize + horizontalSeparator);
            float y = verticalSeparator + (i / COLUMNS) * (iconSize + verticalSeparator);
            cells.get(i).layout(x, y, iconSize, horizontalSeparator, verticalSeparator);
        }
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        for (Cell cell : cells) {
            cell.draw(canvas);
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        Cell cell = null;
        for (Cell c : cells) {
            if (c.clickBounds.contains(event.getX(), event.getY())) {
                cell = c;
                break;
            }
        }
        int action = event.getAction();
        if (action == MotionEvent.ACTION_DOWN) {
            pressedCell = cell;
            if (cell != null) {
                cell.bounce.setPressed(true);
            }
            if (getParent() != null) {
                getParent().requestDisallowInterceptTouchEvent(true);
            }
        } else if (action == MotionEvent.ACTION_MOVE) {
            if (pressedCell != cell) {
                if (pressedCell != null) {
                    pressedCell.bounce.setPressed(false);
                }
                if (cell != null) {
                    cell.bounce.setPressed(true);
                    if (pressedCell != null && cell.type != TYPE_CUSTOM) {
                        select(cell.color);
                    }
                }
                pressedCell = cell;
            }
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            if (action == MotionEvent.ACTION_UP && pressedCell != null) {
                if (pressedCell.type == TYPE_CUSTOM) {
                    openCustomPicker();
                } else {
                    select(pressedCell.color);
                }
            }
            for (Cell c : cells) {
                c.bounce.setPressed(false);
            }
            pressedCell = null;
        }
        return true;
    }

    private void select(@Nullable String color) {
        if (Objects.equals(selected, color)) {
            return;
        }
        selected = color;
        for (Cell cell : cells) {
            cell.setSelected(cell.matches(color), true);
        }
        onSelected.run(color);
    }

    private void openCustomPicker() {
        int initial = PillColors.isCustom(selected) ? Color.parseColor(selected) : PillColors.get(selected).top();
        new ColorPickerBottomSheet(getContext(), resourcesProvider)
                .setColor(initial)
                .setPipetteDelegate(new ColorPickerBottomSheet.PipetteDelegate() {
                    @Override
                    public void onStartColorPipette() {
                    }

                    @Override
                    public void onStopColorPipette() {
                    }

                    @Override
                    public ViewGroup getContainerView() {
                        return null;
                    }

                    @Override
                    public View getSnapshotDrawingView() {
                        return null;
                    }

                    @Override
                    public void onDrawImageOverCanvas(Bitmap bitmap, Canvas canvas) {
                    }

                    @Override
                    public boolean isPipetteVisible() {
                        return false;
                    }

                    @Override
                    public boolean isPipetteAvailable() {
                        return false;
                    }

                    @Override
                    public void onColorSelected(int color) {
                    }
                })
                .setColorListener(color -> select(PillColors.toCustom(color)))
                .show();
    }

    private class Cell {

        final int type;
        @Nullable
        final String color;
        final RectF bounds = new RectF();
        final RectF clickBounds = new RectF();
        final ButtonBounce bounce = new ButtonBounce(PillColorGrid.this);
        boolean selected;
        float selectT;
        @Nullable
        ValueAnimator selectAnimator;
        @Nullable
        Shader shader;
        @Nullable
        Shader innerShader;
        @Nullable
        String innerShaderColor;

        Cell(int type, @Nullable String color) {
            this.type = type;
            this.color = color;
        }

        boolean matches(@Nullable String value) {
            return switch (type) {
                case TYPE_KEEP -> value == null;
                case TYPE_CUSTOM -> PillColors.isCustom(value);
                default -> Objects.equals(color, value);
            };
        }

        void setSelected(boolean selected, boolean animated) {
            if (this.selected == selected && (selectAnimator != null || selectT == (selected ? 1f : 0f))) {
                return;
            }
            this.selected = selected;
            if (selectAnimator != null) {
                selectAnimator.cancel();
                selectAnimator = null;
            }
            float target = selected ? 1f : 0f;
            if (!animated) {
                selectT = target;
                invalidate();
                return;
            }
            ValueAnimator animator = ValueAnimator.ofFloat(selectT, target);
            animator.addUpdateListener(a -> {
                selectT = (float) a.getAnimatedValue();
                invalidate();
            });
            animator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (selectAnimator == animation) {
                        selectAnimator = null;
                    }
                }
            });
            animator.setDuration(320);
            animator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
            selectAnimator = animator;
            animator.start();
        }

        void layout(float x, float y, float size, float horizontalSeparator, float verticalSeparator) {
            bounds.set(x, y, x + size, y + size);
            clickBounds.set(bounds);
            clickBounds.inset(-horizontalSeparator / 2f, -verticalSeparator / 2f);
            if (type == TYPE_CUSTOM) {
                shader = new SweepGradient(bounds.centerX(), bounds.centerY(), RAINBOW, null);
            } else {
                PillColors colors = PillColors.get(color);
                shader = new LinearGradient(0, bounds.top, 0, bounds.bottom, colors.top(), colors.bottom(), Shader.TileMode.CLAMP);
            }
            innerShader = null;
        }

        private Shader getInnerShader(String customColor) {
            if (innerShader == null || !customColor.equals(innerShaderColor)) {
                PillColors colors = PillColors.get(customColor);
                float inner = bounds.width() / 2f * 0.62f;
                innerShader = new LinearGradient(0, bounds.centerY() - inner, 0, bounds.centerY() + inner, colors.top(), colors.bottom(), Shader.TileMode.CLAMP);
                innerShaderColor = customColor;
            }
            return innerShader;
        }

        void draw(Canvas canvas) {
            float radius = bounds.width() / 2f;
            float cx = bounds.centerX();
            float cy = bounds.centerY();
            int textColor = Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider);

            canvas.save();
            float scale = bounce.getScale(.05f);
            canvas.scale(scale, scale, cx, cy);

            canvas.save();
            circlePath.rewind();
            circlePath.addCircle(cx, cy, radius, Path.Direction.CW);
            canvas.clipPath(circlePath);
            fillPaint.setShader(null);
            fillPaint.setColor(Color.WHITE);
            switch (type) {
                case TYPE_KEEP -> fillPaint.setColor(Theme.getColor(Theme.key_avatar_backgroundGray, resourcesProvider));
                case TYPE_NEUTRAL -> fillPaint.setColor(Theme.multAlpha(textColor, 0.15f));
                default -> fillPaint.setShader(shader);
            }
            canvas.drawPaint(fillPaint);
            String customColor = PillColorGrid.this.selected;
            if (type == TYPE_CUSTOM && customColor != null && PillColors.isCustom(customColor)) {
                fillPaint.setShader(getInnerShader(customColor));
                canvas.drawCircle(cx, cy, radius * 0.62f, fillPaint);
            }
            canvas.restore();

            if (selectT > 0) {
                ringPaint.setStrokeWidth(dpf2(2));
                ringPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider));
                canvas.drawCircle(cx, cy, radius + ringPaint.getStrokeWidth() * lerp(.5f, -2f, selectT), ringPaint);
            }

            if (type == TYPE_KEEP) {
                closePaint.setColor(Color.WHITE);
                closePaint.setStrokeWidth(dp(2));
                float r = lerp(dp(5), dp(4), selectT);
                closePath.rewind();
                closePath.moveTo(cx - r, cy - r);
                closePath.lineTo(cx + r, cy + r);
                closePath.moveTo(cx + r, cy - r);
                closePath.lineTo(cx - r, cy + r);
                canvas.drawPath(closePath, closePaint);
            }
            canvas.restore();
        }
    }
}
