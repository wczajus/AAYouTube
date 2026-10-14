package pl.czajus.aayoutube.car

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.LinearLayout
import com.google.android.apps.auto.sdk.CarActivity
import org.mozilla.geckoview.GeckoView
import pl.czajus.aayoutube.R
import pl.czajus.aayoutube.YOUTUBE_HOME
import pl.czajus.aayoutube.youTubeSearchUrl

/** The car screen: a toolbar (search, back, home) above the YouTube page. */
class YouTubeCarActivity : CarActivity() {

    private var geckoView: GeckoView? = null
    private var searchField: CarSearchEditText? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setIgnoreConfigChanges(ALL_CONFIG_CHANGES)
        carUiController.statusBarController.hideAppHeader()
        carUiController.menuController.hideMenuButton()

        val session = CarBrowserSession.get(this)

        val search = CarSearchEditText(this) { query -> submitSearch(query) }.apply {
            hint = getString(R.string.search_hint)
            visibility = View.GONE
        }
        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.BLACK)
            addView(toolbarButton(R.string.action_search) { startSearch() })
            addView(toolbarButton(R.string.action_back) { goBack() })
            addView(toolbarButton(R.string.action_home) { session.loadUri(YOUTUBE_HOME) })
            addView(search, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        }
        val view = GeckoView(this)
        view.setSession(session)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            addView(toolbar, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            addView(view, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        }
        setContentView(root)
        geckoView = view
        searchField = search
    }

    override fun onDestroy() {
        // Keep the session (and playback) alive; only detach it from this screen.
        geckoView?.releaseSession()
        geckoView = null
        super.onDestroy()
    }

    override fun onBackPressed() {
        if (CarBrowserSession.canGoBack) goBack() else super.onBackPressed()
    }

    private fun startSearch() {
        val field = searchField ?: return
        field.setText("")
        field.visibility = View.VISIBLE
        a().startInput(field)
    }

    private fun submitSearch(query: String) {
        a().stopInput()
        searchField?.visibility = View.GONE
        if (query.isNotBlank()) CarBrowserSession.get(this).loadUri(youTubeSearchUrl(query))
    }

    private fun goBack() {
        if (CarBrowserSession.canGoBack) CarBrowserSession.get(this).goBack()
    }

    private fun toolbarButton(title: Int, onClick: () -> Unit): Button =
        Button(this).apply {
            text = getString(title)
            setOnClickListener { onClick() }
        }

    private companion object {
        const val ALL_CONFIG_CHANGES = -1 // 0xFFFFFFFF: handle every configuration change ourselves.
    }
}
