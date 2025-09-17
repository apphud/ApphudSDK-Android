package com.apphud.sdk.internal.data.serializer

import com.apphud.sdk.ApphudLog
import com.apphud.sdk.domain.RenderResult
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Сериализатор для рендер элементов
 */
internal class RenderItemsSerializer(
    private val gson: Gson
) {

    /**
     * Сериализует рендер элементы в JSON строку
     * @param renderItems список мап для сериализации
     * @return JSON строка или null в случае ошибки
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
     * Десериализует JSON строку в рендер элементы
     * @param json JSON строка для десериализации
     * @return список мап или null в случае ошибки
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
     * Проверяет, является ли JSON строка валидной для десериализации
     * @param json JSON строка для проверки
     * @return true если JSON валидный, false иначе
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
