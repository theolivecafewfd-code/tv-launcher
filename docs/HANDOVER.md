# Handover: where WIPTV stands

27 October 2026. Written at the move from the Claude chat to Claude Code. Read [CLAUDE.md](../CLAUDE.md) first for the rules.

## Where things are

Everything up to **PR #41 is merged**, and both apps are released from `main`. Wayne updates with the pill in either app.

**Done and on the Fire Stick**
- **Launcher:** TV Shows | Movies home, details, search, Surprise me, Waterford weather, app dock with quick settings, glass restyle (#1–#17, #32).
- **Player, Live TV:** sign-in and account, groups, guide with a now line, preview (OK previews, OK again goes full screen), Edit groups, favourites on Menu, full-screen channel lists, last channel, screen kept on while watching, calmer guide (#19–#28, #31, #35).
- **Player, Movies and Series:** rows, details, seasons and episodes, Continue watching, My List, resume, next episode (#29), now with the cinematic redesign (#37–#39).
- **Shared:** one update pill for both apps (#34); builds queue so releases don't collide (#33).
- **Tooling:** emulator screenshots against a pretend provider, run only on request (#36).
- **Docs:** README, plan and design records refreshed (#40).

**Just merged, not yet checked on the Fire Stick (#41)**
- The focused catalogue row always moves to the top, so Down/Up go one row per press.
- Loading rows show focusable placeholder posters, so focus can't skip past them.
- Episodes without a picture no longer show a big number.
- Full episode codes like S01E01 are removed from titles even when the provider's numbering is off by one.

## Wayne's latest feedback on Movies and Series (build 103)

- **Overall:** "looks much cleaner".
- **Fixed in #41:** leftover captions from the row above, rows jumping several at a time, and the number on fallback episode pictures.
- **Money Heist seasons 4–5:** they had no pictures or descriptions. Most likely the provider has no extra details for newer seasons (the app only shows a season if the provider sent episodes for it). If Wayne says the episode list itself is empty, look at how `Xtream.seriesDetails` groups episodes by season for that series.
- **Odd titles:** some episodes show other shows' titles ("Paul Whitehouse's Sketch…"). That's the provider's data, not the app.

## Still to check on the Fire Stick

From the design acceptance record (Stage 3) and #41:
- Smooth scrolling along poster rows; the backdrop fades in without stutter.
- Text readable over bright backdrops from the sofa.
- Details: focus starts on Play/Resume; More info, My List and Trailer work; Back returns to the same poster.
- Series: resume and next episode unchanged; the selected season stays marked in the episode list.
- #41: one row per press; a loading row takes focus and then hands it to its first title.

## What's next (in Wayne's rough order)

1. Any fixes from the checks above.
2. **Live Categories drawer** (design stage 2): a Categories button and drawer so the guide gets more room. See `docs/PLAYER_DESIGN.md`.
3. **Playback overlays and remaining screens** (design stage 4): live info bar and lists, film progress overlay, login, account, editors and updater in the glass style.
4. **Launcher Home screen on the Fire Stick:** Home on Fire needs `WRITE_SECURE_SETTINGS` granted over ADB from another device. Wayne will do it from his Chromebook. Amazon blocks apps on the stick from using the stick's own ADB.
5. **Launcher link:** a "Watch on IPTV" first choice in the launcher's details, then the player's Continue watching, favourites and chosen provider categories as launcher rows (keep about four rows).
6. **Catch-up** in the player, for channels with `tv_archive`.
7. **Later:** Wayne may buy a certified Google TV / Android TV stick, where the launcher can be the real Home screen (budget matters).

## Useful pointers

| Where | What |
| --- | --- |
| `player/.../Xtream.kt` | All provider calls (`player_api.php`) and parsing; stream URL builders |
| `player/.../LiveScreen.kt`, `Guide.kt`, `LivePlayer.kt`, `WatchScreen.kt` | Live TV browsing, guide rows, the shared ExoPlayer, full screen |
| `player/.../CatalogScreen.kt`, `Cinema.kt` | Movies/Series rows, hero backdrop, Continue watching cards |
| `player/.../VodDetail.kt`, `EpisodeTitles.kt`, `VodPlayer.kt`, `Resume.kt` | Details, episode title cleaning, film playback, resume and My List storage |
| `design/.../Wiptv.kt`, `Scrolling.kt` | Tokens, glass, focus, scrolling behaviour |
| `update/.../Updater.kt` | The shared update pill logic |
| `tools/screenshots/` | Pretend provider and emulator script (manual workflow) |

## First message to try in Claude Code

> Read CLAUDE.md and docs/HANDOVER.md, then tell me in a few lines where things stand and what you'd suggest next. Don't change anything yet.
