package com.japanglify.app.clipboard

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import com.japanglify.app.R

/**
 * Accessibility overlay chip labeled "Japanglify" — the substitute for a
 * PROCESS_TEXT menu entry in hosts (Twitter/X, …) that never show one.
 */
class SelectionActionOverlay(
    private val service: AccessibilityService
) {
    private val windowManager =
        service.getSystemService(WindowManager::class.java)
    private var root: FrameLayout? = null
    private var pendingText: String? = null

    fun show(text: String, anchorOnScreen: Rect?) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || trimmed.length > ClipboardProcessor.MAX_CHARS) {
            hide()
            return
        }
        pendingText = trimmed
        ensureView()
        val view = root ?: return
        val params = view.layoutParams as WindowManager.LayoutParams
        val density = service.resources.displayMetrics
        val margin = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, 8f, density
        ).toInt()
        val approxChipH = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, 44f, density
        ).toInt()

        if (anchorOnScreen != null && !anchorOnScreen.isEmpty) {
            params.gravity = Gravity.TOP or Gravity.START
            params.x = (anchorOnScreen.left).coerceAtLeast(margin)
            // Prefer just below the selection; fall back above if near bottom
            val below = anchorOnScreen.bottom + margin
            val screenH = density.heightPixels
            params.y = if (below + approxChipH < screenH - margin) {
                below
            } else {
                (anchorOnScreen.top - approxChipH - margin).coerceAtLeast(margin)
            }
        } else {
            params.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            params.x = 0
            params.y = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 72f, density
            ).toInt()
        }
        try {
            windowManager.updateViewLayout(view, params)
            view.visibility = android.view.View.VISIBLE
        } catch (_: Exception) {
            try {
                windowManager.addView(view, params)
            } catch (_: Exception) {
                // ignore
            }
        }
    }

    fun hide() {
        pendingText = null
        val view = root ?: return
        view.visibility = android.view.View.GONE
    }

    fun destroy() {
        val view = root ?: return
        try {
            windowManager.removeView(view)
        } catch (_: Exception) {
            // already removed
        }
        root = null
        pendingText = null
    }

    private fun ensureView() {
        if (root != null) return
        val view = LayoutInflater.from(service)
            .inflate(R.layout.overlay_japanglify_chip, null) as FrameLayout
        view.findViewById<TextView>(R.id.chip_label).setOnClickListener {
            onChipClicked()
        }
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        try {
            windowManager.addView(view, params)
            root = view
        } catch (_: Exception) {
            root = null
        }
    }

    private fun onChipClicked() {
        val text = pendingText?.trim().orEmpty()
        hide()
        if (text.isEmpty()) return
        when (ClipboardProcessor.processText(service, text)) {
            ClipboardProcessor.ProcessOutcome.SUCCESS -> {
                Toast.makeText(
                    service,
                    R.string.notif_result_ready,
                    Toast.LENGTH_SHORT
                ).show()
            }
            ClipboardProcessor.ProcessOutcome.DISABLED -> {
                Toast.makeText(
                    service,
                    R.string.clipboard_assist_disabled_hint,
                    Toast.LENGTH_LONG
                ).show()
            }
            else -> {
                Toast.makeText(
                    service,
                    R.string.error_processing_generic,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
}
