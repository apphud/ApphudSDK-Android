package com.apphud.sdk.body

import com.google.gson.Gson
import com.google.gson.TypeAdapter
import com.google.gson.TypeAdapterFactory
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter

/**
 * Leaves a null field out of the request, although the request serializer writes nulls: an id the
 * SDK doesn't have is not sent. Use as `@JsonAdapter(OmitWhenNull::class, nullSafe = false)`.
 */
internal class OmitWhenNull : TypeAdapterFactory {
    override fun <T> create(gson: Gson, type: TypeToken<T>): TypeAdapter<T> {
        val delegate = gson.getAdapter(type)
        return object : TypeAdapter<T>() {
            override fun write(out: JsonWriter, value: T?) {
                if (value != null) {
                    delegate.write(out, value)
                    return
                }
                // With nulls off, the writer drops the field's name together with the null.
                val serializeNulls = out.serializeNulls
                out.serializeNulls = false
                try {
                    out.nullValue()
                } finally {
                    out.serializeNulls = serializeNulls
                }
            }

            override fun read(reader: JsonReader): T? = delegate.read(reader)
        }
    }
}
