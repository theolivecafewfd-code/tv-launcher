# Handover: where WIPTV stands

28 October 2026, Claude Code session (updated after #79). Read [CLAUDE.md](../CLAUDE.md) first for the rules.

## Where things are

Everything up to **PR #79 is merged**, and both apps are released from `main` at **build 196**. Updates install with the pill in either app. Wayne has checked everything up to build 196 on the Fire Stick ("both looking the same now").

The two apps now look and behave as one: the same header, the same glass, the same posters, rows, cards and top-of-screen hero, and they open each other without a slide animation.

**Done and checked on the Fire Stick**
- **Launcher home (#58–#63, #79):** TV Shows | Movies. The header and the focused card's artwork, title, details and synopsis stay at the top (the shared `HeroInfo` over `CinematicBackdrop`), as in the player; the rows scroll underneath, one row per press, the focused row at the top (shared `PivotScrolling(alwaysAlign = true)`). Rows: Continue watching (from the player), Your channels, My List (shows and films together, the same on both tabs), Recommended for you. Top 10 was removed at Wayne's request (#87). The old featured Top 10 block is gone (#79). The poster you rest on widens to its backdrop.
- **Continue watching on the launcher (#79):** the hero shows the episode, "Resume from 1:24:03" (the player's provider sends `resume_from`) and the time left; the synopsis, and the picture if the player has none, come from a TMDB search by the cleaned name once you rest on a card, cached for the session.
- **Your channels (#65):** the live channels starred in the player (Menu ≡), logo and name; OK plays full screen, Back goes to that channel's group in Live TV. The hero shows the name and "Live TV · OK to watch" (no programme info yet).
- **Back on the launcher home (#78):** asks "Leave WIPTV?" (Stay focused; Leave finishes the activity). Back in the tray, search, details and menus still closes those first.
- **Charcoal glass and the Apps tray (#67):** version 0.2 tokens in both apps; Apps in the top navigation opens a tray over the bottom of the screen.
- **One header for both apps (#68, #69):** greeting, a status pill (weather icon and temperature, clock, build) and one navigation capsule: TV Shows · Movies · Live TV · Apps · search · dice. In the player, Apps and search hand back to the launcher with them open; Back returns to where you came from (#70).
- **Shared pieces (#73–#75):** posters, rows, row emphasis and the expanding card (`Posters.kt`); one Continue watching card; one list row (`Rows.kt`) for the guide, episodes, schedule, full-screen lists and menus; the playback overlays (`Playback.kt`).
- **Catch-up (#66, #76):** ⟲ programmes replay from the start. Channels that keep recordings carry a small catch-up badge in the lists.
- **Full guide (#86, waiting for Wayne's check):** Right on a Live TV channel opens a full-screen grid of that category's channels, 24 hours back to 24 ahead, under the preview (the categories panel steps aside). The panel by the preview describes the highlighted programme; OK on now watches it in the preview, on a ⟲ programme plays catch-up. Back returns to that channel in Live TV. It replaced the single-channel schedule panel.
- **Since build 196:** what's on now and next for Your channels on the launcher (#81); two rounds of code-review fixes (#82–#85).
- **Player details (#71, #72, #77):** the hero fills in for My List and Continue watching titles; More like this on details pages, as on the launcher; widened film posters show their backdrop once the details arrive; sign-in and account in the glass style.
- **Earlier:** launcher details, search, Surprise me, weather, apps and quick settings (#1–#17); the player's Live TV, Movies and Series (#19–#46); the update pill (#45); the launcher link (#52–#54); one My List for both apps (#64).

## How the launcher link works

- The player keeps the provider's films and series in a small SQLite list on the TV (`CatalogIndex`: names, years, posters, ids, never stream addresses or the login), refreshed about daily.
- `CatalogProvider` (authority `dev.wiptv.player.catalog`) answers `match`, `search`, `status`, `continue` (Continue watching, with `resume_from` since #79), `mylist` (insert and delete too) and `channels` (starred live channels). It's guarded by the signature permission `dev.wiptv.permission.CATALOG`, declared in both apps so install order doesn't matter. The launcher reads new columns by name, so an older player still works.
- Titles match by TMDB id when the provider has one, else by a normalised name and a year within one.
- The launcher opens the player with extras on its `MainActivity`: `open_kind`/`open_id` (a title), `open_resume` (Continue watching), `open_channel` (a starred channel), `section=LIVE`. The player hands back with `wiptv_open=apps|search` and `wiptv_return=<section>`. Both use `ActivityOptions.makeCustomAnimation(context, 0, 0)`, so there's no slide.

## Shared design pieces

The `design` module is the single source of the look for both apps. Change things there so both stay the same.

- Tokens: `WiptvColors` (version 0.2 charcoal), `WiptvType`, `WiptvShapes`, `Glass` recipes (`Nav`, `Drawer`, `OverVideo`, `Quiet`, `Sheet`, `Tray`), `wiptvBackground()`.
- `Header.kt`: `WiptvHeader` and the weather (cached for 30 minutes).
- `Hero.kt`: `Hero` and `HeroInfo`, the focused card's title, details and synopsis at a fixed height (#79).
- `Posters.kt`: `PosterCard`, `PosterPlaceholder`, `SectionRow`, row emphasis, `rememberWideCard`, `ContinueCard`.
- `Rows.kt` (`ListRow`) and `Playback.kt` (`PlaybackPanel`, `PlaybackProgress`, `KeyHints`).
- `Cinematic.kt`: `CinematicBackdrop`, `ArtFrame`, `ActionButton`, `InfoSheet`, `MetaLine`, `DisplayTitles`.
- `Wiptv.kt`: `WiptvButton`, `GlassPill`, `focusOutline`.
- `Scrolling.kt`: `PivotScrolling`, `RowScrolling`.

## What's next (ideas, in Wayne's rough order)

1. **Launcher as Home on the Fire Stick:** on hold until Wayne has the Chromebook set up. Home on Fire needs `WRITE_SECURE_SETTINGS` granted over ADB from it.
2. **Shared edit screens** (Choose your apps and Edit groups): low priority.
3. **Code-review items that can wait:** cache the app list and artwork, keep player tabs' data between switches, lighter search, now/next refreshing while focused, per-account data (only if the provider changes), tests and lint (need a test library, so ask first).
4. **Later:** a certified Google TV / Android TV stick, where the launcher can be the real Home screen.

The Live Categories drawer (design stage 2) is dropped: Wayne is happy with Live TV as it is.

## Things to know

- **Builds:** there's no Android SDK in cloud sessions; CI on the PR is the compile check. Each PR build uses a run number, so release numbers skip (a merge after one PR build lands two numbers higher).
- **Release check:** after a merge, `releases/download/player/wiptv-player.build.txt` and `releases/latest/download/tv-launcher.build.txt` show the published build numbers.
- **Provider data quirks:** some episodes carry other shows' titles, and some series have no details for newer seasons. Both come from the provider. The guide sometimes lists two programmes in the same slot; the app shows the first.

## Useful pointers

| Where | What |
| --- | --- |
| `app/.../HomeScreen.kt`, `Browse.kt`, `Scrolling.kt` | Launcher home: header, hero, rows, exit confirm; focus guards (`TrackAttached` / `ifAttached`) |
| `app/.../AppsTray.kt`, `AppEditor.kt`, `QuickSettings.kt` | The Apps tray, Choose your apps, quick settings |
| `app/.../Iptv.kt`, `Library.kt` | Launcher side of the player link, and the launcher's own My List |
| `app/.../DetailScreen.kt`, `SearchScreen.kt`, `Catalog.kt` | Launcher details, search, TMDB rows and lookups |
| `player/.../CatalogIndex.kt`, `CatalogProvider.kt` | The player's title list and what it answers the launcher |
| `player/.../Xtream.kt` | All provider calls (`player_api.php`) and parsing; stream URL builders |
| `player/.../LiveScreen.kt`, `Guide.kt`, `Schedule.kt`, `WatchScreen.kt` | Live TV, guide rows, schedule and catch-up, full screen |
| `player/.../CatalogScreen.kt`, `VodDetail.kt`, `VodPlayer.kt`, `Resume.kt` | Movies and Series, details, playback, resume and My List |
| `design/...` | Everything shared (see above) |
| `update/.../Updater.kt` | The shared update pill |
| `tools/screenshots/` | Pretend provider and emulator script (manual workflow, only on request) |
