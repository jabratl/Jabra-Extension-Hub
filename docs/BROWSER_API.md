# Browser and media download API (capabilities `browser`, `mediaDownload`)

- `JabraAPI.openBrowser(url)` -> boolean. Opens the Hub browser at an http/https URL. The user sees detected media and chooses what to download.
- `JabraAPI.mediaDownload(url, referer, title)` -> boolean. Starts a background download into Downloads/JabraHub. `referer` is the page that played the media (many sites require it); `title` becomes the file name. Returns false if the URL is not http(s).

Direct files (mp4, webm, mp3, m4a, vtt, srt ...) and unencrypted HLS playlists are supported. Encrypted/DRM streams are not.
Feature-detect with `JabraAPI.capabilities()` first.
