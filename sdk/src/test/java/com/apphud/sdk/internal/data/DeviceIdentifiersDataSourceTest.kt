package com.apphud.sdk.internal.data

import android.content.Context
import com.apphud.sdk.internal.domain.model.DeviceIdentifiers
import com.apphud.sdk.storage.SharedPreferencesStorage
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeviceIdentifiersDataSourceTest {

    private val context: Context = mockk()
    private val storage: SharedPreferencesStorage = mockk()
    private val dataSource = DeviceIdentifiersDataSource(context, storage)

    // region loadCached

    @Test
    fun `GIVEN storage has all identifiers EXPECT returns DeviceIdentifiers with all fields`() {
        every { storage.deviceIdentifiers } returns arrayOf("adId123", "appSetId456", "androidId789")

        val result = dataSource.loadCached()

        assertEquals(DeviceIdentifiers(advertisingId = "adId123", appSetId = "appSetId456", androidId = "androidId789"), result)
    }

    @Test
    fun `GIVEN storage has empty strings EXPECT returns DeviceIdentifiers with all nulls`() {
        every { storage.deviceIdentifiers } returns arrayOf("", "", "")

        val result = dataSource.loadCached()

        assertEquals(DeviceIdentifiers(advertisingId = null, appSetId = null, androidId = null), result)
    }

    @Test
    fun `GIVEN storage has partial data EXPECT returns only non-empty fields`() {
        every { storage.deviceIdentifiers } returns arrayOf("adId123", "", "androidId789")

        val result = dataSource.loadCached()

        assertEquals("adId123", result.advertisingId)
        assertNull(result.appSetId)
        assertEquals("androidId789", result.androidId)
    }

    // endregion

    // region save

    @Test
    fun `GIVEN identifiers with all fields EXPECT writes all values to storage`() {
        val slot = slot<Array<String>>()
        every { storage.deviceIdentifiers = capture(slot) } returns Unit

        dataSource.save(DeviceIdentifiers(advertisingId = "adId123", appSetId = "appSetId456", androidId = "androidId789"))

        assertArrayEquals(arrayOf("adId123", "appSetId456", "androidId789"), slot.captured)
    }

    @Test
    fun `GIVEN identifiers with all nulls EXPECT writes empty strings to storage`() {
        val slot = slot<Array<String>>()
        every { storage.deviceIdentifiers = capture(slot) } returns Unit

        dataSource.save(DeviceIdentifiers(advertisingId = null, appSetId = null, androidId = null))

        assertArrayEquals(arrayOf("", "", ""), slot.captured)
    }

    @Test
    fun `GIVEN identifiers with partial data EXPECT writes empty string for null fields`() {
        val slot = slot<Array<String>>()
        every { storage.deviceIdentifiers = capture(slot) } returns Unit

        dataSource.save(DeviceIdentifiers(advertisingId = "adId123", appSetId = null, androidId = "androidId789"))

        assertArrayEquals(arrayOf("adId123", "", "androidId789"), slot.captured)
    }

    // endregion
}
