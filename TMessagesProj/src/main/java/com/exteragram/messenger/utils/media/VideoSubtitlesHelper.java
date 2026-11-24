/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.utils.media;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.TextView;

import androidx.annotation.Nullable;

import androidx.media3.common.MimeTypes;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.tl.TL_iv;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.VideoPlayer;

import java.io.File;
import java.util.Locale;

public final class VideoSubtitlesHelper {

    private static final String PREF_NAME = "video_external_subtitles";
    private static final String KEY_PATH_PREFIX = "path_";
    private static final String KEY_MIME_PREFIX = "mime_";
    private static final String KEY_LABEL_PREFIX = "label_";

    private VideoSubtitlesHelper() {
    }

    public enum LoadError {
        NONE,
        UNSUPPORTED_FORMAT,
        LOAD_FAILED
    }

    public record SubtitleState(String path, String mimeType, String label) {

        public boolean isValid() {
            if (TextUtils.isEmpty(path) || TextUtils.isEmpty(mimeType)) {
                return false;
            }
            File file = new File(path);
            return file.exists() && file.length() > 0;
        }

        public String getDisplayName() {
            if (!TextUtils.isEmpty(label)) {
                return label;
            }
            return new File(path).getName();
        }

        public VideoPlayer.ExternalSubtitle toExternalSubtitle() {
            return new VideoPlayer.ExternalSubtitle(Uri.fromFile(new File(path)), mimeType, getDisplayName());
        }
    }

    public record SubtitleLoadResult(@Nullable SubtitleState subtitleState, LoadError error) {

        public boolean isSuccess() {
            return subtitleState != null;
        }
    }

    public static boolean areSame(@Nullable SubtitleState first, @Nullable SubtitleState second) {
        if (first == second) {
            return true;
        }
        if (first == null || second == null) {
            return false;
        }
        return TextUtils.equals(first.path, second.path)
                && TextUtils.equals(first.mimeType, second.mimeType)
                && TextUtils.equals(first.label, second.label);
    }

