package com.github.kiulian.downloader;

import com.github.kiulian.downloader.cipher.CachedCipherFactory;
import com.github.kiulian.downloader.downloader.Downloader;
import com.github.kiulian.downloader.downloader.DownloaderImpl;
import com.github.kiulian.downloader.downloader.request.RequestVideoInfo;
import com.github.kiulian.downloader.downloader.response.Response;
import com.github.kiulian.downloader.extractor.ExtractorImpl;
import com.github.kiulian.downloader.model.videos.VideoInfo;
import com.github.kiulian.downloader.parser.Parser;
import com.github.kiulian.downloader.parser.ParserImpl;

import okhttp3.OkHttpClient;

public class YoutubeDownloader {

    private final Downloader downloader;
    private final Config config;
    private final Parser parser;

    public YoutubeDownloader(OkHttpClient client) {
        this(Config.buildDefault(), client);
    }

    public YoutubeDownloader(Config config, OkHttpClient client) {
        this.downloader = new DownloaderImpl(this.config = config, client);
        this.parser = new ParserImpl(config, downloader, new ExtractorImpl(downloader), new CachedCipherFactory(downloader));
    }

    public Config getConfig() {
        return config;
    }

    public Response<VideoInfo> getVideoInfo(RequestVideoInfo request) {
        return parser.parseVideo(request);
    }
}
