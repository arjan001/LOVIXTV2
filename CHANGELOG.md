# Changelog

## 1.0.28

- **Navbar redesign** — single horizontal scrollable row with gender dropdown (icons + film counts), covers/stills toggle, heart-shaped favorites button with pink count badge, and refresh button.
- **Genre dropdown menu** — each option shows icon, name, and film count; includes "Favorites" option with pink glow effect.
- **Favorites toggle** — local state filters movies to favorites only; heart icon changes between ♡/♥ with pink accent when active.
- **Clear filters** — pill-shaped "Clear all ×" button resets all filters: selected tag, search query, favorites filter, and gender filter.
- **Covers/Stills aspect ratio** — poster cards switch from 2:3 (portrait) to 16:10 (landscape) with adjusted grid column width.
- **Multi-field search** — search across name, cast, genre, and plot fields; supports cast name search and genre filtering.
- **Navbar width** — controls container takes ~75% width, matching design where navbar doesn't cover full width.

## 1.0.27

- **Tag filter fixed** — searching for "blonde" or "threesome" now correctly filters all matching films with proper poster thumbnails.
- **Favorites tab removed from sidebar** — redundant Favorites tab removed from sidebar navigation; favorites toggle is now only in the navbar.
- **Cast images consolidated** — all cast/actor circular images are displayed in one consolidated row after the tags section.
- **Active tag highlighting** — selected tag is highlighted in orange with a clear filter button to reset.

## 1.0.26

- **Version bump** — new release to trigger updated APK build via GitHub Actions.

## 1.0.25

- **Whisper subtitles fixed** — subtitle service now binds to `0.0.0.0` on the VPS so the Android
  app can reach it over the network. Subtitle server URL is configurable in app settings, defaulting
  to `vpn.istreamix.com`.
- **Subtitle languages** — focused on French, Spanish, and Dutch (Belgium) as requested. English
  removed from the default subtitle track list.
- **Subtitle caching** — subtitles are cached locally after first download, so they play even when
  the server is temporarily unreachable.

## 1.0.24

- **Dynamic tag counts** — tags are no longer hardcoded with static counts; the app scans all movies
  and series at startup and computes how many titles match each tag name (genre, cast, plot, name).
- **Tag filter fixed** — clicking a tag now correctly filters movies because the ViewModel's
  `selectTag()` is called. Tag search checks genre, cast, plot, and title fields.
- **Pill-shaped tags** — tag chips are now smaller, pill-shaped (16dp corner radius), with tiny
  padding and labelSmall text. Tags with zero matches are hidden.
- **Horizontally scrollable tags** — tags shelf uses LazyRow for smooth left-to-right scrolling.

## 1.0.23

- **Search navigation fixed** — pressing Enter or clicking a suggestion now opens the first matching
  movie directly. Search results include tags and actors alongside movie titles.
- **Continue Watching layout fixed** — the section now appears at the top of the home page in its
  own row, above the main film grid, without breaking the layout.
- **Hardcoded tags added** — 100+ tags with counts (Blonde, MILF, Anal, etc.) are always available
  on the home page navbar, even when the API returns no tag data.
- **QR code login** — Smart TV users can now scan a QR code from their phone to sign in instead of
  typing credentials with a remote.
- **On-screen keyboard OK button** — added an OK/Enter key to the virtual keyboard for confirming
  search results.

## 1.0.22

- **Fixed video player remote controls** — replaced `Key.DpadLeft`/`Key.DpadRight` with the correct
  `Key.DirectionLeft`/`Key.DirectionRight` constants so the VOD player compiles and responds to
  TV remote left/right arrow keys for seeking ±10 seconds.

## 1.0.21

- **Modern inline search** — the home page search bar now opens a QWERTY keyboard at the bottom
  of the screen with autocomplete suggestions as you type. Connected to the ViewModel for live
  movie and series search results.
- **Removed shelf labels** — the Tags and Cast shelves no longer display "Tags" or "Cast" headers;
  they show only the actual tag chips and circular cast images. Shelves are hidden when empty.

## 1.0.20

