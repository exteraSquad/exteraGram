/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.preferences.appearance.components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.exteragram.messenger.ExteraConfig;
import com.exteragram.messenger.preferences.components.CustomPreferenceCell;
import com.exteragram.messenger.preferences.components.PreviewBackgroundDrawable;
import com.exteragram.messenger.utils.text.LocaleUtils;

import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.LayoutHelper;

import java.util.Objects;

@SuppressLint("ViewConstructor")
public class ChatListPreviewCell extends FrameLayout implements CustomPreferenceCell {

    private final ActionBar actionBar;
    private final AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable statusDrawable;

    public ChatListPreviewCell(Context context) {
        super(context);
        setWillNotDraw(false);

        statusDrawable = new AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable(null, dp(26));
        statusDrawable.center = true;

        actionBar = new ActionBar(context);
        actionBar.setItemsColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText), false);
        actionBar.setOccupyStatusBar(false);
        ActionBarMenu menu = actionBar.createMenu();
        menu.addItem(0, R.drawable.ic_ab_other);
        actionBar.setBackground(new PreviewBackgroundDrawable());
        actionBar.setSupportsHolidayImage(true);
        addView(actionBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 21, 21, 21, 21));

        updateStatus(false);
    }

    public void updateCentered(boolean animated) {
        actionBar.refreshTitlePosition(animated);
    }

    @Override
    public void invalidate() {
        super.invalidate();
        if (actionBar != null) {
            actionBar.invalidate();
        }
    }

    private Drawable premiumStar;

    public void updateStatus(boolean animated) {
        if (actionBar == null) {
            return;
        }

        var user = UserConfig.getInstance(UserConfig.selectedAccount).getCurrentUser();

        Drawable iconToSet = null;
        
        if (user != null && !ExteraConfig.getHideActionBarStatus()) {
            Long emojiStatusId = UserObject.getEmojiStatusDocumentId(user);
            if (emojiStatusId != null) {
                statusDrawable.set(emojiStatusId, animated);
                statusDrawable.setColor(Theme.getColor(Theme.key_profile_verifiedBackground));
                iconToSet = statusDrawable;
            } else if (user.premium) {
                if (premiumStar == null) {
                    Drawable star = ContextCompat.getDrawable(getContext(), R.drawable.msg_premium_liststar).mutate();
                    star.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_profile_verifiedBackground), PorterDuff.Mode.MULTIPLY));
                    premiumStar = new AnimatedEmojiDrawable.WrapSizeDrawable(star, dp(18), dp(18)) {
                        @Override
                        public void draw(@NonNull Canvas canvas) {
                            canvas.save();
                            canvas.translate(0, dp(1));
                            super.draw(canvas);
                            canvas.restore();
                        }
                    };
                }
                iconToSet = premiumStar;
            }
        }

        if (animated) {
            actionBar.setTitleAnimatedX(LocaleUtils.getActionBarTitle(), iconToSet, true, 250);
        } else {
            actionBar.setTitle(LocaleUtils.getActionBarTitle(), iconToSet);
        }
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawLine(0, getMeasuredHeight() - 1, getMeasuredWidth(), getMeasuredHeight() - 1, Theme.dividerPaint);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), getMeasuredHeight());
    }

    @Override
    public boolean isSameCell(@Nullable Object object) {
        if (this == object) return true;
        if (!(object instanceof ChatListPreviewCell other)) return false;
        return Objects.equals(actionBar, other.actionBar);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (statusDrawable != null) {
            statusDrawable.attach();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (statusDrawable != null) {
            statusDrawable.detach();
        }
    }
}
