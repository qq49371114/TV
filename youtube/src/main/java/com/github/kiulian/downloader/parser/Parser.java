package com.github.kiulian.downloader.parser;

import com.github.kiulian.downloader.downloader.request.RequestVideoInfo;
import com.github.kiulian.downloader.downloader.response.Response;
import com.github.kiulian.downloader.model.videos.VideoInfo;

public interface Parser {

    Response<VideoInfo> parseVideo(RequestVideoInfo request);
}
