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
import com.apphud.sdk.internal.domain.model.ApphudIdentity
import com.apphud.sdk.internal.domain.model.Version1Ids
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
import com.apphud.sdk.ApphudIdentityVersion
import com.apphud.sdk.storage.IdentityVersionStorage
import java.io.IOException

class IdentityStoreTest {

    private class FakeStorage : Storage {
        override var lastRegistration: Long = 0L
        override var userId: String? = null
        override var deviceId: String? = null
        override var oldDeviceId: String? = null
        override var anonymousId: String? = null
        override var identifiedUserId: String? = null
        override var identitySyncPending: Boolean = false
        override var idsIdentityVersion: String? = null
        override var version1IdsReset: Boolean = false
        override var version1LoggedOut: Boolean = false
        override var version1RegistrationPending: Boolean = false
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
        /** Called before every write, e.g. to see what was saved in the preferences at that moment. */
        var onWrite: (() -> Unit)? = null

        override fun read(): InstallRecord? {
            if (failReads) throw IOException("unreadable")
            return record
        }

        override fun write(record: InstallRecord) {
            onWrite?.invoke()
            if (failWrites) throw IOException("not saved")
            this.record = record
        }
    }

    /** The identity version this install ran last: install-scoped, like the record. */
    private class FakeVersions : IdentityVersionStorage {
        var version: ApphudIdentityVersion? = null

        override fun read(): ApphudIdentityVersion? = version

        override fun write(version: ApphudIdentityVersion) {
            this.version = version
        }
    }

    private val storage = FakeStorage()
    private val records = FakeInstallRecords()
    private val versions = FakeVersions()
    private var freshPackage: Boolean? = true
    private var clearedUserData = 0
    private var nextId = 0

    /** A launch over the same device storage. */
    private fun store() = IdentityStore(
        storage = storage,
        installRecords = records,
        versions = versions,
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
        versions.version = null
        storage.anonymousId = null
        storage.identifiedUserId = null
        storage.identitySyncPending = false
        storage.apphudUser = null
        storage.deviceId = null
        storage.idsIdentityVersion = null
        storage.version1IdsReset = false
        storage.version1LoggedOut = false
        storage.version1RegistrationPending = false
    }

    /**
     * What identity version 1 leaves when it runs with these ids: its device id, its customer, the
     * install's version. [tookOverVersion2]: its first start after version 2; false after its own logout.
     */
    private fun runVersion1(deviceId: String, userId: String, tookOverVersion2: Boolean = true) {
        storage.deviceId = deviceId
        storage.apphudUser = legacyUser(userId)
        store().markVersion1(tookOverVersion2)
    }

