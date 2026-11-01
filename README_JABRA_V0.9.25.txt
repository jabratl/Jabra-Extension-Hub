Jabra Extension Hub v0.9.25 - Quiet Pill step H5a: one Share entry

CHANGED:
 - The Share sheet shows ONE entry, "Jabra" (was "Jabra Scan" and "Jabra Grab"). It is ShareActivity; it accepts text/plain, text/html, .docx and .epub.
 - Where a share goes:
   * a file, or text without a link -> read as a chapter (same as the old Jabra Scan);
   * a link and no plugin has "share": true -> the page is read as a chapter (same as before);
   * a link and a plugin has "share": true (Media Grabber) -> a small list: that plugin, or "Read as chapter: <Novel Assistant>". Cancel closes it.
 - ShareActivity passes the share on to IntakeActivity (same intent, file access forwarded). IntakeActivity.kt is unchanged.
 - AndroidManifest.xml: IntakeActivity lost its SEND filter (still has text-selection "Jabra Scan", Open-with, and the clipboard action); ShareActivity got the SEND filters and the label "Jabra".
 - Behaviour change: with Media Grabber installed, sharing a link now asks once where to send it (before you chose the app in the Share sheet). Novel Assistant itself is never offered as a link plugin, even if its manifest says "share": true.
 - Version 0.9.25 (code 45).

Changed files: ShareActivity.kt, AndroidManifest.xml, build.gradle.kts (2 lines), README.
Left for H5b: bubble colours/icons, letter chips, Floater.kt emoji, "Quick input" settings. The list above is still a plain system dialog (the Intake theme is not a Material theme; styling goes with H7).
Not built, not run.
Please check: share a link from the browser (with and without Media Grabber installed); share selected text without a link; share a .docx and an .epub from Files; share a .txt file; select text and use "Jabra Scan" in the selection menu; Open-with on a .txt; Hub menu "scan clipboard"; Share sheet shows only one Jabra entry (restart the sheet or reinstall if the old two stay cached).
