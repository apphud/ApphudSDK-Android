package com.apphud.sdk.internal.domain.model

internal sealed class FetchRulesScreenResult {
    object Success : FetchRulesScreenResult()

    data class Error(val exception: Throwable) : FetchRulesScreenResult()
}
