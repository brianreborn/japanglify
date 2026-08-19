package com.japanglify.app.share

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.japanglify.app.R
import com.japanglify.app.ShareTargetActivity
import com.japanglify.app.domain.ShareTarget

/**
 * Syncs user-defined [ShareTarget]s to the OS's dynamic Share-sheet
 * shortcuts (Android's "Direct Share" mechanism), so each target appears as
 * its own named icon in the system Share sheet alongside the default
 * "Japanglify" entry.
 *
 * How this actually fires (worth being explicit about, since it's easy to
 * assume the shortcut's own [ShortcutInfoCompat.Builder.setIntent] is what
 * launches — it isn't, for a Share-sheet pick specifically): a shortcut only
 * shows up as a Direct-Share target when it's tagged with a category that
 * matches a `<share-target>` entry in `res/xml/shortcuts.xml`, which in turn
 * names [ShareTargetActivity] as the target class for `text/plain`. Tapping
 * the shortcut in the Share sheet makes the OS launch that target class with
 * the *original* ACTION_SEND intent (carrying the actually-shared
 * EXTRA_TEXT) plus [Intent.EXTRA_SHORTCUT_ID] set to this shortcut's own id
 * — that's how [ShareTargetActivity] knows which target was picked. The
 * `.setIntent(...)` below is still required (a [ShortcutInfoCompat] can't be
 * built without one) and matters if the shortcut is ever launched some other
 * way (e.g. a long-press app-icon shortcut), but isn't the Share-sheet path.
 *
 * Uses the target's own [ShareTarget.id] directly as the shortcut id (no
 * extra prefix/mapping needed) precisely so it round-trips cleanly through
 * [Intent.EXTRA_SHORTCUT_ID] back to [ShareTargetRepository.find].
 */
object ShareTargetShortcuts {
    /**
     * New model (2026-08): single "Japanglify" entry in the system Share sheet.
     * When chosen, ShareTargetActivity shows an in-app dropdown/chooser of:
     *   - current settings (text)
     *   - current settings (image)
     *   - each saved ShareTarget (with its frozen snapshot + action)
     *
     * We no longer publish per-target dynamic shortcuts as Direct Share targets.
     * That approach caused:
     *   - multiple "Japanglify" icons cluttering the sheet
     *   - flakiness of Direct Share across launchers/OEMs/Android versions
     *   - pinned shortcuts going stale when the user renamed/reordered targets
     *
     * Legacy pinned per-target shortcuts (if any still exist on device) are
     * still honored via EXTRA_SHORTCUT_ID for a transition period.
     */
    fun sync(context: Context, targets: List<ShareTarget>) {
        // Stop publishing per-target Direct Share icons.
        // This leaves only the single generic "Japanglify" ACTION_SEND entry.
        ShortcutManagerCompat.removeAllDynamicShortcuts(context)
        // We intentionally publish nothing here. The dropdown is driven inside
        // ShareTargetActivity when the generic share target is chosen.
    }
}
