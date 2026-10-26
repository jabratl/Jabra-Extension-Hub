Jabra Extension Hub v0.9.2 - Built-in browser and media downloader

NEW:
 - Menu -> "Browser - catch media": open any website. Jabra notices video, HLS (.m3u8), audio and subtitle files the page loads and lists them under a "found" button.
 - Download saves to Downloads/JabraHub. Direct files and unencrypted HLS streams are supported (HLS is joined into one .ts, or .mp4 when the stream uses fMP4). Encrypted streams are refused with a message.
 - Downloads keep running in the background (a small "Jabra is downloading" notification).
 - Plugin API (capabilities "browser", "mediaDownload"): JabraAPI.openBrowser(url) and JabraAPI.mediaDownload(url, referer, title). A Media Grabber plugin can now be just a user interface on top of this engine.
 - Includes v0.9.0 (in-app updates) and v0.9.1 (all-purpose floating bubble).
Limits: no fullscreen video button inside the browser yet; DRM or encrypted streams cannot be saved.
Not built or device-tested in this package.
