Jabra Extension Hub v0.9.12 - Per-site ad blocker and desktop mode, Downloads list kept after restart

NEW (browser):
 - Ad blocker on this site (3-dot menu -> Page tools): untick it to let ads through on one site only (for sites that break with the blocker). Tick again to block. The global "Ad blocker" switch still turns it off everywhere.
 - Desktop site (3-dot menu) now works for the site you are on only. Page tools -> "Desktop for all sites" is the old global switch; a site can differ from it (e.g. global off, one site on).
 - Page tools -> Site settings: lists every site with its own setting; tap one to reset it.
 - Switching to a site with a different desktop setting changes the browser identity before the page loads; for typed or redirected addresses the page may load once more.
NEW (Downloads):
 - The Downloads screen keeps its list after the app is closed (last 100 entries). A download that was running when the app closed shows "Interrupted (the app was closed)". "Clear finished" empties the list.
Changed: BrowserActivity.kt, BrowserStore.kt (adblock_allow, desktop_sites), Downloader.kt (DlJobs keeps and loads the list, DlJob.ts), DownloadsActivity.kt (loads the list), build.gradle.kts (0.9.12 / code 32).
IMPORTANT: Downloader.kt and DownloadsActivity.kt are now in this zip with the changes; replace your project's copies with these (they were built from the files you sent, so nothing else changes). AdBlock.kt needs no change.
Data: new preferences "adblock_allow", "desktop_sites" (browser_prefs) and "dl_history". No change to bookmarks, history, tabs.
Not built or device-tested in this package.
Please check: Page tools -> Ad blocker on this site (reload, ads come back); Desktop site on one site only and another site stays mobile; Site settings lists and resets; start a download, close the app fully, open Downloads: the entry is still there (Play / Open with still work); Clear finished.
