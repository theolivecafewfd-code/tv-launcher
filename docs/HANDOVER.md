# Handover: where WIPTV stands

27 October 2026, end of the first Claude Code session. Read [CLAUDE.md](../CLAUDE.md) first for the rules.

## Where things are

Everything up to **PR #51 is merged**, and both apps are released from `main`. The latest build Wayne has checked is **125**. Updates install with the pill in either app.

**Done and checked on the Fire Stick**
- **Launcher:** TV Shows | Movies home, details, search, Surprise me, Waterford weather, app dock with quick settings (#1–#17).
- **Launcher in the player's style (#47–#49):** the featured title's artwork large at the top right; greeting, weather and clock in a glass capsule; player-size posters with names; app tiles and quick settings in the poster frame; details page like the player's (Watch on first and bright, More info panel, More like this); search, Choose your apps and the hold-OK menu restyled. Rows settle one per press with their heading at the top; going back up shows the whole header.
- **Player, Live TV:** sign-in, groups, guide, preview, favourites on Menu, full screen (#19–#28, #31, #35). Softer guide focus (soft fill and slim left bar, no outline), no programme boxes, FHD/HD/SD badges, quieter categories panel (#43); the now line runs behind text and one programme shows per slot (#44).
- **Player, Movies and Series:** rows, details, seasons and episodes, Continue watching, My List, resume, next episode, cinematic redesign (#29, #37–#39, #41). Continue watching shows the title once in the hero over its artwork; softer episode focus; about five episodes once the header scrolls away; a darker, content-sized More info panel; trailing years removed when the year is already shown (#46).
- **Update pill (#45):** reads a small build-number file in each release instead of GitHub's API, so "GitHub is limiting checks" no longer appears. Confirmed working.

**Also checked (build 125)**
- **#50:** darker text area over artwork on the home screen and details, details lines that wrap between whole items, quieter forecast line, "Build N" label removed, focused posters bring their names on screen, the focused details button no longer clipped.

**Merged, waiting for Wayne's look**
- **#51:** Recommended for you shows placeholder posters while it loads (so Down isn't stuck at Documentary); FHD/HD/SD badges also in the full-screen channel list and info bar; these notes.

## Shared design pieces

The `design` module now holds what both apps use: tokens (`WiptvColors` including `rowFocus`, `rowFocusMarker`, `panelQuiet`, `sheet`), `Glass` recipes (`Quiet`, `Sheet`), `CinematicBackdrop`, `ArtFrame` (poster/tile frame with the focus gap), `ActionButton`, `InfoSheet` (More info), `MetaLine` (details line that wraps between items), `DisplayTitles.withoutYear`, and the scrolling helpers. It depends on Coil and core-ktx, which both apps already had. Change these carefully: the player's look is the accepted baseline.

## What's next (in Wayne's rough order)

1. Any fixes from Wayne's check of build 125 and the build from #51.
2. **Launcher link:** a "Watch on IPTV" first choice in the launcher's details, then the player's Continue watching, favourites and chosen provider categories as launcher rows (keep about four rows).
3. **Live Categories drawer** (design stage 2): on hold while Wayne looks at another option.
4. **Catch-up** in the player, for channels with `tv_archive`.
5. **Playback overlays and remaining screens** (design stage 4): live info bar and lists, film progress overlay, login, account, editors and updater in the glass style.
6. **Launcher as Home on the Fire Stick:** Home on Fire needs `WRITE_SECURE_SETTINGS` granted over ADB from the Chromebook.
7. **Later:** a certified Google TV / Android TV stick, where the launcher can be the real Home screen.

## Things to know

- **Builds:** there's no Android SDK in cloud sessions; CI on the PR is the compile check. Each PR build uses a run number, so release numbers skip (a merge after one PR build lands two numbers higher).
- **Release check:** after a merge, `releases/download/player/wiptv-player.build.txt` and `releases/latest/download/tv-launcher.build.txt` show the published build numbers.
- **Provider data quirks:** some episodes carry other shows' titles, and some series have no details for newer seasons (Money Heist 4–5). Both come from the provider. The guide sometimes lists two programmes in the same slot; the app shows the first.

## Useful pointers

| Where | What |
| --- | --- |
| `app/.../HomeScreen.kt`, `Browse.kt`, `Scrolling.kt` | Launcher home: header, featured title, rows, apps, scrolling rules |
| `app/.../DetailScreen.kt`, `SearchScreen.kt`, `AppEditor.kt` | Launcher details, search, Choose your apps and the hold-OK menu |
| `app/.../Catalog.kt` | TMDB rows, details, search; Recommended loads separately |
| `player/.../Xtream.kt` | All provider calls (`player_api.php`) and parsing; stream URL builders |
| `player/.../LiveScreen.kt`, `Guide.kt`, `ChannelLabels.kt`, `WatchScreen.kt` | Live TV, guide rows, quality badges, full screen |
| `player/.../CatalogScreen.kt`, `VodDetail.kt`, `EpisodeTitles.kt`, `Resume.kt` | Movies and Series, details, episodes, resume and My List |
| `design/.../Wiptv.kt`, `Cinematic.kt`, `Scrolling.kt` | Shared tokens, glass, cinematic pieces, scrolling |
| `update/.../Updater.kt` | The shared update pill (build files first, API as fallback) |
| `tools/screenshots/` | Pretend provider and emulator script (manual workflow, only on request) |
