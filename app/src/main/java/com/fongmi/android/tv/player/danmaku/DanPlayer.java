package com.fongmi.android.tv.player.danmaku;

import androidx.media3.common.Player;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.bean.Danmaku;
import com.fongmi.android.tv.player.Players;
import com.fongmi.android.tv.utils.ResUtil;

import java.util.HashMap;
import java.util.Map;

import master.flame.danmaku.controller.DrawHandler;
import master.flame.danmaku.danmaku.model.BaseDanmaku;
import master.flame.danmaku.danmaku.model.DanmakuTimer;
import master.flame.danmaku.danmaku.model.IDisplayer;
import master.flame.danmaku.danmaku.model.android.DanmakuContext;
import master.flame.danmaku.ui.widget.DanmakuView;

public class DanPlayer implements DrawHandler.Callback {

    private final DanmakuContext context;
    private final Parser parser;
    private final Loader loader;
    private DanmakuView view;
    private Players player;

    public DanPlayer() {
        parser = new Parser();
        loader = new Loader();
        context = DanmakuContext.create();
        initContext();
    }

    private void initContext() {
        Map<Integer, Integer> lines = new HashMap<>();
        lines.put(BaseDanmaku.TYPE_FIX_TOP, 2);
        lines.put(BaseDanmaku.TYPE_SCROLL_RL, 2);
        lines.put(BaseDanmaku.TYPE_SCROLL_LR, 2);
        lines.put(BaseDanmaku.TYPE_FIX_BOTTOM, 2);
        context.setScaleTextSize(0.8f);
        context.setMaximumLines(lines);
        context.setScrollSpeedFactor(1.2f);
        context.setDanmakuTransparency(0.8f);
        context.setDanmakuMargin(ResUtil.dp2px(8));
        context.setDanmakuStyle(IDisplayer.DANMAKU_STYLE_STROKEN, 3);
    }

    public void setView(DanmakuView view) {
        view.setCallback(this);
        this.view = view;
    }

    public void setPlayer(Players player) {
        context.setDanmakuSync(new Sync(this.player = player));
    }

    private boolean isPrepared() {
        return view != null && view.isPrepared();
    }

    public void seekTo(long time) {
        App.execute(() -> {
            if (!isPrepared()) return;
            view.seekTo(time);
            view.hide();
        });
    }

    public void play() {
        App.execute(() -> {
            if (!isPrepared()) return;
            view.resume();
        });
    }

    public void pause() {
        App.execute(() -> {
            if (!isPrepared()) return;
            view.pause();
        });
    }

    public void stop() {
        App.execute(() -> {
            if (!isPrepared()) return;
            view.stop();
        });
    }

    public void release() {
        App.execute(() -> {
            if (!isPrepared()) return;
            view.release();
        });
    }

    public void setDanmaku(Danmaku item) {
        App.execute(() -> {
            if (view != null) view.release();
            if (item.isEmpty() || view == null) return;
            view.prepare(parser.load(loader.load(item).getDataSource()), context);
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
                if (!isPrepared()) return;
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