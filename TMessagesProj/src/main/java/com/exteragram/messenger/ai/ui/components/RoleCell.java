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

import com.exteragram.messenger.ai.data.Role;

import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RadioButton;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

@SuppressLint("ViewConstructor")
public class RoleCell extends FrameLayout {

    private static final long DEFAULT_EMOJI_ID = 5359441070201513074L;

    private final int currentAccount;
    private final Theme.ResourcesProvider resourcesProvider;
    private final BackupImageView emojiView;
    private final TextView titleView;
    private final TextView subtitleView;
    private final RadioButton radioButton;

    private boolean needDivider;

    public RoleCell(@NonNull Context context, int currentAccount, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.currentAccount = currentAccount;
        this.resourcesProvider = resourcesProvider;

        setWillNotDraw(false);

        emojiView = new BackupImageView(context);
        addView(emojiView, LayoutHelper.createFrame(32, 32, Gravity.LEFT | Gravity.CENTER_VERTICAL, 18, 0, 0, 0));

        LinearLayout textContainer = new LinearLayout(context);
        textContainer.setOrientation(LinearLayout.VERTICAL);
        addView(textContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.CENTER_VERTICAL, 68, 0, 62, 0));

        titleView = new TextView(context);
        titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        titleView.setSingleLine(true);
        titleView.setMaxLines(1);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        titleView.setGravity(Gravity.LEFT);
        titleView.setIncludeFontPadding(false);
        textContainer.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        subtitleView = new TextView(context);
        subtitleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourcesProvider));
        subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        subtitleView.setSingleLine(true);
        subtitleView.setMaxLines(1);
        subtitleView.setEllipsize(TextUtils.TruncateAt.END);
        subtitleView.setGravity(Gravity.LEFT);
        subtitleView.setIncludeFontPadding(false);
        textContainer.addView(subtitleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 4, 0, 0));

        radioButton = new RadioButton(context);
        radioButton.setSize(dp(20));
        radioButton.setColor(Theme.getColor(Theme.key_radioBackground), Theme.getColor(Theme.key_radioBackgroundChecked));
        addView(radioButton, LayoutHelper.createFrame(48, 48, Gravity.RIGHT | Gravity.CENTER_VERTICAL, 0, 0, 8, 0));
    }

    public RadioButton getRadioButton() {
        return radioButton;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(
                MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(dp(62), MeasureSpec.EXACTLY)
        );
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        if (needDivider) {
            canvas.drawLine(dp(68), getMeasuredHeight() - 1, getMeasuredWidth(), getMeasuredHeight() - 1, Theme.dividerPaint);
        }
    }

    public void set(Role role, boolean checked, boolean needDivider) {
        this.needDivider = needDivider;

        titleView.setText(role.getName());
        subtitleView.setText(role.getPrompt());
        subtitleView.setVisibility(VISIBLE);
        radioButton.setChecked(checked, true);

        long emojiId = role.getEmojiId() != 0 ? role.getEmojiId() : DEFAULT_EMOJI_ID;
        AnimatedEmojiDrawable emojiDrawable = new AnimatedEmojiDrawable(AnimatedEmojiDrawable.CACHE_TYPE_ALERT_PREVIEW, currentAccount, emojiId);
        emojiDrawable.setColorFilter(Theme.getAnimatedEmojiColorFilter(resourcesProvider));
        emojiView.setAnimatedEmojiDrawable(emojiDrawable);
    }

    public static class Factory extends UItem.UItemFactory<RoleCell> {
        static {
            setup(new RoleCell.Factory());
        }

        @Override
        public RoleCell createView(Context context, RecyclerListView listView, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
            return new RoleCell(context, currentAccount, resourcesProvider);
        }

        @Override
        public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter, UniversalRecyclerView listView) {
            if (view instanceof RoleCell roleCell && item.object instanceof Role role) {
                roleCell.set(role, item.checked, divider);
                roleCell.getRadioButton().setOnClickListener(item.clickCallback);
            }
        }

        @Override
        public boolean contentsEquals(UItem a, UItem b) {
            if (!(a.object instanceof Role roleA) || !(b.object instanceof Role roleB)) {
                return false;
            }
            return a.checked == b.checked
                    && TextUtils.equals(roleA.getName(), roleB.getName())
                    && TextUtils.equals(roleA.getPrompt(), roleB.getPrompt())
                    && roleA.getEmojiId() == roleB.getEmojiId();
        }

        public static UItem asRoleCell(Role role, View.OnClickListener radioClickListener) {
            UItem item = UItem.ofFactory(RoleCell.Factory.class);
            item.object = role;
            item.checked = role.isSelected();
            item.clickCallback = radioClickListener;
            return item;
        }
    }
}