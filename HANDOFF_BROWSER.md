# Handoff: Jabra Hub browser
Type: MODIFY. Project: Jabra Hub browser. Base: jabra-hub v0.9.8 (zip). versionName must be plain x.y.z (the CI script scripts/validate_source.py rejects suffixes like -t1) and needs README_JABRA_V<version>.txt.
Done: B0, B1 (0.9.6), B2 manager, T0 refactor (build confirmed by user), T1 + T2 browser side. All of B2 + T0 + T1 + T2 ship as 0.9.7 (versionCode 27).

T1/T2 changes:
- BrowserActivity.kt: fields maxTabs=8, webHost/webIdx/webId/webLp (where the XML WebView sits; remembered in onCreate), tabBtn.
  newTab(url, show): Boolean (limit toast), switchTo(t) (pause + detach old, attach new at webIdx with webLp, same id, refresh urlBar/chip/panel), closeTab(t) (last tab resets), showTabs() (rows built in code), addTabButton() (inserts a TextView before R.id.menu only if its parent is a horizontal LinearLayout; colour copied from R.id.close if it is a TextView, else grey), showLinkMenu(), onNewIntent().
  setupTab: long-press listener (hitTestResult + requestFocusNodeHref), new-window tap uses newTab (falls back to same tab at the limit).
  goBack: closes the tab when it cannot go back and more tabs exist. Clear browsing data: clearHistory on all tabs. Menu: ids 20 Tabs, 21 New tab (orders -2, -1).
- AndroidManifest.xml: BrowserActivity launchMode singleTop.
Checks done: brackets balanced, manifest edit verified. NOT compiled, NOT run on a device.

USER MUST test (see README_JABRA_V0.9.7.txt). Likely trouble spots if something is off:
1. Tab button not visible / wrong colour: top bar is not a horizontal LinearLayout or bar colours differ. Menu "Tabs (N)" still works. Send activity_browser.xml to fix properly.
2. Sound or video keeps playing in a background tab: WebView.onPause may not stop media on this device; fix = also call pauseTimers / evaluate pause JS (planned in T3).
3. First WebView comes from XML, new ones are plain WebView(this): look/overscroll can differ slightly.
4. "Open in browser" from a plugin/Hub: onNewIntent only fires when BrowserActivity is the top activity (singleTop). If the browser is behind the plugin, a second browser instance opens. Fix needs PluginActivity.kt (where openBrowser starts the browser): add Intent.FLAG_ACTIVITY_REORDER_TO_FRONT (test on device that onNewIntent fires) or finish the plugin first. Please send PluginActivity.kt.
Next: T3 (restore tabs after restart: urls, max 8, last active; background tab pause + cap; media chip per tab is already per tab) then R1 (new-tab page with bookmarks grid, search engine choice, address suggestions), R2-R4, F.
Context line: Project: Jabra Hub browser. Mode: MODIFY. Base 0.9.7 (build confirmed: yes/no). Done: B0-B2, T0, T1, T2 (browser side). Next: T2 plugin side (send PluginActivity.kt) + T3.


## 0.9.8 (this zip)
BUG FIXED (my T1 mistake): PopupMenu.add(group,id,order,title) with a negative order throws IllegalArgumentException ("order does not contain a valid category"), so the 3-dot menu crashed. "Tabs"/"New tab" now use order 0 and are added first (menu keeps insertion order for equal orders). Rule for next time: menu orders must be >= 0.
T3 (partial): Tab.pending/savedTitle (lazy load on first show, loadPending()), BrowserStore.saveTabs/savedTabs/savedTabCur/clearTabs (JSON in browser_prefs, max 8, http/https only), saveTabs() in onPause (skipped and cleared when Save history is off), restore in onCreate (an "url" extra adds one extra tab), clear browsing data also clears saved tabs, media paused via JS when leaving a tab.
Not done from T3: memory cap beyond 8 tabs, per-tab restore of back/forward history.
Checks done: brackets balanced, no negative menu orders left. NOT compiled, NOT run on a device.
USER MUST test: menu opens; tab restore; Save history off; clear data; link long-press; tab button visible.
Still open: T2 plugin side (send PluginActivity.kt), tab button colour/position (send activity_browser.xml if wrong).
Next: R1 new-tab page + search engine + suggestions, then R2-R4, F.
Context line: Project: Jabra Hub browser. Mode: MODIFY. Base 0.9.8 (build: yes/no, validator: pass/fail). Done: B0-B2, T0-T3, T2 browser side. Next: T2 plugin side (PluginActivity.kt) + R1.


