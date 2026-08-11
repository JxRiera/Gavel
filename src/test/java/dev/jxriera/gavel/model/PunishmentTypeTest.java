package dev.jxriera.gavel.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PunishmentTypeTest {

    @ParameterizedTest
    @ValueSource(strings = {"BAN", "ban", " Ban "})
    void parsesRegardlessOfCase(String raw) {
        assertEquals(PunishmentType.BAN, PunishmentType.parse(raw, null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"IPBAN", "ip-ban", "ip_ban", "banip", "BAN-IP"})
    void parsesEveryIpBanSpelling(String raw) {
        assertEquals(PunishmentType.IPBAN, PunishmentType.parse(raw, null));
    }

    @Test
    void returnsFallbackForUnknownInput() {
        assertNull(PunishmentType.parse("nonsense", null));
        assertNull(PunishmentType.parse(null, null));
        assertEquals(PunishmentType.KICK, PunishmentType.parse("nonsense", PunishmentType.KICK));
    }

    @Test
    void everyTypeMapsToAConfiguredTemplate() {
        assertEquals("ban-temp", PunishmentType.BAN.templateKey(false));
        assertEquals("ban-perm", PunishmentType.BAN.templateKey(true));
        assertEquals("ipban-temp", PunishmentType.IPBAN.templateKey(false));
        assertEquals("mute-temp", PunishmentType.MUTE.templateKey(false));
        assertEquals("warn-perm", PunishmentType.WARN.templateKey(true));
    }

    @Test
    void kickIgnoresDurationAndAlwaysUsesTheSameTemplate() {
        assertFalse(PunishmentType.KICK.supportsDuration());
        assertEquals("kick", PunishmentType.KICK.templateKey(false));
        assertEquals("kick", PunishmentType.KICK.templateKey(true));
    }

    @Test
    void durationSupportMatchesTheTemplateTable() {
        assertTrue(PunishmentType.BAN.supportsDuration());
        assertTrue(PunishmentType.IPBAN.supportsDuration());
        assertTrue(PunishmentType.MUTE.supportsDuration());
        assertTrue(PunishmentType.WARN.supportsDuration());
    }

    @Test
    void wordKeysMatchTheMessagesFile() {
        assertEquals("type-ban", PunishmentType.BAN.wordKey());
        assertEquals("type-ipban", PunishmentType.IPBAN.wordKey());
        assertEquals("type-mute", PunishmentType.MUTE.wordKey());
        assertEquals("type-warn", PunishmentType.WARN.wordKey());
        assertEquals("type-kick", PunishmentType.KICK.wordKey());
    }
}
