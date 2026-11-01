Jabra Extension Hub v0.9.8 - Menu fix, tabs come back after a restart

FIXED:
 - The 3-dot menu of the browser could not be opened since 0.9.7 (the app closed when you tapped it). The new "Tabs" and "New tab" menu entries used an order value Android does not accept. They are now first in the menu and the menu opens normally.
NEW:
 - Open tabs are remembered (addresses only, up to 8) and come back the next time you open the browser, with the same tab selected. Tabs you did not look at yet load only when you open them, so a restart stays light.
 - A link sent to the browser at startup opens as an extra tab next to the restored ones.
 - Switching away from a tab pauses its video and audio.
 - Tabs are not remembered when "Save history" is off, and "Clear browsing data" forgets them.
Changed: BrowserActivity.kt, BrowserStore.kt (saved tabs), build.gradle.kts (0.9.8 / code 28).
Data: no change to bookmarks or history.
Not built or device-tested in this package.
Please check: open the 3-dot menu (Tabs and New tab at the top), open 3 tabs, close the app fully, open the browser again, switch between the restored tabs, turn off "Save history" and restart, "Clear browsing data".
Still to do: "open in browser" from a plugin opens a tab only when the browser is already the top screen (needs PluginActivity.kt); no new-tab page yet.
