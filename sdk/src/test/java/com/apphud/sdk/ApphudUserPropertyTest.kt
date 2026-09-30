package com.apphud.sdk

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class ApphudUserPropertyTest {

    @Test
    fun `GIVEN no attributes EXPECT payload unchanged`() {
        val json = property(value = "a").toJSON()!!

        assertEquals(setOf("name", "value", "set_once", "kind"), json.keys)
    }

    @Test
    fun `GIVEN attributes EXPECT they are sent alongside the property`() {
        val json = property(value = "a", attributes = mapOf("extra" to "x")).toJSON()!!

        assertEquals("x", json["extra"])
    }

    @Test
    fun `GIVEN attributes on removal EXPECT they are still sent`() {
        val json = property(value = null, attributes = mapOf("extra" to "x")).toJSON()!!

        assertEquals("x", json["extra"])
    }

    @Test
    fun `GIVEN attributes named like SDK fields EXPECT SDK fields win`() {
        val attributes = mapOf("name" to "other", "value" to "other", "set_once" to "other", "kind" to "other")
        val json = property(value = "a", attributes = attributes).toJSON()!!

        assertEquals("key", json["name"])
        assertEquals("a", json["value"])
        assertEquals(false, json["set_once"])
        assertEquals("string", json["kind"])
    }

    @Test
    fun `GIVEN property persisted without attributes EXPECT it decodes with none`() {
        val stored = """{"key":{"key":"key","value":"a","increment":false,"setOnce":false,"type":"string"}}"""
        val type = object : TypeToken<HashMap<String, ApphudUserProperty>>() {}.type

        val decoded = Gson().fromJson<HashMap<String, ApphudUserProperty>>(stored, type)["key"]!!

        assertNull(decoded.attributes)
        assertFalse(decoded.toJSON()!!.containsKey("extra"))
    }

    private fun property(value: Any?, attributes: Map<String, String>? = null) =
        ApphudUserProperty(
            key = "key",
            value = value,
            setOnce = false,
            type = if (value == null) "null" else "string",
        ).also { it.attributes = attributes }
}
