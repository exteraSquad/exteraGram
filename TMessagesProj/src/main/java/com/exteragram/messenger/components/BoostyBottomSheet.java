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
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.LinearSmoothScroller;
import androidx.recyclerview.widget.LinearSnapHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.exteragram.messenger.api.dto.BoostySubscriberDTO;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.ColoredImageSpan;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.LinkSpanDrawable;
import org.telegram.ui.Components.Premium.StarParticlesView;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;

import java.util.ArrayList;
import java.util.List;

public class BoostyBottomSheet extends BottomSheet {

    public ButtonWithCounterView buttonView;
    public TextView titleView;
    public LinkSpanDrawable.LinksTextView descriptionView;
    public FrameLayout topView;
    private final RecyclerListView listView;

    private int contentHeight;
    private final int ITEM_HEIGHT = dp(48);

    private static final int PRIMARY_COLOR = 0xFFF3542E;
    private static final int SECONDARY_COLOR = 0xFFD94B29;
    private static final int TOP_RADIUS = 24;

    private final StarParticlesView.Drawable starDrawable;
    private final LinearSnapHelper snapHelper = new LinearSnapHelper();

    private static final int AUTO_SCROLL_DELAY = 1500;
    private static final int WRAP_SCROLL_DELAY = 3000;
    private static final int RESUME_SCROLL_DELAY = 3000;
    private static final float NORMAL_SCROLL_SPEED_MULTIPLIER = 12.5f;
    private static final float WRAP_SCROLL_SPEED_MULTIPLIER = 0.6f;

    private boolean isUserScrolling;
    private int currentAutoScrollPosition;

    private Runnable resumeScrollRunnable;

    private final Runnable autoScrollRunnable = new Runnable() {
        @Override
        public void run() {
            if (listView == null || isUserScrolling || listView.getAdapter() == null || listView.getAdapter().getItemCount() <= 1) {
                return;
            }
            LinearLayoutManager layoutManager = (LinearLayoutManager) listView.getLayoutManager();
            if (layoutManager == null) return;

            currentAutoScrollPosition++;
            boolean wrap = false;
            if (currentAutoScrollPosition >= listView.getAdapter().getItemCount()) {
                currentAutoScrollPosition = 0;
                wrap = true;
            }

            final boolean finalWrap = wrap;
            LinearSmoothScroller smoothScroller = new LinearSmoothScroller(listView.getContext()) {
                @Override
                protected float calculateSpeedPerPixel(android.util.DisplayMetrics displayMetrics) {
                    float multiplier = finalWrap ? WRAP_SCROLL_SPEED_MULTIPLIER : NORMAL_SCROLL_SPEED_MULTIPLIER;
                    return super.calculateSpeedPerPixel(displayMetrics) * multiplier;
                }

                @Override
                public int calculateDtToFit(int viewStart, int viewEnd, int boxStart, int boxEnd, int snapPreference) {
                    return (boxStart + (boxEnd - boxStart) / 2) - (viewStart + (viewEnd - viewStart) / 2);
                }
            };
            smoothScroller.setTargetPosition(currentAutoScrollPosition);
            layoutManager.startSmoothScroll(smoothScroller);

            listView.removeCallbacks(this);
            listView.postDelayed(this, finalWrap ? WRAP_SCROLL_DELAY : AUTO_SCROLL_DELAY);
        }
    };

    public BoostyBottomSheet(Context context, List<BoostySubscriberDTO> subscriberList) {
        super(context, false);
        final List<BoostySubscriberDTO> subscribers = new ArrayList<>(subscriberList);

        int bgColor = Theme.isCurrentThemeDark() ? 0xFF181818 : 0xFFF5F5F5;
        setBackgroundColor(bgColor);
        fixNavigationBar(bgColor);

        setApplyTopPadding(false);
        setApplyBottomPadding(false);
        useBackgroundTopPadding = false;

        Paint topViewBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        topViewBgPaint.setColor(PRIMARY_COLOR);

        starDrawable = new StarParticlesView.Drawable(300);
        starDrawable.color = 0x60FFFFFF;
        starDrawable.size1 = 8;
        starDrawable.size2 = 6;
        starDrawable.size3 = 4;
        starDrawable.k1 = starDrawable.k2 = starDrawable.k3 = 0.98f;
        starDrawable.useRotate = true;
        starDrawable.speedScale = 2.0f;
        starDrawable.checkBounds = true;
        starDrawable.checkTime = true;
        starDrawable.useBlur = true;
        starDrawable.roundEffect = false;
        starDrawable.init();

        FrameLayout content = new FrameLayout(context) {
            private final Path path = new Path();
            private final RectF rectF = new RectF();

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                if (AndroidUtilities.isTablet()) {
                    contentHeight = (int) (MeasureSpec.getSize(heightMeasureSpec) * 0.45f);
                } else if (isPortrait) {
                    contentHeight = (int) (MeasureSpec.getSize(widthMeasureSpec) * 0.8f);
                } else {
                    contentHeight = (int) (MeasureSpec.getSize(heightMeasureSpec) * 0.65f);
                }
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(contentHeight + dp(2), MeasureSpec.EXACTLY));

                starDrawable.rect.set(0, dp(2), getMeasuredWidth(), getMeasuredHeight());
                starDrawable.rect2.set(-dp(15), -dp(15), getMeasuredWidth() + dp(15), getMeasuredHeight() + dp(15));
                starDrawable.resetPositions();
            }