- **Fixed video playback** — when a movie's streamUrl is empty, the app now builds the URL from
  source credentials before navigating to the player.
- **Remote-controlled player** — removed rewind/forward/audio buttons from on-screen controls;
  left/right DPAD arrows now seek ±10 seconds, center/enter toggles play/pause. Only the progress
  bar, play/pause, and subtitle button remain on screen.
- **Tags and cast always visible** — the home page tags shelf and cast circular images are now
  always displayed below the search bar, even when the API returns no data yet.
- **Series screen improved** — added tags and cast shelves to the Shows tab for consistency.

## 1.0.19

- **Fixed build failure** — removed `CategoryChips` call and unused `categories` state from
  MoviesScreen that were causing "Unresolved reference" compilation errors after a concurrent
  merge. The category filter chips are no longer needed on the home page navbar.

## 1.0.18

- **Fixed compilation errors** — removed duplicate `import android.util.Log` in LovixTvViewModels.kt
  and duplicate `val selectedTag` declaration in VodScreens.kt that were causing build failures.

## 1.0.17

- **Removed shelf labels** — the home page Tags and Cast shelves no longer display "Tags" or
  "Cast & Creators" headers; only the actual tag chips and circular cast images appear.
- **Removed "All" section header** — the movie and series grids start directly with poster cards
  instead of an "All" label, eliminating the UI glitch where a card slot was skipped.
- **Changed default login server** — primary LOVIX provider is now `vpn.istreamix.com` so users
  only need to enter username and password on first launch.
- **Conditional shelf display** — Tags and Cast shelves only render when data is available,
  preventing empty rows on the home page.

## 1.0.16

- **Removed "Recently Added" section** — the Movies and Shows home screens now show only Continue
  Watching and All films/series, eliminating the redundant recently-added carousel.
- **Tags shelf always visible** — placed immediately after the search bar, tags now appear even when
  the LOVIX API returns none: the app aggregates genre and year tags from the movie/series catalog
  with movie counts (e.g., "Action 142", "2024 56").
- **Cast shelf always visible** — rounded cast member images appear below the tags shelf. If the
  LOVIX API provides no cast data, TMDB is used to fetch profile photos for up to 20 movies.
- **Navbar layout matches design** — search bar → tags with counts → cast with rounded images →
  scrollable content (Continue Watching → All films grid).

## 1.0.15

- **Fixed compilation errors** — added missing `TmdbClient` to the dependency graph so the home
  screen can fetch cast images from TMDB when the LOVIX API returns no people data. Fixed the
  `recentlyAdded` reference to use `recentlyAddedMovies.value` (StateFlow) instead of calling
  `.take()` on a Flow, which caused type mismatch errors.

## 1.0.14

- **Fixed app crash on scroll** — replaced nested LazyColumn + LazyVerticalGrid with a single
  LazyVerticalGrid in both Movies and Shows screens, eliminating the Compose nested-scrollable crash.
- **Cast images from TMDB** — the TMDB API key is now pre-configured so cast member photos are fetched
  automatically. When the LOVIX API returns no cast images, TMDB provides profile photos for the
  circular cast cards on the home screen and movie detail pages.
- **TMDB cast enrichment** — movie detail pages now use TMDB to fill in cast member images when the
  provider doesn't supply them, ensuring the cast shelf always shows photos.

## 1.0.13

- **Navbar cleaned up** — removed category filter chips ("Rated R", "For Adults") so only the
  search bar and provider chips remain. The home screen shows all movies without category filtering.
- **Home screen restructured** — three sections in order: Continue Watching carousel, Recently Added
  carousel, then all films in a vertical-scrolling adaptive grid (6–7 cards per row on TV screens).
- **Series screen matched** — Shows tab uses the same layout: Continue Watching, Recently Added,
  then all series in an adaptive grid.

## 1.0.12

- **Cast images on movie detail page** — the LOVIX API is now called when opening a movie detail
  to fetch cast member photos and tags, so the circular cast cards and tag chips appear correctly.
