/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.feed;

import org.telegram.messenger.DialogObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

public final class FeedRequestNormalizer {

    private static final Field[] EMPTY_FIELDS = new Field[0];
    private static final ClassMetadata EMPTY_METADATA = new ClassMetadata(null, null, null, null, EMPTY_FIELDS);
    private static final ConcurrentHashMap<Class<?>, ClassMetadata> metadataCache = new ConcurrentHashMap<>();

    private FeedRequestNormalizer() {
    }

    public static TLObject normalize(int currentAccount, TLObject object) {
        if (object == null) {
            return object;
        }
        FeedController feedController = FeedController.peekInstance(currentAccount);
        if (feedController == null || feedController.hasNoSyntheticIds()) {
            return object;
        }
        if (!object.getClass().getName().startsWith("org.telegram.tgnet.")) {
            return object;
        }
        ClassMetadata metadata = getMetadata(object);
        if (metadata.messageIdFields.length == 0 && metadata.invoiceField == null) {
            return object;
        }
        normalizeMessageIds(currentAccount, feedController, object, metadata);
        normalizeInvoice(currentAccount, feedController, getFieldValue(metadata.invoiceField, object));
        return object;
    }

    private static ClassMetadata getMetadata(Object object) {
        if (object == null) {
            return EMPTY_METADATA;
        }
        return metadataCache.computeIfAbsent(object.getClass(), FeedRequestNormalizer::buildMetadata);
    }

    private static ClassMetadata buildMetadata(Class<?> clazz) {
        Field fromPeerField = null;
        Field peerField = null;
        Field channelField = null;
        Field invoiceField = null;
        ArrayList<Field> messageIdFields = null;
        Field[] fields;
        try {
            fields = clazz.getFields();
        } catch (Exception e) {
            reportFailure("fields of " + clazz.getName(), e);
            fields = EMPTY_FIELDS;
        }
        for (Field field : fields) {
            String name = field.getName();
            if ("from_peer".equals(name) && fromPeerField == null) {
                fromPeerField = field;
            } else if ("peer".equals(name) && peerField == null) {
                peerField = field;
            } else if ("channel".equals(name) && channelField == null) {
                channelField = field;
            } else if ("invoice".equals(name) && invoiceField == null) {
                invoiceField = field;
            }
            if (isMessageIdField(field)) {
                if (messageIdFields == null) {
                    messageIdFields = new ArrayList<>();
                }
                messageIdFields.add(field);
            }
        }
        return new ClassMetadata(
                fromPeerField != null ? fromPeerField : peerField,
                peerField,
                channelField,
                invoiceField,
                messageIdFields != null ? messageIdFields.toArray(new Field[0]) : EMPTY_FIELDS
        );
    }

    private static void normalizeMessageIds(int currentAccount, FeedController feedController, Object object) {
        normalizeMessageIds(currentAccount, feedController, object, getMetadata(object));
    }

    private static void normalizeMessageIds(int currentAccount, FeedController feedController, Object object, ClassMetadata metadata) {
        Field peerField = metadata.requestPeerField;
        long dialogId = getDialogId(peerField, object);
        if (dialogId == 0) {
            dialogId = getDialogId(metadata.peerField, object);
        }
        if (dialogId == 0) {
            dialogId = getChannelDialogId(metadata.channelField, object);
        }
        long resolvedDialogId = normalizeMessageIdFields(feedController, object, metadata);
        if (resolvedDialogId != 0 && resolvedDialogId != dialogId) {
            if (peerField != null) {
                setInputPeer(currentAccount, peerField, object, resolvedDialogId);
            } else if (metadata.channelField != null) {
                setInputChannel(currentAccount, metadata.channelField, object, resolvedDialogId);
            }
        }
    }

    private static long normalizeMessageIdFields(FeedController feedController, Object object, ClassMetadata metadata) {
        long resolvedDialogId = 0;
        if (object == null) {
            return resolvedDialogId;
        }
        for (Field field : metadata.messageIdFields) {
            resolvedDialogId = mergeResolvedDialogIds(resolvedDialogId, normalizeMessageIdField(feedController, object, field));
        }
        return resolvedDialogId;
    }

