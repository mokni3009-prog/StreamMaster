# Stream Master — IPTV Player for Android TV

**Stream Master** is an IPTV player exclusively for Android Smart TV and
Android TV Box (phones are not supported). It plays Xtream Codes, Stalker
Portal and M3U playlists with a Netflix-like gold interface, remote-control
only navigation (D-pad + OK + Back + digits), and a dual-engine player
(ExoPlayer primary, VLC automatic fallback) tuned for stable live streaming.

- Version: **1.2.0** (versionCode 120)
- Package: `com.streammaster.tv`
- Language/stack: **Kotlin + Jetpack Compose for TV**, Media3 1.11.1,
  libVLC, Room, DataStore, WorkManager, Hilt, Retrofit/OkHttp/Cronet, Coil
- minSdk **21** (Android 5) — targetSdk/compileSdk **35**

## Open in Android Studio

1. Open Android Studio (Ladybug or newer) → **Open** → select the
   `StreamMaster/` folder.
2. Let Gradle sync finish (first sync downloads the Android Gradle Plugin
   8.5.2, Kotlin 2.0.21 and all dependencies from Google Maven / Maven
   Central — an internet connection is required).
3. Requirements: **JDK 17**, Android SDK with **API 35** (compile/target)
   and an Android TV emulator image (API 21+ for the low-end path, API 35
   for the current path) or a real TV/Box device.
4. Run: select the `app` configuration and press ▶ (or
   `./gradlew installDebug` from a terminal).

> **Note:** this project has never been compiled in the environment where it
> was assembled (no Android SDK / Gradle there). The first Gradle build in
> Android Studio is the real verification step — see "Known limitations".

## First run (demo mode)

With no playlist configured, the app seeds a **demo playlist** on first
launch: 7 channels, 8 films, 8 series (exact data from the approved
interactive prototype), playing public test HLS streams
(`test-streams.mux.dev`, `demo.unified-streaming.com`,
`cph-p2p-msl.akamaized.net`). Demo stream URLs are marked
`// TODO: replace with real server URLs` in `data/demo/DemoData.kt`.

Add a real playlist from **Profil → Ajouter une playlist** (Xtream Codes,
Stalker Portal with MAC address, or an M3U link). Up to 10 playlists.

## Server-side requirements (from GUIDE-playback-stability.md §D)

The player cannot fix a broken origin. For stable live playback the server
should provide:

- **Short, GOP-aligned segments:** 2 s GOP, 2–6 s segments (6 s HLS /
  4 s DASH), closed GOP, IDR at every segment start, identical IDR
  timestamps across renditions.
- **One CMAF (fMP4) mezzanine** feeding both LL-HLS and LL-DASH, chunked
  transfer (200–500 ms chunks) for low-latency live.
- **Multi-CDN / multi-URL failover:** two tested delivery paths per top
  channel, with token signing valid on both; HLS Content Steering or DASH
  multiple `<Location>` elements where supported.
- **CDN tuning:** short TTL on live manifests (seconds), long TTL on
  segments, origin shield + request coalescing.
- **Token hygiene:** short-lived signed URLs refreshed before expiry,
  valid on the backup CDN/pathway too.
- **EPG:** XMLTV from the provider (Xtream/Stalker) or an external XMLTV
  URL for M3U, matched by `tvg-id`.

## Architecture

- `ui/` — Compose for TV screens (18), ViewModels (17), theme (gold
  identity), navigation (`Routes`), shared components (built-in FR/AR/123
  keyboard, floating remote, PIN/selector/number-pad dialogs).
- `player/` — `PlayerManager` (ExoPlayer primary API 23+ / libVLC primary
  API 21–22), automatic fallback chain (retry → mirror → other engine),
  12 s stall timer, LoadControl/LiveConfiguration tuning, HttpEngine →
  Cronet → OkHttp, VOD-only cache, PreloadManager, intro/outro auto-skip
  (60/90/120/90 s demo constants, TODO(server)), instant next episode,
  timeshift, PiP, sleep timer, QoE logging.
