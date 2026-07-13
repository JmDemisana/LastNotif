package io.maru.lastnotif;

import android.content.Context;
import android.util.Log;
import android.webkit.JavascriptInterface;

import org.json.JSONObject;

/**
 * JS ↔ Java bridge injected into the settings WebView as "NativeBridge".
 *
 * Callable from JS:
 *   NativeBridge.getSettings()       → JSON string of all stored settings
 *   NativeBridge.saveSettings(json)  → saves the given settings JSON
 *   NativeBridge.startPoller()       → starts the foreground service
 *   NativeBridge.stopPoller()        → stops the foreground service
 *   NativeBridge.isPollerRunning()   → true/false string
 */
public class LastNotifNativeBridge {

    private static final String TAG = "LastNotifNativeBridge";

    private final Context ctx;
    private final LastNotifStorage storage;

    public LastNotifNativeBridge(Context context) {
        this.ctx     = context.getApplicationContext();
        this.storage = new LastNotifStorage(ctx);
    }

    @JavascriptInterface
    public String getSettings() {
        return storage.toJson();
    }

    @JavascriptInterface
    public void saveSettings(String json) {
        try {
            JSONObject obj = new JSONObject(json);

            if (obj.has("username"))
                storage.setUsername(obj.getString("username"));

            if (obj.has("notifySongUpdate"))
                storage.setNotifySongUpdate(obj.getBoolean("notifySongUpdate"));

            if (obj.has("intervalEnabled"))
                storage.setIntervalEnabled(obj.getBoolean("intervalEnabled"));

            if (obj.has("intervalMinutes"))
                storage.setIntervalMinutes(obj.getInt("intervalMinutes"));

            if (obj.has("notifMainFormat"))
                storage.setNotifMainFormat(obj.getString("notifMainFormat"));

            if (obj.has("notifSubFormat"))
                storage.setNotifSubFormat(obj.getString("notifSubFormat"));

            if (obj.has("lyricsEnabled"))
                storage.setLyricsEnabled(obj.getBoolean("lyricsEnabled"));

            if (obj.has("trackSource"))
                storage.setTrackSource(obj.getString("trackSource"));

        } catch (Exception e) {
            Log.w(TAG, "Malformed JSON", e);
        }
    }

    @JavascriptInterface
    public void startPoller() {
        LastNotifPollerService.start(ctx);
        LastNotifPollerAlarmScheduler.schedule(ctx);
    }

    @JavascriptInterface
    public void stopPoller() {
        LastNotifPollerService.stop(ctx);
        LastNotifPollerAlarmScheduler.cancel(ctx);
    }

    @JavascriptInterface
    public String isPollerRunning() {
        try {
            android.app.ActivityManager manager = (android.app.ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
            if (manager != null) {
                java.util.List<android.app.ActivityManager.RunningServiceInfo> services = manager.getRunningServices(Integer.MAX_VALUE);
                if (services != null) {
                    for (android.app.ActivityManager.RunningServiceInfo service : services) {
                        if (LastNotifPollerService.class.getName().equals(service.service.getClassName())) {
                            return "true";
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking poller status", e);
        }
        return String.valueOf(storage.isServiceRunning());
    }

    @JavascriptInterface
    public String getActiveTrack() {
        try {
            java.io.File file = new java.io.File(ctx.getCacheDir(), "active_track.json");
            if (file.exists()) {
                java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(file));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                reader.close();
                return sb.toString();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error reading active track", e);
        }
        return "{}";
    }

    @JavascriptInterface
    public void openNotificationAccessSettings() {
        android.content.Intent intent = new android.content.Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS");
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
        ctx.startActivity(intent);
    }

    @JavascriptInterface
    public boolean hasNotificationAccess() {
        return LastNotifMediaMonitor.isNotificationAccessGranted(ctx);
    }
}
