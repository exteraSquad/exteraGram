/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.security;

import android.os.SystemClock;

import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.NativeByteBuffer;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;

import java.util.LinkedHashMap;
import java.nio.ByteBuffer;
import java.security.MessageDigest;

public final class SensitivePaymentForms {

    private static final LinkedHashMap<String, Payment> payments = new LinkedHashMap<>();
    private static final long[] generations = new long[UserConfig.MAX_ACCOUNT_COUNT];
    private static final long MAX_AGE = 15 * 60 * 1000;

    private SensitivePaymentForms() {
    }

    public static final class Request {
        final long userId;
        final long generation;
        final String invoiceHash;

        Request(long userId, long generation, String invoiceHash) {
            this.userId = userId;
            this.generation = generation;
            this.invoiceHash = invoiceHash;
        }
    }

    public static synchronized Request capture(int account, TLObject object, NativeByteBuffer buffer) {
        if (!(object instanceof TLRPC.TL_payments_getPaymentForm)) {
            return null;
        }
        TLRPC.InputInvoice invoice = ((TLRPC.TL_payments_getPaymentForm) object).invoice;
        if (invoice == null) {
            return null;
        }
        try {
            int size = invoice.getObjectSize();
            if (size <= 0 || size > buffer.position() - 8) {
                return null;
            }
            ByteBuffer bytes = buffer.buffer.duplicate();
            bytes.position(8);
            bytes.limit(8 + size);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(bytes);
            return new Request(UserConfig.getInstance(account).getClientUserId(), generations[account], Utilities.bytesToHex(digest.digest()));
        } catch (Exception e) {
            return null;
        }
    }

    static final class Payment {
        final long amount;
        final String currency;
        final String invoiceHash;
        final long created = SystemClock.elapsedRealtime();

        Payment(long amount, String currency, String invoiceHash) {
            this.amount = amount;
            this.currency = currency;
            this.invoiceHash = invoiceHash;
        }
    }

    public static synchronized void remember(int account, Request request, TLObject response) {
        if (request == null || request.generation != generations[account] || request.userId != UserConfig.getInstance(account).getClientUserId() || !(response instanceof TLRPC.PaymentForm)) {
            return;
        }
        TLRPC.PaymentForm form = (TLRPC.PaymentForm) response;
        if (!(form instanceof TLRPC.TL_payments_paymentFormStarGift || form instanceof TLRPC.TL_payments_paymentFormStars) || form.invoice == null || form.invoice.prices == null || form.invoice.prices.isEmpty()) {
            return;
        }
        long amount = 0;
        try {
            for (TLRPC.TL_labeledPrice price : form.invoice.prices) {
                if (price == null) {
                    return;
                }
                amount = Math.addExact(amount, price.amount);
            }
        } catch (ArithmeticException e) {
            return;
        }
        if (amount < 0) {
            return;
        }
        String key = account + ":" + UserConfig.getInstance(account).getClientUserId() + ":" + form.form_id;
        payments.put(key, new Payment(amount, form.invoice.currency, request.invoiceHash));
        while (payments.size() > 128) {
            payments.remove(payments.keySet().iterator().next());
        }
    }

    static synchronized Payment get(int account, long formId, String invoiceHash) {
        String key = account + ":" + UserConfig.getInstance(account).getClientUserId() + ":" + formId;
        Payment payment = payments.get(key);
        if (payment != null && SystemClock.elapsedRealtime() - payment.created > MAX_AGE) {
            payments.remove(key);
            return null;
        }
        return payment != null && payment.invoiceHash.equals(invoiceHash) ? payment : null;
    }

    public static synchronized void clear(int account) {
        generations[account]++;
        String prefix = account + ":";
        payments.keySet().removeIf(key -> key.startsWith(prefix));
    }
}
