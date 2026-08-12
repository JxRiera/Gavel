package dev.jxriera.gavel.litebans;

import dev.jxriera.gavel.model.PunishmentType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfirmationsTest {

    private static final UUID TARGET = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");
    private static final String PLAIN = "069a79f444e94726a5befca90e38aaf5";
    private static final long NOW = 1_700_000_000_000L;

    private static Confirmations.Callback noop() {
        return new Confirmations.Callback() {
            @Override
            public void done(boolean confirmed) {
            }
        };
    }

    private static Confirmations waiting(PunishmentType type) {
        Confirmations confirmations = new Confirmations();
        confirmations.await(TARGET, type, NOW, NOW + 3000L, noop());
        return confirmations;
    }

    @Test
    void matchesTheSameUuidRegardlessOfDashesOrCase() {
        Confirmations confirmations = waiting(PunishmentType.BAN);

        assertNotNull(confirmations.confirm(PLAIN.toUpperCase(), "ban"));
        assertEquals(0, confirmations.size());
    }

    @Test
    void anIpBanEntryConfirmsAPlainBanAndViceVersa() {
        assertNotNull(waiting(PunishmentType.IPBAN).confirm(TARGET.toString(), "ban"));
        assertNotNull(waiting(PunishmentType.BAN).confirm(TARGET.toString(), "ban"));
    }

    @Test
    void liteBansTypesMapOntoFamilies() {
        assertEquals("ban", Confirmations.familyOf("ban"));
        assertEquals("mute", Confirmations.familyOf("MUTE"));
        assertEquals("warn", Confirmations.familyOf("warning"));
        assertEquals("warn", Confirmations.familyOf("warn"));
        assertEquals("kick", Confirmations.familyOf("kick"));
        assertNull(Confirmations.familyOf("something-else"));
        assertNull(Confirmations.familyOf(null));
    }

    @Test
    void everyPunishmentTypeHasAFamily() {
        assertEquals("ban", Confirmations.family(PunishmentType.BAN));
        assertEquals("ban", Confirmations.family(PunishmentType.IPBAN));
        assertEquals("mute", Confirmations.family(PunishmentType.MUTE));
        assertEquals("warn", Confirmations.family(PunishmentType.WARN));
        assertEquals("kick", Confirmations.family(PunishmentType.KICK));
    }

    @Test
    void doesNotMatchADifferentFamily() {
        Confirmations confirmations = waiting(PunishmentType.BAN);

        assertNull(confirmations.confirm(TARGET.toString(), "mute"));
        assertEquals(1, confirmations.size());
    }

    @Test
    void doesNotMatchADifferentPlayer() {
        Confirmations confirmations = waiting(PunishmentType.BAN);

        assertNull(confirmations.confirm("11111111-1111-1111-1111-111111111111", "ban"));
        assertEquals(1, confirmations.size());
    }

    @Test
    void ignoresNullInput() {
        Confirmations confirmations = waiting(PunishmentType.BAN);

        assertNull(confirmations.confirm(null, "ban"));
        assertNull(confirmations.confirm(TARGET.toString(), null));
        assertEquals(1, confirmations.size());
    }

    @Test
    void twoPunishmentsForOnePlayerAreConfirmedOneAtATime() {
        Confirmations confirmations = new Confirmations();
        confirmations.await(TARGET, PunishmentType.BAN, NOW, NOW + 3000L, noop());
        confirmations.await(TARGET, PunishmentType.BAN, NOW, NOW + 3000L, noop());

        assertNotNull(confirmations.confirm(TARGET.toString(), "ban"));
        assertEquals(1, confirmations.size());
        assertNotNull(confirmations.confirm(TARGET.toString(), "ban"));
        assertNull(confirmations.confirm(TARGET.toString(), "ban"));
    }

    @Test
    void expiresOnlyOncePastTheDeadline() {
        Confirmations confirmations = waiting(PunishmentType.BAN);

        assertTrue(confirmations.expire(NOW).isEmpty());
        assertTrue(confirmations.expire(NOW + 2999L).isEmpty());

        List<Confirmations.Pending> expired = confirmations.expire(NOW + 3000L);
        assertEquals(1, expired.size());
        assertEquals(NOW, expired.get(0).getSince());
        assertEquals(TARGET, expired.get(0).getTarget());
        assertEquals(0, confirmations.size());
    }

    @Test
    void anExpiredConfirmationReportsFailure() {
        Confirmations confirmations = new Confirmations();
        final AtomicInteger failures = new AtomicInteger();
        confirmations.await(TARGET, PunishmentType.BAN, NOW, NOW, new Confirmations.Callback() {
            @Override
            public void done(boolean confirmed) {
                if (!confirmed) {
                    failures.incrementAndGet();
                }
            }
        });

        for (Confirmations.Pending expired : confirmations.expire(NOW)) {
            expired.getCallback().done(false);
        }

        assertEquals(1, failures.get());
    }

    @Test
    void cancellingRemovesThePendingConfirmation() {
        Confirmations confirmations = waiting(PunishmentType.BAN);

        assertNotNull(confirmations.cancel(TARGET, PunishmentType.BAN));
        assertEquals(0, confirmations.size());
    }
}
