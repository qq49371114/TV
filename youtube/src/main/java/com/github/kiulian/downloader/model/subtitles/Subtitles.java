package com.github.kiulian.downloader.model.subtitles;



import com.github.kiulian.downloader.model.Extension;


public class Subtitles {

    private final String url;
    private final boolean fromCaptions;
    private Extension format;
    private String translationLanguage;

    Subtitles(String url, boolean fromCaptions) {
        this.url = url;
        this.fromCaptions = fromCaptions;
    }

    public Subtitles formatTo(Extension extension) {
        this.format = extension;
        return this;
    }

    public Subtitles translateTo(String language) {
        // currently translation is supported only for subtitles from captions
        if (fromCaptions) {
            this.translationLanguage = language;
        }
        return this;
    }

    public String getDownloadUrl() {
        String downloadUrl = url;
        if (format != null && format.isSubtitle()) {
            downloadUrl += "&fmt=" + format.value();
        }
        if (translationLanguage != null && !translationLanguage.isEmpty()) {
            downloadUrl += "&tlang=" + translationLanguage;
        }
        return downloadUrl;
    }

}
