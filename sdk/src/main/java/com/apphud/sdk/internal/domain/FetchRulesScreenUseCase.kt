package com.apphud.sdk.internal.domain

import com.apphud.sdk.ApphudInternal
import com.apphud.sdk.internal.data.local.LocalRulesScreenRepository
import com.apphud.sdk.internal.data.remote.RemoteRepository
import com.apphud.sdk.internal.data.remote.ScreenRemoteRepository
import com.apphud.sdk.internal.domain.mapper.DateTimeMapper
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
    private val dateTimeMapper: DateTimeMapper,
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
            val legacyRuleScreensEnabled = ApphudInternal.legacyRuleScreensEnabled

            val ruleScreenList = notifications
                .mapNotNull { notification ->
                    val createdTimeStamp = dateTimeMapper.toTimestamp(notification.createdAt)
                    val rule = notification.rule
                    if (rule != null && createdTimeStamp != null) {
                        val screenHtml = when {
                            rule.paywallIdentifier != null -> ""
                            !legacyRuleScreensEnabled -> return@mapNotNull null
                            else -> screenRemoteRepository.loadScreenHtmlData(
                                rule.screenId, deviceId
                            ).getOrThrow()
                        }

                        RuleScreen(createdTimeStamp, rule, screenHtml)
                    } else {
                        null
                    }
                }

            ruleScreenList.forEach { ruleScreen ->
                localRulesScreenRepository.save(ruleScreen)
            }

            if (legacyRuleScreensEnabled) {
                ruleScreenList.forEach { ruleScreen ->
                    remoteRepository.readAllNotifications(ruleScreen.rule.id, deviceId)
                }
            } else {
                notifications
                    .mapNotNull { it.rule?.id }
                    .distinct()
                    .forEach { ruleId ->
                        remoteRepository.readAllNotifications(ruleId, deviceId)
                    }
            }

            FetchRulesScreenResult.Success
        }
            .getOrElse { e -> FetchRulesScreenResult.Error(e) }
}
