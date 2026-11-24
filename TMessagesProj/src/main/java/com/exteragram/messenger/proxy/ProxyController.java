/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.proxy;

import android.app.Activity;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.util.Base64;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.SharedConfig;
import org.telegram.tgnet.SerializedData;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.utils.proxy.ProxySettings;

import java.nio.charset.StandardCharsets;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.net.URLEncoder;
import java.net.InetAddress;
import java.net.IDN;
import java.util.Locale;

public final class ProxyController {

    public static final int PIN_ACTION_NONE = 0;
    public static final int PIN_ACTION_PIN = 1;
    public static final int PIN_ACTION_UNPIN = 2;

    public enum PinOperationResult {
        NO_CHANGE,
        CHANGED,
        LIMIT_REACHED
    }

    public interface ProxyCountryCallback {
        void onCountryResolved(String countryName);
    }

    private static final int MAX_PINNED_PROXIES = 10;
    private static final String PINNED_PROXIES = "proxy_pinned";
    private static final String PINNED_PROXIES_ORDER = "proxy_pinned_order";
    private static final String PROXY_NAMES = "proxy_names";
    private static final int PROXY_SERIALIZATION_VERSION = 5;

    private static final ProxyController INSTANCE = new ProxyController();

    private final ArrayList<SharedConfig.ProxyInfo> proxyList = new ArrayList<>();
    private final HashSet<String> pinnedProxies = new HashSet<>();
    private final ArrayList<String> pinnedProxyOrder = new ArrayList<>();
    private final HashMap<String, String> proxyNames = new HashMap<>();
    private SharedConfig.ProxyInfo currentProxy;
    private boolean loaded;

    public static ProxyController getInstance() {
        return INSTANCE;
    }

    private ProxyController() {
    }

    public synchronized ArrayList<SharedConfig.ProxyInfo> getProxyList() {
        ensureLoaded();
        return new ArrayList<>(proxyList);
    }

    public synchronized SharedConfig.ProxyInfo getCurrentProxy() {
        ensureLoaded();
        return currentProxy;
    }

    public synchronized void setCurrentProxy(SharedConfig.ProxyInfo proxyInfo) {
        ensureLoaded();
        currentProxy = proxyInfo;
        SharedConfig.currentProxy = proxyInfo;
    }

    public synchronized void loadProxyList() {
        ensureLoaded();
    }

    public synchronized void saveProxyList() {
        ensureLoaded();
        ArrayList<SharedConfig.ProxyInfo> infoToSerialize = new ArrayList<>(proxyList);
        Collections.sort(infoToSerialize, (o1, o2) -> {
            long bias1 = currentProxy == o1 ? -200000 : 0;
            if (!o1.available) {
                bias1 += 100000;
            }
            long bias2 = currentProxy == o2 ? -200000 : 0;
            if (!o2.available) {
                bias2 += 100000;
            }
            return Long.compare(o1.ping + bias1, o2.ping + bias2);
        });
        SerializedData serializedData = new SerializedData();
        serializedData.writeInt32(-1);
        serializedData.writeByte(PROXY_SERIALIZATION_VERSION);
        int count = infoToSerialize.size();
        serializedData.writeInt32(count);
        for (int a = count - 1; a >= 0; a--) {
            SharedConfig.ProxyInfo info = infoToSerialize.get(a);
            serializedData.writeString(info.settings.getAddress());
            serializedData.writeInt32(info.settings.getPort());
            serializedData.writeString(info.settings.getUser());
            serializedData.writeString(info.settings.getPassword());
            serializedData.writeString(info.settings.getSecret());
            serializedData.writeInt64(info.ping);
            serializedData.writeInt64(info.availableCheckTime);
            serializedData.writeBool(info.settings.getType() == ProxySettings.Type.WEB);
        }
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
        preferences.edit().putString("proxy_list", Base64.encodeToString(serializedData.toByteArray(), Base64.NO_WRAP)).apply();
        serializedData.cleanup();
    }

