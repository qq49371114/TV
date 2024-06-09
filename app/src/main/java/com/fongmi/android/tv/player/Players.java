package com.fongmi.android.tv.player;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.analytics.AnalyticsListener;
import androidx.media3.exoplayer.util.EventLogger;
import androidx.media3.ui.PlayerView;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.Constant;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.Setting;
import com.fongmi.android.tv.bean.Channel;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Sub;
import com.fongmi.android.tv.bean.Track;
import com.fongmi.android.tv.event.ActionEvent;
import com.fongmi.android.tv.event.ErrorEvent;
import com.fongmi.android.tv.event.PlayerEvent;
import com.fongmi.android.tv.impl.ParseCallback;
import com.fongmi.android.tv.impl.SessionCallback;
import com.fongmi.android.tv.utils.FileUtil;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.UrlUtil;
import com.fongmi.android.tv.utils.Util;
import com.github.catvod.utils.Path;
import com.google.common.net.HttpHeaders;
import com.orhanobut.logger.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Formatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class Players implements Player.Listener, AnalyticsListener, ParseCallback {

    private static final String TAG = Players.class.getSimpleName();

    public static final int SOFT = 0;
    public static final int HARD = 1;

    private Map<String, String> headers;
    private MediaSessionCompat session;
    private StringBuilder builder;
    private Formatter formatter;
    private ParseJob parseJob;
    private Runnable runnable;
    private ExoPlayer player;
    private String url;
    private Sub sub;

    private long position;
    private int decode;
    private int error;
    private int retry;

    public static boolean isHard() {
        return Setting.getDecode() == HARD;
    }

    public static boolean isSoft() {
        return Setting.getDecode() == SOFT;
    }

    public Players init(Activity activity) {
        decode = Setting.getDecode();
        builder = new StringBuilder();
        runnable = ErrorEvent::timeout;
        formatter = new Formatter(builder, Locale.getDefault());
        createSession(activity);
        return this;
    }

    private void createSession(Activity activity) {
        session = new MediaSessionCompat(activity, "TV");
        session.setMediaButtonReceiver(null);
        session.setCallback(SessionCallback.create(this));
        session.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS | MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);
        session.setSessionActivity(PendingIntent.getActivity(App.get(), 99, new Intent(App.get(), activity.getClass()), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        MediaControllerCompat.setMediaController(activity, session.getController());
    }

    public void set(PlayerView view) {
        releasePlayer();
        setPlayer(view);
    }

    private void setPlayer(PlayerView view) {
        player = new ExoPlayer.Builder(App.get()).setLoadControl(ExoUtil.buildLoadControl()).setRenderersFactory(ExoUtil.buildRenderersFactory()).setTrackSelector(ExoUtil.buildTrackSelector()).build();
        player.setAudioAttributes(AudioAttributes.DEFAULT, true);
        player.addAnalyticsListener(new EventLogger());
        player.setHandleAudioBecomingNoisy(true);
        view.setRender(Setting.getRender());
        player.addAnalyticsListener(this);
        player.setPlayWhenReady(true);
        player.addListener(this);
        view.setPlayer(player);
    }

    public void setSub(Sub sub) {
        this.sub = sub;
        setMediaSource(headers, url);
    }

    public ExoPlayer get() {
        return player;
    }

    public Map<String, String> getHeaders() {
        return headers == null ? new HashMap<>() : checkUa(headers);
    }

    public String getUrl() {
        return url;
    }

    public MediaSessionCompat getSession() {
        return session;
    }

    public int getDecode() {
        return decode;
    }

    public void setDecode(int decode) {
        this.decode = decode;
    }

    public void setPosition(long position) {
        this.position = position;
    }

    public void reset() {
        removeTimeoutCheck();
        this.error = 0;
        this.retry = 0;
        stopParse();
    }

    public void clear() {
        this.headers = null;
        this.url = null;
    }

    public int addRetry() {
        ++retry;
        return retry;
    }

    public String stringToTime(long time) {
        return Util.format(builder, formatter, time);
    }

    public float getSpeed() {
        return player == null ? 1.0f : player.getPlaybackParameters().speed;
    }

    public long getPosition() {
        return player == null ? 0 : player.getCurrentPosition();
    }

    public long getDuration() {
        return player == null ? -1 : player.getDuration();
    }

    public long getBuffered() {
        return player == null ? 0 : player.getBufferedPosition();
    }

    public boolean canAdjustSpeed() {
        return !Setting.isTunnel();
    }

    public boolean haveTrack(int type) {
        return player != null && ExoUtil.haveTrack(player.getCurrentTracks(), type);
    }

    public boolean isPlaying() {
        return player != null && player.isPlaying();
    }

    public boolean isEnd() {
        return player != null && player.getPlaybackState() == Player.STATE_ENDED;
    }

    public boolean isEmpty() {
        return TextUtils.isEmpty(getUrl());
    }

    public boolean isLive() {
        return getDuration() < 5 * 60 * 1000;
    }

    public boolean isVod() {
        return getDuration() > 5 * 60 * 1000;
    }

    public boolean isPortrait() {
        return getVideoHeight() > getVideoWidth();
    }

    public String getSizeText() {
        return getVideoWidth() + " x " + getVideoHeight();
    }

    public String getSpeedText() {
        return String.format(Locale.getDefault(), "%.2f", getSpeed());
    }

    public String getDecodeText() {
        return ResUtil.getStringArray(R.array.select_decode)[getDecode()];
    }

    public String setSpeed(float speed) {
        if (player != null && !Setting.isTunnel()) player.setPlaybackSpeed(speed);
        return getSpeedText();
    }

    public String addSpeed() {
        float speed = getSpeed();
        float addon = speed >= 2 ? 1f : 0.25f;
        speed = speed >= 5 ? 0.25f : Math.min(speed + addon, 5.0f);
        return setSpeed(speed);
    }

    public String addSpeed(float value) {
        float speed = getSpeed();
        speed = Math.min(speed + value, 5);
        return setSpeed(speed);
    }

    public String subSpeed(float value) {
        float speed = getSpeed();
        speed = Math.max(speed - value, 0.25f);
        return setSpeed(speed);
    }

    public String toggleSpeed() {
        float speed = getSpeed();
        speed = speed == 1 ? 3f : 1f;
        return setSpeed(speed);
    }

    public void toggleDecode() {
        setDecode(getDecode() == HARD ? SOFT : HARD);
        Setting.putDecode(getDecode());
    }

    public String getPositionTime(long time) {
        time = getPosition() + time;
        if (time > getDuration()) time = getDuration();
        else if (time < 0) time = 0;
        return stringToTime(time);
    }

    public String getDurationTime() {
        long time = getDuration();
        if (time < 0) time = 0;
        return stringToTime(time);
    }

    public void seekTo(int time) {
        seekTo(getPosition() + time);
    }

    public void seekTo(long time) {
        if (player != null) player.seekTo(time);
    }

    public void play() {
        session.setActive(true);
        if (player != null) player.play();
        setPlaybackState(PlaybackStateCompat.STATE_PLAYING);
    }

    public void pause() {
        if (player != null) player.pause();
        setPlaybackState(PlaybackStateCompat.STATE_PAUSED);
    }

    public void stop() {
        reset();
        session.setActive(false);
        if (player != null) player.stop();
        setPlaybackState(PlaybackStateCompat.STATE_STOPPED);
    }

    public void release() {
        stopParse();
        releasePlayer();
        session.release();
        App.execute(() -> Source.get().stop());
    }

    private void releasePlayer() {
        if (player == null) return;
        player.removeListener(this);
        player.clearMediaItems();
        player.release();
        player = null;
    }

    public void start(Channel channel, int timeout) {
        if (channel.hasMsg()) {
            ErrorEvent.extract(channel.getMsg());
        } else if (channel.getParse() == 1) {
            startParse(channel.result(), false);
        } else if (isIllegal(channel.getUrl())) {
            ErrorEvent.url();
        } else {
            setMediaSource(channel, timeout);
        }
    }

    public void start(Result result, boolean useParse, int timeout) {
        if (result.hasMsg()) {
            ErrorEvent.extract(result.getMsg());
        } else if (result.getParse(1) == 1 || result.getJx() == 1) {
            startParse(result, useParse);
        } else if (isIllegal(result.getRealUrl())) {
            ErrorEvent.url();
        } else {
            setMediaSource(result, timeout);
        }
    }

    public int getVideoWidth() {
        return player.getVideoSize().width;
    }

    public int getVideoHeight() {
        return player.getVideoSize().height;
    }

    private void startParse(Result result, boolean useParse) {
        stopParse();
        parseJob = ParseJob.create(this).start(result, useParse);
    }

    private void stopParse() {
        if (parseJob != null) parseJob.stop();
        parseJob = null;
    }

    public void setMediaSource(String url) {
        setMediaSource(new HashMap<>(), url);
    }

    private void setMediaSource(Result result, int timeout) {
        Logger.t(TAG).d(error + "," + result.getRealUrl());
        if (player != null) player.setMediaSource(ExoUtil.getSource(result, sub, error), position);
        setTimeoutCheck(result.getHeaders(), result.getRealUrl(), timeout);
    }

    private void setMediaSource(Channel channel, int timeout) {
        Logger.t(TAG).d(error + "," + channel.getUrl());
        if (player != null) player.setMediaSource(ExoUtil.getSource(channel, error));
        setTimeoutCheck(channel.getHeaders(), channel.getUrl(), timeout);
    }

    private void setMediaSource(Map<String, String> headers, String url) {
        Logger.t(TAG).d(error + "," + url);
        if (player != null) player.setMediaSource(ExoUtil.getSource(headers, url, sub, error), position);
        setTimeoutCheck(headers, url, Constant.TIMEOUT_PLAY);
    }

    private void setTimeoutCheck(Map<String, String> headers, String url, int timeout) {
        if (player != null) player.prepare();
        App.post(runnable, timeout);
        this.headers = headers;
        PlayerEvent.state(0);
        this.url = url;
    }

    private void removeTimeoutCheck() {
        App.removeCallbacks(runnable);
    }

    public void setTrack(List<Track> tracks) {
        for (Track track : tracks) setTrack(track);
    }

    private void setTrack(Track item) {
        if (item.isSelected()) {
            ExoUtil.selectTrack(player, item.getGroup(), item.getTrack());
        } else {
            ExoUtil.deselectTrack(player, item.getGroup(), item.getTrack());
        }
    }

    private void setPlaybackState(int state) {
        long actions = PlaybackStateCompat.ACTION_SEEK_TO | PlaybackStateCompat.ACTION_PLAY_PAUSE | PlaybackStateCompat.ACTION_SKIP_TO_NEXT | PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS;
        session.setPlaybackState(new PlaybackStateCompat.Builder().setActions(actions).setState(state, getPosition(), getSpeed()).build());
    }

    private boolean isIllegal(String url) {
        Uri uri = UrlUtil.uri(url);
        String host = UrlUtil.host(uri);
        String scheme = UrlUtil.scheme(uri);
        if ("data".equals(scheme)) return false;
        return scheme.isEmpty() || "file".equals(scheme) ? !Path.exists(url) : host.isEmpty();
    }

    public static Map<String, String> checkUa(Map<String, String> headers) {
        if (Setting.getUa().isEmpty()) return headers;
        for (Map.Entry<String, String> header : headers.entrySet()) if (HttpHeaders.USER_AGENT.equalsIgnoreCase(header.getKey())) return headers;
        headers.put(HttpHeaders.USER_AGENT, Setting.getUa());
        return headers;
    }

    public Uri getUri() {
        return getUrl().startsWith("file://") || getUrl().startsWith("/") ? FileUtil.getShareUri(getUrl()) : Uri.parse(getUrl());
    }

    public String[] getHeaderArray() {
        List<String> list = new ArrayList<>();
        for (Map.Entry<String, String> entry : getHeaders().entrySet()) list.addAll(Arrays.asList(entry.getKey(), entry.getValue()));
        return list.toArray(new String[0]);
    }

    public Bundle getHeaderBundle() {
        Bundle bundle = new Bundle();
        for (Map.Entry<String, String> entry : getHeaders().entrySet()) bundle.putString(entry.getKey(), entry.getValue());
        return bundle;
    }

    public void setMetadata(String title, String artist, PlayerView view) {
        try {
            Bitmap bitmap = ((BitmapDrawable) view.getDefaultArtwork()).getBitmap();
            MediaMetadataCompat.Builder builder = new MediaMetadataCompat.Builder();
            builder.putString(MediaMetadataCompat.METADATA_KEY_TITLE, title);
            builder.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist);
            builder.putBitmap(MediaMetadataCompat.METADATA_KEY_ART, bitmap);
            builder.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, getDuration());
            session.setMetadata(builder.build());
            ActionEvent.update();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void share(Activity activity, CharSequence title) {
        try {
            if (isEmpty()) return;
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.putExtra(Intent.EXTRA_TEXT, getUrl());
            intent.putExtra("extra_headers", getHeaderBundle());
            intent.putExtra("title", title);
            intent.putExtra("name", title);
            intent.setType("text/plain");
            activity.startActivity(Util.getChooser(intent));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void choose(Activity activity, CharSequence title) {
        try {
            if (isEmpty()) return;
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.setDataAndType(getUri(), "video/*");
            intent.putExtra("title", title);
            intent.putExtra("return_result", isVod());
            intent.putExtra("headers", getHeaderArray());
            if (isVod()) intent.putExtra("position", (int) getPosition());
            activity.startActivityForResult(Util.getChooser(intent), 1001);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void checkData(Intent data) {
        try {
            if (data == null || data.getExtras() == null) return;
            int position = data.getExtras().getInt("position", 0);
            String endBy = data.getExtras().getString("end_by", "");
            if ("playback_completion".equals(endBy)) ActionEvent.next();
            if ("user".equals(endBy)) seekTo(position);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onParseSuccess(Map<String, String> headers, String url, String from) {
        if (!TextUtils.isEmpty(from)) Notify.show(ResUtil.getString(R.string.parse_from, from));
        setMediaSource(headers, url);
    }

    @Override
    public void onParseError() {
        ErrorEvent.parse();
    }

    @Override
    public void onEvents(@NonNull Player player, @NonNull Player.Events events) {
        if (!events.containsAny(Player.EVENT_PLAYBACK_STATE_CHANGED, Player.EVENT_PLAY_WHEN_READY_CHANGED, Player.EVENT_IS_PLAYING_CHANGED, Player.EVENT_TIMELINE_CHANGED, Player.EVENT_PLAYBACK_PARAMETERS_CHANGED, Player.EVENT_POSITION_DISCONTINUITY, Player.EVENT_REPEAT_MODE_CHANGED, Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED, Player.EVENT_MEDIA_METADATA_CHANGED)) return;
        setPlaybackState(isPlaying() ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED);
    }

    @Override
    public void onPlayerError(@NonNull PlaybackException error) {
        setPlaybackState(PlaybackStateCompat.STATE_ERROR);
        ErrorEvent.url(this.error = error.errorCode);
    }

    @Override
    public void onPlaybackStateChanged(int state) {
        switch (state) {
            case Player.STATE_READY:
                PlayerEvent.ready();
                break;
            case Player.STATE_IDLE:
            case Player.STATE_ENDED:
            case Player.STATE_BUFFERING:
                PlayerEvent.state(state);
                break;
        }
    }
}
