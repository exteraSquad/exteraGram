/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.export.api;

import org.telegram.tgnet.InputSerializedData;
import org.telegram.tgnet.OutputSerializedData;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLParseException;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.Vector;

public final class TakeoutRequests {

    private TakeoutRequests() {
    }

    public static class TL_account_takeout extends TLObject {
        public static final int constructor = 0x4dba4501;

        public long id;

        public static TL_account_takeout TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            if (constructor != TL_account_takeout.constructor) {
                TLParseException.doThrowOrLog(stream, "account.Takeout", constructor, exception);
                return null;
            }
            TL_account_takeout result = new TL_account_takeout();
            result.readParams(stream, exception);
            return result;
        }

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            id = stream.readInt64(exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(id);
        }
    }

    public static class TL_account_initTakeoutSession extends TLObject {
        public static final int constructor = 0x8ef3eab0;

        public int flags;
        public boolean contacts;
        public boolean message_users;
        public boolean message_chats;
        public boolean message_megagroups;
        public boolean message_channels;
        public boolean files;
        public long file_max_size;

        @Override
        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TL_account_takeout.TLdeserialize(stream, constructor, exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = contacts ? (flags | 1) : (flags & ~1);
            flags = message_users ? (flags | 2) : (flags & ~2);
            flags = message_chats ? (flags | 4) : (flags & ~4);
            flags = message_megagroups ? (flags | 8) : (flags & ~8);
            flags = message_channels ? (flags | 16) : (flags & ~16);
            flags = files ? (flags | 32) : (flags & ~32);
            stream.writeInt32(flags);
            if ((flags & 32) != 0) {
                stream.writeInt64(file_max_size);
            }
        }
    }

    public static class TL_account_finishTakeoutSession extends TLObject {
        public static final int constructor = 0x1d2652ee;

        public int flags;
        public boolean success;

        @Override
        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Bool.TLdeserialize(stream, constructor, exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = success ? (flags | 1) : (flags & ~1);
            stream.writeInt32(flags);
        }
    }

    public static class TL_invokeWithTakeout extends TLObject {
        public static final int constructor = 0xaca9fd2e;

        public long takeout_id;
        public TLObject query;

        @Override
        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return query.deserializeResponse(stream, constructor, exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(takeout_id);
            query.serializeToStream(stream);
        }
    }

    public static class TL_invokeWithMessagesRange extends TLObject {
        public static final int constructor = 0x365275f2;

        public TLRPC.TL_messageRange range;
        public TLObject query;

        @Override
        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return query.deserializeResponse(stream, constructor, exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            range.serializeToStream(stream);
            query.serializeToStream(stream);
        }
    }

    public static class TL_messages_getSplitRanges extends TLObject {
        public static final int constructor = 0x1cff7e08;

        @Override
        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return Vector.TLDeserialize(stream, constructor, exception, TLRPC.TL_messageRange::TLdeserialize);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_channels_getLeftChannels extends TLObject {
        public static final int constructor = 0x8341ecc0;

        public int offset;

        @Override
        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.messages_Chats.TLdeserialize(stream, constructor, exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(offset);
        }
    }

    public static class TL_contacts_getSaved extends TLObject {
        public static final int constructor = 0x82f1e39f;

        @Override
        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return Vector.TLDeserialize(stream, constructor, exception, TL_savedPhoneContact::TLdeserialize);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_savedPhoneContact extends TLObject {
        public static final int constructor = 0x1142bd56;

        public String phone;
        public String first_name;
        public String last_name;
        public int date;

        public static TL_savedPhoneContact TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            if (constructor != TL_savedPhoneContact.constructor) {
                TLParseException.doThrowOrLog(stream, "SavedContact", constructor, exception);
                return null;
            }
            TL_savedPhoneContact result = new TL_savedPhoneContact();
            result.readParams(stream, exception);
            return result;
        }

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            phone = stream.readString(exception);
            first_name = stream.readString(exception);
            last_name = stream.readString(exception);
            date = stream.readInt32(exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(phone);
            stream.writeString(first_name);
            stream.writeString(last_name);
            stream.writeInt32(date);
        }
    }

    public static class TL_inputTakeoutFileLocation extends TLRPC.InputFileLocation {
        public static final int constructor = 0x29be5899;

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }
}