    public synchronized SharedConfig.ProxyInfo addProxy(SharedConfig.ProxyInfo proxyInfo) {
        ensureLoaded();
        int count = proxyList.size();
        for (int a = 0; a < count; a++) {
            SharedConfig.ProxyInfo info = proxyList.get(a);
            if (proxyInfo.settings.equals(info.settings)) {
                return info;
            }
        }
        proxyList.add(0, proxyInfo);
        syncProxyList();
        saveProxyList();
        return proxyInfo;
    }

    public synchronized SharedConfig.ProxyInfo saveProxy(SharedConfig.ProxyInfo proxyInfo, String previousProxyKey, String name) {
        ensureLoaded();
        if (!TextUtils.isEmpty(previousProxyKey) && !TextUtils.equals(previousProxyKey, proxyInfo.settings.getLink())) {
            moveMetadata(previousProxyKey, proxyInfo.settings.getLink());
        }
        SharedConfig.ProxyInfo storedProxy = addProxy(proxyInfo);
        if (name != null) {
            setName(storedProxy, name);
        }
        return storedProxy;
    }

    public synchronized String buildShareLink(SharedConfig.ProxyInfo proxyInfo) {
        return buildShareLink(proxyInfo, null);
    }

    public synchronized String buildShareLink(SharedConfig.ProxyInfo proxyInfo, String overrideName) {
        if (proxyInfo == null) {
            return "";
        }
        String link = proxyInfo.settings.getLink();
        String name = normalizeName(overrideName);
        if (TextUtils.isEmpty(name)) {
            name = getName(proxyInfo);
        }
        if (TextUtils.isEmpty(name)) {
            return link;
        }
        try {
            return link + "&title=" + URLEncoder.encode(name, "UTF-8");
        } catch (UnsupportedEncodingException ignored) {
            return link;
        }
    }

    public synchronized boolean isProxyEnabled() {
        ensureLoaded();
        return MessagesController.getGlobalMainSettings().getBoolean("proxy_enabled", false) && currentProxy != null;
    }

    public synchronized void deleteProxy(SharedConfig.ProxyInfo proxyInfo) {
        ensureLoaded();
        String proxyKey = getProxyKey(proxyInfo);
        boolean deletingCurrentProxy = currentProxy == proxyInfo
                || (proxyKey != null && proxyKey.equals(getProxyKey(currentProxy)));
        if (deletingCurrentProxy) {
            currentProxy = null;
            SharedConfig.currentProxy = null;
            SharedPreferences preferences = MessagesController.getGlobalMainSettings();
            boolean enabled = preferences.getBoolean("proxy_enabled", false);
            SharedPreferences.Editor editor = preferences.edit();
            ProxySettings.EMPTY.toSharedPreferences(editor);
            editor.putBoolean("proxy_enabled", false);
            editor.apply();
            if (enabled) {
                ConnectionsManager.setProxySettings(false, null);
            }
        }
        proxyList.remove(proxyInfo);
        removeProxy(proxyInfo);
        syncProxyList();
        saveProxyList();
    }

    public synchronized String getDisplayName(SharedConfig.ProxyInfo proxyInfo) {
        if (proxyInfo == null) {
            return "";
        }
        String name = getName(proxyInfo);
        if (!TextUtils.isEmpty(name)) {
            return name;
        }
        return proxyInfo.settings.getType() == ProxySettings.Type.WEB
                ? proxyInfo.settings.getAddress()
                : proxyInfo.settings.getAddress() + ":" + proxyInfo.settings.getPort();
    }

    public synchronized String getProxyTypeName(SharedConfig.ProxyInfo proxyInfo) {
        if (proxyInfo != null && proxyInfo.settings.getType() == ProxySettings.Type.WEB) {
            return LocaleController.getString(R.string.UseProxyWeb);
        }
        return isTelegramProxy(proxyInfo) ? LocaleController.getString(R.string.UseProxyTelegram) : LocaleController.getString(R.string.UseProxySocks5);
    }

