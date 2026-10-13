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
        every { start(any()) } just runs
        every { useHostValue(any()) } just runs
    }
    private val useCase = ResolveIdentityUseCase(identityStore, reinstallRepository)

    private fun resolvesTo(launch: ApphudLaunchKind) {
        every { identityStore.resolve(any()) } returns ApphudIdentityResolution(IDENTITY, launch, changed = false)
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

        verify(exactly = 0) { reinstallRepository.start(any()) }
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

    private companion object {
        val IDENTITY = ApphudIdentity("installation-1", "anonymous-1", null)
    }
}
