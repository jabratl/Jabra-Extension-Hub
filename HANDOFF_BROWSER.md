# Handoff: Jabra Hub browser
Type: MODIFY. Project: Jabra Hub browser. Base: jabra-hub v0.9.7-t1 (zip).
Done: B0, B1 (0.9.6), B2 (0.9.6b), T0 (0.9.7-t0, build confirmed by user), T1 + T2 browser side (0.9.7-t1).

T1/T2 changes:
- BrowserActivity.kt: fields maxTabs=8, webHost/webIdx/webId/webLp (where the XML WebView sits; remembered in onCreate), tabBtn.
  newTab(url, show): Boolean (limit toast), switchTo(t) (pause + detach old, attach new at webIdx with webLp, same id, refresh urlBar/chip/panel), closeTab(t) (last tab resets), showTabs() (rows built in code), addTabButton() (inserts a TextView before R.id.menu only if its parent is a horizontal LinearLayout; colour copied from R.id.close if it is a TextView, else grey), showLinkMenu(), onNewIntent().
  setupTab: long-press listener (hitTestResult + requestFocusNodeHref), new-window tap uses newTab (falls back to same tab at the limit).
  goBack: closes the tab when it cannot go back and more tabs exist. Clear browsing data: clearHistory on all tabs. Menu: ids 20 Tabs, 21 New tab (orders -2, -1).
- AndroidManifest.xml: BrowserActivity launchMode singleTop.
Checks done: brackets balanced, manifest edit verified. NOT compiled, NOT run on a device.

USER MUST test (see README_JABRA_V0.9.7-T1.txt). Likely trouble spots if something is off:
1. Tab button not visible / wrong colour: top bar is not a horizontal LinearLayout or bar colours differ. Menu "Tabs (N)" still works. Send activity_browser.xml to fix properly.
2. Sound or video keeps playing in a background tab: WebView.onPause may not stop media on this device; fix = also call pauseTimers / evaluate pause JS (planned in T3).
3. First WebView comes from XML, new ones are plain WebView(this): look/overscroll can differ slightly.
4. "Open in browser" from a plugin/Hub: onNewIntent only fires when BrowserActivity is the top activity (singleTop). If the browser is behind the plugin, a second browser instance opens. Fix needs PluginActivity.kt (where openBrowser starts the browser): add Intent.FLAG_ACTIVITY_REORDER_TO_FRONT (test on device that onNewIntent fires) or finish the plugin first. Please send PluginActivity.kt.
Next: T3 (restore tabs after restart: urls, max 8, last active; background tab pause + cap; media chip per tab is already per tab) then R1 (new-tab page with bookmarks grid, search engine choice, address suggestions), R2-R4, F.
Context line: Project: Jabra Hub browser. Mode: MODIFY. Base 0.9.7-t1 (build confirmed: yes/no). Done: B0-B2, T0, T1, T2 (browser side). Next: T2 plugin side (send PluginActivity.kt) + T3.
