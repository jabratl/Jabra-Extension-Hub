# Handoff: Jabra Hub browser
Type: MODIFY. Project: Jabra Hub browser. Base: jabra-hub v0.9.7-t0 (zip).
Done: B0, B1 (0.9.6), B2 (0.9.6b manager), T0 (0.9.7-t0 refactor, no behaviour change).

T0 changes (BrowserActivity.kt only + build.gradle.kts):
- `internal class Tab(val web: WebView)` with `found` map. Fields: `tabs: ArrayList<Tab>`, `curTab: Tab?`.
- `web` and `found` are now private getters = current tab (`curTab!!`). All old call sites unchanged.
- `setupTab(t)` = all WebView setup, moved out of onCreate; callbacks use `t` (own tab) and update UI only `if (t === curTab)`.
- `internal inner class Sniff(tab)`; `onFound(tab, url, kind)`; `applyDesktop(w)` + `applyDesktopAll()`.
- onPause pauses all tabs, onResume resumes current, onDestroy destroys all.
Checks done: brackets balanced, no leftover bare `web` in setupTab, no old-style onFound calls. NOT compiled, NOT run on a device.

USER MUST: build and confirm before T1. Test: normal browsing, found chip/panel with a video, download, bookmark, history, desktop toggle, pop-up block, back button, fullscreen video, rotate/app switch.
Possible compile traps if the build fails: visibility of Media (defined outside the patch files; Tab is internal to be safe), `curTab!!` getters, local use of `this` inside setupTab lambdas. Send the first error lines.

Next (only after the user confirms the build): T1 tab UI. Plan: tab count button, tab switcher dialog (title, close, new tab), `newTab(url)` = new WebView + `setupTab`, switch = swap the view in the container that holds R.id.web (find its parent ViewGroup), update urlBar/chip/progress from the tab. Needs `activity_browser.xml` (not in this zip: ask the user for it). T2: long-press link "Open in new tab", real new-window opens a tab, openBrowser from Hub/plugin adds a tab (PluginActivity.kt needed).
Context line: Project: Jabra Hub browser. Mode: MODIFY. Base 0.9.7-t0 (build confirmed: yes/no). Done: B0-B2, T0. Next: T1+T2 (send activity_browser.xml and PluginActivity.kt).
