/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Created by Tesfamariam Gebre.
 */

package plus.takeout;

import android.content.Context;
import android.os.Environment;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONException;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_account;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SeekBarView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import plus.takeout.cells.ChannelChatExportCell;
import plus.takeout.cells.ContactExportCell;
import plus.takeout.cells.ExportButtonCell;
import plus.takeout.cells.ExportTypeRadioCell;
import plus.takeout.cells.GroupChatExportCell;
import plus.takeout.cells.HintInnerCell;
import plus.takeout.cells.PersonalInfoExportCell;
import plus.takeout.cells.PrivateChatExportCell;
import plus.takeout.cells.ProfilePictureExportCell;
import plus.takeout.cells.SavedMessageExportCell;
import plus.takeout.cells.StoryExportCell;

public class TakeoutFragment extends BaseFragment {

    private static final String EXPORT_DIR_NAME = "Exported Data";
    private static final int DEFAULT_FILE_SIZE_MB = 10;
    private static final int MIN_FILE_SIZE_MB = 10;
    private static final int MAX_FILE_SIZE_MB = 100;
    private static final long BYTES_PER_MB = 1024L * 1024L;

    public static final String EXPORT_DIR = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) + "/" + EXPORT_DIR_NAME;

    private boolean exportAsCsv = true;

    private ContactExportCell contactExportCell;
    private PersonalInfoExportCell personalInfoExportCell;
    private StoryExportCell storyExportCell;
    private ProfilePictureExportCell profilePictureExportCell;
    private SavedMessageExportCell savedMessageExportCell;
    private PrivateChatExportCell privateChatExportCell;
    private GroupChatExportCell groupChatExportCell;
    private ChannelChatExportCell channelChatExportCell;
    private ExportTypeRadioCell exportTypeRadioCell;
    private ExportButtonCell exportButtonCell;

    private boolean saveContactChecked;
    private boolean storiesChecked;
    private boolean personalInfoChecked;
    private boolean profilePicturesChecked;
    private boolean savedMessageChecked;
    private boolean sessionsChecked;
    private boolean privateMessagesChecked;
    private boolean groupMessagesChecked;
    private boolean channelMessagesChecked;
    private boolean fileChecked;

    private int currentFileSizeMB = DEFAULT_FILE_SIZE_MB;
    private long currentTakeoutId;

    private RecyclerListView listView;
    private ListAdapter listAdapter;

    private int rowCount;
    private int hintRow;
    private int savedContactsRow;
    private int storiesRow;
    private int personalInfoRow;
    private int profilePicturesRow;
    private int savedMessagesRow;
    private int sessionsRow;
    private int privateMessagesRow;
    private int groupMessagesRow;
    private int channelMessagesRow;
    private int filesCheckRow;
    private int filesSizeRow;
    private int formatRow;
    private int exportButtonRow;
    private int finalDividerRow;

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        currentFileSizeMB = DEFAULT_FILE_SIZE_MB;
        updateRows();
        return true;
    }

    private void updateRows() {
        rowCount = 0;
        hintRow = rowCount++;
        savedContactsRow = rowCount++;
        storiesRow = rowCount++;
        personalInfoRow = rowCount++;
        profilePicturesRow = rowCount++;
        savedMessagesRow = rowCount++;
        sessionsRow = -1;
        privateMessagesRow = rowCount++;
        groupMessagesRow = rowCount++;
        channelMessagesRow = rowCount++;
        filesCheckRow = -1;
        filesSizeRow = -1;
        formatRow = -1;
        exportButtonRow = rowCount++;
        finalDividerRow = rowCount++;
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        if (currentTakeoutId != 0) {
            finishTakeoutSession(currentTakeoutId, true);
        }
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle("Export Telegram Data");

        if (AndroidUtilities.isTablet()) {
            actionBar.setOccupyStatusBar(false);
        }

        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        File exportDir = new File(EXPORT_DIR);
        if (!exportDir.exists() && !exportDir.mkdirs()) {
            FileLog.e("Failed to create export directory: " + EXPORT_DIR);
        }

        listAdapter = new ListAdapter(context);

        fragmentView = new FrameLayout(context);
        fragmentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        FrameLayout frameLayout = (FrameLayout) fragmentView;

        listView = new RecyclerListView(context);
        listView.setVerticalScrollBarEnabled(false);
        listView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false) {
            @Override
            public boolean supportsPredictiveItemAnimations() {
                return false;
            }
        });
        listView.setGlowColor(Theme.getColor(Theme.key_avatar_backgroundActionBarBlue));
        listView.setAdapter(listAdapter);
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT));

        View shadow = new View(context);
        shadow.setBackgroundResource(R.drawable.header_shadow_reverse);
        shadow.setTranslationY(AndroidUtilities.dp(48));
        frameLayout.addView(shadow, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 3, Gravity.BOTTOM | Gravity.LEFT, 0, 0, 0, 48));

        listView.setOnItemClickListener((view, position) -> {
            if (position == savedContactsRow) {
                saveContactChecked = !saveContactChecked;
            } else if (position == privateMessagesRow) {
                privateMessagesChecked = !privateMessagesChecked;
            } else if (position == groupMessagesRow) {
                groupMessagesChecked = !groupMessagesChecked;
            } else if (position == channelMessagesRow) {
                channelMessagesChecked = !channelMessagesChecked;
            } else if (position == filesCheckRow) {
                fileChecked = !fileChecked;
                updateRows();
            } else if (position == storiesRow) {
                storiesChecked = !storiesChecked;
            } else if (position == personalInfoRow) {
                personalInfoChecked = !personalInfoChecked;
            } else if (position == profilePicturesRow) {
                profilePicturesChecked = !profilePicturesChecked;
            } else if (position == savedMessagesRow) {
                savedMessageChecked = !savedMessageChecked;
            } else if (position == sessionsRow) {
                sessionsChecked = !sessionsChecked;
            } else if (position == hintRow) {
                HintInnerCell hintInnerCell = (HintInnerCell) view;
                hintInnerCell.openExportDirectory(context);
            } else if (position == exportButtonRow) {
                startTakeout();
            }
            if (listAdapter != null) {
                listAdapter.notifyItemChanged(position);
            }
        });

        return fragmentView;
    }

    private void startTakeout() {
        TL_takeout.TL_account_initTakeoutSession req = new TL_takeout.TL_account_initTakeoutSession();
        req.flags = 0;

        if (saveContactChecked) {
            req.contacts = true;
        }
        if (privateMessagesChecked) {
            req.message_users = true;
        }
        if (groupMessagesChecked) {
            req.message_megagroups = true;
        }
        if (channelMessagesChecked) {
            req.message_channels = true;
        }
        if (fileChecked) {
            req.files = true;
            req.file_max_size = currentFileSizeMB * BYTES_PER_MB;
        }

        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {
            AndroidUtilities.runOnUIThread(() -> {
                if (error != null) {
                    FileLog.e("Takeout initialization failed: " + error.text);
                    Toast.makeText(getContext(), "Error: " + error.text, Toast.LENGTH_SHORT).show();
                    return;
                }

                if (!(response instanceof TL_takeout.TL_account_takeout)) {
                    FileLog.e("Invalid takeout response type");
                    return;
                }

                TL_takeout.TL_account_takeout takeout = (TL_takeout.TL_account_takeout) response;
                currentTakeoutId = takeout.id;

                if (saveContactChecked && contactExportCell != null) {
                    contactExportCell.exportContacts(takeout.id);
                }
                if (storiesChecked && storyExportCell != null) {
                    storyExportCell.exportStory(takeout.id);
                }
                if (personalInfoChecked && personalInfoExportCell != null) {
                    personalInfoExportCell.exportPersonalInfo(takeout.id);
                }
                if (profilePicturesChecked && profilePictureExportCell != null) {
                    profilePictureExportCell.exportProfilePicture(takeout.id);
                }
                if (savedMessageChecked && savedMessageExportCell != null) {
                    savedMessageExportCell.exportSavedMessages(takeout.id);
                }
                if (sessionsChecked) {
                    exportSessions(takeout.id);
                }
                if (privateMessagesChecked && privateChatExportCell != null) {
                    privateChatExportCell.exportPrivateChatMessages(takeout.id);
                }
                if (groupMessagesChecked && groupChatExportCell != null) {
                    groupChatExportCell.exportGroupMessages(takeout.id);
                }
                if (channelMessagesChecked && channelChatExportCell != null) {
                    channelChatExportCell.exportChannelMessages(takeout.id);
                }
                if (fileChecked) {
                    exportFiles(takeout.id);
                }
            });
        });
    }

    private void updateMaxFileSize(int sizeMB) {
        currentFileSizeMB = sizeMB;
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {

        private Context context;

        private static final int VIEW_TYPE_SHADOW = 1;
        private static final int VIEW_TYPE_TEXT_CHECK = 4;
        private static final int VIEW_TYPE_FILE_SIZE = 6;
        private static final int VIEW_TYPE_HINT = 16;
        private static final int VIEW_TYPE_CONTACT_INFO = 8;
        private static final int VIEW_TYPE_PERSONAL_INFO = 9;
        private static final int VIEW_TYPE_STORY_INFO = 10;
        private static final int VIEW_TYPE_PROFILE_PICTURE_INFO = 11;
        private static final int VIEW_TYPE_SAVED_MESSAGE_INFO = 12;
        private static final int VIEW_TYPE_PRIVATE_CHAT_INFO = 13;
        private static final int VIEW_TYPE_GROUP_MESSAGE_INFO = 14;
        private static final int VIEW_TYPE_CHANNEL_MESSAGE_INFO = 15;
        private static final int VIEW_TYPE_FORMAT_INFO = 17;
        private static final int VIEW_TYPE_EXPORT_BUTTON = 18;

        private ListAdapter(Context context) {
            this.context = context;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int type = holder.getItemViewType();
            return type != VIEW_TYPE_SHADOW && type != VIEW_TYPE_HINT && type != VIEW_TYPE_EXPORT_BUTTON;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = null;
            switch (viewType) {
                case VIEW_TYPE_HINT:
                    view = new HintInnerCell(context, R.raw.import_loop);
                    view.setBackgroundDrawable(Theme.getThemedDrawableByKey(context, R.drawable.greydivider_top, Theme.key_windowBackgroundGrayShadow));
                    break;
                case VIEW_TYPE_SHADOW:
                    view = new ShadowSectionCell(context);
                    break;
                case VIEW_TYPE_TEXT_CHECK:
                    view = new TextCheckCell(context);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case VIEW_TYPE_FILE_SIZE:
                    view = createFileSizeView();
                    break;
                case VIEW_TYPE_CONTACT_INFO:
                    view = contactExportCell = new ContactExportCell(context);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case VIEW_TYPE_PERSONAL_INFO:
                    view = personalInfoExportCell = new PersonalInfoExportCell(context);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case VIEW_TYPE_STORY_INFO:
                    view = storyExportCell = new StoryExportCell(context);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case VIEW_TYPE_PROFILE_PICTURE_INFO:
                    view = profilePictureExportCell = new ProfilePictureExportCell(context);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case VIEW_TYPE_SAVED_MESSAGE_INFO:
                    view = savedMessageExportCell = new SavedMessageExportCell(context);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case VIEW_TYPE_PRIVATE_CHAT_INFO:
                    view = privateChatExportCell = new PrivateChatExportCell(context);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case VIEW_TYPE_GROUP_MESSAGE_INFO:
                    view = groupChatExportCell = new GroupChatExportCell(context);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case VIEW_TYPE_CHANNEL_MESSAGE_INFO:
                    view = channelChatExportCell = new ChannelChatExportCell(context);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case VIEW_TYPE_FORMAT_INFO:
                    view = exportTypeRadioCell = new ExportTypeRadioCell(context);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case VIEW_TYPE_EXPORT_BUTTON:
                    view = exportButtonCell = new ExportButtonCell(context);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
            }
            if (view != null) {
                view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
            }
            return new RecyclerListView.Holder(view);
        }

        private View createFileSizeView() {
            LinearLayout linearLayout = new LinearLayout(context);
            linearLayout.setOrientation(LinearLayout.VERTICAL);
            linearLayout.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(8), AndroidUtilities.dp(16), AndroidUtilities.dp(8));

            TextView sizeLabel = new TextView(context);
            sizeLabel.setText("Maximum file size");
            sizeLabel.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            sizeLabel.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            linearLayout.addView(sizeLabel);

            TextView currentSize = new TextView(context);
            currentSize.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText));
            currentSize.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            linearLayout.addView(currentSize, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 4, 0, 8));

            SeekBarView seekBar = new SeekBarView(context);
            seekBar.setReportChanges(true);
            seekBar.setDelegate(new SeekBarView.SeekBarViewDelegate() {
                @Override
                public void onSeekBarDrag(boolean stop, float progress) {
                    int sizeMB = MIN_FILE_SIZE_MB + (int) ((MAX_FILE_SIZE_MB - MIN_FILE_SIZE_MB) * progress);
                    currentFileSizeMB = sizeMB;
                    currentSize.setText(sizeMB + " MB");
                    if (stop) {
                        updateMaxFileSize(sizeMB);
                    }
                }

                @Override
                public void onSeekBarPressed(boolean pressed) {
                }
            });
            linearLayout.addView(seekBar, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 38));

            LinearLayout sizeLabels = new LinearLayout(context);
            sizeLabels.setOrientation(LinearLayout.HORIZONTAL);

            TextView minLabel = new TextView(context);
            minLabel.setText(MIN_FILE_SIZE_MB + " MB");
            minLabel.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            minLabel.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
            sizeLabels.addView(minLabel, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1.0f));

            TextView maxLabel = new TextView(context);
            maxLabel.setText(MAX_FILE_SIZE_MB + " MB");
            maxLabel.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            maxLabel.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
            sizeLabels.addView(maxLabel, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

            linearLayout.addView(sizeLabels, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 8, 0, 0));

            linearLayout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            return linearLayout;
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case VIEW_TYPE_SHADOW: {
                    if (position == finalDividerRow) {
                        holder.itemView.setBackgroundDrawable(Theme.getThemedDrawable(context, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    } else {
                        holder.itemView.setBackgroundDrawable(Theme.getThemedDrawable(context, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
                    }
                    break;
                }
                case VIEW_TYPE_TEXT_CHECK: {
                    TextCheckCell textCheckCell = (TextCheckCell) holder.itemView;
                    if (position == filesCheckRow) {
                        textCheckCell.setTextAndCheck("Files", fileChecked, true);
                    } else if (position == sessionsRow) {
                        textCheckCell.setTextAndCheck("Sessions", sessionsChecked, true);
                    }
                    break;
                }
                case VIEW_TYPE_FORMAT_INFO: {
                    ExportTypeRadioCell exportTypeRadioCell1 = (ExportTypeRadioCell) holder.itemView;
                    exportTypeRadioCell1.setFormat(exportAsCsv);
                    break;
                }
                case VIEW_TYPE_CONTACT_INFO: {
                    contactExportCell = (ContactExportCell) holder.itemView;
                    contactExportCell.setExportAsCsv(exportAsCsv);
                    contactExportCell.setExportData("Saved Contacts", saveContactChecked);
                    contactExportCell.setContactExportStatusDelegate(createContactExportDelegate());
                    break;
                }
                case VIEW_TYPE_PERSONAL_INFO: {
                    personalInfoExportCell = (PersonalInfoExportCell) holder.itemView;
                    personalInfoExportCell.setExportAsCsv(exportAsCsv);
                    personalInfoExportCell.setExportData("Personal Info", personalInfoChecked);
                    personalInfoExportCell.setPersonalInfoExportDelegate(createPersonalInfoExportDelegate());
                    break;
                }
                case VIEW_TYPE_STORY_INFO: {
                    storyExportCell = (StoryExportCell) holder.itemView;
                    storyExportCell.setExportAsCsv(exportAsCsv);
                    storyExportCell.setExportData("Stories", storiesChecked);
                    storyExportCell.setStoryExportDelegate(createStoryExportDelegate());
                    break;
                }
                case VIEW_TYPE_PROFILE_PICTURE_INFO: {
                    profilePictureExportCell = (ProfilePictureExportCell) holder.itemView;
                    profilePictureExportCell.setExportAsCsv(exportAsCsv);
                    profilePictureExportCell.setExportData("Profile Pictures", profilePicturesChecked);
                    profilePictureExportCell.setProfilePictureExportDelegate(createProfilePictureExportDelegate());
                    break;
                }
                case VIEW_TYPE_SAVED_MESSAGE_INFO: {
                    savedMessageExportCell = (SavedMessageExportCell) holder.itemView;
                    savedMessageExportCell.setExportAsCsv(exportAsCsv);
                    savedMessageExportCell.setExportData("Saved Messages", savedMessageChecked);
                    savedMessageExportCell.setSavedMessageExportDelegate(createSavedMessageExportDelegate());
                    break;
                }
                case VIEW_TYPE_PRIVATE_CHAT_INFO: {
                    privateChatExportCell = (PrivateChatExportCell) holder.itemView;
                    privateChatExportCell.setExportAsCsv(exportAsCsv);
                    privateChatExportCell.setExportData("Private Messages", privateMessagesChecked);
                    privateChatExportCell.setPrivateChatExportDelegate(createPrivateChatExportDelegate());
                    break;
                }
                case VIEW_TYPE_GROUP_MESSAGE_INFO: {
                    groupChatExportCell = (GroupChatExportCell) holder.itemView;
                    groupChatExportCell.setExportData("Group Messages", groupMessagesChecked);
                    groupChatExportCell.setGroupChatExportDelegate(createGroupChatExportDelegate());
                    break;
                }
                case VIEW_TYPE_CHANNEL_MESSAGE_INFO: {
                    channelChatExportCell = (ChannelChatExportCell) holder.itemView;
                    channelChatExportCell.setExportAsCsv(exportAsCsv);
                    channelChatExportCell.setExportData("Channel Messages", channelMessagesChecked);
                    channelChatExportCell.setChannelChatExportDelegate(createChannelChatExportDelegate());
                    break;
                }
            }
        }

        private ContactExportCell.ContactExportStatusDelegate createContactExportDelegate() {
            return new ContactExportCell.ContactExportStatusDelegate() {
                @Override
                public void onExportStatusUpdate(String status, int color) {
                    if (contactExportCell.statusTextView != null) {
                        contactExportCell.statusTextView.setText(status);
                        contactExportCell.statusTextView.setTextColor(color);
                    }
                    if (contactExportCell.progressView != null) {
                        contactExportCell.progressView.setProgress(0, false);
                    }
                }

                @Override
                public void onContactExported(int order, String contactName, int color, int totalSize) {
                    if (contactExportCell.statusTextView != null) {
                        contactExportCell.statusTextView.setText(order + ". " + contactName);
                        contactExportCell.statusTextView.setTextColor(color);
                    }
                    if (contactExportCell.progressView != null) {
                        contactExportCell.progressView.setProgress(Math.min(1f, order / (float) totalSize), true);
                    }
                }

                @Override
                public void onExportFinished(String status, int color) {
                    if (contactExportCell.statusTextView != null) {
                        contactExportCell.statusTextView.setText(status);
                        contactExportCell.statusTextView.setTextColor(color);
                    }
                    if (contactExportCell.progressView != null) {
                        contactExportCell.progressView.setProgress(1, true);
                    }
                }
            };
        }

        private PersonalInfoExportCell.PersonalInfoExportDelegate createPersonalInfoExportDelegate() {
            return new PersonalInfoExportCell.PersonalInfoExportDelegate() {
                @Override
                public void onExportStatusUpdate(String status, int color) {
                    if (personalInfoExportCell.statusTextView != null) {
                        personalInfoExportCell.statusTextView.setText(status);
                        personalInfoExportCell.statusTextView.setTextColor(color);
                    }
                    if (personalInfoExportCell.progressView != null) {
                        personalInfoExportCell.progressView.setProgress(0, false);
                    }
                }

                @Override
                public void onPersonalInfoExported(int order, String infoName, int color, int totalSize) {
                    if (personalInfoExportCell.statusTextView != null) {
                        personalInfoExportCell.statusTextView.setText(infoName);
                        personalInfoExportCell.statusTextView.setTextColor(color);
                    }
                    if (personalInfoExportCell.progressView != null) {
                        personalInfoExportCell.progressView.setProgress(Math.min(1f, order / (float) totalSize), true);
                    }
                }

                @Override
                public void onExportFinished(String status, int color) {
                    if (personalInfoExportCell.statusTextView != null) {
                        personalInfoExportCell.statusTextView.setText(status);
                        personalInfoExportCell.statusTextView.setTextColor(color);
                    }
                    if (personalInfoExportCell.progressView != null) {
                        personalInfoExportCell.progressView.setProgress(1, true);
                    }
                }
            };
        }

        private StoryExportCell.StoryExportDelegate createStoryExportDelegate() {
            return new StoryExportCell.StoryExportDelegate() {
                @Override
                public void onExportStatusUpdate(String status, int color) {
                    if (storyExportCell.statusTextView != null) {
                        storyExportCell.statusTextView.setText(status);
                        storyExportCell.statusTextView.setTextColor(color);
                    }
                    if (storyExportCell.progressView != null) {
                        storyExportCell.progressView.setProgress(0, false);
                    }
                }

                @Override
                public void onStoryExported(int order, String infoName, int color, int totalSize) {
                    if (storyExportCell.statusTextView != null) {
                        storyExportCell.statusTextView.setText(infoName);
                        storyExportCell.statusTextView.setTextColor(color);
                    }
                    if (storyExportCell.progressView != null) {
                        storyExportCell.progressView.setProgress(Math.min(1f, order / (float) totalSize), true);
                    }
                }

                @Override
                public void onExportFinished(String status, int color) {
                    if (storyExportCell.statusTextView != null) {
                        storyExportCell.statusTextView.setText(status);
                        storyExportCell.statusTextView.setTextColor(color);
                    }
                    if (storyExportCell.progressView != null) {
                        storyExportCell.progressView.setProgress(1, true);
                    }
                }
            };
        }

        private ProfilePictureExportCell.ProfilePictureExportDelegate createProfilePictureExportDelegate() {
            return new ProfilePictureExportCell.ProfilePictureExportDelegate() {
                @Override
                public void onExportStatusUpdate(String status, int color) {
                    if (profilePictureExportCell.statusTextView != null) {
                        profilePictureExportCell.statusTextView.setText(status);
                        profilePictureExportCell.statusTextView.setTextColor(color);
                    }
                    if (profilePictureExportCell.progressView != null) {
                        profilePictureExportCell.progressView.setProgress(0, false);
                    }
                }

                @Override
                public void onProfilePictureExported(int order, String infoName, int color, int totalSize) {
                    if (profilePictureExportCell.statusTextView != null) {
                        profilePictureExportCell.statusTextView.setText(infoName);
                        profilePictureExportCell.statusTextView.setTextColor(color);
                    }
                    if (profilePictureExportCell.progressView != null) {
                        profilePictureExportCell.progressView.setProgress(Math.min(1f, order / (float) totalSize), true);
                    }
                }

                @Override
                public void onExportFinished(String status, int color) {
                    if (profilePictureExportCell.statusTextView != null) {
                        profilePictureExportCell.statusTextView.setText(status);
                        profilePictureExportCell.statusTextView.setTextColor(color);
                    }
                    if (profilePictureExportCell.progressView != null) {
                        profilePictureExportCell.progressView.setProgress(1, true);
                    }
                }
            };
        }

        private SavedMessageExportCell.SavedMessageExportDelegate createSavedMessageExportDelegate() {
            return new SavedMessageExportCell.SavedMessageExportDelegate() {
                @Override
                public void onExportStatusUpdate(String status, int color) {
                    if (savedMessageExportCell.statusTextView != null) {
                        savedMessageExportCell.statusTextView.setText(status);
                        savedMessageExportCell.statusTextView.setTextColor(color);
                    }
                    if (savedMessageExportCell.progressView != null) {
                        savedMessageExportCell.progressView.setProgress(0, false);
                    }
                }

                @Override
                public void onSavedMessageExported(int order, String infoName, int color, int totalSize) {
                    if (savedMessageExportCell.statusTextView != null) {
                        savedMessageExportCell.statusTextView.setText(infoName);
                        savedMessageExportCell.statusTextView.setTextColor(color);
                    }
                    if (savedMessageExportCell.progressView != null) {
                        savedMessageExportCell.progressView.setProgress(Math.min(1f, order / (float) totalSize), true);
                    }
                }

                @Override
                public void onExportFinished(String status, int color) {
                    if (savedMessageExportCell.statusTextView != null) {
                        savedMessageExportCell.statusTextView.setText(status);
                        savedMessageExportCell.statusTextView.setTextColor(color);
                    }
                    if (savedMessageExportCell.progressView != null) {
                        savedMessageExportCell.progressView.setProgress(1, true);
                    }
                }
            };
        }

        private PrivateChatExportCell.PrivateChatExportDelegate createPrivateChatExportDelegate() {
            return new PrivateChatExportCell.PrivateChatExportDelegate() {
                @Override
                public void onExportStatusUpdate(String status, int color) {
                    if (privateChatExportCell.statusTextView != null) {
                        privateChatExportCell.statusTextView.setText(status);
                        privateChatExportCell.statusTextView.setTextColor(color);
                    }
                    if (privateChatExportCell.progressView != null) {
                        privateChatExportCell.progressView.setProgress(0, false);
                    }
                }

                @Override
                public void onPrivateChatExported(int order, String infoName, int color, int totalSize) {
                    if (privateChatExportCell.statusTextView != null) {
                        privateChatExportCell.statusTextView.setText(infoName);
                        privateChatExportCell.statusTextView.setTextColor(color);
                    }
                    if (privateChatExportCell.progressView != null) {
                        privateChatExportCell.progressView.setProgress(Math.min(1f, order / (float) totalSize), true);
                    }
                }

                @Override
                public void onExportFinished(String status, int color) {
                    if (privateChatExportCell.statusTextView != null) {
                        privateChatExportCell.statusTextView.setText(status);
                        privateChatExportCell.statusTextView.setTextColor(color);
                    }
                    if (privateChatExportCell.progressView != null) {
                        privateChatExportCell.progressView.setProgress(1, true);
                    }
                }
            };
        }

        private GroupChatExportCell.GroupChatExportDelegate createGroupChatExportDelegate() {
            return new GroupChatExportCell.GroupChatExportDelegate() {
                @Override
                public void onExportStatusUpdate(String status, int color) {
                    if (groupChatExportCell.statusTextView != null) {
                        groupChatExportCell.statusTextView.setText(status);
                        groupChatExportCell.statusTextView.setTextColor(color);
                    }
                    if (groupChatExportCell.progressView != null) {
                        groupChatExportCell.progressView.setProgress(0, false);
                    }
                }

                @Override
                public void onGroupChatExported(int order, String infoName, int color, int totalSize) {
                    if (groupChatExportCell.statusTextView != null) {
                        groupChatExportCell.statusTextView.setText(infoName);
                        groupChatExportCell.statusTextView.setTextColor(color);
                    }
                    if (groupChatExportCell.progressView != null) {
                        groupChatExportCell.progressView.setProgress(Math.min(1f, order / (float) totalSize), true);
                    }
                }

                @Override
                public void onExportFinished(String status, int color) {
                    if (groupChatExportCell.statusTextView != null) {
                        groupChatExportCell.statusTextView.setText(status);
                        groupChatExportCell.statusTextView.setTextColor(color);
                    }
                    if (groupChatExportCell.progressView != null) {
                        groupChatExportCell.progressView.setProgress(1, true);
                    }
                }
            };
        }

        private ChannelChatExportCell.ChannelChatExportDelegate createChannelChatExportDelegate() {
            return new ChannelChatExportCell.ChannelChatExportDelegate() {
                @Override
                public void onExportStatusUpdate(String status, int color) {
                    if (channelChatExportCell.statusTextView != null) {
                        channelChatExportCell.statusTextView.setText(status);
                        channelChatExportCell.statusTextView.setTextColor(color);
                    }
                    if (channelChatExportCell.progressView != null) {
                        channelChatExportCell.progressView.setProgress(0, false);
                    }
                }

                @Override
                public void onChannelChatExported(int order, String infoName, int color, int totalSize) {
                    if (channelChatExportCell.statusTextView != null) {
                        channelChatExportCell.statusTextView.setText(infoName);
                        channelChatExportCell.statusTextView.setTextColor(color);
                    }
                    if (channelChatExportCell.progressView != null) {
                        channelChatExportCell.progressView.setProgress(Math.min(1f, order / (float) totalSize), true);
                    }
                }

                @Override
                public void onExportFinished(String status, int color) {
                    if (channelChatExportCell.statusTextView != null) {
                        channelChatExportCell.statusTextView.setText(status);
                        channelChatExportCell.statusTextView.setTextColor(color);
                    }
                    if (channelChatExportCell.progressView != null) {
                        channelChatExportCell.progressView.setProgress(1, true);
                    }
                }
            };
        }

        @Override
        public int getItemCount() {
            return rowCount;
        }

        @Override
        public int getItemViewType(int position) {
            if (position == filesCheckRow || position == sessionsRow) {
                return VIEW_TYPE_TEXT_CHECK;
            } else if (position == filesSizeRow) {
                return VIEW_TYPE_FILE_SIZE;
            } else if (position == finalDividerRow) {
                return VIEW_TYPE_SHADOW;
            } else if (position == hintRow) {
                return VIEW_TYPE_HINT;
            } else if (position == savedContactsRow) {
                return VIEW_TYPE_CONTACT_INFO;
            } else if (position == personalInfoRow) {
                return VIEW_TYPE_PERSONAL_INFO;
            } else if (position == storiesRow) {
                return VIEW_TYPE_STORY_INFO;
            } else if (position == profilePicturesRow) {
                return VIEW_TYPE_PROFILE_PICTURE_INFO;
            } else if (position == savedMessagesRow) {
                return VIEW_TYPE_SAVED_MESSAGE_INFO;
            } else if (position == privateMessagesRow) {
                return VIEW_TYPE_PRIVATE_CHAT_INFO;
            } else if (position == groupMessagesRow) {
                return VIEW_TYPE_GROUP_MESSAGE_INFO;
            } else if (position == channelMessagesRow) {
                return VIEW_TYPE_CHANNEL_MESSAGE_INFO;
            } else if (position == formatRow) {
                return VIEW_TYPE_FORMAT_INFO;
            } else if (position == exportButtonRow) {
                return VIEW_TYPE_EXPORT_BUTTON;
            }
            return 0;
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
    }

    private void finishTakeoutSession(long takeoutId, boolean success) {
        if (takeoutId == 0) {
            return;
        }
        TL_takeout.TL_account_finishTakeoutSession req = new TL_takeout.TL_account_finishTakeoutSession();
        req.flags = 1;
        req.success = success;
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, null);
    }

    private void exportSessions(long takeoutId) {
        List<JSONObject> sessions = new ArrayList<>();

        TL_account.getAuthorizations authRequest = new TL_account.getAuthorizations();
        TL_takeout.TL_invokeWithTakeout authInvoke = new TL_takeout.TL_invokeWithTakeout();
        authInvoke.takeout_id = takeoutId;
        authInvoke.query = authRequest;

        ConnectionsManager.getInstance(currentAccount).sendRequest(authInvoke, (authResponse, authError) -> {
            AndroidUtilities.runOnUIThread(() -> {
                if (authError != null) {
                    FileLog.e("Authorization export failed: " + authError.text);
                    Toast.makeText(getContext(), "Authorization export failed: " + authError.text, Toast.LENGTH_SHORT).show();
                    finishTakeoutSession(takeoutId, false);
                    return;
                }

                if (authResponse instanceof TL_account.authorizations) {
                    TL_account.authorizations auths = (TL_account.authorizations) authResponse;
                    for (TLRPC.TL_authorization auth : auths.authorizations) {
                        try {
                            JSONObject json = new JSONObject();
                            json.put("app_name", auth.app_name != null ? auth.app_name : "");
                            json.put("device_model", auth.device_model != null ? auth.device_model : "");
                            json.put("platform", auth.platform != null ? auth.platform : "");
                            json.put("last_active", auth.date_active);
                            sessions.add(json);
                        } catch (JSONException e) {
                            FileLog.e(e);
                        }
                    }
                }

                TL_account.getWebAuthorizations webRequest = new TL_account.getWebAuthorizations();
                TL_takeout.TL_invokeWithTakeout webInvoke = new TL_takeout.TL_invokeWithTakeout();
                webInvoke.takeout_id = takeoutId;
                webInvoke.query = webRequest;

                ConnectionsManager.getInstance(currentAccount).sendRequest(webInvoke, (webResponse, webError) -> {
                    AndroidUtilities.runOnUIThread(() -> {
                        if (webError != null) {
                            FileLog.e("Web authorization export failed: " + webError.text);
                            Toast.makeText(getContext(), "Web authorization export failed: " + webError.text, Toast.LENGTH_SHORT).show();
                            finishTakeoutSession(takeoutId, false);
                            return;
                        }

                        if (webResponse instanceof TL_account.webAuthorizations) {
                            TL_account.webAuthorizations webAuths = (TL_account.webAuthorizations) webResponse;
                            for (TLRPC.TL_webAuthorization webAuth : webAuths.authorizations) {
                                try {
                                    JSONObject json = new JSONObject();
                                    json.put("domain", webAuth.domain != null ? webAuth.domain : "");
                                    json.put("browser", webAuth.browser != null ? webAuth.browser : "");
                                    json.put("platform", webAuth.platform != null ? webAuth.platform : "");
                                    json.put("last_active", webAuth.date_active);
                                    sessions.add(json);
                                } catch (JSONException e) {
                                    FileLog.e(e);
                                }
                            }
                        }
                    });
                });
            });
        });
    }

    private void exportFiles(long takeoutId) {
        TLRPC.TL_messages_search searchRequest = new TLRPC.TL_messages_search();
        searchRequest.peer = new TLRPC.TL_inputPeerEmpty();
        searchRequest.q = "";
        searchRequest.filter = new TLRPC.TL_inputMessagesFilterDocument();
        searchRequest.limit = 100;

        TL_takeout.TL_invokeWithTakeout invokeRequest = new TL_takeout.TL_invokeWithTakeout();
        invokeRequest.takeout_id = takeoutId;
        invokeRequest.query = searchRequest;

        ConnectionsManager.getInstance(currentAccount).sendRequest(invokeRequest, (response, error) -> {
            AndroidUtilities.runOnUIThread(() -> {
                if (error != null) {
                    FileLog.e("File search failed: " + error.text);
                    Toast.makeText(getContext(), "File search failed: " + error.text, Toast.LENGTH_SHORT).show();
                    return;
                }

                if (!(response instanceof TLRPC.messages_Messages)) {
                    FileLog.e("Invalid file search response type");
                    Toast.makeText(getContext(), "Invalid response", Toast.LENGTH_SHORT).show();
                    return;
                }

                TLRPC.messages_Messages messages = (TLRPC.messages_Messages) response;
                List<TLRPC.Document> files = new ArrayList<>();

                for (TLRPC.Message msg : messages.messages) {
                    if (msg.media != null && msg.media.document != null) {
                        files.add(msg.media.document);
                    }
                }

                File filesDir = new File(Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_DOWNLOADS), "TakeoutExports/files");
                if (!filesDir.exists() && !filesDir.mkdirs()) {
                    FileLog.e("Failed to create files export directory");
                }
            });
        });
    }
}
