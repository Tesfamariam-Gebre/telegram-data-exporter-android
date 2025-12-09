/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Created by Tesfamariam Gebre.
 */

package plus.takeout.cells;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Environment;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.style.ClickableSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;

import org.telegram.messenger.FileLog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RLottieImageView;

import java.io.File;
import java.util.List;

public class HintInnerCell extends FrameLayout {

    private static final String EXPORT_DIR_NAME = "Exported Data";
    private static final int ANIMATION_SIZE = 90;
    private static final int TEXT_SIZE = 14;
    private static final int HORIZONTAL_PADDING = 20;
    private static final int TOP_PADDING = 100;
    private static final int BOTTOM_PADDING = 14;

    public static final String EXPORT_DIR = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) + "/" + EXPORT_DIR_NAME;

    private RLottieImageView imageView;
    private TextView messageTextView;

    public HintInnerCell(Context context, int resId) {
        super(context);

        imageView = new RLottieImageView(context);
        imageView.setScaleType(ImageView.ScaleType.CENTER);
        imageView.setAutoRepeat(true);
        imageView.setAnimation(resId, ANIMATION_SIZE, ANIMATION_SIZE);
        imageView.playAnimation();
        imageView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        addView(imageView, LayoutHelper.createFrame(ANIMATION_SIZE, ANIMATION_SIZE, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 6, 0, 0));
        imageView.setOnClickListener(v -> {
            if (!imageView.isPlaying()) {
                imageView.playAnimation();
            }
        });

        String text = "Export a copy of your Telegram data, including your chats, media, and contacts. Select what you need, and Find it in **Exported Data** Folder (this can take time).";
        SpannableStringBuilder builder = new SpannableStringBuilder(text);
        int index1 = text.indexOf("**");
        int index2 = text.lastIndexOf("**");
        if (index1 >= 0 && index2 >= 0 && index1 != index2) {
            builder.replace(index2, index2 + 2, "");
            builder.replace(index1, index1 + 2, "");
            try {
                ClickableSpan clickableSpan = new ClickableSpan() {
                    @Override
                    public void onClick(@NonNull View widget) {
                        openExportDirectory(context);
                    }

                    @Override
                    public void updateDrawState(@NonNull TextPaint ds) {
                        super.updateDrawState(ds);
                        ds.setColor(Theme.getColor(Theme.key_chat_attachFileText));
                        ds.setUnderlineText(false);
                    }
                };
                builder.setSpan(clickableSpan, index1, index2 - 2, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        messageTextView = new TextView(context);
        messageTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText4));
        messageTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, TEXT_SIZE);
        messageTextView.setGravity(Gravity.CENTER);
        messageTextView.setText(builder);
        addView(messageTextView, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT,
                Gravity.TOP | Gravity.CENTER_HORIZONTAL,
                HORIZONTAL_PADDING,
                TOP_PADDING,
                HORIZONTAL_PADDING,
                BOTTOM_PADDING
        ));
    }

    public void openExportDirectory(Context context) {
        File exportDir = new File(EXPORT_DIR);

        if (!exportDir.exists() && !exportDir.mkdirs()) {
            FileLog.e("Failed to create export directory: " + EXPORT_DIR);
            Toast.makeText(context, "Failed to create export directory", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            Uri uri = FileProvider.getUriForFile(context, context.getPackageName() + ".provider", exportDir);

            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, "resource/folder");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            List<ResolveInfo> resInfoList = context.getPackageManager().queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);

            for (ResolveInfo resolveInfo : resInfoList) {
                context.grantUriPermission(
                        resolveInfo.activityInfo.packageName,
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                );
            }

            context.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            FileLog.e(e);
            File parentDir = exportDir.getParentFile();
            if (parentDir != null && parentDir.exists()) {
                try {
                    Uri parentUri = FileProvider.getUriForFile(context,
                            context.getPackageName() + ".provider", parentDir);

                    Intent fallbackIntent = new Intent(Intent.ACTION_VIEW);
                    fallbackIntent.setDataAndType(parentUri, "resource/folder");
                    fallbackIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    fallbackIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                    context.startActivity(fallbackIntent);
                } catch (ActivityNotFoundException e2) {
                    FileLog.e(e2);
                    Toast.makeText(context,
                            "Exported to: " + exportDir.getAbsolutePath(),
                            Toast.LENGTH_LONG).show();
                }
            } else {
                Toast.makeText(context,
                        "Exported to: " + exportDir.getAbsolutePath(),
                        Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            FileLog.e(e);
            Toast.makeText(context,
                    "Exported to: " + exportDir.getAbsolutePath(),
                    Toast.LENGTH_LONG).show();
        }
    }
}
