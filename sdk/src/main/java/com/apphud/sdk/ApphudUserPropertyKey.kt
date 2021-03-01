package com.apphud.sdk

/**
Built-in property keys.
 */
/** User email. Value must be String. */
internal const val ApphudUserPropertyKeyEmail = "\$email"

/** User name. Value must be String. */
internal const val ApphudUserPropertyKeyName = "\$name"

/** User phone number. Value must be String. */
internal const val ApphudUserPropertyKeyPhone = "\$phone"

/** User install cohort. Value must be String. */
internal const val ApphudUserPropertyKeyCohort = "\$cohort"

/** User email. Value must be Int. */
internal const val ApphudUserPropertyKeyAge = "\$age"

/** User email. Value must be one of: "male", "female", "other". */
internal const val ApphudUserPropertyKeyGender = "\$gender"

sealed class ApphudUserPropertyKey(val key: String){
    object Email:ApphudUserPropertyKey(ApphudUserPropertyKeyEmail)
    object Name:ApphudUserPropertyKey(ApphudUserPropertyKeyName)
    object Phone:ApphudUserPropertyKey(ApphudUserPropertyKeyPhone)
    object Cohort:ApphudUserPropertyKey(ApphudUserPropertyKeyCohort)
    object Age:ApphudUserPropertyKey(ApphudUserPropertyKeyAge)
    object Gender:ApphudUserPropertyKey(ApphudUserPropertyKeyGender)
    /**
    Initialize with custom property key string.
    Example:
    Apphud.setUserProperty(key = ApphudUserPropertyKey.CustomProperty("custom_prop_1"), value = 0.5)
     */
    class CustomProperty(value: String):ApphudUserPropertyKey(value)
}