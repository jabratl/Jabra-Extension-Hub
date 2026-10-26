Jabra Extension Hub v0.9.1 - All-purpose floating bubble

NEW:
 - The floating J bubble is now a shared launcher for any plugin, not only Novel Assistant.
 - A plugin declares a "floater" block in manifest.json (icon, label, color, source). The user switches it on from the plugin's menu: "Show in floating bubble".
 - One enabled plugin: tap J runs it. Several: tap J opens a tray with one button each. Drag J to move it.
 - Plugin API (capability "floater"): JabraAPI.floaterGet(), floaterClear(), floaterEnabled(), floaterRequestEnable(); optional page callback window.__jabraFloater().
 - Novel Assistant keeps working exactly as before (its clipboard scan button is built in).
 - Includes everything from v0.9.0 (in-app app/plugin updates).
 - See docs/FLOATER_API.md and sample-plugin/floater-demo.zip.
Not built or device-tested in this package.
