package pl.czajus.aayoutube

import android.util.Log
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebExtensionController

/**
 * Ad blocking with uBlock Origin from Mozilla's add-on site (addons.mozilla.org). It is installed
 * into the shared runtime on the first start, so the phone and the car screen both use it, and
 * checked for updates on every start after that. Installed add-ons stay in the GeckoView profile.
 */
object AdBlocker {

    private const val TAG = "AAYouTube"
    private const val UBLOCK_ID = "uBlock0@raymondhill.net"
    private const val UBLOCK_URL =
        "https://addons.mozilla.org/firefox/downloads/latest/ublock-origin/latest.xpi"

    fun setUp(runtime: GeckoRuntime) {
        val controller = runtime.webExtensionController
        // Nobody sees an install prompt: uBlock Origin is the only add-on installed this way.
        controller.setPromptDelegate(object : WebExtensionController.PromptDelegate {
            override fun onInstallPromptRequest(
                extension: WebExtension,
                permissions: Array<String>,
                origins: Array<String>,
                dataCollectionPermissions: Array<String>,
            ): GeckoResult<WebExtension.PermissionPromptResponse> = GeckoResult.fromValue(
                WebExtension.PermissionPromptResponse(extension.id == UBLOCK_ID, false, false),
            )

            override fun onUpdatePrompt(
                extension: WebExtension,
                newPermissions: Array<String>,
                newOrigins: Array<String>,
                newDataCollectionPermissions: Array<String>,
            ): GeckoResult<AllowOrDeny> = GeckoResult.fromValue(
                if (extension.id == UBLOCK_ID) AllowOrDeny.ALLOW else AllowOrDeny.DENY,
            )
        })
        controller.list().accept({ extensions ->
            val installed = extensions?.firstOrNull { it.id == UBLOCK_ID }
            if (installed == null) {
                // Without a connection this fails and is tried again on the next start.
                controller.install(UBLOCK_URL).accept(
                    { Log.i(TAG, "uBlock Origin installed: ${it?.metaData?.version}") },
                    { Log.e(TAG, "uBlock Origin not installed", it) },
                )
            } else {
                controller.update(installed).accept(
                    { Log.i(TAG, "uBlock Origin ${it?.metaData?.version ?: "up to date"}") },
                    { Log.w(TAG, "uBlock Origin update check failed", it) },
                )
            }
        }) { error -> Log.e(TAG, "Add-on list not available", error) }
    }
}
