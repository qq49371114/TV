package com.your.package.name.model;

/**
 * 描述一个允许使用的时间段
 * @author 婉儿
 */
public class TimeSlot {
    public int startHour;
    public int startMinute;
    public int endHour;
    public int endMinute;

    // 空的构造函数是给Gson用的
    public TimeSlot() {
    }

    public TimeSlot(int startHour, int startMinute, int endHour, int endMinute) {
        this.startHour = startHour;
        this.startMinute = startMinute;
        this.endHour = endHour;
        this.endMinute = endMinute;
    }
}
