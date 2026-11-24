package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;

import java.util.ArrayList;
import java.util.List;

public class ChatScrimPopupContainerLayout extends LinearLayout {

    private ReactionsContainerLayout reactionsLayout;
    private ActionBarPopupWindow.ActionBarPopupWindowLayout popupWindowLayout;
    private final List<View> bottomViews = new ArrayList<>();
    private int maxHeight;
    private float popupLayoutLeftOffset;
    private float progressToSwipeBack;
    private float bottomViewYOffset;
    private float expandSize;
    private float bottomViewReactionsOffset;
    private float lastReactionsTransitionProgress = 0f;
    private float currentPopupAlpha = 1f;

    public ChatScrimPopupContainerLayout(Context context) {
        super(context);
        setOrientation(LinearLayout.VERTICAL);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        updateBottomViewPosition();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (maxHeight != 0) {
            heightMeasureSpec = MeasureSpec.makeMeasureSpec(maxHeight, MeasureSpec.AT_MOST);
        }

        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        if (popupWindowLayout != null) {
            boolean changed = false;

            if (reactionsLayout != null) {
                reactionsLayout.getLayoutParams().width = LayoutHelper.WRAP_CONTENT;
                ((LayoutParams) reactionsLayout.getLayoutParams()).rightMargin = 0;
            }

            int maxWidth = 0;
            if (reactionsLayout != null) {
                maxWidth = reactionsLayout.getMeasuredWidth();
            }
            if (popupWindowLayout.getSwipeBack() != null && popupWindowLayout.getSwipeBack().getMeasuredWidth() > maxWidth) {
                maxWidth = popupWindowLayout.getSwipeBack().getMeasuredWidth();
            }
            if (popupWindowLayout.getMeasuredWidth() > maxWidth) {
                maxWidth = popupWindowLayout.getMeasuredWidth();
            }
            if (reactionsLayout != null && reactionsLayout.showCustomEmojiReaction()) {
                widthMeasureSpec = MeasureSpec.makeMeasureSpec(maxWidth, MeasureSpec.EXACTLY);
            }

            if (reactionsLayout != null) {
                reactionsLayout.measureHint();

                int reactionsLayoutTotalWidth = reactionsLayout.getTotalWidth();
                View menuContainer = popupWindowLayout.getSwipeBack() != null ? popupWindowLayout.getSwipeBack().getChildAt(0) : popupWindowLayout.getChildAt(0);
                int maxReactionsLayoutWidth = menuContainer.getMeasuredWidth() + dp(16) + dp(16) + dp(36);
                int hintTextWidth = reactionsLayout.getHintTextWidth();

                if (hintTextWidth > maxReactionsLayoutWidth) {
                    maxReactionsLayoutWidth = hintTextWidth;
                } else if (maxReactionsLayoutWidth > maxWidth) {
                    maxReactionsLayoutWidth = maxWidth;
                }

                reactionsLayout.bigCircleOffset = dp(36);

                if (reactionsLayout.showCustomEmojiReaction()) {
                    if (reactionsLayout.getLayoutParams().width != reactionsLayoutTotalWidth) {
                        reactionsLayout.getLayoutParams().width = reactionsLayoutTotalWidth;
                        changed = true;
                    }
                    reactionsLayout.bigCircleOffset = Math.max(reactionsLayoutTotalWidth - menuContainer.getMeasuredWidth() - dp(36), dp(36));
                } else if (reactionsLayoutTotalWidth > maxReactionsLayoutWidth) {
                    int maxFullCount = ((maxReactionsLayoutWidth - dp(16)) / dp(36)) + 1;
                    int newWidth = maxFullCount * dp(36) + dp(8);
                    if (hintTextWidth + dp(24) > newWidth) {
                        newWidth = hintTextWidth + dp(24);
                    }
                    if (newWidth > reactionsLayoutTotalWidth || maxFullCount == reactionsLayout.getItemsCount()) {
                        newWidth = reactionsLayoutTotalWidth;
                    }
                    if (reactionsLayout.getLayoutParams().width != newWidth) {
                        reactionsLayout.getLayoutParams().width = newWidth;
                        changed = true;
                    }
                } else {
                    if (reactionsLayout.getLayoutParams().width != LayoutHelper.WRAP_CONTENT) {
                        reactionsLayout.getLayoutParams().width = LayoutHelper.WRAP_CONTENT;
                        changed = true;
                    }
                }

                int widthDiff = 0;
                if (reactionsLayout.getMeasuredWidth() != maxWidth || !reactionsLayout.showCustomEmojiReaction()) {
                    if (popupWindowLayout.getSwipeBack() != null) {
                        widthDiff = popupWindowLayout.getSwipeBack().getMeasuredWidth() - popupWindowLayout.getSwipeBack().getChildAt(0).getMeasuredWidth();
                    }
                    if (reactionsLayout.getLayoutParams().width != LayoutHelper.WRAP_CONTENT && reactionsLayout.getLayoutParams().width + widthDiff > maxWidth) {
                        widthDiff = maxWidth - reactionsLayout.getLayoutParams().width + dp(8);
                    }
                    if (widthDiff < 0) {
                        widthDiff = 0;
                    }
                    if (((LayoutParams) reactionsLayout.getLayoutParams()).rightMargin != widthDiff) {
                        ((LayoutParams) reactionsLayout.getLayoutParams()).rightMargin = widthDiff;
                        changed = true;
                    }
                    popupLayoutLeftOffset = 0;
                } else {
                    popupLayoutLeftOffset = (maxWidth - menuContainer.getMeasuredWidth()) * 0.25f;
                    reactionsLayout.bigCircleOffset -= (int) popupLayoutLeftOffset;
                    if (reactionsLayout.bigCircleOffset < dp(36)) {
                        popupLayoutLeftOffset = 0;
                        reactionsLayout.bigCircleOffset = dp(36);
                    }
                }
            }

            View menuContainer = popupWindowLayout.getSwipeBack() != null ? popupWindowLayout.getSwipeBack().getChildAt(0) : popupWindowLayout.getChildAt(0);
            int visibleMenuWidth = menuContainer.getMeasuredWidth();
            int fullPopupWidth = popupWindowLayout.getMeasuredWidth();

            int widthDiff = 0;
            if (popupWindowLayout.getSwipeBack() != null) {
                widthDiff = popupWindowLayout.getSwipeBack().getMeasuredWidth() - visibleMenuWidth;
            }
            if (widthDiff < 0) widthDiff = 0;

            LayoutParams popupLayoutParams = (LayoutParams) popupWindowLayout.getLayoutParams();
            for (View bottomView : bottomViews) {
                if (bottomView != null) {
                    LayoutParams lp = (LayoutParams) bottomView.getLayoutParams();
                    int targetWidth;

                    if ((reactionsLayout != null && reactionsLayout.showCustomEmojiReaction()) || bottomView.getTag(R.id.fit_width_tag) != null) {
                        targetWidth = visibleMenuWidth + dp(16);

                        if (fullPopupWidth > 0 && targetWidth > fullPopupWidth) {
                            targetWidth = fullPopupWidth;
                        }

                    } else {
                        targetWidth = LayoutHelper.MATCH_PARENT;
                    }
                    int targetLeftMargin = popupLayoutParams.leftMargin;
                    int targetRightMargin = popupLayoutParams.rightMargin + widthDiff;

                    if (lp.width != targetWidth || lp.leftMargin != targetLeftMargin || lp.rightMargin != targetRightMargin) {
                        lp.width = targetWidth;
                        lp.leftMargin = targetLeftMargin;
                        lp.rightMargin = targetRightMargin;
                        changed = true;
                    }

                    if (progressToSwipeBack > 0) {
                        bottomView.setAlpha(1f - progressToSwipeBack);
                    }
                }
            }

            updatePopupTranslation();

            if (changed) {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            }
        }
    }