- `data/` — Room database, Xtream/Stalker/M3U clients, XMLTV (SAX) parser,
  DataStore settings, WorkManager refresh (playlists 12 h, EPG 6 h),
  AlarmManager program reminders with deep link
  `streammaster://player/live/{channelId}`.
- `di/` — Hilt modules (`AppModule`, `PlayerModule`).

## Known limitations (first-build checklist)

1. **Never compiled yet** — run the first Gradle build in Android Studio
   and fix what it reports. The player agent flagged several Media3 /
   libVLC call sites for compile-time confirmation (see comments at the use
   sites, e.g. `DefaultTrackSelector` tunneling, `AudioOffloadPreferences`,
   `StuckPlayerException` package, `VLCVideoLayout.attachViews` arity).
2. **libVLC version:** `org.videolan.android:libvlc-all:3.0.24` is **not
   published** on Maven Central (the artifact has no 3.0.x at all —
   "3.0.24" is the VLC Android *app* version, not the library version).
   The catalog uses the newest stable 3.x library, **3.7.7**; libVLC 3.x
   API is stable so the code is unaffected.
3. **Cronet** resolves from Google Maven (not Maven Central), pinned to
   143.7445.0 — the Chromium line Media3 1.11.1 was built against.
4. **Real Xtream episode URLs — FIXED 2026-11-22:** the Xtream episode
   stream id is now carried through (`XtreamApi` → `Episode.streamUrl` →
   Room `EpisodeEntity.streamUrl` → player), and playback resolves the
   exact episode by (seriesId, code) via `getEpisodeStreamUrls`, so the
   S01E01 code shared by every series can no longer pick the wrong
   episode. Room schema bumped to version 2 (destructive-migration
   fallback already in place; the app is unreleased so no user data is at
   risk). Re-sync a playlist after updating to pick up episode URLs.
5. **QoE events go to logcat only** — no `RoomQoeStore` binding yet
   (the `QoeDao`/`qoe_events` table exists).
6. **Battery-optimization exemption** (`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`)
   is restricted by Play Store policy — it is intended for direct-APK
   distribution; the Settings screen opens the system screen directly.
7. `PlayerPool` (GUIDE B23) is not implemented; the single-standby
   `DefaultPreloadManager` slot covers the same need.
8. **EPG deepened 2026-11-22:** multi-day grid (tonight + the next nights,
   up to 7 days per the EPG window setting), a second XMLTV source per
   playlist (fills only channels the provider did not cover), and reminders
   that actually arm an `AlarmManager` alarm (they were stored but never
   scheduled before). Room schema version 3 (`Playlist.xmltvUrl` column +
   reminders primary key now includes `startMillis`; destructive-migration
   fallback in place, app unreleased). Xtream short-EPG coverage still
   depends on the provider (limit raised to 100 programs); reminder alarms
   are not yet re-armed after a device reboot.
9. **Instant DVR recording added 2026-11-22 (method 2, user-chosen):**
   REC button while watching live → the stream is captured to a `.ts` file
   in the app-private external `recordings/` folder, playable from the new
   Enregistrements screen. Room schema version 4 (recordings table).
   Known limits: HLS AES-encrypted segments and fMP4 segments are not
   supported; a raw-TS recording stops on a stream stall (OkHttp read
   timeout); recording a channel while watching another needs a second
   provider connection.
