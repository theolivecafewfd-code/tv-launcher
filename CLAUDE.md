# CLAUDE.md

Guidance for Claude working in this repo. Read [docs/HANDOVER.md](docs/HANDOVER.md) for where things stand, then [docs/PLAN.md](docs/PLAN.md) for the roadmap.

## The project

**WIPTV** is two Android TV apps for Wayne's Fire TV Stick HD (Fire OS 7.7, Android 9 / API 28), built and released entirely through GitHub.

| Module | Package | What it is |
| --- | --- | --- |
| `app/` | `dev.tvlauncher` | **WIPTV**, the launcher: greeting, Waterford weather, clock; TV Shows \| Movies home with a featured Top 10 and rows from TMDB (Netflix and Prime in Ireland); details, search, Surprise me; app dock with quick settings |
| `player/` | `dev.wiptv.player` | **WIPTV Player**, an Xtream Codes IPTV app: Live TV (preview, guide, favourites, full screen), Movies and Series (rows, details, seasons, episodes, Continue watching, My List) |
| `design/` | `dev.wiptv.design` | Shared glass look: `WiptvColors`, `WiptvType`, `WiptvShapes`, `Modifier.glass(shape, Glass.*)`, `focusOutline`, `SectionCapsule`, `WiptvButton`, `GlassPill`, `PivotScrolling` / `RowScrolling` |
| `update/` | `dev.wiptv.update` | Shared updater: the pill in either app checks both releases and installs whichever app is out of date |

**Stack:** Kotlin 2.0.21, AGP 8.7.3, Compose BOM 2024.12.01, `androidx.tv:tv-material` 1.0.0, Coil 2.7.0, Media3 1.5.1. minSdk 28, target and compile SDK 35. No Google Play services (Fire OS has none). Don't add dependencies without asking.

## How Wayne works (follow this)

- **Wayne works mostly from his phone.** He tests on the TV and sends photos. Keep replies short and in plain language. No code walls in chat, and no long progress narration.
- **Every change goes through a pull request.** Branch from `main`, push, and open a PR with a plain-English description that ends with a short **"Check on the Fire Stick"** list.
- **Never merge unless Wayne says "merge it"** (or clearly asks to merge). Then you do the merge. He sometimes checks changes in another chat first.
- **Keep PRs focused.** If a request grows into something big (new tooling, a redesign), say so and check before building it. Wayne watches his usage.
- **Screenshots workflow: only when Wayne asks.** `.github/workflows/screenshots.yml` is manual-only (`workflow_dispatch`). When he does ask, send a quick overview of two or three images, not every screen.
- **Don't make changes in connected tools** (Square, QuickBooks, Drive and so on) without his approval. They're read-only unless he says otherwise.

## Hard rules

- **No secrets in code.** The TMDB key comes from the `TMDB_API_KEY` GitHub secret at build time. Provider logins are typed on the TV and stored encrypted on the device (`LoginStore`). This repo is public.
- **No precise location.** Weather is Waterford at town level only. Never add an Eircode or a street address.
- **Never show passwords or credential-bearing stream URLs** in the UI or in error messages.
- **One active stream.** Leaving Live TV must stop its stream; never keep a hidden player running.
- **Keep the launcher lean:** about four rows on the home screen at most.
- `app/signing/tvlauncher.keystore` is in the repo on purpose (sideload only; one key so updates install over each other). Don't move or replace it.

## Build, CI and releases

- `.github/workflows/build.yml` builds `:app` and `:player` (release) on every PR and on every push to `main`. Builds queue one at a time per branch.
- Merging to `main` publishes the launcher as release `build-N` (the repo's latest, `tv-launcher.apk`) and replaces the single `player` release ("WIPTV Player build N", `wiptv-player.apk`, never "latest"). N is the Actions run number, which is also the `versionCode`.
- Download links: `is.gd/mAyKiF` (launcher) and `is.gd/pPSEvh` (player). Both point at fixed release URLs.
- **Local Android builds may not be possible** in a cloud session, because the SDK and Maven downloads can be blocked. Push and let CI build. If the log download is blocked, compiler errors are also posted as a single `Build failed` annotation:
  `gh api repos/theolivecafewfd-code/tv-launcher/check-runs/<id>/annotations`
- In some sessions `gh`'s GraphQL commands (`gh pr create`) are blocked; the REST API works: `gh api repos/theolivecafewfd-code/tv-launcher/pulls -f base=main -f head=<branch> -f title=... -F body=@file`.
- If a release upload ever fails with "Not Found … release asset" (two builds racing), re-run the failed job.
- **Screenshots (on request only):** Actions → Screenshots → Run workflow on a branch. It runs the debug player on an Android 9 TV emulator against a pretend provider (`tools/screenshots/`, invented titles and artwork) and pushes PNGs to the branch `screenshots/<branch>`. `DemoSetupActivity` exists only in debug builds.

## Fire Stick and Compose lessons (learned the hard way)

- **Remote:** the Menu (≡) key toggles favourites. Holding OK doesn't reach the app reliably on the Fire Stick. Act on `KeyUp` so one press toggles once.
- **Video:** put Media3's `PlayerView` (via `AndroidView`) in a plain `Box`, never inside a tv-material `Surface`. Inside a Surface the picture goes black and only the sound plays.
- **Focus restore:** `Modifier.focusRestorer()` crashed ("Release should only be called once") with changing lazy lists. Use `focusProperties { enter = { … } }` with `focusGroup()` instead.
- **Coroutines:** when catching exceptions around network calls, always rethrow `CancellationException`.
- **Scrolling:** Fire TV's default `LocalBringIntoViewSpec` pivots focus about a third of the way across. The `design` module's `PivotScrolling` and `RowScrolling` give the same behaviour on every device. Catalogue rows use `alwaysAlign` so the focused row sits at the top, one row per press.
- **Lazy rows that load late:** give them focusable placeholders, or D-pad focus search will skip past them to the next loaded row.
- **No real blur:** the Fire Stick can't afford it. The "glass" is tint, a white wash and a faint edge (`Modifier.glass`). The soft backdrop fallback is a tiny poster stretched up.
- **Screen saver:** the window keeps the screen on while video plays (`KeepScreenOn`).
- **Images:** keep Coil requests bounded to their display size; the stick has little memory.

## Design

The player design spec is [docs/PLAYER_DESIGN.md](docs/PLAYER_DESIGN.md) (the glass direction, approved). Its section 16 records implementation status and agreed deviations, and [docs/player-design/ACCEPTANCE.md](docs/player-design/ACCEPTANCE.md) is the check record. Use the tokens in `design/` rather than new hard-coded colours or sizes. Posters, rows of them, row emphasis and the expanding cards are shared too (`design/.../Posters.kt`), as is the header (`Header.kt`): change them there so both apps stay the same.

**Check both sides every time.** A fix or feature for TV Shows must be checked against Movies (and the reverse), and one in the launcher against the player where the same thing exists there. The player's Movies and TV Shows are one screen that switches on film or series: look at every `VodKind` branch a change touches. Focus must always be unmistakable and different from "selected", "playing" and "favourite".

## Writing style for PRs and replies

Plain English, short sentences, British spelling (favourites, colour). Describe what Wayne will see on the TV, not internals. Say clearly what still needs checking on the Fire Stick, and never claim something was tested on the device when it wasn't.
