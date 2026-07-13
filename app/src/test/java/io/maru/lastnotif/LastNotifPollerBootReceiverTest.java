package io.maru.lastnotif;

import android.app.AlarmManager;
import android.content.Context;
import android.content.Intent;
import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlarmManager;
import org.robolectric.shadows.ShadowApplication;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class LastNotifPollerBootReceiverTest {

    private Context context;
    private LastNotifStorage storage;
    private LastNotifPollerBootReceiver receiver;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        storage = new LastNotifStorage(context);
        receiver = new LastNotifPollerBootReceiver();

        // Ensure clean state
        storage.setServiceRunning(false);
        ShadowApplication.getInstance().clearStartedServices();
    }

    @Test
    public void onReceive_nullIntent_doesNothing() {
        receiver.onReceive(context, null);
        assertNull(ShadowApplication.getInstance().getNextStartedService());
    }

    @Test
    public void onReceive_nullAction_doesNothing() {
        receiver.onReceive(context, new Intent());
        assertNull(ShadowApplication.getInstance().getNextStartedService());
    }

    @Test
    public void onReceive_unrelatedAction_doesNothing() {
        Intent intent = new Intent("com.example.SOME_ACTION");
        receiver.onReceive(context, intent);
        assertNull(ShadowApplication.getInstance().getNextStartedService());
    }

    @Test
    public void onReceive_bootCompleted_serviceNotRunning_doesNothing() {
        storage.setServiceRunning(false);
        Intent intent = new Intent(Intent.ACTION_BOOT_COMPLETED);

        receiver.onReceive(context, intent);

        assertNull(ShadowApplication.getInstance().getNextStartedService());
    }

    @Test
    public void onReceive_bootCompleted_serviceRunning_startsServiceAndAlarm() {
        storage.setServiceRunning(true);
        Intent intent = new Intent(Intent.ACTION_BOOT_COMPLETED);

        receiver.onReceive(context, intent);

        // Check if service was started
        Intent startedIntent = ShadowApplication.getInstance().getNextStartedService();
        assertNotNull("Service should have been started", startedIntent);
        assertEquals(LastNotifPollerService.class.getName(), startedIntent.getComponent().getClassName());
        assertEquals(LastNotifPollerService.ACTION_START, startedIntent.getAction());

        // Check if alarm was scheduled
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        ShadowAlarmManager shadowAlarmManager = Shadows.shadowOf(alarmManager);
        ShadowAlarmManager.ScheduledAlarm nextAlarm = shadowAlarmManager.getNextScheduledAlarm();
        assertNotNull("Alarm should have been scheduled", nextAlarm);
        Intent alarmIntent = Shadows.shadowOf(nextAlarm.operation).getSavedIntent();
        assertEquals(LastNotifPollerAlarmReceiver.class.getName(), alarmIntent.getComponent().getClassName());
    }

    @Test
    public void onReceive_packageReplaced_serviceRunning_startsServiceAndAlarm() {
        storage.setServiceRunning(true);
        Intent intent = new Intent(Intent.ACTION_MY_PACKAGE_REPLACED);

        receiver.onReceive(context, intent);

        // Check if service was started
        Intent startedIntent = ShadowApplication.getInstance().getNextStartedService();
        assertNotNull("Service should have been started", startedIntent);
        assertEquals(LastNotifPollerService.class.getName(), startedIntent.getComponent().getClassName());
        assertEquals(LastNotifPollerService.ACTION_START, startedIntent.getAction());
    }
}
