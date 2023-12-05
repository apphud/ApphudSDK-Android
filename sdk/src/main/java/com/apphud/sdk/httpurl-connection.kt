package com.apphud.sdk

import java.net.HttpURLConnection

internal val HttpURLConnection.isSuccess
    get() = responseCode in 200..299