## 0.9.9 (this zip)
Done: T2 plugin side (user-confirmed working: PluginActivity.Bridge.openBrowser now adds FLAG_ACTIVITY_REORDER_TO_FRONT or FLAG_ACTIVITY_SINGLE_TOP; PluginActivity.kt is NOT in this zip, the user applied the patch in their own project - ask for the file again if it must be edited).
R1: NewTabPage.kt (new): https://jabra.newtab/ is answered by BrowserActivity's shouldInterceptRequest (HTML: search box, bookmark tiles, recent history), so back/forward/reload work. Not stored in history/last_url; address bar shows empty. BrowserStore: engines list (index in browser_prefs key "engine"), engine(), searchUrl(), suggest(). BrowserActivity: setUrlText(), showSuggest() (ListPopupWindow under the bar, non-modal), chooseEngine() (menu id 22, order 2), go() uses the chosen engine, all "https://duckduckgo.com" defaults replaced by NewTabPage.URL, tabHost() for the tab list.
Rules learned: menu orders must be >= 0. Layout activity_browser.xml was never seen; everything is built in code.
Checks done: brackets balanced, no leftover duckduckgo default URLs. NOT compiled, NOT run on a device.
USER MUST test: see README_JABRA_V0.9.9.txt. Likely trouble spots: (1) suggestion list width/position if the url bar parent is odd; (2) ListPopupWindow stealing keyboard focus (then set isFocusable false / use a PopupWindow); (3) new-tab page blank if shouldInterceptRequest is not hit for the https://jabra.newtab/ URL (check AdBlock/other interceptors); (4) back from a site opened from the new-tab page should return to the page.
Not done: favicons on tiles, per-engine suggestions from the web, editing tiles, "set as home page".
Next: R2 (find in page, text zoom, dark-page mode, save page), R3 (media panel: select all, filter, copy link, send all to Media Grabber via Floater.kt), R4 (ad-block per-site allow-list, per-site desktop mode, Downloads list after restart), F (360/410px, dark/light, build, device test, changelog).
Context line: Project: Jabra Hub browser. Mode: MODIFY. Base 0.9.9 (build: yes/no, validator: pass/fail). Done: B0-B2, T0-T3, T2 browser+plugin side, R1. Next: R2.


## 0.9.10 (this zip)
Done: R2. BrowserActivity: menu submenu "Page tools" (ids 23 find, 24 text size, 25 dark pages checkable, 26 save page; submenu id 30, order 3). showFind()/hideFind(): bottom bar added to android.R.id.content (FrameLayout, Gravity.BOTTOM), WebView.findAllAsync + setFindListener, closed in switchTo/onPause/Back. Text size: BrowserStore.textZoom (settings.textZoom set in setupTab and on change). Dark pages: CSS invert+hue-rotate injected by evaluateJavascript in onPageCommitVisible/onPageFinished (applyDark), skipped on NewTabPage. Save page: savePdf() uses PrintManager; saveArchive() uses WebView.saveWebArchive into cache/shared_exports then MediaStore Downloads (API 29+) or FileProvider share (below).
Checks done: brackets balanced. NOT compiled, NOT run on a device.
USER MUST test: see README_JABRA_V0.9.10.txt. Trouble spots: (1) PopupMenu submenu clicks not reaching the listener (then make the 4 entries top-level instead); (2) find bar hidden behind the media panel or keyboard; (3) dark pages looks wrong on some sites; (4) FileProvider path for cache/shared_exports exists only if res/xml/file_paths has a cache-path (used already by the plugin share), needed only on Android 9 and below.
Media Grabber question (answered to the user): the Media Grabber plugin cannot run inside browser pages (no Chrome-extension runtime, and giving pages the JabraAPI bridge would be a security hole). The browser already has its own built-in media finder (chip + panel, download, play). R3 enriches it and adds "Send all to Media Grabber": build a text payload (one link per line, with page title) and hand it over like the share sheet does: pick plugins with Floater.acceptsShare(ctx, id), Floater.save(ctx, pluginId, text, "share"), then start PluginActivity with extras id/name/entry (same as ShareActivity.kt line ~63; copy that code). PluginActivity.onNewIntent already calls window.__jabraFloater(). Plugin ids are not in the zip: list installed plugins that accept share and let the user pick.
Next: R3 (media panel: select all, type filter, copy link, send all to Media Grabber), R4 (ad-block per-site allow-list, per-site desktop mode, Downloads list after restart), F.
Context line: Project: Jabra Hub browser. Mode: MODIFY. Base 0.9.10 (build: yes/no, validator: pass/fail). Done: B0-B2, T0-T3, T2 both sides, R1, R2. Next: R3.