- **Home screen restructured** — the Movies home now shows "Recently Added" as a horizontal carousel
  first, followed by a "Trending" section with all movies in a vertical-scrolling adaptive grid
  (5–7 cards per row on TV screens).
- **Cast & Tags shelves** — cast member circular cards and tag chips with counts are displayed
  below the navigation bar on the Movies home screen, cached after the first sync for instant load.

## 1.0.10

- **Fixed Kotlin serialization build error** — the app now compiles correctly by using explicit
  `ListSerializer` for Person and Tag JSON serialization, resolving the persistent "Cannot infer
  type" and "SerializationStrategy was expected" compilation errors.

## 1.0.9

- **Cast & Tags shelves restored** — the Movies home screen now displays cast member photos in
  circular cards and tag chips with counts. Data is cached locally after the first sync so the
  sections appear instantly on subsequent launches even if the API is unreachable.
- **APK update installation fixed** — the update installer now handles signature mismatches on
  emulators by requesting uninstall of the old package first, so the update no longer fails midway.
- **Whisper subtitles via WebVTT** — the subtitle service now serves WebVTT files directly via
  `GET /subtitles/{mediaKey}.vtt?lang=en`, matching what the Android app expects. Subtitles are
  cached locally and work offline when the server is down.

## 1.0.8

- **LOVIX logo as app icon** — the app now displays the LOVIX brand logo when installing, updating,
  and on the Android TV home screen, replacing the generic launcher icon.
- **Improved URL handling** — entering a server URL with or without `http://` / `https://` prefix
  now works correctly; the app strips and re-adds the scheme to avoid double-prefix errors.

## 1.0.7

- **Movies carousel sections restored** — the Movies home screen now displays Continue Watching,
  Recently Added, Recommended, and genre-based rows (scrollable left-to-right carousels) instead
  of a flat grid.
- **Subtitle caching for offline playback** — Whisper-generated subtitles are downloaded and cached
  locally on first use. If the server is down, cached subtitles continue to play. Cache expires
  after 7 days and refreshes in the background.
- **Subtitle format detection** — the player now correctly detects WebVTT vs SRT subtitle format
  from the file extension, so both formats render properly.

## 1.0.6

- **Whisper AI subtitles enabled** — VOD movies now display auto-generated subtitles in English,
  Dutch, Spanish, and French. The subtitle service uses Whisper small model for accurate
  transcription and translation.
- **Subtitle language selector** — the player's CC button shows all available subtitle tracks
  including Whisper-generated ones, so you can switch between languages during playback.
- **APK update installation fixed** — the in-app update installer now handles signature mismatches
  gracefully, so updating from a debug build to a release APK on emulators works correctly.

## 1.0.5

- **LOVIX logo in update dialog** — the update prompt now displays the LOVIX brand logo so users
  recognize the app at a glance when updating.

## 1.0.4

- **Prereleases included in update checks** — the app now detects beta and preview releases, not
  just stable versions, so you never miss an update.
- **Cast members and tags fetched from LOVIX API** — movie detail screens display cast photos in
  circular cards and tag chips when the provider returns that data.
- **Favorites tab restored** — sidebar navigation includes a Favorites tab showing only starred
  movies.
- **URL normalization fixed** — pasting a URL with `http://` or `https://` no longer creates
  double-prefixed addresses, and HTTPS scheme is preserved.
- **Responsive movie grid** — poster cards adapt to TV screen width (4–6 columns on 1080p/4K).
- **Fixed Room database schema errors** — removed stale schema files from the old package name.

## 1.0.3

- **Fixed compilation errors** that prevented the app from building — LoginScreen import and
  VodDetailScreens model references are now correct.
- **Fixed resource translation errors** — 120 lint errors from 30 locale files referencing string
  keys missing from the default English locale are resolved.
- **Stabilized CI builds** — stale Gradle build cache no longer causes false compilation errors
  on continuous integration.

## 0.11.8

- **Edit a saved provider.** Settings → Providers now has an **Edit** button on each provider, so you
  can fix a server address, password, MAC or name in place instead of removing and re-adding it —
  your favourites, hidden channels and per-provider settings are kept.
