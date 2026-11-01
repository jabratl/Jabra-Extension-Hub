Jabra Extension Hub v0.9.28 - Quiet Pill step H7a: Light theme

CHANGED:
 - The app now follows the system Light / Dark setting (no option in Settings). Theme.Hub is Theme.Material3.DayNight.NoActionBar.
 - res/values/q_tokens.xml now holds the LIGHT colours. res/values-night/q_tokens.xml holds the DARK colours (same values as before). Dimens only in values/.
 - Light accent for fills/icons/strokes is #E5612F (3.2:1 on the light background), accent text is #B8431A (5:1). Dark accent is unchanged (#FF8A5B).
 - res/values/themes.xml replaced: DayNight parent, all colours from q_* tokens (no more @color/bg, surface, accent). Dialogs and sheets now use the token surfaces in dark too (before: Material default grey).
 - Status bar and navigation bar icons switch dark/light with the theme (@bool/q_light_bars).
 - Version 0.9.28 (code 48).

NOT changed: Kotlin code, layouts, drawables (they already used only q_* tokens). Old colors.xml / colors_browser.xml are still in your project but nothing uses them.
Replace: app/src/main/res/values/q_tokens.xml, values-night/q_tokens.xml, values/themes.xml, build.gradle.kts, README. Keep your own colors.xml.

Not built, not run, not rendered.
Please check in LIGHT and DARK (switch the phone setting): main list + bottom nav, settings, browser pill + menu sheet, tabs grid, new-tab page, downloads, find bar, dialogs, status/nav bar icons readable, accent buttons, progress bar.
Risk: plugin pages (PluginActivity WebView) use q_bg as background, so a plugin that assumes a dark page may look wrong in Light.
