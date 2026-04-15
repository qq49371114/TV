package com.fongmi.android.tv.player;

import android.net.Uri;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.Tracks;
import androidx.media3.common.VideoSize;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.drm.FrameworkMediaDrm;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.Constant;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.Setting;
import com.fongmi.android.tv.bean.Danmaku;
import com.fongmi.android.tv.bean.Drm;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Sub;
import com.fongmi.android.tv.bean.Track;
import com.fongmi.android.tv.impl.ParseCallback;
import com.fongmi.android.tv.player.danmaku.DanPlayer;
import com.fongmi.android.tv.player.exo.ErrorMsgProvider;
import com.fongmi.android.tv.player.exo.ExoUtil;
import com.fongmi.android.tv.player.exo.TrackUtil;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.UrlUtil;
import com.fongmi.android.tv.utils.Util;
import com.github.catvod.utils.Path;
import com.google.common.net.HttpHeaders;
import com.orhanobut.logger.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import master.flame.danmaku.ui.widget.DanmakuView;

public class PlayerManager implements ParseCallback {

    public static final String TAG = PlayerManager.class.getSimpleName();
    public static final int SOFT = 0;
    public static final int HARD = 1;

    private final ErrorMsgProvider provider = new ErrorMsgProvider();
    private final Runnable timeoutRunnable;
    private final Callback callback;
    private ExoPlayer exoPlayer;
    private DanPlayer danPlayer;
    private ParseJob parseJob;

    private Map<String, String> headers;
    private MediaMetadata metadata;
    private List<Danmaku> danmakus;
    private VideoSize videoSize;
    private List<Sub> subs;
    private String format;
    private String key;
    private String url;
    private Drm drm;
    private Sub sub;
    private int retry;
    private int decode = HARD;
    private boolean initTrack;

    public PlayerManager(Callback callback) {
        this.callback = callback;
        this.exoPlayer = ExoUtil.buildExoPlayer(decode, playerListener);
        this.timeoutRunnable = () -> callback.onError(ResUtil.getString(R.string.error_play_timeout));
    }

    public void release() {
        stopParse();
        App.removeCallbacks(timeoutRunnable);
        if (danPlayer != null) {
            danPlayer.release();
            danPlayer = null;
        }
        if (exoPlayer != null) {
            exoPlayer.removeListener(playerListener);
            exoPlayer.release();
            exoPlayer = null;
        }
    }

    public Player getPlayer() {
        return exoPlayer;
    }

    public Tracks getCurrentTracks() {
        return exoPlayer.getCurrentTracks();
    }

    public MediaItem getCurrentMediaItem() {
        return exoPlayer.getCurrentMediaItem();
    }

    public int getPlaybackState() {
        return exoPlayer.getPlaybackState();
    }

    public boolean isPlaying() {
        return exoPlayer.isPlaying();
    }

    public String getUrl() {
        return url;
    }

