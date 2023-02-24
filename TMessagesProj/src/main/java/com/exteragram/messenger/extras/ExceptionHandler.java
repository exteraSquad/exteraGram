/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @immat0x1, 2023

*/

package com.exteragram.messenger.extras;

import android.util.Log;

import androidx.annotation.NonNull;

import org.telegram.messenger.FileLog;

public class ExceptionHandler implements Thread.UncaughtExceptionHandler {
    private final Thread.UncaughtExceptionHandler defHandler;

    public ExceptionHandler() {
        this.defHandler = Thread.getDefaultUncaughtExceptionHandler();
    }

    @Override
    public void uncaughtException(@NonNull Thread th, @NonNull Throwable ex) {
        // TODO
        // for now just log crashes into telegram logs
        FileLog.e(Log.getStackTraceString(ex));
        defHandler.uncaughtException(th, ex);
    }
}
