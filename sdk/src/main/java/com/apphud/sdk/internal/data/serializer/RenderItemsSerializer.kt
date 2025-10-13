package com.apphud.sdk.internal.data.serializer

import com.apphud.sdk.ApphudLog
import com.apphud.sdk.domain.RenderResult
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Serializer for render items
 */
internal class RenderItemsSerializer(
    private val gson: Gson
) {

    /**
     * Serializes render items to JSON string
     * @param renderItems list of maps to serialize
     * @return JSON string or null in case of error
     */
    fun serialize(renderItems: RenderResult?): String? {
        return try {
            if (renderItems.isNullOrEmpty()) {
                ApphudLog.log("[RenderItemsSerializer] Empty or null render items, returning null")
                null
            } else {
                val json = gson.toJson(renderItems)
                ApphudLog.log("[RenderItemsSerializer] Serialized ${renderItems.size} render items")
                json
            }
        } catch (e: Exception) {
            ApphudLog.logE("[RenderItemsSerializer] Failed to serialize render items: ${e.message}")
            null
        }
    }

    /**
     * Deserializes JSON string to render items
     * @param json JSON string to deserialize
     * @return list of maps or null in case of error
     */
    fun deserialize(json: String?): RenderResult? {
        return try {
            if (json.isNullOrBlank()) {
                ApphudLog.log("[RenderItemsSerializer] Empty or null JSON, returning null")
                null
            } else {
                val listType = object : TypeToken<RenderResult>() {}.type
                val renderItems = gson.fromJson<RenderResult>(json, listType)
                ApphudLog.log("[RenderItemsSerializer] Deserialized ${renderItems?.size ?: 0} render items")
                renderItems
            }
        } catch (e: Exception) {
            ApphudLog.logE("[RenderItemsSerializer] Failed to deserialize render items: ${e.message}")
            null
        }
    }

    /**
     * Checks if JSON string is valid for deserialization
     * @param json JSON string to validate
     * @return true if JSON is valid, false otherwise
     */
    fun isValidJson(json: String?): Boolean {
        return try {
            if (json.isNullOrBlank()) return false
            val listType = object : TypeToken<RenderResult>() {}.type
            gson.fromJson<RenderResult>(json, listType)
            true
        } catch (e: Exception) {
            ApphudLog.logE("[RenderItemsSerializer] Invalid JSON: ${e.message}")
            false
        }
    }
}
