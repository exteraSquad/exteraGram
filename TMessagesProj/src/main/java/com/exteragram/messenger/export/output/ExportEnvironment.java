/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.export.output;

public final class ExportEnvironment {

    public static final String INTERNAL_LINKS_DOMAIN = "https://t.me/";

    public static final String ABOUT_TELEGRAM = "Here is the data you requested. Remember: Telegram is ad free, it doesn't use your data for ad targeting and doesn't sell it to others. Telegram only keeps the information it needs to function as a secure and feature-rich cloud service.\n\nCheck out Settings > Privacy & Security on Telegram's mobile apps for the relevant settings.";
    public static final String ABOUT_CONTACTS = "If you allow access, Telegram continuously synchronizes your contacts, so that you can connect with your friends on all your devices. You can disable syncing or delete your stored contacts in Settings > Privacy & Security on Telegram's mobile apps.";
    public static final String ABOUT_FREQUENT = "This rating shows which people you are likely to message frequently. Telegram uses this data to populate the 'People' box at the top of the Search section. The rating is also calculated for inline bots so that the app can suggest you the bots you are most likely to use in the attachment menu (or when you start a new message with \"@\").\n\nTo delete this data, go to Settings > Privacy & Security > Data Settings and disable 'Suggest Frequent Contacts'.";
    public static final String ABOUT_SESSIONS = "We store this to display your connected devices in Settings > Privacy & Security > Active Sessions. Terminating a session removes this data from Telegram servers.";
    public static final String ABOUT_WEB_SESSIONS = "We store this to display you the websites where you logged in using Telegram. Terminating a session removes this data from Telegram servers.";
    public static final String ABOUT_CHATS = "This page lists all chats from this export.";
    public static final String ABOUT_LEFT_CHATS = "Below are the supergroups and channels from this export that you've left or where you were banned.\n\nNote that when you leave a channel or supergroup you've created, you have the option to either delete it, or simply leave (in case you want to rejoin later, or keep the community alive despite not being a member).";

    private ExportEnvironment() {
    }
}
