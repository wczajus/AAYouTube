package pl.czajus.aayoutube

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings

const val YOUTUBE_HOME = "https://m.youtube.com/"

// Built-in add-on with changes to the YouTube page (app/src/main/assets/aayt).
private const val PAGE_TWEAKS_URL = "resource://android/assets/aayt/"
private const val PAGE_TWEAKS_ID = "aayt@czajus.pl"

fun youTubeSearchUrl(query: String): String =
    "https://m.youtube.com/results?search_query=" + Uri.encode(query)

class AAYouTubeApp : Application() {
    // One runtime per process, shared by the phone screen and the car screen so they share
    // cookies (and therefore the YouTube login). Created lazily: GeckoView's own child
    // processes also instantiate this Application class and must not start a runtime.
    val geckoRuntime: GeckoRuntime by lazy {
        val settings = GeckoRuntimeSettings.Builder()
            // Page console messages land in logcat (tag GeckoConsole), for car-test logs.
            .consoleOutput(true)
            .build()
        GeckoRuntime.create(this, settings).also { runtime ->
            runtime.webExtensionController.ensureBuiltIn(PAGE_TWEAKS_URL, PAGE_TWEAKS_ID)
                .accept(null) { error -> Log.e(TAG, "Page tweaks not installed", error) }
        }
    }

    private companion object {
        const val TAG = "AAYouTube"
    }
}

val Context.geckoRuntime: GeckoRuntime
    get() = (applicationContext as AAYouTubeApp).geckoRuntime
