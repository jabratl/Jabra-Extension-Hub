# Share-sheet API (Jabra API v1, capability `shareIntake`, Hub 0.9.5+)

Any app can share a link to the Hub: **Share -> Jabra Grab**. The Hub hands the text to a plugin that opted in. The plugin never needs extra permissions and nothing downloads by itself.

## 1. Opt in (manifest.json)
```json
"share": true
```
No `floater` block is required. Older Hubs ignore the key. With no opted-in plugin, Jabra Grab shows a short message; with one it opens that plugin; with several the user picks.

## 2. Receive the payload (same calls as the floating bubble)
```js
function onFloater() {
  var raw = JabraAPI.floaterGet();      // null if nothing pending
  if (!raw) return;
  var p = JSON.parse(raw);              // { text, source: "share", ts }
  JabraAPI.floaterClear();
  handle(p.text);                       // whole shared text: often "Page title\nhttps://..."; extract the links yourself
}
onFloater();                            // first open (cold start)
window.__jabraFloater = onFloater;      // plugin already open (warm)
```
`p.source` is `"share"` for the Share sheet, `"clipboard"` / `"none"` for the bubble.

## Rules for plugins
- Treat shared text as untrusted input from any app: list it and let the user confirm. Do not start downloads or other actions automatically.
- Text is capped at 3 million characters.
- Feature-detect with `JabraAPI.capabilities()` containing `"shareIntake"`.

## Hub side
`ShareActivity` (label "Jabra Grab", `ACTION_SEND` `text/plain`) -> `Floater.save(ctx, pluginId, text, "share")` -> `PluginActivity` (singleTop; `onNewIntent` calls `window.__jabraFloater()`). "Jabra Scan" (novel intake) keeps its own entry.