    /** Another phone restored from this one's backup: the preferences come along, noBackupFilesDir doesn't. */
    private fun restoreBackupOntoAnotherPhone() {
        records.record = null
        versions.version = null
        freshPackage = true
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
    fun `GIVEN update in place EXPECT the old device id kept for identity version 1`() {
        storage.deviceId = "OLD"
        freshPackage = false

        launch()

        assertEquals("OLD", storage.deviceId)
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
    fun `GIVEN old data restored and the app's own anonymous id EXPECT the old device id kept for the backend and the reinstall check`() {
        storage.deviceId = "OLD"
        storage.apphudUser = legacyUser("OLD")
        freshPackage = true

        launch(ApphudSuppliedIds.of(anonymousId = "host-anon"))

        assertEquals("OLD", records.record?.legacyDeviceId)
    }

    @Test
    fun `GIVEN old data restored and another user started EXPECT the old device id dropped`() {
        storage.deviceId = "OLD"
        storage.apphudUser = legacyUser("alice")
        freshPackage = true

        val resolution = launch(ApphudSuppliedIds.of(userId = "bob"))

        assertNull("not after a user switch", records.record?.legacyDeviceId)
        assertTrue(resolution.dropsLegacyDeviceId)
    }

    @Test
    fun `GIVEN an update started with another user EXPECT the record without the old device id saved before the user`() {
        storage.deviceId = "OLD"
        storage.apphudUser = legacyUser("alice")
        freshPackage = false
        var userAtTheRecord: String? = "unset"
        records.onWrite = { userAtTheRecord = storage.identifiedUserId }

        launch(ApphudSuppliedIds.of(userId = "bob"))

        assertNull("a crash in between leaves the user before: the next launch drops it again", userAtTheRecord)
        assertNull(records.record?.legacyDeviceId)
        assertEquals("bob", storage.identifiedUserId)
    }

    @Test
    fun `GIVEN a user switch EXPECT the old device id dropped from the record before the new user is saved`() {
        storage.deviceId = "OLD"
        storage.apphudUser = legacyUser("alice")
        freshPackage = false
        val launched = store().also { it.resolve(ApphudSuppliedIds()) }
        var userAtTheDrop: String? = "unset"
        records.onWrite = { userAtTheDrop = storage.identifiedUserId }

        assertEquals(ApphudIdentifyOutcome.SWITCHED, launched.identify("bob"))

        assertEquals("alice", userAtTheDrop)
        assertNull(records.record?.legacyDeviceId)
    }

    @Test
    fun `GIVEN the first identify of the anonymous person EXPECT the old device id kept`() {
        storage.deviceId = "OLD"
        storage.apphudUser = legacyUser("OLD")
        freshPackage = false
        val launched = store().also { it.resolve(ApphudSuppliedIds()) }

        assertEquals(ApphudIdentifyOutcome.IDENTIFIED, launched.identify("alice"))

        assertEquals("OLD", records.record?.legacyDeviceId)
    }

    @Test
    fun `GIVEN a logout EXPECT the old device id dropped from the record before the new person is saved`() {
        storage.deviceId = "OLD"
        storage.apphudUser = legacyUser("alice")
        freshPackage = false
        val launched = store().also { it.resolve(ApphudSuppliedIds()) }
        val before = storage.anonymousId
        var anonymousIdAtTheDrop: String? = "unset"
        records.onWrite = { anonymousIdAtTheDrop = storage.anonymousId }

        launched.logout(null)

        assertEquals(before, anonymousIdAtTheDrop)
        assertNull(records.record?.legacyDeviceId)
    }

    @Test
    fun `GIVEN version 2 of a build before the version marker then version 1 EXPECT version 2 again on the same anonymous person`() {
        val first = launch()
        // A build before the version marker: the ids of version 2, no marker.
        versions.version = null
        storage.idsIdentityVersion = null

        val version1 = store().idsForVersion1()
        assertEquals(first.identity.installationId, version1?.deviceId)
        assertEquals("the anonymous id finds the customer", first.identity.anonymousId, version1?.userId)
        // Version 1 registered; the backend answered with that customer, whose user id is the anonymous id.
        runVersion1(deviceId = first.identity.installationId, userId = first.identity.anonymousId)

        val back = launch().identity

        assertEquals(first.identity.anonymousId, back.anonymousId)
        assertNull("the user id version 1 had was the anonymous id: no user", back.userId)
        assertEquals(first.identity.installationId, records.record?.legacyDeviceId)
    }

    @Test
    fun `GIVEN version 2 anonymous then version 1 WHEN version 2 starts with a user EXPECT a first identify, not a switch`() {
        val first = launch()
        runVersion1(deviceId = first.identity.installationId, userId = first.identity.anonymousId)

        val resolution = launch(ApphudSuppliedIds.of(userId = "alice"))

        assertFalse("version 1's user id was version 2's anonymous id", resolution.switchedUser)
        assertFalse(resolution.dropsLegacyDeviceId)
        assertEquals(first.identity.installationId, records.record?.legacyDeviceId)
    }

    @Test
    fun `GIVEN the host's new anonymous id EXPECT the old device id dropped as after a logout`() {
        storage.deviceId = "OLD"
        storage.apphudUser = legacyUser("alice")
        freshPackage = false
        launch()

        val resolution = launch(ApphudSuppliedIds.of(anonymousId = "host-anon", installationId = "inst"))

        assertNull(records.record?.legacyDeviceId)
        assertTrue(resolution.dropsLegacyDeviceId)
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
    fun `GIVEN a restored person already dropped by an earlier launch EXPECT the old device id left to the reinstall check`() {
        records.record = InstallRecord(InstallState.PENDING, "installation-1", restoredAnonymousId = "OLD", legacyDeviceId = "OLD")
        storage.anonymousId = "new-anonymous"

        launch()

        assertEquals("OLD", records.record?.legacyDeviceId)
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
    fun `GIVEN an update and the app passing back the old generated id EXPECT no user and no switch`() {
        // The old SDK took a user id the server returned; the app kept the generated id it was given.
        storage.deviceId = "OLD"
        storage.apphudUser = legacyUser("alice")
        freshPackage = false

        val resolution = launch(ApphudSuppliedIds.of(userId = "OLD"))

        assertEquals("alice", resolution.identity.userId)
        assertFalse(resolution.switchedUser)
        assertEquals("OLD", records.record?.legacyDeviceId)
        val later = launch(ApphudSuppliedIds.of(userId = "OLD"))
        assertEquals("alice", later.identity.userId)
        assertFalse(later.switchedUser)
    }

    @Test
    fun `GIVEN old data restored and the app passing back the old generated id EXPECT the restored user`() {
        storage.deviceId = "OLD"
        storage.apphudUser = legacyUser("alice")
        freshPackage = true

        val resolution = launch(ApphudSuppliedIds.of(userId = "OLD"))

        assertEquals("alice", resolution.identity.userId)
        assertEquals("OLD", records.record?.legacyDeviceId)
        assertTrue("the reinstall check still decides the restored person", resolution.restoredPersonPending)
        assertEquals("alice", launch(ApphudSuppliedIds.of(userId = "OLD")).identity.userId)
    }

    @Test
    fun `GIVEN an update EXPECT the old generated id no user on identify nor with every id supplied`() {
        storage.deviceId = "OLD"
        storage.apphudUser = legacyUser("alice")
        freshPackage = false
        val store = store()
        store.resolve(ApphudSuppliedIds())

        assertEquals(ApphudIdentifyOutcome.UNCHANGED, store.identify("OLD"))
        val supplied = launch(ApphudSuppliedIds.of(userId = "OLD", anonymousId = "a", installationId = "inst")).identity
        assertNull(supplied.userId)
    }

    @Test
    fun `GIVEN an update with the app's own installation id EXPECT the old generated id no user on later launches`() {
        storage.deviceId = "OLD"
        storage.apphudUser = legacyUser("alice")
        freshPackage = false
        launch(ApphudSuppliedIds.of(userId = "OLD", installationId = "host-inst"))

        val later = launch(ApphudSuppliedIds.of(userId = "OLD", installationId = "host-inst"))

        assertEquals("alice", later.identity.userId)
        assertFalse(later.switchedUser)
    }

    @Test
    fun `GIVEN an old version given the app's own device id as its user id EXPECT that id a user again after logout`() {
        // Old versions also kept a device id the app passed, here the same as its user id.
        storage.deviceId = "u42"
        storage.apphudUser = legacyUser("u42")
        freshPackage = false
        val store = store()
        store.resolve(ApphudSuppliedIds.of(userId = "u42", installationId = "u42"))
        store.logout(null)

        val identity = launch(ApphudSuppliedIds.of(userId = "u42", installationId = "u42")).identity

        assertNull(storage.oldDeviceId)
        assertEquals("u42", identity.userId)
    }

    @Test
    fun `GIVEN the old generated id passed back as both ids while another user is cached EXPECT it no user`() {
        storage.deviceId = "OLD"
        storage.apphudUser = legacyUser("alice")
        freshPackage = false

        val resolution = launch(ApphudSuppliedIds.of(userId = "OLD", installationId = "OLD"))

        assertEquals("alice", resolution.identity.userId)
        assertFalse(resolution.switchedUser)
        assertEquals("OLD", storage.oldDeviceId)
    }

    @Test
    fun `GIVEN a user id the app chose EXPECT a user even when it is the installation id`() {
        assertEquals("U", launch(ApphudSuppliedIds.of(userId = "U", installationId = "U")).identity.userId)
        assertEquals("U", launch(ApphudSuppliedIds.of(userId = "U", anonymousId = "a", installationId = "U")).identity.userId)
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
    fun `GIVEN the app's kept reinstall answer WHEN it passes another installation id EXPECT the answer dropped`() {
        launch(ApphudSuppliedIds.of(anonymousId = "a", installationId = "inst-A"))
        records.record = records.record?.copy(hostReinstall = true)

        launch(ApphudSuppliedIds.of(anonymousId = "a", installationId = "inst-B"))

        assertNull("the answer belongs to its installation", records.record?.hostReinstall)
    }

    @Test
    fun `GIVEN the app's kept reinstall answer WHEN the same installation id EXPECT it kept`() {
        launch(ApphudSuppliedIds.of(anonymousId = "a", installationId = "inst-A"))
        records.record = records.record?.copy(hostReinstall = true)

        launch(ApphudSuppliedIds.of(anonymousId = "a", installationId = "inst-A"))

        assertEquals(true, records.record?.hostReinstall)
    }

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

    // region identity version switch

    @Test
    fun `GIVEN a fresh version 2 install WHEN version 1 starts EXPECT the installation and anonymous ids`() {
        val identity = launch().identity

        assertEquals(Version1Ids(identity.installationId, identity.anonymousId), store().idsForVersion1())
    }

    @Test
    fun `GIVEN an identified version 2 user WHEN version 1 starts EXPECT that user`() {
        val store = store().also { it.resolve(ApphudSuppliedIds()) }
        store.identify("alice")

        assertEquals("alice", store().idsForVersion1()?.userId)
    }

    @Test
    fun `GIVEN an updated install WHEN version 1 starts EXPECT the old ids`() {
        storage.deviceId = "OLD"
        storage.apphudUser = legacyUser("OLD")
        freshPackage = false
        launch()

        assertEquals(Version1Ids("OLD", "OLD"), store().idsForVersion1())
    }

    @Test
    fun `GIVEN an updated install with a real user WHEN version 1 starts EXPECT the old device id and the user`() {
        storage.deviceId = "OLD"
        storage.apphudUser = legacyUser("alice@example.com")
        freshPackage = false
        launch()

        assertEquals(Version1Ids("OLD", "alice@example.com"), store().idsForVersion1())
    }

    @Test
    fun `GIVEN a logout on version 2 WHEN version 1 starts EXPECT a new device id and the new anonymous id`() {
        storage.deviceId = "OLD"
        storage.apphudUser = legacyUser("alice@example.com")
        freshPackage = false
        val store = store().also { it.resolve(ApphudSuppliedIds()) }

        store.logout(null)

        assertNull(storage.deviceId)
        assertTrue(storage.version1IdsReset)
        val ids = store().idsForVersion1()
        assertEquals(store.current!!.anonymousId, ids?.userId)
        assertNotEquals("never the logged-out person's", "OLD", ids?.deviceId)
        assertTrue("a new device id", !ids?.deviceId.isNullOrEmpty())
    }

    @Test
    fun `GIVEN version 2 never ran EXPECT nothing to take over`() {
        assertNull("a fresh install", store().idsForVersion1())
        storage.deviceId = "OLD"
        storage.apphudUser = legacyUser("OLD")
        assertNull("only an old version ran", store().idsForVersion1())
        store().markVersion1()
        assertNull("only version 1 ran", store().idsForVersion1())
    }

    @Test
    fun `GIVEN version 1 already took the ids over EXPECT nothing to take over again`() {
        val identity = launch().identity
        runVersion1(identity.installationId, identity.anonymousId)

        assertNull(store().idsForVersion1())
    }

    @Test
    fun `GIVEN a build without the version marker WHEN version 1 starts EXPECT the ids of version 2`() {
        val identity = launch().identity
        versions.version = null

        assertEquals(identity.installationId, store().idsForVersion1()?.deviceId)
    }

    @Test
    fun `GIVEN version 1 ran after version 2 WHEN version 2 starts EXPECT its ids taken over again`() {
        val first = launch().identity
        store().markSynced()
        runVersion1(first.installationId, first.anonymousId)

        val resolution = launch()

        assertEquals(ApphudLaunchKind.UPGRADE, resolution.launch)
        assertEquals(first, resolution.identity)
        assertTrue(resolution.changed)
        assertTrue(store().isSyncPending)
        assertEquals(first.installationId, records.record?.legacyDeviceId)
        assertEquals(ApphudIdentityVersion.V2, versions.version)
        assertEquals("V2", storage.idsIdentityVersion)
    }

    @Test
    fun `GIVEN version 1 kept the user of version 2 WHEN version 2 starts EXPECT a sync for the old device id alone`() {
        val store = store().also { it.resolve(ApphudSuppliedIds()) }
        store.identify("alice")
        val first = store.current!!
        runVersion1(first.installationId, "alice")
        store().markSynced()

        val resolution = launch()

        assertEquals(first, resolution.identity)
        assertTrue("only legacy_device_id is new, and it must be registered", resolution.changed)
    }

    @Test
    fun `GIVEN a logout and a new start on version 1 WHEN version 2 starts EXPECT its new ids`() {
        val first = launch().identity
        runVersion1(first.installationId, first.anonymousId)
        store().markVersion1LoggedOut()
        runVersion1("V1-NEW", "V1-NEW", tookOverVersion2 = false)

        val identity = launch().identity

        assertEquals(ApphudIdentity(first.installationId, "V1-NEW", null), identity)
        assertEquals("V1-NEW", records.record?.legacyDeviceId)
    }

    @Test
    fun `GIVEN a logout, a new start and another user on version 1 WHEN version 2 starts EXPECT no anonymous id from before`() {
        val store = store().also { it.resolve(ApphudSuppliedIds()) }
        store.identify("alice")
        val first = store.current!!
        runVersion1(first.installationId, "alice")
        store().markVersion1LoggedOut()
        runVersion1("V1-NEW", "bob", tookOverVersion2 = false)

        val identity = launch().identity

        assertEquals("bob", identity.userId)
        assertNotEquals("alice's anonymous id stays with alice", first.anonymousId, identity.anonymousId)
        assertEquals("V1-NEW", records.record?.legacyDeviceId)
    }

    @Test
    fun `GIVEN a logout on version 1 without a start WHEN version 2 starts EXPECT a new person`() {
        val store = store().also { it.resolve(ApphudSuppliedIds()) }
        store.identify("alice")
        val first = store.current!!
        runVersion1(first.installationId, "alice")
        // Logout on version 1 clears its device id and customer.
        store().markVersion1LoggedOut()
        storage.deviceId = null
        storage.apphudUser = null

        val identity = launch().identity

        assertEquals(first.installationId, identity.installationId)
        assertNotEquals(first.anonymousId, identity.anonymousId)
        assertNull(identity.userId)
        assertNull(records.record?.legacyDeviceId)
    }

    @Test
    fun `GIVEN an old version, then version 2, version 1 and version 2 EXPECT the same ids`() {
        storage.deviceId = "OLD"
        storage.apphudUser = legacyUser("OLD")
        freshPackage = false
        val updated = launch().identity
        val ids = store().idsForVersion1()!!
        runVersion1(ids.deviceId!!, ids.userId)

        val again = launch()

        assertEquals(ApphudLaunchKind.UPGRADE, again.launch)
        assertEquals(updated, again.identity)
        assertEquals(Version1Ids("OLD", "OLD"), store().idsForVersion1())
        assertEquals("OLD", records.record?.legacyDeviceId)
    }

    @Test
    fun `GIVEN version 1 installed the app WHEN version 2 starts without an app update EXPECT an update, not a restore`() {
        // A fresh package: the app was installed with version 1 and never updated.
        freshPackage = true
        runVersion1("V1-DEVICE", "V1-DEVICE", tookOverVersion2 = false)

        val resolution = launch()

        assertEquals(ApphudLaunchKind.UPGRADE, resolution.launch)
        assertEquals(ApphudIdentity("V1-DEVICE", "V1-DEVICE", null), resolution.identity)
        assertEquals(0, clearedUserData)
        assertEquals("V1-DEVICE", records.record?.legacyDeviceId)
    }

    @Test
    fun `GIVEN a backup of an updated install restored and the app updated before it opened EXPECT no update`() {
        storage.deviceId = "OLD"
        storage.apphudUser = legacyUser("OLD")
        freshPackage = false
        val updated = launch().identity
        restoreBackupOntoAnotherPhone()
        freshPackage = false

        val resolution = launch()

        assertEquals(ApphudLaunchKind.NEW_INSTALL, resolution.launch)
        assertNotEquals("its old device id never becomes an installation id", "OLD", resolution.identity.installationId)
        assertEquals(updated.anonymousId, resolution.identity.anonymousId)
    }

    @Test
    fun `GIVEN a switch from version 1 that could not be saved EXPECT it runs again`() {
        val first = launch().identity
        runVersion1(first.installationId, first.anonymousId)
        records.failWrites = true
        assertEquals(ApphudLaunchKind.UPGRADE, launch().launch)

        records.failWrites = false

        assertEquals("the marker waits for the saved record", ApphudLaunchKind.UPGRADE, launch().launch)
        assertEquals(ApphudLaunchKind.NORMAL, launch().launch)
    }

    @Test
    fun `GIVEN version 1 then ids the app supplies on version 2 WHEN version 1 starts EXPECT those ids`() {
        store().markVersion1()
        launch(ApphudSuppliedIds(userId = "host-user", anonymousId = "host-anon", installationId = "host-install", isReinstall = false))

        assertEquals(ApphudIdentityVersion.V2, versions.version)
        assertEquals(Version1Ids("host-install", "host-user"), store().idsForVersion1())
    }

    @Test
    fun `GIVEN an update in place EXPECT both version markers set to version 2`() {
        storage.deviceId = "OLD"
        freshPackage = false

        launch()

        assertEquals(ApphudIdentityVersion.V2, versions.version)
        assertEquals("V2", storage.idsIdentityVersion)
    }

    @Test
    fun `GIVEN a logout on version 1, then version 2, WHEN version 1 starts EXPECT a new device id`() {
        val store = store().also { it.resolve(ApphudSuppliedIds()) }
        store.identify("alice")
        val first = store.current!!
        runVersion1(first.installationId, "alice")
        store().markVersion1LoggedOut()
        storage.deviceId = null
        storage.apphudUser = null
        val second = launch().identity

        val ids = store().idsForVersion1()

        assertEquals(second.anonymousId, ids?.userId)
        assertTrue("a new device id", !ids?.deviceId.isNullOrEmpty())
        assertNotEquals("alice's device id stays with alice", first.installationId, ids?.deviceId)
    }

    @Test
    fun `GIVEN version 1 went on with the person of version 2 WHEN version 2 starts EXPECT that person`() {
        val first = launch().identity
        runVersion1(first.installationId, first.anonymousId)

        val identity = launch().identity

        assertEquals(first, identity)
        assertEquals("its device id adopts the customer again", first.installationId, records.record?.legacyDeviceId)
    }

    @Test
    fun `GIVEN version 1 ran last and logged out, WHEN its backup is restored onto a version 2 phone EXPECT its person`() {
        val store = store().also { it.resolve(ApphudSuppliedIds()) }
        store.identify("alice")
        val first = store.current!!
        runVersion1(first.installationId, "alice")
        store().markVersion1LoggedOut()
        runVersion1("V1-NEW", "bob", tookOverVersion2 = false)
        restoreBackupOntoAnotherPhone()

        val identity = launch().identity

        assertEquals("bob", identity.userId)
        assertNotEquals("alice's anonymous id stays with alice", first.anonymousId, identity.anonymousId)
        assertEquals("V1-NEW", records.record?.legacyDeviceId)
    }

    @Test
    fun `GIVEN version 1 ran last, WHEN its backup is restored and version 1 starts EXPECT its own ids, not those of version 2`() {
        val store = store().also { it.resolve(ApphudSuppliedIds()) }
        store.identify("alice")
        val first = store.current!!
        runVersion1(first.installationId, "alice")
        store().markVersion1LoggedOut()
        runVersion1("V1-NEW", "bob", tookOverVersion2 = false)
        restoreBackupOntoAnotherPhone()

        assertNull("version 1 ran last on these ids: nothing of version 2 to take over", store().idsForVersion1())
    }

    @Test
    fun `GIVEN version 1 took over WHEN no registration succeeded yet EXPECT it still pending on a later launch`() {
        val first = launch().identity
        runVersion1(first.installationId, first.anonymousId)
        assertTrue(store().isVersion1RegistrationPending)

        store().markVersion1()
        assertTrue(store().isVersion1RegistrationPending)

        store().markVersion1Registered()
        assertFalse(store().isVersion1RegistrationPending)
    }

    @Test
    fun `GIVEN the version that ran last EXPECT it known before a start`() {
        assertNull(store().lastIdentityVersion())
        store().markVersion1()
        assertEquals(ApphudIdentityVersion.V1, store().lastIdentityVersion())
        launch()
        assertEquals(ApphudIdentityVersion.V2, store().lastIdentityVersion())
        versions.version = null
        assertEquals("the marker next to the ids when the install's own is gone", ApphudIdentityVersion.V2, store().lastIdentityVersion())
        storage.idsIdentityVersion = null
        assertEquals("ids of version 2 without a marker", ApphudIdentityVersion.V2, store().lastIdentityVersion())
    }

    /** What the logout of version 1 (as in SDK 3.6) leaves: no device id, no cached customer. */
    private fun logOutOnVersion1() {
        store().markVersion1LoggedOut()
        storage.deviceId = null
        storage.apphudUser = null
    }

    @Test
    fun `GIVEN a backup taken after a logout on version 1 WHEN restored onto a version 2 phone EXPECT no person back`() {
        val store = store().also { it.resolve(ApphudSuppliedIds()) }
        store.identify("alice")
        val first = store.current!!
        runVersion1(first.installationId, "alice")
        logOutOnVersion1()
        restoreBackupOntoAnotherPhone()

        val identity = launch().identity

        assertNotEquals(first.anonymousId, identity.anonymousId)
        assertNull(identity.userId)
        assertFalse(storage.version1LoggedOut)
        assertTrue(storage.version1IdsReset)
    }

    @Test
    fun `GIVEN a logout on version 1 WHEN the record cannot be saved EXPECT one new person, not one per launch`() {
        // An update whose old device id is not adopted yet: the switch back changes the record.
        storage.deviceId = "OLD"
        storage.apphudUser = legacyUser("alice@example.com")
        freshPackage = false
        val first = launch().identity
        runVersion1("OLD", "alice@example.com")
        logOutOnVersion1()
        records.failWrites = true

        val once = launch().identity
        val again = launch().identity

        assertNotEquals(first.anonymousId, once.anonymousId)
        assertEquals(once.anonymousId, again.anonymousId)
    }

    @Test
    fun `GIVEN a logout on version 2 EXPECT version 1's device id reset before the new person is saved`() {
        storage.deviceId = "OLD"
        storage.apphudUser = legacyUser("OLD")
        freshPackage = false
        var watching = false
        var resetWhenSaved: Boolean? = null
        val watched = object : Storage by storage {
            override var anonymousId: String?
                get() = storage.anonymousId
                set(value) {
                    if (watching && resetWhenSaved == null) resetWhenSaved = storage.version1IdsReset && storage.deviceId == null
                    storage.anonymousId = value
                }
        }
        val store = IdentityStore(watched, records, versions, { freshPackage }, {}, { "id-x" }).apply { resolve(ApphudSuppliedIds()) }
        assertEquals("OLD", storage.deviceId)

        watching = true
        store.logout(null)

        assertEquals(true, resetWhenSaved)
    }

    @Test
    fun `GIVEN a damaged record after a logout on version 1 EXPECT no person back`() {
        val store = store().also { it.resolve(ApphudSuppliedIds()) }
        store.identify("alice")
        val first = store.current!!
        runVersion1(first.installationId, "alice")
        logOutOnVersion1()
        // A record without an installation id is damaged.
        records.record = InstallRecord(InstallState.EXISTING)

        val identity = launch().identity

        assertNotEquals(first.anonymousId, identity.anonymousId)
        assertNull(identity.userId)
    }

    @Test
    fun `GIVEN a logout on version 2 while the marker still says version 1 WHEN version 1 starts EXPECT a new device id`() {
        val first = launch().identity
        runVersion1(first.installationId, first.anonymousId)
        store().logout(null)

        val ids = store().idsForVersion1()

        assertEquals(storage.anonymousId, ids?.userId)
        assertTrue(!ids?.deviceId.isNullOrEmpty())
        assertNotEquals(first.installationId, ids?.deviceId)
    }

    // endregion
}
