package ru.rosbank.mbdg.myapplication

import java.net.HttpURLConnection

internal val HttpURLConnection.isSuccess
    get() = responseCode in 200..299