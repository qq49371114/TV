package com.fongmi.android.tv.ui.custom;

import static android.widget.ImageView.ScaleType.CENTER_CROP;
import static androidx.media3.exoplayer.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON;

import android.content.Context;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.activity.ComponentActivity;
import androidx.annotation.NonNull;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.media3.common.MediaItem;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.Setting;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.player.exo.ExoUtil;
import com.fongmi.android.tv.utils.FileUtil;
import com.fongmi.android.tv.utils.ImgUtil;
import com.fongmi.android.tv.utils.ResUtil;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;

public class CustomWallView extends FrameLayout implements DefaultLifecycleObserver {

    private ImageView image;
    private ExoPlayer player;
    private PlayerView video;

    public CustomWallView(@NonNull Context context) {
        super(context);
        init(context);
    }

    private void init(Context context) {
        ((ComponentActivity) context).getLifecycle().addObserver(this);
        createPlayer();
        addImageView();
        addVideoView();
        refresh();
    }

    private void createPlayer() {
        player = new ExoPlayer.Builder(App.get()).setRenderersFactory(ExoUtil.buildRenderersFactory(EXTENSION_RENDERER_MODE_ON)).setMediaSourceFactory(ExoUtil.buildMediaSourceFactory()).build();
        player.setRepeatMode(ExoPlayer.REPEAT_MODE_ALL);
        player.setPlayWhenReady(true);
        player.setVolume(0);
    }

    private void addImageView() {
        image = new ImageView(getContext());
        image.setScaleType(CENTER_CROP);
        addView(image, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    }

    private void addVideoView() {
        video = new PlayerView(getContext());
        video.setUseController(false);
        video.setKeepContentOnPlayerReset(true);
        video.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_ZOOM);
        addView(video, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    }

    private boolean isVideo(File file) {
        try (MediaMetadataRetriever retriever = new MediaMetadataRetriever()) {
            retriever.setDataSource(file.getAbsolutePath());
            return "yes".equalsIgnoreCase(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO));
        } catch (Exception e) {
            return false;
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onRefreshEvent(RefreshEvent event) {
        if (event.getType() == RefreshEvent.Type.WALL) refresh();
    }

    private void refresh() {
        load(FileUtil.getWall(Setting.getWall()));
    }

    private void load(File file) {
        if (!file.getName().endsWith("0")) loadRes(ResUtil.getDrawable(file.getName()));
        else if (isVideo(file)) loadVideo(file);
        else loadImage(file);
    }

    private void loadRes(int resId) {
        video.setPlayer(null);
        player.clearMediaItems();
        video.setVisibility(GONE);
        image.setImageResource(resId);
    }

    private void loadImage(File file) {
        video.setPlayer(null);
        player.clearMediaItems();
        video.setVisibility(GONE);
        ImgUtil.load(file, image);
    }

    private void loadVideo(File file) {
        player.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)));
        video.setVisibility(VISIBLE);
        video.setPlayer(player);
        player.prepare();
    }

    @Override
    public void onCreate(@NonNull LifecycleOwner owner) {
        EventBus.getDefault().register(this);
    }

    @Override
    public void onDestroy(@NonNull LifecycleOwner owner) {
        EventBus.getDefault().unregister(this);
        video.setPlayer(null);
        player.release();
        player = null;
    }
}
