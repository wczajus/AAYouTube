package pl.czajus.aayoutube.car

import android.graphics.PixelFormat
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.ImageReader
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.car.app.AppManager
import androidx.car.app.CarContext
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import org.json.JSONObject
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.MediaSession
import org.mozilla.geckoview.WebExtension
import pl.czajus.aayoutube.YOUTUBE_HOME
import pl.czajus.aayoutube.geckoRuntime
import pl.czajus.aayoutube.withPageTweaks
import kotlin.math.abs

/**
 * Renders the YouTube site onto the Android Auto map surface.
 *
 * A GeckoView sits in a [BrowserPresentation] on a private virtual display whose output is the
 * car surface. When the car surface goes away (e.g. the driver switches to Google Maps) the
 * virtual display is pointed at an off-screen buffer instead, so the page stays "visible" and
 * keeps playing. Everything is released when the car session ends (cable unplugged).
 *
 * The video holds Android's audio focus while it plays, so Android Auto keeps sending its sound
 * to the car and other players (e.g. a radio app) stop, and the other way round.
 */
class CarBrowser(
    private val carContext: CarContext,
    lifecycle: Lifecycle,
) : SurfaceCallback, DefaultLifecycleObserver {

    private val session = GeckoSession()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val touch = TouchInjector { event -> presentation?.dispatchTouch(event) }

    private var virtualDisplay: VirtualDisplay? = null
    private var presentation: BrowserPresentation? = null
    private var offscreen: ImageReader? = null
    private var width = 0
    private var height = 0
    private var dpi = 0
    private var stableArea: Rect? = null

    // The page's media (the YouTube video), controlled directly so the car buttons can seek.
    // The playback position is the last reported one, extrapolated with the playback rate.
    private var media: MediaSession? = null
    private var mediaPosition = 0.0
    private var mediaRate = 0.0
    private var mediaDuration = 0.0
    private var mediaPositionTime = 0L
    private var mediaPlaying = false

    private val audioManager = carContext.getSystemService(AudioManager::class.java)
    private val audioFocus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                .build(),
        )
        .setOnAudioFocusChangeListener({ change -> onAudioFocusChange(change) }, mainHandler)
        .build()
    private var hasAudioFocus = false
    private var resumeOnFocusGain = false

    // Connection to the built-in add-on in the page (assets/aayt), for the fullscreen button.
    private var pagePort: WebExtension.Port? = null

    private val pageMessages = object : WebExtension.MessageDelegate {
        override fun onConnect(port: WebExtension.Port) {
            Log.d(TAG, "Page connected")
            pagePort = port
            port.setDelegate(object : WebExtension.PortDelegate {
                override fun onPortMessage(message: Any, port: WebExtension.Port) {
                    val json = message as? JSONObject ?: return
                    json.optString("log").takeIf { it.isNotEmpty() }?.let { Log.d(TAG, "Page: $it") }
                    if (json.has("fullscreen") && pagePort === port) {
                        setPageFullscreen(json.optBoolean("fullscreen"))
                    }
                }

                override fun onDisconnect(port: WebExtension.Port) {
                    // A new page connects before the old one goes away.
                    if (pagePort !== port) return
                    pagePort = null
                    setPageFullscreen(false)
                }
            })
        }
    }

    // Two taps close together on the car screen.
    private var lastClickTime = 0L
    private var lastClickX = 0f
    private var lastClickY = 0f

    var canGoBack = false
        private set

    /** Whether the page shows the video player stretched over the whole screen. */
    var pageFullscreen = false
        private set

    var onFullscreenChanged: (() -> Unit)? = null

    // Last, as adding the observer can run onCreate right away.
    init {
        lifecycle.addObserver(this)
    }

    override fun onCreate(owner: LifecycleOwner) {
        session.navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) {
                this@CarBrowser.canGoBack = canGoBack
            }
        }
        session.mediaSessionDelegate = object : MediaSession.Delegate {
            override fun onActivated(session: GeckoSession, mediaSession: MediaSession) {
                Log.d(TAG, "Media activated")
                media = mediaSession
            }

            override fun onDeactivated(session: GeckoSession, mediaSession: MediaSession) {
                Log.d(TAG, "Media deactivated")
                if (media !== mediaSession) return
                media = null
                mediaDuration = 0.0
                mediaPlaying = false
                abandonAudioFocus()
            }

            override fun onPlay(session: GeckoSession, mediaSession: MediaSession) {
                Log.d(TAG, "Media playing")
                media = mediaSession
                mediaPlaying = true
                resumeOnFocusGain = false
                requestAudioFocus()
            }

            override fun onPause(session: GeckoSession, mediaSession: MediaSession) {
                Log.d(TAG, "Media paused")
                // Focus is kept while paused, so the radio does not jump back in between videos.
                mediaPlaying = false
            }

            override fun onStop(session: GeckoSession, mediaSession: MediaSession) {
                Log.d(TAG, "Media stopped")
                mediaPlaying = false
                resumeOnFocusGain = false
                abandonAudioFocus()
            }

            override fun onPositionState(
                session: GeckoSession,
                mediaSession: MediaSession,
                state: MediaSession.PositionState,
            ) {
                mediaPosition = state.position
                mediaRate = state.playbackRate
                mediaDuration = state.duration
                mediaPositionTime = SystemClock.elapsedRealtime()
            }
        }
        session.open(carContext.geckoRuntime)
        // The page connects to the app as soon as it starts loading, so the add-on and the
        // message delegate must be in place first.
        carContext.withPageTweaks { extension ->
            if (!session.isOpen) return@withPageTweaks
            if (extension != null) {
                session.webExtensionController.setMessageDelegate(extension, pageMessages, NATIVE_APP)
            }
            session.loadUri(YOUTUBE_HOME)
        }
        carContext.getCarService(AppManager::class.java).setSurfaceCallback(this)
    }

    override fun onDestroy(owner: LifecycleOwner) {
        abandonAudioFocus()
        pagePort = null
        touch.cancel()
        presentation?.dismiss()
        presentation = null
        virtualDisplay?.release()
        virtualDisplay = null
        offscreen?.close()
        offscreen = null
        session.close()
    }

    fun load(url: String) = session.loadUri(url)

    fun goBack() {
        if (canGoBack) session.goBack()
    }

    /** Stretches the video player over the whole screen, or brings the page back. */
    fun toggleFullscreen() = sendToPage(if (pageFullscreen) "exitFullscreen" else "enterFullscreen")

    /** Moves the playing video by [seconds] (negative = back). */
    fun seekBy(seconds: Double) {
        val media = media ?: return
        if (!(mediaDuration > 0.0)) {
            // Length not known (yet): let Gecko pick the step.
            if (seconds < 0) media.seekBackward() else media.seekForward()
            return
        }
        val now = SystemClock.elapsedRealtime()
        val current = mediaPosition + (now - mediaPositionTime) / 1000.0 * mediaRate
        val target = (current + seconds).coerceIn(0.0, mediaDuration)
        media.seekTo(target, false)
        // Remember the target so quick repeated presses add up before the page reports back.
        mediaPosition = target
        mediaPositionTime = now
    }

    // --- SurfaceCallback -------------------------------------------------------------------

    override fun onSurfaceAvailable(surfaceContainer: SurfaceContainer) {
        Log.d(TAG, "Surface available ${surfaceContainer.width}x${surfaceContainer.height} " +
            "dpi ${surfaceContainer.dpi}")
        val surface = surfaceContainer.surface ?: return
        val newDpi = surfaceContainer.dpi.takeIf { it > 0 } ?: DEFAULT_DPI
        val sizeChanged = surfaceContainer.width != width ||
            surfaceContainer.height != height || newDpi != dpi
        width = surfaceContainer.width
        height = surfaceContainer.height
        dpi = newDpi

        val display = virtualDisplay
        if (display == null) {
            val displayManager = carContext.getSystemService(DisplayManager::class.java) ?: return
            virtualDisplay = displayManager.createVirtualDisplay(
                VIRTUAL_DISPLAY_NAME, width, height, dpi, surface,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY,
            )
        } else {
            if (sizeChanged) {
                // A Presentation cannot follow a display resize, so it is recreated below.
                presentation?.dismiss()
                presentation = null
                display.resize(width, height, dpi)
            }
            display.surface = surface
        }
        offscreen?.close()
        offscreen = null
        ensurePresentation()
    }

    override fun onSurfaceDestroyed(surfaceContainer: SurfaceContainer) {
        Log.d(TAG, "Surface destroyed")
        touch.cancel()
        val display = virtualDisplay ?: return
        if (width <= 0 || height <= 0) return
        val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        reader.setOnImageAvailableListener({ it.acquireLatestImage()?.close() }, mainHandler)
        display.surface = reader.surface
        offscreen = reader
    }

    override fun onStableAreaChanged(stableArea: Rect) {
        Log.d(TAG, "Stable area $stableArea")
        this.stableArea = Rect(stableArea)
        applyInsets()
    }

    override fun onVisibleAreaChanged(visibleArea: Rect) {
        Log.d(TAG, "Visible area $visibleArea")
    }

    override fun onClick(x: Float, y: Float) {
        Log.d(TAG, "Click $x,$y")
        val now = SystemClock.uptimeMillis()
        val secondTap = now - lastClickTime <= DOUBLE_TAP_MS &&
            abs(x - lastClickX) <= DOUBLE_TAP_SLOP && abs(y - lastClickY) <= DOUBLE_TAP_SLOP
        lastClickTime = if (secondTap) 0L else now
        lastClickX = x
        lastClickY = y
        if (secondTap && pageFullscreen) {
            sendToPage("exitFullscreen")
        } else {
            touch.click(x, y)
        }
    }

    override fun onScroll(distanceX: Float, distanceY: Float) {
        if (!touch.dragging) Log.d(TAG, "Scroll start $distanceX,$distanceY")
        val area = stableArea ?: Rect(0, 0, width, height)
        touch.scroll(distanceX, distanceY, area.exactCenterX(), area.exactCenterY())
    }

    override fun onFling(velocityX: Float, velocityY: Float) = touch.endDrag()

    override fun onScale(focusX: Float, focusY: Float, scaleFactor: Float) {
        Log.d(TAG, "Scale $focusX,$focusY by $scaleFactor")
        // Android Auto may report a double tap as one big zoom-in step; a pinch comes as many
        // small steps and is ignored (the page cannot be zoomed).
        if (scaleFactor < DOUBLE_TAP_SCALE) return
        if (pageFullscreen) {
            sendToPage("exitFullscreen")
            return
        }
        val area = stableArea ?: Rect(0, 0, width, height)
        val x = if (focusX >= 0) focusX else area.exactCenterX()
        val y = if (focusY >= 0) focusY else area.exactCenterY()
        // The first tap may already have come as a click.
        if (SystemClock.uptimeMillis() - lastClickTime <= DOUBLE_TAP_MS) {
            touch.click(x, y)
        } else {
            touch.doubleClick(x, y)
        }
        lastClickTime = 0L
    }

    // ---------------------------------------------------------------------------------------

    private fun sendToPage(action: String) {
        Log.d(TAG, "Page action $action")
        val port = pagePort
        if (port == null) {
            Log.w(TAG, "Page not connected")
            return
        }
        port.postMessage(JSONObject().put("action", action))
    }

    private fun setPageFullscreen(fullscreen: Boolean) {
        if (pageFullscreen == fullscreen) return
        Log.d(TAG, "Page fullscreen $fullscreen")
        pageFullscreen = fullscreen
        onFullscreenChanged?.invoke()
    }

    private fun requestAudioFocus() {
        if (hasAudioFocus) return
        val result = audioManager?.requestAudioFocus(audioFocus)
        Log.d(TAG, "Audio focus request: $result")
        hasAudioFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        // Not granted, e.g. during a phone call: the sound would not reach the car anyway.
        if (!hasAudioFocus) media?.pause()
    }

    private fun abandonAudioFocus() {
        if (!hasAudioFocus) return
        audioManager?.abandonAudioFocusRequest(audioFocus)
        hasAudioFocus = false
    }

    private fun onAudioFocusChange(change: Int) {
        Log.d(TAG, "Audio focus change $change")
        when (change) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                hasAudioFocus = true
                if (resumeOnFocusGain) media?.play()
                resumeOnFocusGain = false
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                // Another player started (e.g. the radio): stop for good.
                hasAudioFocus = false
                resumeOnFocusGain = false
                media?.pause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                // E.g. a phone call or the voice assistant: carry on afterwards.
                resumeOnFocusGain = mediaPlaying
                media?.pause()
            }
            // AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK (navigation prompts): Android lowers the volume.
        }
    }

    private fun ensurePresentation() {
        if (presentation != null) return
        val display = virtualDisplay?.display ?: return
        val created = BrowserPresentation(carContext, display, session)
        created.setOnDismissListener { if (presentation === created) presentation = null }
        presentation = created
        created.show()
        applyInsets()
    }

    /** Keeps the page clear of the Android Auto buttons drawn over the surface. */
    private fun applyInsets() {
        val area = stableArea ?: return
        if (width <= 0 || height <= 0) return
        presentation?.setInsets(
            Rect(area.left, area.top, (width - area.right).coerceAtLeast(0),
                (height - area.bottom).coerceAtLeast(0)),
        )
    }

    private companion object {
        const val TAG = "AAYouTubeCar"
        const val VIRTUAL_DISPLAY_NAME = "AAYouTube"
        const val DEFAULT_DPI = 160

        // Must match browser.runtime.connectNative() in assets/aayt/fullscreen.js.
        const val NATIVE_APP = "aayt"

        const val DOUBLE_TAP_MS = 400L
        const val DOUBLE_TAP_SLOP = 60f
        const val DOUBLE_TAP_SCALE = 1.9f
    }
}
