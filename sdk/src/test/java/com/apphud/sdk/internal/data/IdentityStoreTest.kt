package com.apphud.sdk.internal.data

import com.apphud.sdk.ApphudUserProperty
import com.apphud.sdk.domain.AdjustInfo
import com.apphud.sdk.domain.ApphudGroup
import com.apphud.sdk.domain.ApphudUser
import com.apphud.sdk.domain.AppsflyerInfo
import com.apphud.sdk.domain.FacebookInfo
import com.apphud.sdk.internal.domain.model.ApphudIdentifyOutcome
import com.apphud.sdk.internal.domain.model.ApphudLaunchKind
import com.apphud.sdk.internal.domain.model.ApphudSuppliedIds
import com.apphud.sdk.storage.InstallRecord
import com.apphud.sdk.storage.InstallRecordStorage
import com.apphud.sdk.storage.InstallState
import com.apphud.sdk.storage.Storage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class IdentityStoreTest {

    private class FakeStorage : Storage {
        override var lastRegistration: Long = 0L
        override var userId: String? = null
        override var deviceId: String? = null
        override var anonymousId: String? = null
        override var identifiedUserId: String? = null
        override var identitySyncPending: Boolean = false
        override var apphudUser: ApphudUser? = null
        override var isNeedSync: Boolean = false
        override var facebook: FacebookInfo? = null
        override var firebase: String? = null
        override var appsflyer: AppsflyerInfo? = null
        override var adjust: AdjustInfo? = null
        override var productGroups: List<ApphudGroup>? = null
        override var productDetails: List<String>? = null
        override var properties: HashMap<String, ApphudUserProperty>? = null
        override var deviceIdentifiers: Array<String> = emptyArray()
        override var syncedDeviceIdentifiersUserId: String? = null
        override var syncedDeviceIdentifiers: Array<String>? = null
        override var cacheVersion: String? = null
        override var connectDomainUrl: String? = null
        override var submittedPushToken: String? = null
    }

    private class FakeInstallRecords : InstallRecordStorage {
        var record: InstallRecord? = null
        var failWrites = false
        var failReads = false

        override fun read(): InstallRecord? {
            if (failReads) throw IOException("unreadable")
            return record
        }

        override fun write(record: InstallRecord) {
            if (failWrites) throw IOException("not saved")
            this.record = record
        }
    }

    private val storage = FakeStorage()
    private val records = FakeInstallRecords()
    private var freshPackage: Boolean? = true
    private var clearedUserData = 0
    private var nextId = 0

    /** A launch over the same device storage. */
    private fun store() = IdentityStore(
        storage = storage,
        installRecords = records,
        isFreshPackageInstall = { freshPackage },
        clearUserData = {
            clearedUserData += 1
            storage.apphudUser = null
            storage.deviceId = null
        },
        makeId = { "id-${++nextId}" },
    )

    private fun launch(supplied: ApphudSuppliedIds = ApphudSuppliedIds()) = store().resolve(supplied)

    /** Delete the app: everything the install kept is gone; nothing survives in app storage. */
    private fun deleteApp() {
        records.record = null
        storage.anonymousId = null
        storage.identifiedUserId = null
        storage.identitySyncPending = false
        storage.apphudUser = null
        storage.deviceId = null
    }

    private fun legacyUser(userId: String) = ApphudUser(
        userId = userId,
        currencyCode = null,
        countryCode = null,
        subscriptions = emptyList(),
        purchases = emptyList(),
        placements = emptyList(),
        isTemporary = false,
    )

    // region identity model table

    @Test
    fun `GIVEN fresh install EXPECT installation and anonymous minted and no user`() {
        val identity = launch().identity

        assertEquals(Triple("id-1", "id-2", null), Triple(identity.installationId, identity.anonymousId, identity.userId))
    }

    @Test
    fun `GIVEN fresh install EXPECT current identity is the anonymous id`() {
        assertEquals("id-2", launch().identity.currentId)
    }

    @Test
    fun `GIVEN fresh install EXPECT record pending the reinstall check`() {
        launch()

        assertEquals(InstallState.PENDING, records.record?.state)
    }

    @Test
    fun `GIVEN anonymous WHEN identify Alice EXPECT only the user set`() {
        val store = store()
        val before = store.resolve(ApphudSuppliedIds()).identity

        store.identify("Alice")

        assertEquals(before.copy(userId = "Alice"), store.current)
    }

    @Test
    fun `GIVEN anonymous WHEN identify Alice EXPECT identified`() {
        val store = store().also { it.resolve(ApphudSuppliedIds()) }

        assertEquals(ApphudIdentifyOutcome.IDENTIFIED, store.identify("Alice"))
    }

    @Test
    fun `GIVEN Alice WHEN identify Alice again EXPECT no-op`() {
        val store = store().also { it.resolve(ApphudSuppliedIds()) }
        store.identify("Alice")

        assertEquals(ApphudIdentifyOutcome.UNCHANGED, store.identify("Alice"))
    }

    @Test
    fun `GIVEN Alice WHEN identify Bob without logout EXPECT switch keeping the anonymous id`() {
        val store = store()
        val before = store.resolve(ApphudSuppliedIds()).identity
        store.identify("Alice")

        val outcome = store.identify("Bob")

        assertEquals(ApphudIdentifyOutcome.SWITCHED to before.copy(userId = "Bob"), outcome to store.current)
    }

    @Test
    fun `GIVEN anonymous WHEN identify with the anonymous id EXPECT never a user`() {
        val store = store()
        val identity = store.resolve(ApphudSuppliedIds()).identity

        assertEquals(ApphudIdentifyOutcome.UNCHANGED, store.identify(identity.anonymousId))
    }

    @Test
    fun `GIVEN Alice WHEN logout EXPECT same installation new anonymous no user`() {
        val store = store()
        val before = store.resolve(ApphudSuppliedIds()).identity
        store.identify("Alice")

        store.logout(anonymousId = null)

        assertEquals(before.copy(anonymousId = "id-3", userId = null), store.current)
    }

    @Test
    fun `GIVEN logout WHEN next launch EXPECT the new anonymous id and no user`() {
        val store = store().also { it.resolve(ApphudSuppliedIds()) }
        store.identify("Alice")
        store.logout(anonymousId = null)

        val identity = launch().identity

        assertEquals("id-3" to null, identity.anonymousId to identity.userId)
    }

    @Test
    fun `GIVEN logout with an anonymous id EXPECT it replaces the current one`() {
        val store = store().also { it.resolve(ApphudSuppliedIds()) }

        store.logout(anonymousId = "host-anonymous")

        assertEquals("host-anonymous", store.current?.anonymousId)
    }

    @Test
    fun `GIVEN Alice WHEN delete and reinstall EXPECT new installation new anonymous no user`() {
        val store = store()
        val before = store.resolve(ApphudSuppliedIds()).identity
        store.identify("Alice")
        deleteApp()

        val after = launch().identity

        assertTrue(after.installationId != before.installationId && after.anonymousId != before.anonymousId && after.userId == null)
    }

    // endregion

    // region launch flow

    @Test
    fun `GIVEN installation id stored EXPECT normal launch with the same ids`() {
        val first = launch().identity

        val second = launch()

        assertEquals(ApphudLaunchKind.NORMAL to first, second.launch to second.identity)
    }

    @Test
    fun `GIVEN installation id stored EXPECT nothing changed`() {
        launch()

        assertFalse(launch().changed)
    }

    @Test
    fun `GIVEN old device id and an updated package EXPECT update in place keeps the device id as installation id`() {
        storage.deviceId = "OLD"
        storage.apphudUser = legacyUser("OLD")
        freshPackage = false

        val resolution = launch()

        assertEquals(ApphudLaunchKind.UPGRADE to "OLD", resolution.launch to resolution.identity.installationId)
    }

    @Test
    fun `GIVEN old SDK-generated user id equal to the device id EXPECT it becomes the anonymous id and no user`() {
        storage.deviceId = "OLD"
        storage.apphudUser = legacyUser("OLD")
        freshPackage = false

        val identity = launch().identity

        assertEquals("OLD" to null, identity.anonymousId to identity.userId)
    }

    @Test
    fun `GIVEN old real user id EXPECT it stays the user and the anonymous id is minted`() {
        storage.deviceId = "OLD"
        storage.apphudUser = legacyUser("alice")
        freshPackage = false

        val identity = launch().identity

        assertEquals("id-1" to "alice", identity.anonymousId to identity.userId)
    }

    @Test
    fun `GIVEN update in place EXPECT reinstall unknown and never checked`() {
        storage.deviceId = "OLD"
        freshPackage = false

        launch()

        assertEquals(InstallState.EXISTING, records.record?.state)
    }

    @Test
    fun `GIVEN update in place EXPECT the old device id key removed`() {
        storage.deviceId = "OLD"
        freshPackage = false

        launch()

        assertNull(storage.deviceId)
    }

    @Test
    fun `GIVEN update in place WHEN next launch EXPECT the same ids`() {
        storage.deviceId = "OLD"
        storage.apphudUser = legacyUser("OLD")
        freshPackage = false
        val first = launch().identity

        assertEquals(first, launch().identity)
    }

    @Test
    fun `GIVEN update in place killed before the record was saved WHEN relaunch EXPECT still an update with the same ids`() {
        storage.deviceId = "OLD"
        storage.apphudUser = legacyUser("OLD")
        freshPackage = false
        records.failWrites = true
        launch()
        records.failWrites = false

        val identity = launch().identity

        assertEquals(Triple("OLD", "OLD", null), Triple(identity.installationId, identity.anonymousId, identity.userId))
    }

    @Test
    fun `GIVEN an update from an old version EXPECT the old device id kept for adoption`() {
        storage.deviceId = "OLD"
        freshPackage = false

        launch()

        assertEquals("OLD", records.record?.legacyDeviceId)
    }

    @Test
    fun `GIVEN old data restored by backup EXPECT the old device id kept for adoption`() {
        storage.deviceId = "OLD"
        storage.apphudUser = legacyUser("OLD")
        freshPackage = true

        launch()

        assertEquals("OLD", records.record?.legacyDeviceId)
    }

    @Test
    fun `GIVEN old data restored and the app's own anonymous id EXPECT no old device id to adopt`() {
        storage.deviceId = "OLD"
        storage.apphudUser = legacyUser("OLD")
        freshPackage = true

        launch(ApphudSuppliedIds.of(anonymousId = "host-anon"))

        assertNull(records.record?.legacyDeviceId)
    }

    @Test
    fun `GIVEN old data restored and the app's own user EXPECT no old device id to adopt`() {
        storage.deviceId = "OLD"
        storage.apphudUser = legacyUser("alice")
        freshPackage = true

        launch(ApphudSuppliedIds.of(userId = "bob"))

        assertNull(records.record?.legacyDeviceId)
    }

    @Test
    fun `GIVEN old data restored and only the app's own user EXPECT the old device id kept with the restored anonymous id`() {
        storage.deviceId = "OLD"
        storage.apphudUser = legacyUser("OLD")
        freshPackage = true

        val identity = launch(ApphudSuppliedIds.of(userId = "bob")).identity

        assertEquals("OLD", identity.anonymousId)
        assertEquals("OLD", records.record?.legacyDeviceId)
    }

    @Test
    fun `GIVEN a restored person already dropped by an earlier launch EXPECT no old device id to adopt`() {
        records.record = InstallRecord(InstallState.PENDING, "installation-1", restoredAnonymousId = "OLD", legacyDeviceId = "OLD")
        storage.anonymousId = "new-anonymous"

        launch()

        assertNull(records.record?.legacyDeviceId)
    }

    @Test
    fun `GIVEN an update whose record wasn't saved EXPECT the old device id handed on for this launch`() {
        storage.deviceId = "OLD"
        freshPackage = false
        records.failWrites = true

        assertEquals("OLD", launch().unsavedLegacyDeviceId)
    }

    @Test
    fun `GIVEN an update whose record was saved EXPECT nothing unsaved`() {
        storage.deviceId = "OLD"
        freshPackage = false

        assertNull(launch().unsavedLegacyDeviceId)
    }

    @Test
    fun `GIVEN a fresh install EXPECT no old device id to adopt`() {
        launch()

        assertNull(records.record?.legacyDeviceId)
    }

    @Test
    fun `GIVEN package freshness unknown and an old device id EXPECT update in place`() {
        storage.deviceId = "OLD"
        freshPackage = null

        assertEquals(ApphudLaunchKind.UPGRADE, launch().launch)
    }

    @Test
    fun `GIVEN fresh package with old data restored by backup EXPECT a new install that never adopts the old device id`() {
        storage.deviceId = "OLD"
        storage.apphudUser = legacyUser("OLD")
        freshPackage = true

        val resolution = launch()

        assertEquals(ApphudLaunchKind.NEW_INSTALL to "id-1", resolution.launch to resolution.identity.installationId)
    }

    @Test
    fun `GIVEN fresh package with old data restored by backup EXPECT the restored caches cleared`() {
        storage.deviceId = "OLD"
        storage.apphudUser = legacyUser("OLD")
        freshPackage = true

        launch()

        assertEquals(1, clearedUserData)
    }

    @Test
    fun `GIVEN fresh package with old generated ids restored by backup EXPECT the old id kept as the anonymous id`() {
        storage.deviceId = "OLD"
        storage.apphudUser = legacyUser("OLD")
        freshPackage = true

        val identity = launch().identity

        assertEquals("OLD" to null, identity.anonymousId to identity.userId)
    }

    @Test
    fun `GIVEN fresh package with an old app-set user restored by backup EXPECT the user kept`() {
        storage.deviceId = "OLD"
        storage.apphudUser = legacyUser("Alice")
        freshPackage = true

        assertEquals("Alice", launch().identity.userId)
    }

    @Test
    fun `GIVEN fresh package with restored ids EXPECT the restored person kept until the reinstall check`() {
        storage.anonymousId = "RESTORED-ANON"
        storage.identifiedUserId = "RESTORED-USER"
        freshPackage = true

        val identity = launch().identity

        assertEquals("RESTORED-ANON" to "RESTORED-USER", identity.anonymousId to identity.userId)
    }

    @Test
    fun `GIVEN fresh package with restored ids EXPECT a new installation id`() {
        storage.anonymousId = "RESTORED-ANON"
        freshPackage = true

        assertEquals("id-1", launch().identity.installationId)
    }

    @Test
    fun `GIVEN fresh package with restored ids EXPECT the record waits for the check on the person`() {
        storage.anonymousId = "RESTORED-ANON"
        freshPackage = true

        launch()

        assertEquals(InstallState.PENDING to true, records.record?.state to records.record?.restoredPerson)
    }

    @Test
    fun `GIVEN fresh package with restored ids EXPECT the check told to decide the person`() {
        storage.anonymousId = "RESTORED-ANON"
        freshPackage = true

        assertTrue(launch().restoredPersonPending)
    }

    @Test
    fun `GIVEN restored ids but the host supplies its own person EXPECT nothing left for the check`() {
        storage.anonymousId = "RESTORED-ANON"
        storage.identifiedUserId = "RESTORED-USER"
        freshPackage = true

        val resolution = launch(ApphudSuppliedIds(userId = "Host", anonymousId = "host-anon"))

        assertFalse(resolution.restoredPersonPending)
    }

    @Test
    fun `GIVEN a launch that died before the check decided EXPECT the next launch tells the check again`() {
        storage.anonymousId = "RESTORED-ANON"
        freshPackage = true
        launch()
        freshPackage = false

        assertTrue(launch().restoredPersonPending)
    }

    @Test
    fun `GIVEN the host passes the restored user at start EXPECT the drop keeps it`() {
        storage.anonymousId = "RESTORED-ANON"
        storage.identifiedUserId = "U"
        freshPackage = true
        val store = store()
        store.resolve(ApphudSuppliedIds(userId = "U"))

        store.dropRestoredPerson()

        assertEquals("U", store.current?.userId)
    }

    @Test
    fun `GIVEN the host passes the restored anonymous id at start EXPECT the drop keeps it`() {
        storage.anonymousId = "A"
        storage.identifiedUserId = "RESTORED-USER"
        freshPackage = true
        val store = store()
        store.resolve(ApphudSuppliedIds(anonymousId = "A"))

        store.dropRestoredPerson()

        assertEquals("A" to null, store.current?.anonymousId to store.current?.userId)
    }

    @Test
    fun `GIVEN the host's user set in a launch that died before the check EXPECT the next launch never drops it`() {
        storage.anonymousId = "RESTORED-ANON"
        storage.identifiedUserId = "RESTORED-USER"
        freshPackage = true
        launch(ApphudSuppliedIds(userId = "Host"))
        freshPackage = false
        val store = store()
        store.resolve(ApphudSuppliedIds())

        store.dropRestoredPerson()

        assertEquals("Host", store.current?.userId)
    }

    @Test
    fun `GIVEN a launch that died before the check decided EXPECT the next launch can still drop the restored user`() {
        storage.anonymousId = "RESTORED-ANON"
        storage.identifiedUserId = "RESTORED-USER"
        freshPackage = true
        launch()
        freshPackage = false
        val store = store()
        store.resolve(ApphudSuppliedIds())

        store.dropRestoredPerson()

        assertNull(store.current?.userId)
    }

    @Test
    fun `GIVEN restored ids no longer in use at a relaunch EXPECT the record forgets them`() {
        storage.anonymousId = "RESTORED-ANON"
        storage.identifiedUserId = "U"
        freshPackage = true
        store().also { it.resolve(ApphudSuppliedIds()) }.logout(null)
        freshPackage = false

        launch()

        assertFalse(records.record?.restoredPerson ?: true)
    }

    @Test
    fun `GIVEN a drop the record never settled EXPECT the next launch keeps the new ids`() {
        storage.anonymousId = "RESTORED-ANON"
        freshPackage = true
        store().also { it.resolve(ApphudSuppliedIds()) }.dropRestoredPerson()
        freshPackage = false

        assertFalse(launch().restoredPersonPending)
    }

    @Test
    fun `GIVEN restored data and a failed record write EXPECT the restored caches cleared anyway`() {
        storage.deviceId = "OLD"
        storage.apphudUser = legacyUser("OLD")
        freshPackage = true
        records.failWrites = true

        launch()

        assertEquals(1, clearedUserData)
    }

    @Test
    fun `GIVEN restored person dropped EXPECT a push token sent during the check sent again`() {
        storage.anonymousId = "RESTORED-ANON"
        freshPackage = true
        val store = store()
        store.resolve(ApphudSuppliedIds())
        storage.submittedPushToken = "token"

        store.dropRestoredPerson()

        assertNull(storage.submittedPushToken)
    }

    @Test
    fun `GIVEN a new install with nothing restored EXPECT no person waiting for the check`() {
        launch()

        assertEquals(false, records.record?.restoredPerson)
    }

    @Test
    fun `GIVEN restored ids on an updated package EXPECT no person waiting for the check`() {
        storage.anonymousId = "RESTORED-ANON"
        freshPackage = false

        launch()

        assertEquals(false, records.record?.restoredPerson)
    }

    @Test
    fun `GIVEN restored ids and the host's user at start EXPECT only the restored anonymous id waits for the check`() {
        storage.anonymousId = "RESTORED-ANON"
        storage.identifiedUserId = "RESTORED-USER"
        freshPackage = true
        val store = store()
        store.resolve(ApphudSuppliedIds(userId = "Host"))

        store.dropRestoredPerson()

        assertEquals("Host", store.current?.userId)
    }

    @Test
    fun `GIVEN restored person dropped EXPECT a new anonymous id and no user`() {
        storage.anonymousId = "RESTORED-ANON"
        storage.identifiedUserId = "RESTORED-USER"
        freshPackage = true
        val store = store()
        store.resolve(ApphudSuppliedIds())

        store.dropRestoredPerson()

        assertEquals("id-2" to null, store.current?.anonymousId to store.current?.userId)
    }

    @Test
    fun `GIVEN restored person dropped EXPECT the installation id kept`() {
        storage.anonymousId = "RESTORED-ANON"
        freshPackage = true
        val store = store()
        store.resolve(ApphudSuppliedIds())

        store.dropRestoredPerson()

        assertEquals("id-1", store.current?.installationId)
    }

    @Test
    fun `GIVEN restored person dropped EXPECT the new ids saved`() {
        storage.anonymousId = "RESTORED-ANON"
        storage.identifiedUserId = "RESTORED-USER"
        freshPackage = true
        val store = store()
        store.resolve(ApphudSuppliedIds())

        store.dropRestoredPerson()

        assertEquals("id-2" to null, storage.anonymousId to storage.identifiedUserId)
    }

    @Test
    fun `GIVEN restored person dropped EXPECT the new ids sent at the next registration`() {
        storage.anonymousId = "RESTORED-ANON"
        freshPackage = true
        val store = store()
        store.resolve(ApphudSuppliedIds())
        store.markSynced()

        store.dropRestoredPerson()

        assertTrue(store.isSyncPending)
    }

    @Test
    fun `GIVEN the host identified during the check EXPECT the drop keeps that user`() {
        storage.anonymousId = "RESTORED-ANON"
        storage.identifiedUserId = "RESTORED-USER"
        freshPackage = true
        val store = store()
        store.resolve(ApphudSuppliedIds())
        store.identify("Bob")

        store.dropRestoredPerson()

        assertEquals("Bob", store.current?.userId)
    }

    @Test
    fun `GIVEN nothing restored EXPECT drop changes nothing`() {
        val store = store()
        val before = store.resolve(ApphudSuppliedIds()).identity

        store.dropRestoredPerson()

        assertEquals(before, store.current)
    }

    @Test
    fun `GIVEN a launch that died before the check decided EXPECT the next launch can still drop the restored person`() {
        storage.anonymousId = "RESTORED-ANON"
        freshPackage = true
        launch()
        freshPackage = false
        val store = store()
        store.resolve(ApphudSuppliedIds())

        store.dropRestoredPerson()

        assertEquals("id-2", store.current?.anonymousId)
    }

    @Test
    fun `GIVEN unreadable install record EXPECT a new installation id keeping the stored person`() {
        launch()
        records.failReads = true

        val identity = launch().identity

        assertEquals("id-3" to "id-2", identity.installationId to identity.anonymousId)
    }

    @Test
    fun `GIVEN unreadable install record EXPECT no reinstall verdict`() {
        launch()
        records.failReads = true

        launch()

        assertEquals(InstallState.EXISTING, records.record?.state)
    }

    @Test
    fun `GIVEN missing record on an updated package EXPECT the stored person kept`() {
        store().also { it.resolve(ApphudSuppliedIds()) }.identify("Alice")
        records.record = null
        freshPackage = false

        val identity = launch().identity

        assertEquals("id-2" to "Alice", identity.anonymousId to identity.userId)
    }

    @Test
    fun `GIVEN missing record on an updated package with a kept person EXPECT the reinstall check runs`() {
        store().also { it.resolve(ApphudSuppliedIds()) }.identify("Alice")
        records.record = null
        freshPackage = false

        launch()

        assertEquals(InstallState.PENDING, records.record?.state)
    }

    @Test
    fun `GIVEN first SDK launch on an updated package without old data EXPECT the reinstall check runs`() {
        freshPackage = false

        launch()

        assertEquals(InstallState.PENDING, records.record?.state)
    }

    @Test
    fun `GIVEN missing record on an updated package EXPECT no user data cleared`() {
        store().also { it.resolve(ApphudSuppliedIds()) }.identify("Alice")
        records.record = null
        freshPackage = false

        launch()

        assertEquals(0, clearedUserData)
    }

    // endregion

    // region ids supplied by the app

    @Test
    fun `GIVEN every id supplied EXPECT stored as given`() {
        val identity = launch(ApphudSuppliedIds.of(userId = "u", anonymousId = "a", installationId = "i")).identity

        assertEquals(Triple("i", "a", "u"), Triple(identity.installationId, identity.anonymousId, identity.userId))
    }

    @Test
    fun `GIVEN every id supplied without a user EXPECT no user even when one was stored`() {
        launch(ApphudSuppliedIds.of(userId = "u", anonymousId = "a", installationId = "i"))

        val identity = launch(ApphudSuppliedIds.of(anonymousId = "a", installationId = "i")).identity

        assertNull(identity.userId)
    }

    @Test
    fun `GIVEN every id supplied with the anonymous id as user EXPECT no user`() {
        val identity = launch(ApphudSuppliedIds.of(userId = "a", anonymousId = "a", installationId = "i")).identity

        assertNull(identity.userId)
    }

    @Test
    fun `GIVEN supplied ids survive relaunch EXPECT never rotated`() {
        val supplied = ApphudSuppliedIds.of(userId = "u", anonymousId = "a", installationId = "i")
        val first = launch(supplied).identity

        assertEquals(first, launch(supplied).identity)
    }

    @Test
    fun `GIVEN only an installation id supplied EXPECT it is used and the anonymous id minted`() {
        val identity = launch(ApphudSuppliedIds.of(installationId = "host-device")).identity

        assertEquals("host-device" to "id-2", identity.installationId to identity.anonymousId)
    }

    @Test
    fun `GIVEN a supplied anonymous id as user EXPECT the stored user stays`() {
        val store = store().also { it.resolve(ApphudSuppliedIds()) }
        store.identify("Alice")
        val anonymousId = requireNotNull(store.current).anonymousId

        val identity = launch(ApphudSuppliedIds.of(userId = anonymousId)).identity

        assertEquals("Alice", identity.userId)
    }

    @Test
    fun `GIVEN Alice stored WHEN started with Bob EXPECT a switch`() {
        val store = store().also { it.resolve(ApphudSuppliedIds()) }
        store.identify("Alice")

        assertTrue(launch(ApphudSuppliedIds.of(userId = "Bob")).switchedUser)
    }

    @Test
    fun `GIVEN blank supplied ids EXPECT treated as not supplied`() {
        assertEquals(ApphudSuppliedIds(), ApphudSuppliedIds.of(userId = " ", anonymousId = "", installationId = ""))
    }

    // endregion

    // region sync with the backend

    @Test
    fun `GIVEN changed ids EXPECT sync pending`() {
        launch()

        assertTrue(store().isSyncPending)
    }

    @Test
    fun `GIVEN synced WHEN relaunch without changes EXPECT not pending`() {
        store().also { it.resolve(ApphudSuppliedIds()) }.markSynced()

        launch()

        assertFalse(store().isSyncPending)
    }

    @Test
    fun `GIVEN synced WHEN identify EXPECT sync pending`() {
        val store = store().also { it.resolve(ApphudSuppliedIds()) }
        store.markSynced()

        store.identify("Alice")

        assertTrue(store.isSyncPending)
    }

    @Test
    fun `GIVEN synced WHEN logout EXPECT sync pending`() {
        val store = store().also { it.resolve(ApphudSuppliedIds()) }
        store.markSynced()

        store.logout(anonymousId = null)

        assertTrue(store.isSyncPending)
    }

    @Test
    fun `GIVEN identify EXPECT the user survives relaunch`() {
        store().also { it.resolve(ApphudSuppliedIds()) }.identify("Alice")

        assertEquals("Alice", launch().identity.userId)
    }

    @Test
    fun `GIVEN reinstall EXPECT installation id differs from the one before`() {
        val before = launch().identity
        deleteApp()

        assertNotEquals(before.installationId, launch().identity.installationId)
    }

    // endregion
}
