# TODO / Handoff (resume here)

Current target: **1.0.0-beta2** (versionCode 4).

- Build source of truth bumped + signed release APKs produced.
- GitHub prerelease: tag `v1.0.0-beta2` (assets: app-release.apk + app-bundled-release.apk + explicit japanglify-*-beta2-* names).
- Direct downloads from the tag work. This is the UAT area going forward.
- Old temporary `snapshot-2026-08-19` tag can be ignored/deleted later.

This session delivered:
- Max line width UI finally usable for wide blocks (slider max 120, 3-digit EditText, no forced clamping on persisted values, parse ceiling raised to 200, updated summaries).
- Safe preemptive background image generation after text results: generation counter + cancelPrevious, low-prio single-thread executor, lastSource + generation guards on every completion path, cooperative AbortCheck inside renderers (preview + between rows), length gate (full PNG only for short sources ≤600 chars). On-demand "Copy image" path untouched and higher priority.
- Clipboard / Cut / result handling polish (early finish for long renders, focused Process shim, auto handling for emptied clipboard, etc.).
- New scripts under scripts/:
  - create-new-beta.sh — bump + build the two signed release APKs for a new beta.
  - push-beta-to-gh.sh — publish current built release APKs to a GitHub prerelease tag (conventional names + explicit beta names).
- Version bumped cleanly to beta2 / vc4. Beta2 remains a prerelease so /latest still serves beta1 until promoted.

UAT: use the v1.0.0-beta2 tag assets for upgrade testing from beta1.

---

Parked items below. Update as you test the beta2 bits.

UAT screenshots and pulled PNGs stay on the DUT/host only
(`app/build/reports/acceptance/`). Do not commit or attach them to
the GitHub release — they include a live Discord thread.

Previous shipped reference (beta1): tag `v1.0.0-beta1`, `versionCode` 3.
Japanglify Copy assist + UMAssisted were re-enabled after the last
reinstall. JMdict / CLDR / WordNet are downloaded on the DUT.

---

## Critical — does the release actually work?

Checked 2026-08-18 on DUT Pixel 8, `1.0.0-beta1` / `versionCode` 3,
against **raw Japanese**. No Discord screenshots.

- [x] **Share → Japanglify** — `last_source` stayed `日本語を勉強する` (raw);
      `last_result` is a first-pass interlinear (furigana/base/romaji/gloss).
      Result notification posted.
- [x] **Copy image** — you pasted in Discord; output looked right
      (`懐かしいね。` after the grouping fix).
- [ ] **Copy-hook** — **needs you.** Service is *Enabled* but stuck
      `Binding` after a `uiautomator dump` (known: dump unbinds us).
      Hook log last said `10:11:20 service connected`. Flip
      **Settings → Accessibility → Japanglify Copy assist** off/on,
      then Copy `猫` (or anything raw) in Discord/Keep and we check
      `last_source` + the result notification. Do not blame the hook
      until Bound services lists "Japanglify Copy assist" again.
- [ ] **PROCESS_TEXT** — **needs a real host select.** `am start`
      launches `ProcessTextActivity` but `-W` times out (bad test:
      translucent, no calling editor). Pick Japanese in Messages /
      Chrome / Keep → Japanglify in the toolbar.
- [ ] **Share URL** — **inconclusive / maybe stuck.** Sharing
      `https://example.com/` left `ShareTargetActivity` resumed for
      well past the 20s Jsoup timeout; Settings never opened. Could
      be network or a hang. Try Share from the browser on a real page.
- [x] **Settings stick** — `include_glosses` / `include_emoji` /
      dict READY still in prefs after Shares and process use.
- [x] **Dictionary download** — JMdict + CLDR + WordNet READY, DBs
      on disk, survived earlier `force-stop`s.
- [x] **No double-convert on Share/image.** `last_source` is raw
      Japanese, not the padded result. (Try-it still *can* double-convert;
      that is parked below, not this path.)

Stop here if Share, Copy-image, or a real Copy-hook/PROCESS_TEXT fail.

---

## After we know it is not utterly broken

### Pipeline / “don't re-process our output”

- Try-it **replaces the input box with the rendered interlinear**, then live
  preview / a second tap / clipboard-from-field will run the engine on
  pads + furigana + romaji. Convert should only write the **output** pane
  (and clipboard). Input stays Japanese.
- Copy-hook: interlinear still contains kanji, so a user copying *our*
  result can retrigger. Confirm `LastResultStore` self-write guards hold.
- Emoji Strict looks up the whole gloss string (`cat/feline`) and misses.
  Try each `/`-separated synonym.

### Layout / readings (do not start now)

- Furigana placement still wrong in real Copy-image PNGs: 被造物 wrapping
  so ひ rides a lone 被; 語れ / 思う leave the reading on the kanji cell
  with okurigana parked apart. The beta1 image fix only spread *romaji*
  across a group (`74962ec`). Resume against PNGs, not the Try-it box.
- `章だった` romaji fuses as `sho·uda·t·ta` (mora seam).

### Experimental gloss / emoji (expected rough)

- `勉強` → “experience” (sense pick).
- `車` / “car” : Strict has no CLDR tts hit (`🚗` is “automobile”).
- Gloss + emoji stay off-by-default Experimental.

### Known non-blockers already in NOTES.md

- X/Twitter Copy-hook still silent.
- Print-to-PDF not in this build.
- GitHub `app-bundled-release.apk` may still be the pre-`versionCode` 3 build.
- Translation is a notification action, not an image line; unofficial
  endpoint 429s; stale-callback race if it ever returns.
