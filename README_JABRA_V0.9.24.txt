Jabra Extension Hub v0.9.24 - Quiet Pill step H4: Downloads

CHANGED:
 - DownloadsActivity.kt (rewritten, still built in code): toolbar with q_ic_back and a "Clear finished" overflow item; filter chips All / Active (count) / Done / Failed (Failed includes cancelled); rows grouped by day (Today, Yesterday, then the date); empty state per filter.
 - Row (new item_download.xml): kind tile (HLS, VIDEO, AUDIO, SUB, FILE), title, status line, thin q_progress bar while running, two 48 dp icon buttons. Running: Pause/Resume + Cancel. Done: Play (video/audio/HLS) + More. Failed or cancelled: Try again + More. More = Open with... and Remove from list (the file itself is never deleted).
 - Rows are kept between the 800 ms refreshes (only changed text/progress is updated), so taps are not lost and the list does not flicker.
 - Status colours from tokens (q_danger for failed). No "[VIDEO]" text, no back-arrow character, no hardcoded colours.
 - Downloader.kt: DlJob gets paused and retry; DlJobs.remove(). Pause works for HLS (the 4 parts already in flight finish, then it waits) and for direct files (the connection stays open). Cancel while paused works. Speed ignores the paused time. Retry restarts the same link from the start as a new job (the old failed row is removed). Retry is not kept over an app restart; "Interrupted" rows have no Try again.
 - drawable/q_ic_pause.xml (new).
 - Version 0.9.24 (code 44).

NOT touched: item_media.xml (that is the browser's media panel, not Downloads; the copy sent for H4 was the old br_* version, do not paste it back), DownloadService, PlayerActivity.

Changed files: DownloadsActivity.kt, Downloader.kt, item_download.xml (new), q_ic_pause.xml (new), build.gradle.kts (2 lines), README.
Not built, not run, not rendered.
Please check: build passes; start a direct file and an HLS download, pause, resume, cancel while paused; a long pause on a direct file (the server may close the connection, then the row fails and Try again restarts it); failed link then Try again; filters and day headers (change the phone date to test Yesterday); More menu; Clear finished; open Downloads from the bottom nav, browser menu and a plugin; TalkBack on the icon buttons; font size Large; 360 dp and tablet width.
