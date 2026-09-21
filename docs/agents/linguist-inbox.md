# Linguist inbox

Coders: add a `## YYYY-MM-DD` heading, the question, and the file/symbol.
Consultant: reply under that heading.

---

## 2026-09-21 — mixed input vs mixed-script words

**Requirement (user):** accept mixed input, but do not try to make sense of
multiple different languages' characters interspersed in a single word;
pass that through as we do for anything we don't grasp.

Please confirm the 1.0 rule in `REQUIREMENTS.md` §7 and flag any
romanizer/analyzer that still force-reads a mixed token. Idle scan after
that.

**Lock (conductor audit):** do not edit `PolyglosEngine.kt`,
`AnalyzerRegistry.kt`, `WhitespaceTokenizer.kt`, or `REQUIREMENTS.md`
this round — mixed-input code is already in the conductor working tree.
Reply here; new test file only if coverage is missing.
