/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.components;

import android.content.Context;
import android.text.method.LinkMovementMethod;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.exteragram.messenger.components.ReverseImageSearch.Provider;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.LinkSpanDrawable;
import org.telegram.ui.Components.PopupSwipeBackLayout;
import org.telegram.ui.Stories.DarkThemeResourceProvider;

public class SearchPhotoPopupWrapper {

    public ActionBarPopupWindow.ActionBarPopupWindowLayout searchSwipeBackLayout;

    public SearchPhotoPopupWrapper(Context context, PopupSwipeBackLayout swipeBackLayout, Utilities.Callback<Provider> callback) {
        searchSwipeBackLayout = new ActionBarPopupWindow.ActionBarPopupWindowLayout(context, 0, null);
        searchSwipeBackLayout.setFitItems(true);

        ActionBarMenuSubItem backItem = ActionBarMenuItem.addItem(searchSwipeBackLayout, R.drawable.msg_arrow_back, LocaleController.getString(R.string.Back), false, null);
        backItem.setOnClickListener(view -> swipeBackLayout.closeForeground());
        backItem.setColors(0xfffafafa, 0xfffafafa);
        backItem.setSelectorColor(0x0fffffff);

        FrameLayout gap = new FrameLayout(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            }
        };
        gap.setMinimumWidth(AndroidUtilities.dp(196));
        gap.setBackgroundColor(0xff181818);
        searchSwipeBackLayout.addView(gap);
        LinearLayout.LayoutParams layoutParams = (LinearLayout.LayoutParams) gap.getLayoutParams();
        if (LocaleController.isRTL) {
            layoutParams.gravity = Gravity.RIGHT;
        }
        layoutParams.width = LayoutHelper.MATCH_PARENT;
        layoutParams.height = AndroidUtilities.dp(8);
        gap.setLayoutParams(layoutParams);

        ActionBarMenuSubItem item = ActionBarMenuItem.addItem(searchSwipeBackLayout, 0, "Yandex", false, null);
        item.setColors(0xfffafafa, 0xfffafafa);
        item.setOnClickListener(v -> callback.run(Provider.YANDEX));
        item.setSelectorColor(0x0fffffff);

        item = ActionBarMenuItem.addItem(searchSwipeBackLayout, 0, "Google", false, null);
        item.setColors(0xfffafafa, 0xfffafafa);
        item.setOnClickListener(v -> callback.run(Provider.GOOGLE));
        item.setSelectorColor(0x0fffffff);

        item = ActionBarMenuItem.addItem(searchSwipeBackLayout, 0, "Bing", false, null);
        item.setColors(0xfffafafa, 0xfffafafa);
        item.setOnClickListener(v -> callback.run(Provider.BING));
        item.setSelectorColor(0x0fffffff);

        item = ActionBarMenuItem.addItem(searchSwipeBackLayout, 0, "TinEye", false, null);
        item.setColors(0xfffafafa, 0xfffafafa);
        item.setOnClickListener(v -> callback.run(Provider.TINEYE));
        item.setSelectorColor(0x0fffffff);

        gap = new FrameLayout(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            }
        };
        gap.setMinimumWidth(AndroidUtilities.dp(196));
        gap.setBackgroundColor(0xff181818);
        searchSwipeBackLayout.addView(gap);
        gap.setLayoutParams(layoutParams);

        TextView textView = new LinkSpanDrawable.LinksTextView(context);
        textView.setTag(R.id.fit_width_tag, 1);
        textView.setPadding(AndroidUtilities.dp(13), 0, AndroidUtilities.dp(13), AndroidUtilities.dp(8));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        textView.setTextColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuItem, new DarkThemeResourceProvider()));
        textView.setMovementMethod(LinkMovementMethod.getInstance());
        textView.setLinkTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteLinkText, new DarkThemeResourceProvider()));
        textView.setText(LocaleController.getString(R.string.SearchPhotoInfo));
        searchSwipeBackLayout.addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8, 0, 0));
    }
}