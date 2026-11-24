-keep public class com.google.android.gms.* { public *; }
-keepnames @com.google.android.gms.common.annotation.KeepName class *
-keepclassmembernames class * {
    @com.google.android.gms.common.annotation.KeepName *;
}

-keep @interface androidx.annotation.Keep
-keep @androidx.annotation.Keep class * { *; }
-keepclasseswithmembers class * { @androidx.annotation.Keep *; }

-keep class org.webrtc.* { *; }
-keep class org.webrtc.audio.* { *; }
-keep class org.webrtc.voiceengine.* { *; }
# org.telegram.messenger: only the names that native code and reflection resolve at runtime.
# Before changing these rules see TMessagesProj/proguard-checklist.md and run Tools/r8_keep_check.py.
# Static JNI exports Java_org_telegram_messenger_<Class>_<method> (Utilities, Intro, MediaController, MrzRecognizer).
-keepclasseswithmembers,includedescriptorclasses class org.telegram.messenger.* {
    native <methods>;
}
# gifvideo.cpp: FindClass + GetMethodID, called from the native reader.
-keep class org.telegram.messenger.AnimatedFileDrawableStream {
    int read(int, int);
    void cancel();
    boolean isFinishedLoadingFile();
    boolean isCanceled();
    java.lang.String getFinishedFilePath();
}
# NotificationBadge.BADGERS holds class literals, instances are made with Class.newInstance().
-keep class org.telegram.messenger.NotificationBadge$* implements org.telegram.messenger.NotificationBadge$Badger {
    <init>();
}
-keep class org.telegram.messenger.camera.* { *; }
-keep class org.telegram.messenger.secretmedia.* { *; }
-keep class org.telegram.messenger.support.* { *; }
-keep class org.telegram.messenger.time.* { *; }
-keep class org.telegram.messenger.video.* { *; }
-keep class org.telegram.messenger.voip.* { *; }
-keep class org.telegram.messenger.AnimatedFileDrawableStream { <methods>; }
-keep class org.telegram.SQLite.** { *; }
-keep class org.telegram.tgnet.TLRPC$TL_* { <init>(); }
-keep class org.telegram.tgnet.ConnectionsManager { *; }
-keep class org.telegram.tgnet.NativeByteBuffer { *; }
-keep class org.telegram.tgnet.RequestTimeDelegate { *; }
-keep class org.telegram.tgnet.RequestDelegate { *; }
-keep class org.telegram.tgnet.tl.** { *; }
-keep class org.telegram.ui.Stories.recorder.FfmpegAudioWaveformLoader { *; }
-keep class * extends org.telegram.ui.Components.UItem$UItemFactory { public <init>(...); }
-keep class androidx.mediarouter.app.MediaRouteButton { *; }
-keep class androidx.mediarouter.** { *; }
-keep class com.google.android.gms.cast.** { *; }
-keepclassmembers class ** {
    @android.webkit.JavascriptInterface <methods>;
}

-keepattributes Signature,InnerClasses,EnclosingMethod
-keep,allowobfuscation,allowshrinking class com.google.gson.reflect.TypeToken
-keep,allowobfuscation,allowshrinking class * extends com.google.gson.reflect.TypeToken
-keepclasseswithmembers,allowobfuscation class * {
  @com.google.gson.annotations.SerializedName <fields>;
}

# AdBlock
-keep class com.exteragram.messenger.adblock.interop.** { *; }
-keep class com.exteragram.messenger.adblock.data.** { *; }

-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

# Don't warn about okhttp
-dontwarn org.bouncycastle.jsse.BCSSLParameters
-dontwarn org.bouncycastle.jsse.BCSSLSocket
-dontwarn org.bouncycastle.jsse.provider.BouncyCastleJsseProvider
-dontwarn org.conscrypt.Conscrypt$Version
-dontwarn org.conscrypt.Conscrypt
-dontwarn org.conscrypt.ConscryptHostnameVerifier
-dontwarn org.openjsse.javax.net.ssl.SSLParameters
-dontwarn org.openjsse.javax.net.ssl.SSLSocket
-dontwarn org.openjsse.net.ssl.OpenJSSE

-keep class com.sun.jna.* { *; }
-keepclassmembers class * extends com.sun.jna.* { public *; }

-dontwarn java.awt.**
-dontwarn javax.imageio.**

-keepclassmembernames,allowshrinking class org.telegram.ui.* { <fields>; }
-keepclassmembernames,allowshrinking class org.telegram.ui.Cells.* { <fields>; }
-keepclassmembernames,allowshrinking class org.telegram.ui.Components.* { <fields>; }

-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
    public static void check*(...);
    public static void throw*(...);
}

-keepclassmembers enum * {
     public static **[] values();
     public static ** valueOf(java.lang.String);
}

-keep class com.exteragram.messenger.utils.network.ExchangeRates$State { *; }
-keep class com.exteragram.messenger.utils.network.ExchangeRates$CoinbaseResponse { *; }
-keep class com.exteragram.messenger.utils.network.ExchangeRates$Data { *; }

-keep class org.telegram.utils.code.highlight.PrismaHighlighter {
    private static native <methods>;
}

-keep class com.google.mlkit.nl.languageid.internal.LanguageIdentificationJni { *; }

# Fix long screenshot on some ROMs
-keepnames class androidx.recyclerview.widget.RecyclerView
-keepclassmembers class androidx.recyclerview.widget.RecyclerView {
    public void suppressLayout(boolean);
    public boolean isLayoutSuppressed();
}

# WidgetSandbox: JNI_OnLoad uses RegisterNatives; backend methods are called by native workers.
-keep class org.telegram.utils.twidget.WidgetSandboxNative { *; }
# The client's share (filters, gesture arbiter) in the client library: static Java_* exports, resolved by name.
-keepclasseswithmembers,includedescriptorclasses class org.telegram.utils.twidget.WidgetClientNative {
    native <methods>;
}
-keep class org.telegram.utils.twidget.WidgetSandboxBackend { *; }
# The isolated engine process's backend (WidgetEngineService): the same JNI callbacks.
-keep class org.telegram.utils.twidget.WidgetEngineBackend { *; }

# Don't warn about checkerframework and Kotlin annotations
-dontwarn org.checkerframework.**

-keep class io.nano.tex.** {*;}

-keep class org.telegram.tgnet.** { *; }

# JLatexMath: macro/atom classes are loaded reflectively by Class.forName
-keep class org.scilab.forge.jlatexmath.** { *; }
-keep class ru.noties.jlatexmath.** { *; }
-dontwarn org.scilab.forge.jlatexmath.**

# Use -keep to explicitly keep any other classes shrinking would remove
-dontoptimize
-dontobfuscate

-keep,allowshrinking class ** {
    *;
}

-keepnames class ** extends org.telegram.ui.ActionBar.BaseFragment

-keep class ** extends com.exteragram.messenger.preferences.BasePreferencesActivity {
    <init>();
}

-keep class com.exteragram.messenger.security.SensitiveRequestConfirmation {
    private static native <methods>;
    public static void request(int, int, int, long, long[], java.lang.String[], java.lang.String);
}
-keep class com.exteragram.messenger.security.SensitiveRequestSwitch {
    private static native <methods>;
}
