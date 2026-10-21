package com.apphud.sdk.internal.domain

import com.apphud.sdk.internal.data.IdentityStore
import com.apphud.sdk.internal.data.ReinstallRepository
import com.apphud.sdk.internal.data.network.FirstRegistrationHold
import com.apphud.sdk.internal.domain.model.ApphudIdentity
import com.apphud.sdk.internal.domain.model.ApphudIdentityResolution
import com.apphud.sdk.internal.domain.model.ApphudLaunchKind
import com.apphud.sdk.internal.domain.model.ApphudSuppliedIds
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.Assert.assertFalse
import org.junit.Test

class ResolveIdentityUseCaseTest {

    private val identityStore: IdentityStore = mockk()
    private val reinstallRepository: ReinstallRepository = mockk {
        every { start(any(), any(), any(), any()) } just runs
        every { useHostValue(any(), any(), any()) } just runs
        every { prepareForHostIds() } just runs
        every { legacyDeviceId() } returns null
        every { forgetLegacyDeviceId() } just runs
    }
    private val hold = FirstRegistrationHold()
    private val useCase = ResolveIdentityUseCase(identityStore, reinstallRepository, hold)

    @Test
    fun `GIVEN host ids EXPECT native detection superseded before host state is written`() {
        resolvesTo(ApphudLaunchKind.NORMAL)
        val supplied = ApphudSuppliedIds.of(anonymousId = "a", installationId = "i", isReinstall = false)

        useCase(supplied)

        verifyOrder {
            reinstallRepository.prepareForHostIds()
            identityStore.resolve(supplied)
            reinstallRepository.useHostValue(false, IDENTITY.installationId)
        }
    }

    @Test
    fun `GIVEN unsaved embedded migration EXPECT live legacy metadata passed to host mode`() {
        every { identityStore.resolve(any()) } returns ApphudIdentityResolution(
            IDENTITY, ApphudLaunchKind.UPGRADE, changed = true, unsavedLegacyDeviceId = "old-device",
        )
        useCase(ApphudSuppliedIds.of(anonymousId = "a", installationId = "i"))
        verify { reinstallRepository.useHostValue(null, IDENTITY.installationId, "old-device") }
    }

    private fun resolvesTo(launch: ApphudLaunchKind, restoredPersonPending: Boolean = false) {
        every { identityStore.resolve(any()) } returns
            ApphudIdentityResolution(IDENTITY, launch, changed = false, restoredPersonPending = restoredPersonPending)
    }

    @Test
    fun `GIVEN every id supplied EXPECT the app's reinstall flag used`() {
        resolvesTo(ApphudLaunchKind.NORMAL)

        useCase(ApphudSuppliedIds.of(anonymousId = "a", installationId = "i", isReinstall = true))

        verify { reinstallRepository.useHostValue(true, IDENTITY.installationId) }
    }

    @Test
    fun `GIVEN every id supplied EXPECT no reinstall check started`() {
        resolvesTo(ApphudLaunchKind.NEW_INSTALL)

        useCase(ApphudSuppliedIds.of(anonymousId = "a", installationId = "i", isReinstall = true))

        verify(exactly = 0) { reinstallRepository.start(any(), any(), any(), any()) }
    }

    @Test
    fun `GIVEN a new install EXPECT the check started holding registration`() {
        resolvesTo(ApphudLaunchKind.NEW_INSTALL)

        useCase(ApphudSuppliedIds())

        verify { reinstallRepository.start(newInstall = true, installationId = IDENTITY.installationId) }
    }

    @Test
    fun `GIVEN a normal launch EXPECT the check started without holding registration`() {
        resolvesTo(ApphudLaunchKind.NORMAL)

        useCase(ApphudSuppliedIds())

        verify { reinstallRepository.start(newInstall = false, installationId = IDENTITY.installationId) }
    }

    @Test
    fun `GIVEN a restored person to confirm EXPECT the check started deciding it`() {
        resolvesTo(ApphudLaunchKind.NEW_INSTALL, restoredPersonPending = true)

        useCase(ApphudSuppliedIds())

        verify { reinstallRepository.start(newInstall = true, restoredPerson = true, installationId = IDENTITY.installationId) }
    }

    @Test
    fun `GIVEN the person changed EXPECT the old device id forgotten`() {
        every { identityStore.resolve(any()) } returns
            ApphudIdentityResolution(IDENTITY, ApphudLaunchKind.NORMAL, changed = true, switchedUser = true, dropsLegacyDeviceId = true)

        useCase(ApphudSuppliedIds.of(userId = "bob"))

        verify { reinstallRepository.forgetLegacyDeviceId() }
    }

    @Test
    fun `GIVEN the host's new anonymous id without a user switch EXPECT the old device id forgotten`() {
        every { identityStore.resolve(any()) } returns
            ApphudIdentityResolution(IDENTITY, ApphudLaunchKind.NORMAL, changed = true, switchedUser = false, dropsLegacyDeviceId = true)

        useCase(ApphudSuppliedIds.of(anonymousId = "host-anon", installationId = "i"))

        verify { reinstallRepository.forgetLegacyDeviceId() }
    }

    @Test
    fun `GIVEN the person changed after an update EXPECT nothing held for the old customer`() {
        var legacy: String? = "OLD"
        every { reinstallRepository.legacyDeviceId() } answers { legacy }
        every { reinstallRepository.forgetLegacyDeviceId() } answers { legacy = null }
        every { identityStore.resolve(any()) } returns
            ApphudIdentityResolution(IDENTITY, ApphudLaunchKind.UPGRADE, changed = true, switchedUser = true, dropsLegacyDeviceId = true)

        useCase(ApphudSuppliedIds.of(userId = "bob"))

        assertFalse("dropped before the hold is decided", hold.isClosed)
    }

    @Test
    fun `GIVEN the same user EXPECT the old device id kept`() {
        resolvesTo(ApphudLaunchKind.NORMAL)

        useCase(ApphudSuppliedIds())

        verify(exactly = 0) { reinstallRepository.forgetLegacyDeviceId() }
    }

    @Test
    fun `GIVEN an old device id the record couldn't keep EXPECT it handed to the check`() {
        every { identityStore.resolve(any()) } returns
            ApphudIdentityResolution(IDENTITY, ApphudLaunchKind.UPGRADE, changed = true, unsavedLegacyDeviceId = "OLD")

        useCase(ApphudSuppliedIds())

        verify { reinstallRepository.start(newInstall = false, restoredPerson = false, unsavedLegacyDeviceId = "OLD", installationId = IDENTITY.installationId) }
    }

    private companion object {
        val IDENTITY = ApphudIdentity("installation-1", "anonymous-1", null)
    }

    @Test
    fun `GIVEN the first launch after an update EXPECT requests held for the first registration`() {
        resolvesTo(ApphudLaunchKind.UPGRADE)
        every { reinstallRepository.legacyDeviceId() } returns "OLD"

        useCase(ApphudSuppliedIds())

        org.junit.Assert.assertTrue(hold.isClosed)
    }

    @Test
    fun `GIVEN an installation the backend knows EXPECT nothing held`() {
        resolvesTo(ApphudLaunchKind.NORMAL)

        useCase(ApphudSuppliedIds())

        org.junit.Assert.assertFalse(hold.isClosed)
    }
}
