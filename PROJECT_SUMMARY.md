# FutTV — Project Summary

> Last updated: 2026-04-20
> For Claude: read this first to get full context before touching any file.

---

## 1. Purpose

**FutTV** is an Android TV streaming app targeting Argentine sports fans.
It aggregates live sports streams from `pelotalibretv.su` / `pelotalibrestv.org` and shows upcoming/live events from TheSportsDB free API.
The user selects a channel → picks a server → the stream opens in a dedicated player.

---

## 2. Tech Stack

| Layer | Technology |
|---|---|
| Language | Kotlin 2.0.21 |
| UI | Jetpack Compose (Material3) — standard, NOT tv-material |
| Build | Android Gradle Plugin 8.7.0, compileSdk 35, minSdk 23 |
| Networking | OkHttp 4.12.0 + Jsoup 1.18.3 (HTML scraping) |
| Images | Coil 2.7.0 (`coil-compose`) |
| Video | ExoPlayer via Media3 1.5.0 + WebView fallback |
| Coroutines | kotlinx-coroutines-android 1.9.0 |
| TV Leanback | `androidx.leanback:leanback:1.0.0` (manifest only, no Compose TV lib) |

**Notable:** NO tv-foundation / tv-material Compose libraries — standard Material3 only.

---

## 3. Project Structure

```
app/src/main/java/com/futtv/app/
├── FutTVApp.kt                      ← Application class
├── MainActivity.kt                  ← Single activity, hosts HomeScreen
│
├── data/
│   ├── api/
│   │   └── TheSportsDbApi.kt        ← Sports events from TheSportsDB free API (key=3)
│   ├── model/
│   │   └── Models.kt                ← Data classes + ChannelRegistry
│   ├── repository/
│   │   └── SportsRepository.kt      ← Coordinates scraper + API, 30min cache
│   └── scraper/
│       └── PelotaLibreScraper.kt    ← Jsoup scraper for pelotalibretv.su
│
└── ui/
    ├── components/
    │   └── FocusableCard.kt         ← D-pad-aware focusable card component
    ├── home/
    │   ├── HomeScreen.kt            ← Main UI: events + channels + dialogs
    │   └── HomeViewModel.kt         ← Loads data, manages selected channel state
    ├── player/
    │   └── PlayerActivity.kt        ← Full-screen video player
    └── theme/
        └── FutTVTheme.kt            ← Colors, typography, MaterialTheme
```

---

## 4. Key Files — What Each Does

### `FutTVApp.kt`
- Application subclass; configures a **trust-all SSL OkHttpClient** (needed because
  TheSportsDB and streaming sites have cert issues on Android TV).
- Sets this same client as the Coil singleton via `Coil.setImageLoader()` in `onCreate()`.
- Exposes `httpClient` and `repository` as lazy vals for ViewModel access.

### `Models.kt`
```kotlin
data class StreamServer(label, url, index)
data class Channel(id, name, slug, logoUrl, colorHex, servers, sourceUrl)
enum class EventStatus { UPCOMING, LIVE, FINISHED }
data class SportEvent(id, homeTeam, awayTeam, league, sport, dateTimeUtc,
    thumbUrl, homeTeamBadgeUrl, awayTeamBadgeUrl, leagueBadgeUrl,
    status, homeScore, awayScore, channels)
sealed class UiState<T> { Loading, Success(data), Error(message) }
object ChannelRegistry  // hardcoded 10 channels + fallbackServers()
```

**ChannelRegistry** holds 10 channels: TyC Sports, DirecTV Sports, TNT Sports, ESPN,
ESPN Premium, Fox Sports, TV Pública, DeporTV, TUDN, DSports+.

Each channel has 4 fallback servers (in order of reliability):
1. `la14hd.com/vivo/canales.php?stream=$key`
2. `streamtp10.com/global1.php?stream=$key` (manual)
3. `streamtp10.com/global2.php?stream=$key` (auto — confirmed active domain)
4. `elcanaldeportivo.com/$key.php`

**Important stream keys** (confirmed from streamtpnew.com):
- Fox Sports Argentina → `fox1ar` (NOT `foxsports`)
- TV Pública → `tv_publica` (WITH underscore)
- ESPN → `espn1`, ESPN Premium → `espnpremium`
- TNT Sports → `tntsports`, TyC Sports → `tycsports`
- DirecTV Sports → `dsports`, DSports+ → `dsports2`

