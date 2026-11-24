/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.pillstack.core;

import static com.exteragram.messenger.pillstack.core.PillStackConfig.getActivePills;
import static com.exteragram.messenger.pillstack.core.PillStackConfig.getConfigLoaded;
import static com.exteragram.messenger.pillstack.core.PillStackConfig.getHiddenPills;

import android.content.Context;

import androidx.annotation.Keep;

import com.exteragram.messenger.pillstack.custom.CustomPillStore;
import com.exteragram.messenger.pillstack.ui.pills.BasePill;
import com.exteragram.messenger.pillstack.ui.pills.crypto.BtcPill;
import com.exteragram.messenger.pillstack.ui.pills.crypto.GramPill;
import com.exteragram.messenger.pillstack.ui.pills.crypto.UsdPill;
import com.exteragram.messenger.pillstack.ui.pills.system.CachePill;
import com.exteragram.messenger.pillstack.ui.pills.system.ProxyPill;
import com.exteragram.messenger.pillstack.ui.pills.weather.WeatherPill;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.IconBackgroundColors;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public class PillRegistry {

    public interface PillCreator {
        BasePill create(Context context, Theme.ResourcesProvider resourcesProvider);
    }

    public record PillInfo(int id, CharSequence name, int iconRes, int iconColorTop,
                           int iconColorBottom, PillCreator creator) {
    }

    private static final Map<Integer, PillInfo> registry = new LinkedHashMap<>();
    private static boolean batchRegistration;

    static {
        beginTransaction();
        registerDefaultPills();
        endTransaction();
    }

    @Keep
    public static void beginTransaction() {
        batchRegistration = true;
    }

    @Keep
    public static void endTransaction() {
        batchRegistration = false;
        if (getConfigLoaded()) {
            PillStackConfig.sanitizePills();
            AndroidUtilities.runOnUIThread(() -> NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.pillStackLayoutChanged));
        }
    }

    private static void registerDefaultPills() {
        register(new PillInfo(PillType.WEATHER.getId(), LocaleController.getString(R.string.WeatherPill), R.drawable.weather_cloudy, IconBackgroundColors.BLUE_ALT.top, IconBackgroundColors.BLUE_ALT.bottom, WeatherPill::new));
        register(new PillInfo(PillType.GRAM.getId(), "GRAM", R.drawable.settings_gram_24, IconBackgroundColors.BLUE_LIGHT.top, IconBackgroundColors.BLUE_LIGHT.bottom, GramPill::new));
        register(new PillInfo(PillType.BTC.getId(), "BTC", R.drawable.pillstack_btc_settings, IconBackgroundColors.ORANGE_BRIGHT.top, IconBackgroundColors.ORANGE_BRIGHT.bottom, BtcPill::new));
        register(new PillInfo(PillType.USD.getId(), "USD", R.drawable.pillstack_usd_settings, IconBackgroundColors.GREEN_DEEP.top, IconBackgroundColors.GREEN_DEEP.bottom, UsdPill::new));
        register(new PillInfo(PillType.CACHE.getId(), LocaleController.getString(R.string.StorageUsage), R.drawable.msg_filled_storageusage, IconBackgroundColors.BLUE_DEEP.top, IconBackgroundColors.BLUE_DEEP.bottom, CachePill::new));
        register(new PillInfo(PillType.PROXY.getId(), LocaleController.getString(R.string.Proxy), R.drawable.drawer_proxy_on, IconBackgroundColors.GREEN.top, IconBackgroundColors.GREEN.bottom, ProxyPill::new));
        CustomPillStore.registerAll();
    }

    public static void register(PillInfo info) {
        registry.put(info.id, info);
        if (!batchRegistration) {
            PillStackConfig.sanitizePills();
            AndroidUtilities.runOnUIThread(() -> NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.pillStackLayoutChanged));
        }
    }

    @Keep
    public static void activatePill(int id) {
        if (!isRegistered(id)) return;
        if (!getActivePills().contains(id)) {
            getHiddenPills().remove(Integer.valueOf(id));
            getActivePills().add(id);
            PillStackConfig.savePillsLayout();
            AndroidUtilities.runOnUIThread(() -> NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.pillStackLayoutChanged));
        }
    }

    public static PillInfo getPillInfo(int id) {
        return registry.get(id);
    }

    public static Collection<PillInfo> getRegisteredPills() {
        return registry.values();
    }

    public static boolean isRegistered(int id) {
        return registry.containsKey(id);
    }

    @Keep
    public static void unregister(int id) {
        if (registry.remove(id) != null && !batchRegistration) {
            PillStackConfig.sanitizePills();
            AndroidUtilities.runOnUIThread(() -> NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.pillStackLayoutChanged));
        }
    }
}
