# WIPTV Player — UI design specification

**Version:** 0.2 · **Date:** 27 October 2026 · **Status:** Approved direction; being implemented in stages (see section 16)  
**Repository:** [theolivecafewfd-code/tv-launcher](https://github.com/theolivecafewfd-code/tv-launcher)  
**Code baseline:** [15519d43b08da4c701396ac9dec0e36992673a8d](https://github.com/theolivecafewfd-code/tv-launcher/commit/15519d43b08da4c701396ac9dec0e36992673a8d), including merged Movies and Series PR #29.

This pack specifies a native redesign of the existing `player/` app. It is documentation, not an instruction to begin implementation or a tested UI build. The glass direction is Wayne's preference; the detailed tokens and interaction refinements below are proposals for review. No app files were changed while preparing it.

## 1. Product intent

An IPTV player with the capability of a serious TV app and a calmer, easier interface. Live TV and on-demand films/series have equal importance. Give each a consistent visual language without making their browsing patterns identical.

The feeling is premium, quiet and cinematic: cool blue-grey backgrounds, translucent navigation, softly rounded panels and generous separation. Artwork and live video provide the colour. The UI should never compete with them.

Use Apple-inspired material depth as inspiration, translated into Android TV and Fire TV conventions. The reference is not a request to copy Apple's assets, proprietary fonts or every glass effect.

### Priorities, in order

1. Reliable remote control, visible focus and uninterrupted playback.
2. Readability from a sofa, including busy or bright backgrounds.
3. Fast access to categories, favourites, resume and episodes.
4. Consistent spacing, typography and component states.
5. Glass, depth and short transitions.

A plain, well-composed panel that works on the Fire Stick is acceptable. A beautiful effect that obscures text or slows navigation is not.

## 2. Scope and source of truth

| Included in this redesign | Keep as future feature work |
| --- | --- |
| Live TV, Movies and Series tabs | Unified Home page |
| Live preview, guide, channel and category lists | Global cross-library search |
| Existing category hiding and ordering | Separate top-level My List destination |
| VOD category rows, Continue watching and My List rows | Household profiles or multi-user history |
| Movie details, seasons, episodes and playback overlays | Recommendations, new metadata services or trailer previews |
| Login, account, update and error surfaces | Provider/account model changes |
| A live Categories button and drawer, delivered after the initial styling pass | New playback or catch-up capabilities |

Keep **Live TV · Movies · Series** as the top-level destinations and preserve the current launch destination. My List stays inside Movies and Series. Do not display non-functional search or profile controls from the reference mockup.

Authority order for implementation: this reviewed specification and its acceptance checklist; current application behaviour at the implementation branch; then the HTML references for visual intent. If a newer code change conflicts with the documented baseline, report the difference and preserve working behaviour until the specific change is resolved. A mockup click handler never overrides native remote behaviour.

## 3. Visual references

- [Glass reference](player-design/references/glass.html): preferred visual direction.
- [Clean V1 reference](player-design/references/clean-v1.html): preserved alternative and fallback reference.
- [Pack overview](player-design/README.md): contents and implementation order.

Both references contain illustrative content and extra concept features. Their browser sizes, hover behaviour, sample guide cells, fake playback and mobile layouts are not native requirements. Open them locally in a browser; they do not require a provider account. They use sample titles rather than real stream credentials.

Keep both references in the repository when this pack is added. Use the glass reference for material, light, layering and restraint; use the specification below for actual TV sizing and behaviour.

## 4. Design tokens

Centralize these as native theme values before restyling screens. Values below are the proposed starting system, not measurements taken from a running APK. JSON equivalents are in [tokens.json](player-design/tokens.json). Android colours in Kotlin must use explicit alpha; JSON hex colours below are RGB plus separate opacity values.

### Colour

| Token | Value | Use |
| --- | --- | --- |
| Background | `#111624` | Root canvas |
| Background top | `#182537` | Subtle static backdrop gradient |
| Solid surface | `#202B42` | Reliable card and guide base |
| Elevated surface | `#33425B` | Raised panel or emphasis |
| Panel base | `#25354E` | Drawers, dialogs, fallback glass |
| Primary text | `#F5F7FF` | Main labels and programme titles |
| Secondary text | `#C0C9DC` | Times, metadata and helper text |
| Quiet text | `#AEBBD0` | Non-critical supporting labels |
| Focus ring | `#E7EEFF` | Remote focus |
| Focus fill | `#E7EEFF` | Small focused controls |
| Text on light fill | `#202C42` | Focused small control labels |
| Positive | `#B4EED6` | Live dot or saved confirmation |
| Warning | `#FFD09B` | Recoverable warnings |
| Error | `#FFB8B4` | Failure labels, with explanatory text |
| Progress | `#DFEAFF` | Playback and watched progress |
| Scrim | Black at 60–85% | Readability over images/video |

Borders use white at 16% by default, 28% for elevated material and 40% for the restrained top highlight. Use text, icons and state shapes alongside colour. A green dot alone cannot explain a state.

Target text contrast of at least 4.5:1 for ordinary text; focus and essential boundaries should remain clear at 3:1 against adjacent colours. Check the composited result, including artwork and video, rather than just token pairs. Increase the panel opacity when contrast fails.

### Typography

Use the platform sans-serif family initially; no proprietary Apple font or added network font dependency. The mockup's compact browser text is not the TV scale.

| Role | Size / line height | Weight |
| --- | --- | --- |
| Detail or hero title | 30 / 36 sp | Medium or semibold |
| Screen title | 24 / 30 sp | Semibold |
| Section heading | 20 / 26 sp | Medium |
| Primary button, tab, category or channel label | 16 / 22 sp | Medium |
| Programme title and body | 16 / 22 sp | Regular/medium |
| Poster caption and supporting metadata | 14 / 20 sp | Regular |
| Secondary remote hints | 12 / 16 sp | Regular |

Keep essential navigation and programme information at 14 sp or larger. Use sentence case. Titles may use two lines on posters; guide entries normally use one or two depending on available row height. Truncate long provider titles safely. Do not reduce type until it fits.

### Space, shape and motion

- Spacing scale: 4, 8, 12, 16, 24, 32 and 40 dp.
- Base composition: approximately 960 × 540 dp in landscape; actual device density may differ. Use this as a layout reference, not a forced screen resolution or a pixel multiplier.
- Safe content inset: 32 dp horizontally and 24 dp vertically at the reference size. Keep every focused item's scale and outline inside visible bounds.
- Standard gaps: 16 dp between major groups, 12 dp between cards, 8 dp within controls.
- Small controls: minimum 44 dp height; comfortable category/episode rows 48–56 dp.
- Corner radii: small 8 dp; cards/buttons 12 dp; preview/drawer/dialog 20 dp; nav capsule and compact chips 999 dp.
- Focus animation: 120 ms; ordinary fade 160 ms; drawer 180 ms. Avoid spring overshoot, bouncing, parallax and continuously moving backgrounds.
- Focus scale: 1.04 for posters; 1.02 for large tiles; 1.00 for guide rows, drawers and compact tabs.
- Focus outline: 2 dp with 2 dp visual separation where space permits. Reserve room in lazy containers so it is never clipped.
- Transitions must be interruptible. Navigation must respond immediately; an animation must not delay a key action. With animation disabled, all states remain understandable.

## 5. Glass material and component states

### Material recipes

**Navigation glass:** panel-base tint at 86% opacity; white wash gradient from 12% at the top to 4% at the bottom; 1 dp white border at 28%; restrained top highlight. Keep depth static.

**Raised drawer/dialog:** panel-base tint at 96% opacity; white wash from 8% to 2%; 1 dp white border at 28%; scrim behind it where needed. It must be readable over the brightest scene without blur.

**Guide/card surface:** solid-surface base at full opacity for the guide, with optional white wash up to 4%. Poster artwork stays sharp; only its frame receives the material treatment.

**Over-video controls:** black-to-transparent scrim plus a panel-base tint of at least 94% underneath text. Preserve the video image and its existing rendering surface.

These are starting recipes. Composite the wash over the tint, with all foreground text outside any blur layer. There is no requirement for refraction, distortion, moving highlights or real-time backdrop sampling.

### Blur policy

Ship the first pass with **no real-time blur**. It should still read as premium glass through tint, borders, gradients, spacing and contrast.

Android's standard Compose blur affects its own composable content; it is not a drop-in equivalent of CSS backdrop-filter, and uniform blur requires Android 12 or later. Keep the current project dependencies; do not copy newer APIs from documentation into the older pinned Compose stack. The app also targets older Android versions.

A later enhancement may blur a bounded, cached artwork background if supported and demonstrably smooth. Do not blur text, create a blur per poster/guide row, capture live video frames, change the video surface type or upgrade the UI/media stack just for glass. A native glass enhancement is separate from the first styling delivery.

### Focus is different from selection

| State | Visual rule | Meaning |
| --- | --- | --- |
| Normal | Dark/tinted surface, primary or secondary label | Available action |
| Focused small control | Light fill, dark text, 2 dp focus outline | OK acts here |
| Focused poster | Sharp artwork, bright outline, 1.04 scale | OK acts on this title |
| Focused guide row | Raised cool fill plus outline; no scale | OK acts on this channel |
| Selected, unfocused | Muted light wash plus underline/check/side marker | Current section or category |
| Selected and focused | Focus treatment plus retained selected marker | Both states apply |
| Playing | Live/playing icon or text, independent of focus | Current stream |
| Saved/watched | Heart/check and progress where appropriate | Persistent user state |
| Pressed | Brief luminance change without layout shift | Action accepted |
| Disabled | Muted surface; understandable label; skipped in focus traversal | Unavailable action |
| Loading | Stable placeholder or label; navigation stays reachable | Work in progress |
| Error | Readable message with Retry/Back when applicable | Action failed |

Primary Play/Resume buttons can already have a light resting fill. Their focus must add an unmistakable outline and slight elevation; do not rely on that same light fill to communicate focus. Only one item owns keyboard focus at a time.

## 6. Screen specifications

### A. Shared header

At the top, place a compact WIPTV wordmark at left, a floating capsule with Live TV / Movies / Series, and the existing update action at right. Keep current account access reachable; do not invent a household-profile control.

The capsule may sit over a static artwork gradient in VOD and over the root background in Live TV. Give the header its own readable backing. It must not overlap hero text or scrolling rows. Start with the existing header structure if necessary, then converge on this layout without altering section lifecycle.

On a fresh section entry, preserve the current content-focused entry behaviour when content is ready. Up from the first content row reaches the header or secondary toolbar; Down returns to the remembered content item. Moving focus over a tab does not switch sections; OK does. Left/right at the ends do not wrap unexpectedly.

### B. Live TV

Visual hierarchy: header; category access; preview with current-programme information; then a clean guide.

**Initial styling pass:** keep the existing category sidebar and working selection behaviour while replacing colours, typography and surfaces. Do not combine the first theme extraction with navigation restructuring.

**Target layout pass:** replace the persistent sidebar with an obvious **Categories** button above the guide. Its label can include the active category, for example “Categories · Irish channels”. Favourites remains a prominent category; use actual provider categories and preserve hidden/reordered preferences.

The category drawer is approximately 240 dp wide, aligned to the button at the left content edge, with a maximum height bounded by the safe viewport and a lazy vertical list. It overlays the browsing screen and traps focus until dismissed. Opening it must not tune another channel.

- OK on Categories opens the drawer and focuses the active category, or the first available entry.
- Up/Down moves within the list. Focus alone does not commit a category.
- OK on a category commits it, closes the drawer and focuses the remembered channel in that group, or its first available channel.
- If the group has no channels or fails to load, focus moves to the empty/error panel's actionable control; Categories remains reachable.
- Back cancels the drawer without changing the category and restores focus to Categories.
- Re-entering the drawer preserves scroll and selection. Keep Edit categories and account access as labelled secondary actions, not hidden gesture-only controls.
- This explicit commit behaviour is an intentional refinement from the current sidebar; deliver it separately from cosmetic changes.

Place the preview and programme summary side by side. Preserve video aspect ratio without rounded-corner clipping that changes playback behaviour; a surrounding rounded frame is sufficient. Programme summary: channel, programme title, time/progress and up to three lines of description. Show “Programme information unavailable” when EPG is missing.

Retain the current channel-centric guide model and lazy EPG loading. Do not turn illustrative mockup schedule cells into a new timeline feature. Use an aligned channel area, programme area and progress/time indicators. Target at least four complete channel rows at the reference size; compact the preview before shrinking essential text. A 120–136 dp preview height and 44–48 dp guide rows are starting values.

Focus on a row does not tune it. Preserve current OK behaviour: OK starts that channel in preview; OK again on the playing channel opens full screen. Existing preview and media-button actions remain available. Show favourite and playing states independently of focus.

### C. Movies and Series catalogues

Keep the existing lazy horizontal rows inside the vertical catalogue: Continue watching, My List, then visible provider categories in their saved order. Do not fetch the entire catalogue to imitate a mockup grid. Do not add invented “Trending” or “Recommended” rows.

Use a restrained hero at the top, with the focused title's backdrop, a left/bottom readability gradient, title, year/rating when available and a short synopsis. No autoplay trailer. No random hero movement. Preserve the last rendered background during rapid focus movement, or debounce artwork replacement by approximately 120 ms; do not delay focus or fire a new details request per key press.

Starting poster size: 104 × 156 dp, with 14 sp captions and a 12 dp gap. Maintain roughly seven visible posters plus a partial next item at the reference width; adapt to the actual viewport. Use predictable row heights, up to two caption lines and visible focus overflow. Progress bars and saved/watched badges remain readable against any poster.

OK on an ordinary poster opens details. OK on Continue watching resumes directly, matching current behaviour. On return from details or playback, restore the same row, item and scroll offset. Artwork, progress refreshes and category loads must not steal focus.

Existing Edit categories remains a labelled action. A VOD category picker or jump menu is future work; do not repurpose the live drawer into a new VOD filtering model in this pack.

### D. Movie and series details

Use a full-screen cinematic backdrop with strong gradients, a sharp poster, title, metadata and the existing actions. Keep actions visible before long plot/cast content. Allow layout to scroll when content, localisation or font scale requires it.

Movie action order: Resume when available, otherwise Play; Start over when applicable; My List; Trailer when available. Keep optional metadata optional: omit missing year/runtime/rating rather than fabricating values or displaying null. Label the provider score as a score, not a certification or a named third-party rating.

Series: show the existing Resume action when appropriate; a clear season selector; and episode rows with episode number/title, duration when available, watched state and progress. Keep the established next-episode and resume logic. The selected season remains marked when focus moves into its episode list.

Back returns to the originating poster with focus restored. Hiding/showing Trailer or changing My List must not silently transfer focus to an unrelated action. Use a readable “Added to My List” / “Removed from My List” confirmation without a modal interruption.

### E. Full-screen playback

Keep existing key mappings and playback lifecycle. Style the Live TV info bar, channel/group lists and VOD progress overlay with the shared material and typography. Keep the picture dominant.

For VOD, show title/episode, elapsed and total time, a clear progress bar and the existing key hints. Unknown duration must not create a fake percentage. Keep current visibility timing and paused/error visibility; this pack does not introduce a new transport-control focus model.

For live playback, preserve Back's existing overlay ladder, channel zapping, previous-channel action and Menu favourites. Full-screen group/channel overlays are not the new browsing Categories drawer; retain their existing left/right navigation.

Error and buffering text sits on a stable, readable panel. Separate “Loading…” from failure. Do not display passwords, credential-bearing stream URLs or raw sensitive exceptions in new UI copy.

### F. Login, account, editors and updates

Carry the theme through login fields, account screens, category editors and updater states so the app does not revert to an unrelated style. Preserve field validation, credential storage, account sign-out, update download/install and category editing behaviour.

Keep labels visible above or beside text fields. Make the focused field and action unambiguous with a remote. Never expose a password as decorative account information. Keep a clear route back from account/editor screens.

## 7. Remote contract to preserve

This table records the reviewed source baseline, not an on-device verification. Re-check if code changes before implementation.

| Context | Existing action to retain |
| --- | --- |
| Live guide | OK tunes preview; OK on the current channel opens full screen; Menu toggles the row's favourite |
| Live browse | Play/Play-Pause opens the existing preview full screen when a channel is available |
| Live full screen, no list | Up/Down zap channels; Left opens channel list; Right returns to previous channel; OK/Info shows information |
| Live full-screen lists | Left from channels opens groups; Right from groups returns to channels; Right from channels closes list |
| Live favourites | Menu acts on the playing channel without a list, or the highlighted channel in the channel list; no long-OK replacement |
| Live Back | Groups → channels → picture → browsing screen |
| VOD playback | OK or Play/Pause toggles pause; Left seeks back 10 seconds; Right seeks forward 30 seconds; Up/Down/Info reveals overlay |
| VOD Back | Stops playback, saves the existing resume point and returns through the current flow |
| Details/editor/dialog | Back dismisses the topmost surface and restores its originating focus |

Handle key-down/key-up consistently with the current code; one Menu press must toggle a favourite once. Held navigation may repeat; held OK must not trigger repeated activation.

Use explicit focus restoration and stable lazy-item keys. Do not override every D-pad event with one generic app-wide handler. The detail screen, drawer, guide and player each have different navigation needs. The platform Home button remains a platform action.

## 8. Loading, empty and failure states

Every restyled screen needs a useful state before data or images are available.

| Condition | Required presentation |
| --- | --- |
| Initial catalogue load | Stable heading and row placeholders; top navigation remains reachable |
| Empty Continue watching/My List | Omit the empty row; keep the rest of the page intact |
| Empty category | “No channels in this category” or “No titles in this category”, with a route to categories/back |
| All categories hidden | Explain it and offer the existing Edit categories action |
| Missing artwork | Consistent dark placeholder with the title; no broken-image icon or layout collapse |
| Missing EPG | Channel remains usable; “Programme information unavailable” |
| Provider/request failure | Short explanation, Retry where supported, and a working route back |
| No episodes | “No episodes available”; retain Back and other valid actions |
| Playback error | Readable existing error state; Back remains responsive |
| Long titles/descriptions | Controlled wrapping/ellipsis; no overlap with controls or focus borders |

Do not add fake Retry controls where no retry action exists. Add a small scoped UI retry callback only when necessary, preserving existing requests and cancellation behaviour.

## 9. Native implementation map

All source paths in this table are under `player/src/main/java/dev/wiptv/player/`.

| Existing file/component | Design work | Behaviour to protect |
| --- | --- | --- |
| PlayerRoot.kt: Palette, PlayerButton, SectionTabs, UpdatePill | Extract shared theme/tokens; consistent focus and nav capsule | Section disposal, saved login, updater actions |
| LiveScreen.kt: LiveScreen, ListItem, Preview | Restyle sidebar first; later category drawer and layout | Preview tuning, last channel/group, focus restoration |
| Guide.kt: GuideRow, TimeRuler, NowPanel | Crisp guide surfaces, sizes and readable metadata | GuideStore caching and visible-row EPG loading |
| WatchScreen.kt: ChannelListOverlay, GroupListOverlay, InfoBar | Shared glass panels and states | Existing overlay ladder, zapping, Menu favourite handling |
| CatalogScreen.kt: PosterRow, PosterCard, hero | Shared poster component, row spacing and hero treatment | Lazy loading, direct resume, category ordering/hiding |
| VodDetail.kt: VodDetailScreen, SeasonTab, EpisodeRow | Reusable detail/actions/season/episode styling | Detail requests, resume, episode chaining, Trailer |
| VodPlayer.kt: ProgressBar and overlay | Material and typography over existing video | Seek keys, pause, save timing, disposal |
| LoginScreen.kt, AccountScreen.kt, Groups.kt | Consistent forms, dialogs and editor controls | Login/sign-out, hide/order persistence |
| LivePlayer.kt, Xtream.kt, Resume.kt, LoginStore.kt, Updater.kt | Normally no functional changes | Engine, provider API, storage formats, updates |

Suggested new shared files: `PlayerTheme.kt`, `PlayerComponents.kt`, and optionally `PlayerFocus.kt` for small reusable focus helpers. Names are suggestions; follow existing project conventions.

Extract presentational components with state and callbacks. Do not move network requests into glass components or rewrite repositories/ViewModels as a prerequisite for this design. Avoid a broad architecture migration hidden inside a styling PR.

In particular, retain disposal of the live section when leaving Live TV. Keeping all sections alive merely to preserve scroll could leave a live stream running while VOD starts. Preserve one active playback session and use lightweight saved UI state instead.

## 10. Delivery sequence

| Stage | Deliverable | Completion evidence |
| --- | --- | --- |
| 0 — Record baseline | Build current main; capture screenshots, remote paths and known issues on Wayne's Fire Stick | Baseline commit/device/OS/build and honest test notes |
| 1 — Shared style + Live TV | Theme/components; restyled existing live layout and unchanged controls | Native live screenshots plus remote smoke check |
| 2 — Live Categories layout | Categories button/drawer and reclaimed guide space | Drawer focus/Back/empty tests; four-row readability check |
| 3 — Movies + Series | Hero, posters, rows, details, seasons and episodes | Both VOD paths and resume/return-focus checks |
| 4 — Playback + remaining surfaces | Full-screen overlays, login, account, editors and updater | Playback/lifecycle regression checks and visual comparison |

Keep PRs small enough to review. Each PR should name its baseline, include real native screenshots and distinguish “checked in source”, “built”, “emulator tested” and “tested on Fire Stick”. Do not substitute an HTML screenshot for proof that the APK matches the design.

Use the repository's existing build workflow and artifact process. Verify actual Gradle tasks before documenting a command. No CI/dependency migration is needed for this pack.

Do not add screenshot tests that just mirror every style value. Useful checks include state transitions introduced by the drawer and critical resume/playback behaviour when touched; use existing project tests where available and complete the manual remote checklist.

If the target device is unavailable, deliver the build and mark hardware checks pending. Do not call performance or remote behaviour verified from code inspection alone.

## 11. Performance and readability acceptance

- Start from no-blur materials; avoid new heavy graphics dependencies.
- Keep catalogue/guide lists lazy, images bounded to their display size and cache reuse intact.
- Do not preload every category, refetch metadata for every focused title or recreate an ExoPlayer during focus animation.
- Keep playback smooth while opening lists and overlays. Compare the same stream and navigation path against the baseline on the same device.
- Record visible stalls, audio interruptions, unexpected stream restarts and focus loss. A repeatable regression blocks completion until fixed or the effect removed.
- Check bright sport footage, dark film footage, missing artwork, long category names, long programme titles, empty lists and large libraries.
- Check 720p and 1080p output where available, actual screen edges/overscan, and a larger system font scale. Do not use an exact fixed canvas that crops on another output.
- Read the guide from ordinary viewing distance. Increase opacity or simplify material before reducing text size.
- The fallback glass material is the default implementation, not a lesser hidden mode.

## 12. Definition of done

Use [ACCEPTANCE.md](player-design/ACCEPTANCE.md) for the per-stage check record. The redesign is complete when existing features work, the three sections feel consistent, focus is always visible, all data states are handled and the target Fire Stick has passed the relevant playback and remote checks.

Owner review should evaluate the actual APK, beginning with Live TV. Preserve Clean V1 as the archived reference; do not ship a theme switcher unless separately requested.

## 13. Decisions and boundaries for Claude

1. Implement native Compose UI; do not embed the HTML reference in a WebView.
2. Keep Live TV, Movies and Series equally considered, with their current data/interaction models.
3. Use the proposed shared token system; keep one source of truth for repeated values.
4. Deliver the no-blur glass treatment first. Do not change the video renderer to reproduce browser glass.
5. Keep the Categories interaction as a separate, explicit layout stage.
6. Preserve Menu favourites, direct Continue watching, resume/episode logic and one active playback session.
7. Do not add Home/search/profiles/top-level My List as part of styling.
8. If a visual instruction would break established behaviour, document the specific conflict and propose the smallest adjustment.
9. Do not claim real-device verification without performing it.

## 14. Evidence and limitations

Reviewed current merged source on 27 October 2026, including PlayerRoot, LiveScreen, WatchScreen, CatalogScreen, VodDetail, VodPlayer and Groups. The earlier review also covered Guide, LivePlayer, the player build configuration and provider/resume separation. The merged commit confirms the reviewed Movies and Series implementation is now on main.

This is a source-grounded design specification. No APK was compiled, installed or measured while preparing the pack. Native layout dimensions, text contrast over real content and hardware performance require implementation-time validation.

Official implementation references, consulted 27 October 2026:

- [Compose blur overview](https://developer.android.com/develop/ui/compose/graphics/blur/overview): surface blur versus backdrop blur, and platform requirements. New examples may target newer library versions than this project.
- [Focus in Compose](https://developer.android.com/develop/ui/compose/touch-input/focus): focus traversal fundamentals.

## 15. Change record

| Version | Date | Change |
| --- | --- | --- |
| 0.1 | 2026-10-27 | Initial draft against merged Movies and Series baseline; glass direction, remote contract, native implementation map and staged acceptance plan |

| 0.2 | 2026-10-27 | Wayne approved the glass direction for both apps. Implementation status and agreed deviations recorded in section 16 |

After Wayne reviews this pack, record any amendments here. When eventually adding it to GitHub, keep this file at `docs/PLAYER_DESIGN.md` and its companion folder at `docs/player-design/`. Repository publication and implementation are separate next actions; neither has been performed for this draft.


## 16. Implementation status

Kept up to date as stages land. "Built" means the GitHub build passed; "emulator" means native screenshots from the screenshot run (1080p TV profile, pretend provider); "Fire Stick" means Wayne has tried it on the stick.

| Stage | Pull requests | Built | Emulator | Fire Stick |
| --- | --- | --- | --- | --- |
| 1 — Shared style + Live TV | #31 (design module, header, Live TV), #35 (calmer guide), #43–#44 (soft row focus, no programme boxes, quality badges) | Yes | — | Yes (build 112) |
| Launcher in the player's style (added) | #32, #47–#50 (cinematic home, details, search, app editor; polish) | Yes | — | #47–#49 yes; #50 pending (build 125) |
| 2 — Live Categories drawer | Not started (on hold) | | | |
| 3 — Movies + Series | #36–#39, #41, #46 (resume hero, episode focus, More info, years) | Yes | Yes | Yes (build 116) |
| 4 — Playback + remaining surfaces | Not started | | | |

**Agreed deviations from version 0.1**

- **Quiet glass.** A lighter `Glass.Quiet` recipe (45% tint, faint edge) for side panels on a plain background, such as the Live TV categories, after Wayne found the panels too blue (#35).
- **Guide rows.** No programme boxes or fills: programmes sit on the row, with a very faint line where each starts and an 8% hairline between rows. Focus is a soft #30405A fill across the whole row and a 3 × 24 dp #DDE8FF bar at its left edge, with no outline, glow or scaling. The playing channel keeps its green ▶ (no bar); favourites an amber ★. The now line runs behind programme text, and only one programme shows per time slot (#35, #43, #44).
- **Quality badges.** A reliable trailing quality tag ("Sky Sports News | FHD |") shows as the name plus a small FHD/HD/SD badge, in the guide, the preview panel and full-screen lists; ambiguous names are left alone (#43, #51).
- **Channel column** 248 dp (was 196 dp) so names fit (#35).
- **Posters** 126 × 189 dp, about six across with the next peeking in, at Wayne's request (the spec started at 104 × 156 dp, about seven) (#37).
- **Continue watching** uses wide cards with the episode still or backdrop and progress, one row however many there are. The focused card's title, resume point and time left show once in the hero, over the title's artwork (from the catalogue when the card has none); the card itself says "OK to resume" (#37, refined in #46).
- **Hero details for films.** Films' rows rarely carry a synopsis or backdrop, so after focus rests on a film for 0.7 s its details are fetched once and cached. This is not a request per key press (#37).
- **Details actions.** Only the focused action is light. At rest Play/Resume has a brighter wash; other actions are quiet glass (#38).
- **More info** panel for the full synopsis, cast and director, since the header keeps the synopsis to three lines (#38). Dark slate (`Glass.Sheet`, #202B32), about 42% wide, as tall as its text and scrolling when long (#46).
- **Episode rows** take the Live TV guide's focus: a soft #30405A fill and a short #DDE8FF bar on the left, with no outline or scaling. The details page settles a newly focused row near the top, so the header scrolls away and about five episodes show (#46).
- **Years in names.** A trailing "(1987)" that matches the year in the details line is left off the displayed name; bare numbers ("Blade Runner 2049") are never touched (#46).
- **Episode titles** shown cleaned ("1 · The Stolen Eagle"); the provider's title is kept unchanged in the data (#39).
- **Start section.** The player accepts a `section` extra (LIVE, MOVIES or SERIES); the default launch destination is unchanged (#36).