### `TheSportsDbApi.kt`
- Fetches today's events for 5 sports: Soccer, Basketball, American Football, Rugby, Tennis.
- Endpoint: `https://www.thesportsdb.com/api/v1/json/3/eventsday.php?d=DATE&s=SPORT`
- **Team badge URL construction** (no extra API calls needed):
  - `https://www.thesportsdb.com/images/media/team/badge/{idHomeTeam}.png`
  - `https://www.thesportsdb.com/images/media/league/badge/{idLeague}.png`
- Parses: `idHomeTeam`, `idAwayTeam`, `idLeague`, `strStatus`, `intHomeScore`, `intAwayScore`
- `strStatus` mapping: `"1H"/"HT"/"2H"` → LIVE, `"FT"/"AET"` → FINISHED, else → UPCOMING
- `fetchLocalTime()` converts UTC event time to device local time.

### `PelotaLibreScraper.kt`
- Fetches each channel's source URL from `pelotalibretv.su`.
- 4 extraction strategies (in order): "Opción N" links → numbered links → iframes → known provider URLs.
- Extracts logo from page images.
- Falls back to `ChannelRegistry.fallbackServers()` on error.

### `SportsRepository.kt`
- 30-minute in-memory cache for both channels and events.
- `loadAll()` fetches channels + events concurrently via `async/await`.
- Channels: falls back to ChannelRegistry static list on scraper failure.
- Events: returns empty list (not error) on API failure if cache is empty.

### `HomeViewModel.kt`
- `channelsState: StateFlow<UiState<List<Channel>>>` — scraper result
- `eventsState: StateFlow<UiState<List<SportEvent>>>` — TheSportsDB result
- `selectedChannel: StateFlow<Channel?>` — drives ServerSelectionDialog visibility
- Both loaders run in parallel, independent coroutine scopes.
- ChannelRegistry static fallback when scraping fails.

### `HomeScreen.kt` (784 lines)
Main composable tree:
```
HomeScreen
├── BackHandler → ExitConfirmDialog
├── AppHeader (gradient header + pulsing LIVE badge + refresh)
├── EventsSection → LazyRow of EventCards
│   └── EventCard (320×200dp)
│       ├── If thumbUrl: AsyncImage background
│       ├── TeamDisplay (badge URL → AsyncImage, fallback → TeamBadge circle)
│       ├── ScoreDisplay (if LIVE/FINISHED with score) or "VS"
│       ├── EventStatusBadge (EN VIVO pulsing / time / FINALIZADO)
│       └── Channel chips
├── ChannelsSection → LazyRow of ChannelCards (178×116dp)
├── ServerSelectionDialog (Dialog composable = separate window)
│   ├── BackHandler (consumes back BEFORE HomeScreen's handler)
│   └── ServerButton list → starts PlayerActivity
└── ExitConfirmDialog (back press on home root)
```

**D-pad navigation:**
- `FocusableCard` order: `onFocusChanged → focusable → clickable` (critical for TV)
- Initial focus is requested on first channel card via `FocusRequester`
- `Dialog` composable creates separate window → focus trapped naturally
- Each dialog has its own `BackHandler` that consumes back press before propagating

**Team badge display:**
```kotlin
var imageError by remember(badgeUrl) { mutableStateOf(false) }
// AsyncImage with onError → falls back to TeamBadge (colored circle with initials)
```

### `FocusableCard.kt`
Reusable TV-aware card with:
- Spring animation for scale (1.0 → 1.06 on focus, `dampingRatio=0.75, stiffness=380`)
- Shadow with `spotColor = FocusBorder` for blue glow effect
- Border: 1dp transparent → 2dp FocusBorder when focused

### `PlayerActivity.kt`
- Extends `AppCompatActivity`, landscape orientation
- **Back button**: `OnBackPressedCallback` registered in `onCreate()` (works with gestures AND TV remote)
- **Dual playback strategy**:
  - `.m3u8` / `.mpd` / `.ts` URLs → ExoPlayer (Media3)
  - Everything else → WebView
  - ExoPlayer error → falls back to WebView automatically
