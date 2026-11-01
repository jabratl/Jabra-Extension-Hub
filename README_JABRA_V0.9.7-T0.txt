Jabra Extension Hub v0.9.7-t0 - Tab refactor (step T0). NO visible change.

This build only reorganises the browser code so that tabs can be added next (T1). It should look and behave exactly like 0.9.6b.
 - New small Tab class: one WebView plus the media list ("found") of that page.
 - The WebView setup (settings, ad block, media sniffing, pop-up handling, downloads) now lives in one function, setupTab(), instead of onCreate.
 - The media sniffer, the "found" list and the page callbacks are tied to their own tab.
 - Pause / resume / destroy and the desktop-site switch now go through the tab list.
 - Still exactly one tab. Old code keeps using "web" and "found", which now mean the current tab.
Changed: BrowserActivity.kt only, plus build.gradle.kts (0.9.7-t0 / code 26). BrowserStore.kt is the 0.9.6b version.
Data: no change.
Not built or device-tested in this package (no Kotlin compiler was available).
Please check after building: browse, play a video and see the "found" chip and panel, download, bookmark, history, desktop site on/off, pop-up blocking, back button, fullscreen video, rotate and switch apps then return.
