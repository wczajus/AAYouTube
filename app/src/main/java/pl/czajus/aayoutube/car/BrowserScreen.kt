package pl.czajus.aayoutube.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarIcon
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.core.graphics.drawable.IconCompat
import pl.czajus.aayoutube.R
import pl.czajus.aayoutube.YOUTUBE_HOME
import pl.czajus.aayoutube.youTubeSearchUrl

/** Main car screen: the YouTube page on the map surface plus a few buttons. */
class BrowserScreen(
    carContext: CarContext,
    private val browser: CarBrowser,
) : Screen(carContext) {

    override fun onGetTemplate(): Template {
        val actions = ActionStrip.Builder()
            .addAction(textAction(R.string.action_search) { openSearch() })
            .addAction(textAction(R.string.action_back) { browser.goBack() })
            .addAction(textAction(R.string.action_home) { browser.load(YOUTUBE_HOME) })
            .build()

        // Map buttons take icons only. The pan button lets Android Auto forward drag gestures
        // (page scrolling) to the surface.
        val mapActions = ActionStrip.Builder()
            .addAction(iconAction(R.drawable.ic_seek_back) { browser.seekBy(-SEEK_SECONDS) })
            .addAction(iconAction(R.drawable.ic_seek_forward) { browser.seekBy(SEEK_SECONDS) })
            .addAction(Action.PAN)
            .build()

        return NavigationTemplate.Builder()
            .setActionStrip(actions)
            .setMapActionStrip(mapActions)
            .setPanModeListener { }
            .build()
    }

    private fun openSearch() {
        screenManager.pushForResult(SearchScreen(carContext)) { result ->
            (result as? String)?.takeIf { it.isNotBlank() }?.let { browser.load(youTubeSearchUrl(it)) }
        }
    }

    private fun textAction(title: Int, onClick: () -> Unit): Action =
        Action.Builder()
            .setTitle(carContext.getString(title))
            .setOnClickListener { onClick() }
            .build()

    private fun iconAction(icon: Int, onClick: () -> Unit): Action =
        Action.Builder()
            .setIcon(CarIcon.Builder(IconCompat.createWithResource(carContext, icon)).build())
            .setOnClickListener { onClick() }
            .build()

    private companion object {
        const val SEEK_SECONDS = 10.0
    }
}
