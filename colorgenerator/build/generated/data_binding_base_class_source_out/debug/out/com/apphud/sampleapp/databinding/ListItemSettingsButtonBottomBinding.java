// Generated by view binder compiler. Do not edit!
package com.apphud.sampleapp.databinding;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.viewbinding.ViewBinding;
import com.apphud.sampleapp.R;
import java.lang.NullPointerException;
import java.lang.Override;

public final class ListItemSettingsButtonBottomBinding implements ViewBinding {
  @NonNull
  private final ConstraintLayout rootView;

  private ListItemSettingsButtonBottomBinding(@NonNull ConstraintLayout rootView) {
    this.rootView = rootView;
  }

  @Override
  @NonNull
  public ConstraintLayout getRoot() {
    return rootView;
  }

  @NonNull
  public static ListItemSettingsButtonBottomBinding inflate(@NonNull LayoutInflater inflater) {
    return inflate(inflater, null, false);
  }

  @NonNull
  public static ListItemSettingsButtonBottomBinding inflate(@NonNull LayoutInflater inflater,
      @Nullable ViewGroup parent, boolean attachToParent) {
    View root = inflater.inflate(R.layout.list_item_settings_button_bottom, parent, false);
    if (attachToParent) {
      parent.addView(root);
    }
    return bind(root);
  }

  @NonNull
  public static ListItemSettingsButtonBottomBinding bind(@NonNull View rootView) {
    if (rootView == null) {
      throw new NullPointerException("rootView");
    }

    return new ListItemSettingsButtonBottomBinding((ConstraintLayout) rootView);
  }
}