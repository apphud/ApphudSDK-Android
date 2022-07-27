// Generated by view binder compiler. Do not edit!
package com.apphud.app.databinding;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.viewbinding.ViewBinding;
import com.apphud.app.R;
import java.lang.NullPointerException;
import java.lang.Override;
import java.lang.String;

public final class FragmentPurchasesBinding implements ViewBinding {
  @NonNull
  private final ConstraintLayout rootView;

  @NonNull
  public final RecyclerView purchasesList;

  @NonNull
  public final SwipeRefreshLayout swipeRefresh;

  private FragmentPurchasesBinding(@NonNull ConstraintLayout rootView,
      @NonNull RecyclerView purchasesList, @NonNull SwipeRefreshLayout swipeRefresh) {
    this.rootView = rootView;
    this.purchasesList = purchasesList;
    this.swipeRefresh = swipeRefresh;
  }

  @Override
  @NonNull
  public ConstraintLayout getRoot() {
    return rootView;
  }

  @NonNull
  public static FragmentPurchasesBinding inflate(@NonNull LayoutInflater inflater) {
    return inflate(inflater, null, false);
  }

  @NonNull
  public static FragmentPurchasesBinding inflate(@NonNull LayoutInflater inflater,
      @Nullable ViewGroup parent, boolean attachToParent) {
    View root = inflater.inflate(R.layout.fragment_purchases, parent, false);
    if (attachToParent) {
      parent.addView(root);
    }
    return bind(root);
  }

  @NonNull
  public static FragmentPurchasesBinding bind(@NonNull View rootView) {
    // The body of this method is generated in a way you would not otherwise write.
    // This is done to optimize the compiled bytecode for size and performance.
    int id;
    missingId: {
      id = R.id.purchasesList;
      RecyclerView purchasesList = rootView.findViewById(id);
      if (purchasesList == null) {
        break missingId;
      }

      id = R.id.swipeRefresh;
      SwipeRefreshLayout swipeRefresh = rootView.findViewById(id);
      if (swipeRefresh == null) {
        break missingId;
      }

      return new FragmentPurchasesBinding((ConstraintLayout) rootView, purchasesList, swipeRefresh);
    }
    String missingId = rootView.getResources().getResourceName(id);
    throw new NullPointerException("Missing required view with ID: ".concat(missingId));
  }
}