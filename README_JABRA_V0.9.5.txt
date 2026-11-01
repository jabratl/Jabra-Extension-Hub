Jabra Extension Hub v0.9.5 - Share a link to a plugin

NEW:
 - Android Share sheet entry "Jabra Grab": share a link from any browser or app (Share -> Jabra Grab) and it opens in a plugin
   that declares  "share": true  in its manifest.json. The text is handed over like a bubble tap, so a plugin reads it with
   JabraAPI.floaterGet() (payload source = "share") or gets window.__jabraFloater() when it is already open.
   One matching plugin opens directly; several show a chooser; none shows a short message.
 - New capability "shareIntake" in the API contract (API version unchanged, additive).
Changed: new ShareActivity.kt, Floater.kt (acceptsShare), JabraApiContract.kt, AndroidManifest.xml, build.gradle.kts (0.9.5 / code 23).
Unchanged: "Jabra Scan" (novel / chapter intake) keeps its own Share entry. The bubble, Downloads screen and downloader are untouched.
Not built or device-tested in this package.
