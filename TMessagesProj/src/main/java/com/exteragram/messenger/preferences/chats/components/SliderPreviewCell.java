/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.preferences.chats.components;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.Gravity;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.exteragram.messenger.ExteraConfig;
import com.exteragram.messenger.preferences.components.AltSeekbar;
import com.exteragram.messenger.preferences.components.CustomPreferenceCell;

import org.telegram.ui.ActionBar.INavigationLayout;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

import java.util.Objects;

@SuppressLint("ViewConstructor")
public class SliderPreviewCell extends FrameLayout implements CustomPreferenceCell {

    private final MessagesPreviewCell messagesCell;
    public final AltSeekbar seekBar;
    private int lastWidth;

    private final int cellId;
    private OnSliderChangedListener listener;

    public interface OnSliderChangedListener {
        void onChanged(float value);
    }

    public SliderPreviewCell(
            INavigationLayout parentLayout,
            Context context,
            int cellId,
            int startSize,
            int endSize,
            float currentValue,
            String title,
            String leftLabel,
            String rightLabel,
            boolean useAlternativePreview) {

        super(context);
        this.cellId = cellId;
        setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        setWillNotDraw(false);

        seekBar = new AltSeekbar(context, (float p) -> {
            if (listener != null) {
                listener.onChanged(p);
            }
            invalidate();
        }, startSize, endSize, title, leftLabel, rightLabel);
        addView(seekBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        if (useAlternativePreview) {
            messagesCell = new MessagesPreviewCell(context, parentLayout, 1);
        } else {
            messagesCell = new MessagesPreviewCell(context, parentLayout);
        }

        messagesCell.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        addView(messagesCell, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 0, ExteraConfig.getNewSliderStyle() ? 120 : 112, 0, 0));
        setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
        seekBar.setProgress(currentValue);
    }

    public SliderPreviewCell setListener(OnSliderChangedListener listener) {
        this.listener = listener;
        return this;
    }

    public OnSliderChangedListener getListener() {
        return listener;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int width = MeasureSpec.getSize(widthMeasureSpec);
        if (lastWidth != width) {
            lastWidth = width;
        }
    }

    @Override
    public void invalidate() {
        super.invalidate();
        lastWidth = -1;
        messagesCell.refreshMessages();
        seekBar.invalidate();
    }

    @Override
    public boolean isSameCell(@Nullable Object object) {
        if (this == object) return true;
        if (!(object instanceof SliderPreviewCell other)) return false;
        return this.cellId == other.cellId &&
                Objects.equals(messagesCell, other.messagesCell) &&
                Objects.equals(seekBar, other.seekBar) &&
                lastWidth == other.lastWidth;
    }
}