10. **All remaining features added 2026-11-22 (except QR login and
    payments):** scheduled recording from the EPG (once/daily/weekly, exact
    AlarmManager alarms + RecordingService), a user-chosen recording folder
    (SAF tree, recordings stored as content:// URIs), MultiView (4 live
    tiles, own ExoPlayer each, sound on the focused tile), watch-later
    (per profile), user profiles (per-profile history + watch-later;
    favourites and resume progress stay shared), kids mode (name-based
    category filter), app lock (parental PIN at launch), playback speed
    (0.5–2x), auto frame-rate switching (ExoPlayer, API 23+), external
    player (VLC/chooser), server status check, subscription-expiry alert
    (≤7 days), genre-based recommendations, voice search (system
    recognizer), auto-start on boot (some OEMs restrict it), JSON
    backup/restore (settings, playlists, favourites, history, watch-later,
    custom numbers — not the .ts files), in-app update check (needs the
    update JSON URL in Settings; download via DownloadManager). Room
    schema version 5 (`watch_later`, `profiles`, `scheduled_recordings`
    tables + `history.profileId`; destructive-migration fallback in
    place, app unreleased). **Not added, by Mohamed's decision:** QR-code
    login, in-app payments (Phase 1 is fully free), Catchup.
11. **Static review 2026-11-22 (before the all-features ZIP):** a
    read-only audit found and I fixed 5 compile-blocking errors —
    non-exhaustive `when` in `recordHistory` (RecordingItem), missing
    model imports in `Repositories.kt`, 4 screens importing `GhostButton`
    from the wrong package, missing `androidx.documentfile` dependency
    (catalog 1.0.1 + `implementation`), `BuildConfig` not generated
    (`buildConfig = true`) — plus 2 runtime bugs: the kids-mode exit PIN
    dialog never closed on success (now a `LaunchedEffect(kidsMode)`),
    and `startForegroundService` in `ScheduledRecordingReceiver` was
    unguarded on Android 12+ (now `runCatching`). The audit also
    reported 41 missing strings, 2 undeclared receivers and 3 missing
    permissions — all three were already added before the audit ran
    (verified: every `R.string.*` used exists in fr/en/ar, both receivers
    are in the manifest, all three permissions declared). The audit's 3
    minor notes were also cleaned up the same day: unused `HistoryDao`
    methods removed, the WatchLater delete-confirm key made unique per
    kind, and the deprecated `defaultDisplay` replaced with
    `DisplayManager.getDisplay`. Static checks only — the first Gradle
    build in Android Studio remains the real verification.
