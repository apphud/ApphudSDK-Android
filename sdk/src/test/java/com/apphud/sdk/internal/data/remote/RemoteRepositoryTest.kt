package com.apphud.sdk.internal.data.remote

import com.apphud.sdk.internal.ApphudDispatchers
import com.apphud.sdk.internal.data.IdentityStore
import com.apphud.sdk.internal.domain.model.ApphudIdentity
import com.apphud.sdk.body.RegistrationBody
import com.apphud.sdk.domain.ApphudUser
import com.apphud.sdk.internal.data.ReinstallRepository
import com.apphud.sdk.internal.data.dto.CustomerDto
import com.apphud.sdk.internal.data.dto.DataDto
import com.apphud.sdk.internal.data.dto.ResponseDto
import com.apphud.sdk.internal.data.mapper.CustomerMapper
import com.apphud.sdk.internal.data.network.UrlProvider
import com.google.gson.Gson
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import okhttp3.Call
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for RemoteRepository.getCustomers method
 *
 * These tests document the current behavior of error handling,
 * including the known bug with 401 errors not being properly caught.
 */
class RemoteRepositoryTest {

    private val gson = Gson()
    private val mockCustomerDto: CustomerDto = mockk(relaxed = true)
    private val mockApphudUser: ApphudUser = mockk(relaxed = true)
    private val mockRegistrationBody: RegistrationBody = mockk(relaxed = true)

    private val customerMapper: CustomerMapper = mockk {
        every { map(mockCustomerDto) } returns mockApphudUser
    }

    private val registrationBodyFactory: RegistrationBodyFactory = mockk {
        every { create(any(), any(), any(), any()) } returns mockRegistrationBody
    }

    private val urlProvider: UrlProvider = mockk {
        every { customersUrl } returns "https://api.apphud.com/v4/customers".toHttpUrl()
        every { subscriptionsUrl } returns "https://api.apphud.com/v4/subscriptions".toHttpUrl()
        every { updateConnectDomainUrl(any()) } just Runs
    }

    private val okHttpClient: OkHttpClient = mockk()

    private val purchaseBodyFactory: PurchaseBodyFactory = mockk {
        every { create(any(), any(), any(), any()) } returns mockk(relaxed = true)
    }

    private val reinstallRepository: ReinstallRepository = mockk {
        coEvery { awaitBeforeRegistration() } returns null
        every { reinstallFlag() } returns null
        every { onRegistered(any(), any()) } just Runs
    }

    private val identityStore: IdentityStore = mockk(relaxUnitFun = true) {
        every { current } returns IDENTITY
    }

    private val hold = com.apphud.sdk.internal.data.network.FirstRegistrationHold()

    private val remoteRepository = RemoteRepository(
        okHttpClient = okHttpClient,
        gson = gson,
        customerMapper = customerMapper,
        purchaseBodyFactory = purchaseBodyFactory,
        registrationBodyFactory = registrationBodyFactory,
        productMapper = mockk(),
        attributionMapper = mockk(),
        notificationMapper = mockk(),
        paywallsMapper = mockk(),
        urlProvider = urlProvider,
        dispatchers = ApphudDispatchers(),
        reinstallRepository = reinstallRepository,
        identityStore = identityStore,
        firstRegistrationHold = hold,
    )

    private fun createSuccessResponse(): String {
        val response = ResponseDto(
            data = DataDto(results = mockCustomerDto, meta = null),
            errors = null
        )
        return gson.toJson(response)
    }

    private fun create401ErrorResponse(): String {
        return """{"data":{"results":null},"errors":[{"id":"authentication","title":"Invalid Credentials."}]}"""
    }

    private fun createMockResponse(
        code: Int,
        body: String,
        request: Request = Request.Builder().url("https://api.apphud.com/v4/customers").build()
    ): Response {
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("Response")
            .body(body.toResponseBody())
            .build()
    }

    private fun mockHttpCall(response: Response) {
        val call: Call = mockk {
            every { execute() } returns response
        }
        every { okHttpClient.newCall(any()) } returns call
    }

    private fun mockHttpCallWithException(exception: Exception) {
        val call: Call = mockk {
            every { execute() } throws exception
        }
        every { okHttpClient.newCall(any()) } returns call
    }

    @Test
    fun `should return success when response is valid`() = runTest {
        mockHttpCall(createMockResponse(200, createSuccessResponse()))

        val result = remoteRepository.getCustomers(
            needPlacements = true,
            isNew = false,
            email = null
        )

        assertTrue("Result should be success", result.isSuccess)
        assertEquals("Returned user should match mocked user", mockApphudUser, result.getOrNull())
    }

