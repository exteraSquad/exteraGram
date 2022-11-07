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
