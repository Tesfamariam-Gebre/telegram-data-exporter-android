/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Created by Tesfamariam Gebre.
 */

package plus.takeout.cells;

import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

 public class DialogBuckets {

    public List<TLRPC.Dialog> privContacts      = new ArrayList<>();
    public List<TLRPC.Dialog> privNonContacts   = new ArrayList<>();
    public List<TLRPC.Dialog> groupChats        = new ArrayList<>();
    public List<TLRPC.Dialog> supergroupChats   = new ArrayList<>();
    public List<TLRPC.Dialog> channelChats      = new ArrayList<>();

     public DialogBuckets separateDialogs(TLRPC.TL_messages_dialogsSlice slice) {
         // build lookup maps
         Map<Long, TLRPC.User> userMap = new HashMap<>();
         for (TLRPC.User u : slice.users) {
             userMap.put(u.id, u);
         }

         Map<Long, TLRPC.Chat> chatMap = new HashMap<>();
         for (TLRPC.Chat c : slice.chats) {
             chatMap.put(c.id, c);
         }

         DialogBuckets buckets = new DialogBuckets();

         for (TLRPC.Dialog dialog : slice.dialogs) {
             TLRPC.Peer peer = dialog.peer;

             // 1) Private chats
             if (peer instanceof TLRPC.TL_peerUser) {
                 long userId = ((TLRPC.TL_peerUser) peer).user_id;
                 TLRPC.User usr = userMap.get(userId);
                 if (usr != null && usr.contact) {
                     buckets.privContacts.add(dialog);
                 } else {
                     buckets.privNonContacts.add(dialog);
                 }
                 continue;
             }

             // 2) Basic group chats
             if (peer instanceof TLRPC.TL_peerChat) {
                 buckets.groupChats.add(dialog);
                 continue;
             }

             // 3) Supergroups & Channels
             if (peer instanceof TLRPC.TL_peerChannel) {
                 long chanId = ((TLRPC.TL_peerChannel) peer).channel_id;
                 TLRPC.Chat ch = chatMap.get(chanId);
                 if (ch instanceof TLRPC.TL_channel) {
                     if (ch.megagroup) {
                         buckets.supergroupChats.add(dialog);
                     } else {
                         buckets.channelChats.add(dialog);
                     }
                 }
             }
         }

         return buckets;
     }
}