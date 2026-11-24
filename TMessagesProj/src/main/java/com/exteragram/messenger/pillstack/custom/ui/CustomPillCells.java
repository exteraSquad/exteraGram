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

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.exteragram.messenger.ExteraConfig;
import com.exteragram.messenger.pillstack.custom.CustomPillSpec;
import com.exteragram.messenger.pillstack.custom.CustomPillTemplates;
import com.exteragram.messenger.pillstack.custom.PillColors;
import com.exteragram.messenger.pillstack.custom.PillIconBadgeView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.ScaleStateListAnimator;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

import java.util.List;
import java.util.Objects;

public final class CustomPillCells {

    private CustomPillCells() {
    }

    public record SquareRow(CustomPillSpec.Icon icon, String color, CharSequence title, CharSequence subtitle) {
        boolean sameContent(SquareRow other) {
            return Objects.equals(icon, other.icon)
                    && TextUtils.equals(color, other.color)
                    && TextUtils.equals(title, other.title)
                    && TextUtils.equals(subtitle, other.subtitle);
        }
    }

    @SuppressLint("ViewConstructor")
    public static class SquareCell extends FrameLayout {

        private final Theme.ResourcesProvider resourcesProvider;
        private final PillIconBadgeView iconView;
        private final TextView titleView;
        private final TextView subtitleView;
        private boolean needDivider;

        public SquareCell(Context context, Theme.ResourcesProvider resourcesProvider) {
            super(context);
            this.resourcesProvider = resourcesProvider;

            iconView = new PillIconBadgeView(context, resourcesProvider, false);
            addView(iconView, LayoutHelper.createFrame(28, 28, Gravity.CENTER_VERTICAL | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT), 18, 0, 18, 0));

            LinearLayout texts = new LinearLayout(context);
            texts.setOrientation(LinearLayout.VERTICAL);
            addView(texts, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, LocaleController.isRTL ? 21 : 65, 0, LocaleController.isRTL ? 65 : 21, 0));

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

