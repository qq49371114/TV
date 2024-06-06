package com.fongmi.android.tv.api;

import android.net.Uri;

import com.fongmi.android.tv.bean.Channel;
import com.fongmi.android.tv.bean.Epg;
import com.fongmi.android.tv.bean.EpgData;
import com.fongmi.android.tv.bean.Group;
import com.fongmi.android.tv.bean.Live;
import com.fongmi.android.tv.bean.Tv;
import com.fongmi.android.tv.utils.Download;
import com.github.catvod.utils.Path;

import org.simpleframework.xml.core.Persister;

import java.io.File;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class EpgParser {

    private static final SimpleDateFormat formatFull = new SimpleDateFormat("yyyyMMddHHmmss");
    private static final SimpleDateFormat formatDate = new SimpleDateFormat("yyyy-MM-dd");
    private static final SimpleDateFormat formatTime = new SimpleDateFormat("HH:mm");

    public static void start(Live live) throws Exception {
        if (!live.getEpg().contains(".xml") || live.getEpg().contains("{")) return;
        File file = Path.cache(Uri.parse(live.getEpg()).getLastPathSegment());
        if (shouldDownload(file)) Download.create(live.getEpg(), file).start();
        readXml(live, Path.read(file));
    }

    private static boolean shouldDownload(File file) {
        return !file.exists() || isOverOneDay(file);
    }

    private static boolean isOverOneDay(File file) {
        long lastModified = file.lastModified();
        long currentTimeMillis = System.currentTimeMillis();
        return currentTimeMillis - lastModified > 1000 * 60 * 60 * 24;
    }

    private static Date parseDateTime(String text) throws ParseException {
        return formatFull.parse(text.substring(0, 14));
    }

    private static void readXml(Live live, String xml) throws Exception {
        Set<String> exist = new HashSet<>();
        Map<String, Epg> epgMap = new HashMap<>();
        Map<String, String> mapping = new HashMap<>();
        Tv tv = new Persister().read(Tv.class, xml);
        for (Group group : live.getGroups()) for (Channel channel : group.getChannel()) exist.add(channel.getTvgName());
        for (Tv.Channel channel : tv.getChannel()) mapping.put(channel.getId(), channel.getDisplayName());
        for (Tv.Programme programme : tv.getProgramme()) {
            String key = mapping.get(programme.getChannel());
            if (!exist.contains(key)) continue;
            String title = programme.getTitle();
            String start = programme.getStart();
            String stop = programme.getStop();
            Date startDate = parseDateTime(start);
            Date endDate = parseDateTime(stop);
            EpgData epgData = new EpgData();
            epgData.setStart(formatTime.format(startDate));
            epgData.setEnd(formatTime.format(endDate));
            epgData.setStartTime(startDate.getTime());
            epgData.setEndTime(endDate.getTime());
            epgData.setTitle(title);
            Epg epg = epgMap.get(key);
            if (epg == null) epgMap.put(key, epg = Epg.create(key, formatDate.format(startDate)));
            epg.getList().add(epgData);
        }
        for (Group group : live.getGroups()) {
            for (Channel channel : group.getChannel()) {
                channel.setData(epgMap.get(channel.getTvgName()));
            }
        }
    }
}