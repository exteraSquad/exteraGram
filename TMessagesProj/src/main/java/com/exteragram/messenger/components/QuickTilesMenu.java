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

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Shader;
import android.os.IBinder;
import android.os.VibrationEffect;
import android.util.TypedValue;
import android.view.Choreographer;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;

import com.exteragram.messenger.utils.system.VibratorUtils;
import com.exteragram.messenger.utils.ui.SliderHapticsHelper;
import com.exteragram.messenger.utils.ui.material.Material3Tokens;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.quickforward.BlurVisibilityDrawable;

import java.util.ArrayList;
import java.util.function.BooleanSupplier;

@SuppressLint("ViewConstructor")
public abstract class QuickTilesMenu extends FrameLayout implements Choreographer.FrameCallback {

    protected static final int TILE_SIZE = 56;
    private static final int TILE_GAP = 10;
    protected static final int TILE_RADIUS = 16;
    private static final int EDGE_MARGIN = 12;
    private static final float STAGGER = 0.04f;

    private static final float EXIT_STIFFNESS = 900f;
    private static final float PATH_LIFT = 20;
    private static final float START_SCALE = 0.3f;
    private static final float START_ROTATION = 16f;


    private static final float FOCUS_SCALE = 0.1f;
    private static final float FOCUS_LIFT = 8;
    private static final float FOCUS_ELEVATION = 6;
    private static final int HIT_SLOP = 6;

    private static final float BLUR_VISIBLE_AT = 0.85f;
    private static final int BLUR_RADIUS = 10;

    private static final int DIM_EXTENT = 120;
    private static final int DARK_DIM_ALPHA = 0x2E;
    private static final int LIGHT_FOG_ALPHA = 0x4D;
    private static final int ANCHOR_RIPPLE_RADIUS = 20;
    private static final float OPEN_HAPTIC_SCALE = 1f;
    private static final float HOVER_HAPTIC_SCALE = 0.5f;
    private static final float DIM_FADE_RATE = 9f;
    private static final float BLUR_BITMAP_SCALE = 4f;

    private static final int OPEN_FULL_DISTANCE = 28;
    private static final float OPEN_FULL_LIFT = 8;
    private static final float OPEN_FULL_SCALE = 0.08f;
    private static final float OPEN_FULL_ALPHA = 0.5f;
    private static final int HINT_GAP = 12;
    private static final int HINT_DROP = 10;
    private static final float HINT_START_SCALE = 0.85f;
    private static final int HINT_RADIUS = 18;
    private static final int OPEN_FULL_CANCEL_MARGIN = 72;

    protected final ChatActivity chat;
    private final View anchor;
    private final ViewGroup container;
    private final boolean animated;
    protected final int slots;
    private final ArrayList<Tile> tiles = new ArrayList<>();
    private final float[] anchorPosition = new float[2];
    private final float[] menuPosition = new float[2];
    private final float[] tilePosition = new float[2];
    private final Paint dimPaint = new Paint();
    private final Paint anchorBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean contentReady;
    private boolean shown;
    private float dimProgress;
    private float dimTop;
    private float dimSolidTop;
    private int dimShaderHeight;
    private int dimColor;
    private float dimFade;
    private float anchorFade;
    protected final float[] placePosition = new float[2];
    private final int[] screenLocation = new int[2];

    private Runnable onDismiss;
    private View focusWindow;
    private boolean anchorHaptics;
    private final int[] containerLocation = new int[2];
    protected boolean dismissing;
    private boolean finished;
    protected boolean viewerOpen;
    private boolean viewerClosing;
    private Tile selected;
    private boolean capturing = true;
    private boolean captureStarted;
    private boolean dragged;
    private float captureStartX;
    private float captureStartY;
    private Tile hovered;
    private boolean armed;
    private float armProgress;
    private BlurTile hint;
    private float hintProgress;
    private boolean rowLaidOut;
    private boolean rowBelow;
    private float rowTop;
    private boolean frameScheduled;
    private long lastFrameNanos;

    private static final class Tile {
        final BlurTile view;
        final View content;
        final int slot;
        float progress;
        float velocity;
        float delay;
        float focus;
        float space = 1f;
        float spaceVelocity;
        float baseX;
        float baseY;

