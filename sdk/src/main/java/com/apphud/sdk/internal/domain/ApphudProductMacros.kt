package com.apphud.sdk.internal.domain

import com.apphud.sdk.domain.ApphudProduct
import java.util.Locale

internal fun ApphudProduct.jsonProperties(
    langCode: String = Locale.getDefault().language,
    fallback: Boolean = true,
): Map<String, Any>? {
    val props = properties ?: return null
    @Suppress("UNCHECKED_CAST")
    val innerProps = when {
        props[langCode] != null -> props[langCode]
        fallback -> props.values.firstOrNull()
        else -> null
    } as? Map<String, Any> ?: return null
    return innerProps
}

internal fun ApphudProduct.hasMacros(): Boolean {
    val jsonProps = jsonProperties() ?: return false
    return jsonProps.values.any { value ->
        value is String && value.contains("{")
    }
}

internal fun ApphudProduct.hasLiquidMacros(): Boolean {
    val jsonProps = jsonProperties() ?: return false
    return jsonProps.values.any { value ->
        value is String && value.containsLiquidTag()
    }
}

private fun String.containsLiquidTag(): Boolean =
    contains("{{") || contains("{%")
