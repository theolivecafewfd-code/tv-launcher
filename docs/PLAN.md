# WIPTV — Project Plan (Fire Stick)

Nov 24, 2026 · Wayne

> A copy of the WIPTV project plan. The live version is kept in Claude Docs; this file is refreshed from it now and then.

## Status

**WIPTV** (the launcher) and **WIPTV Player** (the IPTV app) are both live on the Fire TV Stick HD; the player is at **build 246** (PR #102). Since build 235 the player is being reworked into a stand-alone IPTV app in the style of the "max" player Wayne liked: version 0.3 colours (near-black greys, a lime accent, white focus), one top menu (Library · Channels · Movies · TV Shows · Guide · Favourites · Settings), a Library start page, a Channels logo grid, a Guide with catch-up days, poster grids with Filter and sort, and a new player panel with a sleep timer. The launcher still works and links to the player, but it will be retired eventually. One update pill in either app updates both. Work is done in Claude Code; see [the handover](HANDOVER.md).

| Area | Status | Notes |
| --- | --- | --- |
| Build pipeline and updates | Done | Every pull request builds both apps; merges publish releases one at a time; the pill reads a build-number file in each release (#33, #34, #45) |
| Player: version 0.3 look and top menu | Done | Near-black greys, lime accent, white focus, in both apps; one centred menu with the clock and Update (#97) |
| Player: Library | Done | TMDB trending banner, Continue watching, My List (#97); Edit Library to show, hide and reorder rows (#98) |
| Player: Channels | Done | Groups and a four-across logo grid with now, progress and time left; sort and counts (#99) |
| Player: Guide and catch-up | Done | Logo tiles, programme blocks, the now line, day pills for Today and 7 days back; ⟲ replays from the start (#66, #76, #86, #100) |
| Player: Movies and TV Shows | Done | One category at a time as a six-across poster grid; Filter and sort panel; Edit categories (#101) |
| Player: details and resume | Done | Seasons, episodes, More like this, More info, My List; Continue or Episodes, Resume or Start over (#96) |
| Player: film and episode playback | Done | New panel with quality badge, round buttons and progress, and a sleep timer (#102); Audio & subtitles, Skip intro (#94); Play Next (#89); next episode on resume (#90) |
| Player: Live TV full screen | Done | Up/Down zap, channel and group lists, info bar, favourites on Menu (≡) |
| Player: sign-in and account | Done | Xtream Codes login stored encrypted on the TV; account expiry and screen limit; now the Settings page |
| Player: Search | Next (Wayne picks) | The player lost the launcher's search when the links went |
| Player: full Favourites page | Next (Wayne picks) | Favourites opens Live TV on the Favourites group for now |
| Player: Live TV sleep timer | Next (Wayne picks) | Films and episodes have one |
| Launcher home: TV Shows \| Movies | Done | Hero at the top; Continue watching, Your channels, My List, Recommended (#87), a More pill; plain cards that grow slightly on focus (#114) |
| Launcher details, search, Surprise me, Apps tray | Done | Kept as they are while the launcher lasts |
| Launcher link | Done | Details, search, Continue watching, My List and Your channels shared with the player (#52–#65) |
| Native screenshots | Done | Emulator run against a pretend provider, only on request (#36) |
| Optional extras | Not started | IMDb and Rotten Tomatoes scores (needs an OMDb key) |
| Fire Stick as Home screen | Waiting | Home on Fire needs a permission granted over ADB from the Chromebook; less important now the player stands alone |
| Own stick | Idea | A certified Google TV / Android TV stick; budget matters |
| Default home on the Xiaomi | Dropped | The stick was counterfeit and was retired |

**Links:** [GitHub repo](https://github.com/theolivecafewfd-code/tv-launcher) · launcher `is.gd/mAyKiF` · player `is.gd/pPSEvh` (both always the newest build)

**How changes are made:** Claude writes each change and opens a pull request; GitHub builds both apps (screenshots only when Wayne asks); Wayne checks it and says "merge it", then Claude merges; the update pill offers the new build on the TV.

## Goal

Build one custom home screen app, modelled on the Fire Stick launcher from the Reddit video: a personal greeting, weather, genre tabs, a big hero carousel with ratings, and a dock of streaming apps. It runs on your Fire TV Stick HD; the Xiaomi stick it was first planned for turned out to be counterfeit and was retired.

- **What stays the same:** the stock system underneath, so Netflix, YouTube, Disney+ and other certified apps keep working in full quality.
- **What changes:** everything you see when you press Home.
- **Why it should feel faster:** no ads, sponsored rows or autoplay trailers from Google or Amazon loading in the background.
- **One codebase, two devices:** the same app file installs on both sticks. The Fire Stick only needs an extra step to send the Home button to it.
- **Constraint:** built and installed entirely from a phone, using GitHub to build the app.

## Features

The home screen as built (Launcher v2), driven by the remote's arrow keys. The earlier genre-tab design was replaced by this.

| Screen | What's on it | Data from |
| --- | --- | --- |
| Home, top | Greeting; weather, clock and build in one pill; TV Shows · Movies · Live TV · Apps · search · Surprise me; the focused card's artwork, title, details and synopsis | TMDB, Open-Meteo, WIPTV Player |
| Home, rows | Continue watching, Your channels, My List, Recommended, then a More pill into the player | WIPTV Player; TMDB, filtered to Netflix and Prime in Ireland |
| Apps tray | Your apps, quick settings and Edit apps, over the bottom of the screen | Apps on the TV |
| Details | Name, year, length, genres, score, where it's on, plot, director or creators, cast; Watch on, Trailer, My List, Mark as watched; More like this | TMDB, YouTube, Wikidata (Netflix ids) |
| Search | Results as you type; your services first, the rest dimmed | TMDB |
| Surprise me | A random well-rated pick you haven't watched, with Another one | TMDB |
| Choose your apps | Every app on the TV; OK to show or hide | Apps on the TV |

- **OK** on a title opens its details; **hold OK** plays it straight away in your first service.
- **Recommended for you** learns from what you play, add to My List or mark as watched.
- **Sizes** are designed for the TV's 960 × 540 layout grid; posters load only as their row comes into view, to suit the Fire Stick's memory.

### Where a title opens

Selecting a film or series shows a button for every place you can watch it, and you choose which comes first.

| Source | How the launcher knows | What the button does |
| --- | --- | --- |
| Your subscriptions | TMDB's where-to-watch data for Ireland, limited to the services you tick in Settings | Netflix usually opens straight on the title; other apps open their title page or home screen |
| Your IPTV provider | Matches against your provider's movie and series list by TMDB ID, or by title and year when the provider has no IDs | Opens our IPTV player straight into playback |

- **Default choice:** a setting for the order, such as provider first or Netflix first, with OK going straight to the top one.
- **Until the launcher link is built:** the provider button stays hidden.

## Tech choices

A single Android app written in Kotlin with Jetpack Compose, the modern standard for Android TV apps.

| Part | Choice | Why |
| --- | --- | --- |
| Language | Kotlin | Google's main language for Android |
| UI | Jetpack Compose + Compose for TV (`androidx.tv:tv-material`) | Built for remote-control focus, tabs and carousels |
| Movie and series data | TMDB API | Free key; posters, backdrops, trending, genres, similar titles |
| Where to watch | TMDB watch providers, region IE | Free, powered by JustWatch; shows Netflix, NOW, Disney+ and others for Ireland |
| IMDb, RT and Metacritic scores | OMDb API (or MDBList) | Free key with a daily limit, so scores are cached |
| Weather | Open-Meteo API | Free, no key needed; Waterford, town-level coordinates only |
| Images | Coil, sized for 1080p | Keeps memory use down on low-memory sticks |
| Saved data | SharedPreferences now (hidden apps, order); Room later | Favourites, My List, watched titles, settings, cached results |
| Minimum Android version | Android 9 (API 28) | Covers your Fire TV Stick HD (Fire OS 7, Android 9) and newer Fire OS 8 and Android TV devices |
| Google services | None | Fire Sticks don't have Google Play services, so the app avoids them |

**Permissions and setup the app needs:**

- A `HOME` intent filter, so Android offers it as a home screen.
- Internet access, for the movie data and weather.
- A `<queries>` entry for launcher apps, so Android 11 lets it list what's installed.
- Usage access for the recently used apps, granted once from a phone over ADB.
- API keys (TMDB, OMDb) injected at build time from GitHub secrets (TMDB\_API\_KEY is set up). Never written into the code.

## Fire Stick version

The same app file runs on any Fire Stick that still uses Android-based Fire OS. Fire Sticks on Amazon's new Vega OS can't run it at all, and there's no workaround.

| Fire Stick | System | Works? |
| --- | --- | --- |
| Fire TV Stick 4K Max (2023), 4K (2nd gen), 4K Plus | Fire OS 8 (Android 11) | Yes |
| Fire TV Stick 3rd gen, Lite, older HD, 4K Max (2021) | Fire OS 7 (Android 9) | Yes, with a short delay on Home |
| Fire TV Stick 4K Select, new Fire TV Stick HD (2026) | Vega OS | No: no Android apps or sideloading |

**Your stick:** Fire TV Stick HD (1st generation) on Fire OS 7.7.1.7, which is compatible. Developer options, ADB debugging and Install unknown apps for Downloader are switched on, and WIPTV and WIPTV Player are installed.

Check yours in Settings → My Fire TV → About. Amazon has said every future Fire TV Stick will run Vega OS, so buy a Fire OS model if you want a spare ([AFTVnews](https://www.aftvnews.com/tag/amazon-fire-tv-stick/), [Fire OS vs Vega list](https://www.aftvnews.com/tag/fire-os/)).

**How Home works on a Fire Stick:** Amazon doesn't let you replace its home screen, and it blocked the old launcher-switching tricks. Instead, a small background service spots Amazon's home screen opening and immediately opens yours on top.

- **Start with an existing app:** [Home on Fire](https://github.com/IBGB-117/HomeOnFire-Plex) is free and open source, and does exactly this for Fire OS 7 and 8. It can also open your launcher when the stick starts up.
- **One-time setup over ADB from another device:** grant it permission to switch on its accessibility service, because that settings page is blank on many Fire OS 8 builds. Amazon blocks apps on the stick (such as Remote ADB Shell) from connecting to the stick's own ADB, so this is done from the Chromebook.
- **What you'll notice:** Amazon's home flashes for about a quarter of a second on each Home press on Fire OS 8, and up to a few seconds on Fire OS 7.
- **Later, optionally:** build the same redirect into our own app, so it's one install instead of two.
- **Risk:** a Fire OS update can break the redirect. The fix is usually an app update, or holding back updates.

**Other Fire Stick differences:**

- No Google Play services, so the app relies on none (see Tech choices).
- Amazon's own apps (Prime Video, Luna) go in the dock; Google TV's do on the Xiaomi.
- Amazon's launcher can't be disabled like Google's, so debloating is limited to hiding apps.

## Build and install pipeline

No computer is needed: GitHub builds the app, and your phone installs it on the TV.

Claude writes the change → pull request → GitHub builds and checks it → Wayne says "merge it" → GitHub Release → update pill (or Downloader) → Fire Stick

Every merge publishes a new build of both apps. Each app checks GitHub when it opens and every 30 minutes; its pill shows **Up to date · build N** or **Update available**, and pressing it downloads and installs whichever app has a newer build, after one Install confirmation each. For a fresh install, Downloader on the TV takes `is.gd/mAyKiF` (launcher) or `is.gd/pPSEvh` (player), which always point at the newest builds. Every build is signed with the same key, kept in the repo on purpose, so updates install over the old version.

**One-time Fire Stick setup (done):**

1. Settings → My Fire TV → About: press OK on the stick's name 7 times to unlock Developer options.
2. Developer options: turn on **ADB debugging**.
3. Developer options → Install unknown apps: turn on **Downloader**, **WIPTV** and **WIPTV Player** (so their update pills can install).
4. Downloader: enter `is.gd/mAyKiF` and install; then `is.gd/pPSEvh` for the player.

## Roadmap

Remaining work. Progress for every area is in the Status table at the top. Wayne picks the order.

1. **Player: Search.**
2. **Player: a full Favourites page.**
3. **Player: a sleep timer for Live TV.**
4. **Make the launcher the Fire Stick's Home screen** with Home on Fire, granted over ADB from the Chromebook (on hold).
5. **Optional extras:** IMDb and Rotten Tomatoes scores.

## Safety and recovery

Everything in this plan can be undone, because apps are only disabled, never deleted, and the system itself is never touched.

- **Test before switching.** Open the new launcher like a normal app and use it for a few days before making it the default.
- **Disable, don't uninstall.** Use `pm disable-user --user 0 <package>` so any app can come back with `pm enable <package>`.
- **Find the real package name first.** Run `pm list packages | grep launcher`. On Android TV 11 the stock home screen is usually `com.google.android.tvlauncher`.
- **Keep a log.** Note every package you disable in this doc, so it can be reversed line by line.
- **Never disable these:** Google Play Store, Google Play services, the setup wizard, Settings, or anything with "system UI" in its name.
- **If the home screen breaks:** connect from the phone over ADB and re-enable the stock launcher. Last resort is a factory reset from Settings.
- **After system updates:** Xiaomi or Google updates can re-enable the stock launcher. Re-run the disable command if that happens.

**Disabled packages log:**

| Package | What it was | Date disabled |
| --- | --- | --- |
|  |  |  |

## App 2: WIPTV Player

A stand-alone app for your Xtream Codes provider, in `player/` in the same repo. It started from TiviMate's layout; since build 235 it follows the "max" player Wayne liked (see [the player design](PLAYER_DESIGN.md), section 16). The launcher still opens it ("Watch on IPTV", Continue watching, Your channels), but the player no longer needs the launcher.

| Page (top menu) | What's on it |
| --- | --- |
| Library (start page) | TMDB trending banner, Continue watching, My List and other rows, chosen with Edit Library |
| Channels | Groups, then a grid of channel logos with what's on now and the time left; OK watches full screen |
| Movies, TV Shows | One category at a time as a poster grid; Filter and sort; details, seasons and episodes |
| Guide | The group's channels against the day; day pills to go back a week for catch-up |
| Favourites | Live TV on the Favourites group (a full page may follow) |
| Settings | Login, account expiry and screen limit, sign out |
| Watching | Live: Up/Down zap, channel and group lists, info bar. Films and episodes: the player panel with Audio, Subtitles, Skip intro, Sleep timer and Play Next |

| Part | Choice |
| --- | --- |
| Login | Server address, username and password typed on the TV, stored encrypted on the device only |
| Provider data | Xtream API: categories, channels, movies, series, short EPG, account info |
| Full TV guide | The provider's XMLTV guide, downloaded in the background and stored with Room |
| Playback | Media3 (ExoPlayer) for live streams, catch-up and films |
| Network | Plain HTTP allowed, because many providers don't use HTTPS |

**Phases:**

| Phase | Status |
| --- | --- |
| 1. Login and account | Done (#19) |
| 2. Live TV basics: groups, channels, full-screen playback, channel up and down | Done (#20, #21) |
| 3. TV guide: preview, now and next, guide grid | Done (#22, #23, #86, #100) |
| 4. Watching screen: channel list over the video, favourites, last channel | Done (#24–#28) |
| 5. Catch-up | Done (#66, #76, #100) |
| 6. Movies and Series: rows, details, Continue watching, My List | Done (#29); redesign done (#37–#41, #46) |
| 7. Launcher link: "play this title" from the launcher, provider rows | Done (#52–#65) |
| 8. Fire Stick check | Ongoing: each build is tried on the Fire Stick |
| 9. Stand-alone rework: Library, Channels, Guide, poster grids, player panel | Done (#97–#102) |

## Brief for the dev chat

Kept for reference. This brief produced Phase 0 and 1, which were built directly from the Claude chat rather than handed to Claude Code; later work follows the same pull-request flow described under Status.

```markdown
New project: a custom Android TV home screen (launcher) for a Xiaomi-branded Android TV 12 stick (model MiTV-AYFR0, 2 GB RAM, Widevine L3, not Play Protect certified). It must also run on Android-based Fire TV Sticks (Fire OS 7 and 8), so use no Google Play services.

Stack: Kotlin, Jetpack Compose, Compose for TV (androidx.tv:tv-material), Coil, DataStore, Room. minSdk 28.

Phase 0 and 1 only:
- Create the repo "tv-launcher" with a standard Gradle Android project.
- Add a GitHub Actions workflow that builds the APK on every push to main and publishes it to a GitHub Release, so I can download it from my phone.
- Sign every build with the same keystore (stored as a GitHub secret), so new versions install over old ones.
- App: a dark home screen with an app dock along the bottom: Netflix, YouTube, Disney+, Prime Video, NOW and Settings when installed, then every other launchable TV app. Use each app's TV banner, else its icon. Selecting a tile opens the app. Remote D-pad focus must work, with a soft glow on the focused tile.
- Manifest: HOME + DEFAULT intent filter, INTERNET, <queries> for LEANBACK_LAUNCHER and LAUNCHER intents, a TV banner, leanback and touchscreen features not required.
- No API keys or credentials in the code.
- README: how to install from a phone over ADB Wi-Fi, and how to undo.

Open a pull request. Do not merge it.
```

The full feature list and later phases are in the sections above, ready to hand over one phase at a time.
