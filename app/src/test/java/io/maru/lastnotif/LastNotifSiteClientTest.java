package io.maru.lastnotif;

import org.junit.Test;
import static org.junit.Assert.*;

public class LastNotifSiteClientTest {

    @Test
    public void testNowPlayingResultNullHandling() {
        LastNotifSiteClient.NowPlayingResult result = new LastNotifSiteClient.NowPlayingResult(null, null, null, true);
        assertEquals("", result.title);
        assertEquals("", result.artist);
        assertEquals("", result.album);
        assertTrue(result.isPlaying);
    }

    @Test
    public void testNowPlayingResultNormalValues() {
        LastNotifSiteClient.NowPlayingResult result = new LastNotifSiteClient.NowPlayingResult("Title", "Artist", "Album", false);
        assertEquals("Title", result.title);
        assertEquals("Artist", result.artist);
        assertEquals("Album", result.album);
        assertFalse(result.isPlaying);
    }
}
