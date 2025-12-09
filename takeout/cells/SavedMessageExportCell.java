/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Created by Tesfamariam Gebre.
 */

package plus.takeout.cells;

import static org.telegram.messenger.MediaDataController.getMediaType;

import android.content.Context;
import android.graphics.Canvas;
import android.os.Environment;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DispatchQueue;
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

import plus.takeout.TL_takeout;

public class SavedMessageExportCell extends FrameLayout {

    private static final String EXPORT_DIR_NAME = "Exported Data/Saved Message";
    private static final int INITIAL_DELAY_MS = 500;
    private static final int STEP_DELAY_MS = 300;
    private static final int FIELD_DELAY_MS = 150;
    private static final int MESSAGE_LIMIT = 100;
    private static final float PROGRESS_INCREMENT = 0.3f;
    private static final float MAX_PROGRESS = 0.95f;
    private static final String CSV_HEADER = "ID,Date,Message,MediaType\n";
    private static final String DATE_FORMAT_PATTERN = "yyyyMMdd_HHmmss";
    private static final int JSON_INDENT = 2;

    public static final String EXPORT_DIR = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) + "/" + EXPORT_DIR_NAME;
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat(DATE_FORMAT_PATTERN, Locale.US);
    private static final DispatchQueue exportQueue = new DispatchQueue("SavedMessageExportQueue");

    public interface SavedMessageExportDelegate {
        void onExportStatusUpdate(String status, int color);
        void onSavedMessageExported(int order, String contactName, int color, int totalSize);
        void onExportFinished(String status, int color);
    }

    private SavedMessageExportDelegate delegate;
    private TextCheckCell textCheckCell;
    public TextView statusTextView;
    public LineProgressView progressView;
    private boolean needDivider;
    private List<TLRPC.Message> allMessages = new ArrayList<>();
    private int processedMessages = 0;
    private int processedBatches = 0;
    private boolean exportAsCsv = true;

    public SavedMessageExportCell(@NonNull Context context) {
        super(context);

        textCheckCell = new TextCheckCell(context);
        addView(textCheckCell, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        statusTextView = new TextView(context);
        statusTextView.setTextColor(Theme.getColor(Theme.key_chat_attachFileText));
        statusTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        statusTextView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        statusTextView.setLines(1);
        statusTextView.setMaxLines(1);
        statusTextView.setSingleLine(true);
        statusTextView.setPadding(0, 0, 0, 0);
        statusTextView.setEllipsize(TextUtils.TruncateAt.END);
        addView(statusTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT,
                (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, 21, 35, 21, 0));

        progressView = new LineProgressView(context);
        progressView.setProgressColor(Theme.getColor(Theme.key_sharedMedia_startStopLoadIcon));
        addView(progressView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 5,
                (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, 21, 60, 21, 0));
    }

    public void setSavedMessageExportDelegate(SavedMessageExportDelegate delegate) {
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

    public void exportSavedMessages(long takeoutId) {
        updateStatus("Initializing export...", Theme.key_chat_attachFileText);

        TL_takeout.TL_messages_getSplitRanges getSplitRanges = new TL_takeout.TL_messages_getSplitRanges();
        TL_takeout.TL_invokeWithTakeout invokeSplitRangesRequest = new TL_takeout.TL_invokeWithTakeout();
        invokeSplitRangesRequest.takeout_id = takeoutId;
        invokeSplitRangesRequest.query = getSplitRanges;

        ConnectionsManager.getInstance(UserConfig.selectedAccount).sendRequest(invokeSplitRangesRequest, (response, error) -> {
            exportQueue.postRunnable(() -> {
                if (error != null) {
                    FileLog.e("Saved message export failed: " + error.text);
                    updateStatus("Export failed: " + error.text, Theme.key_color_red);
                    if (delegate != null) {
                        delegate.onExportFinished("Export failed: " + error.text, Theme.getColor(Theme.key_color_red));
                    }
                    return;
                }

                if (response instanceof Vector) {
                    Vector<TLRPC.TL_messageRange> ranges = (Vector<TLRPC.TL_messageRange>) response;
                    exportQueue.postRunnable(() -> {
                        processRangesSequentially(takeoutId, ranges, 0);
                    }, FIELD_DELAY_MS);
                } else {
                    FileLog.e("Invalid saved message export response type");
                    updateStatus("Invalid response format", Theme.key_color_red);
                    if (delegate != null) {
                        delegate.onExportFinished("Invalid response format", Theme.getColor(Theme.key_color_red));
                    }
                }
            });
        });
    }

    private void processRangesSequentially(long takeoutId, Vector<TLRPC.TL_messageRange> ranges, int index) {
        if (index >= ranges.objects.size()) {
            saveMessagesToFile();
            return;
        }

        TLRPC.TL_messageRange range = ranges.objects.get(index);
        List<TLRPC.Message> rangeMessages = new ArrayList<>();

        processSavedMessagesRange(takeoutId, range, 0, rangeMessages, () -> {
            processedBatches++;
            allMessages.addAll(rangeMessages);
            processedMessages += rangeMessages.size();
            updateProgress();
            processRangesSequentially(takeoutId, ranges, index + 1);
        });
    }

    private void processSavedMessagesRange(long takeoutId, TLRPC.TL_messageRange range, int offsetId,
                                           List<TLRPC.Message> accumulatedMessages, Runnable completion) {
        TLRPC.TL_messages_search searchQuery = new TLRPC.TL_messages_search();
        searchQuery.peer = new TLRPC.TL_inputPeerSelf();
        searchQuery.filter = new TLRPC.TL_inputMessagesFilterEmpty();
        searchQuery.offset_id = offsetId;
        searchQuery.add_offset = 0;
        searchQuery.limit = MESSAGE_LIMIT;
        searchQuery.max_id = 0;
        searchQuery.min_id = 0;

        TL_takeout.TL_invokeWithMessagesRange rangeRequest = new TL_takeout.TL_invokeWithMessagesRange();
        rangeRequest.range = range;
        rangeRequest.query = searchQuery;

        TL_takeout.TL_invokeWithTakeout takeoutRequest = new TL_takeout.TL_invokeWithTakeout();
        takeoutRequest.takeout_id = takeoutId;
        takeoutRequest.query = rangeRequest;

        ConnectionsManager.getInstance(UserConfig.selectedAccount).sendRequest(takeoutRequest, (response, error) -> {
            exportQueue.postRunnable(() -> {
                if (error != null) {
                    FileLog.e("Range request failed: " + error.text);
                    updateStatus("Range request failed: " + error.text, Theme.key_color_red);
                    completion.run();
                    return;
                }

                if (response instanceof TLRPC.messages_Messages) {
                    TLRPC.messages_Messages messages = (TLRPC.messages_Messages) response;
                    List<TLRPC.Message> batch = messages.messages;

                    if (!batch.isEmpty()) {
                        accumulatedMessages.addAll(batch);
                        updateProgress();

                        AndroidUtilities.runOnUIThread(() -> {
                            if (delegate != null) {
                                delegate.onSavedMessageExported(
                                        accumulatedMessages.size(),
                                        "Processing batch...",
                                        Theme.getColor(Theme.key_chat_attachFileText),
                                        batch.size()
                                );
                            }
                        });

                        int nextOffset = batch.get(batch.size() - 1).id - 1;

                        if (batch.size() == searchQuery.limit) {
                            processSavedMessagesRange(takeoutId, range, nextOffset, accumulatedMessages, completion);
                        } else {
                            completion.run();
                        }
                    } else {
                        completion.run();
                    }
                } else {
                    FileLog.e("Invalid range response type");
                    completion.run();
                }
            });
        });
    }

    private void saveMessagesToFile() {
        exportQueue.postRunnable(() -> {
            updateStatus("Saving messages...", Theme.key_chat_attachFileText);

            new Thread(() -> {
                boolean success = exportAsCsv ? saveAsCsv(allMessages) : saveAsJson(allMessages);

                exportQueue.postRunnable(() -> {
                    if (success) {
                        updateStatus("Exported " + allMessages.size() + " messages", Theme.key_chat_attachFileText);
                        AndroidUtilities.runOnUIThread(() -> {
                            progressView.setProgress(1, true);
                            if (delegate != null) {
                                delegate.onExportFinished("Export completed", Theme.getColor(Theme.key_chat_attachFileText));
                            }
                        });
                    } else {
                        updateStatus("Save failed", Theme.key_color_red);
                        AndroidUtilities.runOnUIThread(() -> {
                            if (delegate != null) {
                                delegate.onExportFinished("Export failed", Theme.getColor(Theme.key_color_red));
                            }
                        });
                    }
                });
            }).start();
        }, STEP_DELAY_MS);
    }

    private boolean saveAsCsv(List<TLRPC.Message> messages) {
        try (FileWriter writer = new FileWriter(getExportFile(".csv"))) {
            writer.write(CSV_HEADER);
            for (TLRPC.Message message : messages) {
                writer.write(String.format(Locale.US, "%d,%s,%s,%s\n",
                        message.id,
                        DATE_FORMAT.format(new Date(message.date * 1000L)),
                        escapeCsvField(message.message),
                        getMediaType(message)
                ));
            }
            return true;
        } catch (IOException e) {
            FileLog.e("CSV save failed", e);
            return false;
        } catch (Exception e) {
            FileLog.e("CSV save failed", e);
            return false;
        }
    }

    private boolean saveAsJson(List<TLRPC.Message> messages) {
        try (FileWriter writer = new FileWriter(getExportFile(".json"))) {
            JSONArray jsonArray = new JSONArray();
            for (TLRPC.Message message : messages) {
                JSONObject jsonMessage = new JSONObject();
                jsonMessage.put("id", message.id);
                jsonMessage.put("date", DATE_FORMAT.format(new Date(message.date * 1000L)));
                jsonMessage.put("message", message.message != null ? message.message : "");
                jsonMessage.put("media_type", getMediaType(message));
                jsonArray.put(jsonMessage);
            }
            writer.write(jsonArray.toString(JSON_INDENT));
            return true;
        } catch (IOException e) {
            FileLog.e("JSON save failed", e);
            return false;
        } catch (Exception e) {
            FileLog.e("JSON save failed", e);
            return false;
        }
    }

    private File getExportFile(String extension) {
        String fileName = "saved_messages_" + DATE_FORMAT.format(new Date()) + extension;
        File dir = new File(EXPORT_DIR);
        if (!dir.exists() && !dir.mkdirs()) {
            FileLog.e("Failed to create saved message export directory: " + EXPORT_DIR);
        }
        return new File(dir, fileName);
    }

    private void updateProgress() {
        AndroidUtilities.runOnUIThread(() -> {
            float remaining = 1f - progressView.getCurrentProgress();
            float increment = remaining * PROGRESS_INCREMENT;
            float newProgress = Math.min(progressView.getCurrentProgress() + increment, MAX_PROGRESS);
            progressView.setProgress(newProgress, true);

            if (delegate != null) {
                delegate.onSavedMessageExported(
                        processedBatches,
                        "Processing messages...",
                        Theme.getColor(Theme.key_chat_attachFileText),
                        0
                );
            }
        });
    }

    private void updateStatus(String text, int colorKey) {
        AndroidUtilities.runOnUIThread(() -> {
            if (delegate != null) {
                delegate.onExportStatusUpdate(text, Theme.getColor(colorKey));
            }
        });
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
}