    public void requestProxyCountry(SharedConfig.ProxyInfo proxyInfo, ProxyCountryCallback callback) {
        if (callback == null) {
            return;
        }
        final String unknownCountry = LocaleController.getString(R.string.Unknown);
        String proxyCountryKey = getProxyCountryKey(proxyInfo);
        if (TextUtils.isEmpty(proxyCountryKey)) {
            AndroidUtilities.runOnUIThread(() -> callback.onCountryResolved(unknownCountry));
            return;
        }
        Utilities.globalQueue.postRunnable(() -> {
            String ipAddress = resolveProxyIpAddress(proxyCountryKey);
            if (TextUtils.isEmpty(ipAddress)) {
                AndroidUtilities.runOnUIThread(() -> callback.onCountryResolved(unknownCountry));
                return;
            }
            IpAddressInfoController.requestIpAddressInfo(ipAddress, info -> {
                String countryName = info != null ? getCountryDisplayName(info.countryCode) : unknownCountry;
                AndroidUtilities.runOnUIThread(() -> callback.onCountryResolved(countryName));
            });
        });
    }

    public synchronized String getName(SharedConfig.ProxyInfo proxyInfo) {
        return proxyInfo == null ? "" : getName(getProxyKey(proxyInfo));
    }

    public synchronized String getName(String proxyKey) {
        ensureLoaded();
        if (TextUtils.isEmpty(proxyKey)) {
            return "";
        }
        String name = proxyNames.get(proxyKey);
        return name != null ? name : "";
    }

    public synchronized void setName(SharedConfig.ProxyInfo proxyInfo, String name) {
        setName(getProxyKey(proxyInfo), name);
    }

    public synchronized void setName(String proxyKey, String name) {
        ensureLoaded();
        if (TextUtils.isEmpty(proxyKey)) {
            return;
        }
        String normalizedName = normalizeName(name);
        boolean changed;
        if (TextUtils.isEmpty(normalizedName)) {
            changed = proxyNames.remove(proxyKey) != null;
        } else {
            String previousName = proxyNames.put(proxyKey, normalizedName);
            changed = !TextUtils.equals(previousName, normalizedName);
        }
        if (changed) {
            save();
        }
    }

    public synchronized void moveMetadata(String oldProxyKey, String newProxyKey) {
        ensureLoaded();
        if (TextUtils.isEmpty(oldProxyKey) || TextUtils.isEmpty(newProxyKey) || TextUtils.equals(oldProxyKey, newProxyKey)) {
            return;
        }
        boolean changed = false;
        String name = proxyNames.remove(oldProxyKey);
        if (!TextUtils.isEmpty(name)) {
            proxyNames.put(newProxyKey, name);
            changed = true;
        }
        int pinnedIndex = pinnedProxyOrder.indexOf(oldProxyKey);
        if (pinnedIndex >= 0) {
            pinnedProxyOrder.remove(pinnedIndex);
            pinnedProxies.remove(oldProxyKey);
            if (!pinnedProxyOrder.contains(newProxyKey)) {
                pinnedProxyOrder.add(Math.min(pinnedIndex, pinnedProxyOrder.size()), newProxyKey);
            }
            pinnedProxies.add(newProxyKey);
            changed = true;
        }
        if (changed) {
            save();
        }
    }

    public synchronized boolean isPinned(SharedConfig.ProxyInfo proxyInfo) {
        return proxyInfo != null && isPinned(getProxyKey(proxyInfo));
    }

    public synchronized boolean isPinned(String proxyKey) {
        ensureLoaded();
        return !TextUtils.isEmpty(proxyKey) && pinnedProxies.contains(proxyKey);
    }

    public synchronized int getPinnedIndex(SharedConfig.ProxyInfo proxyInfo) {
        ensureLoaded();
        if (proxyInfo == null) {
            return -1;
        }
        String proxyKey = getProxyKey(proxyInfo);
        return TextUtils.isEmpty(proxyKey) ? -1 : pinnedProxyOrder.indexOf(proxyKey);
    }

