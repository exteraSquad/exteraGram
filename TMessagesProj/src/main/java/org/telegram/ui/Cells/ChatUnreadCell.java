/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Cells;

import android.annotation.SuppressLint;

import org.telegram.messenger.FileLog;
import android.content.Context;
import android.text.Layout;
import android.view.Gravity;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.SimpleTextView;

@SuppressLint("ViewConstructor")
public class ChatUnreadCell extends FrameLayout implements Theme.Colorable {

    private final SimpleTextView textView;
    private final Theme.ResourcesProvider resourcesProvider;

    public ChatUnreadCell(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;

        textView = new SimpleTextView(context);
        textView.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(50), getColor(Theme.key_chat_unreadMessagesStartBackground)));
        if (LocaleController.isRTL) textView.setAlignment(Layout.Alignment.ALIGN_OPPOSITE);
        textView.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(6), AndroidUtilities.dp(12), AndroidUtilities.dp(6));
        textView.setTextSize(14);
        textView.setGravity(Gravity.CENTER);
        textView.setTextColor(getColor(Theme.key_chat_unreadMessagesStartText));
        textView.setTypeface(AndroidUtilities.bold());
        addView(textView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 32, 0, 32, 0));
    }

    public void setText(String text) {
        textView.setText(text);
    }

    public SimpleTextView getTextView() {
        return textView;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(46), MeasureSpec.EXACTLY));
    }

    private int getColor(int key) {
        Integer color = resourcesProvider != null ? resourcesProvider.getColor(key) : null;
        return color != null ? color : Theme.getColor(key);
    }

    private int themedBackgroundColor;

    @Override
    public void updateColors() {
        try {
            final int backgroundColor = getColor(Theme.key_chat_unreadMessagesStartBackground);
            if (backgroundColor != themedBackgroundColor) {
                themedBackgroundColor = backgroundColor;
                textView.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(50), backgroundColor));
            }
            textView.setTextColor(getColor(Theme.key_chat_unreadMessagesStartText));
        } catch (Exception e) {
            FileLog.e(e);
        }
    }
}
