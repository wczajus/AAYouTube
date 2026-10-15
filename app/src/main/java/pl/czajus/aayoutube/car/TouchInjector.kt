package pl.czajus.aayoutube.car

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent

/**
 * Turns the gestures Android Auto reports for the map surface (tap, scroll, fling) into touch
 * events for the web page. Android Auto only reports scroll distances, not where the finger went
 * down, so a drag is simulated from an anchor point and ended shortly after the last scroll
 * update. A drag right after a tap starts at the tapped point, which lets the driver grab the
 * video progress bar: tap it, then slide.
 */
class TouchInjector(private val dispatch: (MotionEvent) -> Unit) {

    private val handler = Handler(Looper.getMainLooper())
    private val endDragRunnable = Runnable { endDrag() }
    private var dragging = false
    private var downTime = 0L
    private var x = 0f
    private var y = 0f
    private var lastTapTime = 0L
    private var lastTapX = 0f
    private var lastTapY = 0f

    fun click(px: Float, py: Float) {
        endDrag()
        val down = SystemClock.uptimeMillis()
        lastTapTime = down
        lastTapX = px
        lastTapY = py
        send(down, down, MotionEvent.ACTION_DOWN, px, py)
        handler.postDelayed({
            send(down, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, px, py)
        }, TAP_DURATION_MS)
    }

    /** Starts at the last tap if it was recent, otherwise at [fallbackX], [fallbackY]. */
    fun scroll(distanceX: Float, distanceY: Float, fallbackX: Float, fallbackY: Float) {
        val now = SystemClock.uptimeMillis()
        if (!dragging) {
            dragging = true
            downTime = now
            val afterTap = now - lastTapTime <= TAP_ANCHOR_MS
            x = if (afterTap) lastTapX else fallbackX
            y = if (afterTap) lastTapY else fallbackY
            send(downTime, now, MotionEvent.ACTION_DOWN, x, y)
        }
        // Distances follow GestureDetector: positive when the finger moves left/up.
        x -= distanceX
        y -= distanceY
        send(downTime, now, MotionEvent.ACTION_MOVE, x, y)
        handler.removeCallbacks(endDragRunnable)
        handler.postDelayed(endDragRunnable, DRAG_END_DELAY_MS)
    }

    fun endDrag() {
        handler.removeCallbacks(endDragRunnable)
        if (!dragging) return
        dragging = false
        send(downTime, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, x, y)
    }

    fun cancel() {
        handler.removeCallbacksAndMessages(null)
        dragging = false
    }

    private fun send(down: Long, time: Long, action: Int, px: Float, py: Float) {
        val event = MotionEvent.obtain(down, time, action, px, py, 0)
        event.source = InputDevice.SOURCE_TOUCHSCREEN
        dispatch(event)
        event.recycle()
    }

    private companion object {
        const val TAP_DURATION_MS = 40L
        const val DRAG_END_DELAY_MS = 150L
        const val TAP_ANCHOR_MS = 4000L
    }
}
