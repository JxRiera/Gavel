package dev.jxriera.gavel.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TierTest {

    @Test
    void temporaryDurationsAreNotPermanent() {
        assertFalse(new Tier(1, PunishmentType.BAN, "15d", "reason").isPermanent());
    }

    @Test
    void permTokenIsPermanent() {
        assertTrue(new Tier(3, PunishmentType.BAN, "perm", "reason").isPermanent());
    }

    @Test
    void kickIsAlwaysPermanentBecauseItCarriesNoDuration() {
        assertTrue(new Tier(1, PunishmentType.KICK, "15d", "reason").isPermanent());
    }
}
