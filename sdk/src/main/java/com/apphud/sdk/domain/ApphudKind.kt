package com.apphud.sdk.domain

enum class ApphudKind(val source: String) {
    NONE("none"),
    NONRENEWABLE("nonrenewable"),
    AUTORENEWABLE("autorenewable");

    companion object {
        fun map(value: String?) =
            values().find { it.source == value } ?: NONE
    }
}