            @Override
            protected void onSizeChanged(int w, int h, int oldw, int oldh) {
                super.onSizeChanged(w, h, oldw, oldh);
                final float radius = dp(TOP_RADIUS) - 1;
                rectF.set(0, dp(2), w, h + radius);
                path.rewind();
                path.addRoundRect(rectF, radius, radius, Path.Direction.CW);
                topViewBgPaint.setShader(new RadialGradient(
                        w / 2f, (h + dp(2)) / 2f,
                        Math.max(w, h + dp(16)) / 2f,
                        PRIMARY_COLOR, SECONDARY_COLOR,
                        Shader.TileMode.CLAMP
                ));
            }

            @Override
            protected void dispatchDraw(Canvas canvas) {
                final float radius = dp(TOP_RADIUS) - 1;
                canvas.save();
                canvas.clipRect(0, dp(2), getMeasuredWidth(), getMeasuredHeight());
                canvas.drawRoundRect(rectF, radius, radius, topViewBgPaint);
                canvas.restore();

                canvas.save();
                canvas.clipPath(path);
                starDrawable.onDraw(canvas);
                super.dispatchDraw(canvas);
                canvas.restore();
                invalidate();
            }
        };

        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.VERTICAL);

        topView = new FrameLayout(context);
        content.addView(topView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP, 0, 2, 0, 0));

        listView = new RecyclerListView(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int height = MeasureSpec.getSize(heightMeasureSpec);
                if (height > 0) {
                    int padding = (height - ITEM_HEIGHT) / 2;
                    if (getPaddingTop() != padding) {
                        setPadding(0, padding, 0, padding);
                    }
                }
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            }

            @Override
            public boolean onInterceptTouchEvent(MotionEvent e) {
                if (e.getAction() == MotionEvent.ACTION_DOWN) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
                return super.onInterceptTouchEvent(e);
            }

            @SuppressLint("ClickableViewAccessibility")
            @Override
            public boolean onTouchEvent(MotionEvent e) {
                if (e.getAction() == MotionEvent.ACTION_DOWN) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
                boolean handled = super.onTouchEvent(e);
                if (e.getAction() == MotionEvent.ACTION_DOWN || e.getAction() == MotionEvent.ACTION_MOVE) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
                return handled || e.getAction() == MotionEvent.ACTION_DOWN;
            }
        };
        listView.setLayoutManager(new LinearLayoutManager(context));
        listView.setAdapter(new RecyclerView.Adapter<ViewHolder>() {
            @NonNull
            @Override
            public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                TextView textView = new TextView(context);
                textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 22);
                textView.setGravity(Gravity.CENTER);
                textView.setTextColor(0xFFFFFFFF);
                textView.setTypeface(AndroidUtilities.bold());
                textView.setMaxLines(2);
                textView.setEllipsize(TextUtils.TruncateAt.END);
                textView.setPadding(dp(16), 0, dp(16), 0);
                textView.setMinHeight(ITEM_HEIGHT);
                textView.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                return new ViewHolder(textView);
            }

            @Override
            public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
                holder.textView.setText(subscribers.get(position).getName());
            }

            @Override
            public int getItemCount() {
                return subscribers.size();
            }
        });
        listView.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                updateScales();
            }

            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    isUserScrolling = true;
                    listView.removeCallbacks(autoScrollRunnable);
                    if (resumeScrollRunnable != null) {
                        listView.removeCallbacks(resumeScrollRunnable);
                    }
                } else if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    if (isUserScrolling) {
                        LinearLayoutManager layoutManager = (LinearLayoutManager) listView.getLayoutManager();
                        if (layoutManager != null) {
                            View snapView = snapHelper.findSnapView(layoutManager);
                            if (snapView != null) {
                                currentAutoScrollPosition = layoutManager.getPosition(snapView);
                            } else {
                                currentAutoScrollPosition = layoutManager.findFirstVisibleItemPosition();
                            }
                        }

                        resumeScrollRunnable = () -> {
                            isUserScrolling = false;
                            listView.postDelayed(autoScrollRunnable, AUTO_SCROLL_DELAY);
                        };
                        listView.postDelayed(resumeScrollRunnable, RESUME_SCROLL_DELAY);
                    }
                }
            }
        });
        listView.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> updateScales());
        listView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        listView.setVerticalScrollBarEnabled(false);
        listView.setClipToPadding(false);
        listView.scrollToPosition(0);

        listView.postDelayed(autoScrollRunnable, AUTO_SCROLL_DELAY);

        snapHelper.attachToRecyclerView(listView);

        topView.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        linearLayout.addView(content, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 0));

        var textColor = Theme.isCurrentThemeDark() ? Color.WHITE : Color.BLACK;
        titleView = new TextView(context);
        titleView.setGravity(Gravity.CENTER_HORIZONTAL);
        titleView.setTextColor(textColor);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setText(LocaleController.formatPluralString("BoostyPeopleCount", subscribers.size()));
        linearLayout.addView(titleView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 20, 24, 20, 0));

        descriptionView = new LinkSpanDrawable.LinksTextView(context);
        descriptionView.setGravity(Gravity.CENTER_HORIZONTAL);
        descriptionView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        descriptionView.setTypeface(AndroidUtilities.regular());
        descriptionView.setTextColor(textColor);
        var infoSpannableStringBuilder = new SpannableStringBuilder(LocaleController.getString(R.string.BoostyInfo))
                .append("\n")
                .append(LocaleController.getString(R.string.BoostyInfo2));
        descriptionView.setText(AndroidUtilities.replaceTags(infoSpannableStringBuilder));
        linearLayout.addView(descriptionView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 20, 6, 20, 0));

        buttonView = new ButtonWithCounterView(context, resourcesProvider);
        buttonView.setRound();

        SpannableStringBuilder buttonText = new SpannableStringBuilder(LocaleController.getString(R.string.Open)).append("  Boosty");
        var iconSpan = new ColoredImageSpan(R.drawable.boosty_icon, ColoredImageSpan.ALIGN_CENTER);
        iconSpan.setSize(dp(20));
        buttonText.setSpan(iconSpan, buttonText.length() - 7, buttonText.length() - 6, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        buttonView.setText(buttonText, false);
        buttonView.setTextColor(0xFFFFFFFF);
        buttonView.setOnClickListener(v -> {
            dismiss();
            onButtonClick();
        });

        int rippleColor = ColorUtils.setAlphaComponent(ColorUtils.blendARGB(PRIMARY_COLOR, 0, 0.2f), 0x33);
        buttonView.setColor(PRIMARY_COLOR, rippleColor);

        linearLayout.addView(buttonView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 0, 14, 24, 14, 14));

        ScrollView scrollView = new ScrollView(context);
        scrollView.addView(linearLayout);
        setCustomView(scrollView);
    }

    @Override
    protected boolean isTouchOutside(float x, float y) {
        if (y >= containerView.getTop() - backgroundPaddingTop && y <= containerView.getBottom()) {
            return false;
        }
        return super.isTouchOutside(x, y);
    }

    private void updateScales() {
        if (listView == null) return;
        int centerY = listView.getHeight() / 2;
        for (int i = 0; i < listView.getChildCount(); i++) {
            View child = listView.getChildAt(i);
            int childCenterY = (child.getTop() + child.getBottom()) / 2;
            float distance = Math.abs(centerY - childCenterY);
            float ratio = Math.min(distance / (listView.getHeight() / 2f), 1.0f);
            float scale = 1.0f - ratio * 0.3f;
            child.setScaleX(scale);
            child.setScaleY(scale);
            child.setAlpha(0.3f + (1.0f - ratio) * 0.7f);
        }
    }

    private static class ViewHolder extends RecyclerView.ViewHolder {
        TextView textView;
        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            textView = (TextView) itemView;
        }
    }

    @Override
    public void dismiss() {
        super.dismiss();
        if (listView != null) {
            listView.removeCallbacks(autoScrollRunnable);
            if (resumeScrollRunnable != null) {
                listView.removeCallbacks(resumeScrollRunnable);
            }
        }
    }

    protected void onButtonClick() {
    }
}