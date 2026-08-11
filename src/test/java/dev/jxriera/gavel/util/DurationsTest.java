package dev.jxriera.gavel.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DurationsTest {

    private static final long SECOND = 1000L;
    private static final long MINUTE = 60L * SECOND;
    private static final long HOUR = 60L * MINUTE;
    private static final long DAY = 24L * HOUR;

    @ParameterizedTest
    @ValueSource(strings = {"perm", "permanent", "PERM", "0", "-1", "forever", ""})
    void treatsPermanentAliasesAsPermanent(String raw) {
        assertTrue(Durations.isPermanent(raw));
    }

    @Test
    void treatsNullAsPermanent() {
        assertTrue(Durations.isPermanent(null));
        assertEquals(-1L, Durations.toMillis(null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"15d", "25d", "2w", "6mo", "30m", "1y", "1d12h", "90s"})
    void acceptsWellFormedDurations(String raw) {
        assertTrue(Durations.isValid(raw), raw);
    }

    @ParameterizedTest
    @ValueSource(strings = {"banana", "15", "15dias", "d15", "15d!", "--", "1x"})
    @DisplayName("rejects anything with characters left over after parsing")
    void rejectsMalformedDurations(String raw) {
        assertFalse(Durations.isValid(raw), raw);
    }

    @Test
    void distinguishesMinutesFromMonths() {
        assertEquals(MINUTE, Durations.toMillis("1m"));
        assertEquals(30L * DAY, Durations.toMillis("1mo"));
    }

    @Test
    void sumsCompoundDurations() {
        assertEquals(DAY + 12L * HOUR, Durations.toMillis("1d12h"));
        assertEquals(2L * HOUR + 30L * MINUTE, Durations.toMillis("2h30m"));
    }

    @Test
    void convertsEveryUnit() {
        assertEquals(SECOND, Durations.toMillis("1s"));
        assertEquals(HOUR, Durations.toMillis("1h"));
        assertEquals(DAY, Durations.toMillis("1d"));
        assertEquals(7L * DAY, Durations.toMillis("1w"));
        assertEquals(365L * DAY, Durations.toMillis("1y"));
        assertEquals(15L * DAY, Durations.toMillis("15d"));
    }

    @Test
    void returnsMinusOneForPermanent() {
        assertEquals(-1L, Durations.toMillis("perm"));
    }

    @Test
    void aTemporaryPunishmentElapsesOnceItsDurationIsUp() {
        long created = 1_000_000L;

        assertFalse(Durations.hasElapsed("15d", created, created));
        assertFalse(Durations.hasElapsed("15d", created, created + 15L * DAY - 1L));
        assertTrue(Durations.hasElapsed("15d", created, created + 15L * DAY));
        assertTrue(Durations.hasElapsed("15d", created, created + 30L * DAY));
    }

    @Test
    void aPermanentPunishmentNeverElapses() {
        assertFalse(Durations.hasElapsed("perm", 0L, Long.MAX_VALUE / 2));
        assertFalse(Durations.hasElapsed(null, 0L, Long.MAX_VALUE / 2));
    }

    @Test
    void anUnparseableDurationNeverElapses() {
        assertFalse(Durations.hasElapsed("banana", 0L, Long.MAX_VALUE / 2));
    }

    @Test
    void displayUsesTheConfiguredWordOnlyForPermanent() {
        assertEquals("Permanent", Durations.display("perm", "Permanent"));
        assertEquals("15d", Durations.display("15d", "Permanent"));
        assertEquals("15d", Durations.display("  15d  ", "Permanent"));
    }
}
