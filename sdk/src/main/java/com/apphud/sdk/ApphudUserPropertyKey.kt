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
    /** User email. Value must be String*/
    object Email:ApphudUserPropertyKey(ApphudUserPropertyKeyEmail)
    /** User name. Value must be String*/
    object Name:ApphudUserPropertyKey(ApphudUserPropertyKeyName)
    /** User phone number. Value must be String.*/
    object Phone:ApphudUserPropertyKey(ApphudUserPropertyKeyPhone)
    /** User install cohort. Value must be String.*/
    object Cohort:ApphudUserPropertyKey(ApphudUserPropertyKeyCohort)
    /** User age. Value must be Int.*/
    object Age:ApphudUserPropertyKey(ApphudUserPropertyKeyAge)
    /** User gender. Value must be one of: "male", "female", "other".*/
    object Gender:ApphudUserPropertyKey(ApphudUserPropertyKeyGender)
    /**
    Initialize with custom property key string.
    Example:
    Apphud.setUserProperty(key = ApphudUserPropertyKey.CustomProperty("custom_prop_1"), value = 0.5)
     */
    class CustomProperty(value: String):ApphudUserPropertyKey(value)
}