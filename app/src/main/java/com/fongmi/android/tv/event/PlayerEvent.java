package com.fongmi.android.tv.event;

import org.greenrobot.eventbus.EventBus;

public class PlayerEvent {

    public static final int PREPARE = 0;
    public static final int TRACK = 21;
    public static final int SIZE = 11;

    private final int state;

    public static void prepare() {
        EventBus.getDefault().post(new PlayerEvent(PREPARE));
    }

    public static void track() {
        EventBus.getDefault().post(new PlayerEvent(TRACK));
    }

    public static void size() {
        EventBus.getDefault().post(new PlayerEvent(SIZE));
    }

    public static void state(int state) {
        EventBus.getDefault().post(new PlayerEvent(state));
    }

    private PlayerEvent(int state) {
        this.state = state;
    }

    public int getState() {
        return state;
    }
}
