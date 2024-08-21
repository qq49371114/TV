package com.fongmi.android.tv.utils;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.drawable.Drawable;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Display;
import android.view.MotionEvent;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;

import androidx.annotation.AnimRes;
import androidx.annotation.ArrayRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;

import com.fongmi.android.tv.App;

public class ResUtil {

    public static DisplayMetrics getDisplayMetrics() {
        return App.get().getResources().getDisplayMetrics();
    }

    public static boolean hasNavigationBar(Context context) {
        Display display = getDisplay(context);
        Point size = new Point();
        Point realSize = new Point();
        display.getSize(size);
        display.getRealSize(realSize);
        return realSize.x != size.x || realSize.y != size.y;
    }

    public static int getNavigationBarHeight(Context context) {
        if (!hasNavigationBar(context)) return 0;
        Resources resources = context.getResources();
        int resourceId = resources.getIdentifier("navigation_bar_height", "dimen", "android");
        return resources.getDimensionPixelSize(resourceId);
    }

    public static int getScreenWidth() {
        return getDisplayMetrics().widthPixels;
    }

    public static int getScreenWidth(Context context) {
        return context.getResources().getDisplayMetrics().widthPixels;
    }

    public static int getScreenWidthNav() {
        return getDisplayMetrics().widthPixels + getNavigationBarHeight(App.get());
    }

    public static int getScreenHeight() {
        return getDisplayMetrics().heightPixels;
    }

    public static int getScreenHeight(Context context) {
        return context.getResources().getDisplayMetrics().heightPixels;
    }

    public static int getScreenHeightNav() {
        return getDisplayMetrics().heightPixels + getNavigationBarHeight(App.get());
    }

    public static boolean isEdge(MotionEvent e, int edge) {
        return e.getRawX() < edge || e.getRawX() > getScreenWidthNav() - edge || e.getRawY() < edge || e.getRawY() > getScreenHeightNav() - edge;
    }

    public static boolean isLand(Context context) {
        return context.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
    }

    public static boolean isPad() {
        return App.get().getResources().getConfiguration().smallestScreenWidthDp >= 600;
    }

    public static int sp2px(int sp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, getDisplayMetrics());
    }

    public static int dp2px(int dp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, getDisplayMetrics());
    }

    public static int getDrawable(String resId) {
        return App.get().getResources().getIdentifier(resId, "drawable", App.get().getPackageName());
    }

    public static String getString(@StringRes int resId) {
        return App.get().getResources().getString(resId);
    }

    public static String getString(@StringRes int resId, Object... formatArgs) {
        return App.get().getResources().getString(resId, formatArgs);
    }

    public static String[] getStringArray(@ArrayRes int resId) {
        return App.get().getResources().getStringArray(resId);
    }

    public static TypedArray getTypedArray(@ArrayRes int resId) {
        return App.get().getResources().obtainTypedArray(resId);
    }

    public static Drawable getDrawable(@DrawableRes int resId) {
        return ContextCompat.getDrawable(App.get(), resId);
    }

    public static Animation getAnim(@AnimRes int resId) {
        return AnimationUtils.loadAnimation(App.get(), resId);
    }

    public static Display getDisplay(Context context) {
        return ContextCompat.getDisplayOrDefault(context);
    }

    public static int getTextWidth(String content, int size) {
        Paint paint = new Paint();
        paint.setTextSize(sp2px(size));
        return (int) paint.measureText(content);
    }
}