        Tile(BlurTile view, View content, int slot, float delay) {
            this.view = view;
            this.content = content;
            this.slot = slot;
            this.delay = delay;
        }
    }

    protected QuickTilesMenu(ChatActivity chat, View anchor, ViewGroup container, int maxTiles) {
        super(anchor.getContext());
        this.chat = chat;
        this.anchor = anchor;
        this.container = container;
        this.animated = SharedConfig.animationsEnabled();
        setClipChildren(false);
        setWillNotDraw(false);
        int width = anchor.getRootView().getWidth();
        int fit = (width - 2 * dp(EDGE_MARGIN) + dp(TILE_GAP)) / (dp(TILE_SIZE) + dp(TILE_GAP));
        slots = Math.max(1, Math.min(maxTiles, fit));
    }

    protected static ViewGroup getContainer(ChatActivity chat) {
        return chat.getParentLayout() == null ? null : chat.getParentLayout().getOverlayContainerView();
    }

    protected void start() {
        anchorHaptics = anchor.isHapticFeedbackEnabled();
        anchor.setHapticFeedbackEnabled(false);
        load();
    }

    @SuppressLint("ClickableViewAccessibility")
    public static void attachLongPressRamp(View view, BooleanSupplier enabled) {
        int slop = ViewConfiguration.get(view.getContext()).getScaledTouchSlop();
        int tapTimeout = ViewConfiguration.getTapTimeout();
        Runnable start = () -> {
            if (enabled.getAsBoolean()) {
                SliderHapticsHelper.startLongPressRamp(ViewConfiguration.getLongPressTimeout() - tapTimeout);
            }
        };
        view.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    v.removeCallbacks(start);
                    v.postDelayed(start, tapTimeout);
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (event.getX() < -slop || event.getY() < -slop || event.getX() > v.getWidth() + slop || event.getY() > v.getHeight() + slop) {
                        v.removeCallbacks(start);
                        SliderHapticsHelper.cancelLongPressRamp();
                    }
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.removeCallbacks(start);
                    SliderHapticsHelper.cancelLongPressRamp();
                    break;
            }
            return false;
        });
    }

    private static void haptic(View view, int primitive, float scale, int fallback) {
        if (!SliderHapticsHelper.vibratePrimitive(primitive, scale)) {
            view.performHapticFeedback(VibratorUtils.getType(fallback), HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);
        }
    }

    protected void onContentReady() {
        if (finished || dismissing) {
            return;
        }
        contentReady = true;
        scheduleFrame();
    }

    protected boolean drawsAnchorScrim() {
        return true;
    }

    protected int getAnchorBackgroundColor() {
        Theme.ResourcesProvider resourcesProvider = chat.getResourceProvider();
        int panel = Theme.getColor(Theme.key_chat_messagePanelBackground, resourcesProvider);
        return ColorUtils.compositeColors(Theme.getColor(Theme.key_listSelector, resourcesProvider), panel);
    }

    protected void drawAnchorBackground(Canvas canvas, int width, int height, Paint paint) {
        canvas.drawCircle(width / 2f, height / 2f, dp(ANCHOR_RIPPLE_RADIUS), paint);
    }

    private void showWhenReady() {
        if (!anchor.isAttachedToWindow() || !anchor.isShown() || chat.isPaused()) {
            finish(true);
            return;
        }
        if (!contentReady) {
            return;
        }
        shown = true;
        lastFrameNanos = 0;
        hint = createHint();
        addView(hint, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP));
        container.addView(this, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        takeFocus();
        haptic(anchor, VibrationEffect.Composition.PRIMITIVE_CLICK, OPEN_HAPTIC_SCALE, HapticFeedbackConstants.LONG_PRESS);
        scheduleFrame();
    }

    protected abstract void load();

    protected abstract int getOpenFullIcon();

    protected abstract CharSequence getOpenFullText();

    protected abstract void openFull();

    private BlurTile createHint() {
        Context context = getContext();
        Theme.ResourcesProvider resourcesProvider = chat.getResourceProvider();
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        layout.setPadding(dp(12), dp(8), dp(14), dp(8));
        layout.setBackground(Theme.createRoundRectDrawable(dp(HINT_RADIUS), Theme.getColor(Theme.key_actionBarDefaultSubmenuBackground, resourcesProvider)));
        ImageView icon = new ImageView(context);
        icon.setImageResource(getOpenFullIcon());
        icon.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_actionBarDefaultSubmenuItemIcon, resourcesProvider), PorterDuff.Mode.SRC_IN));
        layout.addView(icon, LayoutHelper.createLinear(20, 20));
        TextView text = new TextView(context);
        text.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        text.setTypeface(AndroidUtilities.bold());
        text.setTextColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuItem, resourcesProvider));
        text.setSingleLine();
        text.setText(getOpenFullText());
        layout.addView(text, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 8, 0, 0, 0));
        BlurTile tile = new BlurTile(context, null);
        tile.addView(layout, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
        tile.card = true;
        tile.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), Math.min(dp(HINT_RADIUS), view.getHeight() / 2f));
                float visibility = ((BlurTile) view).visibility;
                outline.setAlpha(visibility * visibility);
            }
        });
        tile.setElevation(dp(4));
        tile.setBlurVisibility(0f);
        tile.setVisibility(INVISIBLE);
        tile.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        return tile;
    }

    private void openFullPanel() {
        if (dismissing) {
            return;
        }
        dismiss(true);
        openFull();
    }

    private void takeFocus() {
        IBinder token = anchor.getWindowToken();
        if (token == null || focusWindow != null) {
            return;
        }
        View view = new View(getContext()) {
            @Override
            public boolean dispatchKeyEvent(KeyEvent event) {
                if (event.getKeyCode() != KeyEvent.KEYCODE_BACK && event.isSystem()) {
                    return super.dispatchKeyEvent(event);
                }
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    dismiss(true);
                }
                return true;
            }

            @SuppressLint("ClickableViewAccessibility")
            @Override
            public boolean dispatchTouchEvent(MotionEvent event) {
                container.getLocationOnScreen(containerLocation);
                MotionEvent forwarded = MotionEvent.obtain(event);
                forwarded.offsetLocation(event.getRawX() - event.getX() - containerLocation[0], event.getRawY() - event.getY() - containerLocation[1]);
                float x = forwarded.getX(), y = forwarded.getY();
                if (forwarded.getActionMasked() == MotionEvent.ACTION_DOWN && (x < 0 || y < 0 || x >= container.getWidth() || y >= container.getHeight())) {
                    dismiss(true);
                } else {
                    container.dispatchTouchEvent(forwarded);
                }
                forwarded.recycle();
                return true;
            }
        };
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(1, 1, WindowManager.LayoutParams.TYPE_APPLICATION_PANEL,
                WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM,
                PixelFormat.TRANSPARENT);
        params.gravity = Gravity.TOP | Gravity.LEFT;
        params.token = token;
        params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED;
        WindowManager windowManager = (WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE);
        try {
            windowManager.addView(view, params);
            focusWindow = view;
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private void releaseFocus() {
        View view = focusWindow;
        if (view == null) {
            return;
        }
        focusWindow = null;
        try {
            ((WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE)).removeViewImmediate(view);
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    protected int getDimColor() {
        int background = Theme.getColor(Theme.key_windowBackgroundWhite, chat.getResourceProvider());
        if (AndroidUtilities.computePerceivedBrightness(background) > 0.705f) {
            return ColorUtils.setAlphaComponent(background, LIGHT_FOG_ALPHA);
        }
        return ColorUtils.setAlphaComponent(Color.BLACK, DARK_DIM_ALPHA);
    }

    protected void onFinished() {
    }

    public void setOnDismiss(Runnable onDismiss) {
        this.onDismiss = onDismiss;
    }

    public boolean isDismissing() {
        return dismissing;
    }

    public void dismiss(boolean animate) {
        if (finished) {
            return;
        }
        if (!animate || !animated || !isAttachedToWindow()) {
            finish(true);
            return;
        }
        if (dismissing) {
            return;
        }
        dismissing = true;
        releaseFocus();
        for (int i = 0; i < tiles.size(); i++) {
            Tile tile = tiles.get(i);
            tile.delay = (tiles.size() - 1 - i) * STAGGER * 0.5f;
            tile.content.setPressed(false);
        }
        hovered = null;
        scheduleFrame();
    }

    private void finish(boolean remove) {
        if (finished) {
            return;
        }
        finished = true;
        dismissing = true;
        anchor.setHapticFeedbackEnabled(anchorHaptics);
        Choreographer.getInstance().removeFrameCallback(this);
        frameScheduled = false;
        for (int i = 0; i < tiles.size(); i++) {
            tiles.get(i).view.recycle();
        }
        if (hint != null) {
            hint.recycle();
        }
        releaseFocus();
        if (remove) {
            container.removeView(this);
        }
        onFinished();
        if (onDismiss != null) {
            Runnable callback = onDismiss;
            onDismiss = null;
            callback.run();
        }
    }

    protected void addTile(View content, int slot, boolean card) {
        BlurTile view = new BlurTile(getContext(), content instanceof BackupImageView image ? image : null);
        view.addView(content, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        view.card = card;
        view.setOutlineProvider(card ? new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(TILE_RADIUS));
                float visibility = ((BlurTile) view).visibility;
                outline.setAlpha(visibility * visibility);
            }
        } : null);
        view.setAlpha(0f);
        addView(view, LayoutHelper.createFrame(TILE_SIZE, TILE_SIZE, Gravity.LEFT | Gravity.TOP));
        Tile tile = new Tile(view, content, slot, slot * STAGGER);
        if (!animated) {
            tile.progress = 1f;
            tile.delay = 0f;
        }
        tiles.add(tile);
    }

    protected void invalidateTileBlur(View content) {
        for (int i = 0; i < tiles.size(); i++) {
            if (tiles.get(i).content == content) {
                tiles.get(i).view.invalidateBlur();
            }
        }
    }

    protected void dismissWithout(View content) {
        for (int i = 0; i < tiles.size(); i++) {
            if (tiles.get(i).content == content) {
                selected = tiles.get(i);
            }
        }
        dismiss(true);
    }

    protected void onViewerOpened(View content) {
        releaseFocus();
        viewerOpen = true;
        viewerClosing = false;
        hovered = null;
        for (int i = 0; i < tiles.size(); i++) {
            if (tiles.get(i).content == content) {
                selected = tiles.get(i);
            }
        }
    }

    protected void onViewerPreClose() {
        viewerClosing = true;
    }

    protected void onViewerClosed() {
        if (!dismissing) {
            takeFocus();
        }
        viewerOpen = false;
        viewerClosing = false;
        selected = null;
    }

    protected void scheduleFrame() {
        if (!frameScheduled && !finished) {
            frameScheduled = true;
            Choreographer.getInstance().postFrameCallback(this);
        }
    }

    @Override
    public void doFrame(long frameTimeNanos) {
        frameScheduled = false;
        if (finished) {
            return;
        }
        if (!shown) {
            showWhenReady();
            return;
        }
        if (!anchor.isAttachedToWindow() || !viewerOpen && !anchor.isShown()) {
            finish(true);
            return;
        }
        if (getWidth() == 0) {
            scheduleFrame();
            return;
        }
        float dt =lastFrameNanos == 0 ? 1f / 60f : Utilities.clamp((frameTimeNanos - lastFrameNanos) / 1e9f, 0.05f, 0f);
        lastFrameNanos = frameTimeNanos;
        boolean settled = step(dt);
        layoutTiles();
        if (dismissing && settled) {
            finish(true);
        } else {
            scheduleFrame();
        }
    }

    private boolean step(float dt) {
        boolean settled = true;
        float dimTarget = dismissing || getDimColor() == 0 ? 0f : 1f;
        float anchorTarget = dismissing || !drawsAnchorScrim() ? 0f : 1f;
        if (!animated) {
            dimFade = dimTarget;
            anchorFade = anchorTarget;
        } else {
            float fadeEase = 1f - (float) Math.exp(-dt * DIM_FADE_RATE);
            dimFade += (dimTarget - dimFade) * fadeEase;
            anchorFade += (anchorTarget - anchorFade) * fadeEase;
            if (Math.abs(dimFade - dimTarget) < 0.01f && Math.abs(anchorFade - anchorTarget) < 0.01f) {
                dimFade = dimTarget;
                anchorFade = anchorTarget;
            } else {
                settled = false;
            }
        }
        float target = dismissing ? 0f : 1f;
        float stiffness = dismissing ? EXIT_STIFFNESS : Material3Tokens.DEFAULT_SPATIAL_STIFFNESS;
        float damping = dismissing ? 1f : Material3Tokens.DEFAULT_SPATIAL_DAMPING;
        float friction = 2f * damping * (float) Math.sqrt(stiffness);
        float focusEase = 1f - (float) Math.exp(-dt * 18f);
        float spaceFriction = 2f * Material3Tokens.STANDARD_SPATIAL_DAMPING * (float) Math.sqrt(Material3Tokens.STANDARD_SPATIAL_STIFFNESS);
        float armTarget = armed ? 1f : 0f;
        float hintTarget = armed && !dismissing ? 1f : 0f;
        armProgress = animated ? armProgress + (armTarget - armProgress) * focusEase : armTarget;
        hintProgress = animated ? hintProgress + (hintTarget - hintProgress) * focusEase : hintTarget;
        if (Math.abs(armProgress - armTarget) < Material3Tokens.MIN_VISIBLE_CHANGE) {
            armProgress = armTarget;
        } else {
            settled = false;
        }
        if (Math.abs(hintProgress - hintTarget) < Material3Tokens.MIN_VISIBLE_CHANGE) {
            hintProgress = hintTarget;
        } else {
            settled = false;
        }
        for (int i = 0; i < tiles.size(); i++) {
            Tile tile = tiles.get(i);
            float spaceTarget = tile == selected && !viewerClosing ? 0f : 1f;
            if (!animated) {
                tile.space = spaceTarget;
            } else if (tile.space != spaceTarget || tile.spaceVelocity != 0f) {
                int spaceSteps = (int) Math.ceil(dt / 0.004f);
                float h = dt / spaceSteps;
                for (int s = 0; s < spaceSteps; s++) {
                    float acceleration = -Material3Tokens.STANDARD_SPATIAL_STIFFNESS * (tile.space - spaceTarget) - spaceFriction * tile.spaceVelocity;
                    tile.spaceVelocity += acceleration * h;
                    tile.space += tile.spaceVelocity * h;
                }
                if (Math.abs(tile.space - spaceTarget) < Material3Tokens.MIN_VISIBLE_CHANGE && Math.abs(tile.spaceVelocity) < 0.05f) {
                    tile.space = spaceTarget;
                    tile.spaceVelocity = 0f;
                }
            }
            boolean focused = !dismissing && (tile == hovered || tile.content.isPressed());
            tile.focus = animated ? tile.focus + ((focused ? 1f : 0f) - tile.focus) * focusEase : (focused ? 1f : 0f);
            if (Math.abs(tile.focus - (focused ? 1f : 0f)) > Material3Tokens.MIN_VISIBLE_CHANGE) {
                settled = false;
            }
            if (!animated) {
                tile.progress = target;
                continue;
            }
            if (tile.delay > 0f) {
                tile.delay -= dt;
                settled = false;
                continue;
            }
            int substeps = (int) Math.ceil(dt / 0.004f);
            float h = dt / substeps;
            for (int s = 0; s < substeps; s++) {
                float acceleration = -stiffness * (tile.progress - target) - friction * tile.velocity;
                tile.velocity += acceleration * h;
                tile.progress += tile.velocity * h;
            }
            if (Math.abs(tile.progress - target) < Material3Tokens.MIN_VISIBLE_CHANGE && Math.abs(tile.velocity) < 0.05f) {
                tile.progress = target;
                tile.velocity = 0f;
            } else {
                settled = false;
            }
        }
        return settled;
    }

    private void layoutTiles() {
        int tileSize = dp(TILE_SIZE);
        int step = tileSize + dp(TILE_GAP);
        getUnscaledPosition(anchor, anchorPosition);
        getUnscaledPosition(this, menuPosition);
        float anchorX = anchorPosition[0] - menuPosition[0] + anchor.getWidth() / 2f;
        float anchorTop = anchorPosition[1] - menuPosition[1];
        float anchorY = anchorTop + anchor.getHeight() / 2f;
        int direction = anchorX > getWidth() / 2f ? -1 : 1;

        float firstX = anchorX - tileSize / 2f;
        float lastX = firstX + direction * (slots - 1) * step;
        float minX = Math.min(firstX, lastX);
        float maxX = Math.max(firstX, lastX) + tileSize;
        float shift = 0f;
        if (minX < dp(EDGE_MARGIN)) {
            shift = dp(EDGE_MARGIN) - minX;
        } else if (maxX > getWidth() - dp(EDGE_MARGIN)) {
            shift = getWidth() - dp(EDGE_MARGIN) - maxX;
        }
        float rowY = anchorTop - dp(12) - tileSize;
        rowBelow = rowY < dp(8);
        if (rowBelow) {
            rowY = anchorTop + anchor.getHeight() + dp(12);
        }
        rowTop = rowY;
        rowLaidOut = true;
        float rowLeft = Float.MAX_VALUE;
        float rowRight = -Float.MAX_VALUE;

        View fragmentView = chat.getFragmentView();
        float fragmentScale = fragmentView == null ? 1f : fragmentView.getScaleX();
        if (fragmentView != null && fragmentScale != 1f) {
            getUnscaledPosition(fragmentView, tilePosition);
            setPivotX(tilePosition[0] - menuPosition[0] + fragmentView.getPivotX());
            setPivotY(tilePosition[1] - menuPosition[1] + fragmentView.getPivotY());
        }
        setScaleX(fragmentScale);
        setScaleY(fragmentScale);

        float startX = anchorX - tileSize / 2f;
        float startY = anchorY - tileSize / 2f;
        for (int i = 0; i < tiles.size(); i++) {
            Tile tile = tiles.get(i);
            float p = tile.progress;
            float offset = 0f;
            for (int j = 0; j < tiles.size(); j++) {
                Tile other = tiles.get(j);
                if (other.slot < tile.slot) {
                    offset += other.space;
                }
            }
            float endX = firstX + direction * offset * step + shift;
            tile.baseX = endX;
            tile.baseY = rowY;
            rowLeft = Math.min(rowLeft, endX);
            rowRight = Math.max(rowRight, endX + tileSize);
            float controlX = startX + (endX - startX) * 0.15f;
            float controlY = rowY - dp(PATH_LIFT);
            float q = 1f - p;
            float x = q * q * startX + 2f * q * p * controlX + p * p * endX;
            float y = q * q * startY + 2f * q * p * controlY + p * p * rowY;

            float rotation = -direction * START_ROTATION * (1f - p);
            y -= dp(FOCUS_LIFT) * tile.focus + dp(OPEN_FULL_LIFT) * armProgress;
            float scale = (START_SCALE + (1f - START_SCALE) * p) * (1f + FOCUS_SCALE * tile.focus) * (1f - OPEN_FULL_SCALE * armProgress);

            BlurTile view = tile.view;
            float visibility = Utilities.clamp(p / BLUR_VISIBLE_AT, 1f, 0f);
            view.setElevation(view.card ? dp(4) + dp(FOCUS_ELEVATION) * tile.focus : 0f);
            view.setBlurVisibility(animated ? visibility : 1f);
            view.setTranslationX(x);
            view.setTranslationY(y);
            view.setRotation(rotation);
            view.setScaleX(scale);
            view.setScaleY(scale);
            view.setAlpha(p > 0f && tile != selected ? 1f - OPEN_FULL_ALPHA * armProgress : 0f);
        }
        if (hint != null) {
            layoutHint(rowLeft <= rowRight ? (rowLeft + rowRight) / 2f : anchorX, rowY);
        }
        updateDim(dimFade, rowY - dp(DIM_EXTENT), rowY + tileSize / 2f);
        if (anchorFade > 0f) {
            invalidate();
        }
    }

    private void layoutHint(float centerX, float rowY) {
        if (hintProgress <= 0f) {
            hint.setBlurVisibility(0f);
            hint.setVisibility(INVISIBLE);
            return;
        }
        hint.setVisibility(VISIBLE);
        int width = hint.getMeasuredWidth();
        int height = hint.getMeasuredHeight();
        float x = Math.max(dp(EDGE_MARGIN), Math.min(centerX - width / 2f, getWidth() - dp(EDGE_MARGIN) - width));
        float y = getHintTop(rowY) + dp(HINT_DROP) * (1f - hintProgress);
        float scale = HINT_START_SCALE + (1f - HINT_START_SCALE) * hintProgress;
        hint.setPivotX(Math.max(0f, Math.min(centerX - x, width)));
        hint.setPivotY(height);
        hint.setTranslationX(x);
        hint.setTranslationY(y);
        hint.setScaleX(scale);
        hint.setScaleY(scale);
        hint.setBlurVisibility(animated ? Utilities.clamp(hintProgress / BLUR_VISIBLE_AT, 1f, 0f) : 1f);
    }

    private float getHintTop(float rowY) {
        return rowY - dp(OPEN_FULL_LIFT) - dp(HINT_GAP) - (hint == null ? 0 : hint.getMeasuredHeight());
    }

    private float toLocalY(float rawY) {
        getLocationOnScreen(screenLocation);
        return rawY - screenLocation[1];
    }

    private boolean isInOpenFullZone(float y) {
        return rowLaidOut && !rowBelow && y < rowTop - dp(OPEN_FULL_DISTANCE) && !isAboveOpenFullZone(y);
    }

    private boolean isAboveOpenFullZone(float y) {
        return y <= getHintTop(rowTop) - dp(OPEN_FULL_CANCEL_MARGIN);
    }

    private boolean isOnAnchor(float x, float y) {
        float left = anchorPosition[0] - menuPosition[0];
        float top = anchorPosition[1] - menuPosition[1];
        return x >= left && x < left + anchor.getWidth() && y >= top && y < top + anchor.getHeight();
    }

    private void updateDim(float progress, float top, float solidTop) {
        if (progress == dimProgress && top == dimTop && solidTop == dimSolidTop) {
            return;
        }
        dimProgress = progress;
        int color = getDimColor();
        if (top != dimTop || solidTop != dimSolidTop || dimShaderHeight != getHeight() || color != dimColor) {
            dimTop = top;
            dimSolidTop = solidTop;
            dimShaderHeight = getHeight();
            dimColor = color;
            dimPaint.setShader(new LinearGradient(0, top, 0, solidTop, ColorUtils.setAlphaComponent(color, 0), color, Shader.TileMode.CLAMP));
        }
        invalidate();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        if (dimProgress > 0f) {
            dimPaint.setAlpha((int) (255 * dimProgress));
            canvas.drawRect(0, dimTop, getWidth(), getHeight(), dimPaint);
        }
        if (anchorFade > 0f || dimProgress > 0f && drawsAnchorScrim()) {
            canvas.save();
            canvas.translate(anchorPosition[0] - menuPosition[0], anchorPosition[1] - menuPosition[1]);
            int backgroundColor = getAnchorBackgroundColor();
            if (backgroundColor != 0) {
                anchorBackgroundPaint.setColor(backgroundColor);
                anchorBackgroundPaint.setAlpha((int) (Color.alpha(backgroundColor) * anchorFade));
                drawAnchorBackground(canvas, anchor.getWidth(), anchor.getHeight(), anchorBackgroundPaint);
            }
            anchor.draw(canvas);
            canvas.restore();
        }
    }

    protected static void getUnscaledPosition(View view, float[] out) {
        float x = 0f;
        float y = 0f;
        View current = view;
        while (true) {
            x += current.getLeft() + current.getTranslationX();
            y += current.getTop() + current.getTranslationY();
            if (!(current.getParent() instanceof View parent)) {
                break;
            }
            x -= parent.getScrollX();
            y -= parent.getScrollY();
            current = parent;
        }
        out[0] = x;
        out[1] = y;
    }

    public void dispatchCapturedTouchEvent(MotionEvent event) {
        if (!capturing || dismissing || viewerOpen) {
            return;
        }
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            capturing = false;
            return;
        }
        if (!captureStarted) {
            captureStarted = true;
            captureStartX = event.getRawX();
            captureStartY = event.getRawY();
        } else if (!dragged) {
            int slop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
            dragged = Math.hypot(event.getRawX() - captureStartX, event.getRawY() - captureStartY) > slop;
        }
        Tile hit = action == MotionEvent.ACTION_CANCEL ? null : findTile(event.getRawX(), event.getRawY());
        if (action == MotionEvent.ACTION_MOVE) {
            float y = toLocalY(event.getRawY());
            boolean arm = dragged && hit == null && isInOpenFullZone(y);
            if (arm != armed) {
                armed = arm;
                if (arm || isAboveOpenFullZone(y)) {
                    haptic(this, VibrationEffect.Composition.PRIMITIVE_TICK, HOVER_HAPTIC_SCALE, HapticFeedbackConstants.CLOCK_TICK);
                }
                scheduleFrame();
            }
            if (hit != hovered) {
                hovered = hit;
                if (hit != null) {
                    haptic(hit.view, VibrationEffect.Composition.PRIMITIVE_TICK, HOVER_HAPTIC_SCALE, HapticFeedbackConstants.CLOCK_TICK);
                }
                scheduleFrame();
            }
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            boolean open = action == MotionEvent.ACTION_UP && armed;
            capturing = false;
            armed = open;
            hovered = null;
            scheduleFrame();
            if (open) {
                openFullPanel();
            } else if (action == MotionEvent.ACTION_UP && hit != null) {
                hit.content.performClick();
            } else if (action == MotionEvent.ACTION_UP && dragged) {
                dismiss(true);
            }
        }
    }

    private Tile findTile(float rawX, float rawY) {
        getLocationOnScreen(screenLocation);
        float x = rawX - screenLocation[0];
        float y = rawY - screenLocation[1];
        float half = dp(TILE_SIZE) / 2f + dp(HIT_SLOP);
        for (int i = tiles.size() - 1; i >= 0; i--) {
            Tile tile = tiles.get(i);
            if (tile.progress < 0.9f) {
                continue;
            }
            float cx = tile.baseX + dp(TILE_SIZE) / 2f;
            float cy = tile.baseY + dp(TILE_SIZE) / 2f;
            if (Math.abs(x - cx) <= half && Math.abs(y - cy) <= half) {
                return tile;
            }
        }
        return null;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (dismissing) {
            return false;
        }
        return super.dispatchTouchEvent(ev);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            if (isOnAnchor(event.getX(), event.getY())) {
                openFullPanel();
            } else {
                dismiss(true);
            }
        }
        return true;
    }

    @Override
    public boolean shouldDelayChildPressedState() {
        return false;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        finish(false);
    }

    private static final class BlurTile extends FrameLayout {
        private final BlurVisibilityDrawable blur = new BlurVisibilityDrawable(this::drawContent);
        private final BackupImageView image;
        boolean card;
        private float visibility = 1f;
        private boolean blurDirty = true;

        BlurTile(Context context, BackupImageView image) {
            super(context);
            this.image = image;
        }

        void setBlurVisibility(float value) {
            if (visibility == value) {
                return;
            }
            visibility = value;
            if (visibility < 1f && blurDirty && getWidth() > 0 && isImageLoaded()) {
                blur.render(getWidth(), getHeight(), dp(BLUR_RADIUS), BLUR_BITMAP_SCALE);
                blurDirty = false;
            }
            invalidateOutline();
            invalidate();
        }

        boolean isImageLoaded() {
            return image == null || image.getImageReceiver().hasImageLoaded() || image.getImageReceiver().getThumb() != null;
        }

        void invalidateBlur() {
            blurDirty = true;
        }

        void recycle() {
            blur.recycle();
        }

        private void drawContent(Canvas canvas, int alpha) {
            if (alpha < 255) {
                canvas.saveLayerAlpha(0, 0, getWidth(), getHeight(), alpha);
            } else {
                canvas.save();
            }
            super.dispatchDraw(canvas);
            canvas.restore();
        }

        @Override
        protected void dispatchDraw(@NonNull Canvas canvas) {
            if (visibility >= 1f) {
                super.dispatchDraw(canvas);
            } else if (visibility > 0f) {
                int alpha = (int) (visibility * 255);
                if (blur.hasBitmap()) {
                    blur.setBounds(0, 0, getWidth(), getHeight());
                    blur.setAlpha(alpha);
                    blur.draw(canvas);
                } else {
                    drawContent(canvas, alpha);
                }
            }
        }
    }
}
