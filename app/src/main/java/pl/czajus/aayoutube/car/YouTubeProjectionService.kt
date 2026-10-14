package pl.czajus.aayoutube.car

import com.google.android.apps.auto.sdk.CarActivity
import com.google.android.apps.auto.sdk.CarActivityService

/**
 * Entry point for Android Auto's projection mode (the mechanism Fermata Auto uses). Android Auto
 * binds it while the phone is connected; it is destroyed when the cable is unplugged.
 */
class YouTubeProjectionService : CarActivityService() {

    override fun getCarActivity(): Class<out CarActivity> = YouTubeCarActivity::class.java

    override fun onDestroy() {
        // Disconnected from the car: stop playback.
        CarBrowserSession.release()
        super.onDestroy()
    }
}
