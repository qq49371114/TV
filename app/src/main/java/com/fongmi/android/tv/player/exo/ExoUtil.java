package com.fongmi.android.tv.player.exo;

import android.content.Context;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.accessibility.CaptioningManager;

import androidx.media3.common.AudioAttributes;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.util.Util;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.LoadControl;
import androidx.media3.exoplayer.RenderersFactory;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;
import androidx.media3.exoplayer.trackselection.TrackSelector;
import androidx.media3.exoplayer.util.EventLogger;
import androidx.media3.ui.CaptionStyleCompat;
import androidx.media3.ui.PlayerView;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.BuildConfig;
import com.fongmi.android.tv.Setting;
import com.fongmi.android.tv.bean.Drm;
import com.fongmi.android.tv.bean.Sub;
import com.fongmi.android.tv.player.PlayerManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public class ExoUtil {

    public static ExoPlayer buildExoPlayer(int decode, Player.Listener listener) {
        ExoPlayer player = new ExoPlayer.Builder(App.get()).setLoadControl(buildLoadControl()).setTrackSelector(buildTrackSelector()).setRenderersFactory(buildRenderersFactory(decode == PlayerManager.HARD ? DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON : DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)).setMediaSourceFactory(buildMediaSourceFactory()).build();
        if (BuildConfig.DEBUG) player.addAnalyticsListener(new EventLogger());
        player.setAudioAttributes(AudioAttributes.DEFAULT, true);
        player.setHandleAudioBecomingNoisy(true);
        player.setPlayWhenReady(true);
        player.addListener(listener);
        return player;
    }

    public static String getUa() {
        return Util.getUserAgent(App.get(), BuildConfig.APPLICATION_ID);
    }

    public static LoadControl buildLoadControl() {
        return new DefaultLoadControl.Builder().setBufferDurationsMs(DefaultLoadControl.DEFAULT_MIN_BUFFER_MS * Setting.getBuffer(), DefaultLoadControl.DEFAULT_MAX_BUFFER_MS * Setting.getBuffer(), DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS, DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS).build();
    }

    public static TrackSelector buildTrackSelector() {
        DefaultTrackSelector trackSelector = new DefaultTrackSelector(App.get());
        DefaultTrackSelector.Parameters.Builder builder = trackSelector.buildUponParameters();
        if (Setting.isPreferAAC()) builder.setPreferredAudioMimeType(MimeTypes.AUDIO_AAC);
        builder.setPreferredTextLanguage(Locale.getDefault().getISO3Language());
        builder.setTunnelingEnabled(Setting.isTunnel());
        builder.setForceHighestSupportedBitrate(true);
        trackSelector.setParameters(builder.build());
        return trackSelector;
    }

    public static RenderersFactory buildRenderersFactory(int renderMode) {
        return new DefaultRenderersFactory(App.get()).setEnableDecoderFallback(true).setExtensionRendererMode(renderMode);
    }

    public static MediaSource.Factory buildMediaSourceFactory() {
        return new MediaSourceFactory();
    }

    public static CaptionStyleCompat getCaptionStyle() {
        return Setting.isCaption() ? CaptionStyleCompat.createFromCaptionStyle(((CaptioningManager) App.get().getSystemService(Context.CAPTIONING_SERVICE)).getUserStyle()) : new CaptionStyleCompat(Color.WHITE, Color.TRANSPARENT, Color.TRANSPARENT, CaptionStyleCompat.EDGE_TYPE_OUTLINE, Color.BLACK, null);
    }

    public static void setSubtitleView(PlayerView exo) {
        exo.getSubtitleView().setStyle(getCaptionStyle());
        exo.getSubtitleView().setApplyEmbeddedStyles(true);
        exo.getSubtitleView().setApplyEmbeddedFontSizes(false);
        if (Setting.getSubtitleTextSize() != 0) exo.getSubtitleView().setFractionalTextSize(Setting.getSubtitleTextSize());
    }

    public static String getMimeType(String path) {
        if (TextUtils.isEmpty(path)) return "";
        if (path.endsWith(".vtt")) return MimeTypes.TEXT_VTT;
        if (path.endsWith(".ssa") || path.endsWith(".ass")) return MimeTypes.TEXT_SSA;
        if (path.endsWith(".ttml") || path.endsWith(".xml") || path.endsWith(".dfxp")) return MimeTypes.APPLICATION_TTML;
        return MimeTypes.APPLICATION_SUBRIP;
    }

    public static String getMimeType(int errorCode) {
        if (errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED || errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED || errorCode == PlaybackException.ERROR_CODE_IO_UNSPECIFIED) return MimeTypes.APPLICATION_M3U8;
        return null;
    }

    public static MediaItem getMediaItem(String key, Map<String, String> headers, Uri uri, String mimeType, Drm drm, List<Sub> subs, int decode) {
        MediaItem.Builder builder = new MediaItem.Builder().setUri(uri);
        builder.setRequestMetadata(getRequestMetadata(headers, uri));
        builder.setSubtitleConfigurations(getSubtitleConfigs(subs));
        if (drm != null) builder.setDrmConfiguration(drm.get());
        builder.setMediaId(key == null ? uri.toString() : key);
        if (mimeType != null) builder.setMimeType(mimeType);
        builder.setMediaId(uri.toString());
        builder.setImageDurationMs(15000);
        return builder.build();
    }

    public static Bundle toBundle(Map<String, String> headers) {
        Bundle bundle = new Bundle();
        headers.forEach(bundle::putString);
        return bundle;
    }

    public static Map<String, String> extractHeaders(MediaItem item) {
        Bundle extras = item.requestMetadata.extras;
        if (extras == null) return new HashMap<>();
        return extras.keySet().stream().filter(key -> extras.getString(key) != null).collect(Collectors.toMap(key -> key, extras::getString));
    }

    public static MediaItem.RequestMetadata getRequestMetadata(Map<String, String> headers, Uri uri) {
        return new MediaItem.RequestMetadata.Builder().setMediaUri(uri).setExtras(toBundle(headers)).build();
    }

    private static List<MediaItem.SubtitleConfiguration> getSubtitleConfigs(List<Sub> subs) {
        List<MediaItem.SubtitleConfiguration> configs = new ArrayList<>();
        if (subs != null) for (Sub sub : subs) configs.add(sub.config());
        return configs;
    }
}
