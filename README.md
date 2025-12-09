# Telegram Data Exporter for Android

A complete, production-ready implementation of Telegram's Takeout API for Android, allowing users to export their Telegram data including chats, contacts, media, stories, profile pictures, and more. This feature was developed for [Boltgram](https://github.com/Tesfamariam-Gebre/Boltgram7.8.1) and is ready to be contributed upstream to the official Telegram Android client.

## 📸 Screenshots

<div align="center">
  <img src="docs/media/IMAGE%202025-12-09%2023:59:53.jpg" alt="Export Options" width="300"/>
  <img src="docs/media/IMAGE%202025-12-09%2023:59:58.jpg" alt="Export Progress" width="300"/>
  <img src="docs/media/IMAGE%202025-12-10%2000:00:04.jpg" alt="Export Results" width="300"/>
  <img src="docs/media/IMAGE%202025-12-10%2000:00:10.jpg" alt="Exported Files" width="300"/>
</div>

## ✨ Features

### Export Capabilities

- **📱 Private Messages**: Export all one-on-one conversations with full message history
- **👥 Group Messages**: Export group chat messages with participant information
- **📢 Channel Messages**: Export channel posts and comments
- **💾 Saved Messages**: Export your saved messages/notes
- **📇 Contacts**: Export saved contacts with phone numbers and names
- **👤 Personal Info**: Export profile information (name, username, phone, bio)
- **📸 Profile Pictures**: Download all profile picture history
- **📖 Stories**: Export stories with media files
- **📁 Files**: Export documents and files (with configurable size limit)
- **🔐 Sessions**: Export active session information

### Export Formats

- **CSV**: Comma-separated values format for easy spreadsheet import
- **JSON**: Structured JSON format for programmatic processing

### User Experience

- ✅ Real-time progress indicators for each export type
- ✅ Individual toggle controls for each data category
- ✅ Configurable file size limits (10-100 MB)
- ✅ Format selection (CSV/JSON)
- ✅ Organized folder structure in Downloads
- ✅ Clickable directory link to view exported files
- ✅ Error handling and status messages

## 🏗️ Architecture

### Core Components

```
takeout/
├── TakeoutFragment.java          # Main UI fragment
├── TL_takeout.java               # Telegram API wrapper classes
└── cells/
    ├── ContactExportCell.java
    ├── PersonalInfoExportCell.java
    ├── StoryExportCell.java
    ├── ProfilePictureExportCell.java
    ├── SavedMessageExportCell.java
    ├── PrivateChatExportCell.java
    ├── GroupChatExportCell.java
    ├── ChannelChatExportCell.java
    ├── ExportButtonCell.java
    ├── ExportTypeRadioCell.java
    ├── HintInnerCell.java
    └── DialogBuckets.java
```

### API Usage

