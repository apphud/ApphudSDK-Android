// Generated by view binder compiler. Do not edit!
package com.apphud.sampleapp.databinding;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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

public final class ListItemSettingsInfoBinding implements ViewBinding {
  @NonNull
  private final ConstraintLayout rootView;

  @NonNull
  public final TextView labelTitle;

  @NonNull
  public final TextView labelValue;

  private ListItemSettingsInfoBinding(@NonNull ConstraintLayout rootView,
      @NonNull TextView labelTitle, @NonNull TextView labelValue) {
    this.rootView = rootView;
    this.labelTitle = labelTitle;
    this.labelValue = labelValue;
  }

  @Override
  @NonNull
  public ConstraintLayout getRoot() {
    return rootView;
  }

  @NonNull
  public static ListItemSettingsInfoBinding inflate(@NonNull LayoutInflater inflater) {
    return inflate(inflater, null, false);
  }

  @NonNull
  public static ListItemSettingsInfoBinding inflate(@NonNull LayoutInflater inflater,
      @Nullable ViewGroup parent, boolean attachToParent) {
    View root = inflater.inflate(R.layout.list_item_settings_info, parent, false);
    if (attachToParent) {
      parent.addView(root);
    }
    return bind(root);
  }

  @NonNull
  public static ListItemSettingsInfoBinding bind(@NonNull View rootView) {
    // The body of this method is generated in a way you would not otherwise write.
    // This is done to optimize the compiled bytecode for size and performance.
    int id;
    missingId: {
      id = R.id.labelTitle;
      TextView labelTitle = ViewBindings.findChildViewById(rootView, id);
      if (labelTitle == null) {
        break missingId;
      }

      id = R.id.labelValue;
      TextView labelValue = ViewBindings.findChildViewById(rootView, id);
      if (labelValue == null) {
        break missingId;
      }

      return new ListItemSettingsInfoBinding((ConstraintLayout) rootView, labelTitle, labelValue);
    }
    String missingId = rootView.getResources().getResourceName(id);
    throw new NullPointerException("Missing required view with ID: ".concat(missingId));
  }
}
