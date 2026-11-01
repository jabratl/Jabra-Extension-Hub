Jabra Extension Hub v0.9.39 - Steps S12 + S13: subtitles with the video, shared media settings

CHANGELOG 0.9.39 (code 59):
 S12
 - MediaNaming.kt: subtitleSuffix(url) tells the language from a subtitle link (en.vtt, movie_eng.srt, Spanish.vtt) -> ".en".
 - Downloader.kt: subtitle files are saved as "<video name>.<lang>.vtt/.srt" (e.g. "Show - S01E05.en.vtt"), the name Plex / Jellyfin match to the video. Same page title = same base name as the video.
 - BrowserActivity.kt: Select > Download with video(s) ticked, no subtitle ticked, and subtitle files on the page: asks "Download them with the video?" (Yes / No / Cancel). Audio files stay single downloads (they are not merged).
 S13
 - MediaPrefs.kt: new saved values: detect (default on), library names (default off), delay between batch starts (default 0.2 s). Stream quality was added in 0.9.38.
 - MediaSettings.kt (new): "Media settings" dialog: find media on pages, library names, stream quality, wait between downloads. MediaSettings.show(activity) can be called from any screen.
 - BrowserActivity.kt: menu > "Media settings"; found list stays empty when "Find media on pages" is off; batch downloads start with the chosen wait; episode lists use the library-name setting.
 - Downloader.kt: library names read MediaPrefs.library(). MediaNaming.libraryOn (S9) is removed, nothing else used it.
 - build.gradle.kts: version 0.9.39 (code 59).
Not touched: SettingsActivity.kt (not attached; add a row that calls MediaSettings.show(this) later), PluginActivity.kt, plugin (Media Grabber stays 1.4.4: its own settings are not synced yet, that is S14).
Checked: subtitleSuffix on 6 links (Java copy of the same steps); braces and parens balanced in the 5 changed Kotlin files.
Not built (no Kotlin compiler here), not run on a device.
Please check: Hub builds (new files MediaSettings.kt, MediaPrefs.kt, MediaNaming.kt must be in the project); Browser menu > Media settings opens, Save keeps the values after closing the app; turn "Find media" off and open a page with video: nothing is found; a page with a video and a .vtt: Select the video > Download asks about the subtitle; the files are "<name>.en.vtt" and "<name>.mp4"; library names on + a page titled "Show Episode 5 - Site": "Show - S01E05".
