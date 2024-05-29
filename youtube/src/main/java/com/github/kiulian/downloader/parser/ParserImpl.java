package com.github.kiulian.downloader.parser;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.github.kiulian.downloader.Config;
import com.github.kiulian.downloader.YoutubeException;
import com.github.kiulian.downloader.cipher.Cipher;
import com.github.kiulian.downloader.cipher.CipherFactory;
import com.github.kiulian.downloader.downloader.Downloader;
import com.github.kiulian.downloader.downloader.YoutubeCallback;
import com.github.kiulian.downloader.downloader.request.RequestVideoInfo;
import com.github.kiulian.downloader.downloader.request.RequestWebpage;
import com.github.kiulian.downloader.downloader.response.Response;
import com.github.kiulian.downloader.downloader.response.ResponseImpl;
import com.github.kiulian.downloader.extractor.Extractor;
import com.github.kiulian.downloader.model.subtitles.SubtitlesInfo;
import com.github.kiulian.downloader.model.videos.VideoDetails;
import com.github.kiulian.downloader.model.videos.VideoInfo;
import com.github.kiulian.downloader.model.videos.formats.AudioFormat;
import com.github.kiulian.downloader.model.videos.formats.Format;
import com.github.kiulian.downloader.model.videos.formats.Itag;
import com.github.kiulian.downloader.model.videos.formats.VideoFormat;
import com.github.kiulian.downloader.model.videos.formats.VideoWithAudioFormat;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

public class ParserImpl implements Parser {

    private static final String ANDROID_APIKEY = "AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8";

    private final Config config;
    private final Downloader downloader;
    private final Extractor extractor;
    private final CipherFactory cipherFactory;

    public ParserImpl(Config config, Downloader downloader, Extractor extractor, CipherFactory cipherFactory) {
        this.config = config;
        this.downloader = downloader;
        this.extractor = extractor;
        this.cipherFactory = cipherFactory;
    }

    @Override
    public Response<VideoInfo> parseVideo(RequestVideoInfo request) {
        if (request.isAsync()) {
            ExecutorService executorService = config.getExecutorService();
            Future<VideoInfo> result = executorService.submit(() -> parseVideo(request.getVideoId(), request.getCallback()));
            return ResponseImpl.fromFuture(result);
        }
        try {
            VideoInfo result = parseVideo(request.getVideoId(), request.getCallback());
            return ResponseImpl.from(result);
        } catch (YoutubeException e) {
            return ResponseImpl.error(e);
        }
    }

    private VideoInfo parseVideo(String videoId, YoutubeCallback<VideoInfo> callback) throws YoutubeException {
        // try to spoof android
        // workaround for issue https://github.com/sealedtx/java-youtube-downloader/issues/97
        VideoInfo videoInfo = parseVideoAndroid(videoId, callback);
        if (videoInfo == null) {
            videoInfo = parseVideoWeb(videoId, callback);
        }
        if (callback != null) {
            callback.onFinished(videoInfo);
        }
        return videoInfo;
    }

    private VideoInfo parseVideoAndroid(String videoId, YoutubeCallback<VideoInfo> callback) throws YoutubeException {
        String url = "https://youtubei.googleapis.com/youtubei/v1/player?key=" + ANDROID_APIKEY;
        String body = "{" +
                "  \"videoId\": \"" + videoId + "\"," +
                "  \"context\": {" +
                "    \"client\": {" +
                "      \"hl\": \"en\"," +
                "      \"gl\": \"US\"," +
                "      \"clientName\": \"ANDROID_TESTSUITE\"," +
                "      \"clientVersion\": \"1.9\"," +
                "      \"androidSdkVersion\": 31" +
                "    }" +
                "  }" +
                "}";
        RequestWebpage request = new RequestWebpage(url, "POST", body).header("Content-Type", "application/json");
        Response<String> response = downloader.downloadWebpage(request);
        if (!response.ok()) {
            return null;
        }
        JSONObject playerResponse;
        try {
            playerResponse = JSONObject.parseObject(response.data());
        } catch (Exception ignore) {
            return null;
        }
        VideoDetails videoDetails = parseVideoDetails(videoId, playerResponse);
        if (videoDetails.isDownloadable()) {
            JSONObject context = playerResponse.getJSONObject("responseContext");
            String clientVersion = extractor.extractClientVersionFromContext(context);
            List<Format> formats;
            try {
                formats = parseFormats(playerResponse, null, clientVersion);
            } catch (YoutubeException e) {
                if (callback != null) callback.onError(e);
                throw e;
            }
            List<SubtitlesInfo> subtitlesInfo = parseCaptions(playerResponse);
            return new VideoInfo(videoDetails, formats, subtitlesInfo);
        } else {
            return new VideoInfo(videoDetails, Collections.emptyList(), Collections.emptyList());
        }
    }

