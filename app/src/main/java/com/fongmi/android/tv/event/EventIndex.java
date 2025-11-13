    package com.fongmi.android.tv.event;

    import org.greenrobot.eventbus.meta.SubscriberInfo;
    import org.greenrobot.eventbus.meta.SubscriberInfoIndex;
    import java.util.HashMap;
    import java.util.Map;

    public class EventIndex implements SubscriberInfoIndex {

        private static final Map<Class<?>, SubscriberInfo> SUBSCRIBER_INDEX = new HashMap<>();

        public EventIndex() {
            // 这个文件就是为了让 App.java 能找到它，编译能通过。
            // 里面的内容是空的没关系。
        }

        @Override
        public SubscriberInfo getSubscriberInfo(Class<?> subscriberClass) {
            return SUBSCRIBER_INDEX.get(subscriberClass);
        }
    }
