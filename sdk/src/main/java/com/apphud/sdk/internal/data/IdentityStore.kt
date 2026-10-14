package com.apphud.sdk.internal.data

import com.apphud.sdk.ApphudLog
import com.apphud.sdk.internal.domain.model.ApphudIdentifyOutcome
import com.apphud.sdk.internal.domain.model.ApphudIdentity
import com.apphud.sdk.internal.domain.model.ApphudIdentityResolution
import com.apphud.sdk.internal.domain.model.ApphudLaunchKind
import com.apphud.sdk.internal.domain.model.ApphudSuppliedIds
import com.apphud.sdk.storage.InstallRecord
import com.apphud.sdk.storage.InstallRecordStorage
import com.apphud.sdk.storage.InstallState
import com.apphud.sdk.storage.Storage
import java.util.UUID

/**
 * Resolves, persists and changes the three ids.
 *
 * - `installation_id` lives in the install record in `noBackupFilesDir`: uninstall removes it and
 *   Auto Backup never restores it, so it is new after a reinstall and is never read from the
 *   backed-up preferences.
 * - `anonymous_id` and the identified `user_id` live in the preferences.
 * - Old versions kept one generated id as both `device_id` and the customer's `user_id`. An update
 *   in place adopts them: `device_id` becomes `installation_id` and goes as `legacy_device_id`, which
 *   has the backend adopt the old customer, and a `user_id` equal to it is the anonymous id, never a
 *   user.
 * - Auto Backup can restore the preferences onto a new install: after a reinstall on this device, or
 *   onto a new device. The person comes back at start, as on a new device; the reinstall check drops
 *   it ([dropRestoredPerson]) when it finds this device's marker.
 */