- **Stalker portals: fewer dropped sessions.** A portal that occasionally rejected a call
  mid-session (channels loading only "sometimes") now gets one automatic re-handshake and retry, so
  an intermittent refusal becomes a silent re-auth rather than an empty channel list or a channel
  that won't tune.
- **Player controls stay clear of the system bar off-TV.** On phones and tablets the bottom control
  bar (subtitles, audio, quality, aspect ratio) could be drawn under the navigation bar and cut off;
  it's now inset above it. No change on Android TV.

## 0.11.7

- **Stalker portals: closer to a real box.** Building on v0.11.6, OpenTV now sends the rest of the
  identity a real MAG set-top box presents at login — the auth token in the session cookie (not just
  the header), a hardware-version hash, a timestamp and the box's API signature — and it probes a
  couple more portal paths. Some Ministra/Stalker panels only return the channel list when they see
  all of this, so lines that connected but showed no channels have a better chance of loading. If a
  portal still comes up empty, please open an issue and say which panel software it runs.

## 0.11.6

- **Stalker portals: full set-top-box identity.** OpenTV now authenticates to Stalker/Ministra
  portals the way a real MAG box does — sending the device serial, `device_id`, a signature and a
  metrics blob derived from your MAC, instead of the MAC on its own. Many portals won't hand over
  the channel list or stream links to a box that only presents a MAC, which is why some lines that
  worked in other apps came up empty in OpenTV. This lets those portals authorise OpenTV. If a
  portal still fails, please open an issue and say which one — it helps a lot.

## 0.11.5

- **Add a provider from your phone or laptop.** The "Manage on phone or laptop" web page now has an
  **Add a provider** section — pick Xtream, M3U or Stalker portal, fill it in with a real keyboard,
  then Test and Add. Channels load straight onto the TV, so you never have to type a server address
  or a MAC on the remote again.
- **Fixed the "camera won't scan it?" web address.** The manage screen was printing that link without
  its access token, so typing it into a browser hit a dead "Not found". It now shows the full working
  URL, and the token is short enough to type.


## 0.11.4

The big DVR release. Recordings behave like Sky Q, you can record straight to a NAS, watch a
recording while it's still taping, and the whole interface now speaks 30 languages.

### Recording & DVR

- **Watch a recording while it's still recording.** Start playing something the moment it begins
  taping — OpenTV reads the growing file straight off disk, so it costs **zero extra connections**
  to your provider. On a single-stream account that's the difference between "wait until it's
  finished" and "watch now". Fast-forward and rewind work within whatever's been taped so far.
- **Record straight to a NAS.** Point recordings at a network share over SMB (Settings →
  Recordings → NAS, or set it up from the phone/laptop web manager) so a cheap box isn't boxed in
  by its own storage — and every OpenTV in the house can reach the same recordings.
- **Single-connection auto-switch.** On a one-stream provider, if a recording is due to start on
  another channel, OpenTV moves the screen onto that recording as it begins — with a **30-second
  warning** first and a *Keep watching* button if you'd rather not. No more "why did my live stream
  just cut out?" (Recordings → Recording behaviour → *Auto-switch when recording starts*.)
- **Clash handling with multi-provider fallback.** Book two overlapping recordings and, if more
  than one of your providers carries that channel, OpenTV records each from a **different provider**
  so neither is cut. On a single provider it flags the clash instead.
- **Recording padding.** Start each recording a minute early and run a few minutes past the listed
  end, so a late kick-off or an overrun isn't clipped. Adjustable per side.
- **Series links show the next episode.** A series link now tells you exactly when it next records.
- **Storage readout.** See how much space recordings use and how much is left.

### Live TV

- **Pause & rewind live TV** *(experimental, opt-in).* Holds the last couple of minutes so you can
  pause and jump back. Off by default — it uses more memory.

### Languages

