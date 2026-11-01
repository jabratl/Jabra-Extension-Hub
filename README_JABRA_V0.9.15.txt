Jabra Extension Hub v0.9.15 - Quiet Pill step D3: tab switcher grid

NEW (browser):
 - Tap the tab-count button in the address pill (or menu > Tabs): a full-screen grid opens instead of the old dialog.
 - Each tab is a rounded card: title, site name, close (X) and a thumbnail of the page. The current tab has an orange outline.
 - Tap a card to open that tab. Swipe a card left or right (or flick it) to close it. The last tab is never removed: it goes back to a fresh page.
 - Big orange "New tab" button at the bottom. Top right shows "3 / 8" (limit is 8 tabs). Back arrow, back gesture or back button close the grid.
 - Thumbnails are small (360 px wide, about 0.3 MB each, 8 tabs = about 2.6 MB). They are taken when you leave a tab and when the grid opens. Tabs restored after a restart show a letter until you visit them.

Changed: BrowserActivity.kt (Tab.thumb, captureThumb, showTabs/hideTabs/tabCard/attachSwipe, switchTo(captureOld), closeTab, back callback, onNewIntent), res/drawable/q_btn_accent.xml (new), res/drawable/q_tab_card_on.xml (new), build.gradle.kts (0.9.15 / code 35).
Not changed: activity_browser.xml, NewTabPage.kt, item_media.xml, media panel.
Not built or device-tested in this package (0.9.14 build result also still unknown).

Please check: grid opens from the tab button and from the menu; cards show thumbnails after you visited a few pages; tap opens the right tab; swipe closes a tab (card slides out, others re-flow, count and badge update); swipe on the last tab springs back and opens a fresh page; New tab works and is dimmed at 8 tabs (toast); back gesture closes the grid first; list scrolls with 8 tabs on a small phone; thumbnails are not black/blank on video or heavy pages (WebView.draw limitation).
Trouble spots to expect: (1) black or blank thumbnails (hardware-accelerated WebView/video; then switch to PixelCopy); (2) swipe fighting with the vertical scroll; (3) card corners/outline clipping; (4) grid looks cramped at font scale 1.3+; (5) status/navigation bar colour behind the grid (edge-to-edge comes in D6).
