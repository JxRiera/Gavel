package dev.jxriera.gavel.punish;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DuplicateGuardTest {

    private static final long WINDOW = 5000L;
    private static final long NOW = 1_700_000_000_000L;

    @Test
    void firstAttemptIsAllowed() {
        assertTrue(new DuplicateGuard().tryBegin("uuid", WINDOW, NOW));
    }

    @Test
    void aSecondStaffMemberIsBlockedWhileTheFirstIsStillApplying() {
        DuplicateGuard guard = new DuplicateGuard();

        assertTrue(guard.tryBegin("uuid", WINDOW, NOW));
        assertFalse(guard.tryBegin("uuid", WINDOW, NOW));
    }

    @Test
    void blockedForTheWindowAfterAnAppliedPunishment() {
        DuplicateGuard guard = new DuplicateGuard();
        guard.tryBegin("uuid", WINDOW, NOW);
        guard.finish("uuid", true, NOW);

        assertFalse(guard.tryBegin("uuid", WINDOW, NOW + 1L));
        assertFalse(guard.tryBegin("uuid", WINDOW, NOW + WINDOW - 1L));
        assertTrue(guard.tryBegin("uuid", WINDOW, NOW + WINDOW));
    }

    @Test
    void aFailedAttemptDoesNotStartTheWindow() {
        DuplicateGuard guard = new DuplicateGuard();
        guard.tryBegin("uuid", WINDOW, NOW);
        guard.finish("uuid", false, NOW);

        assertTrue(guard.tryBegin("uuid", WINDOW, NOW));
    }

    @Test
    void differentPlayersDoNotBlockEachOther() {
        DuplicateGuard guard = new DuplicateGuard();

        assertTrue(guard.tryBegin("steve", WINDOW, NOW));
        assertTrue(guard.tryBegin("alex", WINDOW, NOW));
    }

    @Test
    void aZeroWindowStillBlocksConcurrentAttempts() {
        DuplicateGuard guard = new DuplicateGuard();

        assertTrue(guard.tryBegin("uuid", 0L, NOW));
        assertFalse(guard.tryBegin("uuid", 0L, NOW));

        guard.finish("uuid", true, NOW);
        assertTrue(guard.tryBegin("uuid", 0L, NOW));
    }
}
