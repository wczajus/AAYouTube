package pl.czajus.aayoutube

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView
import pl.czajus.aayoutube.install.installAsFromPlayStore
import pl.czajus.aayoutube.install.isInstalledFromPlayStore

/** Phone screen: the same YouTube site, mainly for signing in to the Google account. */
class MainActivity : AppCompatActivity() {

    private val session = GeckoSession()
    private var canGoBack = false
    private val pickApk = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) installAsFromPlayStore(uri)
    }

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

        requireViewById<Button>(R.id.install_button).setOnClickListener {
            pickApk.launch(arrayOf("application/vnd.android.package-archive", "application/octet-stream"))
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (canGoBack) session.goBack() else finish()
            }
        })
    }

    override fun onResume() {
        super.onResume()
        requireViewById<TextView>(R.id.install_status).setText(
            if (isInstalledFromPlayStore()) R.string.install_status_ok else R.string.install_status_missing
        )
    }

    override fun onDestroy() {
        session.close()
        super.onDestroy()
    }
}
