package com.fongmi.android.tv.api.config;

    import com.fongmi.android.tv.App;
    import com.fongmi.android.tv.bean.Config;
    import com.fongmi.android.tv.utils.TimeLockUtils;

    /**
     * 锁屏功能的专属配置管家
     * @author 婉儿
     */
    public class LockConfig {

        // 当ConfigDialog需要一个“3号”配置时，我们就给它一个
        public static Config find(String url) {
            return new Config().url(url).type(3);
        }

        // 当ConfigDialog需要获取当前“3号”配置的URL时，我们就去读我们自己的“小本本”
        public static String getUrl() {
            return TimeLockUtils.getConfigUrl(App.get());
        }

        // 当ConfigDialog把新的URL发给我们时，我们就把它存到我们自己的“小本本”里
        public static void setUrl(String url) {
            TimeLockUtils.saveConfigUrl(App.get(), url);
        }
    }
