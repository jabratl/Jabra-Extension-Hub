Jabra Extension Hub v0.9.40 - Step S14: plugin sync (mediaPrefs bridge, Scan in Browser, saved quality in plugin batches)

CHANGELOG 0.9.40 (code 60):
 S14
 - JabraApiContract.kt: new capability "mediaPrefs".
 - PluginActivity.kt: JabraAPI.mediaPrefs() (read-only JSON: detect, library, delayMs, hlsHeight). mediaDownload and mediaDownloadEx use the stream quality saved in Media settings (MediaPrefs.hlsHeight) when the plugin gives no quality of its own.
 - build.gradle.kts: version 0.9.40 (code 60).
 - Also in this build: PluginActivity.kt no longer uses ThemeStore (build fix from 0.9.39): theme() follows the phone's light/dark setting.
Plugin: Media Grabber 1.4.5 (media-grabber-plugin_v1_4_5.zip): page links get "Scan in Browser" (warns if the Hub's "Find media on pages" is off); a Settings line shows the Hub's stream quality.
Not touched: Browser, Share, Floater, entry points (S15/S16), colours (S17).
Checked: braces/parens balanced in changed Kotlin; node --check on the plugin script; no duplicate ids; manifest.json valid.
Not built (no Kotlin compiler here), not run on a device.
Please check: Hub builds and validate passes; Media settings quality = 480p, then in Media Grabber download an HLS link: the file is the 480p one; Media Grabber Settings shows "up to 480p"; page link shows "Scan in Browser" and opens the Browser; turn "Find media" off, tap it: a note appears; older Hub: nothing breaks.
