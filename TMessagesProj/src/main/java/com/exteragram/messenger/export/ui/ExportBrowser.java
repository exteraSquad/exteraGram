/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.export.ui;

import android.content.Context;
import android.net.Uri;
import android.text.TextUtils;
import android.webkit.MimeTypeMap;
import android.webkit.WebResourceResponse;

import com.exteragram.messenger.export.ExportController;

import org.telegram.messenger.FileLog;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.browser.Browser;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ExportBrowser {

    private static final String HOST = "export.localhost";
    private static final String[] MAIN_FILES = {"export_results.html", "messages.html"};

    private static volatile File root;

    private ExportBrowser() {
    }

    public static boolean canOpen(String folder) {
        return mainFile(folder) != null;
    }

    public static boolean open(Context context, String folder) {
        String main = mainFile(folder);
        if (main == null) {
            return false;
        }
        File directory;
        try {
            directory = new File(folder).getCanonicalFile();
        } catch (IOException e) {
            FileLog.e(e);
            return false;
        }
        root = directory;
        return Browser.openInTelegramBrowser(context, "https://" + HOST + "/" + Uri.encode(directory.getName()) + "/" + main, null);
    }

    public static boolean isExportUrl(String url) {
        return url != null && isExportUri(Uri.parse(url));
    }

    public static boolean isExportUri(Uri uri) {
        return uri != null && HOST.equalsIgnoreCase(uri.getHost());
    }

    public static WebResourceResponse intercept(Uri uri, Map<String, String> requestHeaders) {
        try {
            File file = resolve(uri);
            if (file == null) {
                return notFound();
            }
            long length = file.length();
            long start = 0;
            long end = length - 1;
            boolean partial = false;
            String range = header(requestHeaders, "Range");
            if (range != null && range.startsWith("bytes=")) {
                String[] parts = range.substring(6).split("-", 2);
                long from = parts[0].isEmpty() ? -1 : Utilities.parseLong(parts[0]);
                long till = parts.length < 2 || parts[1].isEmpty() ? -1 : Utilities.parseLong(parts[1]);
                if (from < 0 && till > 0) {
                    start = Math.max(0, length - till);
                } else if (from >= 0) {
                    start = from;
                    if (till >= from) {
                        end = Math.min(end, till);
                    }
                }
                partial = start <= end && start < length;
                if (!partial) {
                    start = 0;
                    end = length - 1;
                }
            }
            long count = Math.max(0, end - start + 1);
            FileInputStream input = new FileInputStream(file);
            if (start > 0) {
                input.getChannel().position(start);
            }
            Map<String, String> headers = new HashMap<>();
            headers.put("Accept-Ranges", "bytes");
            headers.put("Content-Length", String.valueOf(count));
            headers.put("Cache-Control", "no-store");
            if (partial) {
                headers.put("Content-Range", "bytes " + start + "-" + end + "/" + length);
            }
            String mime = mimeType(file.getName());
            String encoding = mime.startsWith("text/") || mime.endsWith("/json") || mime.endsWith("/javascript") ? "utf-8" : null;
            return new WebResourceResponse(mime, encoding, partial ? 206 : 200, partial ? "Partial Content" : "OK", headers, new BoundedStream(input, count));
        } catch (Exception e) {
            FileLog.e(e);
            return notFound();
        }
    }

    private static File resolve(Uri uri) throws IOException {
        File directory = root;
        if (directory == null) {
            ExportController.LastExport last = ExportController.getInstance(UserConfig.selectedAccount).getLastExport();
            if (last == null) {
                return null;
            }
            directory = new File(last.path()).getCanonicalFile();
            root = directory;
        }
        List<String> segments = uri.getPathSegments();
        if (segments.size() < 2 || !directory.getName().equals(segments.get(0))) {
            return null;
        }
        File file = new File(directory, TextUtils.join("/", segments.subList(1, segments.size()))).getCanonicalFile();
        if (!file.getPath().startsWith(directory.getPath() + File.separator) || !file.isFile()) {
            return null;
        }
        return file;
    }

    private static String mainFile(String folder) {
        if (TextUtils.isEmpty(folder)) {
            return null;
        }
        for (String name : MAIN_FILES) {
            if (new File(folder, name).isFile()) {
                return name;
            }
        }
        return null;
    }

    private static String header(Map<String, String> headers, String name) {
        if (headers == null) {
            return null;
        }
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (name.equalsIgnoreCase(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static String mimeType(String name) {
        int dot = name.lastIndexOf('.');
        String extension = dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
        switch (extension) {
            case "html":
            case "htm":
                return "text/html";
            case "css":
                return "text/css";
            case "js":
                return "text/javascript";
            case "json":
                return "application/json";
            case "tgs":
                return "application/x-tgsticker";
        }
        String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
        return mime != null ? mime : "application/octet-stream";
    }

    private static WebResourceResponse notFound() {
        return new WebResourceResponse("text/plain", "utf-8", 404, "Not Found", new HashMap<>(), new ByteArrayInputStream(new byte[0]));
    }

    private static final class BoundedStream extends FilterInputStream {

        private long left;

        BoundedStream(InputStream in, long count) {
            super(in);
            left = count;
        }

        @Override
        public int read() throws IOException {
            if (left <= 0) {
                return -1;
            }
            int value = super.read();
            if (value >= 0) {
                left--;
            }
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int count) throws IOException {
            if (left <= 0) {
                return -1;
            }
            int read = super.read(buffer, offset, (int) Math.min(count, left));
            if (read > 0) {
                left -= read;
            }
            return read;
        }
    }
}