    // region identity sync

    @Test
    fun `GIVEN requests held for the first registration WHEN it succeeds EXPECT them released`() = runTest {
        mockHttpCall(createMockResponse(200, createSuccessResponse()))
        hold.close()

        remoteRepository.getCustomers(needPlacements = true, isNew = false, email = null)

        assertFalse(hold.isClosed)
    }

    @Test
    fun `GIVEN requests held for the first registration WHEN it fails EXPECT them released too`() = runTest {
        mockHttpCallWithException(java.io.IOException("offline"))
        hold.close()

        remoteRepository.getCustomers(needPlacements = true, isNew = false, email = null)

        assertFalse(hold.isClosed)
    }

    @Test
    fun `GIVEN the restore the user asked for EXPECT the purchases sent as a restore`() = runTest {
        mockHttpCall(createMockResponse(200, createSuccessResponse()))

        remoteRepository.restorePurchased(null, emptyList(), observerMode = false, restore = true)

        verify { purchaseBodyFactory.create(null, emptyList(), false, true) }
    }

    @Test
    fun `GIVEN registration EXPECT the body built after the reinstall wait`() = runTest {
        mockHttpCall(createMockResponse(200, createSuccessResponse()))

        remoteRepository.getCustomers(needPlacements = true, isNew = false, email = null)

        coVerifyOrder {
            reinstallRepository.awaitBeforeRegistration()
            registrationBodyFactory.create(any(), any(), any(), any())
        }
    }

    private fun bodyWithIds(installationId: String, anonymousId: String, userId: String?) {
        every { mockRegistrationBody.installationId } returns installationId
        every { mockRegistrationBody.anonymousId } returns anonymousId
        every { mockRegistrationBody.userId } returns userId
        every { mockRegistrationBody.reinstall } returns null
        every { mockRegistrationBody.legacyDeviceId } returns null
    }

    @Test
    fun `GIVEN registration sent with the current ids WHEN it succeeds EXPECT ids marked synced`() = runTest {
        bodyWithIds(IDENTITY.installationId, IDENTITY.anonymousId, IDENTITY.userId)
        mockHttpCall(createMockResponse(200, createSuccessResponse()))

        remoteRepository.getCustomers(needPlacements = true, isNew = false, email = null)

        verify { identityStore.markSynced() }
    }

    @Test
    fun `GIVEN ids changed while the registration was out WHEN it succeeds EXPECT not marked synced`() = runTest {
        bodyWithIds(IDENTITY.installationId, "previous-anonymous-id", IDENTITY.userId)
        mockHttpCall(createMockResponse(200, createSuccessResponse()))

        remoteRepository.getCustomers(needPlacements = true, isNew = false, email = null)

        verify(exactly = 0) { identityStore.markSynced() }
    }

    @Test
    fun `GIVEN reinstall decided after the body was built WHEN it succeeds EXPECT not marked synced`() = runTest {
        bodyWithIds(IDENTITY.installationId, IDENTITY.anonymousId, IDENTITY.userId)
        every { reinstallRepository.reinstallFlag() } returns true
        mockHttpCall(createMockResponse(200, createSuccessResponse()))

        remoteRepository.getCustomers(needPlacements = true, isNew = false, email = null)

        verify(exactly = 0) { identityStore.markSynced() }
    }

    @Test
    fun `GIVEN registration with the reinstall flag WHEN it succeeds EXPECT the reinstall reported`() = runTest {
        bodyWithIds(IDENTITY.installationId, IDENTITY.anonymousId, IDENTITY.userId)
        every { mockRegistrationBody.reinstall } returns true
        mockHttpCall(createMockResponse(200, createSuccessResponse()))

        remoteRepository.getCustomers(needPlacements = true, isNew = false, email = null)

        verify { reinstallRepository.onRegistered(reinstallSent = true, legacyDeviceIdSent = false) }
    }

    @Test
    fun `GIVEN registration with the reinstall flag WHEN it fails EXPECT nothing reported`() = runTest {
        bodyWithIds(IDENTITY.installationId, IDENTITY.anonymousId, IDENTITY.userId)
        every { mockRegistrationBody.reinstall } returns true
        mockHttpCall(createMockResponse(500, "Internal Server Error"))

        remoteRepository.getCustomers(needPlacements = true, isNew = false, email = null)

        verify(exactly = 0) { reinstallRepository.onRegistered(any(), any()) }
    }

