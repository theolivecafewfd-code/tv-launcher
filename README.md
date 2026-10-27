# WIPTV

Two Android TV apps for Wayne's Fire TV Stick, built entirely in GitHub. Every merge to `main` produces new app files ready to install from the TV.

- **WIPTV** (`app/`): a custom home screen. Greeting, Waterford weather and clock; TV Shows | Movies with a featured Top 10 and a few rows of titles on Netflix and Prime in Ireland; details pages with trailers and My List; search and Surprise me; your apps with quick settings.
- **WIPTV Player** (`player/`): an IPTV app for an Xtream Codes provider. Live TV with a preview and a simple guide, favourites, full-screen channel lists, and Movies and Series with Continue watching and My List.

Both use the same restrained "glass" look (`design/`), and a single update button in either app keeps both up to date (`update/`).

## Download links

| App | Type on the TV | Full link (never changes) |
| --- | --- | --- |
| WIPTV (launcher) | **`is.gd/mAyKiF`** | `https://github.com/theolivecafewfd-code/tv-launcher/releases/latest/download/tv-launcher.apk` |
| WIPTV Player | **`is.gd/pPSEvh`** | `https://github.com/theolivecafewfd-code/tv-launcher/releases/download/player/wiptv-player.apk` |

The short links point at the full links, which always serve the newest build.

## Install on a Fire TV Stick

1. **Settings → My Fire TV → About:** press OK on the stick's name 7 times to unlock Developer options.
2. **Developer options → Install unknown apps:** turn on **Downloader** (install it from the Amazon Appstore first).
3. Open Downloader, enter `is.gd/mAyKiF` and install WIPTV. Do the same with `is.gd/pPSEvh` for WIPTV Player.
4. Open either app from **Your apps**. The launcher runs like a normal app until it's made the Home screen (see the plan).

The player signs in with the server address, username and password typed on the TV. They're stored encrypted on the TV only and never in this repo.

## Updates

The pill in the top corner of either app shows **Up to date · build N** or **Update available**. It checks GitHub when the app opens and every 30 minutes, and covers both apps: pressing it downloads and installs whichever of the two has a newer build, each after one Install confirmation. Every build is signed with the same key, so updates install over the old version.

## Undo

Uninstall either app like any other: **Settings → Applications → Manage installed applications → WIPTV (or WIPTV Player) → Uninstall**. Nothing else on the TV is changed.

## How it's built

- Kotlin, Jetpack Compose and Compose for TV (`androidx.tv:tv-material`); Coil for images; Media3 (ExoPlayer) for playback. Minimum Android 9 (API 28).
- No Google Play services, so both apps run on Fire OS.
- Modules: `app` (launcher), `player` (WIPTV Player), `design` (shared colours, type, glass panels and focus styles), `update` (shared updater).
- `.github/workflows/build.yml` builds both apps on every pull request, so each change is checked before merging. Every merge to `main` publishes the launcher as a new `build-N` release (the repo's latest) and replaces the single `player` release. Builds queue one at a time so releases don't collide.
- **Signing:** `app/signing/tvlauncher.keystore` is kept in the repo on purpose. The apps are only sideloaded onto your own TVs, and one fixed key lets each build install over the last. If they're ever published to a store, replace it with a private key held in GitHub secrets.
- **Keys:** the TMDB key is injected at build time from the `TMDB_API_KEY` GitHub secret. No API keys, passwords or provider details are stored in the code.

## Docs

- [Project plan](docs/PLAN.md): status, roadmap, Fire Stick notes and the player's phases.
- [Handover](docs/HANDOVER.md) and [CLAUDE.md](CLAUDE.md): where things stand and the working rules, for Claude Code.
- [Player design](docs/PLAYER_DESIGN.md): the glass design specification, with [references, tokens and the acceptance record](docs/player-design/).
