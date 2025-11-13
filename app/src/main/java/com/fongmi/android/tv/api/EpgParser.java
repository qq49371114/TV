package com.fongmi.android.tv.api;

import android.net.Uri;

import com.fongmi.android.tv.bean.Channel;
import com.fongmi.android.tv.bean.Epg;
import com.fongmi.android.tv.bean.EpgData;
import com.fongmi.android.tv.bean.Live;
import com.fongmi.android.tv.bean.Tv;
import com.fongmi.android.tv.utils.Download;
import com.fongmi.android.tv.utils.FileUtil;
import com.github.catvod.utils.Path;
import com.github.catvod.utils.Trans;

import org.simpleframework.xml.core.Persister;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.AbstractMap;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class EpgParser {

    private static final SimpleDateFormat formatTime = new SimpleDateFormat("HH:mm", Locale.getDefault());
    private static final SimpleDateFormat formatDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
    private static final SimpleDateFormat formatFull = new SimpleDateFormat("yyyyMMddHHmmss Z", Locale.getDefault());

    public static boolean start(Live live, String url) throws Exception {
        File file = Path.epg(Uri.parse(url).getLastPathSegment());
        if (shouldDownload(file)) Download.create(url, file).start();
        if (file.getName().endsWith(".gz")) readGzip(live, file);
        else readXml(live, file);
        return true;
    }

    public static Epg getEpg(String xml, String key) throws Exception {
        Tv tv = new Persister().read(Tv.class, xml, false);
        Epg epg = Epg.create(key, formatDate.format(parse(formatFull, tv.getDate())));
        tv.getProgramme().forEach(programme -> epg.getList().add(getEpgData(programme)));
        return epg;
    }

    private static boolean shouldDownload(File file) {
        return !Path.exists(file) || !isToday(file.lastModified()) || System.currentTimeMillis() - file.lastModified() > TimeUnit.HOURS.toMillis(6);
    }

    private static boolean isToday(long millis) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(millis);
        return calendar.get(Calendar.DAY_OF_MONTH) == Calendar.getInstance().get(Calendar.DAY_OF_MONTH);
    }

    private static void readGzip(Live live, File file) throws Exception {
        File xml = Path.epg(file.getName().replace(".gz", ""));
        if (!xml.exists()) FileUtil.gzipDecompress(file, xml);
        readXml(live, xml);
    }

    private static void readXml(Live live, File file) throws Exception {
        Map<String, Channel> liveChannelMap = prepareLiveChannels(live);
        XmlData xmlData = parseXmlData(file);
        String today = formatDate.format(new Date());
        bindResultsToLive(live, processProgramme(xmlData, liveChannelMap, today));
    }

    private static Map<String, Channel> prepareLiveChannels(Live live) {
        return live.getGroups().stream()
                .flatMap(group -> group.getChannel().stream())
                .flatMap(channel -> Stream.of(channel.getTvgId(), channel.getTvgName(), channel.getName()).filter(key -> !key.isEmpty()).map(key -> new AbstractMap.SimpleEntry<>(key, channel)))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (oldValue, newValue) -> oldValue, HashMap::new));
    }

    private static XmlData parseXmlData(File file) throws Exception {
        Tv tv = new Persister().read(Tv.class, file, false);
        Map<String, Tv.Channel> map = tv.getChannel().stream().collect(Collectors.toMap(Tv.Channel::getId, channel -> channel));
        return new XmlData(tv, map);
    }

    private static ProgrammeResult processProgramme(XmlData data, Map<String, Channel> liveChannelMap, String today) {
        Map<String, Epg> epgMap = new HashMap<>();
        Map<String, String> srcMap = new HashMap<>();
        for (Tv.Programme programme : data.tv.getProgramme()) {
            String xmlChannelId = programme.getChannel();
            Channel targetChannel = findTargetChannel(xmlChannelId, liveChannelMap, data.map);
            if (targetChannel == null) continue;
            Date startDate = parse(formatFull, programme.getStart());
            if (!isToday(startDate.getTime())) continue;
            String liveTvgId = targetChannel.getTvgId();
            Date endDate = parse(formatFull, programme.getStop());
            epgMap.computeIfAbsent(liveTvgId, key -> Epg.create(key, today)).getList().add(getEpgData(startDate, endDate, programme));
            Optional.ofNullable(data.map.get(xmlChannelId)).filter(Tv.Channel::hasSrc).ifPresent(ch -> srcMap.putIfAbsent(liveTvgId, ch.getSrc()));
        }
        return new ProgrammeResult(epgMap, srcMap);
    }

    private static Channel findTargetChannel(String xmlChannelId, Map<String, Channel> liveChannelMap, Map<String, Tv.Channel> xmlChannelIdMap) {
        Channel targetChannel = liveChannelMap.get(xmlChannelId);
        if (targetChannel != null) return targetChannel;
        return Optional.ofNullable(xmlChannelIdMap.get(xmlChannelId)).flatMap(xmlChannel -> xmlChannel.getDisplayName().stream()
                .map(Tv.DisplayName::getText).filter(name -> !name.isEmpty()).filter(liveChannelMap::containsKey)
                .findFirst().map(liveChannelMap::get)).orElse(null);
    }

    private static void bindResultsToLive(Live live, ProgrammeResult result) {
        live.getGroups().stream()
                .flatMap(group -> group.getChannel().stream())
                .forEach(channel -> {
                    String tvgId = channel.getTvgId();
                    Optional.ofNullable(result.epgMap.get(tvgId)).ifPresent(channel::setData);
                    Optional.ofNullable(result.srcMap.get(tvgId)).ifPresent(channel::setLogo);
                });
    }

    private static EpgData getEpgData(Tv.Programme programme) {
        Date startDate = parse(formatFull, programme.getStart());
        Date endDate = parse(formatFull, programme.getStop());
        return getEpgData(startDate, endDate, programme);
    }

    private static EpgData getEpgData(Date startDate, Date endDate, Tv.Programme programme) {
        try {
            EpgData epgData = new EpgData();
            epgData.setTitle(Trans.s2t(programme.getTitle()));
            epgData.setStart(formatTime.format(startDate));
            epgData.setEnd(formatTime.format(endDate));
            epgData.setStartTime(startDate.getTime());
            epgData.setEndTime(endDate.getTime());
            return epgData;
        } catch (Exception e) {
            return new EpgData();
        }
    }

    private static Date parse(SimpleDateFormat format, String source) {
        try {
            return format.parse(source);
        } catch (Exception e) {
            return new Date(0);
        }
    }

    private static class XmlData {

        Tv tv;
        Map<String, Tv.Channel> map;

        public XmlData(Tv tv, Map<String, Tv.Channel> map) {
            this.tv = tv;
            this.map = map;
        }
    }

    private static class ProgrammeResult {

        Map<String, Epg> epgMap;
        Map<String, String> srcMap;

        public ProgrammeResult(Map<String, Epg> epgMap, Map<String, String> srcMap) {
            this.epgMap = epgMap;
            this.srcMap = srcMap;
        }
    }
}