    @Test
    fun `GIVEN registration without the reinstall flag WHEN it succeeds EXPECT no reinstall reported`() = runTest {
        bodyWithIds(IDENTITY.installationId, IDENTITY.anonymousId, IDENTITY.userId)
        mockHttpCall(createMockResponse(200, createSuccessResponse()))

        remoteRepository.getCustomers(needPlacements = true, isNew = false, email = null)

        verify { reinstallRepository.onRegistered(reinstallSent = false, legacyDeviceIdSent = false) }
    }

    @Test
    fun `GIVEN registration that reported the reinstall WHEN it succeeds EXPECT ids marked synced`() = runTest {
        bodyWithIds(IDENTITY.installationId, IDENTITY.anonymousId, IDENTITY.userId)
        every { mockRegistrationBody.reinstall } returns true
        mockHttpCall(createMockResponse(200, createSuccessResponse()))

        remoteRepository.getCustomers(needPlacements = true, isNew = false, email = null)

        verify { identityStore.markSynced() }
    }

    @Test
    fun `GIVEN registration with the old device id WHEN it succeeds EXPECT the old customer reported adopted`() = runTest {
        bodyWithIds(IDENTITY.installationId, IDENTITY.anonymousId, IDENTITY.userId)
        every { mockRegistrationBody.legacyDeviceId } returns "OLD"
        mockHttpCall(createMockResponse(200, createSuccessResponse()))

        remoteRepository.getCustomers(needPlacements = true, isNew = false, email = null)

        verify { reinstallRepository.onRegistered(reinstallSent = false, legacyDeviceIdSent = true) }
    }

    // endregion

    @Test
    fun `should return failure when server returns 401 unauthorized - CURRENT BUG`() = runTest {
        mockHttpCall(createMockResponse(401, create401ErrorResponse()))

        val result = remoteRepository.getCustomers(
            needPlacements = true,
            isNew = false,
            email = null
        )

        // BUG: The error is NOT being properly caught and wrapped in Result.failure
        // Instead, it throws an IllegalStateException which gets caught by
        // recoverCatchingCancellable and re-thrown as ApphudError
        assertTrue("Result should be failure", result.isFailure)

        result.exceptionOrNull()?.let { exception ->
            assertTrue(
                "Exception message should contain 401 error details",
                exception.message?.contains("401") == true
            )
        }
    }

    @Test
    fun `should return failure when network timeout occurs`() = runTest {
        mockHttpCallWithException(java.net.SocketTimeoutException("timeout"))

        val result = remoteRepository.getCustomers(
            needPlacements = true,
            isNew = false,
            email = null
        )

        assertTrue("Result should be failure", result.isFailure)
        result.exceptionOrNull()?.let { exception ->
            assertTrue(
                "Exception should be related to timeout",
                exception.cause is java.net.SocketTimeoutException ||
                exception is java.net.SocketTimeoutException ||
                exception.message?.contains("timeout", ignoreCase = true) == true
            )
        }
    }

    @Test
    fun `should return failure when response contains invalid JSON`() = runTest {
        val invalidJson = "{ this is not valid json }"
        mockHttpCall(createMockResponse(200, invalidJson))

        val result = remoteRepository.getCustomers(
            needPlacements = true,
            isNew = false,
            email = null
        )

        assertTrue("Result should be failure due to JSON parsing error", result.isFailure)
    }

    @Test
    fun `should return failure when response contains null results`() = runTest {
        val responseWithNullResults = """{"data":{"results":null},"errors":null}"""
        mockHttpCall(createMockResponse(200, responseWithNullResults))

        val result = remoteRepository.getCustomers(
            needPlacements = true,
            isNew = false,
            email = null
        )

        assertTrue("Result should be failure when results is null", result.isFailure)
        result.exceptionOrNull()?.let { exception ->
            assertTrue(
                "Exception message should indicate registration failed",
                exception.message?.contains("Registration failed", ignoreCase = true) == true
            )
        }
    }

    @Test
    fun `should return failure when server returns 500 internal error`() = runTest {
        mockHttpCall(createMockResponse(500, "Internal Server Error"))

        val result = remoteRepository.getCustomers(
            needPlacements = true,
            isNew = false,
            email = null
        )

        assertTrue("Result should be failure", result.isFailure)
        result.exceptionOrNull()?.let { exception ->
            assertTrue(
                "Exception message should contain 500 error details",
                exception.message?.contains("500") == true
            )
        }
    }

    @Test
    fun `should return failure when connection is refused`() = runTest {
        mockHttpCallWithException(java.net.ConnectException("Connection refused"))

        val result = remoteRepository.getCustomers(
            needPlacements = true,
            isNew = false,
            email = null
        )

        assertTrue("Result should be failure", result.isFailure)
    }

    private companion object {
        val IDENTITY = ApphudIdentity("installation-id", "anonymous-id", "user-id")
    }
}
