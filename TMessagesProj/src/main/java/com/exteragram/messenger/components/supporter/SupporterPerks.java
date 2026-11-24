/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.components.supporter;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.view.View;

import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.Theme;

public final class SupporterPerks {

    private SupporterPerks() {
    }

    public static void addTo(Context context, Utilities.Callback<View> add, Theme.ResourcesProvider resourcesProvider, Runnable onBadgeClick, Runnable onNowPlayingClick, Runnable onEarlyBuildsClick) {
        SupporterFeatureCell badge = new SupporterFeatureCell(context, R.drawable.menu_supporter_badge, getString(R.string.SupporterPerkBadge), getString(R.string.SupporterPerkBadgeInfo), resourcesProvider);
        badge.setAction(onBadgeClick);
        add.run(badge);
        SupporterFeatureCell nowPlaying = new SupporterFeatureCell(context, R.drawable.msg_voice_headphones, getString(R.string.SupporterPerkNowPlaying), getString(R.string.SupporterPerkNowPlayingInfo), resourcesProvider);
        nowPlaying.setAction(onNowPlayingClick);
        add.run(nowPlaying);
        add.run(new SupporterFeatureCell(context, R.drawable.msg_calendar2, getString(R.string.SupporterPerkRegDate), getString(R.string.SupporterPerkRegDateInfo), resourcesProvider));
        SupporterFeatureCell earlyBuilds = new SupporterFeatureCell(context, R.drawable.msg_channel, getString(R.string.SupporterPerkEarlyBuilds), getString(R.string.SupporterPerkEarlyBuildsInfo), resourcesProvider);
        earlyBuilds.setAction(onEarlyBuildsClick);
        add.run(earlyBuilds);
    }
}