internal class IdentityStore(
    private val storage: Storage,
    private val installRecords: InstallRecordStorage,
    /** True when the app hasn't been updated since it was installed; null when unknown. */
    private val isFreshPackageInstall: () -> Boolean?,
    /** Drops the user data the preferences hold, e.g. ones Auto Backup restored onto a new install. */
    private val clearUserData: () -> Unit,
    private val makeId: () -> String = { UUID.randomUUID().toString() },
) {
    @Volatile
    var current: ApphudIdentity? = null
        private set

    /** The ids that came back from a backup, until the reinstall check confirms or drops them. */
    private data class RestoredPerson(val anonymousId: String?, val userId: String?)

    private var restoredPerson: RestoredPerson? = null

    /** The backend hasn't seen the current ids yet, so registration must not be skipped. */
    val isSyncPending: Boolean get() = storage.identitySyncPending

    fun markSynced() {
        storage.identitySyncPending = false
    }

    fun markSyncPending() {
        storage.identitySyncPending = true
    }

    /** Resolves the ids at `start`. */
    @Synchronized
    fun resolve(supplied: ApphudSuppliedIds): ApphudIdentityResolution {
        var damaged = false
        val stored: InstallRecord? = runCatching { installRecords.read() }
            .onFailure {
                damaged = true
                ApphudLog.logE("Install record can't be read: ${it.message}")
            }
            .getOrNull()
            // A record without an installation id gives no id to keep.
            ?.also { if (it.installationId == null) damaged = true }
            ?.takeIf { it.installationId != null }
        val storedAnonymousId = storage.anonymousId.nonBlank()
        val storedUserId = storage.identifiedUserId.nonBlank()

        if (supplied.suppliesAllIds) {
            return resolveSupplied(supplied, stored, storedAnonymousId, storedUserId)
        }

        var anonymousId = storedAnonymousId
        var userId = storedUserId
        var record: InstallRecord
        val launch: ApphudLaunchKind
        val legacyDeviceId = storage.deviceId.nonBlank()
        // The person Auto Backup brought back, still to be confirmed by the reinstall check.
        var restored: RestoredPerson? = null
        // The old SDK's device_id, sent as legacy_device_id until a registration adopts its customer.
        var adoptLegacyDeviceId: String? = null

        if (stored != null) {
            launch = ApphudLaunchKind.NORMAL
            record = stored
            // The launch that found the restored person ended before the check decided.
            if (stored.restoredPerson && stored.state == InstallState.PENDING) {
                restored = RestoredPerson(stored.restoredAnonymousId, stored.restoredUserId)
            }
        } else if (legacyDeviceId != null && isFreshPackageInstall() != true) {
            // In-place update: the old device_id stays as installation_id and goes as legacy_device_id,
            // which has the backend adopt the old customer. A fresh install never takes it as an update:
            // Auto Backup restores old preferences.
            launch = ApphudLaunchKind.UPGRADE
            adoptLegacyDeviceId = legacyDeviceId
            record = InstallRecord(InstallState.EXISTING, legacyDeviceId, legacyDeviceId = legacyDeviceId)
            // Old versions kept the user only in the cached customer.
            val legacyUserId = storage.apphudUser?.userId.nonBlank()
            if (legacyUserId == null || legacyUserId == legacyDeviceId) {
                // A user_id equal to the device_id was generated by the SDK: it is the anonymous id.
                anonymousId = legacyDeviceId
                userId = null
            } else {
                userId = legacyUserId
            }
        } else if (damaged) {
            // The record was there: our own reinstall marker may be too, so it gives no verdict.
            launch = ApphudLaunchKind.NEW_INSTALL
            record = InstallRecord(InstallState.EXISTING, makeId())
        } else {
            launch = ApphudLaunchKind.NEW_INSTALL
            val restoredData = legacyDeviceId != null || storedAnonymousId != null || storedUserId != null || storage.apphudUser != null
            // Only a fresh package can carry data Auto Backup restored; an updated one keeps its person.
            val fresh = isFreshPackageInstall() == true
            if (restoredData && fresh) {
                // Auto Backup restored this data, after a reinstall on this device or onto a new one.
                // Only the reinstall marker tells them apart: until it does, the person is kept, as on
                // a new device. Data from an old version maps as an update does, but never gives the
                // installation id.
                if (anonymousId == null && legacyDeviceId != null) {
                    adoptLegacyDeviceId = legacyDeviceId
                    val legacyUserId = storage.apphudUser?.userId.nonBlank()
                    if (legacyUserId == null || legacyUserId == legacyDeviceId) {
                        anonymousId = legacyDeviceId
                    } else if (userId == null) {
                        userId = legacyUserId
                    }
                }
                ApphudLog.log("New install with restored data: the reinstall check decides the person")
                restored = RestoredPerson(anonymousId, userId)
            }
            // No installation id: a new install, so the reinstall marker decides.
            record = InstallRecord(InstallState.PENDING, makeId(), legacyDeviceId = adoptLegacyDeviceId)
        }

        // The stored user after the update rewrite: an SDK-generated id was never a user.
        val userBeforeSupplied = userId

        // Ids the host supplied are kept as given: never minted over, never rotated.
        supplied.installationId?.let { record = record.copy(installationId = it) }
        supplied.anonymousId?.let { anonymousId = it }
        val resolvedAnonymousId = anonymousId ?: makeId()
        // The anonymous id is never a user: a supplied one is ignored (the stored user stays), a
        // stored one is dropped.
        supplied.userId?.takeIf { it != resolvedAnonymousId }?.let { userId = it }
        if (userId == resolvedAnonymousId) userId = null

        // Only restored ids still in use wait for the check: an id the host supplied stays, even one
        // equal to the restored id.
        val restoredInUse = restored
            ?.let {
                RestoredPerson(
                    anonymousId = it.anonymousId?.takeIf { id -> supplied.anonymousId == null && id == resolvedAnonymousId },
                    userId = it.userId?.takeIf { id -> supplied.userId == null && id == userId },
                )
            }
            ?.takeIf { it.anonymousId != null || it.userId != null }
        // Restored ids no longer in use are forgotten, so an id set again later is never taken for one.
        record = record.copy(restoredAnonymousId = restoredInUse?.anonymousId, restoredUserId = restoredInUse?.userId)
        // The old customer goes with the restored person: none in use, nothing to adopt.
        if (restored != null && restoredInUse == null) record = record.copy(legacyDeviceId = null)
        val clearRestoredData = launch == ApphudLaunchKind.NEW_INSTALL && restored != null

        val changed = record.installationId != stored?.installationId ||
            resolvedAnonymousId != storedAnonymousId || userId != storedUserId
        if (changed) markSyncPending()

        val migrating = launch == ApphudLaunchKind.UPGRADE
        // An update, or a restore, keeps the person before the old data is deleted: a crash in between
        // must not lose it, or leave an SDK-generated user_id behind as a real user.
        if (migrating || clearRestoredData) persist(resolvedAnonymousId, userId)
        val saved = record == stored || write(record)
        if (saved && migrating) storage.deviceId = null
        // The restored caches belong to the install the backup was taken from, and its old device_id
        // must never become an installation id: the customer is loaded again by the registration.
        if (clearRestoredData) clearUserData()
        persist(resolvedAnonymousId, userId)
        restoredPerson = restoredInUse

        val identity = ApphudIdentity(
            installationId = requireNotNull(record.installationId),
            anonymousId = resolvedAnonymousId,
            userId = userId,
        )
        current = identity
        return ApphudIdentityResolution(
            identity = identity,
            launch = launch,
            changed = changed,
            switchedUser = isSwitch(userBeforeSupplied, userId),
            restoredPersonPending = restoredInUse != null,
            // The next launch updates again; this one still has the old customer adopted.
            unsavedLegacyDeviceId = record.legacyDeviceId.takeUnless { saved },
        )
    }

    /** `updateUserId`: identify while anonymous, a switch while identified, a no-op for the same id. */
    @Synchronized
    fun identify(userId: String): ApphudIdentifyOutcome {
        val identity = current ?: return ApphudIdentifyOutcome.UNCHANGED
        if (identity.userId == userId || userId == identity.anonymousId) return ApphudIdentifyOutcome.UNCHANGED
        val outcome = if (identity.userId == null) ApphudIdentifyOutcome.IDENTIFIED else ApphudIdentifyOutcome.SWITCHED
        current = identity.copy(userId = userId)
        persist(identity.anonymousId, userId)
        markSyncPending()
        return outcome
    }

    /** Logout: a new anonymous id (the supplied one, or a fresh one), no user; the installation stays. */
    @Synchronized
    fun logout(anonymousId: String?) {
        val newAnonymousId = anonymousId.nonBlank() ?: makeId()
        current = current?.copy(anonymousId = newAnonymousId, userId = null)
        persist(newAnonymousId, null)
        markSyncPending()
    }

    /**
     * The reinstall check found this device's marker: the restored person belongs to the deleted
     * install, so a reinstall starts with a new anonymous id and no user. Ids the host set since
     * start stay.
     */
    @Synchronized
    fun dropRestoredPerson() {
        val restored = restoredPerson ?: return
        restoredPerson = null
        val identity = current ?: return
        val anonymousId = if (identity.anonymousId == restored.anonymousId) makeId() else identity.anonymousId
        val userId = identity.userId?.takeUnless { it == restored.userId }
        if (anonymousId == identity.anonymousId && userId == identity.userId) return
        ApphudLog.log("Reinstall on this device: the restored person is dropped")
        current = identity.copy(anonymousId = anonymousId, userId = userId)
        persist(anonymousId, userId)
        markSyncPending()
        // A push token sent during the check went out with the restored ids: send it again.
        storage.submittedPushToken = null
    }

    /**
     * Every id supplied by the app: the app owns the ids and the reinstall flag. No reinstall marker
     * is read or written.
     */
    private fun resolveSupplied(
        supplied: ApphudSuppliedIds,
        stored: InstallRecord?,
        storedAnonymousId: String?,
        storedUserId: String?,
    ): ApphudIdentityResolution {
        val installationId = requireNotNull(supplied.installationId)
        val anonymousId = requireNotNull(supplied.anonymousId)
        // The host passes a user id when it has one: none means no user. The anonymous id is never one.
        val userId = supplied.userId?.takeIf { it != anonymousId }

        val record = (stored ?: InstallRecord(InstallState.EXISTING)).copy(installationId = installationId)
        if (record != stored) write(record)
        persist(anonymousId, userId)

        val changed = installationId != stored?.installationId ||
            anonymousId != storedAnonymousId || userId != storedUserId
        if (changed) markSyncPending()

        val identity = ApphudIdentity(installationId, anonymousId, userId)
        current = identity
        return ApphudIdentityResolution(
            identity = identity,
            launch = if (stored == null) ApphudLaunchKind.NEW_INSTALL else ApphudLaunchKind.NORMAL,
            changed = changed,
            switchedUser = isSwitch(storedUserId, userId),
        )
    }

    private fun write(record: InstallRecord): Boolean =
        runCatching { installRecords.write(record) }
            .onFailure { ApphudLog.logE("Failed to save the install record: ${it.message}") }
            .isSuccess

    private fun persist(anonymousId: String, userId: String?) {
        storage.anonymousId = anonymousId
        storage.identifiedUserId = userId
    }

    private fun isSwitch(storedUserId: String?, userId: String?): Boolean =
        storedUserId != null && userId != null && storedUserId != userId

    private fun String?.nonBlank(): String? = this?.takeUnless { it.isBlank() }
}
