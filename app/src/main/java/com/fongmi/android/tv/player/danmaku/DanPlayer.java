package com.fongmi.android.tv.player.danmaku;

import androidx.media3.common.Player;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.bean.Danmaku;
import com.fongmi.android.tv.player.Players;
import com.fongmi.android.tv.utils.ResUtil;

import java.util.HashMap;

import master.flame.danmaku.controller.DrawHandler;
import master.flame.danmaku.danmaku.model.BaseDanmaku;
import master.flame.danmaku.danmaku.model.DanmakuTimer;
import master.flame.danmaku.danmaku.model.IDisplayer;
import master.flame.danmaku.danmaku.model.android.DanmakuContext;
import master.flame.danmaku.ui.widget.DanmakuView;

public class DanPlayer implements DrawHandler.Callback {

    private static final String TAG = DanPlayer.class.getSimpleName();
    private final DanmakuContext context;
    private DanmakuView view;
    private Players player;

    public DanPlayer() {
        context = DanmakuContext.create();
        HashMap<Integer, Integer> maxLines = new HashMap<>();
        maxLines.put(BaseDanmaku.TYPE_FIX_TOP, 2);
        maxLines.put(BaseDanmaku.TYPE_SCROLL_RL, 2);
        maxLines.put(BaseDanmaku.TYPE_SCROLL_LR, 2);
        maxLines.put(BaseDanmaku.TYPE_FIX_BOTTOM, 2);
        context.setMaximumLines(maxLines).setScrollSpeedFactor(1.2f).setDanmakuTransparency(0.8f);
        context.setDanmakuStyle(IDisplayer.DANMAKU_STYLE_STROKEN, 3).setDanmakuMargin(ResUtil.dp2px(8)).setScaleTextSize(0.8f);
    }

    public void setView(DanmakuView view) {
        view.setCallback(this);
        this.view = view;
    }

    public void setPlayer(Players player) {
        context.setDanmakuSync(new Sync(this.player = player));
    }

    private boolean isDanmakuPrepared() {
        return view != null && view.isPrepared();
    }

    public void seekTo(long time) {
        App.execute(() -> {
            if (isDanmakuPrepared()) view.seekTo(time);
            if (isDanmakuPrepared()) view.hide();
        });
    }

    public void play() {
        App.execute(() -> {
            if (isDanmakuPrepared()) view.resume();
        });
    }

    public void pause() {
        App.execute(() -> {
            if (isDanmakuPrepared()) view.pause();
        });
    }

    public void stop() {
        App.execute(() -> {
            if (isDanmakuPrepared()) view.stop();
        });
    }

    public void release() {
        App.execute(() -> {
            if (isDanmakuPrepared()) view.release();
        });
    }

    public void setDanmaku(Danmaku item) {
        App.execute(() -> {
            view.release();
            if (item.isEmpty()) return;
            view.prepare(new Parser().load(new Loader(item).getDataSource()), context);
        });
    }

    public void setTextSize(float size) {
        context.setScaleTextSize(size);
    }

    public void check(int state) {
        if (state == Player.STATE_BUFFERING) pause();
        else if (state == Player.STATE_READY) prepared();
    }

    @Override
    public void prepared() {
        App.post(() -> {
            boolean playing = player.isPlaying();
            long position = player.getPosition();
            App.execute(() -> {
                if (!isDanmakuPrepared()) return;
                if (playing) view.start(position);
                else view.pause();
                view.show();
            });
        });
    }

    @Override
    public void updateTimer(DanmakuTimer danmakuTimer) {
    }

    @Override
    public void danmakuShown(BaseDanmaku baseDanmaku) {
    }

    @Override
    public void drawingFinished() {
    }
}