    private VideoInfo parseVideoWeb(String videoId, YoutubeCallback<VideoInfo> callback) throws YoutubeException {
        String htmlUrl = "https://www.youtube.com/watch?v=" + videoId;
        Response<String> response = downloader.downloadWebpage(new RequestWebpage(htmlUrl));
        if (!response.ok()) {
            YoutubeException e = new YoutubeException.DownloadException(String.format("Could not load url: %s, exception: %s", htmlUrl, response.error().getMessage()));
            if (callback != null) callback.onError(e);
            throw e;
        }
        String html = response.data();
        JSONObject playerConfig;
        try {
            playerConfig = extractor.extractPlayerConfigFromHtml(html);
        } catch (YoutubeException e) {
            if (callback != null) callback.onError(e);
            throw e;
        }
        JSONObject args = playerConfig.getJSONObject("args");
        JSONObject playerResponse = args.getJSONObject("player_response");
        if (!playerResponse.containsKey("streamingData") && !playerResponse.containsKey("videoDetails")) {
            YoutubeException e = new YoutubeException.BadPageException("streamingData and videoDetails not found");
            if (callback != null) callback.onError(e);
            throw e;
        }
        VideoDetails videoDetails = parseVideoDetails(videoId, playerResponse);
        if (videoDetails.isDownloadable()) {
            String jsUrl;
            try {
                jsUrl = extractor.extractJsUrlFromConfig(playerConfig, videoId);
            } catch (YoutubeException e) {
                if (callback != null) callback.onError(e);
                throw e;
            }
            JSONObject context = playerConfig.getJSONObject("args").getJSONObject("player_response").getJSONObject("responseContext");
            String clientVersion = extractor.extractClientVersionFromContext(context);
            List<Format> formats;
            try {
                formats = parseFormats(playerResponse, jsUrl, clientVersion);
            } catch (YoutubeException e) {
                if (callback != null) callback.onError(e);
                throw e;
            }
            List<SubtitlesInfo> subtitlesInfo = parseCaptions(playerResponse);
            return new VideoInfo(videoDetails, formats, subtitlesInfo);
        } else {
            return new VideoInfo(videoDetails, Collections.emptyList(), Collections.emptyList());
        }
    }

    private VideoDetails parseVideoDetails(String videoId, JSONObject playerResponse) {
        if (!playerResponse.containsKey("videoDetails")) {
            return new VideoDetails(videoId);
        }
        JSONObject videoDetails = playerResponse.getJSONObject("videoDetails");
        String liveHLSUrl = null;
        if (videoDetails.getBooleanValue("isLive")) {
            if (playerResponse.containsKey("streamingData")) {
                liveHLSUrl = playerResponse.getJSONObject("streamingData").getString("hlsManifestUrl");
            }
        }
        return new VideoDetails(videoDetails, liveHLSUrl);
    }

    private List<Format> parseFormats(JSONObject playerResponse, String jsUrl, String clientVersion) throws YoutubeException {
        if (!playerResponse.containsKey("streamingData")) {
            throw new YoutubeException.BadPageException("streamingData not found");
        }
        JSONObject streamingData = playerResponse.getJSONObject("streamingData");
        JSONArray jsonFormats = new JSONArray();
        if (streamingData.containsKey("formats")) {
            jsonFormats.addAll(streamingData.getJSONArray("formats"));
        }
        JSONArray jsonAdaptiveFormats = new JSONArray();
        if (streamingData.containsKey("adaptiveFormats")) {
            jsonAdaptiveFormats.addAll(streamingData.getJSONArray("adaptiveFormats"));
        }
        List<Format> formats = new ArrayList<>(jsonFormats.size() + jsonAdaptiveFormats.size());
        populateFormats(formats, jsonFormats, jsUrl, false, clientVersion);
        populateFormats(formats, jsonAdaptiveFormats, jsUrl, true, clientVersion);
        return formats;
    }

