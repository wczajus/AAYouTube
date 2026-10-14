package pl.czajus.aayoutube

import android.app.Application
import android.content.Context
import android.net.Uri
import org.mozilla.geckoview.GeckoRuntime

const val YOUTUBE_HOME = "https://m.youtube.com/"

fun youTubeSearchUrl(query: String): String =
    "https://m.youtube.com/results?search_query=" + Uri.encode(query)

class AAYouTubeApp : Application() {
    // One runtime per process, shared by the phone screen and the car screen so they share
    // cookies (and therefore the YouTube login). Created lazily: GeckoView's own child
    // processes also instantiate this Application class and must not start a runtime.
    val geckoRuntime: GeckoRuntime by lazy { GeckoRuntime.create(this) }
}

val Context.geckoRuntime: GeckoRuntime
    get() = (applicationContext as AAYouTubeApp).geckoRuntime
