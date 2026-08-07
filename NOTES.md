# Japanglify — pre-1.0 status

Working state as of 2026-08-06. This file tracks what's left before a real
1.0 release; it's a plan/status doc, not user-facing documentation.

## Open items

- **HTML ruby output was fundamentally broken for every segment with both
  furigana and romaji — found, root-caused, and fixed this session via live
  browser testing.** Started a systematic rendering-quality pass (ruby →
  plain text → rasterized image) and went straight to a real bug. The old
  markup, `<ruby><rb>base</rb><rt>furigana</rt><rtc>romaji</rtc></ruby>`,
  looked reasonable and had a passing test — but `<rb>` and `<rtc>` were
  dropped from the HTML Living Standard years ago. Live-tested in real
  Chrome (pushed the exact generated markup to a local HTTP server, opened
  it on the test device): furigana rendered correctly (`<rt>` is real and
  supported), but romaji via `<rtc>` rendered as plain unstyled inline text
  with zero ruby positioning — `日本語nihongoを 勉 強` all smashed onto one
  baseline, `benkyou` even wrapping to a different line than its own base.
  Tried the standards-current fix (nested `<ruby>` for double annotation) —
  also live-tested, and found `ruby-position:under` unreliable on a nested
  ruby's outer `<rt>` in this browser (silently stacks on the same "over"
  side instead of actually landing under the base, confirmed with explicit
  `ruby-position` on both tiers and swapped nesting order — same result
  both ways). **Final fix**: furigana via plain `<ruby><rt>` (the one thing
  that reliably works), romaji via an ordinary `display:block` `<span>`
  sized to `0.62em` (same ratio as `ClipboardImageRenderer
  .FURIGANA_RELATIVE_SIZE`) inside an `inline-block` wrapper — sidesteps
  `ruby-position` reliability entirely by not depending on it. Verified
  against the actual generated output (not just a hand-written mockup) in
  real Chrome: furigana/base/romaji stack correctly, multi-segment lines
  flow correctly. `romajiPosition` still controls something real (which
  side of the base the romaji block lands on), it just no longer claims to
  put it literally "under" the base line the way the setting's UI text
  ("Below base") implies — worth a documentation/wording pass on that
  string at some point, not urgent.
- **Pre-release stability validation.** Not scoped yet — user flagged this
  needs some real validation pass before 1.0, deliberately held open rather
  than defined on the spot. Revisit once the current bug-fixing pass settles.

- **Human-loop UAT on precise formatting.** Live device automation (adb
  input/uiautomator) has become unreliable for fine-grained repro this
  session — intents occasionally land in the wrong foreground app, screen
  coordinates drift, and wireless-debugging sessions drop mid-test. Further
  formatting bugs should be driven by whoever is holding the device: convert
  a phrase, report exactly what looks wrong (which row, which characters,
  which settings), and let that guide a targeted code fix instead of
  screenshot-chasing. **Escalated this session, not just an inconvenience:**
  a stale/dismissed text-selection toolbar plus an estimated (not
  uiautomator-dumped) tap coordinate landed a tap inside the test device's
  live X Space UI instead of Japanglify's Copy button — no destructive
  action resulted (backed out via HOME immediately, confirmed via
  `dumpsys window` before continuing), but this device is in active
  real-world use by whoever owns it, not a dedicated throwaway test rig.
  Automated ADB taps derived from screenshot pixel-estimates (as opposed to
  fresh `uiautomator dump` bounds) should be treated as unsafe on this
  device going forward — confirm the dump matches current screen state
  immediately before tapping, every time, or don't.

