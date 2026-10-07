package com.apphud.sdk.storage

/**
 * Persisted client-session state. Device-scoped: survives logout.
 */
internal interface ClientSessionStorage {
    var clientSessionId: String?
    var clientSessionLastBackgroundAt: Long
}