    public String getKey() {
        return key != null ? key : url;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public List<Danmaku> getDanmakus() {
        return danmakus;
    }

    public Map<String, String> getHeaders() {
        return headers == null ? new HashMap<>() : headers;
    }

    public float getSpeed() {
        return exoPlayer.getPlaybackParameters().speed;
    }

    public boolean isEmpty() {
        return TextUtils.isEmpty(url);
    }

    public boolean isHard() {
        return decode == HARD;
    }

    public boolean isPortrait() {
        return getVideoHeight() > getVideoWidth();
    }

    public boolean isLandscape() {
        return getVideoWidth() > getVideoHeight();
    }

    public boolean isLive() {
        return exoPlayer.getDuration() < TimeUnit.MINUTES.toMillis(1) || exoPlayer.isCurrentMediaItemLive();
    }

    public boolean isVod() {
        return exoPlayer.getDuration() > TimeUnit.MINUTES.toMillis(1) && !exoPlayer.isCurrentMediaItemLive();
    }

    public boolean haveTrack(int type) {
        return TrackUtil.count(exoPlayer.getCurrentTracks(), type) > 0;
    }

    public boolean haveDanmaku() {
        return danmakus != null && danmakus.stream().anyMatch(Danmaku::isSelected);
    }

    public boolean canSetOpening(long position, long duration) {
        return position > 0 && duration > 0 && position <= Constant.getOpEdLimit(duration);
    }

    public boolean canSetEnding(long position, long duration) {
        return position > 0 && duration > 0 && duration - position <= Constant.getOpEdLimit(duration);
    }

    public int getVideoWidth() {
        return videoSize == null ? 0 : videoSize.width;
    }

    public int getVideoHeight() {
        return videoSize == null ? 0 : videoSize.height;
    }

    public long getPosition() {
        return exoPlayer.getCurrentPosition();
    }

    public String getSizeText() {
        return (getVideoWidth() == 0 && getVideoHeight() == 0) ? "" : getVideoWidth() + " x " + getVideoHeight();
    }

    public String getSpeedText() {
        return String.format(Locale.getDefault(), "%.2f", getSpeed());
    }

    public String getDecodeText() {
        return ResUtil.getStringArray(R.array.select_decode)[decode];
    }

    public String getPositionTime(long delta) {
        long time = Math.max(0, Math.min(getPosition() + delta, Math.max(0, getDuration())));
        return Util.timeMs(time);
    }

    public long getDuration() {
        return exoPlayer.getDuration();
    }

    public String getDurationTime() {
        return Util.timeMs(Math.max(0, getDuration()));
    }

    public void setSub(Sub sub) {
        this.sub = sub;
        setMediaItem();
    }

    public void setFormat(String format) {
        this.format = format;
        setMediaItem();
    }

    public void setMetadata(String title, String artist, String artUri) {
        Uri artwork = TextUtils.isEmpty(artUri) ? null : Uri.parse(artUri);
        MediaItem current = exoPlayer.getCurrentMediaItem();
        metadata = new MediaMetadata.Builder().setTitle(title).setArtist(artist).setArtworkUri(artwork).build();
        if (current != null) exoPlayer.replaceMediaItem(exoPlayer.getCurrentMediaItemIndex(), current.buildUpon().setMediaMetadata(metadata).build());
    }

    public void setDanmakuView(DanmakuView view) {
        danPlayer = new DanPlayer(view);
        danPlayer.attachPlayer(exoPlayer);
    }

    public void setDanmaku(Danmaku item) {
        if (danPlayer != null) danPlayer.setDanmaku(item);
        if (danmakus == null) danmakus = new ArrayList<>();
        if (!item.isEmpty() && !danmakus.contains(item)) danmakus.add(0, item);
        danmakus.forEach(danmaku -> danmaku.setSelected(danmaku.getUrl().equals(item.getUrl())));
    }

    public void setDanmakuSize(float size) {
        danPlayer.setTextSize(size);
    }

    public String setSpeed(float speed) {
        if (!exoPlayer.isCommandAvailable(Player.COMMAND_SET_SPEED_AND_PITCH)) return getSpeedText();
        exoPlayer.setPlaybackParameters(exoPlayer.getPlaybackParameters().withSpeed(speed));
        return getSpeedText();
    }

    public String addSpeed() {
        float speed = getSpeed();
        float addon = speed >= 2 ? 1f : 0.25f;
        speed = speed >= 5 ? 0.25f : Math.min(speed + addon, 5.0f);
        return setSpeed(speed);
    }

    public String addSpeed(float value) {
        return setSpeed(Math.min(getSpeed() + value, 5));
    }

    public String subSpeed(float value) {
        return setSpeed(Math.max(getSpeed() - value, 0.25f));
    }

    public String toggleSpeed() {
        return setSpeed(getSpeed() == 1 ? Setting.getSpeed() : 1);
    }

    public void setTrack(List<Track> tracks) {
        if (!tracks.isEmpty()) TrackUtil.setTrackSelection(exoPlayer, tracks);
    }

    public void play() {
        exoPlayer.play();
    }

    public void pause() {
        exoPlayer.pause();
    }

    public void stop() {
        if (danPlayer != null) danPlayer.stop();
        exoPlayer.stop();
        stopParse();
    }

    public void setRepeatOne(boolean repeat) {
        exoPlayer.setRepeatMode(repeat ? Player.REPEAT_MODE_ONE : Player.REPEAT_MODE_OFF);
    }

    public void seekTo(long time) {
        exoPlayer.seekTo(time);
    }

    private void seekToDefaultPosition() {
        exoPlayer.seekToDefaultPosition();
        exoPlayer.prepare();
    }

    public void reset() {
        App.removeCallbacks(timeoutRunnable);
        retry = 0;
    }

    public void clear() {
        danmakus = null;
        metadata = null;
        headers = null;
        format = null;
        subs = null;
        drm = null;
        key = null;
        url = null;
    }

    public void resetTrack() {
        TrackUtil.reset(exoPlayer);
    }

    public void toggleDecode() {
        decode = isHard() ? SOFT : HARD;
        rebuildPlayer();
        App.post(this::setMediaItem, 100);
    }

    private void rebuildPlayer() {
        exoPlayer.release();
        exoPlayer = ExoUtil.buildExoPlayer(decode, playerListener);
        if (danPlayer != null) danPlayer.attachPlayer(exoPlayer);
        callback.onPlayerRebuild(exoPlayer);
    }

    public void start(Result result, boolean useParse, long timeout) {
        if (result.getDrm() != null && !FrameworkMediaDrm.isCryptoSchemeSupported(result.getDrm().getUUID())) {
            callback.onError(ResUtil.getString(R.string.error_play_drm));
        } else if (result.hasMsg()) {
            callback.onError(result.getMsg());
        } else if (result.getParse() == 1 || result.getJx() == 1) {
            startParse(result, useParse);
        } else if (isIllegal(result.getRealUrl())) {
            callback.onError(ResUtil.getString(R.string.error_play_url));
        } else {
            setMediaItem(result, timeout);
        }
    }

    private void startParse(Result result, boolean useParse) {
        stopParse();
        drm = result.getDrm();
        subs = result.getSubs();
        format = result.getFormat();
        danmakus = result.getDanmaku();
        parseJob = ParseJob.create(this).start(result, useParse);
    }

    private void stopParse() {
        if (parseJob != null) parseJob.stop();
        parseJob = null;
    }

    public void setMediaItem() {
        if (url != null) setMediaItem(headers, url, format, drm, subs, danmakus, Constant.TIMEOUT_PLAY);
    }

    public void setMediaItem(String url) {
        setMediaItem(headers, url);
    }

    public void setMediaItem(Map<String, String> headers, String url) {
        setMediaItem(headers, url, format, drm, subs, danmakus, Constant.TIMEOUT_PLAY);
    }

    public void startBrowse(String key, MediaMetadata metadata, Result result) {
        reset();
        clear();
        stopParse();
        this.key = key;
        this.metadata = metadata;
        setMediaItem(result, Constant.TIMEOUT_PLAY);
    }

    private void setMediaItem(Result result, long timeout) {
        setMediaItem(result.getHeader(), result.getRealUrl(), result.getFormat(), result.getDrm(), result.getSubs(), result.getDanmaku(), timeout);
    }

    private void setMediaItem(Map<String, String> headers, String url, String format, Drm drm, List<Sub> subs, List<Danmaku> danmakus, long timeout) {
        MediaItem mediaItem = ExoUtil.getMediaItem(this.key, this.headers = checkUa(headers), UrlUtil.uri(this.url = url), this.format = format, this.drm = drm, checkSub(this.subs = subs), decode);
        Logger.t(TAG).d("headers=%s\nurl=%s\nformat=%s\ndrm=%s\nsubs=%s\ndanmakus=%s\ntimeout=%s", this.headers, url, format, drm, this.subs, danmakus, timeout);
        if (metadata != null) mediaItem = mediaItem.buildUpon().setMediaMetadata(metadata).build();
        if (danPlayer != null) setDanmakuItem(this.danmakus = danmakus);
        App.post(timeoutRunnable, timeout);
        exoPlayer.setMediaItem(mediaItem);
        exoPlayer.prepare();
        exoPlayer.play();
        callback.onPrepare();
        initTrack = false;
    }

    private void setDanmakuItem(List<Danmaku> items) {
        setDanmaku(items == null || items.isEmpty() ? Danmaku.empty() : items.get(0));
    }

    private Map<String, String> checkUa(Map<String, String> headers) {
        if (headers == null) headers = new HashMap<>();
        if (headers.keySet().stream().noneMatch(HttpHeaders.USER_AGENT::equalsIgnoreCase)) headers.put(HttpHeaders.USER_AGENT, Setting.getUa().isEmpty() ? ExoUtil.getUa() : Setting.getUa());
        return headers;
    }

    private List<Sub> checkSub(List<Sub> subs) {
        if (subs == null) subs = this.subs = new ArrayList<>();
        if (sub == null || subs.contains(sub)) return subs;
        subs.add(0, sub);
        return subs;
    }

    private boolean isIllegal(String url) {
        Uri uri = UrlUtil.uri(url);
        String host = UrlUtil.host(uri);
        String scheme = UrlUtil.scheme(uri);
        if ("data".equals(scheme)) return false;
        return scheme.isEmpty() || "file".equals(scheme) ? !Path.exists(url) : host.isEmpty();
    }

    @Override
    public void onParseSuccess(Map<String, String> headers, String url, String from) {
        if (!TextUtils.isEmpty(from)) Notify.show(ResUtil.getString(R.string.parse_from, from));
        if (headers != null) headers.remove(HttpHeaders.RANGE);
        setMediaItem(headers, url);
    }

    @Override
    public void onParseError() {
        callback.onError(ResUtil.getString(R.string.error_play_parse));
    }

    public interface Callback {

        void onPrepare();

        void onTracksChanged();

        void onTitlesChanged();

        void onError(String msg);

        void onPlayerRebuild(Player newPlayer);
    }

    private final Player.Listener playerListener = new Player.Listener() {
        @Override
        public void onPlaybackStateChanged(int state) {
            if (state != Player.STATE_IDLE) App.removeCallbacks(timeoutRunnable);
        }

        @Override
        public void onVideoSizeChanged(@NonNull VideoSize size) {
            videoSize = size;
        }

        @Override
        public void onTracksChanged(@NonNull Tracks tracks) {
            if (tracks.isEmpty() || initTrack) return;
            setTrack(Track.find(getKey()));
            callback.onTracksChanged();
            initTrack = true;
        }

        @Override
        public void onPlayerError(@NonNull PlaybackException e) {
            if (++retry > 2) {
                callback.onError(provider.get(e));
            } else {
                switch (e.errorCode) {
                    case PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW:
                        seekToDefaultPosition();
                        break;
                    case PlaybackException.ERROR_CODE_DECODER_INIT_FAILED:
                    case PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED:
                    case PlaybackException.ERROR_CODE_DECODING_FAILED:
                        toggleDecode();
                        break;
                    case PlaybackException.ERROR_CODE_IO_UNSPECIFIED:
                    case PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED:
                    case PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED:
                    case PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED:
                    case PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED:
                        setFormat(ExoUtil.getMimeType(e.errorCode));
                        break;
                    default:
                        callback.onError(provider.get(e));
                        break;
                }
            }
        }
    };
}
