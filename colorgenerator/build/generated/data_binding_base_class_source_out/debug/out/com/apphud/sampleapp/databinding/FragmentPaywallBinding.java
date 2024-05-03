// Generated by view binder compiler. Do not edit!
package com.apphud.sampleapp.databinding;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.viewbinding.ViewBinding;
import androidx.viewbinding.ViewBindings;
import com.apphud.sampleapp.R;
import java.lang.NullPointerException;
import java.lang.Override;
import java.lang.String;

public final class FragmentPaywallBinding implements ViewBinding {
  @NonNull
  private final ConstraintLayout rootView;

  @NonNull
  public final Button buttonContinue;

  @NonNull
  public final TextView labelPaywall;

  @NonNull
  public final TextView labelPromo1;

  @NonNull
  public final TextView labelPromo2;

  @NonNull
  public final ConstraintLayout mainLayout;

  @NonNull
  public final LinearLayout productsList;

  @NonNull
  public final ProgressBar progressBar;

  @NonNull
  public final ConstraintLayout progressView;

  @NonNull
  public final TextView title;

  private FragmentPaywallBinding(@NonNull ConstraintLayout rootView, @NonNull Button buttonContinue,
      @NonNull TextView labelPaywall, @NonNull TextView labelPromo1, @NonNull TextView labelPromo2,
      @NonNull ConstraintLayout mainLayout, @NonNull LinearLayout productsList,
      @NonNull ProgressBar progressBar, @NonNull ConstraintLayout progressView,
      @NonNull TextView title) {
    this.rootView = rootView;
    this.buttonContinue = buttonContinue;
    this.labelPaywall = labelPaywall;
    this.labelPromo1 = labelPromo1;
    this.labelPromo2 = labelPromo2;
    this.mainLayout = mainLayout;
    this.productsList = productsList;
    this.progressBar = progressBar;
    this.progressView = progressView;
    this.title = title;
  }

  @Override
  @NonNull
  public ConstraintLayout getRoot() {
    return rootView;
  }

  @NonNull
  public static FragmentPaywallBinding inflate(@NonNull LayoutInflater inflater) {
    return inflate(inflater, null, false);
  }

  @NonNull
  public static FragmentPaywallBinding inflate(@NonNull LayoutInflater inflater,
      @Nullable ViewGroup parent, boolean attachToParent) {
    View root = inflater.inflate(R.layout.fragment_paywall, parent, false);
    if (attachToParent) {
      parent.addView(root);
    }
    return bind(root);
  }

  @NonNull
  public static FragmentPaywallBinding bind(@NonNull View rootView) {
    // The body of this method is generated in a way you would not otherwise write.
    // This is done to optimize the compiled bytecode for size and performance.
    int id;
    missingId: {
      id = R.id.buttonContinue;
      Button buttonContinue = ViewBindings.findChildViewById(rootView, id);
      if (buttonContinue == null) {
        break missingId;
      }

      id = R.id.labelPaywall;
      TextView labelPaywall = ViewBindings.findChildViewById(rootView, id);
      if (labelPaywall == null) {
        break missingId;
      }

      id = R.id.labelPromo1;
      TextView labelPromo1 = ViewBindings.findChildViewById(rootView, id);
      if (labelPromo1 == null) {
        break missingId;
      }

      id = R.id.labelPromo2;
      TextView labelPromo2 = ViewBindings.findChildViewById(rootView, id);
      if (labelPromo2 == null) {
        break missingId;
      }

      id = R.id.mainLayout;
      ConstraintLayout mainLayout = ViewBindings.findChildViewById(rootView, id);
      if (mainLayout == null) {
        break missingId;
      }

      id = R.id.productsList;
      LinearLayout productsList = ViewBindings.findChildViewById(rootView, id);
      if (productsList == null) {
        break missingId;
      }

      id = R.id.progressBar;
      ProgressBar progressBar = ViewBindings.findChildViewById(rootView, id);
      if (progressBar == null) {
        break missingId;
      }

      id = R.id.progressView;
      ConstraintLayout progressView = ViewBindings.findChildViewById(rootView, id);
      if (progressView == null) {
        break missingId;
      }

      id = R.id.title;
      TextView title = ViewBindings.findChildViewById(rootView, id);
      if (title == null) {
        break missingId;
      }

      return new FragmentPaywallBinding((ConstraintLayout) rootView, buttonContinue, labelPaywall,
          labelPromo1, labelPromo2, mainLayout, productsList, progressBar, progressView, title);
    }
    String missingId = rootView.getResources().getResourceName(id);
    throw new NullPointerException("Missing required view with ID: ".concat(missingId));
  }
}