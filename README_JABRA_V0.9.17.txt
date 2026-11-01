Jabra Extension Hub v0.9.17 - Quiet Pill step D5: media sheet

NEW (browser):
 - The media chip is now an orange pill with a play icon ("3 found"), floating just above the address pill.
 - The media list is a rounded bottom sheet with a drag handle. It slides up; close it with the X, the back gesture, or by dragging the handle/title down.
 - Pinned bar under the title: a filter chip ("All (3)") and a "Select" chip.
 - Select mode shows a contextual bar: X (done), "n selected", All/None, Copy (icon), Grabber. Tap a row to tick it. CHANGE: Copy/Grabber now act on the ticked items only (before, with nothing ticked they used every item). To send everything: Select > All > Grabber.
 - Rows use the app palette (no more fixed #7C8CFF): Play and Download buttons have icons.

Changed: res/layout/activity_browser.xml (REPLACE; ids kept, added panelHead and panelBar, panelClose is now an ImageView), res/layout/item_media.xml (NEW COPY in the zip: REPLACE your own; same ids mname msub mstatus mplay mbtn; uses only q_* colours/drawables), res/drawable/q_handle.xml (new), BrowserActivity.kt (updateChip, hidePanel, attachSheetDrag, showPanel rewritten; chooseMediaFilter/copyLinks/sendToGrabber unchanged), build.gradle.kts (0.9.17 / code 37).
Checked: XML well-formed, all referenced q_* resources exist, brackets balanced. Not built, not run on a device (0.9.14 to 0.9.16 build results also unknown).

Please check: chip shows above the pill and opens the sheet; sheet slides up/down; drag down closes, short drag springs back; filter chip opens the filter dialog; Select > bar appears, tap rows tick them, All/None, Copy toasts "n link(s) copied", Grabber opens the plugin; Done leaves select mode; long-press a row still gives the Copy/Grabber dialog; Play and Download buttons still work; new media found while the sheet is open updates the list; 360 px: the select bar fits on one line.
Trouble spots: (1) select bar too wide at 360 px (shorten "Grabber" or drop its icon); (2) sheet sliding while the keyboard is open; (3) the find-in-page bar still has the old colours; (4) item_media.xml in your project must be replaced, otherwise br_* styling stays.