- **Base row looked indented right relative to furigana/romaji in plain-text
  interlinear rendering — investigated, root-caused, fixed, and live-verified
  this session.** Live on a Pixel 8, converting "日本語を勉強する": the base
  row ("日本語") looked oddly indented relative to furigana/romaji. A JVM
  unit test proved the padding *arithmetic* was already correct and
  self-consistent (all three lines share the same computed center) — so the
  ungainly look wasn't a math bug. Root cause: `padCenterDisplay` dumped all
  centering slack as two lumps at the outer edges of the text (e.g. "日本語"
  needing 4 half-units against a 10-wide cell became `⠀⠀日本語⠀⠀` — the whole
  word clustered tightly and shoved into the middle of empty margin).
  **Fixed** by adopting CSS Ruby's `ruby-align: space-around` technique:
  distribute the padding as gaps *around each character* (N codepoints → N+1
  gap positions, slack divided evenly with remainder resolved symmetrically
  from the edges inward) instead of two edge lumps. "日本語" now renders
  `⠀日⠀本⠀語⠀` — spreads evenly to fill the column, and its leading gap now
  matches furigana's, so 日 and に start at the same horizontal position.
  Degenerates to the exact old behavior for 0- or 1-codepoint text (the
  common single-kanji-per-cell case), so only multi-character cells that
  need centering are affected. New permanent regression tests
  (`TripleScriptRendererTest.interlinearCenterPaddingDistributesAroundCharac
  tersNotAtEdges`, `padCenterDistributionDegeneratesToEdgesForSingleCharacte
  rText`) pin exact expected strings. Full existing suite (16 tests) and
  static analysis still pass unmodified — width-equality/Discord-trim
  assertions hold by construction since total width is unchanged, only
  redistributed. **Live-verified**: rebuilt, installed, screenshotted the
  Settings preview across all three font/size combinations (small
  sans-serif, default sans-serif, monospace) — the fix holds consistently
  regardless of font, as expected from a purely character-count-driven
  algorithm. The `勉強`/`べんきょう` cell's remaining visible offset (base
  still noticeably indented there) was checked against the same math and
  confirmed correct: furigana there ties romaji as the widest line (both 10
  half-units) so it's flush already, and base's 2-unit-per-gap spread across
  only 2 characters is exactly what real `ruby-align: space-around` would
  also produce for that width ratio — not a residual bug.

- **"でき" line-wrap fix — re-verified live end-to-end this session.**
  `TripleScriptRenderer` applies Word-Joiner (U+2060) wrap protection to the
  plain-text interlinear output (previously reserved for the in-app preview
  only; reverted and reinstated once earlier this session). Originally
  confirmed only via raw-text inspection that a real host (Discord's
  compose box) was soft-wrapping mid-cell and splitting a single furigana
  reading ("でき") across two visual lines with no protection in place.
  **Now actually re-verified with a fresh round-trip**: converted "えっと、
  出来ましたかい？" via the Try-It card's from-clipboard button, pasted the
  real result into Discord's live compose field, and confirmed visually —
  "でき" renders intact on one line. The row did still soft-wrap elsewhere
  (between た/かい, and between kai/?), which is correct: Word Joiner
  protects the inside of a single cell/reading, not all wrapping
  everywhere; wraps at cell boundaries are expected and fine.

