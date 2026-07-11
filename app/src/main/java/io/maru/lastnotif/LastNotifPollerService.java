package io.maru.lastnotif;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import org.json.JSONObject;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Foreground service that polls the Maru site's Last.fm endpoints.
 *
 * Polling interval:
 *  - Normal mode  (no lyrics): every 5 seconds (enough to catch song changes fast)
 *  - Lyrics mode             : every 1 second  (we need frequent lyric position updates)
 *
 * What it does each tick:
 *  1. Calls now-playing endpoint
 *  2. If song changed + notifySongUpdate → fire song notification, reset lyric state
 *  3. If interval alert enabled + enough time elapsed → fire interval notification
 *  4. If lyrics enabled + now playing → find current lyric line, fire if changed
 */
public class LastNotifPollerService extends Service {

    public static final String TAG = "LastNotifPoller";
    public static final String ACTION_START = "io.maru.lastnotif.ACTION_START";
    public static final String ACTION_STOP  = "io.maru.lastnotif.ACTION_STOP";

    private static volatile boolean sRunning = false;

    public static boolean isRunning() { return sRunning; }

    private ScheduledExecutorService executor;
    private ScheduledFuture<?> pollFuture;

    private LastNotifStorage        storage;
    private LastNotifNotificationManager notifMgr;

    // Lyric state — cached per track to avoid re-fetching every second
    private String cachedLyricsForTrackKey = "";
    private List<LastNotifSiteClient.LyricLine> cachedLyricLines = null;
    private long   cachedSyncStartedAtMs = 0L;
    private int    lastLyricIndex = -1;

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    @Override
    public void onCreate() {
        super.onCreate();
        sRunning = true;
        Log.i(TAG, "Poller service onCreate");
        storage  = new LastNotifStorage(this);
        storage.setServiceRunning(true);
        notifMgr = new LastNotifNotificationManager(this);
        executor = Executors.newSingleThreadScheduledExecutor();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : "null";
        Log.i(TAG, "Poller service onStartCommand action=" + action);

        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }

