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
import org.telegram.messenger.DispatchQueue;
import org.telegram.messenger.FileLoadOperation;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.FilePathDatabase;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_stories;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.LineProgressView;

import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import plus.takeout.TL_takeout;

public class StoryExportCell extends FrameLayout {

    private static final String EXPORT_DIR_NAME = "Exported Data/Story";
    private static final long DOWNLOAD_TIMEOUT_SECONDS = 30;
    private static final String DATE_FORMAT_PATTERN = "yyyyMMdd_HHmmss";
    private static final String CSV_HEADER = "ID,Date,Caption,Media Type,File Name\n";
    private static final int JSON_INDENT = 2;
    private static final String MIME_VIDEO_PREFIX = "video/";
    private static final String MIME_AUDIO_PREFIX = "audio/";
    private static final String PHOTO_EXTENSION = ".jpg";
    private static final String DEFAULT_EXTENSION = ".dat";

    public static final String EXPORT_DIR = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) + "/" + EXPORT_DIR_NAME;
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat(DATE_FORMAT_PATTERN, Locale.US);
    private static final DispatchQueue exportQueue = new DispatchQueue("StoryExportQueue");

    public interface StoryExportDelegate {
        void onExportStatusUpdate(String status, int color);
        void onStoryExported(int order, String infoName, int color, int totalSize);
        void onExportFinished(String status, int color);
    }

    private StoryExportDelegate delegate;
    private TextCheckCell textCheckCell;
    public TextView statusTextView;
    public LineProgressView progressView;
    private boolean needDivider;
    private boolean exportAsCsv = true;
    private File exportFolder;
    private final AtomicInteger pendingDownloads = new AtomicInteger(0);
    private List<TL_stories.StoryItem> storiesToExport = new ArrayList<>();
    private int totalStories = 0;
    private final HashMap<Integer, String> storyIdToFileName = new HashMap<>();

    public StoryExportCell(@NonNull Context context) {
        super(context);
        initializeViews();
    }

    private void initializeViews() {
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
                (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, 21, 35, 21, 0));

        progressView = new LineProgressView(getContext());
        progressView.setProgressColor(Theme.getColor(Theme.key_sharedMedia_startStopLoadIcon));
        addView(progressView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 5,
                (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, 21, 60, 21, 0));
    }

    public void setStoryExportDelegate(StoryExportDelegate delegate) {
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

    public void exportStory(long takeoutId) {
        exportQueue.postRunnable(() -> {
            updateStatus("Initializing story export...", Theme.key_dialogTextGray3);

            TL_stories.TL_stories_getStoriesArchive storiesRequest = new TL_stories.TL_stories_getStoriesArchive();
            storiesRequest.peer = new TLRPC.TL_inputPeerSelf();

            TL_takeout.TL_invokeWithTakeout invokeRequest = new TL_takeout.TL_invokeWithTakeout();
            invokeRequest.takeout_id = takeoutId;
            invokeRequest.query = storiesRequest;

            ConnectionsManager.getInstance(UserConfig.selectedAccount).sendRequest(invokeRequest, (response, error) -> {
                exportQueue.postRunnable(() -> {
                    if (error != null) {
                        FileLog.e("Story export failed: " + error.text);
                        handleError("Export failed: " + error.text, "Export failed");
                        return;
                    }

                    if (!(response instanceof TL_stories.TL_stories_stories)) {
                        FileLog.e("Invalid story export response type");
                        handleError("Invalid response format", "Export failed");
                        return;
                    }

                    TL_stories.TL_stories_stories tlStories = (TL_stories.TL_stories_stories) response;
                    storiesToExport = new ArrayList<>(tlStories.stories);
                    totalStories = storiesToExport.size();
                    pendingDownloads.set(totalStories);
                    storyIdToFileName.clear();

                    if (storiesToExport.isEmpty()) {
                        finishExport("No stories found", Theme.key_color_red);
                        return;
                    }

                    String folderName = "stories_" + DATE_FORMAT.format(new Date());
                    exportFolder = new File(EXPORT_DIR, folderName);
                    File baseDir = new File(EXPORT_DIR);
                    if (!baseDir.exists() && !baseDir.mkdirs()) {
                        FileLog.e("Failed to create story export directory: " + EXPORT_DIR);
                        handleError("Failed to create export directory", "Export failed");
                        return;
                    }
                    if (!exportFolder.exists() && !exportFolder.mkdirs()) {
                        FileLog.e("Failed to create story export folder: " + exportFolder.getAbsolutePath());
                        handleError("Failed to create folder", "Export failed");
                        return;
                    }

                    for (int i = 0; i < storiesToExport.size(); i++) {
                        TL_stories.StoryItem story = storiesToExport.get(i);
                        updateStoryProgress(i + 1, story, totalStories);
                        saveMediaFile(story, exportFolder);
                    }
                });
            });
        });
    }

    private void saveMediaFile(TL_stories.StoryItem story, File folder) {
        if (story.media == null || !story.isPublic) {
            pendingDownloads.decrementAndGet();
            checkExportFinished();
            return;
        }

        if (story.media.photo != null) {
            savePhotoFile(story.media.photo, folder, story.id);
        } else if (story.media.document != null) {
            saveDocumentFile(story.media.document, folder, story.id);
        } else {
            pendingDownloads.decrementAndGet();
            checkExportFinished();
        }
    }

    private void savePhotoFile(TLRPC.Photo photo, File folder, int storyId) {
        if (photo == null) {
            FileLog.e("No photo found for story " + storyId);
            pendingDownloads.decrementAndGet();
            checkExportFinished();
            return;
        }

        try {
            TLRPC.PhotoSize photoSize = FileLoader.getClosestPhotoSizeWithSize(photo.sizes, Integer.MAX_VALUE);
            if (photoSize == null) {
                FileLog.e("No valid photo size for story " + storyId);
                pendingDownloads.decrementAndGet();
                checkExportFinished();
                return;
            }

            ImageLocation imageLocation = ImageLocation.getForPhoto(photoSize, photo);
            String fileExtension = getPhotoExtension(photoSize);
            String fileName = "story_" + storyId + fileExtension;
            downloadMediaFile(imageLocation, photo, folder, fileName, storyId);
        } catch (Exception e) {
            FileLog.e("Failed to save photo for story " + storyId, e);
            pendingDownloads.decrementAndGet();
            checkExportFinished();
        }
    }

    private void saveDocumentFile(TLRPC.Document document, File folder, int storyId) {
        if (document == null) {
            FileLog.e("No document found for story " + storyId);
            pendingDownloads.decrementAndGet();
            checkExportFinished();
            return;
        }

        try {
            ImageLocation imageLocation = ImageLocation.getForDocument(document);
            String fileExtension = getDocumentExtension(document);
            String fileName = "story_" + storyId + fileExtension;
            downloadMediaFile(imageLocation, document, folder, fileName, storyId);
        } catch (Exception e) {
            FileLog.e("Failed to save document for story " + storyId, e);
            pendingDownloads.decrementAndGet();
            checkExportFinished();
        }
    }

    private void downloadMediaFile(ImageLocation imageLocation, Object parent, File folder, String fileName, int storyId) {
        FileLoadOperation operation = new FileLoadOperation(
                imageLocation,
                parent,
                null,
                0
        );

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
                storyIdToFileName.put(storyId, fileName);
                pendingDownloads.decrementAndGet();
                checkExportFinished();
            }

            @Override
            public void didFailedLoadingFile(FileLoadOperation operation, int state) {
                FileLog.e("Failed to save media for story " + storyId + ": state=" + state);
                pendingDownloads.decrementAndGet();
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

        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean success = new AtomicBoolean(false);

        Utilities.stageQueue.postRunnable(() -> {
            if (operation.start()) {
                success.set(true);
            }
            latch.countDown();
        });

        try {
            if (!latch.await(DOWNLOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                FileLog.e("Download start timeout for story " + storyId);
                pendingDownloads.decrementAndGet();
                checkExportFinished();
                return;
            }

            if (!success.get()) {
                FileLog.e("Download start failed for story " + storyId);
                pendingDownloads.decrementAndGet();
                checkExportFinished();
            }
        } catch (InterruptedException e) {
            FileLog.e("Download interrupted for story " + storyId, e);
            Thread.currentThread().interrupt();
            pendingDownloads.decrementAndGet();
            checkExportFinished();
        }
    }

    private void saveMetadata() {
        exportQueue.postRunnable(() -> {
            updateStatus("Saving metadata...", Theme.key_dialogTextGray3);
            try {
                List<Map<String, Object>> metadata = new ArrayList<>();
                for (TL_stories.StoryItem story : storiesToExport) {
                    metadata.add(createStoryEntry(story));
                }

                boolean success = exportAsCsv ? saveAsCsv(metadata) : saveAsJson(metadata);
                if (success) {
                    finishExport("Exported " + totalStories + " stories", Theme.key_chat_attachFileText);
                } else {
                    FileLog.e("Metadata save failed");
                    handleError("Metadata save failed", "Metadata save failed");
                }
            } catch (Exception e) {
                FileLog.e("Error saving metadata", e);
                handleError("Metadata save failed: " + e.getMessage(), "Metadata save failed");
            }
        });
    }

    private Map<String, Object> createStoryEntry(TL_stories.StoryItem story) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("id", story.id);
        entry.put("date", new Date(story.date * 1000L));
        entry.put("caption", story.caption != null ? story.caption : "");
        entry.put("media_type", getMediaType(story.media));

        String fileName = storyIdToFileName.get(story.id);
        if (fileName == null) {
            fileName = "story_" + story.id;
            if (story.media != null) {
                if (story.media.photo != null) {
                    fileName += PHOTO_EXTENSION;
                } else if (story.media.document != null) {
                    fileName += getDocumentExtension(story.media.document);
                }
            }
        }
        entry.put("file_name", fileName);

        return entry;
    }

    private boolean saveAsCsv(List<Map<String, Object>> metadata) {
        try (FileWriter writer = new FileWriter(new File(exportFolder, "metadata.csv"))) {
            writer.write(CSV_HEADER);
            for (Map<String, Object> entry : metadata) {
                writer.write(String.format(Locale.US, "%s,%s,%s,%s,%s\n",
                        escapeCsvField(entry.get("id").toString()),
                        escapeCsvField(entry.get("date").toString()),
                        escapeCsvField((String) entry.get("caption")),
                        escapeCsvField((String) entry.get("media_type")),
                        escapeCsvField((String) entry.get("file_name"))
                ));
            }
            return true;
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
                jsonEntry.put("date", entry.get("date").toString());
                jsonEntry.put("caption", entry.get("caption"));
                jsonEntry.put("media_type", entry.get("media_type"));
                jsonEntry.put("file_name", entry.get("file_name"));
                jsonArray.put(jsonEntry);
            }
            writer.write(jsonArray.toString(JSON_INDENT));
            return true;
        } catch (Exception e) {
            FileLog.e("Failed to save JSON", e);
            return false;
        }
    }

    private void updateStoryProgress(int order, TL_stories.StoryItem story, int total) {
        AndroidUtilities.runOnUIThread(() -> {
            statusTextView.setText("Processing story " + order + " of " + total);
            if (delegate != null) {
                delegate.onStoryExported(order - 1, "Story #" + story.id, Theme.getColor(Theme.key_chat_attachFileText), total);
            }
            updateProgress();
        });
    }

    private void updateProgress() {
        if (totalStories > 0) {
            float progress = 1f - ((float) pendingDownloads.get() / totalStories);
            AndroidUtilities.runOnUIThread(() -> progressView.setProgress(progress, true));
        }
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

    private void checkExportFinished() {
        if (pendingDownloads.get() == 0) {
            saveMetadata();
        }
    }

    private void updateStatus(String text, int colorKey) {
        AndroidUtilities.runOnUIThread(() -> {
            statusTextView.setText(text);
            if (delegate != null) {
                delegate.onExportStatusUpdate(text, Theme.getColor(colorKey));
            }
        });
    }

    private String getMediaType(TLRPC.MessageMedia media) {
        if (media == null) {
            return "unknown";
        }
        if (media.photo != null) {
            return "photo";
        }
        if (media.document != null) {
            if (media.document.mime_type != null) {
                if (media.document.mime_type.startsWith(MIME_VIDEO_PREFIX)) {
                    return "video";
                }
                if (media.document.mime_type.startsWith(MIME_AUDIO_PREFIX)) {
                    return "audio";
                }
            }
        }
        return "other";
    }

    private String getPhotoExtension(TLRPC.PhotoSize photoSize) {
        return PHOTO_EXTENSION;
    }

    private String getDocumentExtension(TLRPC.Document document) {
        if (document == null || document.mime_type == null) {
            return DEFAULT_EXTENSION;
        }

        String mimeType = document.mime_type;
        if ("video/mp4".equals(mimeType)) {
            return ".mp4";
        } else if ("image/png".equals(mimeType)) {
            return ".png";
        } else if ("image/jpeg".equals(mimeType)) {
            return ".jpg";
        } else if ("image/webp".equals(mimeType)) {
            return ".webp";
        } else if ("audio/ogg".equals(mimeType)) {
            return ".ogg";
        }

        if (mimeType.contains("/")) {
            return "." + mimeType.split("/")[1];
        }
        return DEFAULT_EXTENSION;
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
