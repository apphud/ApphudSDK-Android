package com.apphud.sdk.internal.data

import com.apphud.sdk.ApphudIdentityVersion
import com.apphud.sdk.ApphudLog
import com.apphud.sdk.internal.domain.model.ApphudIdentifyOutcome
import com.apphud.sdk.internal.domain.model.ApphudIdentity
import com.apphud.sdk.internal.domain.model.ApphudIdentityResolution
import com.apphud.sdk.internal.domain.model.ApphudLaunchKind
import com.apphud.sdk.internal.domain.model.ApphudSuppliedIds
import com.apphud.sdk.internal.domain.model.Version1Ids
import com.apphud.sdk.storage.IdentityVersionStorage
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
 *   user. Identity version 1 keeps those ids, and they stay while version 2 runs, so version 1 can
 *   come back to the same customer. Every switch from version 1 adopts its ids again.
 * - Auto Backup can restore the preferences onto a new install: after a reinstall on this device, or
 *   onto a new device. The person comes back at start, as on a new device; the reinstall check drops
 *   it ([dropRestoredPerson]) when it finds this device's marker.
 */
internal class IdentityStore(
    private val storage: Storage,
    private val installRecords: InstallRecordStorage,
    /** The identity version this install ran last. */
    private val versions: IdentityVersionStorage,
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
        val lastVersion = readVersion()
        var version1LoggedOut = false
        // The person Auto Backup brought back, still to be confirmed by the reinstall check.
        var restored: RestoredPerson? = null
        // The old SDK's device_id, sent as legacy_device_id until a registration adopts its customer.
        var adoptLegacyDeviceId: String? = null

        if (stored != null && lastVersion != ApphudIdentityVersion.V1) {
            launch = ApphudLaunchKind.NORMAL
            record = stored
            // The launch that found the restored person ended before the check decided.
            if (stored.restoredPerson && stored.state == InstallState.PENDING) {
                restored = RestoredPerson(stored.restoredAnonymousId, stored.restoredUserId)
            }
        } else if (stored != null) {
            // Version 1 ran since: its device_id adopts its customer again, as on the first update, and
            // its ids are the current ones. The installation stays.
            launch = ApphudLaunchKind.UPGRADE
            adoptLegacyDeviceId = legacyDeviceId
            record = stored.copy(legacyDeviceId = legacyDeviceId)
            version1LoggedOut = storage.version1LoggedOut
            val (anonymous, user) = version1Person(legacyDeviceId, anonymousId, userId, version1LoggedOut)
            anonymousId = anonymous
            userId = user
        } else if (legacyDeviceId != null && (
                lastVersion == ApphudIdentityVersion.V1 ||
                    (storage.idsIdentityVersion != ApphudIdentityVersion.V2.name && isFreshPackageInstall() != true)
                )
        ) {
            // In-place update: the old device_id stays as installation_id and goes as legacy_device_id,
            // which has the backend adopt the old customer. A fresh install never takes it as an update:
            // Auto Backup restores old preferences. Version 1 that ran on this install left its own ids,
            // a fresh package or not; ids version 2 used last came from a backup.
            launch = ApphudLaunchKind.UPGRADE
            adoptLegacyDeviceId = legacyDeviceId
            record = InstallRecord(InstallState.EXISTING, legacyDeviceId, legacyDeviceId = legacyDeviceId)
            version1LoggedOut = storage.version1LoggedOut
            val (anonymous, user) = version1Person(legacyDeviceId, anonymousId, userId, version1LoggedOut)
            anonymousId = anonymous
            userId = user
        } else if (damaged) {
            // The record was there: our own reinstall marker may be too, so it gives no verdict.
            launch = ApphudLaunchKind.NEW_INSTALL
            record = InstallRecord(InstallState.EXISTING, makeId())
            if (dropsPersonOfVersion1Logout(legacyDeviceId)) {
                version1LoggedOut = true
                anonymousId = null
                userId = null
            }
        } else {
            launch = ApphudLaunchKind.NEW_INSTALL
            if (dropsPersonOfVersion1Logout(legacyDeviceId)) {
                version1LoggedOut = true
                anonymousId = null
                userId = null
            }
            val restoredData = legacyDeviceId != null || storedAnonymousId != null || storedUserId != null || storage.apphudUser != null
            // Only a fresh package can carry data Auto Backup restored; an updated one keeps its person.
            val fresh = isFreshPackageInstall() == true
            if (restoredData && fresh) {
                // Auto Backup restored this data, after a reinstall on this device or onto a new one.
                // Only the reinstall marker tells them apart: until it does, the person is kept, as on
                // a new device. Data from an old version maps as an update does, but never gives the
                // installation id.
                // Data version 1 used last maps the same way: its person, not the one of version 2 before it.
                val version1Last = storage.idsIdentityVersion == ApphudIdentityVersion.V1.name
                if (legacyDeviceId != null && (anonymousId == null || version1Last)) {
                    adoptLegacyDeviceId = legacyDeviceId
                    version1LoggedOut = version1Last && storage.version1LoggedOut
                    val (anonymous, user) = version1Person(legacyDeviceId, anonymousId, userId, version1LoggedOut)
                    anonymousId = anonymous
                    userId = user
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
        // Old versions also kept a device id the app passed: one the app still passes as the installation id,
        // with no other user cached, is the app's own.
        val appsOwnDeviceId = legacyDeviceId == supplied.installationId &&
            storage.apphudUser?.userId.nonBlank().let { it == null || it == legacyDeviceId }
        if (legacyDeviceId != null && !appsOwnDeviceId && storage.oldDeviceId.nonBlank() == null) {
            storage.oldDeviceId = legacyDeviceId
        }
        // Generated ids are never a user: a supplied one is ignored (the stored user stays), a stored one
        // is dropped.
        val generatedIds = setOfNotNull(resolvedAnonymousId, storage.oldDeviceId.nonBlank())
        val suppliedUserId = supplied.userId?.takeIf { it !in generatedIds }
        suppliedUserId?.let { userId = it }
        if (userId in generatedIds) userId = null

        // Only restored ids still in use wait for the check: an id the host supplied stays, even one
        // equal to the restored id.
        val restoredInUse = restored
            ?.let {
                RestoredPerson(
                    anonymousId = it.anonymousId?.takeIf { id -> supplied.anonymousId == null && id == resolvedAnonymousId },
                    userId = it.userId?.takeIf { id -> suppliedUserId == null && id == userId },
                )
            }
            ?.takeIf { it.anonymousId != null || it.userId != null }
        // Restored ids no longer in use are forgotten, so an id set again later is never taken for one.
        record = record.copy(restoredAnonymousId = restoredInUse?.anonymousId, restoredUserId = restoredInUse?.userId)
        val clearRestoredData = launch == ApphudLaunchKind.NEW_INSTALL && restored != null
        val switchedUser = isSwitch(userBeforeSupplied, userId)
        // Not after a user switch: the old customer belongs to the user before.
        if (switchedUser) record = record.copy(legacyDeviceId = null)

        val changed = record.installationId != stored?.installationId ||
            resolvedAnonymousId != storedAnonymousId || userId != storedUserId ||
            record.legacyDeviceId != stored?.legacyDeviceId
        if (changed) markSyncPending()

        val migrating = launch == ApphudLaunchKind.UPGRADE
        // An update keeps the person before the record that ends it is saved, and a restore before the
        // old data is deleted: a crash in between must not lose it, or leave an SDK-generated user_id
        // behind as a real user.
        // Not after a user switch: the record without the old customer goes first, so a crash in between
        // leaves the user before, and the next launch drops it again.
        if ((migrating && !switchedUser) || clearRestoredData) persist(resolvedAnonymousId, userId)
        val saved = record == stored || write(record)
        // The restored caches belong to the install the backup was taken from, and its old device_id
        // must never become an installation id: the customer is loaded again by the registration.
        if (clearRestoredData) clearUserData()
        persist(resolvedAnonymousId, userId)
        // The person after version 1's logout is saved: a later launch keeps it.
        if (version1LoggedOut) {
            storage.version1LoggedOut = false
            // No device id of its own since the logout: the next version 1 makes a new one.
            if (legacyDeviceId == null) storage.version1IdsReset = true
        }
        // Last: a launch that didn't finish runs the switch from version 1 again.
        if (saved) writeVersion(ApphudIdentityVersion.V2)
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
            switchedUser = switchedUser,
            restoredPersonPending = restoredInUse != null,
            // The next launch updates again; this one still has the old customer adopted.
            unsavedLegacyDeviceId = record.legacyDeviceId.takeUnless { saved },
            dropsLegacyDeviceId = switchedUser,
        )
    }

    /** `updateUserId`: identify while anonymous, a switch while identified, a no-op for the same id. */
    @Synchronized
    fun identify(userId: String): ApphudIdentifyOutcome {
        val identity = current ?: return ApphudIdentifyOutcome.UNCHANGED
        if (identity.userId == userId || userId == identity.anonymousId || userId == storage.oldDeviceId.nonBlank()) {
            return ApphudIdentifyOutcome.UNCHANGED
        }
        val outcome = if (identity.userId == null) ApphudIdentifyOutcome.IDENTIFIED else ApphudIdentifyOutcome.SWITCHED
        // Not after a user switch: the old customer is the user before's. Dropped before the new user is
        // saved, so a crash in between never leaves it to them.
        if (outcome == ApphudIdentifyOutcome.SWITCHED) dropLegacyDeviceId()
        current = identity.copy(userId = userId)
        persist(identity.anonymousId, userId)
        markSyncPending()
        return outcome
    }

    /** Logout: a new anonymous id (the supplied one, or a fresh one), no user; the installation stays. */
    @Synchronized
    fun logout(anonymousId: String?) {
        // Version 1's device id belongs to the person logged out: version 1 starts a new one, as its own
        // logout does. Reset before the new person is saved: a crash in between must not leave the old
        // device id to the new person.
        storage.version1IdsReset = true
        storage.deviceId = null
        // Not after a logout: the old customer is the logged-out person's. Dropped before the new person.
        dropLegacyDeviceId()
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
        // The host passes a user id when it has one: none means no user. Generated ids are never one.
        val userId = supplied.userId?.takeIf { it != anonymousId && it != storage.oldDeviceId.nonBlank() }

        // Not after a user switch or a logout (the host's new anonymous id): the old customer belongs to the
        // person before. Dropped in the record before the new person is saved.
        val personChanged = isSwitch(storedUserId, userId) || (storedAnonymousId != null && anonymousId != storedAnonymousId)
        var record = (stored ?: InstallRecord(InstallState.EXISTING)).copy(installationId = installationId)
        // The app's reinstall answer belongs to its installation.
        if (stored?.installationId != installationId) record = record.copy(hostReinstall = null)
        if (personChanged) record = record.copy(legacyDeviceId = null)
        if (record != stored) write(record)
        persist(anonymousId, userId)
        writeVersion(ApphudIdentityVersion.V2)

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
            dropsLegacyDeviceId = personChanged,
        )
    }

    /**
     * Identity version 1 after version 2 ran here: the ids it sends, so the backend finds the same
     * customer by the user id. The device id is the old one, or the installation id when the install
     * started on version 2. Null when version 2 never ran here.
     */
    @Synchronized
    fun idsForVersion1(): Version1Ids? {
        // The marker that travels with the ids: a backup or a reinstall loses the install's own.
        val lastVersion = idsVersion() ?: readVersion()
        val anonymousId = storage.anonymousId.nonBlank()
        // After a logout on version 2, whatever the marker says, version 1 starts with a new device id,
        // never one still stored.
        if (storage.version1IdsReset && anonymousId != null) {
            return Version1Ids(deviceId = makeId(), userId = storage.identifiedUserId.nonBlank() ?: anonymousId)
        }
        // Builds before the version marker ran version 2 only.
        if (lastVersion != ApphudIdentityVersion.V2 && !(lastVersion == null && anonymousId != null)) return null
        val userId = storage.identifiedUserId.nonBlank() ?: anonymousId ?: return null
        val deviceId = storage.deviceId.nonBlank() ?: storage.oldDeviceId.nonBlank()
            ?: runCatching { installRecords.read()?.installationId }.getOrNull()
        return Version1Ids(deviceId = deviceId, userId = userId)
    }

    /**
     * Identity version 1 ran: the next start on version 2 takes its ids over. [tookOverVersion2]: this
     * start took the ids of version 2 over, and its device id is saved.
     */
    @Synchronized
    fun markVersion1(tookOverVersion2: Boolean = false) {
        if (tookOverVersion2) {
            storage.version1IdsReset = false
            storage.version1LoggedOut = false
            storage.version1RegistrationPending = true
        }
        writeVersion(ApphudIdentityVersion.V1)
    }

    /** A logout on version 1. */
    fun markVersion1LoggedOut() {
        storage.version1LoggedOut = true
    }

    /** Version 1 took the ids of version 2 over and no registration of it has succeeded yet. */
    val isVersion1RegistrationPending: Boolean get() = storage.version1RegistrationPending

    fun markVersion1Registered() {
        storage.version1RegistrationPending = false
    }

    /** The identity version that ran last on this install, or on these ids; null before either ran. */
    fun lastIdentityVersion(): ApphudIdentityVersion? =
        // Builds before the version marker, or a first launch whose record wasn't saved, ran version 2.
        readVersion() ?: idsVersion() ?: ApphudIdentityVersion.V2.takeIf { storage.anonymousId.nonBlank() != null }

    private fun readVersion(): ApphudIdentityVersion? =
        runCatching { versions.read() }.getOrNull()

    /**
     * Version 1 ran last on these ids and logged out, and this install has no record (a backup, or a
     * damaged record): the person before the logout doesn't come back.
     */
    private fun dropsPersonOfVersion1Logout(legacyDeviceId: String?): Boolean =
        legacyDeviceId == null && storage.version1LoggedOut && idsVersion() == ApphudIdentityVersion.V1

    private fun idsVersion(): ApphudIdentityVersion? =
        ApphudIdentityVersion.values().firstOrNull { it.name == storage.idsIdentityVersion }

    /**
     * The person identity version 1 left, over the ids of version 2 it found: (anonymous id, user id).
     * Version 1 keeps its device id in the preferences and its user only in the cached customer. A user
     * id equal to the device id was generated by the SDK: it is the anonymous id. The anonymous id of
     * version 2 stays only while version 1 went on for the same person.
     */
    private fun version1Person(
        deviceId: String?,
        anonymousId: String?,
        userId: String?,
        loggedOut: Boolean,
    ): Pair<String?, String?> {
        val version1UserId = storage.apphudUser?.userId.nonBlank()
        return when {
            deviceId != null && version1UserId == deviceId -> deviceId to null
            // Version 1 sent version 2's anonymous id as its user id: still the anonymous person.
            anonymousId != null && version1UserId == anonymousId -> anonymousId to null
            // Its own person since the logout: its device id while no customer came back, or a new one.
            loggedOut -> deviceId.takeIf { version1UserId == null } to version1UserId
            // Dropped later when it is a generated id, e.g. the anonymous id version 1 sent.
            version1UserId != null -> anonymousId to version1UserId
            anonymousId == null -> deviceId to null
            else -> anonymousId to userId
        }
    }

    private fun writeVersion(version: ApphudIdentityVersion) {
        storage.idsIdentityVersion = version.name
        runCatching { versions.write(version) }
            .onFailure { ApphudLog.logE("Failed to save the identity version: ${it.message}") }
    }

    /** The old SDK's `device_id` is no longer sent: dropped from the saved record. */
    private fun dropLegacyDeviceId() {
        // In one step with the storage: the reinstall check may change the record meanwhile.
        runCatching { installRecords.update(fallback = null) { it.copy(legacyDeviceId = null) } }
            .onFailure { ApphudLog.logE("Failed to save the install record: ${it.message}") }
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