This implementation uses **MTProto** (Telegram's native protocol) via the Telegram Android client's `ConnectionsManager`. Key API methods:

- `account.initTakeoutSession` - Initialize export session
- `invokeWithTakeout` - Execute API calls within takeout context
- `invokeWithMessagesRange` - Fetch messages within specific ranges
- `messages.getSplitRanges` - Get message ranges for efficient pagination
- `messages.getDialogs` - Fetch chat dialogs
- `messages.getHistory` - Fetch message history
- `account.finishTakeoutSession` - Complete export session

### Export Flow

1. **Initialization**: User selects export options and clicks "Export"
2. **Session Creation**: `account.initTakeoutSession` creates a takeout session
3. **Parallel Exports**: Each selected data type exports independently:
   - Contacts, Personal Info, Stories, Profile Pictures use direct API calls
   - Messages (Private/Group/Channel) use range-based pagination
   - Files are searched and downloaded
4. **Progress Tracking**: Real-time updates via delegate callbacks
5. **File Writing**: Data saved to organized folder structure
6. **Session Completion**: `account.finishTakeoutSession` marks export complete

## 📦 Setup Instructions

### Prerequisites

- Android Studio Iguana or later
- Android Gradle Plugin 8.x
- JDK 17+
- Telegram Android client codebase (for integration)
- Android device/emulator with Telegram account

### Integration Steps

1. **Copy the takeout package** to your Telegram Android project:
   ```bash
   cp -r takeout/ <your-telegram-project>/app/src/main/java/plus/takeout/
   ```

2. **Add to your navigation/menu**:
   ```java
   // Example: Add to Settings menu
   TakeoutFragment takeoutFragment = new TakeoutFragment();
   presentFragment(takeoutFragment);
   ```

3. **Ensure required permissions**:
   ```xml
   <!-- AndroidManifest.xml -->
   <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" 
                    android:maxSdkVersion="32" />
   <!-- For Android 13+, use Scoped Storage or SAF -->
   ```

4. **Build and run**:
   ```bash
   ./gradlew assembleDebug
   ```

### Export Directory

Exports are saved to:
```
/storage/emulated/0/Download/Exported Data/
├── Chats/
│   ├── PrivateChatName_20241210_120000.csv
│   └── GroupName_20241210_120000.json
├── Groups/
├── Channels/
├── Contacts/
│   └── contacts_20241210_120000.csv
├── Personal Info/
│   └── personal_info_20241210_120000.json
├── Profile Picture/
│   └── profile_pictures_20241210_120000/
│       ├── metadata.csv
│       └── profile_20241210_120000.jpg
└── Story/
    └── stories_20241210_120000/
        ├── metadata.csv
        └── story_12345.mp4
```

## 🔧 Technical Details

### Message Export Strategy

The implementation uses Telegram's split ranges API for efficient message export:

1. **Get Split Ranges**: `messages.getSplitRanges` returns time-based ranges
2. **Fetch Dialogs**: For each range, fetch dialogs using `invokeWithMessagesRange`
3. **Filter by Type**: Separate private chats, groups, and channels
4. **Fetch History**: For each dialog, recursively fetch all messages
5. **Save to File**: Write messages in batches to CSV/JSON

### Media Download

- **Stories & Profile Pictures**: Uses `FileLoadOperation` with priority queuing
- **Files**: Searches documents and downloads with size limits
- **Progress Tracking**: Atomic counters track pending downloads

### Error Handling

- Network errors are caught and displayed to users
- File write failures are logged and reported
- Session cleanup on fragment destruction
- Graceful degradation when data is unavailable

## 📊 Data Formats

### CSV Format Example

```csv
id,date,from_id,message
12345,Mon Dec 10 12:00:00 GMT 2024,67890,"Hello, world!"
```

### JSON Format Example

```json
[
  {
    "id": 12345,
    "date": "Mon Dec 10 12:00:00 GMT 2024",
    "from_id": 67890,
    "message": "Hello, world!"
  }
]
```

## 🚀 Contributing to Telegram

This implementation is designed to be contributed to the official Telegram Android client.

**📖 Detailed guides:**
- **[CONTRIBUTING.md](CONTRIBUTING.md)** - Complete step-by-step contribution guide
- **[SUBMISSION_GUIDE.md](SUBMISSION_GUIDE.md)** - Quick submission checklist

### Quick Start

1. **Start with a GitHub Issue** (Recommended)
   - Go to: https://github.com/DrKLO/Telegram/issues
   - Click "New Issue" and create a feature request
   - Describe the feature and link to this repo
   - **Note:** The repository doesn't have Discussions enabled, so use Issues instead

2. **Or Submit a Pull Request**
   - Fork: https://github.com/DrKLO/Telegram
   - Integrate the code
   - Create PR with detailed description

3. **Key Points**
   - ✅ Code follows Telegram's style
   - ✅ All files have proper license headers
   - ✅ Implementation is complete and tested
   - ✅ Screenshots and documentation included

### Design Considerations

- **Privacy**: All exports are user-initiated and stored locally
- **Performance**: Pagination and batching prevent memory issues
- **Storage**: Uses Android's Downloads directory (accessible via file managers)
- **Permissions**: Respects Android 13+ scoped storage requirements
- **Throttling**: Respects Telegram's rate limits via takeout API

## 📝 License

This code is licensed under **GNU GPL v. 2 or later**, compatible with Telegram's license. See `LICENSE` file for details.

## 👤 Author

**Created by Tesfamariam Gebre**

This feature was developed as part of the Boltgram project and is available for contribution to the Telegram Android client.

## 🙏 Acknowledgments

- Telegram team for the Takeout API
- Boltgram community for testing and feedback
- Open source contributors who made this possible

## 📚 Additional Resources

- [Telegram API Documentation](https://core.telegram.org/api)
- [MTProto Protocol](https://core.telegram.org/mtproto)
- [Telegram Android Source](https://github.com/DrKLO/Telegram)
- [Boltgram Project](https://github.com/Tesfamariam-Gebre/Boltgram7.8.1)

## ⚠️ Important Notes

- **Rate Limiting**: The takeout API has rate limits. Large exports may take time.
- **Storage Space**: Ensure sufficient storage space before exporting large datasets.
- **Privacy**: Exported data contains personal information. Handle with care.
- **Compatibility**: Requires Telegram Android client v5.x.x or compatible API.

## 🔍 Example Integration

```java
// In your SettingsActivity or similar
public void showExportData() {
    TakeoutFragment fragment = new TakeoutFragment();
    fragment.setArguments(getArguments());
    presentFragment(fragment);
}
```

The fragment handles all UI, API calls, and file operations automatically.

---

**Ready to contribute?** This implementation is production-tested and ready for upstream integration. All code follows Telegram's standards and includes comprehensive error handling and user feedback.
