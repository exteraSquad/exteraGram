/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.adblock;

import android.text.TextUtils;
import android.util.Base64;
import android.webkit.MimeTypeMap;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;

import com.exteragram.messenger.adblock.data.BlockResult;
import com.exteragram.messenger.adblock.interop.AdBlock;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Locale;

public class AdBlockClient {
    private static final IOException BLOCKED_EXCEPTION = new IOException("Blocked by filter") {
        @Override
        public synchronized Throwable fillInStackTrace() {
            return this;
        }
    };

    public static CosmeticHide getCosmeticHide(String url) {
        var result = AdBlock.getCosmeticResources(url);
        if (result == null) {
            return null;
        }
        return new CosmeticHide(url, createHideCss(result.getHideSelectors()), result.getInjectedScript(), result.getExceptions(), result.isGenericHide());
    }

    public static String getHiddenSelectorsCss(CosmeticHide cosmeticHide, String[] classes, String[] ids) {
        if (classes.length == 0 && ids.length == 0) {
            return null;
        }
        return createHideCss(AdBlock.getHiddenSelectors(classes, ids, cosmeticHide.getExceptions()));
    }

    public static BlockResult isAdRequest(WebResourceRequest request, String currentUrl) {
        var type = getRequestType(request, currentUrl);
        return AdBlock.getBlockResult(request.getUrl().toString(), currentUrl, type);
    }

    public static WebResourceResponse createBlockedResponse(String requestType, BlockResult blockResult) {
        var redirect = blockResult.getRedirect();
        if (redirect != null && redirect.startsWith("data:")) {
            int mimeEnd = redirect.indexOf(';');
            int dataStart = redirect.indexOf(',');
            if (mimeEnd > 5 && dataStart > mimeEnd) {
                try {
                    var mime = redirect.substring(5, mimeEnd);
                    var data = Base64.decode(redirect.substring(dataStart + 1), Base64.DEFAULT);

                    var headers = new HashMap<String, String>();
                    headers.put("Content-Type", mime);
                    headers.put("Access-Control-Allow-Credentials", "true");
                    headers.put("Access-Control-Allow-Headers", "Cache-Control");
                    headers.put("Access-Control-Allow-Origin", "*");

                    return new WebResourceResponse(mime, null, 200, "OK", headers, new ByteArrayInputStream(data));
                } catch (IllegalArgumentException ignore) {
                }
            }
        }
        if ("sub_frame".equals(requestType)) {
            return new WebResourceResponse("text/html", "utf-8", 500, "Internal Server Error", null, null);
        }
        return new WebResourceResponse("text/plain", "utf-8", new BlockedInputStream());
    }

    public static String getRequestType(WebResourceRequest request, String currentUrl) {
        if ("OPTIONS".equals(request.getMethod())) {
            return "beacon";
        }

        var requestUrl = request.getUrl().toString();
        var headers = request.getRequestHeaders();

        if (request.isForMainFrame() && requestUrl.equals(currentUrl)) {
            return "main_frame";
        }

        if (requestUrl.startsWith("ws")) {
            return "websocket";
        }

        if (headers != null && "XMLHttpRequest".equals(headers.get("X-Requested-With"))) {
            return "xhr";
        }

        var accept = headers != null ? headers.get("Accept") : null;
        if (!request.isForMainFrame() && accept != null && accept.startsWith("text/html")) {
            return "sub_frame";
        }

        var ext = getRequestExtension(requestUrl);
        if ("js".equals(ext)) {
            return "script";
        }
        if ("css".equals(ext)) {
            return "stylesheet";
        }
        if ("otf".equals(ext) || "ttf".equals(ext) || "ttc".equals(ext) || "woff".equals(ext) || "woff2".equals(ext)) {
            return "font";
        }
        if (!"php".equals(ext)) {
            var mime = getRequestMime(ext);
            if (!"application/octet-stream".equals(mime)) {
                return getRequestTypeFromMime(mime);
            }
        }

        if (TextUtils.isEmpty(accept) || "*/*".equals(accept)) {
            return "other";
        }

        int indexOf = accept.indexOf(',');
        if (indexOf > 0) {
            accept = accept.substring(0, indexOf).trim();
        }

        return getRequestTypeFromMime(accept);
    }

