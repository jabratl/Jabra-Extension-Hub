Jabra Extension Hub v0.9.27 - Quiet Pill step H6: browser menu

CHANGED (BrowserActivity.kt, menu only):
 - "Tabs" row removed: the tab-count button in the address pill already opens the tabs. "New tab" stays and is now the first row.
 - Ad block and Desktop now mean the same thing: both buttons in the quick row act on the page you are on (before, Ad block was for all sites and Desktop for this site, and each had a second row with the opposite scope).
 - The rows "Ad blocker on this site" and "Desktop for all sites" are gone. The all-sites switches are the first two lines of Site settings (the row shows their state). Site settings also shows "Blocked N on this page", a hint when no site has its own setting, and then the sites with their own setting (tap to reset).
 - Rows regrouped: tabs/bookmarks/history/downloads | page (Forward, Reload, Copy link, Text size, Save page) | browser (Search engine, Site settings, Save history) | data and close. Search engine uses the globe icon (it shared the Find icon).
 - Texts that named old menus ("Page tools ->") now say "Menu > Site settings".
 - menuAction ids are unchanged (6, 27 and 20 still work); toggleAdblockAll() and toggleDesktopAll() are new helper functions. Desktop for all sites now shows a toast.
 - Version 0.9.27 (code 47).

Changed files: BrowserActivity.kt (menu code only), build.gradle.kts (2 lines), README. BrowserStore.kt unchanged.
Left for later: "Bookmarks: export / import" could move into the bookmarks manager; "Save page" and "Downloads" share one icon (needs a new vector, H7).
Not built, not run, not rendered.
Please check: menu opens; Ad block button on a normal page (toggles that site, page reloads, toast names the site), with the global switch off (toast points to Site settings); Desktop button; Site settings: global switches, per-site reset, empty hint; New tab; the tabs button in the pill; TalkBack reads the quick buttons as on/off.
