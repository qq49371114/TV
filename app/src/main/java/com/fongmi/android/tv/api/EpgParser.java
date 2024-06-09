package com.fongmi.android.tv.api;

import android.net.Uri;

import com.fongmi.android.tv.bean.Channel;
import com.fongmi.android.tv.bean.Epg;
import com.fongmi.android.tv.bean.EpgData;
import com.fongmi.android.tv.bean.Group;
import com.fongmi.android.tv.bean.Live;
import com.fongmi.android.tv.bean.Tv;
import com.fongmi.android.tv.utils.Download;
import com.fongmi.android.tv.utils.FileUtil;
import com.github.catvod.utils.Path;
import com.github.catvod.utils.Trans;

import org.simpleframework.xml.core.Persister;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class EpgParser {

    private static final SimpleDateFormat formatFull = new SimpleDateFormat("yyyyMMddHHmmss");
    private static final SimpleDateFormat formatDate = new SimpleDateFormat("yyyy-MM-dd");
    private static final SimpleDateFormat formatTime = new SimpleDateFormat("HH:mm");

    public static void start(Live live) {
        try {
            if (!live.getEpg().endsWith(".xml") && !live.getEpg().endsWith(".gz")) return;
            File file = Path.epg(Uri.parse(live.getEpg()).getLastPathSegment());
            if (shouldDownload(file)) Download.create(live.getEpg(), file).start();
            if (file.getName().endsWith(".gz")) readGzip(live, file);
            else readXml(live, file);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static boolean shouldDownload(File file) {
        return !file.exists() || !equalToday(file);
    }

    private static boolean equalToday(File file) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(file.lastModified());
        return calendar.get(Calendar.DAY_OF_MONTH) == Calendar.getInstance().get(Calendar.DAY_OF_MONTH);
    }

    private static Date parseDateTime(String text) throws Exception {
        return formatFull.parse(text.substring(0, 14));
    }

    private static void readGzip(Live live, File file) throws Exception {
        File xml = Path.epg(file.getName().replace(".gz", ""));
        if (!xml.exists()) FileUtil.extractGzip(file, xml);
        readXml(live, xml);
    }

    private static void readXml(Live live, File file) throws Exception {
        Set<String> exist = new HashSet<>();
        Map<String, Epg> epgMap = new HashMap<>();
        Map<String, String> mapping = new HashMap<>();
        String today = formatDate.format(new Date());
        Tv tv = new Persister().read(Tv.class, Path.read(file));
        for (Group group : live.getGroups()) for (Channel channel : group.getChannel()) exist.add(channel.getTvgName());
        for (Tv.Channel channel : tv.getChannel()) mapping.put(channel.getId(), channel.getDisplayName());
        for (Tv.Programme programme : tv.getProgramme()) {
            String key = mapping.get(programme.getChannel());
            if (!exist.contains(key)) continue;
            if (!programme.equals(today)) continue;
            if (!epgMap.containsKey(key)) epgMap.put(key, Epg.create(key, today));
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
            epgData.setTitle(Trans.s2t(title));
            epgMap.get(key).getList().add(epgData);
        }
        for (Group group : live.getGroups()) {
            for (Channel channel : group.getChannel()) {
                channel.setData(epgMap.get(channel.getTvgName()));
            }
        }
    }
}