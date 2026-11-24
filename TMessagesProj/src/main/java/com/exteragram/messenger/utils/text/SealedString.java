/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.utils.text;

import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public final class SealedString {
    private static final int SALT_LENGTH = 8;
    private static final byte[] KEY = "exteraGram/SealedString/v1".getBytes(StandardCharsets.UTF_8);

    private SealedString() {
    }

    public static String open(String sealed) {
        try {
            byte[] data = Base64.decode(sealed, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
            if (data.length < SALT_LENGTH) {
                throw new IllegalArgumentException("sealed string is too short");
            }
            byte[] plain = new byte[data.length - SALT_LENGTH];
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] block = null;
            for (int i = 0; i < plain.length; i++) {
                int offset = i % 32;
                if (offset == 0) {
                    digest.update(KEY);
                    digest.update(data, 0, SALT_LENGTH);
                    digest.update((byte) (i / 32));
                    block = digest.digest();
                }
                plain[i] = (byte) (data[SALT_LENGTH + i] ^ block[offset]);
            }
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
