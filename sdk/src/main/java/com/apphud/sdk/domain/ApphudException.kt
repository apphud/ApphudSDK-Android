package com.apphud.sdk.domain

class ApphudException (override val message: String, val errorCode: Int? = null) :Exception()