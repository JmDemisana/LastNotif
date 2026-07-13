package io.maru.lastnotif;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import androidx.core.app.NotificationCompat;

/**
 * Manages the two notification channels and posts/replaces song/lyric alerts.
 *
 * Channel "lastnotif_alerts"    — HIGH importance, for song/lyric pings
 * Channel "lastnotif_keepalive" — MIN importance, silent, for the foreground notif
 *
 * All song/lyric notifications share ID 13001 so they replace each other
 * instead of stacking in the shade.
 */
public class LastNotifNotificationManager {

    public static final String CHANNEL_ALERTS    = "lastnotif_alerts";
    public static final String CHANNEL_KEEPALIVE = "lastnotif_keepalive";
    public static final int    ID_ALERT          = 13001;
    public static final int    ID_KEEPALIVE      = 13000;

    private final Context ctx;
    private final NotificationManager nm;

    public LastNotifNotificationManager(Context context) {
        this.ctx = context.getApplicationContext();
        this.nm  = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        createChannels();
    }

    // ─── Channel setup ────────────────────────────────────────────────────────

    private void createChannels() {
        // Song/lyric alerts — high importance so they pop up
        NotificationChannel alerts = new NotificationChannel(
            CHANNEL_ALERTS,
            ctx.getString(R.string.notif_channel_alerts),
            NotificationManager.IMPORTANCE_HIGH
        );
        alerts.setDescription(ctx.getString(R.string.notif_channel_alerts_desc));
        alerts.setShowBadge(true);
        nm.createNotificationChannel(alerts);

        // Keepalive — silent, no badge, minimal shade presence
        NotificationChannel keepalive = new NotificationChannel(
            CHANNEL_KEEPALIVE,
            ctx.getString(R.string.notif_channel_keepalive),
            NotificationManager.IMPORTANCE_MIN
        );
        keepalive.setDescription(ctx.getString(R.string.notif_channel_keepalive_desc));
        keepalive.setShowBadge(false);
        keepalive.setSound(null, null);
        nm.createNotificationChannel(keepalive);
    }

    // ─── Keepalive (foreground service notification) ──────────────────────────

    public Notification buildKeepaliveNotification() {
        PendingIntent tapIntent = PendingIntent.getActivity(
            ctx, 0,
            new Intent(ctx, MainActivity.class),
            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );

        return new NotificationCompat.Builder(ctx, CHANNEL_KEEPALIVE)
            .setSmallIcon(R.mipmap.ic_launcher_lastnotif_legacy)
            .setContentTitle(ctx.getString(R.string.notif_keepalive_title))
            .setContentText(ctx.getString(R.string.notif_keepalive_text))
            .setContentIntent(tapIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build();
    }

    // ─── Song alert ───────────────────────────────────────────────────────────

    /**
     * Posts/replaces the song alert notification.
     * Token-replaces {song_name}, {artist}, {album}, {polling_method} in the format strings.
     */
    public void postSongAlert(String title, String artist, String album,
                              String mainFmt, String subFmt, String pollingMethod) {
        String mainText = applyFormat(mainFmt, title, artist, album, pollingMethod);
        String subText  = applyFormat(subFmt,  title, artist, album, pollingMethod);
        post(mainText, subText);
    }

    // ─── Lyric alert ──────────────────────────────────────────────────────────

    /**
     * Posts/replaces the notification with the current lyric line as main text.
     * Uses artist as sub-text for context.
     */
    public void postLyricAlert(String lyricLine, String title, String artist) {
        String subText = title.isEmpty() ? artist : title + " — " + artist;
        post(lyricLine.isEmpty() ? "♪" : lyricLine, subText);
    }

    // ─── Internal ─────────────────────────────────────────────────────────────

    private void post(String main, String sub) {
        PendingIntent tapIntent = PendingIntent.getActivity(
            ctx, 0,
            new Intent(ctx, MainActivity.class),
            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );

        Notification n = new NotificationCompat.Builder(ctx, CHANNEL_ALERTS)
            .setSmallIcon(R.mipmap.ic_launcher_lastnotif_legacy)
            .setContentTitle(main)
            .setContentText(sub)
            .setContentIntent(tapIntent)
            .setAutoCancel(false)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build();

        nm.notify(ID_ALERT, n);
    }

    private static String applyFormat(String fmt, String title, String artist, String album, String pollingMethod) {
        if (fmt == null || fmt.isEmpty()) return title;
        return fmt
            .replace("{song_name}",      title)
            .replace("{artist}",         artist)
            .replace("{album}",          album)
            .replace("{polling_method}", pollingMethod != null ? pollingMethod : "");
    }
}