12. **Six differentiator features added 2026-11-22 (Mohamed's picks 2,
    3, 4, 5, 6, 9):** series-link recording (rule per channel+title, 6h
    WorkManager scan of the next 7 days of EPG, max 10 new airings per
    scan, `series_recording_rules` table), EPG enrichment (program dialog
    shows the matching VOD catalog poster/rating/genre/plot — no external
    API key; Xtream films carry no plot in the VOD list, so the EPG
    description is the fallback), offline VOD downloads (sequential
    queue, HTTP Range resume, app-private `downloads/` folder,
    `DownloadService` foreground dataSync; **HLS (.m3u8) URLs are
    refused**; downloads do not save a resume position and do not enter
    history), smart failover (backup URLs computed at playlist refresh by
    matching normalized channel names across playlists, max 3 per
    channel — **re-sync playlists after updating**; "Source de secours"
    toast + gold badge in the player), Match Center (ESPN public
    scoreboard API, no key, 8 leagues, 60s refresh; the API is
    unofficial and may change), trailers (Xtream `youtube_trailer` for
    series, else a YouTube search; always opened externally). Room
    schema version 6 (`series_recording_rules`, `downloads`,
    `FilmEntity.trailerUrl`, `SeriesEntity.trailerUrl`; destructive
    migration fallback in place, app unreleased). **Opt-in toggles added
    2026-11-22 (Mohamed's request):** smart failover and the in-player
    Match Center are OFF by default and enabled from player buttons that
    persist in DataStore until turned off from the same button —
    "Bascule auto" gates the mirror branch of `handleFailure`, and
    "Scores" (✓ when on) enables Match Center and opens the scores
    dialog; closing the dialog with Back keeps it enabled. The sidebar
    "Matchs en direct" screen stays available regardless. Static checks
    only — every `R.string.*` used exists in fr/en/ar (295 used, 335
    defined, no duplicates) and all `PlaybackItem` `when`s cover the new
    `DownloadItem` subtype.
13. **Phone web remote added 2026-11-22 (local version, QR pairing,
    Mohamed-approved):** embedded NanoHTTPD server (2.3.2) inside the app
    + ZXing-core QR codes (3.5.3); opt-in via the new "Télécommande"
    sidebar screen (QR + 6-digit code + URL + "Nouveau code"), server
    follows the persisted `remoteEnabled` setting and serves
    `assets/remote.html` on the first free port in 8080..8090. The page
    uses the app's gold visual identity (SPEC §9) and carries every
    button of a physical remote (power, search, menu, D-pad + OK, back,
    home, digits 0-9, info, guide, TV, Ch±, prev/next, volume/mute,
    play/pause, ±10s, stop, REC, lists, live, 4 colour shortcuts, audio,
    subtitles, source) plus a phone-keyboard text field and a live
    now-playing line (2s poll). Navigation keys are replayed as real
    Android key events; volume via AudioManager; player/nav actions via
    `RemoteBus`; search text via `RemoteTextStore`. Power leaves the app
    for the TV launcher (an app cannot switch the TV off); Source is a
    TV function and the page says so. Limits (v1): same-Wi-Fi only, the
    server dies with the app process (no background service), digits work
    in the player only, remote text appends to search only while the
    search screen is open. Static checks only — every `R.string.*` used
    exists in fr/en/ar (306 used, 346 defined, no duplicates).

14. **Goal alerts added 2026-11-22 (opt-in, off by default, §4.33):**
    foreground `GoalAlertService` (dataSync) polls the ESPN scoreboard
    every 30s for ALL of today's matches in the 8 Match Center leagues;
    every new goal (key = match + minute + scorer + score, persisted in
    a separate `goal_alerts` DataStore so it is never announced twice,
    first poll after enabling only seeds) shows a small gold popup in
    Shell over ANY screen — scorer, minute, teams + live score, league —
    auto-dismissed after 10s (tap dismisses sooner, never takes focus;
    simultaneous goals queue). When the app is in the background the
    same alert arrives as a high-importance system notification (tap
    opens the app). Toggle in the Match Center header and in Settings
    (both write `goalAlerts`); `BootReceiver` restarts the service after
    a reboot when enabled. Limits: ~30s max delay after the goal (ESPN
    poll, no push API); scorer/minute blank when ESPN omits them; the
    service needs the notification permission for background alerts on
    Android 13+. Static checks only — every `R.string.*` used exists in
    fr/en/ar (313 used, 353 defined, no duplicates).

15. **Maximum speed added 2026-11-22 (ON by default, §4.34):**
    ExoPlayer uses ALL available bandwidth — highest supported rendition
    forced (`setForceHighestSupportedBitrate`), ABR bandwidth fraction
    1.0 (was 0.7), faster live-edge upshift (0.5 vs 0.75), bigger
    prefetch buffer (30s/90s vs 25s/60s), initial bitrate from last
    measured value or 8 Mbps fresh. Live gold badge in the player shows
    the measured speed ("12.5 Mb/s", 1s updates from `BandwidthMeter`).
    Setting `maxSpeed` in Settings (toggle applies to rendition choice
    at once; fraction/buffers apply from next player creation). Manual
    quality choice still wins; low-RAM decode cap (720p/5 Mbps) stays
    (device limit, not network). VLC keeps its internal ABR. Honest
    ceiling: ISP speed, device network and the channel's own bitrate —
    the app removes its own throttling, it cannot exceed those.
    Static checks only — every `R.string.*` used exists in fr/en/ar
    (314 used, 354 defined, no duplicates).

## Version history

- **1.2.0 (2026-11-19)** — Interactive prototype approved as final;
  first Android project: all screens, dual-engine player, data layer,
  components. Build/config module completed so the project opens in
  Android Studio.