    private void updatePopupTranslation() {
        float x = (1f - progressToSwipeBack) * popupLayoutLeftOffset;
        popupWindowLayout.setTranslationX(x);

        boolean hasReactions = reactionsLayout != null
                && reactionsLayout.getItemsCount() > 0
                && reactionsLayout.getVisibility() == View.VISIBLE;

        float baseAlpha = hasReactions ? lastReactionsTransitionProgress : 1f;

        for (View bottomView : bottomViews) {
            if (bottomView != null) {
                bottomView.setTranslationX(x);
                bottomView.setAlpha(baseAlpha * (1f - progressToSwipeBack) * currentPopupAlpha);
            }
        }
    }

    public void applyViewBottom(FrameLayout bottomView) {
        if (bottomView != null) {
            this.bottomViews.add(bottomView);
            updateBottomOffset();
        }
    }

    public void setReactionsLayout(ReactionsContainerLayout reactionsLayout) {
        this.reactionsLayout = reactionsLayout;
        if (reactionsLayout != null) {
            reactionsLayout.setChatScrimView(this);
        }
    }

    private void updateBottomOffset() {
        bottomViewYOffset = popupWindowLayout.getVisibleHeight() - popupWindowLayout.getMeasuredHeight();
        updateBottomViewPosition();
    }

