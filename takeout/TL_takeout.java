/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Created by Tesfamariam Gebre.
 */

package plus.takeout;

import org.telegram.tgnet.InputSerializedData;
import org.telegram.tgnet.OutputSerializedData;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.Vector;

public class TL_takeout {

    public static class TL_invokeWithTakeout extends TLObject {
        public static final int CONSTRUCTOR = 0xaca9fd2e;
        public long takeout_id;
        public TLObject query;

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(CONSTRUCTOR);
            stream.writeInt64(takeout_id);
            query.serializeToStream(stream);
        }

        @Override
        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return query.deserializeResponse(stream, constructor, exception);
        }
    }

    public static class TL_messages_getSplitRanges extends TLObject {
        public static final int CONSTRUCTOR = 0x1cff7e08;

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(CONSTRUCTOR);
        }

        @Override
        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            Vector<TLRPC.TL_messageRange> vector = new Vector<>(TLRPC.TL_messageRange::TLdeserialize);
            vector.readParams(stream, exception);
            return vector;
        }
    }

    public static class TL_invokeWithMessagesRange extends TLObject {
        public static final int CONSTRUCTOR = 0x365275f2;
        public TLRPC.TL_messageRange range;
        public TLObject query;

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(CONSTRUCTOR);
            range.serializeToStream(stream);
            query.serializeToStream(stream);
        }

        @Override
        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return query.deserializeResponse(stream, constructor, exception);
        }
    }

    public static class TL_account_initTakeoutSession extends TLObject {
        public static final int CONSTRUCTOR = 0x8ef3eab0;
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
            stream.writeInt32(CONSTRUCTOR);
            flags = 0;
            if (contacts) {
                flags |= 1 << 0;
            }
            if (message_users) {
                flags |= 1 << 1;
            }
            if (message_chats) {
                flags |= 1 << 2;
            }
            if (message_megagroups) {
                flags |= 1 << 3;
            }
            if (message_channels) {
                flags |= 1 << 4;
            }
            if (files) {
                flags |= 1 << 5;
            }
            stream.writeInt32(flags);
            if ((flags & (1 << 5)) != 0) {
                stream.writeInt64(file_max_size);
            }
        }
    }

    public static class TL_savedContact extends TLObject {
        public static final int CONSTRUCTOR = 0x1142bd56;
        public String phone;
        public String first_name;
        public String last_name;
        public int date;

        public static TL_savedContact TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            if (constructor != CONSTRUCTOR) {
                if (exception) {
                    throw new RuntimeException("Invalid constructor");
                }
                return null;
            }
            TL_savedContact contact = new TL_savedContact();
            contact.readParams(stream, exception);
            return contact;
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
            stream.writeInt32(CONSTRUCTOR);
            stream.writeString(phone);
            stream.writeString(first_name);
            stream.writeString(last_name);
            stream.writeInt32(date);
        }
    }

    public static class TL_contacts_getSaved extends TLObject {
        public static final int CONSTRUCTOR = 0x82f1e39f;

        @Override
        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            Vector<TL_savedContact> vector = new Vector<>(TL_savedContact::TLdeserialize);
            vector.readParams(stream, exception);
            return vector;
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(CONSTRUCTOR);
        }
    }

    public static class TL_account_takeout extends TLObject {
        public static final int CONSTRUCTOR = 0x4dba4501;
        public long id;

        public static TL_account_takeout TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            if (TL_account_takeout.CONSTRUCTOR != constructor) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in TL_account_takeout", constructor));
                }
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
    }

    public static class TL_account_finishTakeoutSession extends TLObject {
        public static final int CONSTRUCTOR = 0x1d2652ee;
        public int flags;
        public boolean success;

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(CONSTRUCTOR);
            stream.writeInt32(flags);
            if ((flags & 1) != 0) {
                stream.writeBool(success);
            }
        }

        @Override
        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Bool.TLdeserialize(stream, constructor, exception);
        }
    }
}
