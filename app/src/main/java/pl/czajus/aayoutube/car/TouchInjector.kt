package pl.czajus.aayoutube.car

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent

/**
 * Turns the gestures Android Auto reports for the map surface (tap, scroll, fling) into touch
 * events for the web page. Android Auto only reports scroll distances, so a drag is simulated
 * from an anchor point and ended shortly after the last scroll update.
 */
class TouchInjector(private val dispatch: (MotionEvent) -> Unit) {

    private val handler = Handler(Looper.getMainLooper())
    private val endDragRunnable = Runnable { endDrag() }
    private var dragging = false
    private var downTime = 0L
    private var x = 0f
    private var y = 0f

    fun click(px: Float, py: Float) {
        endDrag()
        val down = SystemClock.uptimeMillis()
        send(down, down, MotionEvent.ACTION_DOWN, px, py)
        handler.postDelayed({
            send(down, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, px, py)
        }, TAP_DURATION_MS)
    }

    fun scroll(distanceX: Float, distanceY: Float, anchorX: Float, anchorY: Float) {
        val now = SystemClock.uptimeMillis()
        if (!dragging) {
            dragging = true
            downTime = now
            x = anchorX
            y = anchorY
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
    }
}
