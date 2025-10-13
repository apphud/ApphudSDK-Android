package com.apphud.sdk.internal.preloader.data.source

import com.apphud.sdk.ApphudLog
import com.apphud.sdk.internal.preloader.domain.model.PreloadedPaywallData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * In-memory cache data source for preloaded paywalls using StateFlow
 */
internal class PaywallCacheDataSource {
    private val _cacheFlow = MutableStateFlow<Map<String, PreloadedPaywallData>>(emptyMap())

    /**
     * StateFlow with all cached paywalls
     */
    val cacheFlow: StateFlow<Map<String, PreloadedPaywallData>> = _cacheFlow.asStateFlow()

    private val mutex = Mutex()

    /**
     * Saves preloaded paywall data to cache
     */
    suspend fun save(data: PreloadedPaywallData) = mutex.withLock {
        val currentCache = _cacheFlow.value
        val previousData = currentCache[data.paywallId]

        _cacheFlow.value = currentCache + (data.paywallId to data)

        val sizeKB = data.getHtmlSizeBytes() / 1024
        if (previousData != null) {
            ApphudLog.log("[CacheDataSource] Updated cached paywall: ${data.paywallId}, size: ${sizeKB}KB")
        } else {
            ApphudLog.log("[CacheDataSource] Saved new paywall to cache: ${data.paywallId}, size: ${sizeKB}KB")
        }

        // Log total cache size
        val totalSizeKB = getTotalCacheSizeBytes() / 1024
        val cacheCount = _cacheFlow.value.size
        ApphudLog.log("[CacheDataSource] Total cache: $cacheCount paywalls, ${totalSizeKB}KB")
    }

    /**
     * Returns Flow for specific paywall ID
     */
    fun getFlow(paywallId: String): Flow<PreloadedPaywallData?> {
        return _cacheFlow.map { cache ->
            cache[paywallId]?.also { data ->
                val ageSeconds = (System.currentTimeMillis() - data.preloadedAt) / 1000
                ApphudLog.log("[CacheDataSource] Flow emitted paywall: $paywallId, age: ${ageSeconds}s")
            }
        }
    }

    /**
     * Retrieves preloaded paywall data from cache (for compatibility)
     */
    suspend fun get(paywallId: String): PreloadedPaywallData? {
        val data = _cacheFlow.value[paywallId]
        if (data != null) {
            val ageSeconds = (System.currentTimeMillis() - data.preloadedAt) / 1000
            ApphudLog.log("[CacheDataSource] Retrieved paywall from cache: $paywallId, age: ${ageSeconds}s")
        }
        return data
    }

    /**
     * Removes specific paywall from cache
     */
    suspend fun remove(paywallId: String) = mutex.withLock {
        val currentCache = _cacheFlow.value
        val removed = currentCache[paywallId]

        if (removed != null) {
            _cacheFlow.value = currentCache - paywallId
            val sizeKB = removed.getHtmlSizeBytes() / 1024
            ApphudLog.log("[CacheDataSource] Removed paywall from cache: $paywallId, freed: ${sizeKB}KB")
        }
    }

    /**
     * Clears all cached paywalls
     */
    suspend fun clear() = mutex.withLock {
        val previousSize = _cacheFlow.value.size
        val previousSizeKB = getTotalCacheSizeBytes() / 1024
        _cacheFlow.value = emptyMap()
        ApphudLog.log("[CacheDataSource] Cleared cache: removed $previousSize paywalls, freed ${previousSizeKB}KB")
    }

    /**
     * Gets total size of all cached data in bytes
     */
    fun getTotalCacheSizeBytes(): Long {
        return _cacheFlow.value.values.sumOf { it.getHtmlSizeBytes() }
    }

    /**
     * Gets number of cached paywalls
     */
    fun getCacheCount(): Int = _cacheFlow.value.size

    /**
     * Gets all cached paywall IDs
     */
    fun getCachedPaywallIds(): Set<String> = _cacheFlow.value.keys

    /**
     * Removes expired cache entries
     * @param maxAgeMillis Maximum age in milliseconds
     * @return Number of removed entries
     */
    suspend fun removeExpired(maxAgeMillis: Long): Int = mutex.withLock {
        val currentTime = System.currentTimeMillis()
        val currentCache = _cacheFlow.value

        val toRemove = currentCache.filter { (_, data) ->
            (currentTime - data.preloadedAt) > maxAgeMillis
        }

        if (toRemove.isNotEmpty()) {
            _cacheFlow.value = currentCache - toRemove.keys

            toRemove.forEach { (paywallId, data) ->
                val sizeKB = data.getHtmlSizeBytes() / 1024
                ApphudLog.log("[CacheDataSource] Removed expired paywall: $paywallId, freed: ${sizeKB}KB")
            }

            ApphudLog.log("[CacheDataSource] Removed ${toRemove.size} expired paywalls")
        }

        toRemove.size
    }

    /**
     * Gets cache statistics
     */
    fun getCacheStats(): CacheStats {
        val entries = _cacheFlow.value.values.toList()
        val currentTime = System.currentTimeMillis()

        return CacheStats(
            totalCount = entries.size,
            totalSizeBytes = entries.sumOf { it.getHtmlSizeBytes() },
            oldestEntryAgeMillis = entries.minOfOrNull { currentTime - it.preloadedAt },
            newestEntryAgeMillis = entries.maxOfOrNull { currentTime - it.preloadedAt },
            averageEntrySizeBytes = if (entries.isNotEmpty()) {
                entries.sumOf { it.getHtmlSizeBytes() } / entries.size
            } else 0
        )
    }

    /**
     * Cache statistics data class
     */
    data class CacheStats(
        val totalCount: Int,
        val totalSizeBytes: Long,
        val oldestEntryAgeMillis: Long?,
        val newestEntryAgeMillis: Long?,
        val averageEntrySizeBytes: Long
    ) {
        fun toLogString(): String {
            return "CacheStats(" +
                    "count=$totalCount, " +
                    "size=${totalSizeBytes / 1024}KB, " +
                    "avgSize=${averageEntrySizeBytes / 1024}KB, " +
                    "oldest=${oldestEntryAgeMillis?.let { it / 1000 }}s, " +
                    "newest=${newestEntryAgeMillis?.let { it / 1000 }}s)"
        }
    }
}