    public synchronized int getPinnedCount() {
        ensureLoaded();
        return pinnedProxyOrder.size();
    }

    public synchronized int getSelectedPinAction(List<SharedConfig.ProxyInfo> selectedItems) {
        ensureLoaded();
        boolean hasPinned = false;
        boolean hasUnpinned = false;
        for (SharedConfig.ProxyInfo proxyInfo : selectedItems) {
            if (isPinned(proxyInfo)) {
                hasPinned = true;
            } else {
                hasUnpinned = true;
            }
            if (hasPinned && hasUnpinned) {
                return PIN_ACTION_NONE;
            }
        }
        if (hasPinned) {
            return PIN_ACTION_UNPIN;
        }
        if (hasUnpinned) {
            return PIN_ACTION_PIN;
        }
        return PIN_ACTION_NONE;
    }

    public synchronized PinOperationResult applySelectedPinAction(List<SharedConfig.ProxyInfo> selectedItems) {
        ensureLoaded();
        int pinAction = getSelectedPinAction(selectedItems);
        if (pinAction == PIN_ACTION_NONE) {
            return PinOperationResult.NO_CHANGE;
        }
        boolean changed = false;
        if (pinAction == PIN_ACTION_PIN) {
            ArrayList<String> keysToPin = new ArrayList<>();
            for (SharedConfig.ProxyInfo proxyInfo : selectedItems) {
                String proxyKey = getProxyKey(proxyInfo);
                if (!TextUtils.isEmpty(proxyKey) && !pinnedProxies.contains(proxyKey) && !keysToPin.contains(proxyKey)) {
                    keysToPin.add(proxyKey);
                }
            }
            if (pinnedProxyOrder.size() + keysToPin.size() > MAX_PINNED_PROXIES) {
                return PinOperationResult.LIMIT_REACHED;
            }
            for (String proxyKey : keysToPin) {
                if (pinnedProxies.add(proxyKey)) {
                    pinnedProxyOrder.add(proxyKey);
                    changed = true;
                }
            }
        } else {
            for (SharedConfig.ProxyInfo proxyInfo : selectedItems) {
                String proxyKey = getProxyKey(proxyInfo);
                if (TextUtils.isEmpty(proxyKey)) {
                    continue;
                }
                if (pinnedProxies.remove(proxyKey)) {
                    changed = true;
                }
                if (pinnedProxyOrder.remove(proxyKey)) {
                    changed = true;
                }
            }
        }
        if (changed) {
            save();
            return PinOperationResult.CHANGED;
        }
        return PinOperationResult.NO_CHANGE;
    }

    public synchronized boolean movePinnedProxy(SharedConfig.ProxyInfo fromProxyInfo, SharedConfig.ProxyInfo toProxyInfo) {
        ensureLoaded();
        String fromProxyKey = getProxyKey(fromProxyInfo);
        String toProxyKey = getProxyKey(toProxyInfo);
        if (TextUtils.isEmpty(fromProxyKey) || TextUtils.isEmpty(toProxyKey) || TextUtils.equals(fromProxyKey, toProxyKey)) {
            return false;
        }
        if (!pinnedProxies.contains(fromProxyKey) || !pinnedProxies.contains(toProxyKey)) {
            return false;
        }
        int fromPinnedIndex = pinnedProxyOrder.indexOf(fromProxyKey);
        int toPinnedIndex = pinnedProxyOrder.indexOf(toProxyKey);
        if (fromPinnedIndex < 0 || toPinnedIndex < 0) {
            return false;
        }
        pinnedProxyOrder.remove(fromPinnedIndex);
        pinnedProxyOrder.add(toPinnedIndex, fromProxyKey);
        save();
        return true;
    }

