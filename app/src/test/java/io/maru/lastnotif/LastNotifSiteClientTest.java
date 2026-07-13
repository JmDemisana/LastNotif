package io.maru.lastnotif;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class LastNotifSiteClientTest {

    @Test
    public void lyricLine_creation() {
        LastNotifSiteClient.LyricLine line = new LastNotifSiteClient.LyricLine(12345L, "Test lyric");
        assertEquals(12345L, line.timestampMs);
        assertEquals("Test lyric", line.text);
    }

    @Test
    public void lyricLine_nullText() {
        LastNotifSiteClient.LyricLine line = new LastNotifSiteClient.LyricLine(54321L, null);
        assertEquals(54321L, line.timestampMs);
        assertEquals("", line.text);
    }
}
