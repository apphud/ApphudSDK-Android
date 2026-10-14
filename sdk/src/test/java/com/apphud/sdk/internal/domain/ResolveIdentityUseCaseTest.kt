package com.apphud.sdk.internal.domain

import com.apphud.sdk.internal.data.IdentityStore
import com.apphud.sdk.internal.data.ReinstallRepository
import com.apphud.sdk.internal.domain.model.ApphudIdentity
import com.apphud.sdk.internal.domain.model.ApphudIdentityResolution
import com.apphud.sdk.internal.domain.model.ApphudLaunchKind
import com.apphud.sdk.internal.domain.model.ApphudSuppliedIds
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.Test

class ResolveIdentityUseCaseTest {

    private val identityStore: IdentityStore = mockk()
    private val reinstallRepository: ReinstallRepository = mockk {
        every { start(any(), any(), any()) } just runs
        every { useHostValue(any()) } just runs
        every { forgetLegacyDeviceId() } just runs
    }
    private val useCase = ResolveIdentityUseCase(identityStore, reinstallRepository)

    private fun resolvesTo(launch: ApphudLaunchKind, restoredPersonPending: Boolean = false) {
        every { identityStore.resolve(any()) } returns
            ApphudIdentityResolution(IDENTITY, launch, changed = false, restoredPersonPending = restoredPersonPending)
    }

    @Test
    fun `GIVEN every id supplied EXPECT the app's reinstall flag used`() {
        resolvesTo(ApphudLaunchKind.NORMAL)

        useCase(ApphudSuppliedIds.of(anonymousId = "a", installationId = "i", isReinstall = true))

        verify { reinstallRepository.useHostValue(true) }
    }

    @Test
    fun `GIVEN every id supplied EXPECT no reinstall check started`() {
        resolvesTo(ApphudLaunchKind.NEW_INSTALL)

        useCase(ApphudSuppliedIds.of(anonymousId = "a", installationId = "i", isReinstall = true))

        verify(exactly = 0) { reinstallRepository.start(any(), any(), any()) }
    }

    @Test
    fun `GIVEN a new install EXPECT the check started holding registration`() {
        resolvesTo(ApphudLaunchKind.NEW_INSTALL)

        useCase(ApphudSuppliedIds())

        verify { reinstallRepository.start(newInstall = true) }
    }

    @Test
    fun `GIVEN a normal launch EXPECT the check started without holding registration`() {
        resolvesTo(ApphudLaunchKind.NORMAL)

        useCase(ApphudSuppliedIds())

        verify { reinstallRepository.start(newInstall = false) }
    }

    @Test
    fun `GIVEN a restored person to confirm EXPECT the check started deciding it`() {
        resolvesTo(ApphudLaunchKind.NEW_INSTALL, restoredPersonPending = true)

        useCase(ApphudSuppliedIds())

        verify { reinstallRepository.start(newInstall = true, restoredPerson = true) }
    }

    @Test
    fun `GIVEN another user than the stored one EXPECT the old device id forgotten`() {
        every { identityStore.resolve(any()) } returns
            ApphudIdentityResolution(IDENTITY, ApphudLaunchKind.NORMAL, changed = true, switchedUser = true)

        useCase(ApphudSuppliedIds.of(userId = "bob"))

        verify { reinstallRepository.forgetLegacyDeviceId() }
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

        verify { reinstallRepository.start(newInstall = false, restoredPerson = false, unsavedLegacyDeviceId = "OLD") }
    }

    private companion object {
        val IDENTITY = ApphudIdentity("installation-1", "anonymous-1", null)
    }
}
