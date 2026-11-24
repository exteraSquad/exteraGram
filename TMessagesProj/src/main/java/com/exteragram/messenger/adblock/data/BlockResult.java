/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.adblock.data;

public class BlockResult {
    private final boolean matched;
    private final boolean important;
    private final String redirect;
    private final String rewrittenUrl;
    private final String exception;
    private final String filter;

    public BlockResult(boolean matched, boolean important, String redirect,
                       String rewrittenUrl, String exception, String filter) {
        this.matched = matched;
        this.important = important;
        this.redirect = redirect;
        this.rewrittenUrl = rewrittenUrl;
        this.exception = exception;
        this.filter = filter;
    }

    public boolean isMatched() {
        return matched;
    }

    public boolean isImportant() {
        return important;
    }

    public String getRedirect() {
        return redirect;
    }

    public String getRewrittenUrl() {
        return rewrittenUrl;
    }

    public String getException() {
        return exception;
    }

    public String getFilter() {
        return filter;
    }
}
