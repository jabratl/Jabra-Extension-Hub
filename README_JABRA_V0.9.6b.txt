Jabra Extension Hub v0.9.6b - Bookmark and history manager (step B2)

NEW in the Hub browser menu:
 - "Bookmarks" and "History" now open a manager: a search box (title, link or folder) above the list.
 - Tap an item to open it. Long-press an item for actions:
     Bookmarks: Open, Rename, Move to folder (existing folder, no folder, or a new one; "A / B" makes a subfolder), Delete, Copy link.
     History: Open, Add bookmark, Delete from history, Copy link.
 - Bookmarks: button "Tools" opens the export / import menu from 0.9.6. "Clear" removes all after a confirmation.
 - History: button "Export" saves a plain text file (date, title, link; newest first) with the Android file picker. "Clear" empties it after a confirmation.
Changed: BrowserStore.kt (bookmarkFolders, updateBookmark, removeBookmark, removeHistory, exportHistoryText), BrowserActivity.kt (manager dialogs, history export), build.gradle.kts (0.9.6b / code 25).
Data: no format change. Includes 0.9.6 (bookmark export / import / folders) and 0.9.5 (Share sheet "Jabra Grab").
Not built or device-tested in this package (no Kotlin compiler was available). Please build and try: search while typing, long-press on a bookmark and a history item, move to a new folder, export history.
Still to come: "open in new tab" in the long-press menu arrives with tabs (0.9.7).
