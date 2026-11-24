/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.adblock.data;

public class UrlCosmeticResources {
    private final String[] hideSelectors;
    private final String[] proceduralActions;
    private final String[] exceptions;
    private final String injectedScript;
    private final boolean genericHide;

    public UrlCosmeticResources(String[] hideSelectors, String[] proceduralActions, String[] exceptions, String injectedScript, boolean genericHide) {
        this.hideSelectors = hideSelectors;
        this.proceduralActions = proceduralActions;
        this.exceptions = exceptions;
        this.injectedScript = injectedScript;
        this.genericHide = genericHide;
    }

    public String[] getHideSelectors() {
        return hideSelectors;
    }

    public String[] getProceduralActions() {
        return proceduralActions;
    }

    public String[] getExceptions() {
        return exceptions;
    }

    public String getInjectedScript() {
        return injectedScript;
    }

    public boolean isGenericHide() {
        return genericHide;
    }
}
