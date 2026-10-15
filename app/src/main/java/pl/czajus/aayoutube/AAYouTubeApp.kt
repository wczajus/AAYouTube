package pl.czajus.aayoutube

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings
import org.mozilla.geckoview.WebExtension

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
                .accept({ extension -> pageTweaksReady(extension) }) { error ->
                    Log.e(TAG, "Page tweaks not installed", error)
                    pageTweaksReady(null)
                }
            AdBlocker.setUp(runtime)
        }
    }

    private var pageTweaks: WebExtension? = null
    private var pageTweaksDone = false
    private val pageTweaksWaiting = mutableListOf<(WebExtension?) -> Unit>()

    /** Runs [block] once the built-in add-on is installed, with null if that failed. */
    fun withPageTweaks(block: (WebExtension?) -> Unit) {
        geckoRuntime // Starts the installation.
        if (pageTweaksDone) block(pageTweaks) else pageTweaksWaiting += block
    }

    private fun pageTweaksReady(extension: WebExtension?) {
        pageTweaks = extension
        pageTweaksDone = true
        pageTweaksWaiting.forEach { it(extension) }
        pageTweaksWaiting.clear()
    }

    private companion object {
        const val TAG = "AAYouTube"
    }
}

val Context.geckoRuntime: GeckoRuntime
    get() = (applicationContext as AAYouTubeApp).geckoRuntime

fun Context.withPageTweaks(block: (WebExtension?) -> Unit) =
    (applicationContext as AAYouTubeApp).withPageTweaks(block)
