package pl.czajus.aayoutube.car

import android.graphics.PixelFormat
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.car.app.AppManager
import androidx.car.app.CarContext
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.MediaSession
import pl.czajus.aayoutube.YOUTUBE_HOME
import pl.czajus.aayoutube.geckoRuntime

/**
 * Renders the YouTube site onto the Android Auto map surface.
 *
 * A GeckoView sits in a [BrowserPresentation] on a private virtual display whose output is the
 * car surface. When the car surface goes away (e.g. the driver switches to Google Maps) the
 * virtual display is pointed at an off-screen buffer instead, so the page stays "visible" and
 * keeps playing. Everything is released when the car session ends (cable unplugged).
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

    var canGoBack = false
        private set

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
                media = mediaSession
            }

            override fun onDeactivated(session: GeckoSession, mediaSession: MediaSession) {
                if (media !== mediaSession) return
                media = null
                mediaDuration = 0.0
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
        session.loadUri(YOUTUBE_HOME)
        carContext.getCarService(AppManager::class.java).setSurfaceCallback(this)
    }

    override fun onDestroy(owner: LifecycleOwner) {
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
        touch.cancel()
        val display = virtualDisplay ?: return
        if (width <= 0 || height <= 0) return
        val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        reader.setOnImageAvailableListener({ it.acquireLatestImage()?.close() }, mainHandler)
        display.surface = reader.surface
        offscreen = reader
    }

    override fun onStableAreaChanged(stableArea: Rect) {
        this.stableArea = Rect(stableArea)
        applyInsets()
    }

    override fun onVisibleAreaChanged(visibleArea: Rect) = Unit

    override fun onClick(x: Float, y: Float) = touch.click(x, y)

    override fun onScroll(distanceX: Float, distanceY: Float) {
        val area = stableArea ?: Rect(0, 0, width, height)
        touch.scroll(distanceX, distanceY, area.exactCenterX(), area.exactCenterY())
    }

    override fun onFling(velocityX: Float, velocityY: Float) = touch.endDrag()

    override fun onScale(focusX: Float, focusY: Float, scaleFactor: Float) = Unit

    // ---------------------------------------------------------------------------------------

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
        const val VIRTUAL_DISPLAY_NAME = "AAYouTube"
        const val DEFAULT_DPI = 160
    }
}
