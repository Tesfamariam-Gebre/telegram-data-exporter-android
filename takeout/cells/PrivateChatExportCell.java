/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Created by Tesfamariam Gebre.
 */

package plus.takeout.cells;

import android.content.Context;
import android.graphics.Canvas;
import android.os.Environment;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.Vector;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.LineProgressView;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

import plus.takeout.TL_takeout;

public class PrivateChatExportCell extends FrameLayout {

    private static final String EXPORT_DIR_NAME = "Exported Data/Chats";
    private static final int DIALOGS_LIMIT = 100;
    private static final int MESSAGES_LIMIT = 100;
    private static final String DATE_FORMAT_PATTERN = "yyyyMMdd_HHmmss";
    private static final String CSV_HEADER = "id, date, from_id, message\n";
    private static final String FILENAME_REGEX = "[^a-zA-Z0-9]";

    public static final String EXPORT_DIR = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) + "/" + EXPORT_DIR_NAME;
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat(DATE_FORMAT_PATTERN, Locale.US);

    public interface PrivateChatExportDelegate {
        void onExportStatusUpdate(String status, int color);
        void onPrivateChatExported(int order, String infoName, int color, int totalSize);
        void onExportFinished(String status, int color);
    }

    private PrivateChatExportDelegate delegate;
    private TextCheckCell textCheckCell;
    public TextView statusTextView;
    public LineProgressView progressView;
    private boolean needDivider;
    private boolean exportAsCsv = true;
    private final AtomicInteger pendingRanges = new AtomicInteger(0);
    private final AtomicInteger pendingDialogs = new AtomicInteger(0);
    private int totalDialogs = 0;

    public PrivateChatExportCell(@NonNull Context context) {
        super(context);

        textCheckCell = new TextCheckCell(getContext());
        addView(textCheckCell, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        statusTextView = new TextView(getContext());
        statusTextView.setTextColor(Theme.getColor(Theme.key_chat_attachFileText));
        statusTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        statusTextView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        statusTextView.setLines(1);
        statusTextView.setMaxLines(1);
        statusTextView.setSingleLine(true);
        statusTextView.setPadding(0, 0, 0, 0);
        statusTextView.setEllipsize(TextUtils.TruncateAt.END);
        addView(statusTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT,
                (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP,
                21, 35, 21, 0));

        progressView = new LineProgressView(getContext());
        progressView.setProgressColor(Theme.getColor(Theme.key_sharedMedia_startStopLoadIcon));
        addView(progressView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 5,
                (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP,
                21, 60, 21, 0));
    }

    public void setPrivateChatExportDelegate(PrivateChatExportDelegate delegate) {
        this.delegate = delegate;
    }

    public void setDivider(boolean divider) {
        needDivider = divider;
        setWillNotDraw(!divider);
    }

    public void setExportData(CharSequence text, boolean checked) {
        textCheckCell.setTextAndCheck(text, checked, false);
        needDivider = true;
    }

    public void setExportAsCsv(boolean exportAsCsv) {
        this.exportAsCsv = exportAsCsv;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (needDivider) {
            int left = LocaleController.isRTL ? 0 : AndroidUtilities.dp(20);
            int right = getMeasuredWidth() - (LocaleController.isRTL ? AndroidUtilities.dp(20) : 0);
            canvas.drawLine(left, getMeasuredHeight() - 1, right, getMeasuredHeight() - 1, Theme.dividerPaint);
        }
    }

    public void exportPrivateChatMessages(long takeoutId) {
        AndroidUtilities.runOnUIThread(() -> {
            if (delegate != null) {
                delegate.onExportStatusUpdate("Starting export...", Theme.getColor(Theme.key_chat_attachFileText));
            }
        });

        TL_takeout.TL_messages_getSplitRanges getSplitRanges = new TL_takeout.TL_messages_getSplitRanges();
        TL_takeout.TL_invokeWithTakeout invokeSplitRangesRequest = new TL_takeout.TL_invokeWithTakeout();
        invokeSplitRangesRequest.takeout_id = takeoutId;
        invokeSplitRangesRequest.query = getSplitRanges;

        ConnectionsManager.getInstance(UserConfig.selectedAccount).sendRequest(invokeSplitRangesRequest, (response, error) -> {
            if (error != null) {
                FileLog.e("Failed to get split ranges: " + error.text);
                handleError("Failed to get split ranges: " + error.text, "Export failed");
                return;
            }
            if (!(response instanceof Vector)) {
                FileLog.e("Unexpected response type for split ranges");
                handleError("Unexpected response type", "Export failed");
                return;
            }

            Vector<TLRPC.TL_messageRange> ranges = (Vector<TLRPC.TL_messageRange>) response;
            pendingRanges.set(ranges.objects.size());
            if (ranges.objects.isEmpty()) {
                finishExport("No ranges found", Theme.key_color_red);
                return;
            }

            for (TLRPC.TL_messageRange range : ranges.objects) {
                processRangeRecursive(takeoutId, range, new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
            }
        });
    }

    private void processRangeRecursive(long takeoutId,
                                       TLRPC.TL_messageRange range,
                                       List<TLRPC.Dialog> dialogs,
                                       List<TLRPC.User> users,
                                       List<TLRPC.Chat> chats) {
        AndroidUtilities.runOnUIThread(() -> {
            if (delegate != null) {
                delegate.onExportStatusUpdate("Fetching dialogs...", Theme.getColor(Theme.key_chat_attachFileText));
            }
        });

        getMeToMessagesDialogsRecursive(
                takeoutId, range, dialogs, users, chats,
                0, 0, new TLRPC.TL_inputPeerEmpty(),
                () -> {
                    List<TLRPC.Dialog> privateDialogs = filterPrivateDialogs(dialogs);
                    totalDialogs += privateDialogs.size();
                    pendingRanges.decrementAndGet();
                    if (privateDialogs.isEmpty()) {
                        checkExportFinished();
                    } else {
                        AndroidUtilities.runOnUIThread(() -> {
                            if (delegate != null) {
                                delegate.onExportStatusUpdate("Processing private chats...", Theme.getColor(Theme.key_chat_attachFileText));
                            }
                        });
                        processFilteredDialogs(takeoutId, range, privateDialogs, users, chats);
                    }
                }
        );
    }

    private List<TLRPC.Dialog> filterPrivateDialogs(List<TLRPC.Dialog> dialogs) {
        List<TLRPC.Dialog> privateDialogs = new ArrayList<>();
        for (TLRPC.Dialog dialog : dialogs) {
            if (dialog.peer instanceof TLRPC.TL_peerUser) {
                privateDialogs.add(dialog);
            }
        }
        return privateDialogs;
    }

    private void processFilteredDialogs(long takeoutId,
                                        TLRPC.TL_messageRange range,
                                        List<TLRPC.Dialog> filteredDialogs,
                                        List<TLRPC.User> users,
                                        List<TLRPC.Chat> chats) {
        for (int i = 0; i < filteredDialogs.size(); i++) {
            TLRPC.Dialog dialog = filteredDialogs.get(i);
            TLRPC.InputPeer peer = getInputPeerFromDialog(dialog, users, chats);
            if (peer != null) {
                pendingDialogs.incrementAndGet();
                List<TLRPC.Message> messages = new ArrayList<>();
                String chatName = getChatName(dialog, users, chats);
                AndroidUtilities.runOnUIThread(() -> {
                    if (delegate != null) {
                        delegate.onExportStatusUpdate("Exporting messages for " + chatName, Theme.getColor(Theme.key_chat_attachFileText));
                    }
                });

                getMeToMessagesHistoryRecursive(
                        takeoutId, range, peer,
                        0, 0, messages,
                        () -> {
                            saveMessagesToFile(messages, chatName);
                            AndroidUtilities.runOnUIThread(() -> {
                                if (delegate != null) {
                                    delegate.onPrivateChatExported(filteredDialogs.indexOf(dialog) + 1, chatName, Theme.getColor(Theme.key_chat_attachFileText), messages.size());
                                }
                            });
                            pendingDialogs.decrementAndGet();
                            updateProgress();
                            checkExportFinished();
                        }
                );
            }
        }
    }

    private void updateProgress() {
        AndroidUtilities.runOnUIThread(() -> {
            if (totalDialogs > 0) {
                float progress = 1f - ((float) pendingDialogs.get() / totalDialogs);
                progressView.setProgress(progress, true);
            }
        });
    }

    private void checkExportFinished() {
        AndroidUtilities.runOnUIThread(() -> {
            if (pendingRanges.get() == 0 && pendingDialogs.get() == 0) {
                progressView.setProgress(1f, true);
                if (delegate != null) {
                    delegate.onExportFinished("Export completed successfully", Theme.getColor(Theme.key_chat_attachFileText));
                }
            }
        });
    }

    private String getChatName(TLRPC.Dialog dialog, List<TLRPC.User> users, List<TLRPC.Chat> chats) {
        if (dialog.peer instanceof TLRPC.TL_peerUser) {
            long userId = ((TLRPC.TL_peerUser) dialog.peer).user_id;
            for (TLRPC.User u : users) {
                if (u.id == userId) {
                    String firstName = u.first_name != null ? u.first_name : "";
                    String lastName = u.last_name != null ? u.last_name : "";
                    return (firstName + " " + lastName).trim();
                }
            }
        }
        return "UnknownUser";
    }

    private void saveMessagesToFile(List<TLRPC.Message> messages, String chatName) {
        if (exportAsCsv) {
            saveAsCsv(messages, chatName);
        } else {
            saveAsJson(messages, chatName);
        }
    }

    private void saveAsCsv(List<TLRPC.Message> messages, String chatName) {
        ensureExportDirectory();
        String fileName = generateFileName(chatName, true);
        File file = new File(EXPORT_DIR, fileName);
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(CSV_HEADER);
            for (TLRPC.Message msg : messages) {
                String id = String.valueOf(msg.id);
                String date = new Date((long) msg.date * 1000).toString();
                String fromId = msg.from_id != null ? String.valueOf(msg.from_id.user_id) : "";
                String message = escapeCsvField(msg.message);
                writer.write(id + "," + date + "," + fromId + "," + message + "\n");
            }
        } catch (IOException e) {
            FileLog.e("Failed to save CSV for chat: " + chatName, e);
        } catch (Exception e) {
            FileLog.e("Failed to save CSV for chat: " + chatName, e);
        }
    }

    private void saveAsJson(List<TLRPC.Message> messages, String chatName) {
        ensureExportDirectory();
        String fileName = generateFileName(chatName, false);
        File file = new File(EXPORT_DIR, fileName);
        try (FileWriter writer = new FileWriter(file)) {
            writer.write("[\n");
            for (int i = 0; i < messages.size(); i++) {
                TLRPC.Message msg = messages.get(i);
                String json = String.format(
                        "  {\"id\": %d, \"date\": \"%s\", \"from_id\": %s, \"message\": \"%s\"}",
                        msg.id,
                        new Date((long) msg.date * 1000).toString(),
                        msg.from_id != null ? String.valueOf(msg.from_id.user_id) : "null",
                        escapeJsonString(msg.message)
                );
                writer.write(json);
                if (i < messages.size() - 1) {
                    writer.write(",\n");
                } else {
                    writer.write("\n");
                }
            }
            writer.write("]\n");
        } catch (IOException e) {
            FileLog.e("Failed to save JSON for chat: " + chatName, e);
        } catch (Exception e) {
            FileLog.e("Failed to save JSON for chat: " + chatName, e);
        }
    }

    private String escapeJsonString(String str) {
        if (str == null) {
            return "";
        }
        return str.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private String escapeCsvField(String field) {
        if (field == null) {
            return "";
        }
        if (field.contains(",") || field.contains("\"") || field.contains("\n")) {
            return "\"" + field.replace("\"", "\"\"") + "\"";
        }
        return field;
    }

    private void ensureExportDirectory() {
        File dir = new File(EXPORT_DIR);
        if (!dir.exists() && !dir.mkdirs()) {
            FileLog.e("Failed to create private chat export directory: " + EXPORT_DIR);
        }
    }

    private String generateFileName(String chatName, boolean isCsv) {
        String timestamp = DATE_FORMAT.format(new Date());
        String extension = isCsv ? ".csv" : ".json";
        return chatName.replaceAll(FILENAME_REGEX, "_") + "_" + timestamp + extension;
    }

    private void handleError(String logMessage, String statusMessage) {
        FileLog.e(logMessage);
        finishExport(statusMessage, Theme.key_color_red);
    }

    private void finishExport(String message, int colorKey) {
        AndroidUtilities.runOnUIThread(() -> {
            statusTextView.setText(message);
            progressView.setProgress(1f, true);
            if (delegate != null) {
                delegate.onExportFinished(message, Theme.getColor(colorKey));
            }
        });
    }

    private void getMeToMessagesDialogsRecursive(
            long takeoutId,
            TLRPC.TL_messageRange range,
            List<TLRPC.Dialog> accumulatedDialogs,
            List<TLRPC.User> accumulatedUsers,
            List<TLRPC.Chat> accumulatedChats,
            int offsetDate,
            int offsetId,
            TLRPC.InputPeer offsetPeer,
            Runnable onComplete) {
        TLRPC.TL_messages_getDialogs getDialogs = new TLRPC.TL_messages_getDialogs();
        getDialogs.offset_date = offsetDate;
        getDialogs.offset_id = offsetId;
        getDialogs.offset_peer = offsetPeer;
        getDialogs.limit = DIALOGS_LIMIT;
        getDialogs.hash = 0;

        TL_takeout.TL_invokeWithMessagesRange rangeRequest = new TL_takeout.TL_invokeWithMessagesRange();
        rangeRequest.range = range;
        rangeRequest.query = getDialogs;

        TL_takeout.TL_invokeWithTakeout takeoutRequest = new TL_takeout.TL_invokeWithTakeout();
        takeoutRequest.takeout_id = takeoutId;
        takeoutRequest.query = rangeRequest;

        ConnectionsManager.getInstance(UserConfig.selectedAccount).sendRequest(takeoutRequest, (response, error) -> {
            if (error != null) {
                FileLog.e("Dialog fetch failed: " + error.text);
                onComplete.run();
                return;
            }

            if (response instanceof TLRPC.TL_messages_dialogsSlice) {
                TLRPC.TL_messages_dialogsSlice slice = (TLRPC.TL_messages_dialogsSlice) response;
                accumulatedDialogs.addAll(slice.dialogs);
                accumulatedUsers.addAll(slice.users);
                accumulatedChats.addAll(slice.chats);

                if (accumulatedDialogs.size() < slice.count && !slice.dialogs.isEmpty()) {
                    TLRPC.Dialog lastDialog = slice.dialogs.get(slice.dialogs.size() - 1);
                    int newOffsetId = lastDialog.top_message;
                    int newOffsetDate = lastDialog.last_message_date;
                    TLRPC.InputPeer newOffsetPeer = getInputPeerFromDialog(lastDialog, slice.users, slice.chats);

                    getMeToMessagesDialogsRecursive(
                            takeoutId, range,
                            accumulatedDialogs, accumulatedUsers, accumulatedChats,
                            newOffsetDate, newOffsetId, newOffsetPeer,
                            onComplete
                    );
                } else {
                    onComplete.run();
                }
            } else if (response instanceof TLRPC.TL_messages_dialogs) {
                TLRPC.TL_messages_dialogs dialogsResponse = (TLRPC.TL_messages_dialogs) response;
                accumulatedDialogs.addAll(dialogsResponse.dialogs);
                accumulatedUsers.addAll(dialogsResponse.users);
                accumulatedChats.addAll(dialogsResponse.chats);
                onComplete.run();
            } else {
                onComplete.run();
            }
        });
    }

    private void getMeToMessagesHistoryRecursive(
            long takeoutId,
            TLRPC.TL_messageRange range,
            TLRPC.InputPeer peer,
            int offsetId,
            int offsetDate,
            List<TLRPC.Message> accumulatedMessages,
            Runnable onComplete) {
        TLRPC.TL_messages_getHistory getHistory = new TLRPC.TL_messages_getHistory();
        getHistory.peer = peer;
        getHistory.offset_id = offsetId;
        getHistory.offset_date = offsetDate;
        getHistory.add_offset = 0;
        getHistory.limit = MESSAGES_LIMIT;

        TL_takeout.TL_invokeWithMessagesRange rangeRequest = new TL_takeout.TL_invokeWithMessagesRange();
        rangeRequest.range = range;
        rangeRequest.query = getHistory;

        TL_takeout.TL_invokeWithTakeout takeoutRequest = new TL_takeout.TL_invokeWithTakeout();
        takeoutRequest.takeout_id = takeoutId;
        takeoutRequest.query = rangeRequest;

        ConnectionsManager.getInstance(UserConfig.selectedAccount).sendRequest(takeoutRequest, (response, error) -> {
            if (error != null) {
                FileLog.e("History fetch failed: " + error.text);
                onComplete.run();
                return;
            }

            if (response instanceof TLRPC.messages_Messages) {
                TLRPC.messages_Messages messages = (TLRPC.messages_Messages) response;
                List<TLRPC.Message> batch = messages.messages;

                if (!batch.isEmpty()) {
                    accumulatedMessages.addAll(batch);
                    if (batch.size() == getHistory.limit) {
                        TLRPC.Message lastMessage = batch.get(batch.size() - 1);
                        getMeToMessagesHistoryRecursive(
                                takeoutId, range, peer,
                                lastMessage.id, lastMessage.date,
                                accumulatedMessages,
                                onComplete
                        );
                    } else {
                        onComplete.run();
                    }
                } else {
                    onComplete.run();
                }
            } else {
                onComplete.run();
            }
        });
    }

    private TLRPC.InputPeer getInputPeerFromDialog(TLRPC.Dialog dialog, List<TLRPC.User> users, List<TLRPC.Chat> chats) {
        if (dialog.peer instanceof TLRPC.TL_peerUser) {
            long userId = ((TLRPC.TL_peerUser) dialog.peer).user_id;
            for (TLRPC.User u : users) {
                if (u.id == userId) {
                    TLRPC.TL_inputPeerUser inp = new TLRPC.TL_inputPeerUser();
                    inp.user_id = u.id;
                    inp.access_hash = u.access_hash;
                    return inp;
                }
            }
        }
        return new TLRPC.TL_inputPeerEmpty();
    }
}
