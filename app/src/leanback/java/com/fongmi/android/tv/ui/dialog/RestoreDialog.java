package com.fongmi.android.tv.ui.dialog;

import android.app.Activity;
import com.fongmi.android.tv.impl.RestoreCallback;
import com.github.catvod.utils.Path;
import com.obsez.android.lib.filechooser.ChooserDialog;

import java.io.File;

public class RestoreDialog {

    private ChooserDialog dialog;

    private RestoreCallback callback;

    public static RestoreDialog create() {
        return new RestoreDialog();
    }

    public RestoreDialog callback(RestoreCallback callback) {
        this.callback = callback;
        return this;
    }

    public void show(Activity activity) {
        dialog = new ChooserDialog(activity);
        dialog.withFilter(false, false, "tv", "backup");
        dialog.withStartFile(Path.tv().getAbsolutePath());
        dialog.withChosenListener(this::onChoosePath);
        dialog.withOnBackPressedListener(d -> dialog.goBack());
        dialog.withOnLastBackPressedListener(d -> dialog.dismiss());
        dialog.build().show();
    }


    private void onChoosePath(String path, File pathFile) {
        callback.onRestore(pathFile);
        if (dialog != null) dialog.dismiss();
    }
}