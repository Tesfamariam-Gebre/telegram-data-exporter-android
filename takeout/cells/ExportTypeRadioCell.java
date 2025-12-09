/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Created by Tesfamariam Gebre.
 */

package plus.takeout.cells;

import android.content.Context;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;

import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.RadioColorCell;

import static org.telegram.messenger.AndroidUtilities.dp;

public class ExportTypeRadioCell extends FrameLayout {

    private static final int HEADER_TEXT_SIZE = 15;
    private static final int HEADER_TOP_MARGIN = 14;
    private static final int HEADER_BOTTOM_MARGIN = 14;
    private static final int CELL_PADDING = 4;
    private static final int CSV_INDEX = 0;
    private static final int JSON_INDEX = 1;

    private HeaderCell headerCell;
    private final RadioColorCell[] radioCells;
    private LinearLayout linearLayout;
    private final int[] selected = new int[1];

    private String[] descriptions = new String[]{
            "CSV",
            "JSON"
    };

    public ExportTypeRadioCell(@NonNull Context context) {
        super(context);
        setEnabled(false);
        linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.VERTICAL);

        headerCell = new HeaderCell(context);
        headerCell.setText("Export Format");
        headerCell.setTextSize(HEADER_TEXT_SIZE);
        headerCell.setTopMargin(HEADER_TOP_MARGIN);
        headerCell.setBottomMargin(HEADER_BOTTOM_MARGIN);
        linearLayout.addView(headerCell);

        radioCells = new RadioColorCell[descriptions.length];
        for (int a = 0; a < descriptions.length; a++) {
            RadioColorCell cell = new RadioColorCell(context);
            cell.setPadding(dp(CELL_PADDING), 0, dp(CELL_PADDING), 0);
            cell.setTag(a);
            cell.setCheckColor(Theme.getColor(Theme.key_radioBackground), Theme.getColor(Theme.key_dialogRadioBackgroundChecked));
            cell.setTextAndValue(descriptions[a], a == CSV_INDEX);
            radioCells[a] = cell;

            cell.setOnClickListener(v -> {
                int index = (Integer) v.getTag();
                selected[0] = index;

                for (int i = 0; i < radioCells.length; i++) {
                    radioCells[i].setChecked(i == index, true);
                }
            });

            linearLayout.addView(cell);
        }

        selected[0] = CSV_INDEX;
        radioCells[CSV_INDEX].setChecked(true, false);

        addView(linearLayout);
    }

    public boolean isCsvSelected() {
        return selected[0] == CSV_INDEX;
    }

    public void setFormat(boolean csvSelected) {
        int index = csvSelected ? CSV_INDEX : JSON_INDEX;
        selected[0] = index;
        for (int i = 0; i < radioCells.length; i++) {
            radioCells[i].setChecked(i == index, true);
        }
    }
}
