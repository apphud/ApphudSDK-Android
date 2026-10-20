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
import com.apphud.sdk.ApphudInternal
import com.apphud.sdk.internal.ApphudDispatchers
import com.apphud.sdk.internal.ServiceLocator
import com.apphud.sdk.internal.domain.EnrichPlacementProductsUseCase
import com.apphud.sdk.internal.domain.RegisterUserUseCase
import com.apphud.sdk.internal.domain.RegistrationInteractor
import com.apphud.sdk.internal.domain.ResolveIdentityUseCase
import com.apphud.sdk.managers.RequestManager
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.unmockkObject
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody

class IdentityStoreTest {

    @Test
    fun `GIVEN identical legacy credentials on separate V1 installs EXPECT unique V2 installations and legacy adoption`() {
        fun migrate(): Pair<String, String?> {
            val local = FakeStorage().apply {
                deviceId = "shared-legacy-device"
                userId = "legacy-user"
                apphudUser = legacyUser("legacy-user")
            }
            val users = mockk<UserRepository> {
                every { getDeviceId() } answers { local.deviceId }
                every { getUserId() } answers { local.userId }
            }
            com.apphud.sdk.internal.domain.ResolveCredentialsUseCase(users)(null, null)
            val localRecords = FakeInstallRecords()
            val identity = IdentityStore(local, localRecords, FakeVersions(), { false }, {})
            identity.markVersion1(tookOverVersion2 = false)
            val resolved = identity.resolve(ApphudSuppliedIds()).identity
            return resolved.installationId to localRecords.record?.legacyDeviceId
        }
        val first = migrate()
        val second = migrate()
        assertTrue(first.first != second.first && first.first != "shared-legacy-device" &&
            second.first != "shared-legacy-device" && first.second == "shared-legacy-device" && second.second == first.second)
    }

    @Test
    fun `GIVEN existing V2 installation through V1 rollback EXPECT installation retained`() {
        val original = launch().identity
        runVersion1(original.installationId, "Alice")
        assertEquals(original.installationId, launch().identity.installationId)
    }

    private fun seedEmbeddedLegacy(user: String = "legacy-user") {
        storage.deviceId = "legacy-device"
        storage.apphudUser = legacyUser(user)
        freshPackage = false
    }

    private fun embeddedIds(user: String? = null, reinstall: Boolean? = null) =
        ApphudSuppliedIds.of(userId = user, anonymousId = "host-anonymous", installationId = "host-installation", isReinstall = reinstall)

    @Test
    fun `GIVEN embedded legacy upgrade EXPECT old device sent separately`() {
        seedEmbeddedLegacy()
        val resolution = launch(embeddedIds())
        assertEquals("legacy-device" to ApphudLaunchKind.UPGRADE, records.record?.legacyDeviceId to resolution.launch)
    }

    @Test
    fun `GIVEN embedded legacy upgrade without host user EXPECT anonymous host identity`() {
        seedEmbeddedLegacy()
        assertEquals(ApphudIdentity("host-installation", "host-anonymous", null), launch(embeddedIds()).identity)
    }

    @Test
    fun `GIVEN embedded generated legacy user echoed EXPECT no identified user`() {
        seedEmbeddedLegacy("legacy-device")
        assertNull(launch(embeddedIds(user = "legacy-device")).identity.userId)
    }

    @Test
    fun `GIVEN embedded generated legacy user WHEN identified after relaunch EXPECT no promotion`() {
        seedEmbeddedLegacy("legacy-device")
        launch(embeddedIds())
        val identity = store().also { it.resolve(embeddedIds()) }
        assertEquals(ApphudIdentifyOutcome.UNCHANGED, identity.identify("legacy-device"))
    }

    @Test
    fun `GIVEN embedded current generated device after earlier migration EXPECT no promotion`() {
        storage.oldDeviceId = "earlier-generated-device"
        seedEmbeddedLegacy("legacy-device")
        launch(embeddedIds())
        val identity = store().also { it.resolve(embeddedIds()) }
        assertEquals(ApphudIdentifyOutcome.UNCHANGED, identity.identify("legacy-device"))
    }

