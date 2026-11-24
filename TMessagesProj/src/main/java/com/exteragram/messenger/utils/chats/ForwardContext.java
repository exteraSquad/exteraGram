/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.utils.chats;

import android.os.Bundle;

import org.telegram.messenger.MessageObject;

import java.util.ArrayList;

public interface ForwardContext {
    String FORWARD_NOQUOTE = "forward_noquote";

    ForwardParams getForwardParams();

    ArrayList<MessageObject> getForwardingMessages();

    default void setForwardParams(boolean noQuote) {
        getForwardParams().noQuote = noQuote;
    }

    default boolean isForwardNoQuote() {
        return getForwardParams().noQuote;
    }

    default void writeForwardParams(Bundle args) {
        if (args == null) {
            return;
        }
        args.putBoolean(FORWARD_NOQUOTE, isForwardNoQuote());
    }

    default void readForwardParams(Bundle args) {
        if (args == null) {
            return;
        }
        setForwardParams(args.getBoolean(FORWARD_NOQUOTE, false));
    }

    class ForwardParams {
        public boolean noQuote;
    }
}