    public synchronized void removeProxy(SharedConfig.ProxyInfo proxyInfo) {
        ensureLoaded();
        String proxyKey = getProxyKey(proxyInfo);
        if (TextUtils.isEmpty(proxyKey)) {
            return;
        }
        boolean changed = proxyNames.remove(proxyKey) != null;
        changed |= pinnedProxies.remove(proxyKey);
        changed |= pinnedProxyOrder.remove(proxyKey);
        if (changed) {
            save();
        }
    }

    public synchronized void clearAll() {
        ensureLoaded();
        if (pinnedProxies.isEmpty() && pinnedProxyOrder.isEmpty() && proxyNames.isEmpty()) {
            return;
        }
        pinnedProxies.clear();
        pinnedProxyOrder.clear();
        proxyNames.clear();
        save();
    }

    public synchronized void sortProxyList(List<SharedConfig.ProxyInfo> proxyList, boolean isChecking, SharedConfig.ProxyInfo currentProxy) {
        ensureLoaded();
        final ArrayList<SharedConfig.ProxyInfo> originalOrder = new ArrayList<>(proxyList);
        Collections.sort(proxyList, (o1, o2) -> {
            int pinnedIndex1 = getPinnedIndex(o1);
            int pinnedIndex2 = getPinnedIndex(o2);
            boolean pinned1 = pinnedIndex1 >= 0;
            boolean pinned2 = pinnedIndex2 >= 0;
            if (pinned1 != pinned2) {
                return pinned1 ? -1 : 1;
            }
            if (pinned1) {
                return Integer.compare(pinnedIndex1, pinnedIndex2);
            }
            long bias1 = currentProxy == o1 ? -200000 : 0;
            if (!o1.available) {
                bias1 += 100000;
            }
            long bias2 = currentProxy == o2 ? -200000 : 0;
            if (!o2.available) {
                bias2 += 100000;
            }
            return Long.compare(isChecking && o1 != currentProxy ? originalOrder.indexOf(o1) * 10000L : o1.ping + bias1,
                    isChecking && o2 != currentProxy ? originalOrder.indexOf(o2) * 10000L : o2.ping + bias2);
        });
    }

    public int getMaxPinnedProxies() {
        return MAX_PINNED_PROXIES;
    }

    private void ensureLoaded() {
        if (!loaded) {
            load();
        }
    }

