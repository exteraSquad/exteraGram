/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.messageexport.ui;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.exteragram.messenger.messageexport.appearance.MessageExportAppearance;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatThemeController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ResultCallback;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.EmojiThemes;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.ScaleStateListAnimator;

import java.util.ArrayList;
import java.util.List;

@SuppressLint("ViewConstructor")
final class ThemePresetsView extends RecyclerView {

    private static final Object REFRESH = new Object();

    private final MessageExportAppearance appearance;
    private final Theme.ResourcesProvider resourcesProvider;
    private final Utilities.Callback<EmojiThemes> onSelect;
    private final Adapter adapter = new Adapter();

    ThemePresetsView(Context context, int account, MessageExportAppearance appearance, Theme.ResourcesProvider resourcesProvider, Utilities.Callback<EmojiThemes> onSelect) {
        super(context);
        this.appearance = appearance;
        this.resourcesProvider = resourcesProvider;
        this.onSelect = onSelect;
        setOverScrollMode(View.OVER_SCROLL_NEVER);
        setHorizontalScrollBarEnabled(false);
        setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false));
        setClipToPadding(false);
        setPadding(dp(8), 0, dp(8), 0);
        setItemAnimator(null);
        setAdapter(adapter);
        adapter.setThemes(createBaseThemes());
        loadThemes(account);
    }

    private ArrayList<EmojiThemes> createBaseThemes() {
        ArrayList<EmojiThemes> themes = new ArrayList<>();
        if (appearance.showsHomePreset()) {
            themes.add(appearance.getHomePreset());
        }
        return themes;
    }

    private void loadThemes(int account) {
        ChatThemeController.getInstance(account).requestAllChatThemes(new ResultCallback<List<EmojiThemes>>() {
            @Override
            public void onComplete(List<EmojiThemes> result) {
                if (result == null) {
                    return;
                }
                appearance.onPresetsLoaded(result);
                ArrayList<EmojiThemes> themes = createBaseThemes();
                for (EmojiThemes theme : result) {
                    if (theme != null && !theme.isAnyStub() && theme.getThemeGift() == null && !appearance.isSameAsChat(theme)) {
                        themes.add(theme);
                    }
                }
                adapter.setThemes(themes);
            }

            @Override
            public void onError(TLRPC.TL_error error) {
            }
        }, false);
    }

    void refresh() {
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child instanceof PresetCell) {
                PresetCell cell = (PresetCell) child;
                cell.set(cell.theme, isChecked(cell.theme));
            }
        }
        adapter.notifyItemRangeChanged(0, adapter.getItemCount(), REFRESH);
    }

    void updateSelection() {
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child instanceof PresetCell) {
                PresetCell cell = (PresetCell) child;
                cell.setChecked(isChecked(cell.theme), true);
            }
        }
    }

    private boolean isChecked(EmojiThemes theme) {
        return theme == null ? appearance.isChatSelected() : appearance.getPreset() == theme;
    }

    private final class Adapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        private final ArrayList<EmojiThemes> themes = new ArrayList<>();

        @SuppressLint("NotifyDataSetChanged")
        void setThemes(ArrayList<EmojiThemes> list) {
            themes.clear();
            themes.addAll(list);
            notifyDataSetChanged();
        }

        EmojiThemes getTheme(int position) {
            return position == 0 ? null : themes.get(position - 1);
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            PresetCell cell = new PresetCell(parent.getContext());
            cell.setLayoutParams(new RecyclerView.LayoutParams(dp(56), dp(64)));
            ScaleStateListAnimator.apply(cell, .1f, 1.5f);
            RecyclerView.ViewHolder holder = new RecyclerView.ViewHolder(cell) {
            };
            cell.setOnClickListener(v -> {
                int position = holder.getAdapterPosition();
                if (position >= 0) {
                    onSelect.run(getTheme(position));
                }
            });
            return holder;
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            EmojiThemes theme = getTheme(position);
            ((PresetCell) holder.itemView).set(theme, isChecked(theme));
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position, @NonNull List<Object> payloads) {
            if (payloads.contains(REFRESH) && ((PresetCell) holder.itemView).theme == getTheme(position)) {
                return;
            }
            onBindViewHolder(holder, position);
        }

        @Override
        public int getItemCount() {
            return 1 + themes.size();
        }
    }

    private final class PresetCell extends View {

        private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path clip = new Path();
        private Drawable preview;
        private Drawable previousPreview;
        private float previewProgress = 1f;
        private ValueAnimator previewAnimator;
        private EmojiThemes theme;
        private boolean bound;
        private float checkedProgress;
        private ValueAnimator checkedAnimator;

        PresetCell(Context context) {
            super(context);
            ringPaint.setStyle(Paint.Style.STROKE);
            ringPaint.setStrokeWidth(dp(2));
            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setStrokeWidth(Math.max(1, dp(0.66f)));
        }

        void set(EmojiThemes theme, boolean checked) {
            boolean sameTheme = bound && this.theme == theme;
            this.theme = theme;
            bound = true;
            Drawable newPreview;
            if (theme == null) {
                newPreview = appearance.createChatPreviewDrawable();
                setContentDescription(getString(R.string.MessageExportChatTheme));
            } else if (theme == appearance.getHomePreset()) {
                newPreview = appearance.createPresetPreviewDrawable(theme);
                setContentDescription(getString(R.string.MessageExportStandardTheme));
            } else {
                newPreview = appearance.createPresetPreviewDrawable(theme);
                setContentDescription(theme.getEmoticonOrSlug());
            }
            if (previewAnimator != null) {
                previewAnimator.cancel();
                previewAnimator = null;
            }
            if (sameTheme && preview != null) {
                previousPreview = preview;
                preview = newPreview;
                previewProgress = 0f;
                previewAnimator = ValueAnimator.ofFloat(0f, 1f);
                previewAnimator.addUpdateListener(animation -> {
                    previewProgress = (float) animation.getAnimatedValue();
                    invalidate();
                });
                previewAnimator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        previousPreview = null;
                        previewProgress = 1f;
                    }
                });
                previewAnimator.setDuration(350);
                previewAnimator.setInterpolator(CubicBezierInterpolator.DEFAULT);
                previewAnimator.start();
            } else {
                preview = newPreview;
                previousPreview = null;
                previewProgress = 1f;
            }
            setChecked(checked, sameTheme);
            invalidate();
        }

        void setChecked(boolean value, boolean animated) {
            if (checkedAnimator != null) {
                checkedAnimator.cancel();
                checkedAnimator = null;
            }
            setSelected(value);
            float target = value ? 1f : 0f;
            if (!animated || checkedProgress == target) {
                checkedProgress = target;
                invalidate();
                return;
            }
            checkedAnimator = ValueAnimator.ofFloat(checkedProgress, target);
            checkedAnimator.addUpdateListener(animation -> {
                checkedProgress = (float) animation.getAnimatedValue();
                invalidate();
            });
            checkedAnimator.setDuration(250);
            checkedAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
            checkedAnimator.start();
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            float radius = AndroidUtilities.lerp(AndroidUtilities.dpf2(20), AndroidUtilities.dpf2(17), checkedProgress);
            int size = dp(40);
            clip.rewind();
            clip.addCircle(cx, cy, radius, Path.Direction.CW);
            canvas.save();
            canvas.clipPath(clip);
            int left = (int) (cx - size / 2f);
            int top = (int) (cy - size / 2f);
            if (previousPreview != null && previewProgress < 1f) {
                previousPreview.setBounds(left, top, left + size, top + size);
                previousPreview.setAlpha(255);
                previousPreview.draw(canvas);
            }
            if (preview != null) {
                preview.setBounds(left, top, left + size, top + size);
                preview.setAlpha((int) (255 * previewProgress));
                preview.draw(canvas);
                preview.setAlpha(255);
            }
            canvas.restore();
            strokePaint.setColor(Theme.multAlpha(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider), 0.1f));
            canvas.drawCircle(cx, cy, radius - strokePaint.getStrokeWidth() / 2f, strokePaint);
            if (checkedProgress > 0) {
                ringPaint.setColor(Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider));
                ringPaint.setAlpha((int) (255 * checkedProgress));
                canvas.drawCircle(cx, cy, AndroidUtilities.lerp(AndroidUtilities.dpf2(19), AndroidUtilities.dpf2(21), checkedProgress), ringPaint);
            }
        }
    }
}
