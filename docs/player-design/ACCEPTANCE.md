# WIPTV — implementation acceptance record

**Companion:** [UI specification](../PLAYER_DESIGN.md)  
**Status:** In use. Progress per stage is in [section 16 of the specification](../PLAYER_DESIGN.md#16-implementation-status). Items are ticked only once checked on Wayne's Fire Stick, unless marked otherwise.

Copy the relevant stage into each implementation PR, or update this record with the tested build and evidence. An unchecked item means pending, not a known failure.

## Test record

| Field | Record |
| --- | --- |
| Implementation commit / PR | Stage 1: #31, #35 · Launcher: #32 · Stage 3: #36–#39 (in review) |
| APK / build artifact | Build 79 on `main` (both apps) |
| Device model | Fire TV Stick HD (1st generation) |
| Android / Fire OS version | Fire OS 7.7.1.7 (Android 9) |
| Output resolution / font scale | Pending |
| Tester and date | Pending |
| Comparison baseline | 15519d43b08da4c701396ac9dec0e36992673a8d, or document a newer baseline |
| Known pre-existing issues | Pending baseline check |
| Screenshots / recordings | Wayne's photos of builds on the TV; emulator screenshots from the Screenshots workflow (#36), on `screenshots/<branch>` branches |

## Stage 0 — baseline

- [x] Current main builds through the existing workflow.
- [ ] Capture Live TV, Movies, Series, details and playback surfaces on the target device.
- [ ] Record existing remote actions and any existing faults without attributing them to styling.
- [ ] Identify baseline playback behaviour while navigating and changing sections.

## Stage 1 — shared styling and current Live TV layout

- [ ] Repeated colours, sizes, shapes and focus states come from shared theme/components.
- [ ] Light primary buttons still have a distinct focus state.
- [ ] Selected section/category, focused item, playing channel and favourite are distinguishable.
- [ ] Header, preview, guide and existing sidebar fit without clipped focus borders.
- [ ] At least four complete guide rows remain readable at the reference viewport.
- [ ] No text depends on live background blur for contrast.
- [ ] OK tunes preview; repeating OK on the playing channel opens full screen.
- [ ] Menu toggles a favourite once per press and persists correctly.
- [ ] Play/Play-Pause retains its live preview action.
- [ ] All three tabs are reachable; focus alone does not switch them.
- [ ] Missing EPG, missing logos, long titles and request failures remain usable.

## Stage 2 — live Categories drawer

- [ ] Categories button clearly names its purpose and active group.
- [ ] Opening the drawer focuses the active category and does not tune another stream.
- [ ] D-pad stays within the open drawer; the list scrolls to its focused item.
- [ ] Moving focus does not commit a category.
- [ ] OK commits, closes and restores the group's remembered channel or a safe fallback.
- [ ] Back cancels and restores focus to Categories without changing selection.
- [ ] Empty/failed groups leave a reachable actionable control.
- [ ] Edit categories, account and update actions remain reachable with the remote.
- [ ] Hidden/order preferences survive restart.
- [ ] Guide space improves without shrinking critical labels.
- [ ] Full-screen groups/channels keep their existing navigation; browsing drawer rules do not leak into playback.

## Stage 3 — Movies and Series

- [ ] Both sections retain Continue watching, My List and provider category rows.
- [ ] Rows load lazily; focus changes do not trigger an unbounded request burst.
- [ ] Posters, captions, progress and focus outlines are not clipped.
- [ ] Ordinary poster OK opens details; Continue watching OK resumes directly.
- [ ] Back from detail/playback restores the originating row, item and scroll.
- [ ] Hero changes do not steal focus or flash empty artwork during rapid browsing.
- [ ] Movie Play/Resume/Start over and My List still work.
- [ ] Optional Trailer remains reachable only when available.
- [ ] Series season switching and episode focus are clear.
- [ ] Series resume, watched/progress states and next-episode behaviour are retained.
- [ ] Missing metadata, missing artwork, no episodes, empty categories and all-hidden categories are handled.
- [ ] Home, global search and profile placeholders have not been added.

## Stage 4 — playback and remaining screens

- [ ] Live channel zapping, channel list and previous channel retain their mappings.
- [ ] Live Back follows groups → channels → picture → browse.
- [ ] Menu favourites target the correct playing/highlighted channel.
- [ ] VOD OK/Play-Pause, -10s/+30s and overlay keys retain their mappings.
- [ ] VOD progress saves on the current interval and when leaving playback.
- [ ] Switching Live TV to VOD stops the live session; no concurrent hidden stream is introduced.
- [ ] Returning from background preserves existing pause/resume behaviour.
- [ ] Playback overlays are readable over bright and dark footage.
- [ ] Buffering, failure and unknown duration do not show misleading progress.
- [ ] Login, account, category editors and update states use the same visual language.
- [ ] Passwords and credential-bearing URLs are absent from new UI/error copy.

## Final hardware and visual review

- [ ] Test on Wayne's actual Fire Stick and record model/OS/build.
- [ ] Compare repeated guide/poster navigation and open/close overlays against baseline.
- [ ] No repeatable new visible stalls, audio interruptions or unintended player recreation.
- [ ] Check output resolution/edges and larger text; essential actions remain reachable.
- [ ] Check readability from the sofa, including selected-but-unfocused items.
- [ ] Include real native screenshots of each completed screen.
- [ ] Mark any unavailable device checks as pending rather than passed.
- [ ] Record deviations from the spec and their reason.
- [ ] Wayne has reviewed the native result.

## Outcome

**Result:** Pending implementation.  
**Remaining checks / defects:** Pending.  
**Design deviations accepted by Wayne:** None recorded.  
**Hardware evidence:** None recorded.

