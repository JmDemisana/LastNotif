package io.maru.lastnotif;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class LastNotifSiteClientTest {

    @Test
    public void lyricLine_nullText_shouldBecomeEmptyString() {
        LastNotifSiteClient.LyricLine line = new LastNotifSiteClient.LyricLine(1000L, null);
        assertNotNull(line.text);
        assertEquals("", line.text);
        assertEquals(1000L, line.timestampMs);
    }

    @Test
    public void lyricLine_validText_shouldBeRetained() {
        LastNotifSiteClient.LyricLine line = new LastNotifSiteClient.LyricLine(2000L, "Hello world");
        assertEquals("Hello world", line.text);
        assertEquals(2000L, line.timestampMs);
    }
}
