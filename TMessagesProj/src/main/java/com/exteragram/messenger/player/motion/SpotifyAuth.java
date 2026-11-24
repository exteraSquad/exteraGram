/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.player.motion;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Base64;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.messenger.support.customtabs.CustomTabsIntent;
import org.telegram.ui.ActionBar.Theme;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;

public final class SpotifyAuth {

    public interface Callback {
        void onResult(boolean success);
    }

    private static final String AUTHORIZE_URL = "https://accounts.spotify.com/authorize";
    private static final String SCOPES = "streaming user-read-private user-read-email";
    private static final String CALLBACK_PATH = "/login";
    private static final int PORT = 8898;
    private static final int TIMEOUT_MS = 5 * 60 * 1000;
    private static final int READ_TIMEOUT_MS = 10 * 1000;
    private static final int BASE64_FLAGS = Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP;

    private static ServerSocket server;

    private SpotifyAuth() {
    }

    public static void login(Context context, Callback callback) {
        cancel();
        final String verifier = random(48);
        final String state = random(16);
        final ServerSocket socket;
        try {
            socket = new ServerSocket();
            socket.setReuseAddress(true);
            socket.bind(new InetSocketAddress(InetAddress.getByAddress(new byte[]{127, 0, 0, 1}), PORT));
            socket.setSoTimeout(TIMEOUT_MS);
        } catch (IOException e) {
            FileLog.e(e);
            callback.onResult(false);
            return;
        }
        synchronized (SpotifyAuth.class) {
            server = socket;
        }
        new Thread(() -> listen(socket, state, verifier, callback), "spotifyLogin").start();

        final Uri uri = Uri.parse(AUTHORIZE_URL).buildUpon()
                .appendQueryParameter("client_id", SpotifyCanvasRepository.CLIENT_ID)
                .appendQueryParameter("response_type", "code")
                .appendQueryParameter("redirect_uri", SpotifyCanvasRepository.REDIRECT_URI)
                .appendQueryParameter("scope", SCOPES)
                .appendQueryParameter("code_challenge_method", "S256")
                .appendQueryParameter("code_challenge", challenge(verifier))
                .appendQueryParameter("state", state)
                .build();
        try {
            new CustomTabsIntent.Builder()
                    .setToolbarColor(Theme.getColor(Theme.key_actionBarBrowser))
                    .setShowTitle(true)
                    .build()
                    .launchUrl(context, uri);
        } catch (Exception e) {
            FileLog.e(e);
            try {
                context.startActivity(new Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Exception e2) {
                FileLog.e(e2);
                cancel();
                callback.onResult(false);
            }
        }
    }

    public static synchronized void cancel() {
        if (server != null) {
            closeQuietly(server);
            server = null;
        }
    }

    private static void listen(ServerSocket socket, String state, String verifier, Callback callback) {
        String code = null;
        try {
            while (true) {
                try (Socket client = socket.accept()) {
                    client.setSoTimeout(READ_TIMEOUT_MS);
                    final String line = new BufferedReader(new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8)).readLine();
                    final String[] parts = line != null ? line.split(" ") : new String[0];
                    final Uri uri = parts.length >= 2 ? Uri.parse("http://127.0.0.1" + parts[1]) : null;
                    if (uri == null || !CALLBACK_PATH.equals(uri.getPath())) {
                        respond(client.getOutputStream(), "404 Not Found", "");
                        continue;
                    }
                    if (state.equals(uri.getQueryParameter("state"))) {
                        code = uri.getQueryParameter("code");
                    }
                    respond(client.getOutputStream(), "200 OK", page(code != null));
                    break;
                }
            }
        } catch (IOException e) {
            if (!socket.isClosed()) {
                FileLog.e(e);
            }
        }
        final boolean cancelled;
        synchronized (SpotifyAuth.class) {
            cancelled = server != socket;
            if (!cancelled) {
                server = null;
            }
        }
        closeQuietly(socket);
        if (cancelled) {
            return;
        }
        if (code == null) {
            AndroidUtilities.runOnUIThread(() -> callback.onResult(false));
        } else {
            SpotifyCanvasRepository.exchangeCode(code, verifier, callback::onResult);
        }
    }

    private static String page(boolean success) {
        final String back = "intent://#Intent;package=" + ApplicationLoader.applicationContext.getPackageName()
                + ";action=android.intent.action.MAIN;category=android.intent.category.LAUNCHER;end";
        return "<!doctype html><html><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"></head>"
                + "<body style=\"margin:0;padding:64px 24px;background:#121212;color:#fff;font:18px sans-serif;text-align:center\">"
                + "<p>" + getString(success ? R.string.PlayerSpotifyLoginDone : R.string.PlayerSpotifyLoginFailed) + "</p>"
                + "<p><a id=\"back\" href=\"" + back + "\" style=\"color:#1ed760;font-weight:bold\">" + getString(R.string.PlayerSpotifyLoginBack) + "</a></p>"
                + "<script>location.href=document.getElementById('back').href</script>"
                + "</body></html>";
    }

    private static void respond(OutputStream out, String status, String body) throws IOException {
        final byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        final String head = "HTTP/1.1 " + status + "\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: " + bytes.length + "\r\nConnection: close\r\n\r\n";
        out.write(head.getBytes(StandardCharsets.US_ASCII));
        out.write(bytes);
        out.flush();
    }

    private static String random(int bytes) {
        final byte[] data = new byte[bytes];
        new SecureRandom().nextBytes(data);
        return Base64.encodeToString(data, BASE64_FLAGS);
    }

    private static String challenge(String verifier) {
        try {
            return Base64.encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)), BASE64_FLAGS);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static void closeQuietly(ServerSocket socket) {
        try {
            socket.close();
        } catch (IOException ignore) {
        }
    }
}
