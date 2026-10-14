package pl.czajus.aayoutube.car

import android.content.Context
import org.mozilla.geckoview.GeckoSession
import pl.czajus.aayoutube.YOUTUBE_HOME
import pl.czajus.aayoutube.geckoRuntime

/** The browser tab shown in the car. Outlives the car screen so playback survives app switches. */
object CarBrowserSession {

    private var session: GeckoSession? = null

    var canGoBack = false
        private set

    fun get(context: Context): GeckoSession =
        session ?: GeckoSession().also { created ->
            created.navigationDelegate = object : GeckoSession.NavigationDelegate {
                override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) {
                    this@CarBrowserSession.canGoBack = canGoBack
                }
            }
            created.open(context.geckoRuntime)
            created.loadUri(YOUTUBE_HOME)
            session = created
        }

    fun release() {
        session?.close()
        session = null
        canGoBack = false
    }
}
