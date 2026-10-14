package pl.czajus.aayoutube.car

import android.content.Intent
import androidx.car.app.CarAppService
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator

class YouTubeCarAppService : CarAppService() {

    // Personal, sideloaded app: accept any Android Auto host.
    override fun createHostValidator(): HostValidator = HostValidator.ALLOW_ALL_HOSTS_VALIDATOR

    override fun onCreateSession(): Session = YouTubeCarSession()
}

/** Lives as long as the phone is connected to the car; ending it stops playback. */
class YouTubeCarSession : Session() {

    override fun onCreateScreen(intent: Intent): Screen {
        val browser = CarBrowser(carContext, lifecycle)
        return BrowserScreen(carContext, browser)
    }
}
