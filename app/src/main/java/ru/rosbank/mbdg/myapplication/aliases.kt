package ru.rosbank.mbdg.myapplication

import ru.rosbank.mbdg.myapplication.domain.Attribution
import ru.rosbank.mbdg.myapplication.domain.Customer
import ru.rosbank.mbdg.myapplication.domain.Product

typealias ApiKey = String
typealias UserId = String
typealias DeviceId = String
typealias ProductId = String

typealias Callback<T> = (T) -> Unit
typealias CustomerCallback = Callback<Customer>
typealias ProductsCallback = Callback<List<Product>>
typealias AttributionCallback = Callback<Attribution>