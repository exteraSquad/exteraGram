/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.player;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessageObject;

import java.util.function.BooleanSupplier;

public final class CoverColorsWait {

    private static final long TIMEOUT_MS = 1000;

    private final BooleanSupplier enabled;
    private final Runnable onTimeout;
    private final Runnable timeout = this::expire;
    private MessageObject messageObject;
    private boolean scheduled;
    private boolean expired;

    public CoverColorsWait(BooleanSupplier enabled, Runnable onTimeout) {
        this.enabled = enabled;
        this.onTimeout = onTimeout;
    }

    public boolean shouldWait(MessageObject messageObject) {
        if (this.messageObject != messageObject) {
            cancel();
            this.messageObject = messageObject;
        }
        if (expired || !isPending(messageObject)) {
            return false;
        }
        if (!scheduled) {
            scheduled = true;
            AndroidUtilities.runOnUIThread(timeout, TIMEOUT_MS);
        }
        return true;
    }

    public void cancel() {
        AndroidUtilities.cancelRunOnUIThread(timeout);
        scheduled = false;
        expired = false;
    }

    private void expire() {
        scheduled = false;
        expired = true;
        onTimeout.run();
    }

    private boolean isPending(MessageObject messageObject) {
        return enabled.getAsBoolean() && messageObject != null && !messageObject.isVoice() && !messageObject.isRoundVideo()
                && CoverColorExtractor.getCachedSeed(messageObject) == null && PlayerCovers.hasCover(messageObject);
    }
}
