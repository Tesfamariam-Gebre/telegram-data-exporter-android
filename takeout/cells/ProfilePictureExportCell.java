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
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoadOperation;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.FilePathDatabase;
import org.telegram.messenger.ImageLocation;
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
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import plus.takeout.TL_takeout;

public class ProfilePictureExportCell extends FrameLayout {

    private static final String EXPORT_DIR_NAME = "Exported Data/Profile Picture";
    private static final int PHOTOS_LIMIT = 100;
    private static final String DATE_FORMAT_PATTERN = "yyyyMMdd_HHmmss";
    private static final String CSV_HEADER = "ID,Date,Size (KB),Dimensions,File Name\n";
    private static final int JSON_INDENT = 2;
    private static final int BYTES_PER_KB = 1024;
    private static final String PHOTO_EXTENSION = ".jpg";

    public static final String EXPORT_DIR = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) + "/" + EXPORT_DIR_NAME;
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat(DATE_FORMAT_PATTERN, Locale.US);

    public interface ProfilePictureExportDelegate {
        void onExportStatusUpdate(String status, int color);
        void onProfilePictureExported(int order, String infoName, int color, int totalSize);
        void onExportFinished(String status, int color);
    }

    private ProfilePictureExportDelegate delegate;
    private TextCheckCell textCheckCell;
    public TextView statusTextView;
    public LineProgressView progressView;
    private boolean needDivider;
    private File exportFolder;
    private List<TLRPC.Photo> photosList;
    private final AtomicInteger pendingDownloads = new AtomicInteger(0);
    private int totalPhotos;
    private boolean exportAsCsv = true;

    public ProfilePictureExportCell(@NonNull Context context) {
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

    public void setProfilePictureExportDelegate(ProfilePictureExportDelegate delegate) {
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

    public void exportProfilePicture(long takeoutId) {
        updateStatus("Fetching profile pictures...", Theme.key_dialogTextGray3);

        TLRPC.TL_photos_getUserPhotos request = new TLRPC.TL_photos_getUserPhotos();
        request.user_id = new TLRPC.TL_inputUserSelf();
        request.offset = 0;
        request.limit = PHOTOS_LIMIT;

        TL_takeout.TL_invokeWithTakeout invokeRequest = new TL_takeout.TL_invokeWithTakeout();
        invokeRequest.takeout_id = takeoutId;
        invokeRequest.query = request;

        ConnectionsManager.getInstance(UserConfig.selectedAccount).sendRequest(invokeRequest, (response, error) -> {
            if (error != null) {
                FileLog.e("Profile picture export failed: " + error.text);
                updateStatus("Export failed: " + error.text, Theme.key_color_red);
                if (delegate != null) {
                    delegate.onExportFinished("Export failed: " + error.text, Theme.getColor(Theme.key_color_red));
                }
                return;
            }

            if (!(response instanceof TLRPC.TL_photos_photos)) {
                FileLog.e("Invalid profile picture export response type");
                updateStatus("Invalid response format", Theme.key_color_red);
                if (delegate != null) {
                    delegate.onExportFinished("Invalid response format", Theme.getColor(Theme.key_color_red));
                }
                return;
            }

            TLRPC.TL_photos_photos photos = (TLRPC.TL_photos_photos) response;
            photosList = new ArrayList<>(photos.photos);
            totalPhotos = photosList.size();

            String folderName = "profile_pictures_" + DATE_FORMAT.format(new Date());
            exportFolder = new File(EXPORT_DIR, folderName);
            if (!exportFolder.exists() && !exportFolder.mkdirs()) {
                FileLog.e("Failed to create profile picture export folder: " + exportFolder.getAbsolutePath());
                updateStatus("Failed to create folder", Theme.key_color_red);
                if (delegate != null) {
                    delegate.onExportFinished("Failed to create folder", Theme.getColor(Theme.key_color_red));
                }
                return;
            }

            pendingDownloads.set(totalPhotos);
            if (totalPhotos == 0) {
                saveMetadata();
                return;
            }

            processPhotosWithDelay(photosList, 0);
        });
    }

    private void processPhotosWithDelay(List<TLRPC.Photo> photos, int index) {
        if (index >= photos.size()) {
            checkExportFinished();
            return;
        }

        TLRPC.Photo photo = photos.get(index);
        updatePhotoProgress(index + 1, photo, photos.size());
        pendingDownloads.incrementAndGet();
        downloadAndSavePhoto(photo, exportFolder, index + 1, photos.size());
        processPhotosWithDelay(photos, index + 1);
    }

    private void downloadAndSavePhoto(TLRPC.Photo photo, File folder, int order, int total) {
        TLRPC.PhotoSize photoSize = FileLoader.getClosestPhotoSizeWithSize(photo.sizes, Integer.MAX_VALUE);
        if (photoSize == null) {
            FileLog.e("No valid photo size for photo " + photo.id);
            pendingDownloads.decrementAndGet();
            checkExportFinished();
            return;
        }

        ImageLocation imageLocation = ImageLocation.getForPhoto(photoSize, photo);
        String fileName = getPhotoFileName(photo);
        File destFile = new File(folder, fileName);

        if (destFile.exists()) {
            FileLog.d("File already exists: " + destFile.getAbsolutePath());
            pendingDownloads.decrementAndGet();
            checkExportFinished();
            return;
        }

        FileLoadOperation operation = new FileLoadOperation(imageLocation, photo, null, 0);
        operation.setPriority(FileLoader.PRIORITY_HIGH);
        operation.setPaths(
                UserConfig.selectedAccount,
                fileName,
                operation.getQueue(),
                folder,
                folder,
                fileName
        );

        operation.setDelegate(new FileLoadOperation.FileLoadOperationDelegate() {
            @Override
            public void didPreFinishLoading(FileLoadOperation operation, File finalFile) {}

            @Override
            public void didFinishLoadingFile(FileLoadOperation operation, File finalFile) {
                if (finalFile.exists()) {
                    FileLog.d("Saved photo for photo " + photo.id + " at " + finalFile.getAbsolutePath());
                } else {
                    FileLog.e("File not found for photo " + photo.id + " at " + finalFile.getAbsolutePath());
                }
                pendingDownloads.decrementAndGet();
                updateProgress();
                checkExportFinished();
            }

            @Override
            public void didFailedLoadingFile(FileLoadOperation operation, int state) {
                FileLog.e("Failed to save photo for photo " + photo.id + ": state=" + state);
                pendingDownloads.decrementAndGet();
                updateProgress();
                checkExportFinished();
            }

            @Override
            public void didChangedLoadProgress(FileLoadOperation operation, long uploadedSize, long totalSize) {}

            @Override
            public void saveFilePath(FilePathDatabase.PathData pathSaveData, File cacheFileFinal) {}

            @Override
            public boolean hasAnotherRefOnFile(String path) {
                return false;
            }

            @Override
            public boolean isLocallyCreatedFile(String path) {
                return false;
            }
        });

        if (!operation.start()) {
            FileLog.e("Failed to start download for photo " + photo.id);
            pendingDownloads.decrementAndGet();
            checkExportFinished();
        }
    }

    private void checkExportFinished() {
        if (pendingDownloads.get() == 0) {
            saveMetadata();
        }
    }

    private void saveMetadata() {
        updateStatus("Saving metadata...", Theme.key_dialogTextGray3);

        new Thread(() -> {
            try {
                List<Map<String, Object>> metadata = new ArrayList<>();
                for (TLRPC.Photo photo : photosList) {
                    metadata.add(createPhotoEntry(photo));
                }

                boolean success = exportAsCsv ? saveAsCsv(metadata) : saveAsJson(metadata);

                AndroidUtilities.runOnUIThread(() -> {
                    if (success) {
                        updateStatus("Exported " + photosList.size() + " photos", Theme.key_chat_attachFileText);
                        if (delegate != null) {
                            delegate.onExportFinished("Exported " + photosList.size() + " photos", Theme.getColor(Theme.key_chat_attachFileText));
                        }
                    } else {
                        updateStatus("Metadata save failed", Theme.key_color_red);
                        if (delegate != null) {
                            delegate.onExportFinished("Metadata save failed", Theme.getColor(Theme.key_color_red));
                        }
                    }
                });
            } catch (Exception e) {
                FileLog.e("Error saving profile picture metadata", e);
                AndroidUtilities.runOnUIThread(() -> {
                    updateStatus("Error: " + e.getMessage(), Theme.key_color_red);
                    if (delegate != null) {
                        delegate.onExportFinished("Error: " + e.getMessage(), Theme.getColor(Theme.key_color_red));
                    }
                });
            }
        }).start();
    }

    private Map<String, Object> createPhotoEntry(TLRPC.Photo photo) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("id", photo.id);
        entry.put("date", new Date(photo.date * 1000L));
        entry.put("size", getPhotoSize(photo));
        entry.put("dimensions", getPhotoDimensions(photo));
        entry.put("file_name", getPhotoFileName(photo));
        return entry;
    }

    private boolean saveAsCsv(List<Map<String, Object>> metadata) {
        try (FileWriter writer = new FileWriter(new File(exportFolder, "metadata.csv"))) {
            writer.write(CSV_HEADER);
            for (Map<String, Object> entry : metadata) {
                writer.write(String.format(Locale.US, "%s,%s,%s,%s,%s\n",
                        escapeCsvField(entry.get("id").toString()),
                        escapeCsvField(DATE_FORMAT.format(entry.get("date"))),
                        entry.get("size"),
                        escapeCsvField((String) entry.get("dimensions")),
                        escapeCsvField((String) entry.get("file_name"))
                ));
            }
            return true;
        } catch (IOException e) {
            FileLog.e("Failed to save CSV", e);
            return false;
        } catch (Exception e) {
            FileLog.e("Failed to save CSV", e);
            return false;
        }
    }

    private boolean saveAsJson(List<Map<String, Object>> metadata) {
        try (FileWriter writer = new FileWriter(new File(exportFolder, "metadata.json"))) {
            JSONArray jsonArray = new JSONArray();
            for (Map<String, Object> entry : metadata) {
                JSONObject jsonEntry = new JSONObject();
                jsonEntry.put("id", entry.get("id"));
                jsonEntry.put("date", DATE_FORMAT.format(entry.get("date")));
                jsonEntry.put("size_kb", entry.get("size"));
                jsonEntry.put("dimensions", entry.get("dimensions"));
                jsonEntry.put("file_name", entry.get("file_name"));
                jsonArray.put(jsonEntry);
            }
            writer.write(jsonArray.toString(JSON_INDENT));
            return true;
        } catch (IOException e) {
            FileLog.e("Failed to save JSON", e);
            return false;
        } catch (Exception e) {
            FileLog.e("Failed to save JSON", e);
            return false;
        }
    }

    private String getPhotoFileName(TLRPC.Photo photo) {
        return "profile_" + DATE_FORMAT.format(new Date(photo.date * 1000L)) + PHOTO_EXTENSION;
    }

    private String getPhotoDimensions(TLRPC.Photo photo) {
        TLRPC.PhotoSize size = FileLoader.getClosestPhotoSizeWithSize(photo.sizes, Integer.MAX_VALUE);
        if (size != null) {
            return size.w + "x" + size.h;
        }
        return "unknown";
    }

    private int getPhotoSize(TLRPC.Photo photo) {
        TLRPC.PhotoSize size = FileLoader.getClosestPhotoSizeWithSize(photo.sizes, Integer.MAX_VALUE);
        if (size != null) {
            return (int) (size.size / BYTES_PER_KB);
        }
        return 0;
    }

    private void updatePhotoProgress(int order, TLRPC.Photo photo, int total) {
        AndroidUtilities.runOnUIThread(() -> {
            if (delegate != null) {
                delegate.onProfilePictureExported(
                        order,
                        "Photo " + DATE_FORMAT.format(new Date(photo.date * 1000L)),
                        Theme.getColor(Theme.key_chat_attachFileText),
                        total
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

    private void updateProgress() {
        if (totalPhotos > 0) {
            float progress = 1f - ((float) pendingDownloads.get() / totalPhotos);
            AndroidUtilities.runOnUIThread(() -> progressView.setProgress(progress, true));
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