    private static String getRequestExtension(String url) {
        if (url == null || url.isEmpty()) {
            return null;
        }
        int indexOf = url.indexOf('?');
        if (indexOf > 0) {
            url = url.substring(0, indexOf);
        }
        int lastIndexOf = url.lastIndexOf('/');
        if (lastIndexOf > 0) {
            url = url.substring(lastIndexOf + 1);
        }
        int indexOf2 = url.lastIndexOf('.');
        if (indexOf2 <= 0 || indexOf2 == url.length() - 1) {
            if (url.endsWith("js")) {
                return "js";
            }
            return null;
        }
        String lowerCase = url.substring(indexOf2 + 1).toLowerCase(Locale.ROOT);
        if (lowerCase.isEmpty()) {
            return null;
        }
        if (lowerCase.length() > 8) {
            return null;
        }
        return lowerCase;
    }

    /*

        "beacon" => RequestType::Ping,
        "csp_report" => RequestType::Csp,
        "document" | "main_frame" => RequestType::Document,
        "font" => RequestType::Font,
        "image" | "imageset" => RequestType::Image,
        "media" => RequestType::Media,
        "object" | "object_subrequest" => RequestType::Object,
        "ping" => RequestType::Ping,
        "script" => RequestType::Script,
        "stylesheet" => RequestType::Stylesheet,
        "sub_frame" | "subdocument" => RequestType::Subdocument,
        "websocket" => RequestType::Websocket,
        "xhr" | "xmlhttprequest" => RequestType::Xmlhttprequest,
        "other" => RequestType::Other,
        "speculative" => RequestType::Other,
        "web_manifest" => RequestType::Other,
        "xbl" => RequestType::Other,
        "xml_dtd" => RequestType::Other,
        "xslt" => RequestType::Other,
        _ => RequestType::Other,

     */

    private static String getRequestMime(String extension) {
        if ("mhtml".equals(extension) || "mht".equals(extension)) {
            return "multipart/related";
        }
        if ("json".equals(extension)) {
            return "application/json";
        }
        String mimeTypeFromExtension = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
        return (mimeTypeFromExtension == null || mimeTypeFromExtension.isEmpty()) ? "application/octet-stream" : mimeTypeFromExtension;
    }

    private static String getRequestTypeFromMime(String mime) {
        if (TextUtils.isEmpty(mime)) {
            return "other";
        }
        if ("application/javascript".equals(mime) || "application/x-javascript".equals(mime) || "text/javascript".equals(mime) || "application/json".equals(mime)) {
            return "script";
        }
        if ("text/css".equals(mime)) {
            return "stylesheet";
        }
        if (mime.startsWith("image/")) {
            return "image";
        }
        if (mime.startsWith("video/") || mime.startsWith("audio/")) {
            return "media";
        }
        if (mime.startsWith("font/")) {
            return "font";
        }
        return "other";
    }

    private static String createHideCss(String[] selectors) {
        if (selectors == null || selectors.length == 0) {
            return null;
        }

        var css = new StringBuilder();
        for (var selector : selectors) {
            if (!TextUtils.isEmpty(selector) && selector.indexOf('{') < 0 && selector.indexOf('}') < 0) {
                css.append(selector).append("{display:none!important}\n");
            }
        }
        return css.length() > 0 ? css.toString() : null;
    }

    private static class BlockedInputStream extends InputStream {
        @Override
        public int available() throws IOException {
            throw BLOCKED_EXCEPTION;
        }

        @Override
        public int read() throws IOException {
            throw BLOCKED_EXCEPTION;
        }
    }

    public static class CosmeticHide {
        private final String url;
        private final String hideCss;
        private final String injectedScript;
        private final String[] exceptions;
        private final boolean genericHide;

        public CosmeticHide(String url, String hideCss, String injectedScript, String[] exceptions, boolean genericHide) {
            this.url = url;
            this.hideCss = hideCss;
            this.injectedScript = injectedScript;
            this.exceptions = exceptions;
            this.genericHide = genericHide;
        }

        public String getUrl() {
            return url;
        }

        public String getHideCss() {
            return hideCss;
        }

        public String getInjectedScript() {
            return injectedScript;
        }

        public String[] getExceptions() {
            return exceptions;
        }

        public boolean isGenericHide() {
            return genericHide;
        }
    }
}
