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
import android.os.Handler;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DispatchQueue;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.LineProgressView;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import plus.takeout.TL_takeout;

public class PersonalInfoExportCell extends FrameLayout {

    private static final String EXPORT_DIR_NAME = "Exported Data/Personal Info";
    private static final int INITIAL_DELAY_MS = 500;
    private static final int STEP_DELAY_MS = 300;
    private static final int FIELD_DELAY_MS = 150;
    private static final String CSV_HEADER = "Field Name,Value\n";
    private static final String DATE_FORMAT_PATTERN = "yyyyMMdd_HHmmss";
    private static final int JSON_INDENT = 2;

    public static final String EXPORT_DIR = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) + "/" + EXPORT_DIR_NAME;
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat(DATE_FORMAT_PATTERN, Locale.US);
    private static final DispatchQueue exportQueue = new DispatchQueue("PersonalInfoExportQueue");

    public interface PersonalInfoExportDelegate {
        void onExportStatusUpdate(String status, int color);
        void onPersonalInfoExported(int order, String infoName, int color, int totalSize);
        void onExportFinished(String status, int color);
    }

    private PersonalInfoExportDelegate delegate;
    private TextCheckCell textCheckCell;
    public TextView statusTextView;
    public LineProgressView progressView;
    private boolean needDivider;
    private boolean exportAsCsv = true;

    public PersonalInfoExportCell(@NonNull Context context) {
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

    public void setPersonalInfoExportDelegate(PersonalInfoExportDelegate delegate) {
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

    public void exportPersonalInfo(long takeoutId) {
        exportQueue.postRunnable(() -> {
            updateStatus("Starting export...", Theme.key_dialogTextGray3);

            TLRPC.TL_users_getFullUser userRequest = new TLRPC.TL_users_getFullUser();
            userRequest.id = new TLRPC.TL_inputUserSelf();

            TL_takeout.TL_invokeWithTakeout invokeRequest = new TL_takeout.TL_invokeWithTakeout();
            invokeRequest.takeout_id = takeoutId;
            invokeRequest.query = userRequest;

            ConnectionsManager.getInstance(UserConfig.selectedAccount).sendRequest(invokeRequest, (response, error) -> {
                exportQueue.postRunnable(() -> {
                    if (error != null) {
                        FileLog.e("Personal info export failed: " + error.text);
                        updateStatus("Export failed: " + error.text, Theme.key_color_red);
                        return;
                    }

                    if (!(response instanceof TLRPC.TL_users_userFull)) {
                        FileLog.e("Invalid personal info export response type");
                        updateStatus("Invalid response format", Theme.key_color_red);
                        return;
                    }

                    TLRPC.TL_users_userFull userFull = (TLRPC.TL_users_userFull) response;
                    TLRPC.User user = UserConfig.getInstance(UserConfig.selectedAccount).getCurrentUser();
                    if (user == null) {
                        FileLog.e("Current user is null");
                        updateStatus("User not found", Theme.key_color_red);
                        return;
                    }

                    List<String> fields = Arrays.asList("First Name", "Last Name", "Username", "Phone", "Bio");
                    processFieldsWithDelay(exportQueue.getHandler(), user, userFull, fields, 0);
                });
            });
        }, INITIAL_DELAY_MS);
    }

    private void updateStatus(String text, int colorKey) {
        AndroidUtilities.runOnUIThread(() -> {
            if (delegate != null) {
                delegate.onExportStatusUpdate(text, Theme.getColor(colorKey));
            }
        });
    }

    private void processFieldsWithDelay(Handler handler, TLRPC.User user,
                                        TLRPC.TL_users_userFull userFull,
                                        List<String> fields, int index) {
        if (index >= fields.size()) {
            saveFinalFile(handler, user, userFull);
            return;
        }

        handler.postDelayed(() -> {
            String fieldName = fields.get(index);
            updateFieldProgress(index + 1, fieldName, fields.size());
            processFieldsWithDelay(handler, user, userFull, fields, index + 1);
        }, FIELD_DELAY_MS);
    }

    private void updateFieldProgress(int order, String fieldName, int total) {
        AndroidUtilities.runOnUIThread(() -> {
            if (delegate != null) {
                delegate.onPersonalInfoExported(
                        order,
                        fieldName,
                        Theme.getColor(Theme.key_chat_attachFileText),
                        total
                );
            }
        });
    }

    private void saveFinalFile(Handler handler, TLRPC.User user, TLRPC.TL_users_userFull userFull) {
        handler.postDelayed(() -> {
            updateStatus("Saving to file...", Theme.key_dialogTextGray3);

            new Thread(() -> {
                try {
                    Map<String, String> data = new LinkedHashMap<>();
                    data.put("First Name", user.first_name != null ? user.first_name : "");
                    data.put("Last Name", user.last_name != null ? user.last_name : "");
                    data.put("Username", user.username != null ? user.username : "");
                    data.put("Phone", user.phone != null ? user.phone : "");
                    data.put("Bio", userFull.full_user != null && userFull.full_user.about != null ? userFull.full_user.about : "");

                    boolean success = exportAsCsv ? saveAsCsv(data) : saveAsJson(data);

                    handler.post(() -> {
                        if (success) {
                            updateStatus("Export completed!", Theme.key_chat_attachFileText);
                            if (delegate != null) {
                                delegate.onExportFinished("Export completed!", Theme.getColor(Theme.key_chat_attachFileText));
                            }
                        } else {
                            updateStatus("File save failed", Theme.key_color_red);
                            if (delegate != null) {
                                delegate.onExportFinished("File save failed", Theme.getColor(Theme.key_color_red));
                            }
                        }
                    });
                } catch (Exception e) {
                    FileLog.e(e);
                    handler.post(() -> {
                        updateStatus("Error: " + e.getMessage(), Theme.key_color_red);
                        if (delegate != null) {
                            delegate.onExportFinished("Error: " + e.getMessage(), Theme.getColor(Theme.key_color_red));
                        }
                    });
                }
            }).start();
        }, STEP_DELAY_MS);
    }

    private boolean saveAsCsv(Map<String, String> data) {
        try {
            File exportDir = new File(EXPORT_DIR);
            if (!exportDir.exists() && !exportDir.mkdirs()) {
                FileLog.e("Failed to create personal info export directory: " + EXPORT_DIR);
                return false;
            }

            String timestamp = DATE_FORMAT.format(new Date());
            File file = new File(exportDir, "personal_info_" + timestamp + ".csv");

            try (FileWriter writer = new FileWriter(file)) {
                writer.write(CSV_HEADER);
                for (Map.Entry<String, String> entry : data.entrySet()) {
                    writer.write(escapeCsvField(entry.getKey()) + ",");
                    writer.write(escapeCsvField(entry.getValue()) + "\n");
                }
                return true;
            } catch (IOException e) {
                FileLog.e(e);
                return false;
            }
        } catch (Exception e) {
            FileLog.e(e);
            return false;
        }
    }

    private boolean saveAsJson(Map<String, String> data) {
        try {
            JSONObject json = new JSONObject();
            for (Map.Entry<String, String> entry : data.entrySet()) {
                json.put(entry.getKey(), entry.getValue() != null ? entry.getValue() : "");
            }

            File exportDir = new File(EXPORT_DIR);
            if (!exportDir.exists() && !exportDir.mkdirs()) {
                FileLog.e("Failed to create personal info export directory: " + EXPORT_DIR);
                return false;
            }

            String timestamp = DATE_FORMAT.format(new Date());
            File file = new File(exportDir, "personal_info_" + timestamp + ".json");

            try (FileWriter writer = new FileWriter(file)) {
                writer.write(json.toString(JSON_INDENT));
                return true;
            } catch (IOException e) {
                FileLog.e(e);
                return false;
            }
        } catch (Exception e) {
            FileLog.e(e);
            return false;
        }
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
