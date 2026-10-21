package com.apphud.sdk.internal

import android.app.Activity
import android.content.Context
import android.text.TextUtils
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetails
import com.apphud.sdk.ApphudInternal
import com.apphud.sdk.ApphudUtils
import com.apphud.sdk.domain.ApphudSubscription
import com.apphud.sdk.domain.ApphudUser
import com.apphud.sdk.internal.data.UserRepository
import com.apphud.sdk.internal.domain.model.ApphudIdentity
import com.apphud.sdk.resolveObfuscatedAccountId
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class BillingWrapperAccountIdTest(
    private val label: String,
    private val version1: Boolean,
    private val replacement: Boolean,
    private val suppliedId: String?,
    private val expectedId: String?,
) {
    private val context: Context = mockk()
    private val activity: Activity = mockk()
    private val builder: BillingClient.Builder = mockk()
    private val client: BillingClient = mockk()
    private val capturedParams = slot<BillingFlowParams>()
    private val product: ProductDetails = mockk {
        every { productId } returns "weekly"
        every { productType } returns BillingClient.ProductType.SUBS
        every { oneTimePurchaseOfferDetails } returns null
        every { zza() } returns ""
    }
    private val replacedSubscription: ApphudSubscription = mockk {
        every { purchaseToken } returns "old-token"
        every { obfuscatedDeviceId } returns suppliedId
    }
    private val user: ApphudUser = mockk {
        every { subscriptions } returns listOf(replacedSubscription)
    }
    private val repository: UserRepository = mockk {
        every { getDeviceId() } returns suppliedId
        every { getCurrentUser() } returns user
        // A replacement must carry the previous subscription's key, even after identity changes.
        every { getIdentity() } returns (if (replacement) {
            ApphudIdentity("current-installation", "anonymous", "bob")
        } else {
            suppliedId?.let { ApphudIdentity(it, "anonymous", "bob") }
        })
    }

    init {
        mockkObject(ApphudInternal, ApphudUtils)
        mockkStatic(BillingClient::class, TextUtils::class)
        every { ApphudInternal.isIdentityV1 } returns version1
        every { ApphudInternal.userRepository } returns repository
        every { ApphudUtils.isEmulator() } returns true
        every { ApphudUtils.logging } returns false
        every { TextUtils.isEmpty(any()) } answers { firstArg<CharSequence?>().isNullOrEmpty() }
        every { TextUtils.equals(any(), any()) } answers {
            firstArg<CharSequence?>()?.toString() == secondArg<CharSequence?>()?.toString()
        }
        every { BillingClient.newBuilder(context) } returns builder
        every { builder.enablePendingPurchases(any()) } returns builder
        every { builder.setListener(any()) } returns builder
        every { builder.build() } returns client
        every { client.isReady } returns true
        every { client.launchBillingFlow(activity, capture(capturedParams)) } returns
            BillingResult.newBuilder().setResponseCode(BillingClient.BillingResponseCode.OK).build()
    }

    private val wrapper = BillingWrapper(context)

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `GIVEN purchase identity EXPECT the account id in final Google Billing parameters`() = runTest {
        val oldToken = "old-token".takeIf { replacement }
        val error = wrapper.purchase(
            activity = activity,
            details = product,
            offerToken = "offer-token",
            oldToken = oldToken,
            replacementMode = BillingFlowParams.SubscriptionUpdateParams.ReplacementMode.CHARGE_FULL_PRICE,
            obfuscatedAccountId = ApphudInternal.resolveObfuscatedAccountId(oldToken),
        )
        check(error == null) { "Billing flow did not launch: $error" }

        // BillingFlowParams has no public account-id getter. Inspect the real final object without
        // depending on Google's obfuscated field names; profile id is never set by this flow.
        val accountIds = BillingFlowParams::class.java.declaredFields
            .filter { it.type == String::class.java }
            .mapNotNull { it.isAccessible = true; it.get(capturedParams.captured) as String? }

        assertEquals(listOfNotNull(expectedId), accountIds)
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}, V1={1}, replacement={2}")
        fun cases(): List<Array<Any?>> {
            val uuid = "12345678-ABCD-4321-abcd-1234567890AB"
            val values = listOf(
                Triple("UUID", uuid, uuid),
                Triple("opaque", "installation-1", "installation-1"),
                Triple("64 characters", "i".repeat(64), "i".repeat(64)),
                Triple("unchanged casing and spaces", " Install-Aa_01 ", " Install-Aa_01 "),
                Triple("null", null, null),
                Triple("empty", "", null),
                Triple("blank", " \t\n ", null),
                Triple("65 characters", "i".repeat(65), null),
                Triple("256 characters", "i".repeat(256), null),
            )
            return listOf(false, true).flatMap { version1 ->
                listOf(false, true).flatMap { replacement ->
                    values.map { (label, input, v2Expected) ->
                        val expected = if (version1) input?.takeIf { it == uuid } else v2Expected
                        arrayOf(label, version1, replacement, input, expected)
                    }
                }
            }
        }
    }
}
