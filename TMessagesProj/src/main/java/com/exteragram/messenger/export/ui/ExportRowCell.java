/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.export.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import com.exteragram.messenger.utils.ui.material.ColorTones;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLObject;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

public class ExportRowCell extends FrameLayout {

    private static final int HEIGHT_DP = 60;
    private static final int COVER_SIZE_DP = 40;
    private static final int ICON_SIZE_DP = 22;

    private final int account;
    private final Theme.ResourcesProvider resourcesProvider;
    private final BackupImageView avatarView;
    private final AvatarDrawable avatarDrawable = new AvatarDrawable();
    private final FolderView folderView;
    private final TextView titleView;
    private final TextView subtitleView;
    private final TextView valueView;

    public ExportRowCell(Context context, int account, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.account = account;
        this.resourcesProvider = resourcesProvider;

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.HORIZONTAL);
        content.setGravity(Gravity.CENTER_VERTICAL);
        content.setPadding(dp(18), 0, dp(20), 0);
        addView(content, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        FrameLayout cover = new FrameLayout(context);
        avatarView = new BackupImageView(context);
        avatarView.setRoundRadius(dp(COVER_SIZE_DP / 2f));
        cover.addView(avatarView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        folderView = new FolderView(context);
        cover.addView(folderView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        content.addView(cover, LayoutHelper.createLinear(COVER_SIZE_DP, COVER_SIZE_DP));

        LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(LinearLayout.VERTICAL);
        titleView = createLine(context, 16, Theme.key_windowBackgroundWhiteBlackText);
        NotificationCenter.listenEmojiLoading(titleView);
        texts.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        subtitleView = createLine(context, 13, Theme.key_windowBackgroundWhiteGrayText2);
        texts.addView(subtitleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));
        content.addView(texts, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, 14, 0, 12, 0));

        valueView = createLine(context, 16, Theme.key_windowBackgroundWhiteValueText);
        content.addView(valueView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
    }

    private TextView createLine(Context context, int size, int colorKey) {
        TextView view = new TextView(context);
        view.setTextColor(Theme.getColor(colorKey, resourcesProvider));
        view.setTextSize(TypedValue.COMPLEX_UNIT_DIP, size);
        view.setTypeface(AndroidUtilities.regular());
        view.setLines(1);
        view.setSingleLine(true);
        view.setEllipsize(TextUtils.TruncateAt.END);
        return view;
    }

    public void bind(Row row) {
        bind(row.title, row.subtitle, row.value, row.chat, row.dialogId);
    }

    private void bind(String title, String subtitle, String value, boolean chat, long dialogId) {
        titleView.setText(Emoji.replaceEmoji(title, titleView.getPaint().getFontMetricsInt(), false));
        subtitleView.setText(subtitle);
        valueView.setText(value);
        if (!chat) {
            avatarView.setVisibility(GONE);
            folderView.setVisibility(VISIBLE);
            return;
        }
        folderView.setVisibility(GONE);
        avatarView.setVisibility(VISIBLE);
        MessagesController controller = MessagesController.getInstance(account);
        TLObject peer = dialogId > 0 ? controller.getUser(dialogId) : dialogId < 0 ? controller.getChat(-dialogId) : null;
        if (peer != null) {
            avatarDrawable.setInfo(account, peer);
            avatarView.setForUserOrChat(peer, avatarDrawable);
        } else {
            avatarDrawable.setInfo(dialogId != 0 ? dialogId : title.hashCode(), title, null);
            avatarView.setImageDrawable(avatarDrawable);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(dp(HEIGHT_DP), MeasureSpec.EXACTLY));
    }

    public record Row(String title, String subtitle, String value, boolean chat, long dialogId) {
    }

    public static class Factory extends UItem.UItemFactory<ExportRowCell> {
        static {
            setup(new Factory());
        }

        @Override
        public ExportRowCell createView(Context context, RecyclerListView listView, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
            return new ExportRowCell(context, currentAccount, resourcesProvider);
        }

        @Override
        public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter, UniversalRecyclerView listView) {
            if (view instanceof ExportRowCell cell && item.object instanceof Row row) {
                cell.bind(row);
            }
        }

        @Override
        public boolean contentsEquals(UItem a, UItem b) {
            return a.object instanceof Row rowA && rowA.equals(b.object);
        }

        public static UItem of(int id, Row row) {
            UItem item = UItem.ofFactory(Factory.class);
            item.id = id;
            item.object = row;
            return item;
        }
    }

    private final class FolderView extends View {

        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Drawable icon;
        private int iconColor;

        FolderView(Context context) {
            super(context);
            Drawable drawable = ContextCompat.getDrawable(context, R.drawable.files_folder);
            icon = drawable != null ? drawable.mutate() : null;
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            final int accent = Theme.getColor(Theme.key_inappPlayerPlayPause, resourcesProvider);
            final boolean dark = !ColorTones.isLightSurface(Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider));
            paint.setColor(ColorUtils.setAlphaComponent(accent, (int) (0xFF * (dark ? 0.2f : 0.14f))));
            canvas.drawCircle(getWidth() / 2f, getHeight() / 2f, Math.min(getWidth(), getHeight()) / 2f, paint);
            if (icon == null) {
                return;
            }
            if (iconColor != accent) {
                iconColor = accent;
                icon.setColorFilter(new PorterDuffColorFilter(accent, PorterDuff.Mode.SRC_IN));
            }
            final int size = dp(ICON_SIZE_DP);
            final int left = (getWidth() - size) / 2;
            final int top = (getHeight() - size) / 2;
            icon.setBounds(left, top, left + size, top + size);
            icon.draw(canvas);
        }
    }
}
