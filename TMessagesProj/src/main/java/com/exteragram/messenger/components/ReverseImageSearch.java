/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.components;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.text.TextUtils;
import android.webkit.CookieManager;
import android.webkit.WebSettings;

import androidx.annotation.NonNull;

import com.exteragram.messenger.utils.network.ExteraHttpClient;

import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.browser.Browser;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class ReverseImageSearch {

    public enum Provider {
        YANDEX, GOOGLE, BING, TINEYE
    }

    private static final int MAX_IMAGE_SIDE = 1280;
    private static final String UPLOAD_URL = "https://yandex.com/images/touch/search?rpt=imageview&format=json&request=" +
            Uri.encode("{\"blocks\":[{\"block\":\"cbir-uploader__get-cbir-id\"}]}");

    public static Runnable search(File image, Provider provider, Utilities.Callback<String> onResult) {
        final AtomicBoolean cancelled = new AtomicBoolean();
        final AtomicReference<Call> call = new AtomicReference<>();
        Utilities.globalQueue.postRunnable(() -> {
            byte[] jpeg = null;
            try {
                jpeg = encodeImage(image);
            } catch (Throwable e) {
                FileLog.e(e);
            }
            if (cancelled.get()) {
                return;
            }
            if (jpeg == null) {
                deliver(cancelled, onResult, null);
                return;
            }
            Call uploadCall = ExteraHttpClient.INSTANCE.getClient().newCall(buildUploadRequest(jpeg));
            call.set(uploadCall);
            if (cancelled.get()) {
                uploadCall.cancel();
                return;
            }
            uploadCall.enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call c, @NonNull IOException e) {
                    if (!c.isCanceled()) {
                        FileLog.e(e);
                    }
                    deliver(cancelled, onResult, null);
                }

                @Override
                public void onResponse(@NonNull Call c, @NonNull Response response) {
                    String url = null;
                    try (response) {
                        ResponseBody body = response.body();
                        if (response.isSuccessful() && body != null) {
                            JSONObject params = new JSONObject(body.string()).getJSONArray("blocks").getJSONObject(0).getJSONObject("params");
                            String cbirId = params.optString("cbirId");
                            String imageUrl = params.optString("originalImageUrl");
                            if (!TextUtils.isEmpty(cbirId) && !TextUtils.isEmpty(imageUrl)) {
                                url = buildUrl(provider, response.request().url().host(), cbirId, imageUrl);
                            }
                        }
                    } catch (Throwable e) {
                        FileLog.e(e);
                    }
                    deliver(cancelled, onResult, url);
                }
            });
        });
        return () -> {
            cancelled.set(true);
            Call uploadCall = call.get();
            if (uploadCall != null) {
                uploadCall.cancel();
            }
        };
    }

    public static void open(Context context, String url) {
        seedConsentCookies();
        Browser.openInTelegramBrowser(context, url, null);
    }

    private static void deliver(AtomicBoolean cancelled, Utilities.Callback<String> onResult, String url) {
        AndroidUtilities.runOnUIThread(() -> {
            if (!cancelled.get()) {
                onResult.run(url);
            }
        });
    }

    private static Request buildUploadRequest(byte[] jpeg) {
        MultipartBody body = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("upfile", "image.jpg", RequestBody.create(jpeg, MediaType.get("image/jpeg")))
                .build();
        Request.Builder builder = new Request.Builder()
                .url(UPLOAD_URL)
                .post(body)
                .header("Accept", "application/json")
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Cookie", "gdpr=0");
        try {
            String userAgent = WebSettings.getDefaultUserAgent(ApplicationLoader.applicationContext);
            if (!TextUtils.isEmpty(userAgent)) {
                builder.header("User-Agent", userAgent);
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
        return builder.build();
    }

    private static String buildUrl(Provider provider, String uploadHost, String cbirId, String imageUrl) {
        final String image = Uri.encode(imageUrl);
        return switch (provider) {
            case YANDEX -> "https://" + uploadHost + "/images/search?rpt=imageview&cbir_id=" + Uri.encode(cbirId) + "&url=" + image;
            case GOOGLE -> "https://lens.google.com/uploadbyurl?url=" + image;
            case BING -> "https://www.bing.com/images/search?view=detailv2&iss=sbi&form=SBIVSP&sbisrc=UrlPaste&q=imgurl:" + image;
            case TINEYE -> "https://tineye.com/search?url=" + image;
        };
    }

    private static void seedConsentCookies() {
        try {
            CookieManager cm = CookieManager.getInstance();
            cm.setAcceptCookie(true);
            cm.setCookie("https://www.google.com", "SOCS=CAISHAgBEhJnd3NfMjAyNjA2MjYtMF9SQzEaAmVuIAEaBgiAjozSBg; Domain=.google.com; Path=/; Secure; SameSite=Lax");
            cm.setCookie("https://www.bing.com", "BCP=AD=1&AL=1&SM=1; Domain=.bing.com; Path=/; Secure");
            cm.setCookie("https://tineye.com", "cookie_consent=accepted; Path=/");
            cm.setCookie("https://yandex.com", "gdpr=0; Domain=.yandex.com; Path=/; Secure");
            cm.setCookie("https://yandex.ru", "gdpr=0; Domain=.yandex.ru; Path=/; Secure");
            cm.flush();
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    private static byte[] encodeImage(File file) {
        if (file == null || !file.exists()) {
            return null;
        }
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);

        BitmapFactory.Options options = new BitmapFactory.Options();
        int side = Math.max(bounds.outWidth, bounds.outHeight);
        int sample = 1;
        while (side / sample > MAX_IMAGE_SIDE * 2) {
            sample *= 2;
        }
        options.inSampleSize = sample;

        Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath(), options);
        if (bitmap == null) {
            return null;
        }

        int w = bitmap.getWidth(), h = bitmap.getHeight();
        int max = Math.max(w, h);
        if (max > MAX_IMAGE_SIDE) {
            float scale = MAX_IMAGE_SIDE / (float) max;
            Bitmap scaled = Bitmap.createScaledBitmap(bitmap, Math.round(w * scale), Math.round(h * scale), true);
            if (scaled != bitmap) {
                bitmap.recycle();
                bitmap = scaled;
            }
        }

        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream);
        bitmap.recycle();
        return stream.toByteArray();
    }
}
