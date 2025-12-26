package com.fongmi.android.tv.model; // 它的包名和 TimeSlot 是一样的

import java.util.List;

/**
 * 远程锁屏总配置模型
 * @author 婉儿
 */
public class AppLockConfig {
    public String password;
    public List<TimeSlot> timeSlots;
}
