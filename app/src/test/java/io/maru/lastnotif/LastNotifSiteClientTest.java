package io.maru.lastnotif;

import org.junit.Test;
import static org.junit.Assert.*;

import io.maru.lastnotif.LastNotifSiteClient.LyricsResult;

public class LastNotifSiteClientTest {

    @Test
    public void testGetLyricsErrorHandling_nullUsername() {
        LyricsResult result = LastNotifSiteClient.getLyrics(null);
        assertNull(result);
    }

    @Test
    public void testGetLyricsErrorHandling_invalidJson() {
        LyricsResult result = LastNotifSiteClient.getLyrics("invalid_user_that_throws_or_returns_null");
        assertNull(result);
    }
}
