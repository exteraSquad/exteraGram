/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.icons.ui.components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.res.ResourcesCompat;

import com.caverock.androidsvg.SVG;
import com.exteragram.messenger.icons.ExteraResources;
import com.exteragram.messenger.icons.IconManager;
import com.exteragram.messenger.icons.IconPack;
import com.exteragram.messenger.icons.IconPackStorage;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Locale;

public class ReplaceIconBottomSheet extends BottomSheet {

    private final int resId;
    private final IconPack iconPack;
    private final String resourceName;

    private IconInfoView originalIconInfoView;
    private IconInfoView newIconInfoView;

    private File newIconTempFile;
    private String newIconOriginalName;

    private Drawable originalDrawable;
    private Drawable newDrawable;

    private boolean waitingForResult = false;

    private int loadedOriginalWidth = 0;
    private int loadedOriginalHeight = 0;
    private int savedCustomFileWidth = 0;
    private int savedCustomFileHeight = 0;

    private boolean needSave = false;
    private boolean needReset = false;

    private ButtonWithCounterView resetButton;

    public ReplaceIconBottomSheet(@NonNull Context context, int resId, @NonNull IconPack iconPack) {
        super(context, false);
        fixNavigationBar();
        this.resId = resId;
        this.iconPack = iconPack;
        this.resourceName = context.getResources().getResourceEntryName(resId);

        setCustomView(createView(context));
        loadDrawables(context);
    }

    private void loadDrawables(Context context) {
        Utilities.globalQueue.postRunnable(() -> {
            Drawable original = null;
            if (context.getResources() instanceof ExteraResources) {
                try {
                    original = ((ExteraResources) context.getResources()).getOriginalDrawable(resId);
                } catch (Exception ignored) {
                }
            }
            if (original == null) {
                original = ResourcesCompat.getDrawable(context.getResources(), resId, context.getTheme());
            }

            int oW = 0;
            int oH = 0;

            if (original != null) {
                oW = original.getIntrinsicWidth();
                oH = original.getIntrinsicHeight();
            }

            Drawable newDr = null;
            int newW = 0;
            int newH = 0;

            String currentIconFileName = iconPack.getIcons().get(resourceName);
            if (currentIconFileName != null) {
                File file = new File(IconPackStorage.INSTANCE.getIconPacksDirectory(), iconPack.getId() + "/" + currentIconFileName);
                if (file.exists()) {
                    try {
                        if (file.getName().toLowerCase(Locale.ROOT).endsWith(".svg")) {
                            try (FileInputStream fis = new FileInputStream(file)) {
                                SVG svg = SVG.getFromInputStream(fis);
                                newW = (int) (svg.getDocumentWidth() > 0 ? svg.getDocumentWidth() : svg.getDocumentViewBox().width());
                                newH = (int) (svg.getDocumentHeight() > 0 ? svg.getDocumentHeight() : svg.getDocumentViewBox().height());
                            }
                        } else {
                            BitmapFactory.Options options = new BitmapFactory.Options();
                            options.inJustDecodeBounds = true;
                            BitmapFactory.decodeFile(file.getAbsolutePath(), options);
                            newW = options.outWidth;
                            newH = options.outHeight;
                        }
                    } catch (Exception e) {
                        FileLog.e(e);
                    }

                    Bitmap b = IconManager.INSTANCE.createBitmapFromFile(file.getAbsolutePath(), resId, AndroidUtilities.displayMetrics.densityDpi, context.getTheme());
                    if (b != null) {
                        newDr = new BitmapDrawable(context.getResources(), b);
                    }
                }
            }

            Drawable finalOriginal = original;
            int finalOW = oW;
            int finalOH = oH;
            Drawable finalNewDr = newDr;
            int finalNewW = newW;
            int finalNewH = newH;
            AndroidUtilities.runOnUIThread(() -> {
                originalDrawable = finalOriginal;
                loadedOriginalWidth = finalOW;
                loadedOriginalHeight = finalOH;

                newDrawable = finalNewDr;
                savedCustomFileWidth = finalNewW;
                savedCustomFileHeight = finalNewH;

                if (originalIconInfoView != null) {
                    originalIconInfoView.update(originalDrawable, resourceName, loadedOriginalWidth, loadedOriginalHeight);
                }

                if (resetButton != null) {
                    resetButton.setText(LocaleController.getString(newDrawable != null ? R.string.Reset : R.string.Cancel), false);
                }

                updateNewInfo(newDrawable, iconPack.getIcons().get(resourceName), savedCustomFileWidth, savedCustomFileHeight);
            });
        });
    }