    @Test
    fun `GIVEN embedded legacy real user WHEN first identified EXPECT adoption retained`() {
        seedEmbeddedLegacy()
        val identity = store().also { it.resolve(embeddedIds()) }
        identity.identify("legacy-user")
        assertEquals("legacy-device", records.record?.legacyDeviceId)
    }

    @Test
    fun `GIVEN embedded migration registration acknowledged WHEN relaunched EXPECT no legacy resurrection`() {
        seedEmbeddedLegacy()
        launch(embeddedIds())
        records.record = records.record!!.copy(legacyDeviceId = null)
        launch(embeddedIds())
        assertNull(records.record?.legacyDeviceId)
    }

    @Test
    fun `GIVEN embedded legacy migration not acknowledged WHEN relaunched EXPECT legacy retained`() {
        seedEmbeddedLegacy()
        launch(embeddedIds())
        launch(embeddedIds())
        assertEquals("legacy-device", records.record?.legacyDeviceId)
    }

    @Test
    fun `GIVEN embedded old real user and new host user EXPECT no old customer adoption`() {
        seedEmbeddedLegacy()
        val resolution = launch(embeddedIds(user = "another-user"))
        assertEquals(null to true, records.record?.legacyDeviceId to resolution.dropsLegacyDeviceId)
    }

    @Test
    fun `GIVEN embedded host reinstall EXPECT no old customer adoption`() {
        seedEmbeddedLegacy()
        val resolution = launch(embeddedIds(reinstall = true))
        assertEquals(null to true, records.record?.legacyDeviceId to resolution.dropsLegacyDeviceId)
    }

    @Test
    fun `GIVEN embedded migration and host logout EXPECT no old customer adoption`() {
        seedEmbeddedLegacy()
        launch(embeddedIds())
        launch(ApphudSuppliedIds.of(anonymousId = "logged-out-anonymous", installationId = "host-installation"))
        assertNull(records.record?.legacyDeviceId)
    }

    @Test
    fun `GIVEN embedded legacy record write fails EXPECT live migration metadata`() {
        seedEmbeddedLegacy()
        records.failWrites = true
        assertEquals("legacy-device", launch(embeddedIds()).unsavedLegacyDeviceId)
    }

    @Test
    fun `GIVEN embedded legacy record write fails EXPECT old saved identity untouched`() {
        seedEmbeddedLegacy()
        records.failWrites = true
        launch(embeddedIds())
        assertEquals(Triple(null, null, null), Triple(storage.anonymousId, storage.identifiedUserId, storage.idsIdentityVersion))
    }

    @Test
    fun `GIVEN embedded legacy record write fails WHEN restarted EXPECT migration retried`() {
        seedEmbeddedLegacy()
        records.failWrites = true
        launch(embeddedIds())
        records.failWrites = false
        launch(embeddedIds())
        assertEquals("legacy-device", records.record?.legacyDeviceId)
    }

    @Test
    fun `GIVEN embedded V1 alias for real host user equal installation EXPECT real user preserved`() {
        launch(ApphudSuppliedIds.of(userId = "host-installation", anonymousId = "host-anonymous", installationId = "host-installation"))
        runVersion1("host-installation", "host-installation")
        assertEquals("host-installation", launch(embeddedIds(user = "host-installation")).identity.userId)
    }

    @Test
    fun `GIVEN embedded explicit host device and user equal EXPECT identified user`() {
        seedEmbeddedLegacy("legacy-device")
        assertEquals("legacy-device", launch(ApphudSuppliedIds.of(userId = "legacy-device", anonymousId = "host-anonymous", installationId = "legacy-device")).identity.userId)
    }

    @Test
    fun `GIVEN ambiguous legacy upgrade without install marker EXPECT fresh installation and separate legacy id`() {
        seedEmbeddedLegacy()
        val resolution = launch()
        assertTrue(resolution.identity.installationId != "legacy-device" && records.record?.legacyDeviceId == "legacy-device")
    }