- **30 languages.** The whole interface is translated into Spanish, French, German, Italian,
  Portuguese, Dutch, Polish, Russian, Turkish, Arabic, Simplified Chinese, Japanese, Korean, Hindi,
  Swedish, Danish, Finnish, Norwegian, Czech, Greek, Romanian, Hungarian, Ukrainian, Indonesian,
  Thai, Vietnamese, Bulgarian, Slovak, Croatian and Persian. Settings → Language (each listed in
  its own name), or leave it on *System* to follow your device.

### Fixes & polish

- Smoother watch-while-recording, especially from a NAS: playback now keeps a buffer behind the
  live edge so a network share doesn't stutter at the write head.
- Back never lands you on a stray live channel or drops you out by accident — coming back from a
  recording returns to your recordings, and Back on the home screen asks before it exits.
- Password fields are masked, with a show/hide toggle.
- Recording stability: NAS write timeouts and reconnect handling, so a quiet network doesn't
  silently freeze a capture.

### Notes

- Everything stays on your device — provider logins never leave the box.
- Free and open source (GPL-3.0): https://github.com/opentvproject/opentv


## 0.10.0

- **Movies & Shows, redesigned.** A Plex-style layout with big artwork, cast & director, "more with
  this cast" rows, and cleaned-up titles (no more "NF -" / "(KR)" junk). Optional: add your own free
  TMDB key in Settings → Metadata and OpenTV fills in any posters, backdrops, cast or synopses your
  provider left blank — the key stays on your device.
- **No more stuck "Loading movies & shows".** The movies/series catalogue is cached, so it loads
  instantly on later launches instead of re-downloading every time — and the live preview no longer
  stutters while it loads.
- **Record to a USB / external drive.** Settings → Recordings → USB, pick a folder on a plugged-in
  drive, and recordings write straight there (no storage permission needed) and play back in-app.
- **Recordings screen overhaul.** Scheduled recordings show *when* they'll record and sit in their
  own "Scheduled" section; failed ones have a **Retry**; each is tagged to a profile; and bookings
  re-arm on every launch so a force-stop or app update can't quietly drop them.

## 0.9.0

- **Manage your channels from a phone or laptop.** A new "Manage on phone or laptop" screen in
  Settings shows a QR code and a link — open it on any device on your wifi and you get a proper web
  page to browse, **rename**, hide, favourite and **reorder** channels with a real keyboard and
  mouse. Changes apply to the TV instantly. Local-only: the TV is the server, nothing touches a
  cloud.
- **Recording keeps going when you leave the app or the box sleeps.** OpenTV can now ask Android to
  exempt it from battery optimisation (Settings → Recording → "Recording in the background"), which
  is what keeps recordings running in standby and when you switch away — swiping OpenTV out of
  recents no longer stops a recording either.
- **Rename channels.** Give a channel your own name; it sticks and survives a guide refresh (from
  the web manager for now).
- **Tidier update notes.** The "update available" prompt shows a clean summary of what's new instead
  of build metadata.

## 0.8.0

- **No more endless "Loading channels".** If a provider fails to load or comes back empty (a wrong
  login, a dead server), the guide now shows a clear error with **Retry** and **Provider setup**
  buttons instead of spinning forever.
- **Set up on a phone without the QR dance.** Installing on a phone now shows the login form
  directly so you can type your details in; the QR code is only offered on a TV, where typing is the
  painful part.
- **Paste into the setup fields.** The URL, username and password fields — and the on-screen
  keyboard — now have a **Paste** button, so a copied Xtream line drops straight in.
- **MPEG-TS or HLS, your choice.** Each Xtream provider now has a **Stream format** toggle. If your
  panel only serves `.ts` and channels wouldn't play, switch to MPEG-TS and they will. Defaults to
  HLS, so nothing changes unless you need it.
- **Parental controls scrolls.** The hidden-categories list is reachable to the bottom now, however
  many categories your provider has.
- **A real Channel Manager.** Settings → Channels is now a browsable, two-pane manager: pick a
  category on the left, see its channels on the right, hide or favourite each one — including
  hidden channels so you can bring them back. With more than one provider, a **source filter** keeps
  them separate instead of merged into one list.

## 0.3.0

