package com.fongmi.android.tv.ui.dialog;

import android.content.DialogInterface;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.inputmethod.EditorInfo;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.Setting;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.databinding.DialogJxtokenBinding;
import com.fongmi.android.tv.impl.JxtokenCallback;
import com.fongmi.android.tv.ui.custom.CustomTextListener;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class JxtokenDialog {

    private final DialogJxtokenBinding binding;
    private final JxtokenCallback callback;
    private AlertDialog dialog;
    private boolean append;

    public static JxtokenDialog create(Fragment fragment) {
        return new JxtokenDialog(fragment);
    }

    public JxtokenDialog(Fragment fragment) {
        this.callback = (JxtokenCallback) fragment;
        this.binding = DialogJxtokenBinding.inflate(LayoutInflater.from(fragment.getContext()));
        this.append = true;
    }

    public void show() {
        initDialog();
        initView();
        initEvent();
    }

    private void initDialog() {
        dialog = new MaterialAlertDialogBuilder(binding.getRoot().getContext()).setTitle(R.string.setting_jxtoken).setView(binding.getRoot()).setPositiveButton(R.string.dialog_positive, this::onPositive).setNegativeButton(R.string.dialog_negative, this::onNegative).create();
        dialog.getWindow().setDimAmount(0);
        dialog.show();
    }

    private void initView() {
        String text = Setting.getJxtoken();
        binding.text.setText(text);
        binding.text.setSelection(TextUtils.isEmpty(text) ? 0 : text.length());
    }

    private void initEvent() {
        binding.text.addTextChangedListener(new CustomTextListener() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                detect(s.toString());
            }
        });
        binding.text.setOnEditorActionListener((textView, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
            return true;
        });
    }

    private void detect(String s) {
        append = true;
    }

    private void onPositive(DialogInterface dialog, int which) {
        callback.setJxtoken(binding.text.getText().toString().trim());
        Setting.putJxtoken(binding.text.getText().toString().trim());
        System.out.println("设置保存jxToken成功: "+binding.text.getText().toString().trim());
        Notify.show("设置保存jxToken成功");
        dialog.dismiss();
    }

    private void onNegative(DialogInterface dialog, int which) {
        dialog.dismiss();
    }
}
