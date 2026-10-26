Jabra Extension Hub v0.9.4 - Faster, smarter downloads + Downloads screen

NEW:
 - Downloads screen (browser menu > Downloads, or JabraAPI.openDownloads()): every download with live progress and speed,
   Cancel, Play (built-in player) and Open with... Shows this session's downloads (the list is not kept after the app is closed).
 - HLS downloads are much faster: 4 parts in parallel, each retried up to 3 times.
 - Encrypted HLS (AES-128) now downloads and is decrypted. SAMPLE-AES / DRM are still refused with a clear message.
 - Quality picker for HLS: tap Download on a stream, choose Best / 1080p / 720p ... (needs a master playlist).
 - Clip download for HLS: type From / To (90, 1:30, 1:02:30) to save only a part. The cut is at part boundaries (a few seconds accurate).
   File name gets "clip 90s-130s".
 - Web pages can now upload files (the file chooser was missing in the Hub browser).
 - Direct downloads show percent and speed and can be cancelled.
 - Player can also play finished downloads (content:// files).
 - Plugin API (capability "downloadManager"): JabraAPI.openDownloads().
Changed: Downloader.kt (rewritten), new DownloadsActivity.kt, BrowserActivity.kt, PlayerActivity.kt, PluginActivity.kt,
 JabraApiContract.kt, AndroidManifest.xml, build.gradle.kts (0.9.4 / code 22). Includes v0.9.3 (ad blocker, player, history, bookmarks).
Limits: HLS with separate audio renditions saves the video track only; byte-range HLS, DASH and live streams are not supported;
 no pause/resume yet (Cancel only); output stays .ts (no mp4 remux).
Not built or device-tested in this package.