    private static boolean isMessageIdField(Field field) {
        if (field == null || Modifier.isStatic(field.getModifiers())) {
            return false;
        }
        String name = field.getName();
        return "id".equals(name) || "msg_id".equals(name) || name.endsWith("_msg_id");
    }

    private static void normalizeInvoice(int currentAccount, FeedController feedController, Object invoice) {
        if (invoice instanceof TLRPC.TL_inputInvoiceMessage) {
            normalizeMessageIds(currentAccount, feedController, invoice);
        }
    }

    private static long normalizeMessageIdField(FeedController feedController, Object object, Field field) {
        long resolvedDialogId = 0;
        try {
            Object value = field.get(object);
            if (value instanceof Integer messageId) {
                long realDialogId = feedController.resolveRealDialogId(messageId);
                if (realDialogId != 0) {
                    field.setInt(object, feedController.resolveRealMessageId(realDialogId, messageId));
                    resolvedDialogId = realDialogId;
                }
            } else if (value instanceof ArrayList<?> ids) {
                for (int i = 0; i < ids.size(); i++) {
                    Object item = ids.get(i);
                    if (item instanceof Integer messageId) {
                        long realDialogId = feedController.resolveRealDialogId(messageId);
                        if (realDialogId != 0) {
                            setListInteger(ids, i, feedController.resolveRealMessageId(realDialogId, messageId));
                            resolvedDialogId = mergeResolvedDialogIds(resolvedDialogId, realDialogId);
                        }
                    }
                }
            }
        } catch (Exception e) {
            reportFailure("message id field " + field.getName(), e);
        }
        return resolvedDialogId;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void setListInteger(ArrayList list, int index, int value) {
        list.set(index, value);
    }

    private static long mergeResolvedDialogIds(long oldDialogId, long newDialogId) {
        if (oldDialogId == 0) {
            return newDialogId;
        }
        if (newDialogId == 0) {
            return oldDialogId;
        }
        return oldDialogId == newDialogId ? oldDialogId : 0;
    }

    private static void setInputPeer(int currentAccount, Field peerField, Object object, long dialogId) {
        if (currentAccount < 0) {
            return;
        }
        try {
            TLRPC.InputPeer inputPeer = MessagesController.getInstance(currentAccount).getInputPeer(dialogId);
            if (inputPeer != null) {
                peerField.set(object, inputPeer);
            }
        } catch (Exception e) {
            reportFailure("peer field " + peerField.getName(), e);
        }
    }

    private static void setInputChannel(int currentAccount, Field channelField, Object object, long dialogId) {
        if (currentAccount < 0 || dialogId >= 0) {
            return;
        }
        try {
            TLRPC.InputChannel inputChannel = MessagesController.getInstance(currentAccount).getInputChannel(-dialogId);
            if (inputChannel != null) {
                channelField.set(object, inputChannel);
            }
        } catch (Exception e) {
            reportFailure("channel field " + channelField.getName(), e);
        }
    }

    private static long getDialogId(Field peerField, Object object) {
        if (peerField == null) {
            return 0;
        }
        try {
            Object peer = peerField.get(object);
            if (peer instanceof TLRPC.InputPeer) {
                return DialogObject.getPeerDialogId((TLRPC.InputPeer) peer);
            }
        } catch (Exception e) {
            reportFailure("peer field " + peerField.getName(), e);
        }
        return 0;
    }

    private static long getChannelDialogId(Field channelField, Object object) {
        Object channel = getFieldValue(channelField, object);
        if (channel instanceof TLRPC.InputChannel) {
            return getInputChannelDialogId((TLRPC.InputChannel) channel);
        }
        return 0;
    }

    private static long getInputChannelDialogId(TLRPC.InputChannel channel) {
        if (channel == null || channel.channel_id == 0) {
            return 0;
        }
        return -channel.channel_id;
    }

    private static Object getFieldValue(Field field, Object object) {
        if (field == null) {
            return null;
        }
        try {
            return field.get(object);
        } catch (Exception e) {
            reportFailure("field " + field.getName(), e);
            return null;
        }
    }

    private static void reportFailure(String what, Exception e) {
        FileLog.e("FeedRequestNormalizer failed to normalize " + what, e);
    }

    private record ClassMetadata(Field requestPeerField, Field peerField, Field channelField,
                                 Field invoiceField, Field[] messageIdFields) {
    }
}
