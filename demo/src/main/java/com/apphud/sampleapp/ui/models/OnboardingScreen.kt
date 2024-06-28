package com.apphud.sampleapp.ui.models

import com.google.gson.annotations.SerializedName

data class OnboardingScreen (
    val id: String,
    val color: String,
    @SerializedName("button_title")
    val buttonTitle: String
)