    public static TextView createSubtitlesView(Context context) {
        var textView = new TextView(context);
        textView.setGravity(Gravity.CENTER);
        textView.setTextColor(Color.WHITE);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, AndroidUtilities.isTablet() ? 20 : 16);
        textView.setMaxLines(3);
        textView.setPadding(dp(18), dp(8), dp(18), dp(8));
        textView.setBackground(Theme.createRoundRectDrawable(dp(8), 0xCC000000));
        textView.setShadowLayer(dp(2), 0, dp(1), 0x99000000);
        textView.setVisibility(TextView.GONE);
        return textView;
    }

    @Nullable
    public static String buildVideoKey(@Nullable MessageObject messageObject, @Nullable String pathObject, @Nullable Uri videoUri, @Nullable TL_iv.PageBlock pageBlock) {
        if (messageObject != null && messageObject.getDocument() != null) {
            return "doc_" + messageObject.getDocument().id;
        }
        if (!TextUtils.isEmpty(pathObject)) {
            return makePathKey(pathObject);
        }
        if (videoUri != null) {
            return makeUriKey(videoUri);
        }
        if (pageBlock instanceof TL_iv.pageBlockVideo pageBlockVideo) {
            return "page_video_" + pageBlockVideo.video_id;
        }
        return null;
    }

    public static SubtitleLoadResult loadFromPickerIntent(@Nullable Intent data) {
        return loadFromUri(data != null ? data.getData() : null);
    }

    public static Intent createPickerIntent() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[] {"application/x-subrip", "text/vtt"});
        return intent;
    }

    public static SubtitleLoadResult loadFromUri(@Nullable Uri uri) {
        if (uri == null) {
            return new SubtitleLoadResult(null, LoadError.LOAD_FAILED);
        }

        final String mimeType = resolveMimeType(uri);
        if (TextUtils.isEmpty(mimeType)) {
            return new SubtitleLoadResult(null, LoadError.UNSUPPORTED_FORMAT);
        }

        String path = null;
        final String extension = MimeTypes.TEXT_VTT.equals(mimeType) ? "vtt" : "srt";
        try {
            if ("file".equalsIgnoreCase(uri.getScheme())) {
                path = uri.getPath();
            } else {
                path = AndroidUtilities.getPath(uri);
                if (TextUtils.isEmpty(path) || path.startsWith("content://")) {
                    path = MediaController.copyFileToCache(uri, extension);
                }
            }
        } catch (Exception ignore) {
        }

        if (TextUtils.isEmpty(path)) {
            return new SubtitleLoadResult(null, LoadError.LOAD_FAILED);
        }
        File file = new File(path);
        if (!file.exists() || file.length() <= 0) {
            return new SubtitleLoadResult(null, LoadError.LOAD_FAILED);
        }
        return new SubtitleLoadResult(new SubtitleState(path, mimeType, file.getName()), LoadError.NONE);
    }

    @Nullable
    public static SubtitleState restore(@Nullable String videoKey) {
        if (TextUtils.isEmpty(videoKey)) {
            return null;
        }
        SharedPreferences preferences = getPreferences();
        String path = preferences.getString(KEY_PATH_PREFIX + videoKey, null);
        String mimeType = preferences.getString(KEY_MIME_PREFIX + videoKey, null);
        String label = preferences.getString(KEY_LABEL_PREFIX + videoKey, null);
        if (TextUtils.isEmpty(path) || TextUtils.isEmpty(mimeType)) {
            return null;
        }
        SubtitleState state = new SubtitleState(path, mimeType, label);
        if (!state.isValid()) {
            clear(videoKey);
            return null;
        }
        return state;
    }

    public static void save(@Nullable String videoKey, @Nullable SubtitleState state) {
        if (TextUtils.isEmpty(videoKey) || state == null || !state.isValid()) {
            return;
        }
        getPreferences().edit()
                .putString(KEY_PATH_PREFIX + videoKey, state.path())
                .putString(KEY_MIME_PREFIX + videoKey, state.mimeType())
                .putString(KEY_LABEL_PREFIX + videoKey, state.getDisplayName())
                .apply();
    }

    public static void clear(@Nullable String videoKey) {
        if (TextUtils.isEmpty(videoKey)) {
            return;
        }
        getPreferences().edit()
                .remove(KEY_PATH_PREFIX + videoKey)
                .remove(KEY_MIME_PREFIX + videoKey)
                .remove(KEY_LABEL_PREFIX + videoKey)
                .apply();
    }

    @Nullable
    public static String makeUriKey(@Nullable Uri uri) {
        if (uri == null) {
            return null;
        }
        return makePathKey(uri.toString());
    }

    @Nullable
    public static String makePathKey(@Nullable String path) {
        if (TextUtils.isEmpty(path)) {
            return null;
        }
        return "uri_" + Utilities.MD5(path);
    }

    @Nullable
    private static String resolveMimeType(Uri uri) {
        String mimeType = null;
        try {
            mimeType = ApplicationLoader.applicationContext.getContentResolver().getType(uri);
        } catch (Exception ignore) {
        }
        if (!TextUtils.isEmpty(mimeType)) {
            final String lowerMimeType = mimeType.toLowerCase(Locale.US);
            if (MimeTypes.TEXT_VTT.equals(lowerMimeType) || lowerMimeType.contains("vtt")) {
                return MimeTypes.TEXT_VTT;
            }
            if (MimeTypes.APPLICATION_SUBRIP.equals(lowerMimeType) || lowerMimeType.contains("subrip") || lowerMimeType.contains("srt")) {
                return MimeTypes.APPLICATION_SUBRIP;
            }
        }
        final String extension = getSubtitleExtension(uri);
        if ("vtt".equals(extension)) {
            return MimeTypes.TEXT_VTT;
        }
        if ("srt".equals(extension)) {
            return MimeTypes.APPLICATION_SUBRIP;
        }
        return null;
    }

    @Nullable
    private static String getSubtitleExtension(Uri uri) {
        String path = AndroidUtilities.getPath(uri);
        if (TextUtils.isEmpty(path)) {
            path = uri.getPath();
        }
        if (TextUtils.isEmpty(path)) {
            path = uri.toString();
        }
        if (TextUtils.isEmpty(path)) {
            return null;
        }
        String lowerPath = path.toLowerCase(Locale.US);
        if (lowerPath.endsWith(".srt")) {
            return "srt";
        }
        if (lowerPath.endsWith(".vtt")) {
            return "vtt";
        }
        return null;
    }

    private static SharedPreferences getPreferences() {
        Context context = ApplicationLoader.applicationContext;
        return context.getSharedPreferences(PREF_NAME, Activity.MODE_PRIVATE);
    }
}