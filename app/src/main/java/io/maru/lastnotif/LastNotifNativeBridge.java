package io.maru.lastnotif;

import android.content.Context;
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
        if (json == null || json.length() > 5000) {
            return; // Prevent excessive payload size
        }
        try {
            JSONObject obj = new JSONObject(json);

            if (obj.has("username") && obj.opt("username") instanceof String) {
                String val = obj.getString("username");
                if (val.length() <= 100) {
                    storage.setUsername(val);
                }
            }

            if (obj.has("notifySongUpdate") && obj.opt("notifySongUpdate") instanceof Boolean) {
                storage.setNotifySongUpdate(obj.getBoolean("notifySongUpdate"));
            }

            if (obj.has("intervalEnabled") && obj.opt("intervalEnabled") instanceof Boolean) {
                storage.setIntervalEnabled(obj.getBoolean("intervalEnabled"));
            }

            if (obj.has("intervalMinutes") && obj.opt("intervalMinutes") instanceof Integer) {
                int val = obj.getInt("intervalMinutes");
                if (val >= 1 && val <= 1440) {
                    storage.setIntervalMinutes(val);
                }
            }

            if (obj.has("notifMainFormat") && obj.opt("notifMainFormat") instanceof String) {
                String val = obj.getString("notifMainFormat");
                if (val.length() <= 200) {
                    storage.setNotifMainFormat(val);
                }
            }

            if (obj.has("notifSubFormat") && obj.opt("notifSubFormat") instanceof String) {
                String val = obj.getString("notifSubFormat");
                if (val.length() <= 200) {
                    storage.setNotifSubFormat(val);
                }
            }

            if (obj.has("lyricsEnabled") && obj.opt("lyricsEnabled") instanceof Boolean) {
                storage.setLyricsEnabled(obj.getBoolean("lyricsEnabled"));
            }

            if (obj.has("trackSource") && obj.opt("trackSource") instanceof String) {
                String val = obj.getString("trackSource");
                if ("device".equals(val) || "lastfm".equals(val) || "mixed".equals(val)) {
                    storage.setTrackSource(val);
                }
            }

        } catch (Exception e) {
            // Ignore malformed JSON — settings stay as-is
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
            // Fallback to storage state
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
            // Ignore
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
