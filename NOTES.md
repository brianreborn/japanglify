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
  screenshot-chasing.

- **Re-verify the "でき" line-wrap fix live.** `TripleScriptRenderer` now
  applies the same Word-Joiner (U+2060) wrap protection to the plain-text
  interlinear output that was previously reserved for the in-app preview
  only (see commit history — this was reverted and reinstated once during
  this session). Confirmed via raw-text inspection that a real host
  (Discord's compose box) was soft-wrapping mid-cell and splitting a single
  furigana reading ("でき") across two visual lines with no protection in
  place. Built and reinstalled on both test devices but not yet re-verified
  end-to-end with a fresh Copy → paste round-trip.

- **"Copy Image" rendering not yet re-examined with current defaults.** A
  stale screenshot (dated the day before this session) showed a stray "？"
  appearing alone on a furigana row in a rasterized image result, which
  would only make sense under `FuriganaPunctuationStyle.ORIGINAL` — not
  today's default (`NONE`). Never reproduced against current code; the
  image-generation path (`ClipboardImageRenderer.renderInterlinearToBitmap`)
  hasn't been looked at closely this session. Worth a fresh test: process a
  phrase with punctuation, tap "Copy image" from the result notification,
  and inspect the PNG directly.

- **Task #6 — English-translation 4th line.** Not yet discussed with the
  user beyond being listed. Would need a translation source (no network
  calls exist in this app currently — everything is offline/on-device via
  Kuromoji). Needs a design decision before any implementation: online
  translation API (breaks the "offline, no network" property) vs. dropping
  the idea vs. some on-device approach.

- **Task #8 — Build from Android's Linux Terminal.** Low priority, not
  started. Would mean validating the existing Gradle/FreeBSD-Linuxulator
  build path also works under Android's native Linux Terminal app (Debian
  container), which is a different environment from both the FreeBSD dev
  host and a normal Linux CI box.

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