        // Start foreground immediately to avoid ANR
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                LastNotifNotificationManager.ID_KEEPALIVE,
                notifMgr.buildKeepaliveNotification(),
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            );
        } else {
            startForeground(
                LastNotifNotificationManager.ID_KEEPALIVE,
                notifMgr.buildKeepaliveNotification()
            );
        }

        schedulePoll();

        // Reschedule the alarm keepalive
        LastNotifPollerAlarmScheduler.schedule(this);

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "Poller service onDestroy");
        sRunning = false;
        if (storage != null) {
            storage.setServiceRunning(false);
        }
        try {
            java.io.File file = new java.io.File(getCacheDir(), "active_track.json");
            if (file.exists()) {
                file.delete();
            }
        } catch (Exception e) {
            // Ignore
        }
        if (pollFuture != null) pollFuture.cancel(false);
        if (executor != null)   executor.shutdown();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    // ─── Polling ──────────────────────────────────────────────────────────────

    private void schedulePoll() {
        if (pollFuture != null) pollFuture.cancel(false);

        boolean lyricsEnabled = storage.isLyricsEnabled();
        long intervalSec = lyricsEnabled ? 1L : 5L;

        pollFuture = executor.scheduleWithFixedDelay(
            this::tick, 0, intervalSec, TimeUnit.SECONDS
        );
    }

    private void tick() {
        String username = storage.getUsername().trim();
        String source = storage.getTrackSource(); // "device", "lastfm", "mixed"

        Log.d(TAG, "Poller service tick. Source=" + source + ", Username=" + username);

        LastNotifMediaMonitor.TrackInfo localTrack = null;
        if ("device".equals(source) || "mixed".equals(source)) {
            localTrack = LastNotifMediaMonitor.getActiveTrack(this);
        }

        boolean useLocal = false;
        String title = "";
        String artist = "";
        String album = "";
        boolean isPlaying = false;

        if ("device".equals(source)) {
            if (localTrack != null) {
                title = localTrack.title;
                artist = localTrack.artist;
                album = localTrack.album;
                isPlaying = localTrack.isPlaying;
                useLocal = true;
                Log.d(TAG, "Device track active: " + title + " - " + artist + " (isPlaying=" + isPlaying + ")");
            } else {
                Log.w(TAG, "Device source selected but no track details or notification permission.");
                writeActiveTrack("", "", "", false, "", "Device");
                return;
            }
        } else if ("mixed".equals(source) && localTrack != null && localTrack.isPlaying) {
            title = localTrack.title;
            artist = localTrack.artist;
            album = localTrack.album;
            isPlaying = localTrack.isPlaying;
            useLocal = true;
            Log.d(TAG, "Mixed source: choosing active device track: " + title + " - " + artist);
        } else {
            // Last.fm or mixed fallback
            if (username.isEmpty()) {
                Log.d(TAG, "Last.fm polling skipped: username is empty.");
                writeActiveTrack("", "", "", false, "", source);
                return;
            }
            LastNotifSiteClient.NowPlayingResult np =
                LastNotifSiteClient.getNowPlaying(username);
            if (np == null) {
                Log.w(TAG, "Last.fm fetch returned null.");
                return;
            }
            title = np.title;
            artist = np.artist;
            album = np.album;
            isPlaying = np.isPlaying;
            Log.d(TAG, "Last.fm source active: " + title + " - " + artist + " (isPlaying=" + isPlaying + ")");
        }

        String trackKey = artist + " - " + title;

        // Resolve human-readable polling method label
        String pollingMethod;
        if (useLocal) {
            pollingMethod = "device".equals(source) ? "Device" : "Mixed→Device";
        } else {
            pollingMethod = "mixed".equals(source) ? "Mixed→Last.fm" : "Last.fm";
        }

        // ── Song-change detection ──────────────────────────────────────────
        String lastKey = storage.getLastTrackKey();
        boolean trackChanged = isPlaying && !trackKey.equals(lastKey);

        if (trackChanged) {
            Log.i(TAG, "Track changed detected: " + trackKey + " (previous: " + lastKey + ")");
            storage.setLastTrackKey(trackKey);

            // Reset lyric cache for new track
            cachedLyricsForTrackKey = "";
            cachedLyricLines = null;
            lastLyricIndex = -1;

            if (storage.isNotifySongUpdate() || (storage.isLyricsEnabled() && !useLocal)) {
                Log.i(TAG, "Posting song change notification alert.");
                notifMgr.postSongAlert(
                    title, artist, album,
                    storage.getNotifMainFormat(),
                    storage.getNotifSubFormat(),
                    pollingMethod
                );
            }
        }

        // ── Interval alert ─────────────────────────────────────────────────
        if (storage.isIntervalEnabled() && isPlaying) {
            long now       = System.currentTimeMillis();
            long lastFired = storage.getLastIntervalNotifAt();
            long intervalMs = storage.getIntervalMinutes() * 60_000L;

            if (now - lastFired >= intervalMs) {
                Log.i(TAG, "Posting interval alert notification.");
                storage.setLastIntervalNotifAt(now);
                notifMgr.postSongAlert(
                    title, artist, album,
                    storage.getNotifMainFormat(),
                    storage.getNotifSubFormat(),
                    pollingMethod
                );
            }
        }

        // ── Lyrics loop ────────────────────────────────────────────────────
        String currentLyric = "";
        if (storage.isLyricsEnabled() && isPlaying && !useLocal) {
            // Fetch/refresh lyrics if track changed
            if (!trackKey.equals(cachedLyricsForTrackKey)) {
                cachedLyricsForTrackKey = trackKey;
                cachedLyricLines = null;
                lastLyricIndex = -1;

                LastNotifSiteClient.LyricsResult lr =
                    LastNotifSiteClient.getLyrics(username);

                if (lr != null && !lr.lines.isEmpty()) {
                    cachedLyricLines       = lr.lines;
                    cachedSyncStartedAtMs  = lr.syncStartedAtMs;
                } else {
                    // No lyrics available — nothing to do this tick
                    writeActiveTrack(title, artist, album, isPlaying, "", pollingMethod);
                    return;
                }
            }

            if (cachedLyricLines == null || cachedLyricLines.isEmpty()) {
                writeActiveTrack(title, artist, album, isPlaying, "", pollingMethod);
                return;
            }

            // Compute current position using the server-anchored sync time
            long posMs = System.currentTimeMillis() - cachedSyncStartedAtMs;
            if (posMs < 0) posMs = 0;

            int activeIndex = findLyricIndex(cachedLyricLines, posMs);

            if (activeIndex >= 0) {
                currentLyric = cachedLyricLines.get(activeIndex).text;
                if (activeIndex != lastLyricIndex) {
                    lastLyricIndex = activeIndex;
                    if (!currentLyric.isEmpty()) {
                        notifMgr.postLyricAlert(currentLyric, title, artist);
                    }
                }
            }
        }

        writeActiveTrack(title, artist, album, isPlaying, currentLyric, pollingMethod);
    }

    /** Returns the index of the lyric line active at posMs, or -1 if before first line */
    private static int findLyricIndex(
            List<LastNotifSiteClient.LyricLine> lines, long posMs) {
        int idx = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).timestampMs <= posMs) idx = i;
            else break;
        }
        return idx;
    }

    private void writeActiveTrack(String title, String artist, String album, boolean isPlaying, String lyricLine, String pollingMethod) {
        try {
            java.io.File file = new java.io.File(getCacheDir(), "active_track.json");
            JSONObject json = new JSONObject();
            json.put("title", title);
            json.put("artist", artist);
            json.put("album", album);
            json.put("isPlaying", isPlaying);
            json.put("lyricLine", lyricLine != null ? lyricLine : "");
            json.put("pollingMethod", pollingMethod != null ? pollingMethod : "");
            json.put("timestamp", System.currentTimeMillis());

            java.io.FileWriter writer = new java.io.FileWriter(file);
            writer.write(json.toString());
            writer.close();
        } catch (Exception e) {
            Log.e(TAG, "Error writing active track json", e);
        }
    }

    // ─── Static helpers ───────────────────────────────────────────────────────

    public static void start(Context ctx) {
        try {
            Intent i = new Intent(ctx, LastNotifPollerService.class);
            i.setAction(ACTION_START);
            ctx.startForegroundService(i);
        } catch (Exception e) {
            Log.e(TAG, "Failed to start foreground service", e);
        }
    }

    public static void stop(Context ctx) {
        Intent i = new Intent(ctx, LastNotifPollerService.class);
        i.setAction(ACTION_STOP);
        ctx.startService(i);
    }
}
