// Generated by view binder compiler. Do not edit!
package com.apphud.app.databinding;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.viewbinding.ViewBinding;
import com.apphud.app.R;
import java.lang.NullPointerException;
import java.lang.Override;
import java.lang.String;

public final class ListItemProductCardBinding implements ViewBinding {
  @NonNull
  private final CardView rootView;

  @NonNull
  public final ImageView image;

  @NonNull
  public final TextView productName;

  @NonNull
  public final TextView productPrice;

  private ListItemProductCardBinding(@NonNull CardView rootView, @NonNull ImageView image,
      @NonNull TextView productName, @NonNull TextView productPrice) {
    this.rootView = rootView;
    this.image = image;
    this.productName = productName;
    this.productPrice = productPrice;
  }

  @Override
  @NonNull
  public CardView getRoot() {
    return rootView;
  }

  @NonNull
  public static ListItemProductCardBinding inflate(@NonNull LayoutInflater inflater) {
    return inflate(inflater, null, false);
  }

  @NonNull
  public static ListItemProductCardBinding inflate(@NonNull LayoutInflater inflater,
      @Nullable ViewGroup parent, boolean attachToParent) {
    View root = inflater.inflate(R.layout.list_item_product_card, parent, false);
    if (attachToParent) {
      parent.addView(root);
    }
    return bind(root);
  }

  @NonNull
  public static ListItemProductCardBinding bind(@NonNull View rootView) {
    // The body of this method is generated in a way you would not otherwise write.
    // This is done to optimize the compiled bytecode for size and performance.
    int id;
    missingId: {
      id = R.id.image;
      ImageView image = rootView.findViewById(id);
      if (image == null) {
        break missingId;
      }

      id = R.id.productName;
      TextView productName = rootView.findViewById(id);
      if (productName == null) {
        break missingId;
      }

      id = R.id.productPrice;
      TextView productPrice = rootView.findViewById(id);
      if (productPrice == null) {
        break missingId;
      }

      return new ListItemProductCardBinding((CardView) rootView, image, productName, productPrice);
    }
    String missingId = rootView.getResources().getResourceName(id);
    throw new NullPointerException("Missing required view with ID: ".concat(missingId));
  }
}