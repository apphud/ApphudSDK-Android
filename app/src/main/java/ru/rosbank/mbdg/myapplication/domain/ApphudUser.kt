package ru.rosbank.mbdg.myapplication.domain

import ru.rosbank.mbdg.myapplication.UserId

data class ApphudUser(

    /**
     * Unique user identifier. This can be updated later.
     */
    val userId: UserId,
    val currencyCode: String?,
    val currencyCountryCode: String?
)