Jabra Extension Hub v0.9.30 - Browser "Send to Media Grabber" sends the page address

CHANGED (BrowserActivity.kt, sendToGrabber only):
 - Besides the media links, the page the media was found on is added as a normal link. Media Grabber already uses a page link in its list as the referer, so sites that answered 403 to a bare media link now get the right referer. The page title is still the first line.
 - Not done on purpose: running the plugin inside the browser page. The plugin keeps its own WebView, so the JabraAPI bridge (storage, download, clipboard) is never given to a website. Media Grabber still opens as its own screen over the browser; Back returns to the same page.
 - Version 0.9.30 (code 50).

PLUGIN (Media Grabber 1.4.1, separate zip, import it from the Hub as usual):
 - Share/browser hand-over: a line without a link (the page title) fills the "Name" field if it is empty. Nothing else changed.

Not built, not run, not rendered.
Please check: on a site that needs a referer, catch a video, Grabber (or tap a media row > Send to Media Grabber), see the page link in the list, tap Download; the Name field shows the page title; a normal Android Share still works.
