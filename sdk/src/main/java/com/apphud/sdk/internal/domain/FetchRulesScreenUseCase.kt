package com.apphud.sdk.internal.domain

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

            val ruleScreenList = notifications
                .mapNotNull { notification ->
                    val createdTimeStamp = dateTimeMapper.toTimestamp(notification.createdAt)
                    if (notification.rule != null && createdTimeStamp != null) {
                        val screenHtml = screenRemoteRepository.loadScreenHtmlData(
                            notification.rule.screenId, deviceId
                        ).getOrThrow()

                        RuleScreen(createdTimeStamp, notification.rule, screenHtml)
                    } else {
                        null
                    }
                }

            ruleScreenList.forEach { ruleScreen ->
                localRulesScreenRepository.save(ruleScreen)
            }

            ruleScreenList.forEach { ruleScreen ->
                remoteRepository.readAllNotifications(ruleScreen.rule.id, deviceId)
            }

            FetchRulesScreenResult.Success
        }
            .getOrElse { e -> FetchRulesScreenResult.Error(e) }
}