## 0.9.11 (this zip)
Done: R3. BrowserActivity.showPanel() rebuilt: header (filter button -> chooseMediaFilter(), row Select / All|None / Copy (n) / Grabber (n)), each item wrapped in a row with a CheckBox (visible in select mode) and long-press -> mediaActions(). State: panelFilter, selectMode, selected (urls), cleared on tab switch. copyLinks(), sendToGrabber(): plugins = PluginStore(this).list().filter { Floater.acceptsShare(...) } (pick dialog if several, toast if none), payload = page title line + one media URL per line, Floater.save(ctx, id, text, "share"), then PluginActivity with extras id/name/entry and flags REORDER_TO_FRONT|SINGLE_TOP (onNewIntent calls __jabraFloater when the same plugin is already open). Referer is NOT sent.
Checks done: brackets balanced. NOT compiled, NOT run on a device. Media / PluginStore / AppDiagnostics are not in the zip (used as in ShareActivity/old code).
USER MUST test: see README_JABRA_V0.9.11.txt. Trouble spots: (1) item_media layout rows with checkbox look cramped; (2) Media Grabber plugin must declare "share": true and read floaterGet; (3) panel header scrolls with the list.
Next: R4 (ad-block per-site allow-list: AdBlock.kt + BrowserStore; per-site desktop mode; Downloads list after restart: Downloader.kt / DlJobs - these files are NOT in the zip, ask the user to send AdBlock.kt and Downloader.kt (and DownloadsActivity.kt) first), then F (360/410px, dark/light, build, device test, changelog).
Context line: Project: Jabra Hub browser. Mode: MODIFY. Base 0.9.11 (build: yes/no, validator: pass/fail). Done: B0-B2, T0-T3, T2 both sides, R1, R2, R3. Next: R4 (needs AdBlock.kt, Downloader.kt).


