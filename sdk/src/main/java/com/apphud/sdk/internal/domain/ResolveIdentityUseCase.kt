package com.apphud.sdk.internal.domain

import com.apphud.sdk.internal.data.IdentityStore
import com.apphud.sdk.internal.data.ReinstallRepository
import com.apphud.sdk.internal.data.network.FirstRegistrationHold
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
    private val firstRegistrationHold: FirstRegistrationHold = FirstRegistrationHold(),
) {
    operator fun invoke(supplied: ApphudSuppliedIds): ApphudIdentityResolution {
        if (supplied.suppliesAllIds) reinstallRepository.prepareForHostIds()
        val resolution = identityStore.resolve(supplied)
        if (supplied.suppliesAllIds) {
            reinstallRepository.useHostValue(supplied.isReinstall, resolution.identity.installationId)
        } else {
            reinstallRepository.start(
                newInstall = resolution.launch == ApphudLaunchKind.NEW_INSTALL,
                restoredPerson = resolution.restoredPersonPending,
                unsavedLegacyDeviceId = resolution.unsavedLegacyDeviceId,
                installationId = resolution.identity.installationId,
            )
        }
        // The person changed: the record dropped the old customer; the check's copy of it too.
        if (resolution.dropsLegacyDeviceId) reinstallRepository.forgetLegacyDeviceId()
        // legacy_device_id still to be sent (the first launch after an update): requests wait until the
        // backend knows this installation.
        if (reinstallRepository.legacyDeviceId() != null) firstRegistrationHold.close()
        return resolution
    }
}
