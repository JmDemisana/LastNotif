package io.maru.lastnotif;

import org.junit.Test;
import static org.junit.Assert.*;

public class LastNotifMediaMonitorTest {

    @Test
    public void testTrackInfoNullHandling() {
        LastNotifMediaMonitor.TrackInfo trackInfo = new LastNotifMediaMonitor.TrackInfo(null, null, null, true);
        assertEquals("", trackInfo.title);
        assertEquals("", trackInfo.artist);
        assertEquals("", trackInfo.album);
        assertTrue(trackInfo.isPlaying);
    }
}
