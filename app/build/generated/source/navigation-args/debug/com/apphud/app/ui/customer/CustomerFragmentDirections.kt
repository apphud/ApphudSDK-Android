package com.apphud.app.ui.customer

import android.os.Bundle
import androidx.navigation.NavDirections
import com.apphud.app.R
import kotlin.Int
import kotlin.String

public class CustomerFragmentDirections private constructor() {
  private data class ActionNavCustomerToProductsFragment(
    public val paywallId: String = ""
  ) : NavDirections {
    public override fun getActionId(): Int = R.id.action_nav_customer_to_productsFragment

    public override fun getArguments(): Bundle {
      val result = Bundle()
      result.putString("paywallId", this.paywallId)
      return result
    }
  }

  public companion object {
    public fun actionNavCustomerToProductsFragment(paywallId: String = ""): NavDirections =
        ActionNavCustomerToProductsFragment(paywallId)
  }
}
