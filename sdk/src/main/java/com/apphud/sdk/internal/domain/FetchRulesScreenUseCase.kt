package com.apphud.sdk.internal.domain

import com.apphud.sdk.internal.data.local.LocalRulesScreenRepository
import com.apphud.sdk.internal.data.remote.RemoteRepository
import com.apphud.sdk.internal.data.remote.ScreenRemoteRepository
import com.apphud.sdk.internal.domain.model.FetchRulesScreenResult
import com.apphud.sdk.internal.domain.model.RuleScreen
import com.apphud.sdk.internal.util.runCatchingCancellable

/**
 * Use case responsible for fetching rule screens from remote sources and storing them locally.
 *
 * This class handles the complete process of retrieving notifications, extracting rules,
 * loading HTML data for each rule's screen, saving the rule screens locally, and marking
 * notifications as read on the remote server.
 */
internal class FetchRulesScreenUseCase(
    private val remoteRepository: RemoteRepository,
    private val screenRemoteRepository: ScreenRemoteRepository,
    private val localRulesScreenRepository: LocalRulesScreenRepository,
) {

    /**
     * Fetches rule screens for the specified device.
     *
     * @param deviceId The unique identifier of the device
     * @return A [FetchRulesScreenResult] indicating success or failure with error details
     */
    suspend operator fun invoke(deviceId: String): FetchRulesScreenResult =
        runCatchingCancellable {
            val notifications = remoteRepository.getNotifications(deviceId).getOrThrow()

            val ruleWithScreenList = notifications
                .mapNotNull { it.rule }
                .map { rule ->
                    val screenHtml = screenRemoteRepository.loadScreenHtmlData(rule.screenId, deviceId).getOrThrow()
                    rule to screenHtml
                }

            ruleWithScreenList.forEach { (rule, screenHtml) ->
                localRulesScreenRepository.save(RuleScreen(rule, screenHtml))
            }

            ruleWithScreenList.forEach { (rule, _) ->
                remoteRepository.readAllNotifications(rule.id, deviceId)
            }

            FetchRulesScreenResult.Success
        }
            .getOrElse { e -> FetchRulesScreenResult.Error(e) }
}