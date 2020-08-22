package ru.rosbank.mbdg.myapplication.domain

data class Customer(
    val user: ApphudUser,
    val subscriptions: List<ApphudSubscription>
)