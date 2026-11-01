Jabra Extension Hub v0.9.23 - Quiet Pill step H3: plugin screen

CHANGED:
 - activity_plugin.xml: q_* colours (page, toolbar and WebView background all q_bg, no hardcoded #141629), thin rounded loading bar (q_progress, same as the browser; keeps its 3 dp so the page does not jump), new error panel.
 - Toolbar: q_ic_back (was ic_back) with a "Back" description; new Reload button (q_ic_refresh).
 - Loading: bar follows real page progress (onProgressChanged).
 - Error state: title, a reason (page missing / HTTP code / load error / WebView crashed), "Try again" and "Back to Hub". Missing start page (404) says to reinstall from the Plugin store.
 - WebView crash: "Try again" and Reload rebuild the screen (the old WebView cannot be reused). A new Scan/bubble intent after a crash does the same.
 - Ids kept: toolbar progress web error. New: error_msg error_retry error_back. progress is now a ProgressBar and error is a LinearLayout (was LinearProgressIndicator and TextView); only PluginActivity.kt uses them.
 - Version 0.9.23 (code 43). Validator not changed.

Changed files: PluginActivity.kt, activity_plugin.xml, build.gradle.kts (2 lines), README.
Unchanged on purpose: the JabraAPI bridge, JS dialogs, file chooser, storage shim.
Now unused (delete after grep -rn shows no hits): drawable ic_back.

Not built, not run, not rendered.
Please check: build passes; open a plugin (bar fills, then disappears, page does not jump); Reload button; delete a plugin's index.html or set a wrong "entry" to see the 404 panel and Try again; airplane mode with an online plugin; plugin opened from bubble/Scan while already open; TalkBack reads the error text; font size Large; 360 dp and tablet width.
