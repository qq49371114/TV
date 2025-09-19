package com.fongmi.android.tv.ui.adapter.diff;

public interface Diffable<T> {

    boolean isSameItem(T other);

    boolean isSameContent(T other);
}
