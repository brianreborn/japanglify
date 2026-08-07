# Japanglify — pre-1.0 status

Working state as of 2026-08-06. This file tracks what's left before a real
1.0 release; it's a plan/status doc, not user-facing documentation.

## Open items

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

- **Re-verify the "でき" line-wrap fix live.** `TripleScriptRenderer` now
  applies the same Word-Joiner (U+2060) wrap protection to the plain-text
  interlinear output that was previously reserved for the in-app preview
  only (see commit history — this was reverted and reinstated once during
  this session). Confirmed via raw-text inspection that a real host
  (Discord's compose box) was soft-wrapping mid-cell and splitting a single
  furigana reading ("でき") across two visual lines with no protection in
  place. Built and reinstalled on both test devices but not yet re-verified
  end-to-end with a fresh Copy → paste round-trip.

- **"Copy Image" rendering.** Furigana now renders visibly smaller than
  base/romaji in the rasterized PNG too (`ClipboardImageRenderer` gained a
  second, smaller `TextPaint` for the furigana row — `FURIGANA_RELATIVE_SIZE
  = 0.62f`, the same constant the in-app Try-It preview's `RelativeSizeSpan`
  already used; `SettingsFragment` now references that one shared constant
  instead of duplicating it). Builds clean, but **not live-verified this
  session** — driving the real "Copy image" notification button requires
  either the Accessibility copy-hook or a genuine host Copy action, and
  every ADB-automation path tried this session to reach it artificially
  (direct broadcast to `ClipboardAssistReceiver`, seeding `LastResultStore`
  via `run-as`) silently no-opped without error, consistent with this
  session's broader live-device-automation flakiness (see the UAT item
  above). Whoever is next on a device: process a phrase, tap "Copy image"
  from the result notification, and confirm the furigana row visibly reads
  smaller than the base/romaji rows in the saved PNG. Also still carries the
  older stray-"？"-under-`ORIGINAL`-style unverified report from before this
  session.

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

- **Task #8 — Build from Android's Linux Terminal.** Low priority, not
  started. Would mean validating the existing Gradle/FreeBSD-Linuxulator
  build path also works under Android's native Linux Terminal app (Debian
  container), which is a different environment from both the FreeBSD dev
  host and a normal Linux CI box.

- **Output-format live examples in Settings.** Not started. The Rendering
  category's output-format picker (`OutputFormat`: parenthetical /
  interlinear / HTML ruby / compact / furigana-inline) is currently a plain
  `ListPreference` dropdown — users pick a format by name/description alone
  with no idea what it actually looks like until they try it. Render a short
  sample conversion under each option (or at least under the currently
  selected one) so people can see what they're choosing between, the same
  way `MaxLineWidthPreference` already renders a live sample at the
  candidate line width while dragging its slider — that's the pattern to
  follow rather than inventing a new one.

- **Audit clipboard-event recursion safety.** For a future cleanup pass, not
  urgent now: do a dedicated search across the clipboard-assist pipeline
  (`LastResultStore`'s self-write suppression/ring-buffer,
  `JapanglifyAccessibilityService`'s `clipListener` + `pollRunnable`,
  `ClipboardAssistService`'s own listener, and the new translation
  notification-update path in `ClipboardProcessor`) for any path where a
  Japanglify-triggered clipboard write could re-trigger the pipeline instead
  of being recognized as "ours." The guards (`isSuppressing`,
  `isSelfWrite`, `shouldIgnoreClipboard`, the `CLIP_LABEL` stamp) are spread
  across several independent listeners/pollers rather than one chokepoint,
  and the translation feature just added a second `showResult`/
  `LastResultStore.save` call per conversion — worth explicitly re-checking
  that combination isn't a gap, not just assuming it's covered by the
  existing per-listener guards.

- **Performance measurement / live profiling pass.** For a future cleanup
  pass, not urgent now: no actual profiling has been done on this app yet —
  latency/allocation claims so far (e.g. the translation feature's
  "fastpath" work) are reasoned about from the code, not measured on a
  device. Worth a real pass with Android Studio's profiler or
  `adb shell am start -W` / Perfetto/systrace: `PROCESS_TEXT` end-to-end
  latency (with translation on and off), the accessibility service's
  poll-loop (`JapanglifyAccessibilityService.POLL_MS`) battery/CPU cost over
  time, `KuromojiReadingProvider`'s dictionary-load cost on first use, and
  `TripleScriptRenderer`'s interlinear packing/measurement cost on long
  selections.

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

## Environment notes (don't rediscover these)

- Build: `JAVA_HOME=/usr/local/openjdk21 ./gradlew :app:assembleDebug` — the
  pinned Gradle 8.9 does not support the system default JDK 25.
- `adb install -r` silently unbinds the accessibility service without
  clearing the persisted `enabled_accessibility_services` setting. After any
  reinstall: `adb shell settings put secure enabled_accessibility_services ""`
  then set it back to the service's component name to force a rebind.
- Screenshots: `scripts/adb-shot.sh <out.png> [max_seconds] [poll_interval]`
  busy-polls until two consecutive frames match, but has been flaky this
  session over an unstable wireless-debugging connection — plain
  `adb exec-out screencap -p > file.png` is a more reliable fallback.
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
