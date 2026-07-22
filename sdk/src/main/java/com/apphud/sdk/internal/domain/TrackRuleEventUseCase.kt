package com.apphud.sdk.internal.domain

import com.apphud.sdk.ApphudLog
import com.apphud.sdk.internal.data.UserRepository
import com.apphud.sdk.internal.data.dto.RuleEventDto
import com.apphud.sdk.internal.data.remote.RemoteRepository
import com.apphud.sdk.internal.util.runCatchingCancellable

/**
 * Tracks a rule-related analytics event on the shared `v2/events` endpoint.
 *
 * Mirrors iOS `ApphudInternal.trackEvent(params:)`, used for `$screen_presented`,
 * `$purchase`, `$survey_answer`, `$feedback`, `$billing_issue` and `$push_opened`.
 */
internal class TrackRuleEventUseCase(
    private val remoteRepository: RemoteRepository,
    private val userRepository: UserRepository,
) {

    suspend operator fun invoke(
        ruleId: String,
        screenId: String?,
        name: String,
        properties: Map<String, Any>? = null,
        paywallId: String? = null,
    ) {
        val deviceId = userRepository.getDeviceId()
        if (deviceId == null) {
            ApphudLog.logE("Cannot track rule event '$name': SDK not initialized")
            return
        }

        runCatchingCancellable {
            remoteRepository.trackRuleEvent(
                RuleEventDto(
                    deviceId = deviceId,
                    ruleId = ruleId,
                    screenId = screenId,
                    paywallId = paywallId,
                    name = name,
                    properties = properties,
                ),
            ).getOrThrow()
        }.onFailure { error ->
            ApphudLog.logE("Failed to track rule event '$name': ${error.message}")
        }
    }
}
