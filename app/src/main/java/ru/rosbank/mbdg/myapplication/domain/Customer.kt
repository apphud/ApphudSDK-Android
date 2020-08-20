package ru.rosbank.mbdg.myapplication.domain

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize

@Parcelize
data class Customer(
    val user: User,
    val subscriptions: List<Subscription>
) : Parcelable