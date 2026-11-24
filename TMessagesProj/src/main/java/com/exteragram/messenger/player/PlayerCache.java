/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.player;

import org.telegram.messenger.FileLoader;

import java.io.File;

public final class PlayerCache {

    private PlayerCache() {
    }

    public static File directory(String name) {
        final File root = FileLoader.checkDirectory(FileLoader.MEDIA_DIR_CACHE);
        if (root == null) {
            return null;
        }
        final File dir = new File(root, name);
        return dir.exists() || dir.mkdirs() ? dir : null;
    }
}