    private void load() {
        proxyList.clear();
        currentProxy = null;
        pinnedProxies.clear();
        pinnedProxyOrder.clear();
        proxyNames.clear();
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
        ProxySettings proxySettings = ProxySettings.fromSharedPreferences(preferences);
        boolean hasProxySettings = proxySettings.isValid();

        String list = preferences.getString("proxy_list", null);
        boolean migratedLegacyNames = false;
        if (!TextUtils.isEmpty(list)) {
            byte[] bytes = Base64.decode(list, Base64.DEFAULT);
            SerializedData data = new SerializedData(bytes);
            int count = data.readInt32(false);
            if (count == -1) {
                int version = data.readByte(false);

                if (version == PROXY_SERIALIZATION_VERSION) {
                    count = data.readInt32(false);
                    for (int i = 0; i < count; i++) {
                        final SharedConfig.ProxyInfo info = readProxyInfo(data);
                        info.ping = data.readInt64(false);
                        info.availableCheckTime = SharedConfig.ProxyInfo.normalizeAvailableCheckTime(data.readInt64(false));
                        if (data.readBool(false)) {
                            info.settings = ProxySettings.builder()
                                    .setType(ProxySettings.Type.WEB)
                                    .setAddress(info.settings.getAddress())
                                    .setPort(info.settings.getPort())
                                    .setSecret(info.settings.getSecret())
                                    .build();
                        }

                        proxyList.add(0, info);
                        if (currentProxy == null && hasProxySettings) {
                            if (proxySettings.equals(info.settings)) {
                                currentProxy = info;
                            }
                        }
                    }
                } else if (version == 3) {
                    count = data.readInt32(false);
                    for (int i = 0; i < count; i++) {
                        String proxyName = data.readString(false);
                        final SharedConfig.ProxyInfo info = readProxyInfo(data);

                        info.ping = data.readInt64(false);
                        info.availableCheckTime = SharedConfig.ProxyInfo.normalizeAvailableCheckTime(data.readInt64(false));

                        proxyList.add(0, info);
                        String proxyKey = getProxyKey(info);
                        String normalizedName = normalizeName(proxyName);
                        if (!TextUtils.isEmpty(proxyKey) && !TextUtils.isEmpty(normalizedName)) {
                            proxyNames.put(proxyKey, normalizedName);
                            migratedLegacyNames = true;
                        }
                        if (currentProxy == null && hasProxySettings) {
                            if (proxySettings.equals(info.settings)) {
                                currentProxy = info;
                            }
                        }
                    }
                } else if (version == 4 || version == 2) {
                    count = data.readInt32(false);
                    for (int i = 0; i < count; i++) {
                        final SharedConfig.ProxyInfo info = readProxyInfo(data);

                        info.ping = data.readInt64(false);
                        info.availableCheckTime = SharedConfig.ProxyInfo.normalizeAvailableCheckTime(data.readInt64(false));

                        proxyList.add(0, info);
                        if (currentProxy == null && hasProxySettings) {
                            if (proxySettings.equals(info.settings)) {
                                currentProxy = info;
                            }
                        }
                    }
                } else {
                    FileLog.e("Unknown proxy schema version: " + version);
                }
            } else {
                for (int i = 0; i < count; i++) {
                    final SharedConfig.ProxyInfo info = readProxyInfo(data);
                    proxyList.add(0, info);
                    if (currentProxy == null && hasProxySettings) {
                        if (proxySettings.equals(info.settings)) {
                            currentProxy = info;
                        }
                    }
                }
            }
            data.cleanup();
        }
        if (currentProxy == null && hasProxySettings) {
            currentProxy = new SharedConfig.ProxyInfo(proxySettings);
            proxyList.add(0, currentProxy);
        }
        String orderedKeys = preferences.getString(PINNED_PROXIES_ORDER, preferences.getString(PINNED_PROXIES + "_links_order", null));
        if (!TextUtils.isEmpty(orderedKeys)) {
            String[] keysArray = orderedKeys.split("\\n");
            for (String key : keysArray) {
                if (!TextUtils.isEmpty(key) && pinnedProxies.add(key)) {
                    pinnedProxyOrder.add(key);
                }
            }
        }
        java.util.Set<String> keys = preferences.getStringSet(PINNED_PROXIES, preferences.getStringSet(PINNED_PROXIES + "_links", null));
        if (keys != null) {
            for (String key : keys) {
                if (!TextUtils.isEmpty(key) && pinnedProxies.add(key)) {
                    pinnedProxyOrder.add(key);
                }
            }
        }
        String encodedNames = preferences.getString(PROXY_NAMES, null);
        if (!TextUtils.isEmpty(encodedNames)) {
            String[] lines = encodedNames.split("\\n");
            for (String line : lines) {
                if (TextUtils.isEmpty(line)) {
                    continue;
                }
                int separator = line.indexOf('\t');
                if (separator <= 0 || separator >= line.length() - 1) {
                    continue;
                }
                String key = decode(line.substring(0, separator));
                String name = decode(line.substring(separator + 1));
                if (!TextUtils.isEmpty(key) && !TextUtils.isEmpty(name)) {
                    proxyNames.put(key, name);
                }
            }
        }
        loaded = true;
        if (migratedLegacyNames) {
            save();
        }
        syncProxyList();
    }

