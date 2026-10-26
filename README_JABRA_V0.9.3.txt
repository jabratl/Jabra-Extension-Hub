Jabra Extension Hub v0.9.3 - A real browser: ad blocker, player, history, bookmarks

NEW in the Hub browser (Menu -> "Browser - catch media"):
 - Ad blocker: blocks requests to well-known ad / pop-under / tracker networks, drops pop-ups the user did not tap,
   stops automatic redirects to ad domains and hides common ad boxes. Menu (the three dots) shows how many were blocked on this page.
   Switch it off per your need from the same menu (the page reloads).
 - Built-in media player (Media3 / ExoPlayer): every found video, HLS stream or audio file has a "Play" button next to "Download".
   Seek bar, play/pause, speed (0.5x - 2x), screen stays on, sends the page's Referer, cookies and user agent. Encrypted / DRM streams do not play.
 - History (last 500 pages) and Bookmarks. The browser reopens where you left off.
 - Fullscreen video from inside web pages (landscape, swipe from the edge for the system bars).
 - Desktop site switch, Share link, Copy link, Forward, Reload, Clear browsing data, "Save history" switch.
 - Better media finding: also catches fetch/XHR calls to .m3u8 or media files, and <video>/<audio> sources the page sets by script
   (not only requests whose file name ends in .m3u8 / .mp4).
 - Links with app schemes (intent:, market:) no longer show an error page.
 - Pause/resume of page media when the screen is left.

Plugin API (capability "mediaPlayer"): JabraAPI.mediaPlay(url, referer, title) opens the built-in player. See docs/BROWSER_API.md.

Changed files: BrowserActivity.kt (rewritten), new AdBlock.kt, BrowserStore.kt, PlayerActivity.kt, PluginActivity.kt (mediaPlay),
JabraApiContract.kt, AndroidManifest.xml, activity_browser.xml, item_media.xml, build.gradle.kts (version 0.9.3 / code 21, Media3 1.3.1 dependencies).
Downloader.kt is unchanged.

Not built or device-tested in this package. First build: needs Internet for the new Media3 dependencies.
Known limits: no multiple tabs yet; the ad list is built in (not an updatable filter list); sites that detect ad blockers may complain.
