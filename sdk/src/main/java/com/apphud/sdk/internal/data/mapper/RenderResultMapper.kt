package com.apphud.sdk.internal.data.mapper

import com.apphud.sdk.ApphudLog
import com.apphud.sdk.domain.RenderResult
import com.apphud.sdk.internal.data.serializer.RenderItemsSerializer

/**
 * Mapper for converting render response DTO to domain model and handling JSON serialization.
 */
internal class RenderResultMapper(
    private val serializer: RenderItemsSerializer? = null,
) {

    /**
     * Maps render API response DTO to domain model.
     * This method works with executeForResponse output.
     */
    fun toDomain(resultsDto: List<Map<String, Any>>): RenderResult =
        resultsDto

    /**
     * Serializes render items to JSON string
     * @param renderItems list of maps to serialize
     * @return JSON string or null in case of error
     */
    fun toJson(renderItems: RenderResult?): String? {
        ApphudLog.log("[RenderResultMapper] Starting serialization to JSON")

        if (renderItems.isNullOrEmpty()) {
            ApphudLog.log("[RenderResultMapper] Render items list is null or empty, returning null")
            return null
        }

        return try {
            val result = serializer?.serialize(renderItems)
            ApphudLog.log("[RenderResultMapper] Successfully serialized ${renderItems.size} render items")
            result
        } catch (e: Exception) {
            ApphudLog.logE("[RenderResultMapper] Failed to serialize render items: ${e.message}")
            null
        }
    }

    /**
     * Deserializes JSON string to render items
     * @param json JSON string to deserialize
     * @return list of maps or null in case of error
     */
    fun fromJson(json: String?): RenderResult? {
        ApphudLog.log("[RenderResultMapper] Starting deserialization from JSON")

        if (json.isNullOrBlank()) {
            ApphudLog.log("[RenderResultMapper] JSON is null or blank, returning null")
            return null
        }

        return try {
            val result = serializer?.deserialize(json)
            ApphudLog.log("[RenderResultMapper] Successfully deserialized ${result?.size ?: 0} render items")
            result
        } catch (e: Exception) {
            ApphudLog.logE("[RenderResultMapper] Failed to deserialize render items: ${e.message}")
            null
        }
    }
}