            setWillNotDraw(false);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(dp(60), MeasureSpec.EXACTLY));
        }

        public void set(SquareRow row, boolean divider) {
            PillColors colors = PillColors.get(row.color());
            iconView.set(row.icon(), 0, colors.top(), colors.bottom());
            titleView.setText(row.title());
            subtitleView.setText(row.subtitle());
            subtitleView.setVisibility(TextUtils.isEmpty(row.subtitle()) ? GONE : VISIBLE);
            needDivider = divider;
            updateColors();
        }

        public void updateColors() {
            titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
            subtitleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourcesProvider));
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            super.onDraw(canvas);
            if (needDivider) {
                canvas.drawLine(LocaleController.isRTL ? 0 : dp(65), getMeasuredHeight() - 1, getMeasuredWidth() - (LocaleController.isRTL ? dp(65) : 0), getMeasuredHeight() - 1, Theme.getThemePaint(Theme.key_paint_divider, resourcesProvider));
            }
        }

        public static class Factory extends UItem.UItemFactory<SquareCell> {
            static {
                setup(new Factory());
            }

            @Override
            public SquareCell createView(Context context, RecyclerListView listView, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
                return new SquareCell(context, resourcesProvider);
            }

            @Override
            public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter, UniversalRecyclerView listView) {
                if (view instanceof SquareCell cell && item.object instanceof SquareRow row) {
                    cell.set(row, divider);
                }
            }

            @Override
            public boolean equals(UItem a, UItem b) {
                return a.id == b.id;
            }

            @Override
            public boolean contentsEquals(UItem a, UItem b) {
                return a.object instanceof SquareRow ra && b.object instanceof SquareRow rb && ra.sameContent(rb);
            }

            public static UItem of(int id, SquareRow row) {
                UItem item = UItem.ofFactory(Factory.class);
                item.id = id;
                item.object = row;
                return item;
            }
        }
    }

    public static final int NODE_OBJECT = 0;
    public static final int NODE_ARRAY = 1;
    public static final int NODE_STRING = 2;
    public static final int NODE_NUMBER = 3;
    public static final int NODE_BOOLEAN = 4;
    public static final int NODE_NULL = 5;
    public static final int NODE_MORE = 6;
    public static final int NODE_TEXT = 7;

    public record JsonNode(String path, String key, String value, int depth, int kind, boolean expanded) {
    }

    @SuppressLint("ViewConstructor")
    public static class JsonNodeCell extends FrameLayout {

        private static final int INDENT = 16;
        private static final int START = 17;

        private final Theme.ResourcesProvider resourcesProvider;
        private final ImageView arrowView;
        private final TextView keyView;
        private final TextView valueView;
        private final ImageView addView;
        private final Paint guidePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private JsonNode node;

        public JsonNodeCell(Context context, Theme.ResourcesProvider resourcesProvider) {
            super(context);
            this.resourcesProvider = resourcesProvider;
            setWillNotDraw(false);
            guidePaint.setStyle(Paint.Style.STROKE);
            guidePaint.setStrokeWidth(dp(1));

            arrowView = new ImageView(context);
            arrowView.setScaleType(ImageView.ScaleType.CENTER);
            arrowView.setImageResource(R.drawable.arrow_more);
            addView(arrowView, LayoutHelper.createFrame(20, 20, Gravity.CENTER_VERTICAL | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT)));

            LinearLayout texts = new LinearLayout(context);
            texts.setOrientation(LinearLayout.HORIZONTAL);
            texts.setGravity(Gravity.CENTER_VERTICAL);
            addView(texts, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER_VERTICAL));

            keyView = new TextView(context);
            keyView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            keyView.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MONO));
            keyView.setSingleLine();
            keyView.setEllipsize(TextUtils.TruncateAt.END);
            texts.addView(keyView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0f, Gravity.CENTER_VERTICAL, 0, 0, 10, 0));

            valueView = new TextView(context);
            valueView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            valueView.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MONO));
            valueView.setSingleLine();
            valueView.setEllipsize(TextUtils.TruncateAt.END);
            texts.addView(valueView, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, Gravity.CENTER_VERTICAL));

            addView = new ImageView(context);
            addView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            addView.setPadding(dp(4), dp(4), dp(4), dp(4));
            addView.setImageResource(R.drawable.msg_add);
            texts.addView(addView, LayoutHelper.createLinear(26, 26, 0f, Gravity.CENTER_VERTICAL, 10, 0, 0, 0));
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(dp(44), MeasureSpec.EXACTLY));
        }

        public void set(JsonNode node) {
            boolean sameNode = this.node != null && this.node.path().equals(node.path()) && this.node.kind() == node.kind();
            boolean toggled = sameNode && this.node.expanded() != node.expanded();
            this.node = node;
            int indent = START + node.depth() * INDENT;
            LayoutParams arrowParams = (LayoutParams) arrowView.getLayoutParams();
            arrowParams.leftMargin = LocaleController.isRTL ? 0 : dp(indent);
            arrowParams.rightMargin = LocaleController.isRTL ? dp(indent) : 0;
            View texts = (View) keyView.getParent();
            LayoutParams textParams = (LayoutParams) texts.getLayoutParams();
            textParams.leftMargin = dp(LocaleController.isRTL ? 17 : indent + 24);
            textParams.rightMargin = dp(LocaleController.isRTL ? indent + 24 : 17);
            boolean container = node.kind() == NODE_OBJECT || node.kind() == NODE_ARRAY;
            arrowView.setVisibility(container ? VISIBLE : GONE);
            float rotation = node.expanded() ? 0 : (LocaleController.isRTL ? 90 : -90);
            arrowView.animate().cancel();
            if (toggled) {
                arrowView.animate().rotation(rotation).setDuration(220).setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT).start();
            } else {
                arrowView.setRotation(rotation);
            }
            keyView.setText(node.kind() == NODE_MORE || node.kind() == NODE_TEXT ? "" : node.key());
            keyView.setVisibility(TextUtils.isEmpty(keyView.getText()) ? GONE : VISIBLE);
            valueView.setText(node.value());
            addView.setVisibility(container || node.kind() == NODE_MORE ? GONE : VISIBLE);
            updateColors();
            requestLayout();
            invalidate();
        }

        public void updateColors() {
            if (node == null) {
                return;
            }
            int textColor = Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider);
            int grayColor = Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourcesProvider);
            int accentColor = Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4, resourcesProvider);
            keyView.setTextColor(textColor);
            int valueColor = switch (node.kind()) {
                case NODE_STRING, NODE_TEXT -> Theme.getColor(Theme.key_windowBackgroundWhiteGreenText2, resourcesProvider);
                case NODE_NUMBER -> accentColor;
                case NODE_BOOLEAN, NODE_NULL -> Theme.getColor(Theme.key_color_orange, resourcesProvider);
                default -> grayColor;
            };
            valueView.setTextColor(valueColor);
            arrowView.setColorFilter(grayColor);
            addView.setColorFilter(accentColor);
            addView.setBackground(Theme.createCircleDrawable(dp(26), Theme.multAlpha(accentColor, .12f)));
            guidePaint.setColor(Theme.multAlpha(grayColor, .25f));
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            super.onDraw(canvas);
            if (node == null) {
                return;
            }
            for (int i = 0; i < node.depth(); i++) {
                float x = dp(START + i * INDENT + 10);
                if (LocaleController.isRTL) {
                    x = getWidth() - x;
                }
                canvas.drawLine(x, 0, x, getHeight(), guidePaint);
            }
        }

        public static class Factory extends UItem.UItemFactory<JsonNodeCell> {
            static {
                setup(new Factory());
            }

            @Override
            public JsonNodeCell createView(Context context, RecyclerListView listView, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
                return new JsonNodeCell(context, resourcesProvider);
            }

            @Override
            public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter, UniversalRecyclerView listView) {
                if (view instanceof JsonNodeCell cell && item.object instanceof JsonNode node) {
                    cell.set(node);
                }
            }

            @Override
            public boolean equals(UItem a, UItem b) {
                return a.object instanceof JsonNode na && b.object instanceof JsonNode nb && na.path().equals(nb.path()) && na.kind() == nb.kind();
            }

            @Override
            public boolean contentsEquals(UItem a, UItem b) {
                return Objects.equals(a.object, b.object);
            }

            public static UItem of(JsonNode node) {
                UItem item = UItem.ofFactory(Factory.class);
                item.object = node;
                return item;
            }
        }
    }

    public record TokenRow(int index, String label, String summary, String value, boolean divider) {
    }

    @SuppressLint("ViewConstructor")
    public static class TokenCell extends FrameLayout {

        private final Theme.ResourcesProvider resourcesProvider;
        private final TextView labelView;
        private final TextView summaryView;
        private final TextView valueView;
        private boolean needDivider;

        public TokenCell(Context context, Theme.ResourcesProvider resourcesProvider) {
            super(context);
            this.resourcesProvider = resourcesProvider;

            LinearLayout texts = new LinearLayout(context);
            texts.setOrientation(LinearLayout.VERTICAL);
            addView(texts, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 21, 0, 21, 0));

            labelView = new TextView(context);
            labelView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            labelView.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MONO));
            labelView.setSingleLine();
            labelView.setEllipsize(TextUtils.TruncateAt.END);
            labelView.setPadding(dp(7), dp(2), dp(7), dp(2));
            texts.addView(labelView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT));

            summaryView = new TextView(context);
            summaryView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            summaryView.setTypeface(AndroidUtilities.regular());
            summaryView.setSingleLine();
            summaryView.setEllipsize(TextUtils.TruncateAt.END);
            texts.addView(summaryView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT, 0, 4, 0, 0));

            valueView = new TextView(context);
            valueView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            valueView.setTypeface(AndroidUtilities.bold());
            valueView.setSingleLine();
            valueView.setEllipsize(TextUtils.TruncateAt.END);
            addView(valueView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL | (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT), 21, 0, 21, 0));
            setWillNotDraw(false);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            valueView.setMaxWidth(width / 3);
            valueView.measure(MeasureSpec.makeMeasureSpec(width / 3, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            LayoutParams lp = (LayoutParams) ((View) labelView.getParent()).getLayoutParams();
            int end = valueView.getMeasuredWidth() + dp(33);
            if (LocaleController.isRTL) {
                lp.leftMargin = end;
            } else {
                lp.rightMargin = end;
            }
            super.onMeasure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(dp(62), MeasureSpec.EXACTLY));
        }

        public void set(TokenRow row, boolean divider) {
            labelView.setText(row.label());
            summaryView.setText(row.summary());
            valueView.setText(row.value());
            needDivider = divider;
            updateColors();
            invalidate();
        }

        public void updateColors() {
            int accent = Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4, resourcesProvider);
            labelView.setTextColor(accent);
            labelView.setBackground(Theme.createRoundRectDrawable(dp(6), Theme.multAlpha(accent, 0.12f)));
            summaryView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourcesProvider));
            valueView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            super.onDraw(canvas);
            if (needDivider) {
                canvas.drawLine(LocaleController.isRTL ? 0 : dp(21), getMeasuredHeight() - 1, getMeasuredWidth() - (LocaleController.isRTL ? dp(21) : 0), getMeasuredHeight() - 1, Theme.getThemePaint(Theme.key_paint_divider, resourcesProvider));
            }
        }

        public static class Factory extends UItem.UItemFactory<TokenCell> {
            static {
                setup(new Factory());
            }

            @Override
            public TokenCell createView(Context context, RecyclerListView listView, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
                return new TokenCell(context, resourcesProvider);
            }

            @Override
            public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter, UniversalRecyclerView listView) {
                if (view instanceof TokenCell cell && item.object instanceof TokenRow row) {
                    cell.set(row, row.divider());
                }
            }

            @Override
            public boolean equals(UItem a, UItem b) {
                return a.object instanceof TokenRow ra && b.object instanceof TokenRow rb && ra.index() == rb.index();
            }

            @Override
            public boolean contentsEquals(UItem a, UItem b) {
                return Objects.equals(a.object, b.object);
            }

            public static UItem of(TokenRow row) {
                UItem item = UItem.ofFactory(Factory.class);
                item.object = row;
                return item;
            }
        }
    }

    @SuppressLint("ViewConstructor")
    public static class TemplateRowCell extends LinearLayout {

        private final Theme.ResourcesProvider resourcesProvider;
        private final Card[] cards = new Card[2];

        public TemplateRowCell(Context context, Theme.ResourcesProvider resourcesProvider) {
            super(context);
            this.resourcesProvider = resourcesProvider;
            setOrientation(HORIZONTAL);
            setPadding(dp(12), dp(4), dp(12), dp(4));
            setTag(RecyclerListView.TAG_NOT_SECTION);
            for (int i = 0; i < cards.length; i++) {
                cards[i] = new Card(context);
                addView(cards[i], LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, i == 0 ? 0 : 4, 0, i == 0 ? 4 : 0, 0));
            }
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), heightMeasureSpec);
        }

        public void set(List<CustomPillTemplates.Template> templates, Utilities.Callback<CustomPillTemplates.Template> onClick) {
            for (int i = 0; i < cards.length; i++) {
                if (i < templates.size()) {
                    CustomPillTemplates.Template template = templates.get(i);
                    cards[i].setVisibility(VISIBLE);
                    cards[i].set(template);
                    cards[i].setOnClickListener(v -> onClick.run(template));
                } else {
                    cards[i].setVisibility(INVISIBLE);
                    cards[i].setOnClickListener(null);
                }
            }
        }

        private class Card extends LinearLayout {

            private final PillIconBadgeView iconView;
            private final TextView titleView;
            private final TextView subtitleView;

            Card(Context context) {
                super(context);
                setOrientation(VERTICAL);

                iconView = new PillIconBadgeView(context, resourcesProvider, false);
                addView(iconView, LayoutHelper.createLinear(32, 32, LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT));

                titleView = new TextView(context);
                titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
                titleView.setTypeface(AndroidUtilities.bold());
                titleView.setSingleLine();
                titleView.setEllipsize(TextUtils.TruncateAt.END);
                addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 10, 0, 0));

                subtitleView = new TextView(context);
                subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
                subtitleView.setTypeface(AndroidUtilities.regular());
                subtitleView.setSingleLine();
                subtitleView.setEllipsize(TextUtils.TruncateAt.END);
                addView(subtitleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));
                ScaleStateListAnimator.apply(this, .04f, 1.2f);
            }

            void set(CustomPillTemplates.Template template) {
                PillColors colors = PillColors.get(template.color());
                iconView.set(new CustomPillSpec.Icon(CustomPillSpec.ICON_RES, template.icon()), 0, colors.top(), colors.bottom());
                titleView.setText(template.getTitle());
                subtitleView.setText(template.subtitle());
                int radius = ExteraConfig.getSectionRadiusDp();
                int padding = dp(Math.max(14, radius - 10));
                setPadding(padding, padding, padding, padding);
                int backgroundColor = Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider);
                titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
                subtitleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourcesProvider));
                setBackground(Theme.createSimpleSelectorRoundRectDrawable(dp(radius), backgroundColor, Theme.blendOver(backgroundColor, Theme.getColor(Theme.key_listSelector, resourcesProvider))));
            }
        }

        public static class Factory extends UItem.UItemFactory<TemplateRowCell> {
            static {
                setup(new Factory());
            }

            @Override
            public TemplateRowCell createView(Context context, RecyclerListView listView, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
                return new TemplateRowCell(context, resourcesProvider);
            }

            @Override
            @SuppressWarnings("unchecked")
            public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter, UniversalRecyclerView listView) {
                if (view instanceof TemplateRowCell cell && item.object instanceof List<?> templates && item.object2 instanceof Utilities.Callback<?> callback) {
                    cell.set((List<CustomPillTemplates.Template>) templates, (Utilities.Callback<CustomPillTemplates.Template>) callback);
                }
            }

            @Override
            public boolean isClickable() {
                return false;
            }

            @Override
            public boolean equals(UItem a, UItem b) {
                return a.id == b.id;
            }

            @Override
            public boolean contentsEquals(UItem a, UItem b) {
                return Objects.equals(a.object, b.object);
            }

            public static UItem of(int id, List<CustomPillTemplates.Template> templates, Utilities.Callback<CustomPillTemplates.Template> onClick) {
                UItem item = UItem.ofFactory(Factory.class);
                item.id = id;
                item.object = templates;
                item.object2 = onClick;
                item.transparent = true;
                return item;
            }
        }
    }
}
