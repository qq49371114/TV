package com.fongmi.android.tv.ui.dialog;

import android.content.DialogInterface;
import android.os.Environment;
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
import com.github.catvod.utils.Prefers;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

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
        clearJxTokenFile();
        System.out.println("保存 jxToken 成功: "+binding.text.getText().toString().trim());
        Notify.show("保存 jxToken 成功");
    }

    private void onNegative(DialogInterface dialog, int which) {
        dialog.dismiss();
    }

    public static void clearJxTokenFile() {
        String root = Environment.getExternalStorageDirectory().getAbsolutePath();
        String realPath = root + "/tm/jxToken.txt";
        File file = new File(realPath);

        if (!file.exists()) {
            System.out.println("APP - 不存在:" + realPath);
            // Create required directories
            File parentDir = file.getParentFile();
            if (!parentDir.exists()) {
                if (parentDir.mkdirs()) {
                    System.out.println("APP - 目录已创建:" + parentDir.getAbsolutePath());
                } else {
                    System.out.println("APP - 目录创建失败:" + parentDir.getAbsolutePath());
                }
            }

            // Attempt to create the file
            try {
                if (file.createNewFile()) {
                    System.out.println("APP - 文件已创建, 请配置: " + realPath);
                } else {
                    System.out.println("APP - 文件创建失败, 请自行创建: " + realPath);
                }
            } catch (IOException e) {
                System.out.println("APP - 创建文件时出错, 请自行创建: " + e.getMessage());
            }
        } else {
            System.out.println("APP - 存在:" + realPath);

            // Clear file contents by overwriting with an empty string
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
                writer.write("");
            } catch (IOException e) {
                System.out.println("APP - 清空文件失败: " + e.getMessage());
                return;
            }

            // Check if the file is now empty
            if (file.length() == 0) {
                System.out.println("APP - 文件已清空: " + realPath);
            } else {
                System.out.println("APP - 文件清空可能不成功: " + realPath);
            }
        }
    }
}
