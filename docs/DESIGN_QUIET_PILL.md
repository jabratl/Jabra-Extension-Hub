# Quiet Pill (browser UI plan, starts 0.9.13)
Decision: address bar at the BOTTOM.
1. Bottom floating pill (lock+host, tab-count chip, 3-dot). No back/close buttons: back = gesture, close = menu. Tap pill -> expands to edit, suggestions list ABOVE the pill. Scroll down: pill shrinks to host only; scroll up: returns.
2. Progress: thin rounded bar on the bottom edge of the pill (q_progress).
3. Menu = bottom sheet: top row of round quick buttons (bookmark, share, find, dark, ad block, desktop), below a compact list (Bookmarks, History, Downloads, Text size, Site settings...). No nested submenus.
4. Tab switcher: full-screen grid, thumbnail card + title + X, swipe to close, big "+ New tab" at the bottom. Cap 8 tabs (thumbnail memory).
5. New-tab page: one big search pill, bookmark tiles with favicons, little clutter, dark + light.
6. Media panel: bottom sheet, chip pill floats above the pill; select mode shows a contextual bar (Copy, Grabber, Done).
Rules: one accent #7C8CFF, rest neutral. Corners pill 28dp, card 16dp, 8dp grid. Replace text glyphs with 24dp vector icons (q_ic_*). Spring-like motion + light haptic (Q.pop, Q.tick). 48dp touch targets, TalkBack labels, font scale OK. Edge-to-edge, gesture-nav friendly.
Steps: D0 tokens/icons (DONE 0.9.13) | D1 bottom pill + D2 menu sheet (DONE 0.9.14, accent is the app orange #FF8A5B, not #7C8CFF) | D3 tab grid (DONE 0.9.15) | D4 NewTabPage.kt | D5 media sheet (item_media.xml) | D6 polish 360/410px.
Token names: q_bg q_surface q_surface_hi q_outline q_text q_text_sub q_icon q_accent q_accent_text q_accent_container q_on_accent q_danger q_scrim; dimens q_pill_radius q_card_radius q_chip_radius q_grid q_touch q_icon_size q_pill_height q_stroke. Drawables: q_pill q_card q_chip q_chip_accent q_sheet_bg q_circle_btn q_circle_btn_on q_progress, icons q_ic_* (back close more add search share star star_outline bookmark lock copy check download history tune tabs dark shield desktop text_size refresh home play).
