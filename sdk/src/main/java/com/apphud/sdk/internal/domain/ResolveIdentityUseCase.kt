package com.apphud.sdk.internal.domain

import com.apphud.sdk.internal.data.IdentityStore
import com.apphud.sdk.internal.data.ReinstallRepository
import com.apphud.sdk.internal.domain.model.ApphudIdentityResolution
import com.apphud.sdk.internal.domain.model.ApphudLaunchKind
import com.apphud.sdk.internal.domain.model.ApphudSuppliedIds

/**
 * Resolves the ids at `start` and starts the reinstall check. When the app supplies every id it
 * also decides the reinstall flag, and no check runs.
 */
internal class ResolveIdentityUseCase(
    private val identityStore: IdentityStore,
    private val reinstallRepository: ReinstallRepository,
) {
    operator fun invoke(supplied: ApphudSuppliedIds): ApphudIdentityResolution {
        val resolution = identityStore.resolve(supplied)
        if (supplied.suppliesAllIds) {
            reinstallRepository.useHostValue(supplied.isReinstall)
        } else {
            reinstallRepository.start(newInstall = resolution.launch == ApphudLaunchKind.NEW_INSTALL)
        }
        return resolution
    }
}
