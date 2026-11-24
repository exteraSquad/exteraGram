/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.adblock.data;

public class FilterListMetadata {
    private final String homepage;
    private final String title;
    private final String redirect;
    private final Integer expires;

    public FilterListMetadata(String homepage, String title, String redirect, int expires) {
        this.homepage = homepage;
        this.title = title;
        this.redirect = redirect;
        this.expires = expires == 0 ? null : expires;
    }

    public String getHomepage() {
        return homepage;
    }

    public String getTitle() {
        return title;
    }

    public String getRedirect() {
        return redirect;
    }

    public Integer getExpires() {
        return expires;
    }
}