- **"Copy Image" rendering — fully live-verified end-to-end this session,
  via a real Discord Copy, not another broadcast workaround.** Furigana
  renders visibly smaller than base/romaji in the rasterized PNG
  (`ClipboardImageRenderer` gained a second, smaller `TextPaint` for the
  furigana row — `FURIGANA_RELATIVE_SIZE = 0.62f`, shared with the in-app
  Try-It preview's `RelativeSizeSpan` instead of duplicated). **Confirmed by
  pulling the actual generated PNG off the device and viewing it directly**
  (`run-as ... cat cache/images/japanglify_result.png`): にほんご renders
  visibly smaller than 日本語/ni·ho·n·go, exactly as intended.
  Earlier ADB-broadcast attempts to trigger `ACTION_COPY_IMAGE` directly
  (bypassing the real UI) were abandoned as a dead end — turned out to be
  the wrong approach entirely, not just unreliable. The real flow (enable
  Accessibility → Copy in a host app → tap "Copy image" on the notification)
  was driven for real this session, in the Discord scenario specifically
  (paste "日本語" into a real Discord channel's message box *without
  sending*, select and Copy it there, confirm the real Japanglify
  notification appears with Discord correctly prioritizing "Copy image"
  since it's in `IMAGE_PREFERRED_HOSTS`, tap it, pull and inspect the PNG).
  Getting there surfaced three real environmental gotchas worth recording
  (see below) that were the *actual* blockers, not automation flakiness:
  1. **`am force-stop` unbinds the Accessibility service, not just
     `adb install -r`** (NOTES.md's existing environment tip only mentioned
     reinstall). After any force-stop, Accessibility needs re-enabling via
     Settings → Accessibility → Japanglify Copy assist → toggle → Allow.
  2. **Android's background clipboard-access restriction
     (`ClipboardService: Denying clipboard access ... application is not in
     focus`) blocks `JapanglifyAccessibilityService`'s clipboard *reads***
     (both the poll loop and any `getPrimaryClip()` call) whenever
     Japanglify isn't the focused app — which, structurally, is always true
     for a background accessibility service reacting to a Copy in some
     *other* app. This didn't break the flow: the "selection memory"
     fallback (`lastSelectedText`, captured from `TYPE_VIEW_TEXT_SELECTION_
     CHANGED` accessibility node text rather than `ClipboardManager`)
     correctly bypassed it and processed successfully
     (`CopyHookDiagnostics` showed `processed selection OK`). This is a good
     signal that fallback is load-bearing on modern Android, not a nice-to-have.
  3. **Enabling Accessibility directly via system Settings (rather than
     through the app's own in-app toggle) skips the app's `POST_NOTIFICATIONS`
     runtime-permission request**, since that request is wired to the
     in-app switch's `onPreferenceChangeListener`, not to the service
     actually connecting. Net effect: the whole pipeline runs successfully
     but silently produces no visible notification
     (`dumpsys notification` showed `AppSettings: ... importance=NONE`
     until fixed via Settings → Apps → Japanglify → Notifications). Anyone
     enabling Accessibility from system Settings first should sanity-check
     notification permission separately.
  Still carries the older stray-"？"-under-`ORIGINAL`-style report from
  before this session, never reproduced against current code.
  - **Translation, separately**: the network path is confirmed correct at
    the protocol level — repeated attempts across this session consistently
    logged `non-200 response: 429` (see the Task #6 entry below), never a
    code exception, never a silent black box now that logging was added.
    No successful 200 response was achieved this session despite retrying
    over roughly an hour; whoever picks this up next should expect the rate
    limit may still be active on this same device/network and shouldn't
    assume a repeat 429 means new breakage.
  - **Translation, separately**: the network path is now confirmed correct
    at the protocol level — repeated attempts across this session
    consistently logged `non-200 response: 429` (see the Task #6 entry
    below), never a code exception, and never a silent black box now that
    logging was added. No successful 200 response was achieved this
    session despite retrying ~45 minutes apart; whoever picks this up next
    should expect the rate limit may still be active on this same
    device/network and shouldn't assume a repeat 429 means new breakage.

- **Task #6 — English-translation 4th line. Decided + implemented this
  session.** User chose the online-API direction, specifically the free
  unofficial Google Translate web endpoint (`translate.googleapis.com`,
  `client=gtx`) over the paid Cloud Translation API or a configurable
  LibreTranslate server — no key, no setup. This is now the app's one
  network-touching, opt-in, off-by-default feature (new `translate/`
  package, `INTERNET` permission, `include_translation` setting in
  Scripts). Explicitly kept off the fastpath: the setting defaults off, the
  `Translator`/executor are lazy (never constructed if unused), and
  `ProcessTextActivity` (the latency-sensitive `PROCESS_TEXT` path) is
  byte-for-byte unchanged when the toggle is off.
  **Live-tested this session — root cause confirmed, not a code bug.**
  Installed and drove the real Settings UI on a Pixel 8 (wireless-debugging,
  paired live): toggle renders/persists correctly, offline triple-script
  conversion via the Try-It card works correctly and immediately in both
  `assembleDebug` and a newly-signed `assembleRelease` build (R8 doesn't
  break it). Initial attempts showed no translated line appearing with no
  visible error — since `GoogleWebTranslator`/`Translator` swallow failures
  by design (`runCatching`, so the UI never shows an error for what's an
  optional enhancement), added a `Log.w` on both the exception path and the
  previously-silent non-200-response path. Rebuilt, re-tested on-device, and
  logcat immediately showed `non-200 response: 429` on every subsequent
  attempt (repeated ~5 min apart) — **the free unofficial endpoint rate-
  limited this session's test device**, almost certainly from this same
  session's cumulative requests to it (including an earlier botched
  browser-intent reachability probe that also hit it). This is exactly the
  risk already called out when this backend was chosen over the paid Cloud
  Translation API. The logging addition is a real, permanent improvement:
  a missing 4th line is now diagnosable from logcat instead of being a
  total black box. **Still needs one clean confirmation once the rate limit
  clears**: enable the toggle, convert a phrase, and confirm a translated
  line actually appears (not just the absence of a 429 in logcat).
  - **Stale-translation-callback race, found via code audit this session —
    not yet fixed, not yet live-reproduced.** `Translator.translateAsync()`
    posts its result back on the main thread with no check that the text it
    translated is still the *current* one. Three call sites are affected:
    `ClipboardProcessor.processText()` (the main Copy-hook pipeline),
    `SettingsFragment.japanglifyTryItField()`, and
    `SettingsFragment.japanglifyClipboard()`. Concrete scenario: copy
    Japanese text A (translation on) → its request goes out → before it
    returns, copy different text B → when A's translation eventually
    arrives, its callback unconditionally overwrites `LastResultStore` and
    re-shows the result notification with A's stale, mismatched content,
    clobbering B's. `JapanglifyAccessibilityService` already has the fix
    pattern for this exact class of bug elsewhere in the same file
    (`copyPipelineGeneration`, an incrementing counter checked before any
    delayed retry applies) — it was just never extended to the translation
    callbacks. Only manifests on a *successful* (non-null) translation
    response, so it couldn't be empirically reproduced live this session
    (every attempt has hit the 429 rate limit — see above). **Explicitly
    decided non-blocking for 1.0**: translation is off-by-default and this
    only affects it, so ship without fixing it first; revisit once the rate
    limit clears enough to actually test the fix.

- **Task #8 — Build from Android's Linux Terminal.** Low priority, not
  started. Would mean validating the existing Gradle/FreeBSD-Linuxulator
  build path also works under Android's native Linux Terminal app (Debian
  container), which is a different environment from both the FreeBSD dev
  host and a normal Linux CI box.

- **Output-format live examples in Settings — implemented and live-verified
  this session.** New `OutputFormatPreviewPreference` (mirrors
  `MaxLineWidthPreference`'s pattern: a read-only custom `Preference` card,
  not a replacement for the `ListPreference` picker itself) sits right below
  the `output_format` dropdown and renders all 5 `OutputFormat` options
  against the same short sample ("日本語") side by side, with the currently
  selected one shown in bold with a "✓" prefix. Refreshes on `onResume()`
  and immediately when `output_format` changes (via its
  `setOnPreferenceChangeListener`, using the new value directly since
  `ListPreference` reports it before persisting). Live-verified on the
  Pixel 8: all 5 formats render correctly (furigana-inline, parenthetical,
  interlinear — with the bold "✓ Interlinear" label — HTML ruby showing raw
  tags as expected, compact brackets), confirming people can now compare
  every format before picking one instead of guessing from the name alone.

- **Clipboard-event recursion safety audit — done this session, no bug
  found.** Traced every write/listener path: `LastResultStore`'s
  suppression window + self-write ring buffer + `CLIP_LABEL` stamp,
  `JapanglifyAccessibilityService`'s `clipListener` + `pollRunnable` + Cut
  auto-replace, `ClipboardAssistService`'s own listener, and the new
  translation notification-update path in `ClipboardProcessor`.
  - The translation path (`LastResultStore.save()` called a second time
    once translation resolves) never touches the system clipboard at all —
    it only updates internal state and re-posts the same notification ID —
    so it's structurally outside this class of bug regardless of timing.
  - One real near-miss chased down and confirmed already handled: Cut
    auto-replace repositions the cursor after inserting text
    (`moveCursorAfterInsert`, via `ACTION_SET_SELECTION`), which can fire a
    `TYPE_VIEW_TEXT_SELECTION_CHANGED` event from the *host* app (not
    Japanglify's own package, so the early `event.packageName == packageName`
    guard doesn't catch it). Traced into `captureSelection`: it explicitly
    rejects a collapsed cursor (`start < 0 || end <= start` → `return null`),
    and the resulting selection from `moveCursorAfterInsert` is always
    collapsed (`start == end == position`) — so this event is filtered out
    before it could re-trigger anything. The existing comment on that check
    ("do NOT fall back to fromEventList here, or every cursor tap in a
    populated field re-triggers the chip") shows this was already a
    deliberate design decision, not an accident.
  - Both `ClipboardAssistService` and `JapanglifyAccessibilityService` can
    react to the same real clipboard change if a user has both enabled;
    `ClipboardProcessor.processText`'s `lastHandledRaw` duplicate check
    absorbs the second trigger as `DUPLICATE`. No true race here since
    everything runs on the main/UI `Looper` thread — the lack of explicit
    synchronization on `lastHandledRaw` is fine, not a latent bug.
  - Conclusion: the guards are spread across several independent
    listeners/pollers rather than one chokepoint, which makes this worth
    re-checking whenever a new clipboard-write path is added (like
    translation was this session) — but as of this audit, every path is
    covered.

- **Performance measurement pass — first real numbers gathered this
  session** (a full Android Studio profiler/Perfetto pass is still future
  work, but this replaces "reasoned about, not measured" with actual data):
  - **`PROCESS_TEXT` end-to-end latency** (`adb shell am start -W`, real
    Pixel 8): **cold** (process not running) **~1941ms** `TotalTime`;
    **warm** (process already alive) **~71ms**. The ~1.9s gap is almost
    entirely one-time process/dictionary init, not per-call cost — see next.
  - **Kuromoji dictionary load**: **~649ms**, measured directly via a new
    permanent timing section in `domain`'s `DemoMain` (`./gradlew
    :domain:runDemo`, section "0. Performance") — same library/dictionary
    Android loads in `JapanglifyApp.onCreate()`, so this JVM number is
    representative of the real one-time app-startup cost. Accounts for the
    majority of the cold-start gap above.
  - **`TripleScriptRenderer` on a long selection**: also now in `DemoMain`
    ("4. Performance") — 200 repeated sentences (1800 input chars, wrapping
    to 300 interlinear rows) analyze+render in **~101.7ms**. Scales
    reasonably; not a concern at realistic selection lengths.
  - **Accessibility poll-loop cost**: not live-profiled (would need
    `dumpsys batterystats`/Perfetto over real elapsed time, not attempted
    this session), but characterized from the code:
    `JapanglifyAccessibilityService.POLL_MS = 400`, i.e. up to ~2.5
    `ClipboardManager.getPrimaryClip()` binder calls/second, continuously,
    for as long as the Copy-hook accessibility service is enabled — this is
    the one genuinely open question left (a real battery-cost number, not
    just "it's a poll loop with a 400ms interval").
  - Translation's "fastpath" claim (zero cost when the setting is off)
    wasn't separately re-measured — it's a single `SharedPreferences`
    boolean read before anything else runs, same order of magnitude as
    dictionary/render costs above make negligible-but-unmeasured claims
    like this now easy to double-check the same way if it's ever in doubt.

- **Named configuration profiles — post-1.0, backburner.** Not started.
  Explicitly deprioritized past 1.0 by the user, and explicitly scoped up
  from the original idea: not just named/saved `JapanglifySettings` presets
  switched manually, but **automatic profile selection based on which app
  the text was copied/cut from** — e.g. a Discord-tuned profile applies
  automatically for `com.discord`, a study-format profile for a dictionary
  app. That raises the real design burden beyond simple storage: an
  easy-to-use app-binding UI (pick a profile, pick which installed apps
  trigger it — a picker over the launchable-app list, not a raw package-name
  text field) is called out as a hard requirement of doing this at all, not
  an optional nice-to-have. Also still needs: a storage scheme for multiple
  named `JapanglifySettings` snapshots (`PreferencesRepository` currently
  reads/writes exactly one flat, unnamed set of keys — a real data-model
  change), and a decision on how automatic per-app switching interacts with
  the live Try-It preview and an already-running Copy-hook session (does a
  profile change apply mid-flight or only to the next conversion?). The
  per-app auto-binding piece has a natural home: `JapanglifyAccessibilityService`
  /  `ClipboardProcessor` already track the source package
  (`LastResultStore.lastHostPackage`) for the image-vs-text notification
  choice — the same signal would drive profile auto-selection. Worth
  scoping as its own small design pass once picked back up, not bolted on
  ad hoc.

- **LaTeX output rendering — post-1.0, backburner.** Not started. A new
  `OutputFormat` (or a separate rendering path entirely, since LaTeX's ruby
  story is a package choice, not a Unicode trick like `INTERLINEAR`'s
  PAD/Word-Joiner approach) that emits real LaTeX for furigana-annotated
  Japanese — likely via the `ruby` package (`\ruby{漢字}{かんじ}`) or CJK/
  luatex-ja's native furigana support, plus romaji as a second annotation
  line if that's expressible in the same macro or needs its own row. Needs a
  design pass on: which LaTeX furigana package to target (portability vs.
  fidelity tradeoff — `ruby` is plain pdfLaTeX-compatible but cruder;
  luatex-ja needs LuaLaTeX), whether romaji fits in-line or wants a separate
  block, and escaping (LaTeX special characters in surrounding text, and in
  the rare case a translation's 4th line is included too).

## Known-good as of this session (verified live on Pixel 8 + Galaxy Note 9)

- Settings screen: section order (Scripts → Copy assist → Phoneticization →
  Rendering → status/Try-it → Selection menu help → About), status/nav bar
  inset padding (API 35 edge-to-edge), Team/Contact/Profile/Third-party
  credits, max-line-width slider with live 3-font preview (values 0–32,
  including 8).
- Copy pipeline end-to-end in a real host (Google Keep): correct conversion,
  notification capped at 3 actions, field-replace availability detection,
  app icon rendering.
- Cut auto-replace: converts and inserts in place at the cursor (the
  earlier-suspected "duplication" bug was a misdiagnosis — interlinear
  output legitimately includes the base line, so a short Cut selection
  correctly comes back as multiple lines; this is expected wholesale-replace
  behavior, not a bug).
- No-Japanese-content Copy/Cut correctly no-ops (no notification, no
  engine call).
- Fullwidth-digit/letter punctuation misclassification fixed; Kuromoji's
  one-digit-per-token number fragmentation fixed; non-Japanese passthrough
  text (e.g. "Wi-Fi") no longer dropped from the romaji line.
- Furigana renders visibly smaller than base/romaji in the in-app Try-It
  preview (real ruby-style sizing via `RelativeSizeSpan`, INTERLINEAR format
  only).
- Copy image, end-to-end via a real Discord Copy: small furigana confirmed
  in the actual saved PNG (see the "Copy Image" entry above).
- `RealDictionaryIntegrationTest` (new): the domain suite's first test
  against the real Kuromoji dictionary instead of hand-crafted fixtures —
  captured verbatim from a real, unscripted Discord message draft during
  this session, pinned exactly for `PARENTHETICAL` and smoke-tested across
  every `OutputFormat`/`RomanizationSystem`. Stored proof the full
  tokenize→resolve→render pipeline holds up on organic Japanese, not just
  curated examples.
- `./gradlew acceptanceSmokeTest` (new): turns this session's by-hand live
  verification into a repeatable target. Runs `:domain:test`, then — only if
  a device is connected (`installDebug` is pulled in conditionally, not
  hard-required) — drives Settings + output-format-preview screenshots and
  renders the real proof-sentence pipeline via a new debug-only
  `AcceptanceTestActivity` (`app/src/debug/`, never ships in release —
  confirmed absent from `app-release.apk`'s merged manifest). Writes
  `build/reports/acceptance/index.md`, a GitHub-renderable Markdown report
  with embedded screenshots. Device-optional by design (`-PdeviceSerial=...`
  to target one explicitly) — exits 0 with just the domain summary and a
  "no device" note when none is connected, ahead of the eventual
  locally-spun-up Android VM target. Verified both ways this session.

## Environment notes (don't rediscover these)

- Build: `JAVA_HOME=/usr/local/openjdk21 ./gradlew :app:assembleDebug` — the
  pinned Gradle 8.9 does not support the system default JDK 25.
- `adb install -r` **and plain `am force-stop com.japanglify.app`** both
  unbind the accessibility service — confirmed this session that force-stop
  alone does it too, not just reinstall (`adb shell settings get secure
  enabled_accessibility_services` reads back `null` afterward even though
  the in-app toggle still visually shows "on" until you reopen system
  Settings). Re-enable via Settings → Accessibility → Japanglify Copy
  assist → toggle → Allow after *any* force-stop, not just reinstalls.
- If Accessibility gets enabled via system Settings directly (not through
  the app's own in-app switch), `POST_NOTIFICATIONS` never gets requested —
  the whole copy-hook pipeline runs and succeeds silently with zero visible
  notification. Check `adb shell dumpsys notification --noredact | grep -A2
  japanglify` for `importance=NONE`/`AppSettings:` if a Copy visibly logs
  `processed selection OK` (`CopyHookDiagnostics`) but nothing appears; fix
  via Settings → Apps → Japanglify → Notifications.
- Android's background clipboard-read restriction
  (`ClipboardService: Denying clipboard access ... application is not in
  focus`) blocks `JapanglifyAccessibilityService`'s `getPrimaryClip()` calls
  whenever Japanglify isn't the focused app — i.e. basically always, for a
  background service reacting to another app's Copy. This is expected, not
  a bug: the "selection memory" fallback (captured from accessibility
  selection-changed events, not `ClipboardManager`) is what actually carries
  the pipeline in that case.
- After ~2.5 hours of this session's testing (many apps open simultaneously —
  Discord, X with a live Space, Chrome, repeated Japanglify installs/builds),
  the test device hit real resource exhaustion: `dumpsys meminfo` itself
  timed out (`*** SERVICE 'meminfo' DUMP TIMEOUT (10000ms) EXPIRED ***`),
  `lowmemorykiller` was actively killing background apps, and an ANR dialog
  ("Japanglify isn't responding") appeared and recurred across app switches.
  Given a core system service couldn't respond either, this reads as
  device-wide memory pressure rather than a Japanglify-specific hang — but
  it's not fully ruled out as the latter, since `JapanglifyAccessibilityService`
  was mid-pipeline at the time. Not investigated further this session. If it
  recurs on a *fresh* device/session (not after hours of heavy multi-app
  use), treat it as a real bug and get an actual ANR trace
  (`adb shell logcat -d | grep -A30 "ANR in com.japanglify"` /
  `/data/anr/traces.txt`) rather than assuming resource exhaustion again.
- Screenshots: `scripts/adb-shot.sh <out.png> [max_seconds] [poll_interval]`
  busy-polls until two consecutive frames match, but has been flaky this
  session over an unstable wireless-debugging connection — plain
  `adb exec-out screencap -p > file.png` is a more reliable fallback.
- A device left idle dozes off; `screencap` still "succeeds" against a
  dozing screen — it just captures the lockscreen/notification shade
  instead of the app, a silent wrong-content result, not a loud failure.
  `acceptanceSmokeTest` now wakes + `wm dismiss-keyguard`s before driving
  the UI, but on a phone with a real PIN/pattern/biometric lock enabled
  (as opposed to swipe-only/no lock), that lands on `AlternateBouncerView`
  and can't go further — `wm dismiss-keyguard` only clears an
  insecure/no-auth keyguard, by design, and this script should never try to
  push past real device security. If a fully-populated report is needed
  from a secured phone, unlock it manually right before running. The
  eventual local Android VM target should default to no lock screen, which
  sidesteps this entirely.
- Multiple devices connected simultaneously: always pin commands with
  `adb -s <serial>` (or `export ANDROID_SERIAL=<serial>` per-command, not
  exported globally across tool calls — it doesn't persist in this harness).
- `adb shell input text` cannot type CJK text (throws in
  `InputShellCommand.sendText`). Route Japanese test strings in via
  `am start -a android.intent.action.SEND -t text/plain --es
  android.intent.extra.TEXT '...' -n com.japanglify.app/.SettingsActivity`
  instead — but note this can land as "delivered to currently running
  top-most instance" and silently no-op if the app is already in the
  foreground/back stack; `am force-stop com.japanglify.app` first to
  guarantee a real cold start (then remember to rebind accessibility).
- **`adb shell uiautomator dump` destroys and reconnects
  `JapanglifyAccessibilityService` every time it runs — isolated and
  confirmed reproducible 3× this session, unrelated to split-screen (an
  earlier hypothesis this session wrongly blamed multi-window transitions;
  the actual pid destroying/recreating was uiautomator's own
  `com.android.commands.uiautomator.Launcher` process, not Japanglify's).
  Controlled test: baseline → one `uiautomator dump` → new "service
  connected" timestamp in `CopyHookDiagnostics`. Three rounds of
  `screencap`/`input tap`/`dumpsys window` in between → no new timestamp.
  Another `uiautomator dump` → new timestamp again. Root cause is an Android
  platform behavior, not a Japanglify bug: `uiautomator dump` uses the
  UiAutomation API, which has to establish its own accessibility-service-like
  connection to walk the view tree, and doing so causes
  `AccessibilityManagerService` to reconfigure the set of active connections
  — bouncing whatever other accessibility services (Japanglify's included)
  were already bound. **Practical impact: this session's whole testing
  methodology has been doing this to itself.** Nearly every verification
  step relied on `uiautomator dump` for safe, coordinate-verified tapping,
  while also depending on the Copy-hook accessibility service staying
  continuously bound — so `lastHandledRaw`, `copyPipelineGeneration`,
  `lastSelectedText`, and any in-flight debounce/retry callbacks were likely
  getting silently reset between our own checks, session-long. Plausibly
  explains at least one earlier puzzle this session (a real Discord "Copy
  Text" that didn't auto-trigger the notification) and any other one-off
  "flaky" results chalked up to timing. Not a fixable app bug — a testing-
  methodology constraint to design around (e.g. read `CopyHookDiagnostics`
  or logcat directly instead of dumping mid-sequence when the Copy-hook's
  *state*, not just the screen layout, is what's under test).
