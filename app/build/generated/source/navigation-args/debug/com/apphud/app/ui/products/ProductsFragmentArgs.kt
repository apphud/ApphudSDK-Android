package com.apphud.app.ui.products

import android.os.Bundle
import androidx.navigation.NavArgs
import java.lang.IllegalArgumentException
import kotlin.String
import kotlin.jvm.JvmStatic

public data class ProductsFragmentArgs(
  public val paywallId: String = ""
) : NavArgs {
  public fun toBundle(): Bundle {
    val result = Bundle()
    result.putString("paywallId", this.paywallId)
    return result
  }

  public companion object {
    @JvmStatic
    public fun fromBundle(bundle: Bundle): ProductsFragmentArgs {
      bundle.setClassLoader(ProductsFragmentArgs::class.java.classLoader)
      val __paywallId : String?
      if (bundle.containsKey("paywallId")) {
        __paywallId = bundle.getString("paywallId")
        if (__paywallId == null) {
          throw IllegalArgumentException("Argument \"paywallId\" is marked as non-null but was passed a null value.")
        }
      } else {
        __paywallId = ""
      }
      return ProductsFragmentArgs(__paywallId)
    }
  }
}
