package ru.rosbank.mbdg.myapplication.domain

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize

@Parcelize
data class Subscription(
    val status: String,
    val productId: String,
    val kind: Kind,
    val expiresAt: String,
    val startedAt: String,
    val cancelledAt: String?,
    val inRetryBilling: Boolean,
    val introductoryActivated: Boolean,
    val autoRenewEnabled: Boolean
) : Parcelable