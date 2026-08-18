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
    /** Must match the `<category>` inside `res/xml/shortcuts.xml`'s `<share-target>`. */
    const val CATEGORY = "com.japanglify.app.category.SHARE_TARGET"

    /**
     * Full sync: replaces the entire dynamic shortcut list with exactly
     * [targets], capped to the platform's per-activity max (older devices/
     * launchers can be as low as 4) — silently drops any beyond that rather
     * than failing the whole sync, since a partial set of targets is still
     * useful and [androidx.preference] callers should not need to reason
     * about a platform shortcut-count limit themselves.
     */
    fun sync(context: Context, targets: List<ShareTarget>) {
        val max = ShortcutManagerCompat.getMaxShortcutCountPerActivity(context).let { if (it > 0) it else Int.MAX_VALUE }
        val shortcuts = targets.take(max).map { buildShortcut(context, it) }
        ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts)
    }

    private fun buildShortcut(context: Context, target: ShareTarget): ShortcutInfoCompat {
        val fallbackIntent = Intent(context, ShareTargetActivity::class.java).apply {
            action = Intent.ACTION_SEND
            type = "text/plain"
        }
        return ShortcutInfoCompat.Builder(context, target.id)
            .setShortLabel(target.label)
            .setLongLabel(target.label)
            .setIcon(IconCompat.createWithResource(context, R.mipmap.ic_launcher))
            .setIntent(fallbackIntent)
            .setCategories(setOf(CATEGORY))
            .build()
    }
}
