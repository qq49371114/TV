package com.fongmi.android.tv.db;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.Setting;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.bean.Device;
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.bean.Keep;
import com.fongmi.android.tv.bean.Live;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.Track;
import com.fongmi.android.tv.db.dao.ConfigDao;
import com.fongmi.android.tv.db.dao.DeviceDao;
import com.fongmi.android.tv.db.dao.HistoryDao;
import com.fongmi.android.tv.db.dao.KeepDao;
import com.fongmi.android.tv.db.dao.LiveDao;
import com.fongmi.android.tv.db.dao.SiteDao;
import com.fongmi.android.tv.db.dao.TrackDao;
import com.github.catvod.utils.Path;
import com.github.catvod.utils.Prefers;

import java.io.File;

@Database(entities = {Keep.class, Site.class, Live.class, Track.class, Config.class, Device.class, History.class}, version = AppDatabase.VERSION)
public abstract class AppDatabase extends RoomDatabase {

    public static final int VERSION = 32;
    public static final String NAME = "tv";
    public static final String SYMBOL = "@@@";

    private static volatile AppDatabase instance;

    public static synchronized AppDatabase get() {
        if (instance == null) instance = create(App.get());
        return instance;
    }

    public static File getBackup() {
        return new File(Path.tv(), NAME);
    }

    public static void backup() {
        if (Setting.getBackupMode() == 0) backup(new com.fongmi.android.tv.impl.Callback());
    }

    public static void backup(com.fongmi.android.tv.impl.Callback callback) {
        App.execute(() -> {
            File db = App.get().getDatabasePath(NAME).getAbsoluteFile();
            File wal = App.get().getDatabasePath(NAME + "-wal").getAbsoluteFile();
            File shm = App.get().getDatabasePath(NAME + "-shm").getAbsoluteFile();
            if (db.exists()) Path.copy(db, new File(Path.tv(), db.getName()));
            if (wal.exists()) Path.copy(wal, new File(Path.tv(), wal.getName()));
            if (shm.exists()) Path.copy(shm, new File(Path.tv(), shm.getName()));
            Prefers.backup(new File(Path.tv(), NAME + "-pref"));
            App.post(callback::success);
        });
    }

    public static void restore(com.fongmi.android.tv.impl.Callback callback) {
        App.execute(() -> {
            File db = new File(Path.tv(), NAME);
            File wal = new File(Path.tv(), NAME + "-wal");
            File shm = new File(Path.tv(), NAME + "-shm");
            File pref = new File(Path.tv(), NAME + "-pref");
            if (db.exists()) Path.copy(db, App.get().getDatabasePath(db.getName()).getAbsoluteFile());
            if (wal.exists()) Path.copy(wal, App.get().getDatabasePath(wal.getName()).getAbsoluteFile());
            if (shm.exists()) Path.copy(shm, App.get().getDatabasePath(shm.getName()).getAbsoluteFile());
            if (pref.exists()) Prefers.restore(pref);
            App.post(callback::success);
        });
    }

    private static AppDatabase create(Context context) {
        return Room.databaseBuilder(context, AppDatabase.class, NAME)
                .addMigrations(Migrations.MIGRATION_30_31)
                .addMigrations(Migrations.MIGRATION_31_32)
                .allowMainThreadQueries().fallbackToDestructiveMigration().build();
    }

    public abstract KeepDao getKeepDao();

    public abstract SiteDao getSiteDao();

    public abstract LiveDao getLiveDao();

    public abstract TrackDao getTrackDao();

    public abstract ConfigDao getConfigDao();

    public abstract DeviceDao getDeviceDao();

    public abstract HistoryDao getHistoryDao();
}
