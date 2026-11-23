package com.streammaster.tv.cast

import android.content.Context
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastOptions
import com.google.android.gms.cast.framework.OptionsProvider
import com.google.android.gms.cast.framework.SessionProvider

/**
 * SPEC §4.46 — Chromecast receiver options.
 *
 * Uses the public **Default Media Receiver** (`CC1AD845`, Google's stock
 * receiver — no custom receiver app needed, no secret). A custom receiver
 * can replace it later by changing [RECEIVER_APP_ID] only.
 *
 * INTEGRATION (not done here — AndroidManifest.xml belongs to another
 * builder): register this provider in the manifest, otherwise
 * `CastContext.getSharedInstance()` throws and [CastManager] reports
 * `isCastAvailable = false`:
 *
 * ```xml
 * <meta-data
 *     android:name="com.google.android.gms.cast.framework.OPTIONS_PROVIDER_CLASS_NAME"
 *     android:value="com.streammaster.tv.cast.StreamMasterCastOptionsProvider" />
 * ```
 */
class StreamMasterCastOptionsProvider : OptionsProvider {

    override fun getCastOptions(context: Context): CastOptions =
        CastOptions.Builder()
            .setReceiverApplicationId(RECEIVER_APP_ID)
            .build()

    override fun getAdditionalSessionProviders(context: Context): List<SessionProvider>? = null

    companion object {
        /** Google Default Media Receiver — public, well-known app ID. */
        const val RECEIVER_APP_ID = "CC1AD845"
    }
}
