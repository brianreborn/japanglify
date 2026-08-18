Second public BETA (`versionCode` 3). Upgrade-installs over `v1.0.0-beta`.

### What's new

- **Two APKs.** Downloadable (smaller; dictionaries fetched on demand) and bundled (dictionaries ship in the APK, works fully offline). Installing one replaces the other — they share the same package name.
- **User-defined Share targets.** Save a named settings snapshot and share to it from any app, as copy-text or copy-image.
- **Image color schemes.** Copy-as-image can follow the system theme or use a light/dark print-style scheme, with max width/height.
- **Better interlinear layout.** Phrase matches stay on one row; split-word columns even out; furigana/romaji stay intact over a single-glyph cell; alignment no longer depends on a second tokenizer.
- **HTML ruby actually stacks.** Furigana uses `<ruby><rt>`; romaji is a smaller block span. The old `<rb>`/`<rtc>` markup left romaji as unstyled inline text.
- **Smarter glosses.** Reading-aware lookup, longest-match phrases (expressions/interjections), up to three synonyms per sense, Modern/Classical/custom sense-selection presets, and disambiguation of same-reading different words via conjugation class.
- **Elided-line markers** (default 〃) and a **bound copula/auxiliary separator** (mora dot vs space).
- **Separate "Replace text on Cut"** toggle; clipboard progress bar; cancelable dictionary downloads with a wall-clock timeout (no more hangs on a trickling connection).
- **PROCESS_TEXT** filter narrowed to the single AOSP `text/plain` form.

### Downloads

| APK | What it is |
| --- | --- |
| **[app-release.apk](https://github.com/brianreborn/japanglify/releases/download/v1.0.0-beta1/app-release.apk)** | Signed downloadable (recommended) |
| [app-bundled-release.apk](https://github.com/brianreborn/japanglify/releases/download/v1.0.0-beta1/app-bundled-release.apk) | Signed, dictionaries included (offline) |
| `app-*-debug.apk` | Debug builds, for development |

APK is self-signed (not Play Store signed) — allow installs from this source.

### Known limitations

- X/Twitter Copy-hook is still silent on genuine Japanese text.
- Word/particle glosses and emoji annotation stay Experimental and off by default.
- Print-to-PDF is not in this build.

See [RELNOTES.md](https://github.com/brianreborn/japanglify/blob/main/RELNOTES.md) and [NOTES.md](https://github.com/brianreborn/japanglify/blob/main/NOTES.md) in the repo.
