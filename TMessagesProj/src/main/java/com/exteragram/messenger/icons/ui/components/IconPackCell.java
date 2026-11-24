/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.icons.ui.components;

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

import com.exteragram.messenger.icons.IconPack;
import com.exteragram.messenger.utils.text.LocaleUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.EffectsTextView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

import org.telegram.ui.Components.RadioButton;

@SuppressLint("ViewConstructor")
public class IconPackCell extends FrameLayout {

    private boolean needDivider;

    private final TextView title;
    private final EffectsTextView subtitle;
    private final IconPackPreviewView iconView;

    private final ImageView handle;
    private final RadioButton radioButton;

    @SuppressLint("ClickableViewAccessibility")
    public IconPackCell(@NonNull Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);

        setWillNotDraw(false);

        iconView = new IconPackPreviewView(context);
        addView(iconView, LayoutHelper.createFrame(44, 44, Gravity.LEFT | Gravity.CENTER_VERTICAL, 16, 0, 0, 0));

        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        addView(linearLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.CENTER_VERTICAL, 16 + 44 + 16, 0, 8 + 48 + 4, 0));

        title = new TextView(context);
        title.setGravity(Gravity.LEFT);
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setSingleLine(true);
        title.setMaxLines(1);
        title.setIncludeFontPadding(false);
        title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        title.setTypeface(AndroidUtilities.regular());
//        title.setTypeface(AndroidUtilities.bold());
        linearLayout.addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        subtitle = new EffectsTextView(context);
        subtitle.setGravity(Gravity.LEFT);
        subtitle.setClickable(true);
        subtitle.setTypeface(AndroidUtilities.regular());
        subtitle.setMovementMethod(new AndroidUtilities.LinkMovementMethodMy());
        subtitle.setLinkTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteLinkText));
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        subtitle.setEllipsize(TextUtils.TruncateAt.END);
        subtitle.setSingleLine(true);
        subtitle.setMaxLines(1);
        subtitle.setIncludeFontPadding(false);
        subtitle.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        linearLayout.addView(subtitle, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 4, 0, 0));

        radioButton = new RadioButton(context);
        radioButton.setSize(AndroidUtilities.dp(20));
        radioButton.setColor(Theme.getColor(Theme.key_radioBackground), Theme.getColor(Theme.key_radioBackgroundChecked));
        addView(radioButton, LayoutHelper.createFrame(22, 22, Gravity.RIGHT | Gravity.CENTER_VERTICAL, 0, 0, 18, 0));

        handle = new ImageView(context);
        handle.setImageResource(R.drawable.list_reorder);
        handle.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayIcon, resourcesProvider), PorterDuff.Mode.MULTIPLY));
        handle.setPadding(dp(12), dp(12), dp(12), dp(12));
        handle.setScaleType(ImageView.ScaleType.FIT_CENTER);
        addView(handle, LayoutHelper.createFrame(48, 48, Gravity.RIGHT | Gravity.CENTER_VERTICAL, 0, 0, 4, 0));
    }

    public ImageView getHandle() {
        return handle;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(dp(44 + 10 + 10), MeasureSpec.EXACTLY));
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        if (needDivider) {
            canvas.drawLine(dp(76), getMeasuredHeight() - 1, getMeasuredWidth(), getMeasuredHeight() - 1, Theme.dividerPaint);
        }
    }

    public void set(IconPack iconPack, boolean needDivider, boolean checked, boolean reorderable) {
        this.needDivider = needDivider;

        title.setText(iconPack.getName());
        subtitle.setText(LocaleUtils.fullyFormatText(iconPack.getAuthor()));
        iconView.setIconPack(iconPack);

        if (iconPack.isBase()) {
            handle.setVisibility(GONE);
            radioButton.setVisibility(VISIBLE);
            radioButton.setChecked(checked, true);
        } else {
            handle.setVisibility(reorderable ? VISIBLE : GONE);
            radioButton.setVisibility(GONE);
        }
    }

    public static class Factory extends UItem.UItemFactory<IconPackCell> {
        static {
            setup(new IconPackCell.Factory());
        }

        @Override
        public IconPackCell createView(Context context, RecyclerListView listView, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
            return new IconPackCell(context, resourcesProvider);
        }

        @SuppressLint("ClickableViewAccessibility")
        @Override
        public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter, UniversalRecyclerView listView) {
            if (view instanceof IconPackCell iconPackCell) {
                iconPackCell.set((IconPack) item.object, divider, item.checked, item.reordering);
                iconPackCell.getHandle().setOnTouchListener((v, event) -> {
                    if (event.getAction() == MotionEvent.ACTION_DOWN) {
                        if (listView != null) {
                            listView.startDrag(listView.getChildViewHolder(view));
                        }
                    }
                    return false;
                });
            }
        }

        public static UItem asIconPackCell(IconPack pack) {
            final UItem item = UItem.ofFactory(IconPackCell.Factory.class);
            item.object = pack;
            return item;
        }
    }
}