- **WebView config**: JS enabled, `MIXED_CONTENT_ALWAYS_ALLOW`, third-party cookies, `mediaPlaybackRequiresUserGesture = false`, `LOAD_DEFAULT` cache
- **SSL bypass**: `onReceivedSslError → handler.proceed()`
- **Overlay system**: top bar (channel name + current server) + bottom bar (server buttons)
  - D-pad always consumed when overlay visible
  - `webView.isFocusable = false` when overlay shown (prevents D-pad reaching WebView)
  - Auto-hides after 6 seconds
- **Error overlay**: "↻ OTRO SERVIDOR" → `tryNextServer()`, "✕ SALIR" → `finish()`
- **Extras**: `EXTRA_URL`, `EXTRA_CHANNEL_NAME`, `EXTRA_SERVER_LABEL`, `EXTRA_SERVERS_LABELS[]`, `EXTRA_SERVERS_URLS[]`, `EXTRA_SERVER_INDEX`

### `FutTVTheme.kt`
```kotlin
Background = Color(0xFF08090F)   // near-black with blue tint
Surface = Color(0xFF0E1118)
CardBackground = Color(0xFF131822)
PrimaryRed = Color(0xFFFF1744)   // bright red for LIVE / CTAs
AccentBlue = Color(0xFF2979FF)
FocusBorder = Color(0xFF2979FF)  // blue glow on focus
TextPrimary = White, TextSecondary = Color(0xFF8FA0B5)
TextMuted = Color(0xFF445064)
DividerColor = Color(0x10FFFFFF) // glassmorphism dividers
```

---

## 5. AndroidManifest

```
LEANBACK_LAUNCHER (TV entry point)
android:usesCleartextTraffic="true"   ← needed for HTTP streaming URLs
android:enableOnBackInvokedCallback="true"  ← Android 13+ back gesture support
MainActivity: singleTask, landscape
PlayerActivity: singleTop, landscape, hardwareAccelerated
```

---

## 6. Current State

| Feature | Status |
|---|---|
| Channel list (scraper + static fallback) | ✅ Working |
| Event list from TheSportsDB | ✅ Working (SSL fixed) |
| Team badge images from TheSportsDB IDs | ✅ Implemented |
| League badge images | ✅ Implemented |
| Event status (LIVE/UPCOMING/FINISHED) | ✅ Implemented |
| Live score display | ✅ Implemented |
| Server selection dialog | ✅ Working |
| WebView streaming | ✅ Working (some servers down by nature) |
| ExoPlayer for direct streams | ✅ Working |
| D-pad navigation | ✅ Fixed (correct Compose modifier order) |
| Back button | ✅ Fixed (OnBackPressedCallback + BackHandler) |
| Exit confirmation dialog | ✅ Implemented |
| Coil SSL trust-all | ✅ Fixed (Coil.setImageLoader in onCreate) |
| Server 1 (latamvidz1) | ❌ Domain appears down |
| Channel logos from scraper | ⚠️ Variable (depends on page structure) |

---

## 7. Known Issues / TODOs

1. **Some streaming servers are down** — content availability varies; user switches manually.
   App has "↻ OTRO SERVIDOR" auto-fallback in player.

2. **Channel logos often missing** — pelotalibretv.su pages don't always have logo images
   that match the scraper's strategy. Fallback shows colored initials box.

3. **TheSportsDB free API limitations**:
   - No live score updates (would need polling)
   - Some events lack `strStatus` → always shows UPCOMING
   - Badge URLs sometimes 404 for smaller leagues → AsyncImage error → TeamBadge circle fallback

4. **No persistent storage** — cache is in-memory only (30 min). App restart = fresh fetch.

5. **WebView SSL sub-resources** — `onReceivedSslError` handles main frame;
   sub-resource SSL errors can't be bypassed via WebViewClient (platform limitation).

6. **streamtp10.com servers** — Confirmed active domain from streamtpnew.com analysis.
   `global1.php` = manual embed, `global2.php` = auto-play embed.

---

## 8. Architecture Notes

- **No Room / no persistent cache** — everything fetched fresh per session
- **No Hilt / no DI framework** — ViewModel gets repository via `(application as FutTVApp).repository`
- **No Navigation component used** — two-screen flow handled by Activity (MainActivity → PlayerActivity)
- **Compose Dialog** used for server selection (not `AlertDialog`) — creates separate window which naturally traps D-pad focus
- The trust-all SSL client is intentional (streaming sites have self-signed/expired certs on Android TV's trust store)