    private void populateFormats(List<Format> formats, JSONArray jsonFormats, String jsUrl, boolean isAdaptive, String clientVersion) throws YoutubeException.CipherException {
        for (int i = 0; i < jsonFormats.size(); i++) {
            JSONObject json = jsonFormats.getJSONObject(i);
            if ("FORMAT_STREAM_TYPE_OTF".equals(json.getString("type"))) continue; // unsupported otf formats which cause 404 not found
            int itagValue = json.getIntValue("itag");
            Itag itag;
            try {
                itag = Itag.valueOf("i" + itagValue);
            } catch (IllegalArgumentException e) {
                System.err.println("Error parsing format: unknown itag " + itagValue);
                continue;
            }
            try {
                Format format = parseFormat(json, jsUrl, itag, isAdaptive, clientVersion);
                formats.add(format);
            } catch (YoutubeException.CipherException e) {
                throw e;
            } catch (YoutubeException e) {
                System.err.println("Error " + e.getMessage() + " parsing format: " + json);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private Format parseFormat(JSONObject json, String jsUrl, Itag itag, boolean isAdaptive, String clientVersion) throws YoutubeException {
        if (json.containsKey("signatureCipher")) {
            JSONObject jsonCipher = new JSONObject();
            String[] cipherData = json.getString("signatureCipher").replace("\\u0026", "&").split("&");
            for (String s : cipherData) {
                String[] keyValue = s.split("=");
                jsonCipher.put(keyValue[0], keyValue[1]);
            }
            if (!jsonCipher.containsKey("url")) {
                throw new YoutubeException.BadPageException("Could not found url in cipher data");
            }
            String urlWithSig = jsonCipher.getString("url");
            try {
                urlWithSig = URLDecoder.decode(urlWithSig, "UTF-8");
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
            if (urlWithSig.contains("signature") || (!jsonCipher.containsKey("s") && (urlWithSig.contains("&sig=") || urlWithSig.contains("&lsig=")))) {
                // do nothing, this is pre-signed videos with signature
            } else if (jsUrl != null) {
                String s = jsonCipher.getString("s");
                try {
                    s = URLDecoder.decode(s, "UTF-8");
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
                Cipher cipher = cipherFactory.createCipher(jsUrl);
                String signature = cipher.getSignature(s);
                String decipheredUrl = urlWithSig + "&sig=" + signature;
                json.put("url", decipheredUrl);
            } else {
                throw new YoutubeException.BadPageException("deciphering is required but no js url");
            }
        }

        boolean hasVideo = itag.isVideo() || json.containsKey("size") || json.containsKey("width");
        boolean hasAudio = itag.isAudio() || json.containsKey("audioQuality");
        if (hasVideo && hasAudio) return new VideoWithAudioFormat(json, isAdaptive, clientVersion);
        else if (hasVideo) return new VideoFormat(json, isAdaptive, clientVersion);
        return new AudioFormat(json, isAdaptive, clientVersion);
    }

    private List<SubtitlesInfo> parseCaptions(JSONObject playerResponse) {
        if (!playerResponse.containsKey("captions")) {
            return Collections.emptyList();
        }
        JSONObject captions = playerResponse.getJSONObject("captions");
        JSONObject playerCaptionsTracklistRenderer = captions.getJSONObject("playerCaptionsTracklistRenderer");
        if (playerCaptionsTracklistRenderer == null || playerCaptionsTracklistRenderer.isEmpty()) {
            return Collections.emptyList();
        }
        JSONArray captionsArray = playerCaptionsTracklistRenderer.getJSONArray("captionTracks");
        if (captionsArray == null || captionsArray.isEmpty()) {
            return Collections.emptyList();
        }
        List<SubtitlesInfo> subtitlesInfo = new ArrayList<>();
        for (int i = 0; i < captionsArray.size(); i++) {
            JSONObject subtitleInfo = captionsArray.getJSONObject(i);
            String language = subtitleInfo.getString("languageCode");
            String url = subtitleInfo.getString("baseUrl");
            String vssId = subtitleInfo.getString("vssId");
            if (language != null && url != null && vssId != null) {
                boolean isAutoGenerated = vssId.startsWith("a.");
                subtitlesInfo.add(new SubtitlesInfo(url, language, isAutoGenerated, true));
            }
        }
        return subtitlesInfo;
    }
}
