# TV Launcher

A custom home screen for Android TV (the Xiaomi-branded stick first, Fire OS sticks later). Built entirely in GitHub: every merge to `main` produces a new app file ready to install from the TV.

This is Phase 0 and 1 of the plan: the build pipeline, a dark home screen with a greeting and clock, and a dock of every app on the TV with Netflix, YouTube, Disney+, Prime Video, NOW and TiviMate first. Selecting a tile opens the app.

## Download link

The newest build is always at:

```
https://github.com/theolivecafewfd-code/tv-launcher/releases/latest/download/tv-launcher.apk
```

On the TV, type the short link instead: **`is.gd/mAyKiF`**. It points at the link above, which never changes, so it keeps working for every new build.

## Install on the TV

1. On the TV, install **Downloader** (by AFTVnews) from the Play Store.
2. Allow it to install apps: **Settings → Device Preferences → Security & restrictions → Unknown sources → Downloader** on.
3. Open Downloader, enter `is.gd/mAyKiF`, and choose **Install**.
4. Open **TV Launcher** from your apps list. It runs like a normal app until we make it the home screen in a later phase.

Installing a newer build the same way updates it in place, because every build is signed with the same key.

## Undo

Uninstall it like any app: **Settings → Apps → TV Launcher → Uninstall**. Nothing else on the TV is changed in this phase.

## How it's built

- Kotlin, Jetpack Compose and Compose for TV (`androidx.tv:tv-material`). Minimum Android 9 (API 28).
- No Google Play services, so the same app runs on Fire OS sticks.
- `.github/workflows/build.yml` builds the app on every pull request (so you can check it builds before merging) and publishes a release on every merge to `main`.
- **Signing:** `app/signing/tvlauncher.keystore` is kept in the repo on purpose. The app is only sideloaded onto your own TVs, and one fixed key lets each build install over the last. If it's ever published to a store, replace it with a private key held in GitHub secrets.
- No API keys or passwords for any service are stored in the code.
