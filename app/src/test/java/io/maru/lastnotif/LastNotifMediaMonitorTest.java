package io.maru.lastnotif;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doReturn;

import android.content.Context;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.provider.Settings;
import android.content.ComponentName;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Arrays;
import java.util.Collections;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class LastNotifMediaMonitorTest {

    private Context context;
    private MediaSessionManager mediaSessionManager;
    private MediaController controller1;
    private MediaController controller2;

    @Before
    public void setup() {
        context = mock(Context.class);
        mediaSessionManager = mock(MediaSessionManager.class);

        when(context.getSystemService(Context.MEDIA_SESSION_SERVICE)).thenReturn(mediaSessionManager);
        when(context.getPackageName()).thenReturn("io.maru.lastnotif");

        Context appContext = ApplicationProvider.getApplicationContext();
        when(context.getContentResolver()).thenReturn(appContext.getContentResolver());

        // Grant notification access
        Settings.Secure.putString(appContext.getContentResolver(), "enabled_notification_listeners", "io.maru.lastnotif/io.maru.lastnotif.LastNotifMediaListenerService");

        controller1 = mock(MediaController.class);
        controller2 = mock(MediaController.class);
    }

    private PlaybackState createPlaybackState(int state) {
        PlaybackState playbackState = mock(PlaybackState.class);
        when(playbackState.getState()).thenReturn(state);
        return playbackState;
    }

    private MediaMetadata createMetadata(String title, String artist, String album) {
        MediaMetadata metadata = mock(MediaMetadata.class);
        when(metadata.getString(MediaMetadata.METADATA_KEY_TITLE)).thenReturn(title);
        when(metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)).thenReturn(artist);
        when(metadata.getString(MediaMetadata.METADATA_KEY_ALBUM)).thenReturn(album);
        return metadata;
    }

    @Test
    public void testActiveTrackFallbackBehavior_nonePlaying_shouldFallbackToFirst() throws Exception {
        PlaybackState state1 = createPlaybackState(PlaybackState.STATE_PAUSED);
        MediaMetadata meta1 = createMetadata("Title 1", "Artist 1", "Album 1");
        when(controller1.getPlaybackState()).thenReturn(state1);
        when(controller1.getMetadata()).thenReturn(meta1);

        PlaybackState state2 = createPlaybackState(PlaybackState.STATE_PAUSED);
        MediaMetadata meta2 = createMetadata("Title 2", "Artist 2", "Album 2");
        when(controller2.getPlaybackState()).thenReturn(state2);
        when(controller2.getMetadata()).thenReturn(meta2);

        when(mediaSessionManager.getActiveSessions(any(ComponentName.class)))
                .thenReturn(Arrays.asList(controller1, controller2));

        LastNotifMediaMonitor.TrackInfo trackInfo = LastNotifMediaMonitor.getActiveTrack(context);

        assertNotNull(trackInfo);
        assertEquals("Title 1", trackInfo.title);
        assertEquals("Artist 1", trackInfo.artist);
        assertFalse(trackInfo.isPlaying);
    }

    @Test
    public void testActiveTrackFallbackBehavior_secondPlaying_shouldSelectSecond() throws Exception {
        PlaybackState state1 = createPlaybackState(PlaybackState.STATE_PAUSED);
        MediaMetadata meta1 = createMetadata("Title 1", "Artist 1", "Album 1");
        when(controller1.getPlaybackState()).thenReturn(state1);
        when(controller1.getMetadata()).thenReturn(meta1);

        PlaybackState state2 = createPlaybackState(PlaybackState.STATE_PLAYING);
        MediaMetadata meta2 = createMetadata("Title 2", "Artist 2", "Album 2");
        when(controller2.getPlaybackState()).thenReturn(state2);
        when(controller2.getMetadata()).thenReturn(meta2);

        when(mediaSessionManager.getActiveSessions(any(ComponentName.class)))
                .thenReturn(Arrays.asList(controller1, controller2));

        LastNotifMediaMonitor.TrackInfo trackInfo = LastNotifMediaMonitor.getActiveTrack(context);

        assertNotNull(trackInfo);
        assertEquals("Title 2", trackInfo.title);
        assertEquals("Artist 2", trackInfo.artist);
        assertTrue(trackInfo.isPlaying);
    }

    @Test
    public void testActiveTrackFallbackBehavior_firstPlaying_shouldSelectFirst() throws Exception {
        PlaybackState state1 = createPlaybackState(PlaybackState.STATE_PLAYING);
        MediaMetadata meta1 = createMetadata("Title 1", "Artist 1", "Album 1");
        when(controller1.getPlaybackState()).thenReturn(state1);
        when(controller1.getMetadata()).thenReturn(meta1);

        PlaybackState state2 = createPlaybackState(PlaybackState.STATE_PAUSED);
        MediaMetadata meta2 = createMetadata("Title 2", "Artist 2", "Album 2");
        when(controller2.getPlaybackState()).thenReturn(state2);
        when(controller2.getMetadata()).thenReturn(meta2);

        when(mediaSessionManager.getActiveSessions(any(ComponentName.class)))
                .thenReturn(Arrays.asList(controller1, controller2));

        LastNotifMediaMonitor.TrackInfo trackInfo = LastNotifMediaMonitor.getActiveTrack(context);

        assertNotNull(trackInfo);
        assertEquals("Title 1", trackInfo.title);
        assertEquals("Artist 1", trackInfo.artist);
        assertTrue(trackInfo.isPlaying);
    }
}
