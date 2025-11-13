package com.fongmi.android.tv.model;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.Constant;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.EpgParser;
import com.fongmi.android.tv.api.LiveParser;
import com.fongmi.android.tv.api.config.LiveConfig;
import com.fongmi.android.tv.bean.Channel;
import com.fongmi.android.tv.bean.Epg;
import com.fongmi.android.tv.bean.EpgData;
import com.fongmi.android.tv.bean.Group;
import com.fongmi.android.tv.bean.Live;
import com.fongmi.android.tv.exception.ExtractException;
import com.fongmi.android.tv.player.Source;
import com.github.catvod.net.OkHttp;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class LiveViewModel extends ViewModel {

    private enum TaskType {

        LIVE(Constant.TIMEOUT_LIVE),
        EPG(Constant.TIMEOUT_EPG),
        XML(Constant.TIMEOUT_XML),
        URL(Constant.TIMEOUT_PARSE_LIVE);

        final long timeout;

        TaskType(long timeout) {
            this.timeout = timeout;
        }
    }

    private final List<SimpleDateFormat> formatTime;
    private final Map<TaskType, Future<?>> futures;
    private final SimpleDateFormat formatDate;
    private final ExecutorService executor;

    public final MutableLiveData<Channel> url;
    public final MutableLiveData<Boolean> xml;
    public final MutableLiveData<Live> live;
    public final MutableLiveData<Epg> epg;

    public LiveViewModel() {
        this.live = new MutableLiveData<>();
        this.epg = new MutableLiveData<>();
        this.xml = new MutableLiveData<>();
        this.url = new MutableLiveData<>();
        this.formatTime = new ArrayList<>();
        this.futures = new EnumMap<>(TaskType.class);
        this.executor = Executors.newFixedThreadPool(2);
        this.formatDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        this.formatTime.add(new SimpleDateFormat("yyyy-MM-ddHH:mm", Locale.getDefault()));
        this.formatTime.add(new SimpleDateFormat("yyyy-MM-ddHH:mm:ss", Locale.getDefault()));
    }

    public void getLive(Live item) {
        execute(TaskType.LIVE, () -> {
            LiveParser.start(item.recent());
            setTimeZone(item);
            verify(item);
            return item;
        });
    }

    public void getXml(Live item) {
        execute(TaskType.XML, () -> item.getEpgXml().stream().anyMatch(url -> parseXml(item, url)));
    }

    private boolean parseXml(Live item, String url) {
        try {
            return EpgParser.start(item, url);
        } catch (Exception ignored) {
            return false;
        }
    }

    public void getEpg(Channel item) {
        String date = formatDate.format(new Date());
        String url = item.getEpg().replace("{date}", date);
        execute(TaskType.EPG, () -> {
            if (url.startsWith("http") && !item.getData().equal(date)) item.setData(Epg.objectFrom(OkHttp.string(url), item.getTvgId(), formatTime));
            return item.getData().selected();
        });
    }

    public void getUrl(Channel item) {
        execute(TaskType.URL, () -> {
            item.setMsg(null);
            Source.get().stop();
            item.setUrl(Source.get().fetch(item));
            return item;
        });
    }

    public void getUrl(Channel item, EpgData data) {
        execute(TaskType.URL, () -> {
            item.setMsg(null);
            Source.get().stop();
            item.setUrl(item.getCatchup().format(Source.get().fetch(item), data));
            return item;
        });
    }

    private void setTimeZone(Live live) {
        try {
            TimeZone timeZone = live.getTimeZone().isEmpty() ? TimeZone.getDefault() : TimeZone.getTimeZone(live.getTimeZone());
            formatTime.forEach(simpleDateFormat -> simpleDateFormat.setTimeZone(timeZone));
            formatDate.setTimeZone(timeZone);
        } catch (Exception ignored) {
        }
    }

    private void verify(Live item) {
        item.getGroups().removeIf(Group::isEmpty);
        if (item.getGroups().isEmpty() || item.getGroups().get(0).isKeep()) return;
        item.getGroups().add(0, Group.create(R.string.keep));
        LiveConfig.get().setKeep(item.getGroups());
    }

    private <T> void execute(TaskType type, Callable<T> callable) {
        Future<?> oldFuture = futures.get(type);
        if (oldFuture != null && !oldFuture.isDone()) oldFuture.cancel(true);
        Future<T> newFuture = App.submit(callable);
        if (executor.isShutdown()) return;
        futures.put(type, newFuture);
        executor.execute(() -> {
            try {
                T result = newFuture.get(type.timeout, TimeUnit.MILLISECONDS);
                if (newFuture.isCancelled()) return;
                if (type == TaskType.EPG) epg.postValue((Epg) result);
                else if (type == TaskType.LIVE) live.postValue((Live) result);
                else if (type == TaskType.XML) xml.postValue((Boolean) result);
                else if (type == TaskType.URL) url.postValue((Channel) result);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Throwable e) {
                if (newFuture.isCancelled()) return;
                if (e.getCause() instanceof ExtractException) url.postValue(Channel.error(e.getCause().getMessage()));
                else if (type == TaskType.URL) url.postValue(new Channel());
                else if (type == TaskType.LIVE) live.postValue(new Live());
                else if (type == TaskType.EPG) epg.postValue(new Epg());
                else if (type == TaskType.XML) xml.postValue(false);
                e.printStackTrace();
            }
        });
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        futures.values().forEach(future -> future.cancel(true));
        if (executor != null) executor.shutdownNow();
    }
}