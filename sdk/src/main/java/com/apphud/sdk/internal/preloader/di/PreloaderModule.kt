package com.apphud.sdk.internal.preloader.di

import android.content.Context
import com.apphud.sdk.ApphudLog
import com.apphud.sdk.internal.preloader.data.repository.PaywallPreloadRepository
import com.apphud.sdk.internal.preloader.data.source.HtmlResourceParser
import com.apphud.sdk.internal.preloader.data.source.PaywallResourceLoader
import com.apphud.sdk.internal.preloader.data.source.ResourcePreloader
import com.apphud.sdk.internal.preloader.data.source.WebViewCookieJar
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
            ApphudLog.log("[OkHttp] $message")
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
                    maxSize = 100 * 1024 * 1024
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

    // Repository
    val repository: PaywallPreloadRepository by lazy {
        PaywallPreloadRepository(
            resourceLoader = resourceLoader,
            htmlResourceParser = htmlResourceParser,
            resourcePreloader = resourcePreloader
        )
    }

    // Use Cases
    val prewarmPaywallUseCase: PrewarmPaywallUseCase by lazy {
        PrewarmPaywallUseCase(repository)
    }

    // Presentation
    val webViewPreloadHelper: WebViewPreloadHelper by lazy {
        WebViewPreloadHelper(httpClient)
    }
}