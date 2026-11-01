Jabra Extension Hub v0.9.41 - Steps S15 + S16: links to the Browser found list, media entry points

CHANGELOG 0.9.41 (code 61):
 S15
 - Floater.kt: Floater.firstLink(text) and Floater.openInBrowser(ctx, text): open the first link of a shared or copied text in the Browser (its found list scans the page when "Find media on pages" is on).
 - ShareActivity.kt: the "Send link to" chooser has a new line "Scan in Browser (found list)" (or "Open in Browser" when Find media is off). No link or no Browser = read as chapter, as before. The chooser still appears only when a plugin accepts shared links (Media Grabber), so chapter-only users see no new dialog.
 S16
 - MainActivity.kt: Home grid starts with a built-in "Media Browser" tile (tap = Browser, long-press = Media settings).
 - SettingsActivity.kt: new section "Media": "Media settings" (MediaSettings.show) and "Media Browser".
 - Onboarding.kt: the Browser line now mentions the found list and Media settings (shown on a fresh install only).
 - build.gradle.kts: version 0.9.41 (code 61).
Not done (NEXT): bubble link -> Browser needs FloaterActivity.kt (+ BubbleService.kt), not attached; Floater.openInBrowser is ready for it. PluginStore.kt not changed: it only lists zip plugins; the Home tile and Settings rows are the entry points instead. AndroidManifest.xml not changed (BrowserActivity is already declared).
Checked: braces/parens balanced in the 5 changed Kotlin files (Floater shows -1 only because of the ')' character in trimEnd).
Not built (no Kotlin compiler here), not run on a device.
Please check: Hub builds; Share a page link from Chrome > chooser shows "Scan in Browser (found list)" > Browser opens that page and the found list fills when a video plays; Home shows the Media Browser tile first (tap opens Browser, long-press opens Media settings); Settings > Media > both rows work; with no plugin accepting share, a shared link still goes to the chapter reader.