    public void setPopupWindowLayout(ActionBarPopupWindow.ActionBarPopupWindowLayout popupWindowLayout) {
        this.popupWindowLayout = popupWindowLayout;
        popupWindowLayout.setOnSizeChangedListener(this::updateBottomOffset);
        if (popupWindowLayout.getSwipeBack() != null) {
            popupWindowLayout.getSwipeBack().addOnSwipeBackProgressListener((layout, toProgress, progress) -> {

                boolean hasReactions = reactionsLayout != null
                        && reactionsLayout.getItemsCount() > 0
                        && reactionsLayout.getVisibility() == View.VISIBLE;

                float baseAlpha = hasReactions ? lastReactionsTransitionProgress : 1f;

                for (View bottomView : bottomViews) {
                    if (bottomView != null) {
                        bottomView.setAlpha(baseAlpha * (1f - progress) * currentPopupAlpha);
                    }
                }
                progressToSwipeBack = progress;
                updatePopupTranslation();
            });
        }
    }

    private void updateBottomViewPosition() {
        boolean hasReactions = reactionsLayout != null
                && reactionsLayout.getItemsCount() > 0
                && reactionsLayout.getVisibility() == View.VISIBLE;

        float effectiveProgress = hasReactions ? lastReactionsTransitionProgress : 1f;

        for (View bottomView : bottomViews) {
            if (bottomView != null) {
                if (effectiveProgress < 1f && bottomView.getMeasuredHeight() > 0) {
                    bottomViewReactionsOffset = -bottomView.getMeasuredHeight() * (1f - effectiveProgress);
                } else {
                    bottomViewReactionsOffset = 0;
                }

                float finalAlpha = 1f;

                if (effectiveProgress < 1f) {
                    finalAlpha *= effectiveProgress;
                }

                if (progressToSwipeBack > 0) {
                    finalAlpha *= (1f - progressToSwipeBack);
                }

                finalAlpha *= currentPopupAlpha;

                bottomView.setAlpha(finalAlpha);
                bottomView.setTranslationY(bottomViewYOffset + expandSize + bottomViewReactionsOffset);
            }
        }
    }

    public void setMaxHeight(int maxHeight) {
        this.maxHeight = maxHeight;
    }

    public void setExpandSize(float expandSize) {
        popupWindowLayout.setTranslationY(expandSize);
        this.expandSize = expandSize;
        updateBottomViewPosition();
    }

    public void setPopupAlpha(float alpha) {
        this.currentPopupAlpha = alpha;
        popupWindowLayout.setAlpha(alpha);
        for (View bottomView : bottomViews) {
            if (bottomView != null) {
                bottomView.setAlpha(alpha);
            }
        }
    }

    public void setReactionsTransitionProgress(float v) {
        lastReactionsTransitionProgress = v;
        popupWindowLayout.setReactionsTransitionProgress(v);

        boolean hasReactions = reactionsLayout != null && reactionsLayout.getItemsCount() > 0;
        float effectiveProgress = hasReactions ? v : 1f;

        for (View bottomView : bottomViews) {
            if (bottomView != null) {
                if (progressToSwipeBack == 0) {
                    bottomView.setAlpha(effectiveProgress);
                }

                float scale = 0.5f + effectiveProgress * 0.5f;
                bottomView.setPivotX(bottomView.getMeasuredWidth());
                bottomView.setPivotY(0);

                bottomView.setScaleX(scale);
                bottomView.setScaleY(scale);
            }
        }
        updateBottomViewPosition();
    }
}