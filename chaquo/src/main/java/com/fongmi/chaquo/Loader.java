package com.fongmi.chaquo;

import android.content.Context;

import com.chaquo.python.PyObject;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;
import com.github.catvod.utils.Path;

public class Loader {

    private PyObject app;

    private void init(Context context) {
        if (!Python.isStarted()) Python.start(new AndroidPlatform(context));
        app = Python.getInstance().getModule("app");
    }

    public Spider spider(Context context, String api) {
        if (app == null) init(context);
        PyObject obj = app.callAttr("spider", Path.py().getAbsolutePath(), api);
        return new Spider(app, obj, api);
    }
}
