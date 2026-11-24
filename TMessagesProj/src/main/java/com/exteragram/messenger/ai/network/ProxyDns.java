/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.ai.network;

import android.content.SharedPreferences;

import com.exteragram.messenger.utils.network.ExteraHttpClient;
import com.exteragram.messenger.utils.network.RemoteUtils;

import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.Dns;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.RequestBody;

public class ProxyDns implements Dns {
    public static final Dns INSTANCE = new ProxyDns();
    private static final String DEFAULT_URL = "https://dns.comss.one/dns-query";

    private final OkHttpClient client;
    private volatile String cachedUrl;
    private volatile boolean listenerInitialized = false;
    /**
     * @noinspection FieldCanBeLocal
     */
    private SharedPreferences.OnSharedPreferenceChangeListener changeListener;

    private ProxyDns() {
        this.client = ExteraHttpClient.INSTANCE.getClient().newBuilder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .build();
    }

    private void initializeListener() {
        if (!listenerInitialized) {
            RemoteUtils.initCached();
            if (RemoteUtils.sharedPreferences != null) {
                changeListener = (prefs, key) -> {
                    if (RemoteUtils.DNS_URL.equals(key)) {
                        cachedUrl = null;
                    }
                };
                RemoteUtils.sharedPreferences.registerOnSharedPreferenceChangeListener(changeListener);
                listenerInitialized = true;
            }
        }
    }

    private String getUrl() {
        if (!listenerInitialized) {
            initializeListener();
        }
        var current = cachedUrl;
        if (current == null) {
            current = RemoteUtils.getStringConfigValue(RemoteUtils.DNS_URL, DEFAULT_URL);
            cachedUrl = current;
        }
        return current;
    }

    @NotNull
    @Override
    public List<InetAddress> lookup(@NotNull String hostname) throws UnknownHostException {
        try {
            var resolved = resolveDns(hostname);
            if (resolved == null || resolved.isEmpty()) {
                return Dns.SYSTEM.lookup(hostname);
            }

            var res = new ArrayList<InetAddress>(resolved.size());
            for (var ip : resolved) {
                res.add(InetAddress.getByName(ip));
            }
            return res;
        } catch (Exception ignored) {
            return Dns.SYSTEM.lookup(hostname);
        }
    }

    private List<String> resolveDns(String domain) {
        var dnsQuery = buildDnsQuery(domain);

        var requestBody = RequestBody.create(
                dnsQuery,
                MediaType.parse("application/dns-message")
        );

        var request = new okhttp3.Request.Builder()
                .url(getUrl())
                .post(requestBody)
                .addHeader("Accept", "application/dns-message")
                .build();

        try (var response = client.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                var responseBytes = response.body().bytes();
                return parseDnsResponse(responseBytes);
            }
        } catch (IOException e) {
        }

        return null;
    }

    private byte[] buildDnsQuery(String domain) {
        var baos = new ByteArrayOutputStream();
        var dos = new DataOutputStream(baos);

        try {
            // Transaction ID
            dos.writeShort(0x1234);
            // Flags (standard query)
            dos.writeShort(0x0100);
            // Questions: 1
            dos.writeShort(1);
            // Answers RRs: 0
            dos.writeShort(0);
            // Authority RRs: 0
            dos.writeShort(0);
            // Additional RRs: 0
            dos.writeShort(0);

            // domain name
            var parts = domain.split("\\.");
            for (var part : parts) {
                dos.writeByte(part.length());
                dos.writeBytes(part);
            }
            dos.writeByte(0);

            // Query type (A record)
            dos.writeShort(1);
            // Query class (IN)
            dos.writeShort(1);

        } catch (IOException ignored) {
        }

        return baos.toByteArray();
    }

    private List<String> parseDnsResponse(byte[] response) {
        var buffer = ByteBuffer.wrap(response);

        // Skip transaction ID
        buffer.getShort();
        // Skip flags
        buffer.getShort();

        var questions = buffer.getShort();
        var answers = buffer.getShort();
        // Don't remove
        /*var authorities = */
        buffer.getShort();
        /*var additionals = */
        buffer.getShort();

        // Skip questions section
        for (int i = 0; i < questions; i++) {
            skipDomainName(buffer);
            // Skip QTYPE and QCLASS
            buffer.getShort();
            buffer.getShort();
        }

        var addresses = new ArrayList<String>();
        for (int i = 0; i < answers; i++) {
            skipDomainName(buffer);

            short type = buffer.getShort();    // TYPE
            buffer.getShort();                 // CLASS
            buffer.getInt();                   // TTL
            int rdLength = buffer.getShort();  // RDLENGTH

            if (type == 1) { // A Record
                // Convert 4 bytes to IP address
                StringBuilder ip = new StringBuilder();
                for (int j = 0; j < 4; j++) {
                    ip.append(buffer.get() & 0xFF);
                    if (j < 3) ip.append(".");
                }
                addresses.add(ip.toString());
            } else {
                // Skip other record types
                buffer.position(buffer.position() + rdLength);
            }
        }

        return addresses;
    }

    private void skipDomainName(ByteBuffer buffer) {
        while (true) {
            int length = buffer.get() & 0xFF;

            // Check if it's a pointer
            if ((length & 0xC0) == 0xC0) {
                // Skip the second byte of pointer
                buffer.get();
                break;
            }

            if (length == 0) break;

            // Skip the label
            buffer.position(buffer.position() + length);
        }
    }
}