    private void save() {
        SharedPreferences.Editor editor = MessagesController.getGlobalMainSettings().edit();
        editor.putStringSet(PINNED_PROXIES, new HashSet<>(pinnedProxies));
        if (pinnedProxyOrder.isEmpty()) {
            editor.remove(PINNED_PROXIES_ORDER);
        } else {
            editor.putString(PINNED_PROXIES_ORDER, TextUtils.join("\n", pinnedProxyOrder));
        }
        editor.remove(PINNED_PROXIES + "_links");
        editor.remove(PINNED_PROXIES + "_links_order");
        if (proxyNames.isEmpty()) {
            editor.remove(PROXY_NAMES);
        } else {
            ArrayList<String> encodedNames = new ArrayList<>(proxyNames.size());
            for (Map.Entry<String, String> entry : proxyNames.entrySet()) {
                encodedNames.add(encode(entry.getKey()) + "\t" + encode(entry.getValue()));
            }
            editor.putString(PROXY_NAMES, TextUtils.join("\n", encodedNames));
        }
        editor.apply();
    }

    private String getProxyKey(SharedConfig.ProxyInfo proxyInfo) {
        return proxyInfo == null || TextUtils.isEmpty(proxyInfo.settings.getLink()) ? null : proxyInfo.settings.getLink();
    }

    private static String normalizeName(String name) {
        return name == null ? "" : name.trim();
    }

    private static String encode(String value) {
        return Base64.encodeToString((value != null ? value : "").getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
    }

    private static String decode(String value) {
        return new String(Base64.decode(value, Base64.NO_WRAP), StandardCharsets.UTF_8);
    }

    private void syncProxyList() {
        SharedConfig.proxyList.clear();
        SharedConfig.proxyList.addAll(proxyList);
        SharedConfig.currentProxy = currentProxy;
    }

    private static String resolveProxyIpAddress(String proxyCountryKey) {
        InetAddress inetAddress;
        try {
            inetAddress = InetAddress.getByName(IDN.toASCII(proxyCountryKey));
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        }
        if (inetAddress.isAnyLocalAddress() || inetAddress.isLoopbackAddress() || inetAddress.isLinkLocalAddress() || inetAddress.isSiteLocalAddress()) {
            return null;
        }
        return inetAddress.getHostAddress();
    }

    private static String getProxyCountryKey(SharedConfig.ProxyInfo proxyInfo) {
        return proxyInfo == null ? "" : normalizeProxyCountryKey(proxyInfo.settings.getAddress());
    }

    private static String normalizeProxyCountryKey(String proxyCountryKey) {
        if (TextUtils.isEmpty(proxyCountryKey)) {
            return "";
        }
        String normalized = proxyCountryKey.trim();
        if (normalized.startsWith("[") && normalized.endsWith("]") && normalized.length() > 2) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        return normalized.toLowerCase(Locale.US);
    }

    private static String getCountryDisplayName(String countryCode) {
        if (TextUtils.isEmpty(countryCode)) {
            return "";
        }
        Locale displayLocale = LocaleController.getInstance().getCurrentLocale();
        if (displayLocale == null) {
            displayLocale = Locale.getDefault();
        }
        String displayName = new Locale("", countryCode.toUpperCase(Locale.US)).getDisplayCountry(displayLocale);
        return TextUtils.isEmpty(displayName) ? LocaleController.getString(R.string.Unknown) : displayName;
    }

    private static SharedConfig.ProxyInfo readProxyInfo(SerializedData data) {
        final String address = data.readString(false);
        final int port = data.readInt32(false);
        final String user = data.readString(false);
        final String password = data.readString(false);
        final String secret = data.readString(false);
        return new SharedConfig.ProxyInfo(buildSettings(address, port, user, password, secret));
    }

    private static ProxySettings buildSettings(String address, int port, String user, String password, String secret) {
        return ProxySettings.builder()
                .setType(TextUtils.isEmpty(secret) ? ProxySettings.Type.SOCKS5 : ProxySettings.Type.MTPROTO)
                .setAddress(address)
                .setPort(port)
                .setUser(user)
                .setPassword(password)
                .setSecret(secret)
                .build();
    }

    private static boolean isTelegramProxy(SharedConfig.ProxyInfo proxyInfo) {
        return proxyInfo != null && !TextUtils.isEmpty(proxyInfo.settings.getSecret());
    }
}
