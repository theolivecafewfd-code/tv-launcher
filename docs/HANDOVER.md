# Handover: where WIPTV stands

27 October 2026, Claude Code session (updated after #64). Read [CLAUDE.md](../CLAUDE.md) first for the rules.

## Where things are

Everything up to **PR #64 is merged**, and both apps are released from `main` at **build 159**. Updates install with the pill in either app.

**Done and checked on the Fire Stick**
- **Launcher:** TV Shows | Movies home, details, search, Surprise me, Waterford weather, app dock with quick settings (#1–#17).
- **Launcher in the player's style (#47–#51):** the featured title's artwork large at the top right; greeting, weather and clock in a glass capsule; player-size posters with names; app tiles and quick settings in the poster frame; details page like the player's (Watch on first and bright, More info panel, More like this); search, Choose your apps and the hold-OK menu restyled. Rows settle one per press with their heading at the top.
- **Player, Live TV:** sign-in, groups, guide, preview, favourites on Menu, full screen (#19–#28, #31, #35). Softer guide focus, no programme boxes, FHD/HD/SD badges, quieter categories panel (#43); the now line runs behind text and one programme shows per slot (#44).
- **Player, Movies and Series:** rows, details, seasons and episodes, Continue watching, My List, resume, next episode, cinematic redesign (#29, #37–#39, #41, #46).
- **Update pill (#45):** reads a small build-number file in each release instead of GitHub's API.
- **Launcher link (#52–#54):** "▶ Watch on IPTV" first on details when the provider has the title (a film plays, a series opens its page; Back returns to the launcher); hold OK tries IPTV first; search has an "On your IPTV" row and a status line; posters carry a dark "IPTV" badge.
- **Scrolling and crash fixes (#55–#57):** one smooth scroll back to the top; a crash on quick Down presses fixed (`TrackAttached` / `ifAttached` guard on focus targets); the featured header no longer cut off.
- **Home rows (#58–#61):** the home screen is Continue watching (from the player), Top 10 this week, My List and Recommended for you; Comedy and Documentary were removed. Top 10 is new and popular (TV first aired in the last 18 months, films released in the last 12). Continue watching cards are portrait 144 × 216 dp and the one you rest on widens to 384 dp landscape. The focused row is bright; the others have softer text and a 14% dark wash.
- **#62:** charcoal-navy launcher background (#101620 with a static #1A2533 → #0B1018 glow) and a 2% grain tile; Top 10 numbers removed.
- **#63:** every home row widens the poster you rest on to its 16:9 backdrop (126 → 336 dp), like Continue watching.

**Merged, waiting for Wayne's check (build 159)**
- **#64:** on details pages (both apps), landing on the buttons or season tabs near the top scrolls the whole header back on screen. **One My List for both apps:** titles your provider has live in the player's My List and show in the launcher's My List row (they open in the player); the launcher's "+ My List" adds to or removes from the player's list; titles the provider doesn't have stay in the launcher's own list. Each title shows once.

## How the launcher link works

- The player keeps the provider's films and series in a small SQLite list on the TV (`CatalogIndex`: names, years, posters, ids, never stream addresses or the login), refreshed about daily. The whole-catalogue download is streamed (`Xtream.forEachVod`) and may take up to two minutes to start.
- `CatalogProvider` (authority `dev.wiptv.player.catalog`) answers `match`, `search`, `status`, `continue` (Continue watching), `mylist` (My List; insert and delete too) and `channels` (starred live channels). It's guarded by the signature permission `dev.wiptv.permission.CATALOG` for reads and writes, declared in both apps so install order doesn't matter.
- Titles match by TMDB id when the provider has one, else by a normalised name (no language tags, years, quality labels) and a year within one.
- The launcher opens a title with `open_kind`/`open_id` extras on the player's `MainActivity`, or resumes with `open_resume` (new, cleared task); when that title is closed, the player finishes and you're back in the launcher.
- A starred channel opens with `open_channel`: full screen in its own group, and Back goes to that group in Live TV (not back to the launcher).
- Home refreshes Continue watching and the player's My List each time you come back; the player re-reads My List when it returns to the front.
- If IPTV results don't show, the search screen's "IPTV: …" line says why.

## Shared design pieces

The `design` module now holds what both apps use: tokens (`WiptvColors` including `rowFocus`, `rowFocusMarker`, `panelQuiet`, `sheet`), `Glass` recipes (`Quiet`, `Sheet`), `CinematicBackdrop`, `ArtFrame` (poster/tile frame with the focus gap), `ActionButton`, `InfoSheet` (More info), `MetaLine` (details line that wraps between items), `DisplayTitles.withoutYear`, and the scrolling helpers. It depends on Coil and core-ktx, which both apps already had. Change these carefully: the player's look is the accepted baseline.

## What's next (in Wayne's order)

1. Any fixes from Wayne's check of build 159 (shared My List, details header).
2. **Your channels row (#65, waiting for Wayne's check):** only the live channels Wayne has starred in the player (Menu ≡), logo and name, no what's-on; OK plays it full screen in the player, Back goes to its group in Live TV.
3. **Catch-up** in the player, for channels with `tv_archive`.
4. **Playback overlays and remaining screens** (design stage 4): live info bar and lists, film progress overlay, login, account, editors and updater in the glass style.
5. **Live Categories drawer** (design stage 2): on hold while Wayne looks at another option.
6. **Launcher as Home on the Fire Stick:** Home on Fire needs `WRITE_SECURE_SETTINGS` granted over ADB from the Chromebook.
7. **Later:** a certified Google TV / Android TV stick, where the launcher can be the real Home screen.

## Things to know

- **Builds:** there's no Android SDK in cloud sessions; CI on the PR is the compile check. Each PR build uses a run number, so release numbers skip (a merge after one PR build lands two numbers higher).
- **Release check:** after a merge, `releases/download/player/wiptv-player.build.txt` and `releases/latest/download/tv-launcher.build.txt` show the published build numbers.
- **Provider data quirks:** some episodes carry other shows' titles, and some series have no details for newer seasons (Money Heist 4–5). Both come from the provider. The guide sometimes lists two programmes in the same slot; the app shows the first.

## Useful pointers

| Where | What |
| --- | --- |
| `app/.../HomeScreen.kt`, `Browse.kt`, `Scrolling.kt`, `Background.kt` | Launcher home: header, featured title, rows (expanding cards, section emphasis), apps, scrolling rules, background |
| `app/.../Iptv.kt`, `Library.kt` | Launcher side of the player link (match, search, Continue watching, My List) and the launcher's own My List |
| `player/.../CatalogIndex.kt`, `CatalogProvider.kt` | The player's title list and what it answers the launcher |
| `app/.../DetailScreen.kt`, `SearchScreen.kt`, `AppEditor.kt` | Launcher details, search, Choose your apps and the hold-OK menu |
| `app/.../Catalog.kt` | TMDB rows, details, search; Recommended loads separately |
| `player/.../Xtream.kt` | All provider calls (`player_api.php`) and parsing; stream URL builders |
| `player/.../LiveScreen.kt`, `Guide.kt`, `ChannelLabels.kt`, `WatchScreen.kt` | Live TV, guide rows, quality badges, full screen |
| `player/.../CatalogScreen.kt`, `VodDetail.kt`, `EpisodeTitles.kt`, `Resume.kt` | Movies and Series, details, episodes, resume and My List |
| `design/.../Wiptv.kt`, `Cinematic.kt`, `Scrolling.kt` | Shared tokens, glass, cinematic pieces, scrolling |
| `update/.../Updater.kt` | The shared update pill (build files first, API as fallback) |
| `tools/screenshots/` | Pretend provider and emulator script (manual workflow, only on request) |
