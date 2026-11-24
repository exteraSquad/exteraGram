/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.profilecolor.debug;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.exteragram.messenger.debug.DebugConfig;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LiteMode;
import org.telegram.messenger.NotificationCenter;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.util.ArrayList;

public final class ProfileColorDebugActivity extends UniversalFragment {

    private static final int ID_KIND = 1;
    private static final int ID_MEDIA = 2;
    private static final int ID_THEME = 3;
    private static final int ID_NO_COLOR = 4;
    private static final int ID_PHOTO = 5;
    private static final int ID_BLUR = 6;
    private static final int ID_DESIGN = 7;

    private int kind;
    private boolean media = true;
    private boolean photo;

    public static void addEntry(BaseFragment fragment, FrameLayout container, View anchor) {
        final TextView button = new TextView(container.getContext());
        button.setText("Debug");
        button.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        button.setTypeface(AndroidUtilities.bold());
        button.setGravity(Gravity.CENTER);
        button.setPadding(AndroidUtilities.dp(12), 0, AndroidUtilities.dp(12), 0);
        button.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4));
        button.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), Theme.RIPPLE_MASK_ROUNDRECT_6DP));
        button.setOnClickListener(v -> fragment.presentFragment(new ProfileColorDebugActivity()));
        container.addView(button, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 40, Gravity.TOP | Gravity.LEFT, 8, 0, 0, 0));
        anchor.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) ->
                button.setTranslationY(top + (bottom - top - AndroidUtilities.dp(40)) / 2f));
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        ProfileColorDebugData.restore(currentAccount);
    }

    @Override
    protected CharSequence getTitle() {
        return "Profile colors";
    }

    private PresetsView presetsView;

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        if (presetsView == null) {
            presetsView = new PresetsView(getContext());
        }
        items.add(UItem.asHeader("Profile"));
        items.add(UItem.asCheck(ID_DESIGN, "Profile color design").setChecked(DebugConfig.getProfileColorDesign()));
        items.add(UItem.asButton(ID_KIND, "Type", ProfileColorDebugData.KIND_NAMES[kind]));
        items.add(UItem.asCheck(ID_MEDIA, "Fake shared media").setChecked(media));
        items.add(UItem.asCheck(ID_PHOTO, "Profile photo").setChecked(photo));
        items.add(UItem.asCheck(ID_BLUR, "Blur").setChecked(LiteMode.isEnabled(LiteMode.FLAG_CHAT_BLUR)));
        items.add(UItem.asButton(ID_THEME, "Switch day / night in 4 seconds"));
        items.add(UItem.asButton(ID_NO_COLOR, "Open without a color"));
        items.add(UItem.asShadow(null));
        items.add(UItem.asHeader("Color"));
        items.add(UItem.asCustom(presetsView));
        items.add(UItem.asShadow(null));
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ID_KIND) {
            kind = (kind + 1) % ProfileColorDebugData.KINDS_COUNT;
            listView.adapter.update(true);
        } else if (item.id == ID_DESIGN) {
            DebugConfig.setProfileColorDesign(!DebugConfig.getProfileColorDesign());
            listView.adapter.update(true);
        } else if (item.id == ID_MEDIA) {
            media = !media;
            listView.adapter.update(true);
        } else if (item.id == ID_PHOTO) {
            photo = !photo;
            listView.adapter.update(true);
        } else if (item.id == ID_BLUR) {
            LiteMode.toggleFlag(LiteMode.FLAG_CHAT_BLUR);
            listView.adapter.update(true);
        } else if (item.id == ID_THEME) {
            AndroidUtilities.runOnUIThread(ProfileColorDebugActivity::switchTheme, 4000);
        } else if (item.id == ID_NO_COLOR) {
            ProfileColorDebugData.open(this, kind, null, media, photo);
        }
    }

    private final class PresetsView extends ViewGroup {

        private static final int COLUMNS = 4;

        PresetsView(Context context) {
            super(context);
            final boolean dark = Theme.isCurrentThemeDark();
            for (ProfileColorDebugData.Preset preset : ProfileColorDebugData.PRESETS) {
                final int[] colors = dark ? preset.dark : preset.light;
                final int edge = 0xFF000000 | colors[colors.length > 4 ? 2 : 1];
                final int center = 0xFF000000 | colors[colors.length > 4 ? 3 : 1];
                final TextView chip = new TextView(context);
                chip.setText(preset.name);
                chip.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
                chip.setTypeface(AndroidUtilities.bold());
                chip.setGravity(Gravity.CENTER);
                chip.setMaxLines(2);
                chip.setTextColor(AndroidUtilities.computePerceivedBrightness(edge) > 0.7f ? 0xFF000000 : 0xFFFFFFFF);
                final GradientDrawable background = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{center, edge});
                background.setCornerRadius(AndroidUtilities.dp(12));
                chip.setBackground(background);
                chip.setOnClickListener(v -> ProfileColorDebugData.open(ProfileColorDebugActivity.this, kind, preset, media, photo));
                addView(chip);
            }
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            final int width = MeasureSpec.getSize(widthMeasureSpec);
            final int gap = AndroidUtilities.dp(8);
            final int chipWidth = (width - gap * (COLUMNS + 1)) / COLUMNS;
            final int chipHeight = AndroidUtilities.dp(44);
            for (int i = 0; i < getChildCount(); ++i) {
                getChildAt(i).measure(MeasureSpec.makeMeasureSpec(chipWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(chipHeight, MeasureSpec.EXACTLY));
            }
            final int rows = (getChildCount() + COLUMNS - 1) / COLUMNS;
            setMeasuredDimension(width, gap + rows * (chipHeight + gap));
        }

        @Override
        protected void onLayout(boolean changed, int l, int t, int r, int b) {
            final int gap = AndroidUtilities.dp(8);
            for (int i = 0; i < getChildCount(); ++i) {
                final View child = getChildAt(i);
                final int x = gap + (i % COLUMNS) * (child.getMeasuredWidth() + gap);
                final int y = gap + (i / COLUMNS) * (child.getMeasuredHeight() + gap);
                child.layout(x, y, x + child.getMeasuredWidth(), y + child.getMeasuredHeight());
            }
        }
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }

    private static void switchTheme() {
        final boolean toDark = !Theme.isCurrentThemeDark();
        final Theme.ThemeInfo theme = Theme.getTheme(toDark ? "Night" : "Blue");
        if (theme == null) {
            return;
        }
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.needSetDayNightTheme, theme, false, null, -1);
    }
}
