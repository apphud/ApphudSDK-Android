package com.apphud.sdk

import android.net.Uri

/**
 * Indicates how a deep link attribution result was obtained.
 *
 * - [DIRECT]: the user opened an actual deep link (App Link or custom scheme URL).
 * - [DEFERRED]: attribution was resolved for the current installation without an explicit link,
 *   typically right after install.
 */
enum class ApphudDeeplinkAttributionKind {
    DIRECT,
    DEFERRED,
}

/**
 * Callback that receives deep link attribution updates.
 *
 * It may be invoked multiple times during the app lifecycle for both direct and deferred flows.
 * When no attribution match is found, [attribution] is an empty map.
 *
 * @param attribution The attribution data returned by Apphud.
 * @param kind Whether the attribution came from a direct link open or a deferred lookup.
 * @param uri The original deep link [Uri] for direct opens, or `null` for deferred attribution.
 */
typealias ApphudDeeplinkHandler = (
    attribution: Map<String, Any>,
    kind: ApphudDeeplinkAttributionKind,
    uri: Uri?,
) -> Unit
