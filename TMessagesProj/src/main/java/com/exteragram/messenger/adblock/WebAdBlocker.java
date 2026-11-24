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
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;

import androidx.annotation.Keep;

import com.exteragram.messenger.adblock.backend.AdBlockManager;
import com.exteragram.messenger.adblock.interop.AdBlock;

import org.json.JSONObject;
import org.telegram.messenger.Utilities;

import java.util.concurrent.atomic.AtomicInteger;

public class WebAdBlocker {
    public static final int PAGE_BLOCKED_STATUS = 590;

    private static final String COSMETIC_SCRIPT = """
            (function() {
                var name = '$BRIDGE';
                var bridge = window[name];
                if (!bridge || window[name + '_c']) return;
                Object.defineProperty(window, name + '_c', {value: true});
                function addStyle(css) {
                    if (!css) return;
                    var style = document.createElement('style');
                    style.textContent = css;
                    (document.head || document.documentElement).appendChild(style);
                }
                addStyle($CSS);
                if (!$OBSERVE) return;
                var seenClasses = new Set(), seenIds = new Set(), classes = [], ids = [], scheduled = false;
                var delay = window.setTimeout.bind(window);
                function collect(element) {
                    var list = element.classList;
                    if (list) {
                        for (var i = 0; i < list.length; i++) {
                            if (!seenClasses.has(list[i])) {
                                seenClasses.add(list[i]);
                                classes.push(list[i]);
                            }
                        }
                    }
                    var id = element.id;
                    if (typeof id === 'string' && id && !seenIds.has(id)) {
                        seenIds.add(id);
                        ids.push(id);
                    }
                }
                function collectTree(node) {
                    if (!node || node.nodeType !== 1) return;
                    collect(node);
                    var elements = node.querySelectorAll('[class],[id]');
                    for (var i = 0; i < elements.length; i++) collect(elements[i]);
                }
                function flush() {
                    scheduled = false;
                    if (!classes.length && !ids.length) return;
                    var found = bridge.onElementsFound(classes.join(' '), ids.join(' '));
                    classes = [];
                    ids = [];
                    addStyle(found);
                }
                new MutationObserver(function(mutations) {
                    for (var i = 0; i < mutations.length; i++) {
                        var mutation = mutations[i];
                        if (mutation.type === 'attributes') {
                            collect(mutation.target);
                        } else {
                            for (var j = 0; j < mutation.addedNodes.length; j++) collectTree(mutation.addedNodes[j]);
                        }
                    }
                    if (!scheduled && (classes.length || ids.length)) {
                        scheduled = true;
                        delay(flush, 100);
                    }
                }).observe(document, {childList: true, subtree: true, attributes: true, attributeFilter: ['class', 'id']});
                collectTree(document.documentElement);
                flush();
            })();
            """;

    private final WebView webView;
    private final String bridgeName;
    private final AtomicInteger blockedCount = new AtomicInteger();
    private volatile String pageUrl;
    private volatile String previousPageUrl;
    private volatile int previousBlockedCount;
    private volatile boolean navigationPending;
    private volatile boolean allowBlockedPage;
    private volatile boolean pageBlocked;
    private volatile AdBlockClient.CosmeticHide cosmeticHide;

    public WebAdBlocker(WebView webView) {
        this.webView = webView;
        this.bridgeName = "_" + Long.toHexString(Utilities.fastRandom.nextLong());
        webView.addJavascriptInterface(this, bridgeName);
        AdBlockManager.initialize();
    }

    public int getBlockedCount() {
        return blockedCount.get();
    }

    public void allowBlockedPage() {
        allowBlockedPage = true;
    }

    public boolean consumePageBlocked() {
        var blocked = pageBlocked;
        pageBlocked = false;
        return blocked;
    }

    public void reload() {
        webView.reload();
    }

    public WebResourceResponse interceptRequest(WebResourceRequest request) {
        var url = request.getUrl().toString();
        if (request.isForMainFrame()) {
            if (!navigationPending) {
                navigationPending = true;
                previousPageUrl = pageUrl;
                previousBlockedCount = blockedCount.getAndSet(0);
            }
            pageUrl = url;
            var allowed = allowBlockedPage;
            allowBlockedPage = false;
            if (!allowed && AdBlockManager.isActive()) {
                var blockResult = AdBlockClient.isAdRequest(request, url);
                if (blockResult != null && blockResult.isMatched()) {
                    pageBlocked = true;
                    return new WebResourceResponse("plain/text", "utf-8", PAGE_BLOCKED_STATUS, "Page blocked", null, null);
                }
            }
            return null;
        }
        if (!AdBlockManager.isActive()) {
            return null;
        }
        var currentPageUrl = pageUrl;
        var sourceUrl = TextUtils.isEmpty(currentPageUrl) ? url : currentPageUrl;
        var requestType = AdBlockClient.getRequestType(request, sourceUrl);
        var blockResult = AdBlock.getBlockResult(url, sourceUrl, requestType);
        if (blockResult == null || !blockResult.isMatched()) {
            return null;
        }
        blockedCount.incrementAndGet();
        return AdBlockClient.createBlockedResponse(requestType, blockResult);
    }

    public void onPageStarted(String url) {
        navigationPending = false;
        previousPageUrl = null;
        pageUrl = url;
        cosmeticHide = null;
    }

    public void onDownloadStart() {
        if (navigationPending) {
            navigationPending = false;
            pageUrl = previousPageUrl;
            previousPageUrl = null;
            blockedCount.addAndGet(previousBlockedCount);
        }
    }

    public void injectCosmetics(String url) {
        if (url == null || !(url.startsWith("http://") || url.startsWith("https://")) || !AdBlockManager.isActive()) {
            return;
        }
        var currentHide = cosmeticHide;
        if (currentHide != null && TextUtils.equals(currentHide.getUrl(), url)) {
            return;
        }
        var hide = AdBlockClient.getCosmeticHide(url);
        if (hide == null) {
            return;
        }
        cosmeticHide = hide;
        var css = hide.getHideCss();
        if (css != null || !hide.isGenericHide()) {
            webView.evaluateJavascript(COSMETIC_SCRIPT
                    .replace("$BRIDGE", bridgeName)
                    .replace("$OBSERVE", String.valueOf(!hide.isGenericHide()))
                    .replace("$CSS", css != null ? JSONObject.quote(css) : "null"), null);
        }
        var injectedScript = hide.getInjectedScript();
        if (!TextUtils.isEmpty(injectedScript)) {
            var flag = "'" + bridgeName + "_s'";
            webView.evaluateJavascript("if (!window[" + flag + "]) {\nObject.defineProperty(window, " + flag + ", {value: true});\n" + injectedScript + "\n}", null);
        }
    }

    @Keep
    @JavascriptInterface
    public String onElementsFound(String classes, String ids) {
        var hide = cosmeticHide;
        if (hide == null || hide.isGenericHide() || !AdBlockManager.isActive()) {
            return null;
        }
        return AdBlockClient.getHiddenSelectorsCss(hide, split(classes), split(ids));
    }

    private static String[] split(String value) {
        return TextUtils.isEmpty(value) ? new String[0] : value.split(" ");
    }
}
