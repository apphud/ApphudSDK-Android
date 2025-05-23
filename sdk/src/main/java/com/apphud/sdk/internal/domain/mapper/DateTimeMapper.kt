package com.apphud.sdk.internal.domain.mapper

import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Utility class for converting ISO 8601 date strings to Unix timestamps.
 */
internal class DateTimeMapper {

    private val isoDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    private val isoDateFormatWithoutMillis = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    /**
     * Converts an ISO 8601 formatted date string to Unix timestamp in milliseconds.
     *
     * @param isoDateString The date string in ISO 8601 format (e.g., "2025-05-16T10:37:59.234Z")
     * @return The Unix timestamp in milliseconds, or null if the string couldn't be parsed
     */
    fun toTimestamp(isoDateString: String?): Long? {
        if (isoDateString.isNullOrBlank()) return null

        return try {
            isoDateFormat.parse(isoDateString)?.time
        } catch (e: ParseException) {
            try {
                isoDateFormatWithoutMillis.parse(isoDateString)?.time
            } catch (e: ParseException) {
                null
            }
        }
    }
}
