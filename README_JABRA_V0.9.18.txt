Jabra Extension Hub v0.9.18 - Quiet Pill step D6: polish

CHANGED (browser):
 - Find in page: now a rounded pill like the address bar (app colours, 48 dp icon buttons for previous/next/close, TalkBack labels). It still replaces the address pill at the bottom while open.
 - Tab grid: 2 columns on phones, 3-4 on wide screens/tablets (one column per ~170 dp). While the grid is open TalkBack no longer reads the page behind it.
 - Media sheet: never taller than 60% of the page (so it does not cover everything on a short landscape screen).
 - Font scale: menu quick-button labels and the select-bar count are cut with "..." instead of clipping.
 - Bookmarks list no longer shows the folder emoji.
DECISION: edge-to-edge is NOT switched on. The app targets SDK 34 (Android 15 does not force it), the status/navigation bars already use the same navy as the page, and the pill/keyboard handling (adjustResize) was not tested. Do it only after the pill and keyboard are confirmed on a device.
Light mode: not done on purpose (Theme.Hub is dark only). The new-tab page has a light palette but WebView normally follows the dark app theme.

Changed files: BrowserActivity.kt, build.gradle.kts (0.9.18 / code 38). No layout or drawable changes.
Checked: brackets balanced (same pattern as the original), all icon/colour resources used exist. Not built, not run on a device. 360/410/1280 px layouts were reviewed in code only, not rendered.

Please check (whole Quiet Pill): build passes; pill at the bottom, not hidden by the keyboard; suggestions above; shrink on scroll; menu sheet; tab grid (open, swipe close, new tab, back); new-tab page; media chip + sheet + select bar; find bar (type, previous/next, close); 360 dp phone and a tablet/landscape; font size set to Large; TalkBack on the tab grid and menu.
Send back: build errors, crash logs, screenshots of anything that looks wrong.
