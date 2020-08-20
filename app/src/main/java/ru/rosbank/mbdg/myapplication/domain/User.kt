package ru.rosbank.mbdg.myapplication.domain

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize

@Parcelize
data class User(
    val userId: String,
    val currencyCode: String?,
    val currencyCountryCode: String?
) : Parcelable