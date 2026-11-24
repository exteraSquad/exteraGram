/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.export.output;

public final class Result {

    public static final Result SUCCESS = new Result(null);

    public final String path;

    private Result(String path) {
        this.path = path;
    }

    public static Result error(String path) {
        return new Result(path != null ? path : "");
    }

    public boolean isSuccess() {
        return path == null;
    }
}
