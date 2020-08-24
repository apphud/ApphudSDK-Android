package ru.rosbank.mbdg.myapplication.storage

import ru.rosbank.mbdg.myapplication.domain.Customer

interface Storage {
    var userId: String?
    var deviceId: String?
    var customer: Customer
}