- **Profiles.** Add local profiles (just a name — no accounts) from the person icon in the top
  menu, and switch between them. Each profile keeps its own resume points and watched state, so
  your half-watched film doesn't show as watched on someone else's profile. Existing resume points
  become the default "Me" profile's — nothing is lost.
- **Continue Watching.** Movies and Shows now open with a "Continue watching" shelf for the active
  profile — pick up your last film or episode where you left off, with a progress bar on each.
- **Local sync (no servers).** Settings → Sync copies your continue-watching between two OpenTV
  devices over your own wifi: one device shares behind a six-digit code, the other receives. It's
  local-only — nothing leaves the house — the household answer to the cloud sync that died with
  Viewella.

- **Proper player controls.** A single control bar slides up from the bottom with play/pause,
  rewind/forward (where the stream allows it), and pickers for **subtitles, audio track, quality
  and aspect ratio**. It hides after a few seconds and any remote button brings it back —
  nothing is ever left painted permanently over the picture.
- **Captions that actually show.** Subtitles are now chosen from the real tracks in the stream
  (and can be turned off), instead of a blind on/off that often left the renderer enabled but
  nothing on screen. Audio-track switching works the same way for multi-language streams.
- **Page through the guide.** Earlier / Now / Later buttons above the guide move the timeline
  forward and back, so you can see what's on later this evening with a remote.
- **Live preview in the guide.** The highlighted channel now plays, muted, inside the preview
  pane. It uses a single player that stops the instant you go full-screen or leave the guide —
  no second decoder — and it can be turned off in Display & playback for older boxes.
- **Channel manager.** Settings → Channels: search for a channel and hide it from the guide (or
  favourite it). Hidden channels drop out of Live TV and search but stay here, greyed, so you can
  bring them back. Hiding covers every quality variant of a channel at once.
- **Parental controls.** Set a 4-digit PIN and mark categories as hidden — they drop out of the
  guide, All channels and search until you unlock them (and with a PIN set, the parental screen
  itself is locked). In Settings → Parental controls.
- **Unified search with an on-screen keyboard.** Search now has its own screen with a d-pad
  keyboard (no more "type on your phone") and covers **channels, movies and shows** together, with
  live results as you type. Reachable from the top menu.
- **Loading indicators for Movies and Shows.** Both now show a spinner while the catalogue is
  syncing instead of a premature "nothing here".
- **Settings and Search in the top menu.** Both now live top-right, next to Live TV / Movies /
  Shows, instead of being buried behind the guide's icons.
- **A single Settings screen.** One Settings hub — Providers (add / remove / re-test sources), TV
  guide, Display & playback, Parental controls, and About (version, a manual update check,
  licence and links).
- **Dark / light / follow-system.** A new appearance setting. TV still defaults to dark; pick
  Dark or Light to force it on any device.
- **Quality picker only when there's a choice.** No more "Quality: Standard" on single-quality
  channels; the picker appears only when a channel actually has multiple qualities.
- New **Display & playback** settings screen (the sliders icon in the guide).

## 0.2.0

- **Guide detail pane** — highlighting a channel now shows its logo, what's on now with
  start/end times and a live progress bar, a synopsis when the guide carries one, and what's
  on next. Press to watch full-screen.
- **Live progress fill** on the current programme in the guide grid, and a focus highlight on
  the highlighted channel.
- **Fix: never asks for your provider again.** A cold launch could briefly decide "first run"
  before your saved sources had loaded and drop you on the setup screen — it now waits for the
  sources to load before deciding what to show.
- **Fix: no second video decoder on the guide**, which was locking up low-end boxes (a
  Chromecast could freeze). The detail pane is logo + guide info; the one decoder lives
  full-screen.

## 0.1.0

First public release.

- Xtream Codes and M3U/M3U8 sources
- Streaming XMLTV guide parsing with incremental, non-destructive sync
- Live TV playback on Media3/ExoPlayer with IPTV-tuned retry behaviour
- Movies and series catalogue
- Favourites, categories, search
- In-app self-update for sideloaded installs
- Single APK for Android TV, Fire TV, phones and tablets
