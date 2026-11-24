/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.preferences.components;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.content.pm.PackageInfoCompat;

import com.exteragram.messenger.preferences.utils.IconShapeHelper;
import com.exteragram.messenger.utils.ui.MonetUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RLottieImageView;

public class HeaderSettingsCell extends LinearLayout implements CustomPreferenceCell {

    public final RLottieImageView imageView;
    public final TextView titleTextView;
    public final TextView subtitleTextView;

    private Path shapePath;

    public HeaderSettingsCell(Context context) {
        super(context);

        setOrientation(VERTICAL);
        setGravity(Gravity.CENTER);

        Drawable arrow = ContextCompat.getDrawable(context, R.drawable.ic_foreground).mutate();
        Theme.ThemeInfo theme = Theme.getActiveTheme();
        int color = ContextCompat.getColor(context, R.color.ic_background);

        if (theme.isMonet() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            color = MonetUtils.getColor(theme.isDark() ? "a2_800" : "a1_100");
            arrow = ContextCompat.getDrawable(context, R.drawable.ic_foreground_solid).mutate();
            arrow.setColorFilter(new PorterDuffColorFilter(MonetUtils.getColor(theme.isDark() ? "a1_200" : "a1_700"), PorterDuff.Mode.MULTIPLY));
        }

        imageView = new RLottieImageView(context) {
            @Override
            public void draw(@NonNull Canvas canvas) {
                canvas.save();
                canvas.clipPath(getPath());
                super.draw(canvas);
                canvas.restore();
            }
        };
        imageView.setBackgroundColor(color);
        imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        imageView.setImageDrawable(arrow);
        addView(imageView, LayoutHelper.createLinear(72, 72, Gravity.CENTER | Gravity.TOP, 0, 28, 0, 0));

        titleTextView = new TextView(context);
        titleTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        titleTextView.setTypeface(AndroidUtilities.bold());
        titleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        titleTextView.setText(LocaleController.getString(R.string.exteraAppName));
        titleTextView.setLines(1);
        titleTextView.setMaxLines(1);
        titleTextView.setSingleLine(true);
        titleTextView.setPadding(0, 0, 0, 0);
        titleTextView.setGravity(Gravity.CENTER);
        addView(titleTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER | Gravity.TOP, 50, 16, 50, 0));

        subtitleTextView = new TextView(context);
        subtitleTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        subtitleTextView.setTypeface(AndroidUtilities.bold());
        subtitleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        subtitleTextView.setLineSpacing(AndroidUtilities.dp(2), 1f);
        PackageInfo info;
        try {
            info = ApplicationLoader.applicationContext.getPackageManager().getPackageInfo(ApplicationLoader.applicationContext.getPackageName(), 0);
        } catch (PackageManager.NameNotFoundException e) {
            throw new RuntimeException(e);
        }
        var sb = new StringBuilder(BuildVars.BUILD_VERSION_STRING);
        if (info != null) {
            sb.append(" (").append(PackageInfoCompat.getLongVersionCode(info)).append(")");
        }
        subtitleTextView.setText(sb);
        subtitleTextView.setGravity(Gravity.CENTER);
        subtitleTextView.setLines(0);
        subtitleTextView.setMaxLines(0);
        subtitleTextView.setSingleLine(false);
        subtitleTextView.setPadding(0, 0, 0, 0);
        addView(subtitleTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER | Gravity.TOP, 60, 2, 60, 28));
    }

    private Path getPath() {
        if (shapePath == null) {
            shapePath = IconShapeHelper.INSTANCE.getFinalIconShapePath(72, 72, 18);
        }
        return shapePath;
    }

    @Override
    public void invalidate() {
        shapePath = null;
        imageView.invalidate();
        super.invalidate();
    }

    @Override
    public boolean isSameCell(@Nullable Object object) {
        if (this == object) return true;
        if (!(object instanceof HeaderSettingsCell other)) return false;
        return imageView.equals(other.imageView) &&
                titleTextView.equals(other.titleTextView) &&
                subtitleTextView.equals(other.subtitleTextView);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
    }
}
