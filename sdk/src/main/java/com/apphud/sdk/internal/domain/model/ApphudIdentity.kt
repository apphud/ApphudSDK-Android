package com.apphud.sdk.internal.domain.model

/** The ids of the current install and person. */
internal data class ApphudIdentity(
    /** One per install: new after a reinstall, kept across logout. */
    val installationId: String,
    /** The anonymous person: new after logout or a reinstall, kept by `updateUserId`. */
    val anonymousId: String,
    /** The app's own user id, set by identify. Null while anonymous. */
    val userId: String?,
) {
    /** Whoever is current: the user once identified, the anonymous person before. */
    val currentId: String get() = userId ?: anonymousId

    /** Sent on every request in place of the old `device_id`. */
    val requestParams: Map<String, String>
        get() = mapOf("installation_id" to installationId, "anonymous_id" to anonymousId)

    val requestIds: RequestIds
        get() = RequestIds(installationId = installationId, anonymousId = anonymousId, userId = userId)
}

/**
 * The ids a request carries. Identity version 1 sends `device_id`, and `user_id` where SDK 3.6 sent
 * it; version 2 sends `installation_id` and `anonymous_id`, and `user_id` once identified.
 */
internal data class RequestIds(
    /** Identity version 1 only. */
    val deviceId: String? = null,
    /** Identity version 2 only. */
    val installationId: String? = null,
    /** Identity version 2 only. */
    val anonymousId: String? = null,
    /** Version 1: its user id. Version 2: the identified user, null while anonymous. */
    val userId: String? = null,
) {
    val isVersion1: Boolean get() = deviceId != null

    /** The ids as query parameters. */
    val requestParams: Map<String, String>
        get() = if (deviceId != null) {
            mapOf("device_id" to deviceId)
        } else {
            mapOf("installation_id" to installationId.orEmpty(), "anonymous_id" to anonymousId.orEmpty())
        }

    /** `user_id` of the requests SDK 3.6 sent without one: version 2 only. */
    val identifiedUserId: String? get() = userId.takeUnless { isVersion1 }
}

/** Ids the host passed to `start`. A blank string means "not supplied". */
internal data class ApphudSuppliedIds(
    val userId: String? = null,
    val anonymousId: String? = null,
    val installationId: String? = null,
    val isReinstall: Boolean? = null,
    /** `start(deviceId)`: identity version 1's device id. Version 2 takes it as the installation id. */
    val deviceId: String? = null,
) {
    /**
     * The app supplies every id and decides the reinstall flag itself. The SDK stores what it was
     * given and never generates its own.
     */
    val suppliesAllIds: Boolean get() = installationId != null && anonymousId != null

    companion object {
        fun of(
            userId: String? = null,
            anonymousId: String? = null,
            installationId: String? = null,
            isReinstall: Boolean? = null,
            deviceId: String? = null,
        ) = ApphudSuppliedIds(
            userId = userId?.takeUnless { it.isBlank() },
            anonymousId = anonymousId?.takeUnless { it.isBlank() },
            installationId = installationId?.takeUnless { it.isBlank() },
            isReinstall = isReinstall,
            deviceId = deviceId?.takeUnless { it.isBlank() },
        )
    }
}

internal enum class ApphudLaunchKind {
    /** `installation_id` already stored. */
    NORMAL,

    /** No `installation_id` yet, ids an old SDK version left: an in-place update. */
    UPGRADE,

    /** A first install or a reinstall. */
    NEW_INSTALL,
}

internal data class ApphudIdentityResolution(
    val identity: ApphudIdentity,
    val launch: ApphudLaunchKind,
    /** The ids changed in this launch, so the backend has to see them. */
    val changed: Boolean,
    /** The host passed another user while one was stored, without logout. */
    val switchedUser: Boolean = false,
    /** The ids came back with data Auto Backup restored: the reinstall check decides whether they stay. */
    val restoredPersonPending: Boolean = false,
    /** The old SDK's `device_id` of a record that couldn't be saved: still sent this launch. */
    val unsavedLegacyDeviceId: String? = null,
    /** The person changed (another user, or the host's new anonymous id): `legacy_device_id` is not sent. */
    val dropsLegacyDeviceId: Boolean = false,
)

/** Identity version 1 after version 2 ran here: the ids it sends, so the backend finds the same customer. */
internal data class Version1Ids(
    /** The old device id, or the installation id when the install started on version 2. */
    val deviceId: String?,
    /** The identified user, or the anonymous id. */
    val userId: String,
)

internal enum class ApphudIdentifyOutcome {
    UNCHANGED,
    IDENTIFIED,

    /** A different user while identified, without logout. */
    SWITCHED,
}
