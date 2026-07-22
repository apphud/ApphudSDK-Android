package com.apphud.sdk.internal.data.network

import com.google.gson.JsonParser
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class CustomerIdInterceptorTest {

    // region GET

    @Test
    fun `GIVEN GET and customer id present EXPECT adds customer_id query param`() {
        val interceptor = CustomerIdInterceptor { "internal-123" }
        val request = Request.Builder()
            .url("https://api.apphud.com/v2/products?device_id=dev-1")
            .get()
            .build()

        val proceeded = intercept(interceptor, request)

        assertEquals("internal-123", proceeded.url.queryParameter("customer_id"))
    }

    @Test
    fun `GIVEN GET and customer id null EXPECT does not add customer_id query param`() {
        val interceptor = CustomerIdInterceptor { null }
        val request = Request.Builder()
            .url("https://api.apphud.com/v2/products?device_id=dev-1")
            .get()
            .build()

        val proceeded = intercept(interceptor, request)

        assertNull(proceeded.url.queryParameter("customer_id"))
    }

    @Test
    fun `GIVEN GET with existing customer_id EXPECT replaces with current customer id`() {
        val interceptor = CustomerIdInterceptor { "new-id" }
        val request = Request.Builder()
            .url("https://api.apphud.com/v2/products?customer_id=old-id")
            .get()
            .build()

        val proceeded = intercept(interceptor, request)

        assertEquals("new-id", proceeded.url.queryParameter("customer_id"))
    }

    // endregion

    // region POST body

    @Test
    fun `GIVEN POST JSON body and customer id present EXPECT adds customer_id field`() {
        val interceptor = CustomerIdInterceptor { "internal-123" }
        val request = jsonPostRequest("""{"device_id":"dev-1"}""")

        val proceeded = intercept(interceptor, request)

        assertEquals("internal-123", readBodyJson(proceeded).get("customer_id").asString)
    }

    @Test
    fun `GIVEN POST JSON body and customer id null EXPECT does not add customer_id field`() {
        val interceptor = CustomerIdInterceptor { null }
        val request = jsonPostRequest("""{"device_id":"dev-1"}""")

        val proceeded = intercept(interceptor, request)

        assertFalse(readBodyJson(proceeded).has("customer_id"))
    }

    @Test
    fun `GIVEN POST JSON body with existing customer_id EXPECT leaves original value`() {
        val interceptor = CustomerIdInterceptor { "new-id" }
        val request = jsonPostRequest("""{"device_id":"dev-1","customer_id":"old-id"}""")

        val proceeded = intercept(interceptor, request)

        assertEquals("old-id", readBodyJson(proceeded).get("customer_id").asString)
    }

    @Test
    fun `GIVEN POST JSON body and customer id present EXPECT keeps existing fields`() {
        val interceptor = CustomerIdInterceptor { "internal-123" }
        val request = jsonPostRequest("""{"device_id":"dev-1"}""")

        val proceeded = intercept(interceptor, request)

        assertEquals("dev-1", readBodyJson(proceeded).get("device_id").asString)
    }

    // endregion

    // region PUT body

    @Test
    fun `GIVEN PUT JSON body and customer id present EXPECT adds customer_id field`() {
        val interceptor = CustomerIdInterceptor { "internal-123" }
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val request = Request.Builder()
            .url("https://api.apphud.com/v1/customers/push_token")
            .put("""{"device_id":"dev-1"}""".toRequestBody(mediaType))
            .build()

        val proceeded = intercept(interceptor, request)

        assertEquals("internal-123", readBodyJson(proceeded).get("customer_id").asString)
    }

    // endregion

    // region edge cases

    @Test
    fun `GIVEN blank customer id EXPECT does not add customer_id`() {
        val interceptor = CustomerIdInterceptor { "   " }
        val request = jsonPostRequest("""{"device_id":"dev-1"}""")

        val proceeded = intercept(interceptor, request)

        assertFalse(readBodyJson(proceeded).has("customer_id"))
    }

    @Test
    fun `GIVEN non-JSON content type EXPECT leaves body unchanged`() {
        val interceptor = CustomerIdInterceptor { "internal-123" }
        val mediaType = "text/plain".toMediaType()
        val originalBody = "plain-text"
        val request = Request.Builder()
            .url("https://api.apphud.com/v1/customers")
            .post(originalBody.toRequestBody(mediaType))
            .build()

        val proceeded = intercept(interceptor, request)

        assertEquals(originalBody, readBodyString(proceeded))
    }

    // endregion

    private fun jsonPostRequest(json: String): Request {
        val mediaType = "application/json; charset=utf-8".toMediaType()
        return Request.Builder()
            .url("https://api.apphud.com/v1/customers")
            .post(json.toRequestBody(mediaType))
            .build()
    }

    private fun intercept(interceptor: CustomerIdInterceptor, request: Request): Request {
        var proceededRequest: Request? = null
        val chain = object : Interceptor.Chain {
            override fun request(): Request = request

            override fun proceed(request: Request): Response {
                proceededRequest = request
                return Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("{}".toResponseBody())
                    .build()
            }

            override fun connection() = null
            override fun call() = error("Not used")
            override fun connectTimeoutMillis() = 0
            override fun readTimeoutMillis() = 0
            override fun writeTimeoutMillis() = 0
            override fun withConnectTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this
            override fun withReadTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this
            override fun withWriteTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this
        }

        interceptor.intercept(chain)
        return proceededRequest ?: error("proceed was not called")
    }

    private fun readBodyString(request: Request): String {
        val body = request.body ?: return ""
        val buffer = Buffer()
        body.writeTo(buffer)
        return buffer.readUtf8()
    }

    private fun readBodyJson(request: Request) = JsonParser.parseString(readBodyString(request)).asJsonObject
}
