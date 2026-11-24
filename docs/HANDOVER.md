# Handover: where WIPTV stands

24 November 2026, Claude Code session (updated after #102). Read [CLAUDE.md](../CLAUDE.md) first for the rules.

## Where things are

Everything up to **PR #102 is merged**. The player is at **build 246** and the launcher at the build of the same merge (check the release files below for exact numbers). Updates install with the pill in either app.

**The direction has changed.** WIPTV Player is being reworked into a stand-alone IPTV app in the style of the "max" player Wayne liked, so it no longer leans on the launcher. The launcher still works and still links to the player, but it will be retired eventually. New work goes into the player first.

The rework was done in five parts, each its own PR, merged when Wayne said "merge it". No Fire Stick check of the new pages is written down yet, so treat anything he hasn't mentioned as worth a second look.

### The player now (version 0.3)

- **Look (#97):** version 0.3 colours in both apps: near-black neutral greys (`#0D0D0F` background), one lime accent (`#C5E86C`) for Play, progress and the "now" line, and white for focus. The artwork and video bring the colour.
- **One top menu (#97):** a centred bar, `WiptvTopMenu` in `design/.../Header.kt`: Library · Channels · Movies · TV Shows · Guide · Favourites · Settings, with the clock at the right and a lit Update button when a newer build is ready. Focus is a white fill. The old launcher links (Apps, search, the dice) are gone from the player.
- **Library (#97, #98)** is the start page: a TMDB trending banner (the player now gets the TMDB key at build time too), Continue watching and My List. **Edit Library (#98)** lets Wayne show, hide and reorder the rows: banner, Continue watching, My List films, My List shows, favourite channels, new on the provider, trending, and provider categories.
- **Channels (#99):** groups on the left and the group's channels as a four-across grid of logos, each with the programme on now, its progress and the time left. Sort by the provider's order or A–Z, with counts. Nothing plays on this page; OK watches full screen, Menu (≡) toggles the favourite.
- **Guide (#100):** part of the same screen: the group's channels as logo tiles down the left, programme blocks across, the accent "now" line, and catch-up day pills at the top (Today plus 7 days back). ⟲ programmes replay from the start.
- **Favourites** opens Live TV on the Favourites group. **Settings** is the account screen.
- **Movies and TV Shows (#101):** one category at a time as a six-across poster grid (`CatalogScreen.kt`). The header says "Movies: <category>" with the count and sort; **Filter and sort** opens a panel on the right with Sort by (provider order, newest, name, year, rating), the categories with their counts, and Edit categories. The choice is remembered per section. The hero, backdrop, Continue watching and My List rows left this page (they live in Library now). The focused poster's name turns bold and gains its year.
- **Player panel (#102):** pressing a key while a film or episode plays brings up a panel like the mock: a back circle, WIPTV and the clock at the top; at the bottom a quality badge (4K/1080p/720p/SD) and MOVIES, TV SHOWS or CATCH-UP, the title, details and runtime, a round Play/Pause with "Playing · ends at …", round buttons for Audio, Subtitles, Skip intro (episodes) and Sleep timer, and the progress bar with the time gone and left. Up goes to the buttons; Left/Right on the bar seek −10 s/+30 s; Down opens Audio & subtitles.
- **Sleep timer (#102):** Off, 15, 30, 45, 60 or 90 minutes, or the end of this film or episode. In its last minute a reminder shows and any key cancels it; when it runs out the position is saved, playback pauses and the player closes. It's for films and episodes only (Live TV hasn't got one yet).

### Earlier work still in place

- **Playback (#89, #90, #94, #96):** the Play Next card at the end of an episode; resuming moves on to the next episode when one is finished; Skip intro; Audio & subtitles; Continue watching asks Continue or Episodes, and Resume or Start over.
- **Live TV (#86, #76, #66):** the full guide grid (Right on a channel), catch-up ⟲ and its badge, favourites on Menu (≡), full-screen zapping.
- **Details pages:** seasons, episodes, More like this, More info, My List, resume.
- **Launcher (#58–#95):** TV Shows | Movies home with the focused card's hero at the top; rows: Continue watching, Your channels (with now and next, #81), My List, Recommended (#87), then a "More … in WIPTV Player" pill; posters widen to their backdrop (#91–#93) and Up or Down into a row lands on its first card (#95). Back asks "Leave WIPTV?" (#78). Apps tray, quick settings, details, search and Surprise me.

## How the launcher link works

Still in use while the launcher is around.

- The player keeps the provider's films and series in a small SQLite list on the TV (`CatalogIndex`: names, years, posters, ids, never stream addresses or the login), refreshed about daily.
- `CatalogProvider` (authority `dev.wiptv.player.catalog`) answers `match`, `search`, `status`, `continue` (with `resume_from`), `mylist` (insert and delete too) and `channels` (starred live channels). It's guarded by the signature permission `dev.wiptv.permission.CATALOG`, declared in both apps so install order doesn't matter. The launcher reads new columns by name, so an older player still works.
- The launcher opens the player with extras on its `MainActivity`: `open_kind`/`open_id` (a title), `open_resume` (Continue watching), `open_channel` (a starred channel), `section=LIVE` (or another section). Both apps open each other with no slide animation.

## Shared design pieces

The `design` module is the single source of the look for both apps. Change things there so both stay the same.

- Tokens: `WiptvColors` (version 0.3), `WiptvType`, `WiptvShapes`, `Glass` recipes, `wiptvBackground()`.
- `Header.kt`: `WiptvTopMenu` (the player's menu), `WiptvHeader` (the launcher's header) and the weather.
- `Hero.kt`, `Cinematic.kt`: the launcher's hero and details pieces (`HeroInfo`, `CinematicBackdrop`, `ArtFrame`, `ActionButton`, `InfoSheet`).
- `Posters.kt`: `PosterCard`, `SectionRow`, row emphasis, the widening card, `ContinueCard`.
- `Rows.kt` (`ListRow`) and `Playback.kt` (`PlaybackPanel`, `PlaybackProgress`, `KeyHints`; Live TV's info bar still uses these).
- `Wiptv.kt`: `WiptvButton`, `GlassPill`, `focusOutline`. `Scrolling.kt`: `PivotScrolling`, `RowScrolling`.

## What's next (Wayne picks)

1. **Search** in the player (it lost the launcher's search when the links went).
2. **A full Favourites page** (today it opens Live TV on Favourites).
3. **A sleep timer for Live TV.**
4. **Launcher as Home on the Fire Stick:** on hold until Wayne has the Chromebook set up (Home on Fire needs `WRITE_SECURE_SETTINGS` over ADB). Less important now the player is going stand-alone.
5. **Can wait:** per-account data (only if the provider changes); tests and lint (need a test library, so ask first); IMDb and Rotten Tomatoes scores (needs an OMDb key).

## Things to know

- **Builds:** there's no Android SDK in cloud sessions; CI on the PR is the compile check. Each PR build uses a run number, so release numbers skip (a merge after one PR build lands two numbers higher).
- **Release check:** after a merge, `releases/download/player/wiptv-player.build.txt` and `releases/latest/download/tv-launcher.build.txt` show the published build numbers.
- **Overlays are dialogs:** details, Resume or Start over, the film player, Edit categories and Filter and sort are `Dialog`s, so focus goes back to the poster underneath when they close.
- **Provider data quirks:** some episodes carry other shows' titles, and some series have no details for newer seasons. The guide sometimes lists two programmes in the same slot; the app shows the first.

## Useful pointers

| Where | What |
| --- | --- |
| `player/.../PlayerRoot.kt` | The player's pages (`Section`), the top menu and the launcher extras |
| `player/.../LibraryScreen.kt` | Library: banner, rows, Edit Library |
| `player/.../LiveScreen.kt`, `Guide.kt`, `GuideGrid.kt`, `Schedule.kt`, `WatchScreen.kt` | Channels grid, Live TV, the guide and catch-up, full screen |
| `player/.../CatalogScreen.kt` | Movies and TV Shows grid, Filter and sort, Edit categories |
| `player/.../VodDetail.kt`, `ResumeChoice.kt`, `Resume.kt` | Details, Continue or Episodes / Resume or Start over, saved positions and My List |
| `player/.../VodPlayer.kt`, `Tracks.kt` | Film and episode playback, the player panel, sleep timer, Skip intro, Play Next; audio and subtitles |
| `player/.../Xtream.kt` | All provider calls (`player_api.php`) and parsing; stream URL builders |
| `player/.../Tmdb.kt`, `SessionCache.kt` | TMDB trending for Library; data kept between page switches |
| `player/.../CatalogIndex.kt`, `CatalogProvider.kt` | The player's title list and what it answers the launcher |
| `app/.../HomeScreen.kt`, `Browse.kt`, `Catalog.kt` | Launcher home, rows and TMDB lookups |
| `app/.../AppsTray.kt`, `DetailScreen.kt`, `SearchScreen.kt`, `Iptv.kt` | Launcher tray, details, search and the player link |
| `design/...` | Everything shared (see above) |
| `update/.../Updater.kt` | The shared update pill |
| `tools/screenshots/` | Pretend provider and emulator script (manual workflow, only on request) |
