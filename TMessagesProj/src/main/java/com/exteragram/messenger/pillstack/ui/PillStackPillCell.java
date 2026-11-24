/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.pillstack.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.exteragram.messenger.pillstack.custom.CustomPillSpec;
import com.exteragram.messenger.pillstack.custom.PillIconBadgeView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

import java.util.Objects;

@SuppressLint("ViewConstructor")
public class PillStackPillCell extends FrameLayout implements Theme.Colorable {

    private static final int TEXT_START = 65;

    public record Row(CharSequence name, @Nullable CharSequence subtitle, int iconRes, @Nullable CustomPillSpec.Icon icon, int colorTop, int colorBottom) {
    }

    private final Theme.ResourcesProvider resourcesProvider;
    private final ImageView reorderView;
    private final PillIconBadgeView iconView;
    private final TextView titleView;
    private final TextView subtitleView;
    @Nullable
    private Row row;
    private boolean needDivider;

    public PillStackPillCell(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        setWillNotDraw(false);

        reorderView = new ImageView(context);
        reorderView.setFocusable(false);
        reorderView.setClickable(true);
        reorderView.setScaleType(ImageView.ScaleType.CENTER);
        reorderView.setImageResource(R.drawable.list_reorder);
        reorderView.setContentDescription(LocaleController.getString(R.string.FilterReorder));
        addView(reorderView, LayoutHelper.createFrame(48, 48, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.CENTER_VERTICAL, 2, 0, 2, 0));

        iconView = new PillIconBadgeView(context, resourcesProvider, true);
        addView(iconView, LayoutHelper.createFrame(28, 28, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL, 18, 0, 18, 0));

        LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(LinearLayout.VERTICAL);
        addView(texts, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, LocaleController.isRTL ? 52 : TEXT_START, 0, LocaleController.isRTL ? TEXT_START : 52, 0));

        titleView = new TextView(context);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        titleView.setTypeface(AndroidUtilities.regular());
        titleView.setSingleLine();
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        titleView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        texts.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        subtitleView = new TextView(context);
        subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        subtitleView.setTypeface(AndroidUtilities.regular());
        subtitleView.setSingleLine();
        subtitleView.setEllipsize(TextUtils.TruncateAt.END);
        subtitleView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        texts.addView(subtitleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));

        updateColors();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(dp(subtitleView.getVisibility() == VISIBLE ? 58 : 50), MeasureSpec.EXACTLY));
    }

    public void set(Row row, boolean divider) {
        this.row = row;
        needDivider = divider;
        iconView.set(row.icon(), row.iconRes(), row.colorTop(), row.colorBottom());
        titleView.setText(Emoji.replaceEmoji(row.name(), titleView.getPaint().getFontMetricsInt(), false));
        subtitleView.setText(row.subtitle());
        subtitleView.setVisibility(TextUtils.isEmpty(row.subtitle()) ? GONE : VISIBLE);
        updateColors();
        requestLayout();
    }

    @Override
    public void updateColors() {
        reorderView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_stickers_menu, resourcesProvider), PorterDuff.Mode.MULTIPLY));
        titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        subtitleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourcesProvider));
        if (row != null) {
            iconView.updateColors();
        }
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        if (needDivider) {
            canvas.drawLine(LocaleController.isRTL ? 0 : dp(TEXT_START), getMeasuredHeight() - 1, getMeasuredWidth() - (LocaleController.isRTL ? dp(TEXT_START) : 0), getMeasuredHeight() - 1, Theme.getThemePaint(Theme.key_paint_divider, resourcesProvider));
        }
    }

    public static class Factory extends UItem.UItemFactory<PillStackPillCell> {
        static {
            setup(new Factory());
        }

        @Override
        @SuppressLint("ClickableViewAccessibility")
        public PillStackPillCell createView(Context context, RecyclerListView listView, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
            PillStackPillCell cell = new PillStackPillCell(context, resourcesProvider);
            if (listView instanceof UniversalRecyclerView universalListView) {
                cell.reorderView.setOnTouchListener((v, event) -> {
                    if (event.getAction() == MotionEvent.ACTION_DOWN && universalListView.isReorderAllowed()) {
                        universalListView.startDrag(universalListView.getChildViewHolder(cell));
                    }
                    return false;
                });
            }
            return cell;
        }

        @Override
        public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter, UniversalRecyclerView listView) {
            if (view instanceof PillStackPillCell cell && item.object instanceof Row row) {
                cell.set(row, divider);
            }
        }

        @Override
        public boolean equals(UItem a, UItem b) {
            return a.id == b.id;
        }

        @Override
        public boolean contentsEquals(UItem a, UItem b) {
            return Objects.equals(a.object, b.object);
        }

        public static UItem of(int id, Row row) {
            UItem item = UItem.ofFactory(Factory.class);
            item.id = id;
            item.object = row;
            return item;
        }
    }
}
