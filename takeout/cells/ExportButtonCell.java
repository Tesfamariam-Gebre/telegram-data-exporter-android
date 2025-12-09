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
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.voip.CellFlickerDrawable;

public class ExportButtonCell extends FrameLayout {

    private static final int BUTTON_TEXT_SIZE = 15;
    private static final int BUTTON_HEIGHT = 50;
    private static final int BUTTON_HORIZONTAL_PADDING = 34;
    private static final int BUTTON_BOTTOM_PADDING = 10;
    private static final int BUTTON_MARGIN_HORIZONTAL = 16;
    private static final int BUTTON_MARGIN_TOP = 20;
    private static final int BUTTON_MARGIN_BOTTOM = 30;
    private static final int MAX_BUTTON_WIDTH = 260;
    private static final int FIXED_BUTTON_WIDTH = 320;
    private static final float FLICKER_REPEAT_PROGRESS = 2f;
    private static final int FLICKER_CORNER_RADIUS = 4;

    private TextView exportButton;

    public ExportButtonCell(Context context) {
        super(context);

        exportButton = new androidx.appcompat.widget.AppCompatTextView(context) {
            CellFlickerDrawable cellFlickerDrawable;

            @Override
            protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                if (cellFlickerDrawable == null) {
                    cellFlickerDrawable = new CellFlickerDrawable();
                    cellFlickerDrawable.drawFrame = false;
                    cellFlickerDrawable.repeatProgress = FLICKER_REPEAT_PROGRESS;
                }
                cellFlickerDrawable.setParentWidth(getMeasuredWidth());
                AndroidUtilities.rectTmp.set(0, 0, getMeasuredWidth(), getMeasuredHeight());
                cellFlickerDrawable.draw(canvas, AndroidUtilities.rectTmp, AndroidUtilities.dp(FLICKER_CORNER_RADIUS), null);
                invalidate();
            }

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int size = MeasureSpec.getSize(widthMeasureSpec);
                if (size > AndroidUtilities.dp(MAX_BUTTON_WIDTH)) {
                    super.onMeasure(MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(FIXED_BUTTON_WIDTH), MeasureSpec.EXACTLY), heightMeasureSpec);
                } else {
                    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                }
            }
        };

        exportButton.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText));
        exportButton.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(6), Theme.getColor(Theme.key_changephoneinfo_image2), Theme.getColor(Theme.key_chats_actionPressedBackground)));


        exportButton.setText("Export");
        exportButton.setGravity(Gravity.CENTER);
        exportButton.setTypeface(AndroidUtilities.bold());
        exportButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, BUTTON_TEXT_SIZE);
        exportButton.setPadding(AndroidUtilities.dp(BUTTON_HORIZONTAL_PADDING), 0, AndroidUtilities.dp(BUTTON_HORIZONTAL_PADDING), BUTTON_BOTTOM_PADDING);
        addView(exportButton, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, BUTTON_HEIGHT,
                Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM,
                BUTTON_MARGIN_HORIZONTAL,
                BUTTON_MARGIN_TOP,
                BUTTON_MARGIN_HORIZONTAL,
                BUTTON_MARGIN_BOTTOM
        ));
    }
}
