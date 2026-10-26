# Floating bubble API (Jabra API v1, capability `floater`)

The floating **J** bubble belongs to the Hub. Plugins never draw over other apps themselves; they declare a button and the Hub shows it.

## 1. Declare the button (manifest.json)

```json
"floater": {
  "icon": "🎬",
  "label": "Grab media",
  "color": "#E85D9A",
  "source": "clipboard",
  "allowEmpty": false
}
```

- `icon` (short emoji/text), `label` (max 14 chars), `color` (#RRGGBB) are cosmetic.
- `source`: `"none"` just opens the plugin; `"clipboard"` reads the clipboard first. (Android only allows the focused app to read the clipboard, so Jabra briefly opens in front.)
- `allowEmpty`: with `clipboard`, open the plugin even when nothing was copied (default false).

Add `"requiredCapabilities": ["floater"]` if the plugin cannot work without it.

## 2. User opt-in

Plugin menu in the Hub: **Show in floating bubble / Hide from floating bubble**. A plugin may also call `JabraAPI.floaterRequestEnable()`; the Hub then shows a confirmation dialog. Nothing is added silently.

## 3. Receive the payload

```js
function onFloater() {
  var raw = JabraAPI.floaterGet();        // null if nothing pending
  if (!raw) return;
  var p = JSON.parse(raw);                // { text, source, ts }
  handle(p.text);
  JabraAPI.floaterClear();
}
onFloater();                              // first open
window.__jabraFloater = onFloater;        // plugin already open and tapped again
```

Other calls: `JabraAPI.floaterEnabled()` -> boolean.
Feature-detect first: `JabraAPI.capabilities()` contains `"floater"`.

## Limits
Plugins do not run in the background; the bubble opens the plugin screen. Text is capped at 3 million characters.
