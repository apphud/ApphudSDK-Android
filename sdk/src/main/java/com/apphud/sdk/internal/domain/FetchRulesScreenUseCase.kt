package com.apphud.sdk.internal.domain

import com.apphud.sdk.internal.data.local.LocalRulesScreenRepository
import com.apphud.sdk.internal.data.remote.RemoteRepository
import com.apphud.sdk.internal.data.remote.ScreenRemoteRepository
import com.apphud.sdk.internal.domain.model.FetchRulesScreenResult
import com.apphud.sdk.internal.domain.model.RuleScreen
import com.apphud.sdk.internal.util.mapCatchingCancellable

internal class FetchRulesScreenUseCase(
    private val remoteRepository: RemoteRepository,
    private val screenRemoteRepository: ScreenRemoteRepository,
    private val localRulesScreenRepository: LocalRulesScreenRepository,
) {

    suspend operator fun invoke(deviceId: String): FetchRulesScreenResult {
        val fetchRulesResult = remoteRepository.getNotifications(deviceId)
            .mapCatchingCancellable { notifications ->
                notifications
                    .mapNotNull { it.rule }
                    .map { rule ->
                        rule to screenRemoteRepository.loadScreenHtmlData(rule.screenId, deviceId).getOrThrow()
                    }
            }
        return fetchRulesResult
            .mapCatchingCancellable { ruleWithScreenList ->
                ruleWithScreenList.forEach { (rule, screenHtml) ->
                    localRulesScreenRepository.save(RuleScreen(rule, screenHtml))
                }
                FetchRulesScreenResult.Success
            }
            .getOrElse {
                FetchRulesScreenResult.Error(fetchRulesResult.exceptionOrNull() ?: Exception("Unknown error"))
            }
    }
}