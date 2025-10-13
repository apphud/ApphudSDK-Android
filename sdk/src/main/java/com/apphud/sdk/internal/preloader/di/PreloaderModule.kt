package com.apphud.sdk.internal.preloader.di

import android.content.Context
import com.apphud.sdk.internal.preloader.data.repository.PaywallPreloadRepositoryImpl
import com.apphud.sdk.internal.preloader.data.source.HtmlResourceParser
import com.apphud.sdk.internal.preloader.data.source.PaywallCacheDataSource
import com.apphud.sdk.internal.preloader.data.source.PaywallResourceLoader
import com.apphud.sdk.internal.preloader.data.source.ResourcePreloader
import com.apphud.sdk.internal.preloader.data.source.WebViewCookieJar
import com.apphud.sdk.internal.preloader.domain.repository.PaywallPreloadRepository
import com.apphud.sdk.internal.preloader.domain.usecase.GetPreloadedPaywallUseCase
import com.apphud.sdk.internal.preloader.domain.usecase.PrewarmPaywallUseCase
import com.apphud.sdk.internal.preloader.presentation.WebViewPreloadHelper
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Dependency injection module for the preloader package
 */
internal class PreloaderModule(
    private val context: Context
) {
    // Cookie synchronization between WebView and OkHttp
    private val cookieJar: WebViewCookieJar by lazy {
        WebViewCookieJar()
    }

    // Logging interceptor for debugging
    private val loggingInterceptor: HttpLoggingInterceptor by lazy {
        HttpLoggingInterceptor { message ->
            com.apphud.sdk.ApphudLog.log("[OkHttp] $message")
        }.apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
    }

    // HTTP Client with caching and cookie synchronization
    val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .cache(
                Cache(
                    directory = File(context.cacheDir, "apphud_okhttp_cache"),
                    maxSize = 100 * 1024 * 1024 // 100MB disk cache
                )
            )
            .cookieJar(cookieJar)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    // Utilities
    val htmlResourceParser: HtmlResourceParser by lazy {
        HtmlResourceParser()
    }

    private val resourcePreloader: ResourcePreloader by lazy {
        ResourcePreloader(httpClient)
    }

    // Data Sources
    private val resourceLoader: PaywallResourceLoader by lazy {
        PaywallResourceLoader(httpClient)
    }

    private val cacheDataSource: PaywallCacheDataSource by lazy {
        PaywallCacheDataSource()
    }

    // Repository
    val repository: PaywallPreloadRepository by lazy {
        PaywallPreloadRepositoryImpl(
            resourceLoader = resourceLoader,
            cacheDataSource = cacheDataSource,
            htmlResourceParser = htmlResourceParser,
            resourcePreloader = resourcePreloader
        )
    }

    // Use Cases
    val prewarmPaywallUseCase: PrewarmPaywallUseCase by lazy {
        PrewarmPaywallUseCase(repository)
    }

    val getPreloadedPaywallUseCase: GetPreloadedPaywallUseCase by lazy {
        GetPreloadedPaywallUseCase(repository)
    }

    // Presentation
    val webViewPreloadHelper: WebViewPreloadHelper by lazy {
        WebViewPreloadHelper(httpClient)
    }

    /**
     * Clears all caches (both memory and disk)
     */
    suspend fun clearAllCaches() {
        // Clear memory cache
        repository.clearAllCache()

        // Clear OkHttp disk cache
        httpClient.cache?.evictAll()
    }

    /**
     * Gets total cache size (memory + disk)
     */
    suspend fun getTotalCacheSizeBytes(): Long {
        val memoryCacheSize = repository.getCacheSizeBytes()
        val diskCacheSize = httpClient.cache?.size() ?: 0L
        return memoryCacheSize + diskCacheSize
    }
}