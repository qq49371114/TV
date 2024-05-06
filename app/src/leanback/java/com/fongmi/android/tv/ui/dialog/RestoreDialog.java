package com.fongmi.android.tv.ui.dialog;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;

import androidx.appcompat.app.AlertDialog;

import com.fongmi.android.tv.databinding.DialogRestoreBinding;
import com.fongmi.android.tv.impl.RestoreCallback;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class RestoreDialog {

    private final DialogRestoreBinding binding;
    private final RestoreCallback callback;
    private final AlertDialog dialog;

    public static RestoreDialog create(Activity activity) {
        return new RestoreDialog(activity);
    }

    public RestoreDialog(Activity activity) {
        this.callback = (RestoreCallback) activity;
        this.binding = DialogRestoreBinding.inflate(LayoutInflater.from(activity));
        this.dialog = new MaterialAlertDialogBuilder(activity).setView(binding.getRoot()).create();
    }

    public void show() {
        initDialog();
        initEvent();
    }

    private void initDialog() {
        dialog.getWindow().setDimAmount(0);
        dialog.show();
    }

    private void initEvent() {
        binding.positive.setOnClickListener(this::onPositive);
        binding.negative.setOnClickListener(this::onNegative);
    }

    private void onPositive(View view) {
        callback.onRestore();
        dialog.dismiss();
    }

    private void onNegative(View view) {
        dialog.dismiss();
    }
}