/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.ai.ui.components;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.exteragram.messenger.ai.data.Message;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

@SuppressLint("ViewConstructor")
public class HistoryMessageCell extends FrameLayout {

    private static final int MAX_LINES = 6;

    private final Theme.ResourcesProvider resourcesProvider;
    private final TextView titleView;
    private final TextView textView;

    private boolean needDivider;

    public HistoryMessageCell(@NonNull Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;

        setWillNotDraw(false);

        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        addView(container, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 22, 10, 22, 11));

        titleView = new TextView(context);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setSingleLine(true);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        titleView.setGravity(Gravity.LEFT);
        container.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        textView = new TextView(context);
        textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        textView.setLinkTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteLinkText, resourcesProvider));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        textView.setGravity(Gravity.LEFT);
        container.addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 3, 0, 0));
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), heightMeasureSpec);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        if (needDivider) {
            canvas.drawLine(dp(22), getMeasuredHeight() - 1, getMeasuredWidth() - dp(22), getMeasuredHeight() - 1, Theme.dividerPaint);
        }
    }

    public void set(Message message, CharSequence content, boolean expanded, boolean needDivider) {
        this.needDivider = needDivider;

        textView.setMaxLines(expanded ? Integer.MAX_VALUE : MAX_LINES);
        textView.setEllipsize(expanded ? null : TextUtils.TruncateAt.END);

        boolean fromUser = !Message.ASSISTANT_ROLE.equals(message.role());
        titleView.setText(getString(fromUser ? R.string.FromYou : R.string.AIAssistant));
        titleView.setTextColor(Theme.getColor(fromUser
                ? Theme.key_windowBackgroundWhiteBlueHeader
                : Theme.key_windowBackgroundWhiteGrayText2, resourcesProvider));

        textView.setText(Emoji.replaceEmoji(content == null ? "" : content, textView.getPaint().getFontMetricsInt(), false));
    }

    public static class Factory extends UItem.UItemFactory<HistoryMessageCell> {
        static {
            setup(new Factory());
        }

        @Override
        public HistoryMessageCell createView(Context context, RecyclerListView listView, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
            return new HistoryMessageCell(context, resourcesProvider);
        }

        @Override
        public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter, UniversalRecyclerView listView) {
            if (view instanceof HistoryMessageCell cell && item.object instanceof Message message) {
                cell.set(message, item.text, item.checked, divider);
            }
        }

        @Override
        public boolean equals(UItem a, UItem b) {
            return a.id == b.id;
        }

        @Override
        public boolean contentsEquals(UItem a, UItem b) {
            if (!(a.object instanceof Message messageA) || !(b.object instanceof Message messageB)) {
                return false;
            }
            return a.checked == b.checked
                    && TextUtils.equals(messageA.role(), messageB.role())
                    && TextUtils.equals(a.text, b.text);
        }

        public static UItem asHistoryCell(int id, Message message, CharSequence content, boolean expanded) {
            UItem item = UItem.ofFactory(Factory.class);
            item.id = id;
            item.object = message;
            item.text = content;
            item.checked = expanded;
            return item;
        }
    }
}
