package pl.czajus.aayoutube.car

import android.app.Presentation
import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.os.Bundle
import android.view.Display
import android.view.MotionEvent
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView

/** Full-screen GeckoView shown on the virtual display that mirrors into the car. */
class BrowserPresentation(
    outerContext: Context,
    display: Display,
    private val session: GeckoSession,
) : Presentation(outerContext, display) {

    private var root: FrameLayout? = null
    private var geckoView: GeckoView? = null
    private var insets = Rect()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val view = GeckoView(context)
        view.setSession(session)
        val frame = FrameLayout(context).apply {
            setBackgroundColor(Color.BLACK)
            addView(view, MATCH_PARENT, MATCH_PARENT)
        }
        setContentView(frame)
        geckoView = view
        root = frame
        applyInsets()
    }

    override fun onStop() {
        // Detach so the same session can be attached to a new presentation later.
        geckoView?.releaseSession()
        super.onStop()
    }

    fun setInsets(insets: Rect) {
        this.insets = Rect(insets)
        applyInsets()
    }

    fun dispatchTouch(event: MotionEvent) {
        window?.decorView?.dispatchTouchEvent(event)
    }

    private fun applyInsets() {
        root?.setPadding(insets.left, insets.top, insets.right, insets.bottom)
    }
}