    @Test
    fun `GIVEN ambiguous legacy upgrade WHEN relaunched EXPECT generated installation kept`() {
        seedEmbeddedLegacy()
        val first = launch().identity
        assertEquals(first.installationId, launch().identity.installationId)
    }


    @Test
    fun `GIVEN reinstall reported for a previous host installation EXPECT new host id not reported`() {
        launch(ApphudSuppliedIds.of(anonymousId = "anon", installationId = "first"))
        records.record = records.record!!.copy(reinstallReported = true)
        launch(ApphudSuppliedIds.of(anonymousId = "anon", installationId = "second"))

        assertFalse(records.record!!.reinstallReported)
    }

    @Test
    fun `GIVEN reinstall reported for the same host installation EXPECT report acknowledgement kept`() {
        launch(ApphudSuppliedIds.of(anonymousId = "anon", installationId = "first"))
        records.record = records.record!!.copy(reinstallReported = true)
        launch(ApphudSuppliedIds.of(anonymousId = "anon", installationId = "first"))

        assertTrue(records.record!!.reinstallReported)
    }

    @Test
    fun `GIVEN identify races with pending persistence EXPECT the newer identity stays pending`() {
        val entered = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)
        val failure = java.util.concurrent.atomic.AtomicReference<Throwable?>()
        val backing = object : Storage by storage {
            override var identitySyncPending: Boolean
                get() = storage.identitySyncPending
                set(value) {
                    if (!value) {
                        entered.countDown()
                        check(release.await(5, java.util.concurrent.TimeUnit.SECONDS))
                    }
                    storage.identitySyncPending = value
                }
        }
        val identity = store(backing)
        val sent = identity.resolve(ApphudSuppliedIds(userId = "Alice")).identity
        val revision = identity.pendingRevision
        val response = kotlin.concurrent.thread(name = "identity-response") {
            runCatching { identity.markSynced(sent, revision) }.exceptionOrNull()?.let { failure.set(it) }
        }
        try {
            check(entered.await(5, java.util.concurrent.TimeUnit.SECONDS))
            val identify = kotlin.concurrent.thread(name = "identity-switch") {
                runCatching { identity.identify("Bob") }.exceptionOrNull()?.let { failure.set(it) }
            }
            try {
                fun waitingForResponse(): Boolean = identify.state == Thread.State.BLOCKED &&
                    identify.stackTrace.firstOrNull()?.let {
                        it.className == IdentityStore::class.java.name && it.methodName == "identify"
                    } == true
                val deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5)
                while (identify.isAlive && !waitingForResponse() && System.nanoTime() < deadline) Thread.yield()
                check(!identify.isAlive || waitingForResponse())
            } finally {
                release.countDown()
                identify.join(5_000)
                check(!identify.isAlive)
            }
        } finally {
            release.countDown()
            response.join(5_000)
        }
        check(!response.isAlive && failure.get() == null) { failure.get().toString() }

        assertTrue(identity.isSyncPending)
    }

    @Test
    fun `GIVEN identify changes while a response is checked EXPECT the new identity remains pending`() = runTest {
        val identity = store()
        identity.resolve(ApphudSuppliedIds(userId = "Alice"))
        registrationWithInterleavedChange(identity) { identity.identify("Bob") }

        assertTrue(identity.isSyncPending)
    }

    @Test
    fun `GIVEN reinstall becomes pending while a response is checked EXPECT pending remains`() = runTest {
        val identity = store()
        identity.resolve(ApphudSuppliedIds(userId = "Alice"))
        registrationWithInterleavedChange(identity) { identity.markSyncPending() }

        assertTrue(identity.isSyncPending)
    }

    @Test
    fun `GIVEN full host ids after a pending restore EXPECT a late native verdict cannot change them`() {
        val identity = hostAfterPendingRestore()
        identity.dropRestoredPerson()

        assertEquals(ApphudIdentity("host-installation", "restored-anonymous", "restored-user"), identity.current)
    }

    @Test
    fun `GIVEN full host ids after a pending restore EXPECT the persisted restore hint cleared`() {
        hostAfterPendingRestore()

        assertFalse(records.record!!.restoredPerson)
    }

    private fun hostAfterPendingRestore(): IdentityStore {
        storage.anonymousId = "restored-anonymous"
        storage.identifiedUserId = "restored-user"
        storage.idsIdentityVersion = ApphudIdentityVersion.V2.name
        val identity = store()
        identity.resolve(ApphudSuppliedIds())
        identity.logout(null)
        val supplied = ApphudSuppliedIds("restored-user", "restored-anonymous", "host-installation", false)
        identity.resolve(supplied)
        return identity
    }

    @Test
    fun `GIVEN backed up V1 prefs and APK update before first V2 start EXPECT native restore check`() {
        runVersion1("old-device", "Alice")
        restoreBackupOntoAnotherPhone()
        freshPackage = false

        assertTrue(launch().restoredPersonPending)
    }

    @Test
    fun `GIVEN V1 backup and APK update on this device EXPECT marker drops the restored person`() = runTest {
        runVersion1("old-device", "Alice")
        records.record = null
        versions.version = null
        freshPackage = false
        val identity = store()
        val marker = byteArrayOf(1, 2, 3)
        val source = mockk<InstallMarkerSource> {
            coEvery { read() } returns marker
            coEvery { write(any()) } just runs
        }
        val reinstall = ReinstallRepository(records, source, { marker }, this,
            onDecided = { identity.markSyncPending() }, dropRestoredPerson = { identity.dropRestoredPerson() })
        ResolveIdentityUseCase(identity, reinstall)(ApphudSuppliedIds())
        reinstall.awaitBeforeRegistration()

        assertEquals(null, identity.current?.userId)
    }

    private suspend fun registrationWithInterleavedChange(identity: IdentityStore, change: () -> Unit) {
        val sent = checkNotNull(identity.current)
        val body = com.apphud.sdk.body.RegistrationBody(
            locale = "en", sdkVersion = "test", appVersion = "1", deviceFamily = "Android",
            deviceType = "test", platform = "android", osVersion = "35", startAppVersion = "1",
            idfv = null, idfa = null, androidId = null, userId = sent.userId,
            installationId = sent.installationId, anonymousId = sent.anonymousId,
            timeZone = "UTC", isSandbox = true, isNew = false, needPlacements = false,
            firstSeen = null, sdkLaunchedAt = 0, requestTime = 0, installSource = "test",
            observerMode = true, fromWeb2web = null, email = null,
        )
        val request = okhttp3.Request.Builder().url("https://api.apphuddev.com/v4/customers").build()
        val response = okhttp3.Response.Builder().request(request).protocol(okhttp3.Protocol.HTTP_1_1)
            .code(201).message("Created").body(
                """{"data":{"results":{"id":"customer-A","user_id":"Alice"},"meta":null}}"""
                    .toResponseBody("application/json".toMediaType())
            ).build()
        val client = mockk<okhttp3.OkHttpClient> {
            every { newCall(any()) } returns mockk<okhttp3.Call> { every { execute() } returns response }
        }
        val marker = mockk<ReinstallRepository> {
            coEvery { awaitBeforeRegistration() } returns null
            every { onRegistered(any(), any(), any()) } just runs
            // This interleaves exactly between the response's identity check and pending clear.
            every { reinstallFlag() } answers { change(); null }
        }
        val mapper = mockk<com.apphud.sdk.internal.data.mapper.CustomerMapper> {
            every { map(any(), any()) } returns legacyUser("Alice")
        }
        val factory = mockk<com.apphud.sdk.internal.data.remote.RegistrationBodyFactory> {
            every { create(any(), any(), any(), any()) } returns body
        }
        val urls = mockk<com.apphud.sdk.internal.data.network.UrlProvider> {
            every { customersUrl } returns request.url
            every { updateConnectDomainUrl(any()) } just runs
        }
        val repository = com.apphud.sdk.internal.data.remote.RemoteRepository(
            okHttpClient = client, gson = com.google.gson.Gson(), customerMapper = mapper,
            purchaseBodyFactory = mockk(), registrationBodyFactory = factory,
            productMapper = mockk(), attributionMapper = mockk(), notificationMapper = mockk(), paywallsMapper = mockk(),
            urlProvider = urls, dispatchers = ApphudDispatchers(), reinstallRepository = marker, identityStore = identity,
        )
        check(repository.getCustomers(false, false).isSuccess)
    }

    @Test
    fun `GIVEN failed identify WHEN the same id is retried EXPECT a second registration`() = runTest {
        assertEquals(2, identifyRegistrationAttempts(failFirst = true).attempts)
    }

    @Test
    fun `GIVEN successful identify WHEN the same id is repeated EXPECT no second registration`() = runTest {
        assertEquals(1, identifyRegistrationAttempts(failFirst = false).attempts)
    }

    @Test
    fun `GIVEN a successful identify retry WHEN the same id repeats EXPECT no further registration`() = runTest {
        assertEquals(2, identifyRegistrationAttempts(failFirst = true, repeats = 2).attempts)
    }

    @Test
    fun `GIVEN a successful identify retry EXPECT the customer cache has the chosen user`() = runTest {
        assertEquals("Alice", identifyRegistrationAttempts(failFirst = true).cachedUserId)
    }

    @Test
    fun `GIVEN a successful identify retry EXPECT no pending synchronization`() = runTest {
        assertFalse(identifyRegistrationAttempts(failFirst = true).syncPending)
    }

    @Test
    fun `GIVEN failed identify WHEN the anonymous id is supplied EXPECT no retry for that id`() = runTest {
        assertEquals(1, identifyRegistrationAttempts(failFirst = true, repeatUserId = null).attempts)
    }

    @Test
    fun `GIVEN restored V2 ids and an APK update before start EXPECT the confirmed reinstall drops the user`() = runTest {
        assertNull(resolveSameDeviceRestore(packageUpdatedBeforeStart = true).current?.userId)
    }

    @Test
    fun `GIVEN restored V2 ids without an APK update EXPECT the confirmed reinstall drops the user`() = runTest {
        assertNull(resolveSameDeviceRestore(packageUpdatedBeforeStart = false).current?.userId)
    }

    @Test
    fun `GIVEN restored V2 ids after an APK update EXPECT the confirmed reinstall rotates the anonymous id`() = runTest {
        assertNotEquals("id-2", resolveSameDeviceRestore(packageUpdatedBeforeStart = true).current?.anonymousId)
    }

    @Test
    fun `GIVEN confirmed reinstall after an APK update WHEN the SDK restarts EXPECT the new person kept`() = runTest {
        val reinstalled = resolveSameDeviceRestore(packageUpdatedBeforeStart = true).current

        assertEquals(reinstalled?.copy(userId = null), launch().identity)
    }

    @Test
    fun `GIVEN restored V2 ids after an APK update and no marker EXPECT the user kept`() = runTest {
        assertEquals("Alice", resolveSameDeviceRestore(packageUpdatedBeforeStart = true, markerPresent = false).current?.userId)
    }

    @Test
    fun `GIVEN restored V2 ids after an APK update and an unreadable marker EXPECT the user kept`() = runTest {
        assertEquals("Alice", resolveSameDeviceRestore(packageUpdatedBeforeStart = true, markerReadFails = true).current?.userId)
    }

    @Test
    fun `GIVEN restored V2 ids after an APK update and the app supplies the user EXPECT it kept`() = runTest {
        assertEquals("Alice", resolveSameDeviceRestore(packageUpdatedBeforeStart = true, supplied = ApphudSuppliedIds(userId = "Alice")).current?.userId)
    }

    @Test
    fun `GIVEN offline first registration WHEN identify EXPECT intent persisted and recovery followed by no-op`() = runTest {
        val result = identifyRegistrationAttempts(failFirst = false, repeats = 2, withoutCachedCustomer = true)
        assertEquals(1, result.attempts)
        assertEquals("Alice", result.cachedUserId)
        assertFalse(result.syncPending)
    }

    @Test
    fun `GIVEN explicit host device and user equal WHEN first upgrade EXPECT an identified person`() {
        storage.deviceId = "u42"
        storage.apphudUser = legacyUser("u42")
        freshPackage = false
        val identity = store()
        val first = identity.resolve(ApphudSuppliedIds.of(userId = "u42", installationId = "u42")).identity
        assertEquals("u42", first.userId)
        assertNotEquals("u42", first.anonymousId)
        assertEquals("u42", storage.identifiedUserId)
        assertNull(storage.oldDeviceId)
        assertEquals(ApphudIdentifyOutcome.UNCHANGED, identity.identify("u42"))
        val restarted = launch(ApphudSuppliedIds.of(userId = "u42", installationId = "u42")).identity
        assertEquals(first, restarted)
    }

    @Test
    fun `GIVEN restored legacy registration predates this package and APK updated EXPECT a new install`() {
        storage.deviceId = "old-phone"
        storage.apphudUser = legacyUser("Alice")
        storage.lastRegistration = 1_700_000_000_000L
        firstInstallMillis = 1_700_100_000_000L
        freshPackage = false
        val resolution = launch()
        assertEquals(ApphudLaunchKind.NEW_INSTALL, resolution.launch)
        assertNotEquals("old-phone", resolution.identity.installationId)
        assertEquals("old-phone", records.record?.legacyDeviceId)
        assertEquals(InstallState.PENDING, records.record?.state)
        assertTrue(resolution.restoredPersonPending)
    }

    @Test
    fun `GIVEN legacy registration at or after install EXPECT upgrade with separate legacy id`() {
        for (registration in listOf(1_700_100_000_000L, 1_700_200_000_000L)) {
            records.record = null
            versions.version = null
            storage.idsIdentityVersion = null
            storage.deviceId = "old-phone"
            storage.apphudUser = legacyUser("Alice")
            storage.lastRegistration = registration
            firstInstallMillis = 1_700_100_000_000L
            freshPackage = false
            val resolution = launch()
            assertEquals(ApphudLaunchKind.UPGRADE, resolution.launch)
            assertNotEquals("old-phone", resolution.identity.installationId)
            assertEquals("old-phone", records.record?.legacyDeviceId)
        }
    }

    @Test
    fun `GIVEN install scoped V1 marker EXPECT upgrade even if registration date looks older`() {
        storage.deviceId = "old-phone"
        storage.apphudUser = legacyUser("Alice")
        storage.lastRegistration = 100L
        firstInstallMillis = 200L
        freshPackage = false
        versions.version = ApphudIdentityVersion.V1
        assertEquals(ApphudLaunchKind.UPGRADE, launch().launch)
    }

    @Test
    fun `GIVEN unknown registration or package date EXPECT existing upgrade fallback`() {
        for ((registration, installed) in listOf(0L to 200L, 100L to 0L, 100L to null)) {
            records.record = null
            versions.version = null
            storage.idsIdentityVersion = null
            storage.deviceId = "old-phone"
            storage.lastRegistration = registration
            firstInstallMillis = installed
            freshPackage = false
            assertEquals(ApphudLaunchKind.UPGRADE, launch().launch)
        }
    }

    private data class IdentifyResult(val attempts: Int, val cachedUserId: String?, val syncPending: Boolean)

    private suspend fun TestScope.identifyRegistrationAttempts(
        failFirst: Boolean,
        repeats: Int = 1,
        repeatUserId: String? = "Alice",
        withoutCachedCustomer: Boolean = false,
    ): IdentifyResult {
        val identity = store()
        val anonymous = identity.resolve(ApphudSuppliedIds()).identity
        if (!withoutCachedCustomer) identity.markSynced()
        storage.apphudUser = if (withoutCachedCustomer) null else legacyUser(anonymous.anonymousId)
        val dataSource = mockk<UserDataSource> {
            every { getCachedUser() } answers { storage.apphudUser }
            every { saveUser(any()) } answers {
                storage.apphudUser = firstArg()
                true
            }
            every { updateLastRegistrationTime(any()) } just runs
        }
        val users = UserRepository(dataSource, identity)
        val enrichment = mockk<EnrichPlacementProductsUseCase>()
        every { enrichment.invoke(any<ApphudUser>()) } just runs
        val interactor = RegistrationInteractor(users, RegisterUserUseCase(users, dataSource, RequestManager), enrichment)
        val locator = mockk<ServiceLocator> {
            every { identityStore } returns identity
            every { userRepository } returns users
            every { registrationInteractor } returns interactor
            every { coroutineScope } returns backgroundScope
            every { dispatchers } returns ApphudDispatchers(main = StandardTestDispatcher(testScheduler))
        }
        val apiKeyField = ApphudInternal::class.java.getDeclaredField("apiKey").apply { isAccessible = true }
        val contextField = ApphudInternal::class.java.getDeclaredField("context").apply { isAccessible = true }
        val versionChosenField = ApphudInternal::class.java.getDeclaredField("identityVersionChosen").apply { isAccessible = true }
        val previousApiKey = apiKeyField.get(ApphudInternal)
        val previousContext = contextField.get(ApphudInternal)
        val previousVersionChosen = versionChosenField.getBoolean(ApphudInternal)
        val previousVersion = ApphudInternal.identityVersion
        var attempts = 0
        mockkObject(ServiceLocator.Companion, RequestManager)
        var registrationOffline = withoutCachedCustomer
        if (withoutCachedCustomer) {
            mockkObject(ApphudInternal)
            coEvery { ApphudInternal.awaitUserRegistration() } coAnswers {
                if (registrationOffline) throw IOException("first registration is offline")
            }
        }
        try {
            every { ServiceLocator.instance } returns locator
            every { RequestManager.setParams(any(), any()) } just runs
            coEvery { RequestManager.registration(any(), any(), any(), any(), any(), any()) } answers {
                attempts += 1
                if (failFirst && attempts == 1) throw IOException("controlled offline registration")
                identity.markSynced()
                legacyUser("Alice")
            }
            ApphudInternal.identityVersion = ApphudIdentityVersion.V2
            ApphudInternal.context = mockk(relaxed = true)
            apiKeyField.set(ApphudInternal, "local-test-key")

            ApphudInternal.updateUserId("Alice")
            check(identity.current?.userId == "Alice") { "Identify must be saved even when registration fails before a customer exists" }
            if (withoutCachedCustomer) {
                check(identity.isSyncPending)
                check(storage.apphudUser == null)
                check(store().resolve(ApphudSuppliedIds()).identity.userId == "Alice")
                registrationOffline = false
            }
            if (failFirst) {
                check(identity.isSyncPending)
                check(users.getCurrentUser()?.userId == anonymous.anonymousId)
            }
            repeat(repeats) { ApphudInternal.updateUserId(repeatUserId ?: anonymous.anonymousId) }
            return IdentifyResult(attempts, users.getCurrentUser()?.userId, identity.isSyncPending)
        } finally {
            ApphudInternal.identityVersion = previousVersion
            versionChosenField.setBoolean(ApphudInternal, previousVersionChosen)
            apiKeyField.set(ApphudInternal, previousApiKey)
            contextField.set(ApphudInternal, previousContext)
            unmockkObject(ServiceLocator.Companion, RequestManager)
            if (withoutCachedCustomer) unmockkObject(ApphudInternal)
        }
    }

    private suspend fun TestScope.resolveSameDeviceRestore(
        packageUpdatedBeforeStart: Boolean,
        markerPresent: Boolean = true,
        markerReadFails: Boolean = false,
        supplied: ApphudSuppliedIds = ApphudSuppliedIds(),
    ): IdentityStore {
        val original = store().apply { resolve(ApphudSuppliedIds()); identify("Alice") }
        val previousInstallation = original.current?.installationId
        storage.apphudUser = legacyUser("Alice")
        // Preferences return with the backup; neither noBackup file returns.
        records.record = null
        versions.version = null
        freshPackage = !packageUpdatedBeforeStart
        val next = store()
        val marker = byteArrayOf(1, 2, 3)
        val markers = mockk<InstallMarkerSource> {
            coEvery { read() } answers {
                if (markerReadFails) throw IOException("controlled marker read failure")
                marker.takeIf { markerPresent }
            }
            coEvery { write(any()) } just runs
        }
        val reinstall = ReinstallRepository(
            storage = records,
            markerSource = markers,
            deviceMarker = { marker },
            scope = this,
            onDecided = { next.markSyncPending() },
            dropRestoredPerson = { next.dropRestoredPerson() },
        )

        ResolveIdentityUseCase(next, reinstall)(supplied)
        check(reinstall.awaitBeforeRegistration() == if (markerReadFails) null else markerPresent)
        check(next.current?.installationId != previousInstallation)
        return next
    }

    private class FakeStorage : Storage {
        override var lastRegistration: Long = 0L
        override var userId: String? = null
        override var deviceId: String? = null
        override var oldDeviceId: String? = null
        override var additionalOldDeviceIds: Set<String> = emptySet()
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
    private var firstInstallMillis: Long? = null
    private var clearedUserData = 0
    private var nextId = 0

    /** A launch over the same device storage. */
    private fun store(backing: Storage = storage) = IdentityStore(
        storage = backing,
        installRecords = records,
        versions = versions,
        isFreshPackageInstall = { freshPackage },
        clearUserData = {
            clearedUserData += 1
            storage.apphudUser = null
            storage.deviceId = null
        },
        makeId = { "id-${++nextId}" },
        firstInstallTimeMillis = { firstInstallMillis },
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
    fun `GIVEN old device id without install marker EXPECT fresh installation and legacy adoption`() {
        storage.deviceId = "OLD"
        storage.apphudUser = legacyUser("OLD")
        freshPackage = false

        val resolution = launch()

        assertEquals(ApphudLaunchKind.UPGRADE to "OLD", resolution.launch to records.record?.legacyDeviceId)
        assertNotEquals("OLD", resolution.identity.installationId)
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

        assertEquals("id-2" to "alice", identity.anonymousId to identity.userId)
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
    fun `GIVEN update killed before the record was saved WHEN relaunch EXPECT same person and legacy adoption`() {
        storage.deviceId = "OLD"
        storage.apphudUser = legacyUser("OLD")
        freshPackage = false
        records.failWrites = true
        launch()
        records.failWrites = false

        val identity = launch().identity

        assertEquals(Triple(true, "OLD", null), Triple(identity.installationId != "OLD", identity.anonymousId, identity.userId))
        assertEquals("OLD", records.record?.legacyDeviceId)
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
    fun `GIVEN restored ids on an updated package EXPECT the person waits for the check`() {
        storage.anonymousId = "RESTORED-ANON"
        freshPackage = false

        launch()

        assertEquals(true, records.record?.restoredPerson)
    }

    @Test
    fun `GIVEN an updated package with its install record EXPECT the current person kept without clearing caches`() {
        val original = store().apply { resolve(ApphudSuppliedIds()); identify("Alice") }.current
        val cached = legacyUser("Alice")
        storage.apphudUser = cached
        freshPackage = false

        val resolution = launch()

        assertEquals(Triple(original, cached, 0), Triple(resolution.identity, storage.apphudUser, clearedUserData))
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
    fun `GIVEN missing record on an updated package EXPECT the restored customer cache cleared`() {
        store().also { it.resolve(ApphudSuppliedIds()) }.identify("Alice")
        storage.apphudUser = legacyUser("Alice")
        records.record = null
        freshPackage = false

        launch()

        assertNull(storage.apphudUser)
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
        assertEquals("V1-DEVICE" to null, resolution.identity.anonymousId to resolution.identity.userId)
        assertNotEquals("V1-DEVICE", resolution.identity.installationId)
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
