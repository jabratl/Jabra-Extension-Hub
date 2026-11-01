# Handoff: Jabra Hub browser
Type: MODIFY. Project: Jabra Hub browser. Base: jabra-hub v0.9.6b (zip). 
Done: B0, B1 (0.9.6: folder field, export/import JSON+HTML, file picker), B2 (0.9.6b: manager with search, long-press actions, history search + text export).
Files changed in B2: BrowserStore.kt, BrowserActivity.kt (showManager, entryActions, moveToFolder, promptText, historyExport, runBookmarkFile "export_history"), build.gradle.kts, README_JABRA_V0.9.6b.txt. No layout XML changed (dialogs are built in code).
Checks done: brackets balanced, imports verified by reading. NOT compiled, NOT run on a device.
Please test: build passes; search filters live; long-press menus; rename/move/delete refresh the list; folder shows in label; history export file opens; Clear buttons ask first; 360px dialog height (list is 300dp).
Known gaps: showList() is now unused (harmless warning; remove later). No "open in new tab" until tabs. Search is simple substring.
Next: T0 (refactor, no behaviour change): Tab class (WebView + found + title/url), `web` as current-tab getter, one tab only. Then you build and confirm before T1.
Context line: Project: Jabra Hub browser. Mode: MODIFY. Base 0.9.6b. Done: B0-B2. Next: T0 only, then user builds. Then T1+T2, T3, R1..R4, F.
