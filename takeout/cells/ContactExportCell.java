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

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DispatchQueue;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
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

public class ContactExportCell extends FrameLayout {

    private static final String EXPORT_DIR_NAME = "Exported Data/Contacts";
    private static final int STEP_DELAY_MS = 30;
    private static final String CSV_HEADER = "First Name,Last Name,Phone Number,Added Date\n";
    private static final String DATE_FORMAT_PATTERN = "yyyyMMdd_HHmmss";
    private static final int JSON_INDENT = 2;

    public static final String EXPORT_DIR = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) + "/" + EXPORT_DIR_NAME;
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat(DATE_FORMAT_PATTERN, Locale.US);
    private static final DispatchQueue contactExportQueue = new DispatchQueue("ContactExportQueue");

    public interface ContactExportStatusDelegate {
        void onExportStatusUpdate(String status, int color);
        void onContactExported(int order, String contactName, int color, int totalSize);
        void onExportFinished(String status, int color);
    }

    private ContactExportStatusDelegate delegate;
    private TextCheckCell textCheckCell;
    public TextView statusTextView;
    public LineProgressView progressView;
    private boolean needDivider;
    private boolean exportAsCsv = true;

    public ContactExportCell(@NonNull Context context) {
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

    public void setContactExportStatusDelegate(ContactExportStatusDelegate delegate) {
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

    public void exportContacts(long takeoutId) {
        contactExportQueue.postRunnable(() -> {
            AndroidUtilities.runOnUIThread(() -> {
                if (delegate != null) {
                    delegate.onExportStatusUpdate("Starting export...", Theme.getColor(Theme.key_dialogTextGray3));
                }
            });

            TL_takeout.TL_contacts_getSaved contactsRequest = new TL_takeout.TL_contacts_getSaved();
            TL_takeout.TL_invokeWithTakeout invokeRequest = new TL_takeout.TL_invokeWithTakeout();
            invokeRequest.takeout_id = takeoutId;
            invokeRequest.query = contactsRequest;

            ConnectionsManager.getInstance(UserConfig.selectedAccount).sendRequest(invokeRequest, (response, error) -> {
                contactExportQueue.postRunnable(() -> {
                    if (error != null) {
                        FileLog.e("Contact export failed: " + error.text);
                        AndroidUtilities.runOnUIThread(() -> {
                            if (delegate != null) {
                                delegate.onExportStatusUpdate("Contacts export failed", Theme.getColor(Theme.key_color_red));
                            }
                        });
                        return;
                    }

                    if (!(response instanceof Vector)) {
                        FileLog.e("Invalid contact export response type");
                        return;
                    }

                    Vector vector = (Vector) response;
                    List<TL_takeout.TL_savedContact> contacts = new ArrayList<>();

                    contactExportQueue.postRunnable(() -> {
                        AndroidUtilities.runOnUIThread(() -> {
                            if (delegate != null) {
                                delegate.onExportStatusUpdate("Export ID = " + takeoutId, Theme.getColor(Theme.key_dialogTextGray3));
                            }
                        });

                        contactExportQueue.postRunnable(() -> {
                            AndroidUtilities.runOnUIThread(() -> {
                                if (delegate != null) {
                                    delegate.onExportStatusUpdate("Exporting " + vector.objects.size() + " Contacts", Theme.getColor(Theme.key_chat_attachFileText));
                                }
                            });

                            for (int i = 0; i < vector.objects.size(); i++) {
                                final int index = i;
                                contactExportQueue.postRunnable(() -> {
                                    TL_takeout.TL_savedContact contact = (TL_takeout.TL_savedContact) vector.objects.get(index);
                                    contacts.add(contact);

                                    String contactName = (contact.first_name != null ? contact.first_name : "") +
                                            (contact.last_name != null ? " " + contact.last_name : "").trim();

                                    AndroidUtilities.runOnUIThread(() -> {
                                        if (delegate != null) {
                                            delegate.onContactExported(
                                                    index + 1,
                                                    contactName,
                                                    Theme.getColor(Theme.key_chat_attachFileText),
                                                    vector.objects.size()
                                            );
                                        }
                                    });

                                    if (index == vector.objects.size() - 1) {
                                        contactExportQueue.postRunnable(() -> {
                                            boolean success = saveContactsToFile(contacts, exportAsCsv);
                                            AndroidUtilities.runOnUIThread(() -> {
                                                if (delegate != null) {
                                                    if (success) {
                                                        delegate.onExportFinished("Export Done! " + vector.objects.size() + " Contacts Exported.", Theme.getColor(Theme.key_chat_attachFileText));
                                                    } else {
                                                        delegate.onExportStatusUpdate("Failed to save file", Theme.getColor(Theme.key_color_red));
                                                    }
                                                }
                                            });
                                        }, STEP_DELAY_MS);
                                    }
                                }, (i + 1) * STEP_DELAY_MS);
                            }
                        }, STEP_DELAY_MS);
                    }, STEP_DELAY_MS);
                });
            });
        }, STEP_DELAY_MS);
    }

    private boolean saveContactsToFile(List<TL_takeout.TL_savedContact> contacts, boolean asCSV) {
        try {
            File exportDir = new File(EXPORT_DIR);
            if (!exportDir.exists() && !exportDir.mkdirs()) {
                FileLog.e("Failed to create contact export directory: " + EXPORT_DIR);
                return false;
            }

            String timestamp = DATE_FORMAT.format(new Date());
            String extension = asCSV ? ".csv" : ".json";
            String fileName = "contacts_" + timestamp + extension;
            File file = new File(exportDir, fileName);

            String fileContent = asCSV ? generateCsvContent(contacts) : generateJsonContent(contacts);

            try (FileWriter writer = new FileWriter(file)) {
                writer.write(fileContent);
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

    private String generateCsvContent(List<TL_takeout.TL_savedContact> contacts) {
        StringBuilder csv = new StringBuilder();
        csv.append(CSV_HEADER);

        for (TL_takeout.TL_savedContact contact : contacts) {
            csv.append(escapeCsvField(contact.first_name)).append(',')
                    .append(escapeCsvField(contact.last_name)).append(',')
                    .append(escapeCsvField(contact.phone)).append(',')
                    .append(new Date(contact.date * 1000L)).append('\n');
        }
        return csv.toString();
    }

    private String generateJsonContent(List<TL_takeout.TL_savedContact> contacts) {
        try {
            JSONArray jsonArray = new JSONArray();
            for (TL_takeout.TL_savedContact contact : contacts) {
                JSONObject jsonContact = new JSONObject();
                jsonContact.put("first_name", contact.first_name != null ? contact.first_name : "");
                jsonContact.put("last_name", contact.last_name != null ? contact.last_name : "");
                jsonContact.put("phone", contact.phone != null ? contact.phone : "");
                jsonContact.put("added_date", contact.date);
                jsonArray.put(jsonContact);
            }
            return jsonArray.toString(JSON_INDENT);
        } catch (JSONException e) {
            FileLog.e(e);
            return "[]";
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
