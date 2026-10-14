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
}

/** Ids the host passed to `start`. A blank string means "not supplied". */
internal data class ApphudSuppliedIds(
    val userId: String? = null,
    val anonymousId: String? = null,
    val installationId: String? = null,
    val isReinstall: Boolean? = null,
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
        ) = ApphudSuppliedIds(
            userId = userId?.takeUnless { it.isBlank() },
            anonymousId = anonymousId?.takeUnless { it.isBlank() },
            installationId = installationId?.takeUnless { it.isBlank() },
            isReinstall = isReinstall,
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
)

internal enum class ApphudIdentifyOutcome {
    UNCHANGED,
    IDENTIFIED,

    /** A different user while identified, without logout. */
    SWITCHED,
}
