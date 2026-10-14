package pl.czajus.aayoutube.media

import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.session.MediaSessionCompat
import androidx.media.MediaBrowserServiceCompat
import pl.czajus.aayoutube.R

/**
 * Minimal Android Auto media service. Media apps are listed in Android Auto without the Google
 * Play check, and Fermata ships one next to its projection screen. For now it only lists a hint
 * pointing to the video screen; playback controls can be wired to the YouTube page later.
 */
class YouTubeMediaService : MediaBrowserServiceCompat() {

    private lateinit var mediaSession: MediaSessionCompat

    override fun onCreate() {
        super.onCreate()
        mediaSession = MediaSessionCompat(this, "AAYouTube")
        sessionToken = mediaSession.sessionToken
    }

    override fun onDestroy() {
        mediaSession.release()
        super.onDestroy()
    }

    override fun onGetRoot(clientPackageName: String, clientUid: Int, rootHints: Bundle?): BrowserRoot =
        BrowserRoot(ROOT_ID, null)

    override fun onLoadChildren(
        parentId: String,
        result: Result<MutableList<MediaBrowserCompat.MediaItem>>,
    ) {
        if (parentId != ROOT_ID) {
            result.sendResult(mutableListOf())
            return
        }
        val hint = MediaDescriptionCompat.Builder()
            .setMediaId(HINT_ID)
            .setTitle(getString(R.string.media_hint_title))
            .setSubtitle(getString(R.string.media_hint_subtitle))
            .build()
        result.sendResult(mutableListOf(MediaBrowserCompat.MediaItem(hint, MediaBrowserCompat.MediaItem.FLAG_PLAYABLE)))
    }

    private companion object {
        const val ROOT_ID = "root"
        const val HINT_ID = "hint"
    }
}
