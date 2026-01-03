package com.fongmi.android.tv.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;

/**
 * 描述一个允许使用的时间段 (已升级为支持星期规则)
 * @author 婉儿
 */
public class TimeSlot {

    @SerializedName("days")
    private List<Integer> days;

    @SerializedName("startHour")
    public int startHour;

    @SerializedName("startMinute")
    public int startMinute;

    @SerializedName("endHour")
    public int endHour;

    @SerializedName("endMinute")
    public int endMinute;

    // 空的构造函数是给Gson用的
    public TimeSlot() {
    }
    
    public TimeSlot(List<Integer> days, int startHour, int startMinute, int endHour, int endMinute) {
        this.days = days;
        this.startHour = startHour;
        this.startMinute = startMinute;
        this.endHour = endHour;
        this.endMinute = endMinute;
    }

    // --- ✨↓ 婉儿把所有需要的方法都放在这里啦！↓✨ ---

    public List<Integer> getDays() {
        return days;
    }

    public int getStartHour() {
        return this.startHour;
    }

    public int getStartMinute() {
        return this.startMinute;
    }

    public int getEndHour() {
        return this.endHour;
    }

    public int getEndMinute() {
        return this.endMinute;
    }
    
    // --- ✨↑ 方法区结束！↑✨ ---
}
