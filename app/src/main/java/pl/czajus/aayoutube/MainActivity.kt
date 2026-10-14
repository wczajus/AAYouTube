package pl.czajus.aayoutube

import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView

/** Phone screen: the same YouTube site, mainly for signing in to the Google account. */
class MainActivity : AppCompatActivity() {

    private val session = GeckoSession()
    private var canGoBack = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        session.navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) {
                this@MainActivity.canGoBack = canGoBack
            }
        }
        session.open(geckoRuntime)
        requireViewById<GeckoView>(R.id.gecko_view).setSession(session)
        session.loadUri(YOUTUBE_HOME)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (canGoBack) session.goBack() else finish()
            }
        })
    }

    override fun onDestroy() {
        session.close()
        super.onDestroy()
    }
}
