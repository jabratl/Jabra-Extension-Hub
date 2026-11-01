Jabra Extension Hub v0.9.6 - Bookmarks: export, import, folders

NEW in the Hub browser (menu "Bookmarks: export / import"):
 - Export your bookmarks as JSON (Jabra backup) or HTML (the standard bookmark file Chrome, Firefox, Edge and Samsung Internet import).
 - Import from a Jabra JSON file, a bookmark HTML file exported by any browser (folders are kept, nested folders become "A / B"),
   or a plain text file with links. "Add" skips links you already have; "Replace" swaps the whole list after a confirmation.
 - Uses the Android file picker: no storage permission. Files up to 5 MB, up to 3000 bookmarks. Only http/https links are imported.
 - The Bookmarks list shows the folder of each bookmark.
Changed: BrowserStore.kt (folder field, export / import / parser), BrowserActivity.kt (menu item, file picker, dialogs).
Data: old bookmarks keep working (the folder field is optional). Includes v0.9.5 (Share sheet "Jabra Grab").
Not built or device-tested in this package.
