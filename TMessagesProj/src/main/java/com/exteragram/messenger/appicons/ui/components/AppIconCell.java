/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.appicons.ui.components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;

import com.exteragram.messenger.appicons.AppIcon;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.CheckBox2;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.ScaleStateListAnimator;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

@SuppressLint("ViewConstructor")
public class AppIconCell extends LinearLayout implements Theme.Colorable {

    public static final int PREVIEW_SIZE_DP = 68;
    private static final int CHECKBOX_SIZE_DP = 21;
    private static final float SELECTION_BLEND = 0.07f;

    private final Theme.ResourcesProvider resourcesProvider;
    private final Paint selectionPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final FrameLayout previewContainer;
    private final AppIconPreviewView previewView;
    private final CheckBox2 checkBox;
    private final TextView titleView;

    private ValueAnimator selectionAnimator;
    private float selection;
    private AppIcon icon;

    public AppIconCell(@NonNull Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;

        setWillNotDraw(false);
        setClipChildren(false);
        setOrientation(VERTICAL);
        setGravity(Gravity.CENTER_HORIZONTAL);
        setPadding(dp(4), dp(10), dp(4), dp(12));

        previewContainer = new FrameLayout(context);
        previewContainer.setClipChildren(false);
        previewView = new AppIconPreviewView(context, resourcesProvider);
        previewContainer.addView(previewView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        checkBox = new CheckBox2(context, CHECKBOX_SIZE_DP, key -> key == Theme.key_windowBackgroundWhite
                ? getSelectionColor()
                : Theme.getColor(key, resourcesProvider));
        checkBox.setColor(Theme.key_featuredStickers_addButton, Theme.key_windowBackgroundWhite, Theme.key_checkboxCheck);
        checkBox.setDrawUnchecked(false);
        checkBox.setDrawBackgroundAsArc(4);
        checkBox.setProgressDelegate(progress -> previewView.setPreviewScale(1f - 0.08f * progress));
        previewContainer.addView(checkBox, LayoutHelper.createFrame(CHECKBOX_SIZE_DP + 3, CHECKBOX_SIZE_DP + 3, Gravity.BOTTOM | Gravity.RIGHT, 0, 0, 1, 1));

        addView(previewContainer, LayoutHelper.createLinear(PREVIEW_SIZE_DP, PREVIEW_SIZE_DP, Gravity.CENTER_HORIZONTAL));

        titleView = new TextView(context);
        titleView.setMaxLines(2);
        titleView.setGravity(Gravity.CENTER);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        titleView.setTypeface(AndroidUtilities.regular());
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        titleView.setLineSpacing(0, 0.95f);
        titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 8, 0, 0));

        ScaleStateListAnimator.apply(this, 0.05f, 1.2f);
    }

    @Override
    public void updateColors() {
        titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int available = MeasureSpec.getSize(widthMeasureSpec) - getPaddingLeft() - getPaddingRight();
        final int size = Math.max(dp(40), Math.min(dp(PREVIEW_SIZE_DP), available));
        final LayoutParams params = (LayoutParams) previewContainer.getLayoutParams();
        if (params.width != size) {
            params.width = params.height = size;
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    private int getSelectionColor() {
        final int panel = Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider);
        final int accent = Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider);
        return ColorUtils.blendARGB(panel, accent, SELECTION_BLEND * selection);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        if (selection <= 0f) {
            return;
        }
        selectionPaint.setColor(getSelectionColor());
        AndroidUtilities.rectTmp.set(dp(2), dp(2), getWidth() - dp(2), getHeight() - dp(2));
        canvas.drawRoundRect(AndroidUtilities.rectTmp, dp(16), dp(16), selectionPaint);
    }

    public void set(AppIcon icon, boolean selected, boolean animated) {
        this.icon = icon;
        previewView.setIcon(icon);
        titleView.setText(icon.getTitle());
        setSelected(selected, animated);
    }

    public AppIcon getIcon() {
        return icon;
    }

    private void setSelected(boolean selected, boolean animated) {
        checkBox.setChecked(selected, animated);

        float target = selected ? 1f : 0f;
        if (selectionAnimator != null) {
            selectionAnimator.cancel();
            selectionAnimator = null;
        }
        if (!animated) {
            selection = target;
            invalidate();
            return;
        }
        if (selection == target) {
            return;
        }
        selectionAnimator = ValueAnimator.ofFloat(selection, target).setDuration(220);
        selectionAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        selectionAnimator.addUpdateListener(animation -> {
            selection = (float) animation.getAnimatedValue();
            invalidate();
        });
        selectionAnimator.start();
    }

    public static class Factory extends UItem.UItemFactory<AppIconCell> {
        static {
            setup(new Factory());
        }

        @Override
        public AppIconCell createView(Context context, RecyclerListView listView, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
            return new AppIconCell(context, resourcesProvider);
        }

        @Override
        public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter, UniversalRecyclerView listView) {
            if (view instanceof AppIconCell cell && item.object instanceof AppIcon icon) {
                cell.set(icon, item.checked, cell.getIcon() == icon);
            }
        }

        @Override
        public boolean equals(UItem a, UItem b) {
            return a.object == b.object;
        }

        @Override
        public boolean contentsEquals(UItem a, UItem b) {
            return a.object == b.object && a.checked == b.checked;
        }

        public static UItem asAppIcon(AppIcon icon, boolean selected) {
            UItem item = UItem.ofFactory(Factory.class);
            item.object = icon;
            item.checked = selected;
            item.spanCount = 1;
            item.transparent = true;
            return item;
        }
    }
}
