Jabra Extension Hub v0.9.13 - Quiet Pill design, step D0 (design tokens and icons only)

NEW: nothing changes on screen yet. This version only adds the building blocks for the new browser look:
 - Colours, sizes, dark and light values (res/values/q_tokens.xml, res/values-night/q_tokens.xml)
 - Shape styles (res/values/q_styles.xml)
 - Rounded shapes and a thin rounded progress bar (q_pill, q_card, q_chip, q_chip_accent, q_sheet_bg, q_circle_btn, q_circle_btn_on, q_progress)
 - 23 vector icons, 24dp (q_ic_*)
 - Q.kt: small helpers (dp, isDark, icon, tick haptic, pop animation)
All names start with q_, so nothing existing (br_*) is replaced. Plan: docs/DESIGN_QUIET_PILL.md

Changed: build.gradle.kts (0.9.13 / code 33). New files only otherwise; no Kotlin file you already have was edited.
HOW TO APPLY: copy app/src/main/res/* and Q.kt into your project (they are new files), and replace build.gradle.kts values.
Not built or device-tested in this package.
Please check: the app still builds and looks exactly as before. If the build fails, send the first error lines.