    private View createView(Context context) {
        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(0, dp(16), 0, 0);

        LinearLayout contentContainer = new LinearLayout(context);
        contentContainer.setOrientation(LinearLayout.HORIZONTAL);
        contentContainer.setGravity(Gravity.CENTER_HORIZONTAL);
        contentContainer.setPadding(dp(16), 0, dp(16), 0);

        originalIconInfoView = new IconInfoView(context, false);
        originalIconInfoView.update(originalDrawable, resourceName, loadedOriginalWidth, loadedOriginalHeight);
        contentContainer.addView(originalIconInfoView, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1.0f));

        LinearLayout arrowLayout = new LinearLayout(context);
        arrowLayout.setOrientation(LinearLayout.VERTICAL);
        arrowLayout.setGravity(Gravity.CENTER_HORIZONTAL);

        ArrowView arrowView = new ArrowView(context);
        arrowLayout.addView(arrowView, LayoutHelper.createLinear(24, 60));

        contentContainer.addView(arrowLayout, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, 24, 0, 24, 0));

        newIconInfoView = new IconInfoView(context, true);
        newIconInfoView.setTargetDimensions(loadedOriginalWidth, loadedOriginalHeight);
        newIconInfoView.getIconView().setFocusable(true);
        newIconInfoView.getIconView().setOnClickListener(v -> {
            if (isDismissed()) return;
            BaseFragment fragment = LaunchActivity.getSafeLastFragment();
            if (fragment != null) {
                Activity activity = fragment.getParentActivity();
                if (activity != null) {
                    ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                    boolean canPaste = false;
                    if (clipboard != null && clipboard.hasPrimaryClip()) {
                        ClipData clip = clipboard.getPrimaryClip();
                        if (clip != null && clip.getItemCount() > 0) {
                            ClipData.Item item = clip.getItemAt(0);
                            if (item.getUri() != null) {
                                canPaste = true;
                            } else if (item.getText() != null) {
                                String text = item.getText().toString().trim();
                                if (!text.isEmpty() && (
                                        text.contains("<svg") ||
                                                text.contains("<SVG") ||
                                                text.startsWith("/")
                                )) {
                                    canPaste = true;
                                }
                            }
                        }
                    }
                    ItemOptions.makeOptions(containerView, v)
                            .addIf(canPaste, R.drawable.msg_copy, LocaleController.getString(R.string.PasteFromClipboard), () -> {
                                if (clipboard != null && clipboard.getPrimaryClip() != null && clipboard.getPrimaryClip().getItemCount() > 0) {
                                    ClipData.Item item = clipboard.getPrimaryClip().getItemAt(0);
                                    Uri uri = item.getUri();
                                    if (uri != null) {
                                        processSelectedImage(context, uri);
                                    } else {
                                        CharSequence text = item.getText();
                                        if (text != null) {
                                            processClipboardText(context, text);
                                        }
                                    }
                                }
                            })
                            .add(R.drawable.msg_photos, LocaleController.getString(R.string.SelectFromGallery), () -> {
                                startPicker(activity, false);
                            })
                            .add(R.drawable.msg2_folder, LocaleController.getString(R.string.StoryMusicSelectFromFiles), () -> {
                                startPicker(activity, true);
                            })
                            .setDrawScrim(false)
                            .setOnTopOfScrim()
                            .setDimAlpha(0)
                            .setGravity(Gravity.CENTER_HORIZONTAL)
                            .show();
                }
            }
        });
        contentContainer.addView(newIconInfoView, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1.0f));

        updateNewInfo(newDrawable, iconPack.getIcons().get(resourceName), savedCustomFileWidth, savedCustomFileHeight);

        container.addView(contentContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 24));

        LinearLayout buttonsLayout = new LinearLayout(context);
        buttonsLayout.setOrientation(LinearLayout.VERTICAL);
        buttonsLayout.setPadding(dp(16), 0, dp(16), dp(16));

        ButtonWithCounterView saveButton = new ButtonWithCounterView(context, true, resourcesProvider);
        saveButton.setRound();
        saveButton.setText(LocaleController.getString(R.string.Save), false);
        saveButton.setOnClickListener(v -> {
            if (newIconTempFile != null) {
                needSave = true;
            }
            dismiss();
        });
        buttonsLayout.addView(saveButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48));

        resetButton = new ButtonWithCounterView(context, false, resourcesProvider);
        resetButton.setRound().setNeutral();
        resetButton.setText(LocaleController.getString(iconPack.getIcons().get(resourceName) != null ? R.string.Reset : R.string.Cancel), false);
        resetButton.setOnClickListener(v -> {
            if (newDrawable != null) {
                needReset = true;
            }
            dismiss();
        });
        buttonsLayout.addView(resetButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 0, 8, 0, 0));

        container.addView(buttonsLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        return container;
    }

    private void updateNewInfo(Drawable drawable, String name, int w, int h) {
        if (newIconInfoView != null) {
            if (loadedOriginalWidth > 0 && loadedOriginalHeight > 0) {
                newIconInfoView.setTargetDimensions(loadedOriginalWidth, loadedOriginalHeight);
            }
            newIconInfoView.update(drawable, name, w, h);
        }
    }

    private void startPicker(Activity activity, boolean selectFromFiles) {
        waitingForResult = true;
        IconManager.INSTANCE.startIconPicker(activity, selectFromFiles, uri -> {
            waitingForResult = false;
            if (uri != null) {
                processSelectedImage(activity, uri);
            }
            return kotlin.Unit.INSTANCE;
        });
    }

    private void updateNewIconFromFile(Context context, File file, String originalName, int width, int height) {
        Bitmap b = IconManager.INSTANCE.createBitmapFromFile(
                file.getAbsolutePath(),
                resId,
                AndroidUtilities.displayMetrics.densityDpi,
                context.getTheme()
        );

        if (b != null) {
            Drawable d = new BitmapDrawable(context.getResources(), b);
            AndroidUtilities.runOnUIThread(() -> {
                if (isDismissed()) {
                    file.delete();
                    return;
                }
                if (newIconTempFile != null && newIconTempFile.exists() && !newIconTempFile.equals(file)) {
                    newIconTempFile.delete();
                }
                newIconTempFile = file;
                newIconOriginalName = originalName;
                newDrawable = d;
                if (resetButton != null) {
                    resetButton.setText(LocaleController.getString(R.string.Reset), false);
                }
                updateNewInfo(newDrawable, newIconOriginalName, width, height);
            });
        }
    }

    private void processClipboardText(Context context, CharSequence text) {
        Utilities.globalQueue.postRunnable(() -> {
            try {
                String str = text.toString();
                if (str.contains("<svg") || str.contains("<SVG")) {
                    File tempDest = new File(ApplicationLoader.applicationContext.getCacheDir(), "temp_import_" + System.currentTimeMillis() + ".svg");
                    try (FileOutputStream output = new FileOutputStream(tempDest)) {
                        output.write(str.getBytes());
                    }
                    int w;
                    int h;
                    try (FileInputStream fis = new FileInputStream(tempDest)) {
                        SVG svg = SVG.getFromInputStream(fis);
                        w = (int) (svg.getDocumentWidth() > 0 ? svg.getDocumentWidth() : svg.getDocumentViewBox().width());
                        h = (int) (svg.getDocumentHeight() > 0 ? svg.getDocumentHeight() : svg.getDocumentViewBox().height());
                    }
                    updateNewIconFromFile(context, tempDest, resourceName + ".svg", w, h);
                } else if (str.trim().startsWith("/")) {
                    File f = new File(str.trim());
                    if (f.exists()) {
                        Uri uri = Uri.fromFile(f);
                        processSelectedImage(context, uri);
                    }
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    private void processSelectedImage(Context context, Uri uri) {
        Utilities.globalQueue.postRunnable(() -> {
            File tempRaw = null;
            try {
                String originalName = null;
                try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
                    if (cursor != null && cursor.moveToFirst()) {
                        int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                        if (nameIndex != -1) {
                            originalName = cursor.getString(nameIndex);
                        }
                    }
                }
                if (TextUtils.isEmpty(originalName)) {
                    originalName = "icon_" + System.currentTimeMillis();
                }

                tempRaw = new File(
                        ApplicationLoader.applicationContext.getCacheDir(),
                        "temp_import_" + System.currentTimeMillis() + "_raw"
                );

                try (InputStream input = context.getContentResolver().openInputStream(uri);
                     FileOutputStream output = new FileOutputStream(tempRaw)) {
                    if (input != null) {
                        byte[] buffer = new byte[4096];
                        int n;
                        while ((n = input.read(buffer)) != -1) {
                            output.write(buffer, 0, n);
                        }
                    }
                }

                boolean isSvg = false;
                try (FileInputStream fis = new FileInputStream(tempRaw)) {
                    byte[] header = new byte[1024];
                    int read = fis.read(header);
                    if (read > 0) {
                        String headerStr = new String(header, 0, read).trim().toLowerCase(Locale.ROOT);
                        if (headerStr.contains("<svg") || (headerStr.startsWith("<?xml") && headerStr.contains("<svg"))) {
                            isSvg = true;
                        }
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }

                String finalExt;
                if (isSvg) {
                    finalExt = "svg";
                } else {
                    if (originalName.toLowerCase(Locale.ROOT).endsWith(".jpg") || originalName.toLowerCase(Locale.ROOT).endsWith(".jpeg")) {
                        finalExt = "jpg";
                    } else if (originalName.toLowerCase(Locale.ROOT).endsWith(".webp")) {
                        finalExt = "webp";
                    } else {
                        finalExt = "png";
                    }
                }

                if (!originalName.toLowerCase(Locale.ROOT).endsWith("." + finalExt)) {
                    int dotIndex = originalName.lastIndexOf('.');
                    if (dotIndex > 0) {
                        originalName = originalName.substring(0, dotIndex);
                    }
                    originalName += "." + finalExt;
                }

                File finalTempFile = new File(
                        ApplicationLoader.applicationContext.getCacheDir(),
                        "temp_import_" + System.currentTimeMillis() + "." + finalExt
                );

                if (tempRaw.renameTo(finalTempFile)) {
                    int originalWidth;
                    int originalHeight;

                    if (isSvg) {
                        try (FileInputStream fis = new FileInputStream(finalTempFile)) {
                            SVG svg = SVG.getFromInputStream(fis);
                            originalWidth = (int) (svg.getDocumentWidth() > 0 ? svg.getDocumentWidth() : svg.getDocumentViewBox().width());
                            originalHeight = (int) (svg.getDocumentHeight() > 0 ? svg.getDocumentHeight() : svg.getDocumentViewBox().height());
                        }
                    } else {
                        BitmapFactory.Options options = new BitmapFactory.Options();
                        options.inJustDecodeBounds = true;
                        BitmapFactory.decodeFile(finalTempFile.getAbsolutePath(), options);
                        originalWidth = options.outWidth;
                        originalHeight = options.outHeight;
                    }

                    updateNewIconFromFile(context, finalTempFile, originalName, originalWidth, originalHeight);
                }

            } catch (Exception e) {
                FileLog.e(e);
            } finally {
                if (tempRaw != null && tempRaw.exists()) {
                    tempRaw.delete();
                }
            }
        });
    }

    private static class IconInfoView extends LinearLayout {
        private final BorderedImageView iconView;
        private final TextView infoName;
        private final TextView infoResolution;

        private float targetAspectRatio = -1f;

        public IconInfoView(Context context, boolean isDashed) {
            super(context);
            setOrientation(LinearLayout.VERTICAL);
            setGravity(Gravity.CENTER_HORIZONTAL);

            iconView = new BorderedImageView(context);
            iconView.setDashed(isDashed);
            iconView.setColorFilter(new PorterDuffColorFilter(
                    Theme.getColor(Theme.key_windowBackgroundWhiteGrayIcon),
                    PorterDuff.Mode.MULTIPLY
            ));
            iconView.setPadding(dp(6), dp(6), dp(6), dp(6));
            iconView.setScaleType(ImageView.ScaleType.FIT_CENTER);
            addView(iconView, LayoutHelper.createLinear(60, 60));

            infoName = new TextView(context);
            infoName.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            infoName.setTypeface(AndroidUtilities.bold());
            infoName.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            infoName.setGravity(Gravity.CENTER);
            infoName.setSingleLine(true);
            infoName.setEllipsize(TextUtils.TruncateAt.END);
            addView(infoName, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 12, 0, 0));

            infoResolution = new TextView(context);
            infoResolution.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            infoResolution.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            infoResolution.setGravity(Gravity.CENTER);
            addView(infoResolution, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));
        }

        public void setTargetDimensions(int w, int h) {
            if (w > 0 && h > 0) {
                this.targetAspectRatio = (float) w / (float) h;
            } else {
                this.targetAspectRatio = -1f;
            }
        }

        public void update(Drawable drawable, String name, int w, int h) {
            if (drawable != null) {
                int displayW = w > 0 ? w : drawable.getIntrinsicWidth();
                int displayH = h > 0 ? h : drawable.getIntrinsicHeight();

                String resolutionText = String.format(Locale.ROOT, "%d×%d", displayW, displayH);
                String ratioText = getAspectRatioString(displayW, displayH);
                String fullText = String.format("%s (%s)", resolutionText, ratioText);

                infoResolution.setText(fullText);
                infoResolution.setVisibility(View.VISIBLE);

                boolean isShapeMismatch = false;
                if (targetAspectRatio > 0 && displayH > 0) {
                    float currentRatio = (float) displayW / (float) displayH;
                    if (Math.abs(currentRatio - targetAspectRatio) > 0.1f) {
                        isShapeMismatch = true;
                    }
                }

                if (isShapeMismatch) {
                    infoResolution.setTextColor(Theme.getColor(Theme.key_text_RedRegular));
                } else {
                    infoResolution.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
                }

                iconView.setImageDrawable(drawable);
            } else {
                infoResolution.setVisibility(View.INVISIBLE);
                iconView.setImageDrawable(null);
            }

            if (name != null) {
                infoName.setText(name);
                infoName.setVisibility(View.VISIBLE);
            } else {
                infoName.setVisibility(View.INVISIBLE);
            }
        }

        public BorderedImageView getIconView() {
            return iconView;
        }

        private String getAspectRatioString(int w, int h) {
            if (h == 0) return "?";
            int gcd = gcd(w, h);
            return (w / gcd) + ":" + (h / gcd);
        }

        private int gcd(int a, int b) {
            return b == 0 ? a : gcd(b, a % b);
        }
    }

    private static class ArrowView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();

        public ArrowView(Context context) {
            super(context);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(2f));
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayIcon));
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            super.onDraw(canvas);
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            float w = dp(18);

            path.reset();
            path.moveTo(cx - w / 2f, cy);
            path.lineTo(cx + w / 2f, cy);
            path.moveTo(cx + w / 2f - dp(7), cy - dp(7));
            path.lineTo(cx + w / 2f, cy);
            path.lineTo(cx + w / 2f - dp(7), cy + dp(7));
            canvas.drawPath(path, paint);
        }
    }

    public static class BorderedImageView extends ImageView {

        private final Paint solidPaint;
        private final Paint dashedPaint;
        private final Paint bgPaint;
        private boolean isDashed = false;

        private final Path path = new Path();
        private final RectF rect = new RectF();
        private final float cornerRadius = AndroidUtilities.dp(12);
        private final float strokeWidth = AndroidUtilities.dpf2(1.25f);

        public BorderedImageView(@NonNull Context context) {
            this(context, null);
        }

        public BorderedImageView(@NonNull Context context, @Nullable AttributeSet attrs) {
            super(context, attrs);

            bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            bgPaint.setStyle(Paint.Style.FILL);
            bgPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));

            solidPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            solidPaint.setStyle(Paint.Style.STROKE);
            solidPaint.setColor(AndroidUtilities.multiplyAlphaComponent(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText), 0.3f));
            solidPaint.setStrokeWidth(strokeWidth);

            dashedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            dashedPaint.setStyle(Paint.Style.STROKE);
            dashedPaint.setColor(AndroidUtilities.multiplyAlphaComponent(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText), 0.3f));
            dashedPaint.setStrokeWidth(strokeWidth);
            dashedPaint.setPathEffect(new DashPathEffect(new float[]{AndroidUtilities.dp(8), AndroidUtilities.dp(8)}, 0));
        }

        public void setDashed(boolean isDashed) {
            this.isDashed = isDashed;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float halfStroke = strokeWidth / 2f;
            rect.set(halfStroke, halfStroke, getWidth() - halfStroke, getHeight() - halfStroke);

            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, bgPaint);

            super.onDraw(canvas);

            path.reset();
            path.addRoundRect(rect, cornerRadius, cornerRadius, Path.Direction.CW);
            canvas.drawPath(path, isDashed ? dashedPaint : solidPaint);
        }
    }

    @Override
    public void dismissInternal() {
        super.dismissInternal();
        if (needSave && newIconTempFile != null && !needReset) {
            IconManager.INSTANCE.saveCustomIcon(iconPack.getId(), resId, newIconTempFile, newIconOriginalName);
        } else {
            if (newIconTempFile != null && newIconTempFile.exists()) {
                newIconTempFile.delete();
            }
            if (needReset) {
                IconManager.INSTANCE.resetCustomIcon(iconPack.getId(), resId);
            }
        }
    }

    @Override
    public void dismiss() {
        if (waitingForResult) {
            return;
        }
        super.dismiss();
    }
}