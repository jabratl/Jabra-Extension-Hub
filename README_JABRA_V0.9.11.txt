Jabra Extension Hub v0.9.11 - Browser media panel: filter, select, copy links, send to Media Grabber

NEW (browser, tap the "found" chip to open the media panel):
 - Show: filter the list by type (All, video, audio, hls, file ...) with counts.
 - Select: shows a tick box on every item. "All" / "None" selects everything shown.
 - Copy (n): copies the links, one per line. With ticks it copies the ticked ones, otherwise everything shown.
 - Grabber (n): sends the links to Media Grabber (any plugin that accepts shared links), the same way as the Android Share sheet "Jabra Grab". The plugin opens with the links listed; nothing downloads by itself. If several plugins accept links you pick one.
 - Long-press one item: Copy link / Send to Media Grabber.
 - The tick selection is cleared when you switch tab.
Limits: the page address (referer) is not sent to the plugin, so links that need it may fail there; use the download button in the browser panel for those.
Changed: BrowserActivity.kt (media panel), build.gradle.kts (0.9.11 / code 31).
Data: no change. Includes 0.9.10 (page tools), 0.9.9 (new-tab page).
Not built or device-tested in this package.
Please check: open the panel on a page with several media; filter; Select + All; Copy and paste somewhere; Grabber with ticks and without; long-press an item; Media Grabber shows the links; the old Download and Play buttons still work.