## 0.9.12 (this zip)
Done: R4. BrowserStore: siteKey(), adblockAllowed/setAdblockAllowed (JSON list "adblock_allow", subdomains match parent), desktopSites/setDesktopSite/removeDesktopSite (JSON map "desktop_sites", only entries that differ from the global switch). BrowserActivity: Tab.pageHost (@Volatile, set in shouldInterceptRequest main frame, shouldOverrideUrlLoading, onPageStarted, go()), Tab.desktopOn; adblockOn(tab) replaces every `adblock` check inside tabs (Sniff.adblock() too, so the page CSS hide script follows the site); desktopFor(host), syncDesktop(tab) (changes UA; onPageStarted reloads once if it had to change); menu id 7 = this site's desktop toggle, Page tools: 27 desktop for all sites (old global), 28 ad blocker on this site, 29 site settings dialog (tap to reset). Downloads: Downloader.kt (now in the zip, built from the user's upload): DlJobs.init(ctx)/save() store up to 100 jobs in prefs "dl_history" (running ones come back as "Interrupted"), called from Downloader init block, after create, at the end of every job, and in clearFinished; DlJob.ts; DownloadsActivity.kt (in the zip) calls DlJobs.init. AdBlock.kt unchanged and NOT in the zip.
Checks done: brackets balanced. NOT compiled, NOT run on a device.
USER MUST test: see README_JABRA_V0.9.12.txt. Trouble spots: (1) user agent switch not taking effect on the first request (then a second reload happens in onPageStarted); (2) a download list entry whose file was deleted (Open fails with a toast); (3) PopupMenu submenu clicks (ids 23-29) still unconfirmed by the user.
Files the user must replace/keep in the project: Downloader.kt, DownloadsActivity.kt (from this zip), plus all files changed since 0.9.8. PluginActivity.kt patch (openBrowser flags) is in the user's project only.
Next: F (final): check 360/410 px widths, dark/light, build, device test of everything, changelog; fixes only. Ask the user for build results, crash logs and screenshots of anything that looks wrong (activity_browser.xml / item_media.xml / layouts were never seen).
Context line: Project: Jabra Hub browser. Mode: MODIFY. Base 0.9.12 (build: yes/no, validator: pass/fail). Done: B0-B2, T0-T3, T2 both sides, R1-R4. Next: F (fixes from the user's device test).


## 0.9.13 (this zip) - Quiet Pill design, step D0
Design plan is in docs/DESIGN_QUIET_PILL.md (user decided: address bar at the BOTTOM). Rules: patch only, new resource names start with q_, never touch br_* without seeing the files.
Done: D0 = res/values/q_tokens.xml + values-night/q_tokens.xml (colours, dimens), q_styles.xml (ShapeAppearance.Q.Pill/Card/Sheet, no parent), 8 shape drawables (q_pill, q_card, q_chip, q_chip_accent, q_sheet_bg, q_circle_btn, q_circle_btn_on, q_progress), 23 vector icons q_ic_*, Q.kt (dp, isDark, color, icon, tick, pop). Motion is interpolator-based (no dynamicanimation dependency added). No existing Kotlin edited. versionName 0.9.13 / code 33.
Seen existing names (from code): colors br_bg, br_surface, br_text, br_sub, br_accent; drawables br_chip, br_chip_accent; theme Theme.Hub; layout activity_browser (ids web, url, chip, panel, panelList, progress, back, close, menu, panelClose), item_media (ids mname, msub, mstatus, mbtn, mplay). The res folder was NOT in the zip, activity_browser.xml / item_media.xml / themes.xml / colors.xml never seen.
Open risk: if Theme.Hub is a fixed dark theme, values-night will not match a light phone setting. Decide in D1 after seeing themes.xml (either force night qualifiers via AppCompatDelegate or put dark values in values/).
Checks done: XML well-formed (xmllint), Q.kt brackets balanced. No JS in project so node --check not applicable. NOT compiled, NOT run on a device.
Next: D1 (+D2 if it fits): bottom pill, auto-hide, suggestions above, menu bottom sheet. User must upload activity_browser.xml, item_media.xml, colors.xml, themes.xml, br_chip and br_chip_accent drawables (and the build result of 0.9.13).
Context line: Project: Jabra Hub browser. Mode: MODIFY. Base 0.9.13 (build: yes/no, validator: pass/fail). Done: B0-B2, T0-T3, R1-R4, D0. Next: D1.


## 0.9.14 (this zip) - Quiet Pill D1 + D2
Done: D1 bottom pill, D2 menu bottom sheet. Base 0.9.13 (D0, build result not reported by the user yet).
Facts learned from the user's res files (copies in docs/res_seen/): Theme.Hub = Theme.Material3.Dark.NoActionBar (dark only, status/nav bar = @color/bg). Palette: bg #141629, surface #1B1E38, card #222648, stroke #30356B, accent #FF8A5B, teal #5EEAD4, text #F4F4FF, sub #A3A8D6, danger #FF8DA1; br_* = same values (colors_browser.xml). item_media.xml uses br_card, br_stroke, br_teal, MaterialCardView 18dp. Manifest already has windowSoftInputMode adjustResize for BrowserActivity. The plan's accent #7C8CFF was a wrong guess (only the media panel code has it hardcoded, BrowserActivity showPanel `val accent = 0xFF7C8CFF`); q_* tokens now use the app palette (dark in values/ and values-night/). br_chip_accent / br_chip drawables were never seen (user sent bg_chip.xml: #2A2F5E, 20dp; the layout still uses @drawable/br_chip_accent for the chip). Light mode is NOT planned unless asked.
Layout activity_browser.xml (rewritten): root vertical LinearLayout; page FrameLayout (web, chip, panel, ScrollView id suggest/suggestList gone, gravity bottom); FrameLayout id pillWrap (fixed 72dp, focusableInTouchMode) holding LinearLayout id pill (secure ImageView, url EditText, clearUrl, tabsBtn FrameLayout + tabCount TextView, menu ImageView) and ProgressBar id progress (3dp, q_progress). Ids back and close removed.
BrowserActivity.kt: fields tabCount, tabsBtn, pillWrap, pill, secureIcon, clearBtn, menuBtn, suggestBox, suggestList, fullUrl, compact, compactAnim (tabBtn and suggestPopup removed). setUrlText stores fullUrl and shows only the host unless the bar has focus; onUrlFocus(has) (typing mode, hides tab/menu buttons, TransitionManager); blurUrl() (hides IME, clears focus, focus to pillWrap); setCompact(on) (ValueAnimator on pill margins/height, side buttons fade; called from scroll listener in setupTab, onPageStarted, switchTo); showSuggest builds rows in suggestList (max rows from page height); addTabButton() now only wires the existing button; showMenu() = BottomSheetDialog (quick row + list built in code, ids as before) and menuAction(id, pageUrl) holds the old `when` (new id 14 = finish()). Back callback: urlBar focus or suggestions visible -> blurUrl().
Checks done: brackets balanced, all new ids/drawables/colors exist, XML well-formed. NOT compiled, NOT run on a device.
Trouble spots to expect: (1) BottomSheetDialog background/drag handle (M3 may add its own handle); (2) pill shrink animation jerky or pill hidden by keyboard (adjustResize is set in the manifest); (3) EditText refocusing itself after blurUrl (pillWrap takes focus); (4) tab-count text position inside the tabs icon; (5) first back press with the keyboard open only closes the keyboard, the second leaves typing mode; (6) find-in-page bar (added to android.R.id.content) covers the pill while open, uses the old colours; (7) q_pill stroke/contrast on the dark band.
Not done: edge-to-edge (insets), TalkBack pass on the old dialogs, tab grid, new-tab page, media sheet, font-scale test.
Next: D3 tab switcher grid (BrowserActivity.showTabs + thumbnails, cap 8; Tab gets a thumbnail Bitmap taken when leaving a tab, small size) ; then D4 NewTabPage.kt (in zip), D5 media sheet (item_media.xml in docs/res_seen, panel in activity_browser.xml), D6 polish.
Context line: Project: Jabra Hub browser. Mode: MODIFY. Base 0.9.14 (build: yes/no, validator: pass/fail). Done: B0-B2, T0-T3, R1-R4, D0, D1, D2. Next: D3.


## 0.9.15 (this zip) - Quiet Pill D3
Done: D3 tab switcher grid. Base 0.9.14 (build result not reported by the user yet). BrowserActivity.kt only (+ 2 drawables q_btn_accent, q_tab_card_on; build.gradle 0.9.15 / code 35).
How: Tab.thumb (Bitmap RGB_565, 360 px wide, ratio THUMB_RATIO 1.2) via captureThumb(t) = WebView.draw into a scaled Canvas; called in switchTo(t, captureOld=true) before the old WebView is detached, and for curTab when the grid opens; cleared in closeTab. showTabs() builds a full-screen overlay (FrameLayout added to android.R.id.content, field tabsOverlay): top bar (back arrow, "Tabs", "n / 8"), ScrollView with rows of two tabCard()s, bottom accent "New tab" button (dimmed at maxTabs). tabCard(): header (title, host, X 48dp), RatioFrame thumbnail (letter or search icon when no thumbnail), current card outlined q_tab_card_on, rounded via clipToOutline. attachSwipe(): touch listener (drag > slop and horizontal -> translate+fade, release > 40% width or fling > 1200 px/s closes via closeTab + fill(), else spring back; requestDisallowInterceptTouchEvent). hideTabs() fades out, tag "closing" blocks double back. Back callback: tabsOverlay first. onNewIntent hides the grid. closeTab uses switchTo(next, false).
Checks done: brackets balanced (new block 42/42 braces, 290/290 parens), XML well-formed, all q_* resources used exist. No JS in project (node --check not applicable). NOT compiled, NOT run on a device.
Trouble spots: blank/black thumbnails (then PixelCopy), swipe vs scroll, small-screen/font-scale layout, bar colours behind the grid (D6).
Not done: edge-to-edge, thumbnails are not saved across restarts (placeholder letter until visited), Close all, TalkBack custom action for swipe-close (the X has its own label), find-bar colours, media sheet, new-tab page restyle, light mode.
Next: D4 NewTabPage.kt restyle (in zip): big search pill, bookmark tiles with favicons, less clutter (dark only unless asked). Then D5 media sheet (item_media.xml in docs/res_seen), D6 polish.
Context line: Project: Jabra Hub browser. Mode: MODIFY. Base 0.9.15 (build: yes/no, validator: pass/fail). Done: B0-B2, T0-T3, R1-R4, D0, D1, D2, D3. Next: D4.


## 0.9.16 (this zip) - Quiet Pill D4
Done: D4 new-tab page restyle. Base 0.9.15 (build result of 0.9.14/0.9.15 not reported yet).
Changed: NewTabPage.kt html() rewritten (big search pill, no Go button, engine name in placeholder; bookmark tiles 56px with favicon = scheme://authority/favicon.ico over a letter, removed by onerror/onload<16px; 8 shown + "Show all" button (max 24); Recent = 4 with small favicon; palette = app colours dark, light via prefers-color-scheme); URL/isNewTab/response untouched. build.gradle.kts 0.9.16 / code 36.
Checks done: node --check on the page script, CSS and Kotlin brackets balanced. No browser available to render at 360/1280px, so layout is NOT visually checked.
NOT tested: favicon loading on a real device, light mode look, short screens, 340px grid.
Next: D5 media sheet: panel in activity_browser.xml (docs/res_seen has item_media.xml) + showPanel() in BrowserActivity.kt; chip floats above the pill, select mode = contextual bar (Copy, Grabber, Done); replace hardcoded accent 0xFF7C8CFF with q_accent.
Context line: Project: Jabra Hub browser. Mode: MODIFY. Base 0.9.16 (build: yes/no, validator: pass/fail). Done: B0-B2, T0-T3, R1-R4, D0-D4. Next: D5.


## 0.9.17 (this zip) - Quiet Pill D5
Done: D5 media sheet. Base 0.9.16 (no build result reported yet for 0.9.14-0.9.16).
Changed:
- activity_browser.xml: chip = q_btn_accent pill with q_ic_play, bottom|end margin 16dp (sits above pillWrap because pillWrap is outside the page FrameLayout); panel = q_sheet_bg, handle (q_handle), panelHead (id), title, panelClose now ImageView q_ic_close, new panelBar LinearLayout (gone by default), ScrollView+panelList as before.
- res/layout/item_media.xml: now IN the zip (the user must replace their copy); q_* tokens, mplay/mbtn with icons; copy of the old one stays in docs/res_seen.
- BrowserActivity.kt: updateChip (text "N found"), hidePanel (slide down, panelClosing flag), attachSheetDrag(panelHead) (drag > 96dp closes), showPanel rebuilt (slide-up when it was hidden; panelBar = filter chip + Select chip, or contextual select bar Done/count/All-None/Copy/Grabber; chipBtn/iconBtn helpers; rows tap = toggle in select mode; checkbox tinted q_accent). Behaviour change: in select mode Copy/Grabber use ticked items only (targets()). selectRefresh lambda field. No more 0xFF7C8CFF.
- q_handle.xml new; build.gradle.kts 0.9.17 / code 37.
Checks done: xmllint, resource names exist, brackets balanced (same imbalance pattern as the original file, caused by "http://" in strings in my counter). No JS touched. NOT compiled, NOT run on a device.
NOT tested: sheet animation/drag on device, select bar width at 360px, keyboard + sheet, item_media replacement in the user's project.
Not done: edge-to-edge/insets, TalkBack pass on old dialogs (filter, link menu, media actions), find-bar restyle, light mode, font-scale test.
Next: D6 polish: 360/410px layouts, edge-to-edge + insets (pill, sheet, tab grid), find bar colours (q_*), TalkBack labels on remaining dialogs, font scale 1.3, fixes from the user's device tests. Ask for build results, crash logs, screenshots.
Context line: Project: Jabra Hub browser. Mode: MODIFY. Base 0.9.17 (build: yes/no, validator: pass/fail). Done: B0-B2, T0-T3, R1-R4, D0-D5. Next: D6.

