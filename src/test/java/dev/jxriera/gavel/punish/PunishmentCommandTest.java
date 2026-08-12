package dev.jxriera.gavel.punish;

import dev.jxriera.gavel.model.PunishmentType;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PunishmentCommandTest {

    private static Map<String, String> placeholders(String flags, String duration, String reason) {
        Map<String, String> out = new HashMap<String, String>();
        out.put("target", "Steve");
        out.put("flags", flags);
        out.put("duration", duration);
        out.put("reason", reason);
        return out;
    }

    @Test
    void buildsATemporaryBan() {
        assertEquals("tempban Steve 15d Hacked Client #1", PunishmentService.buildCommand(
                "tempban %flags%%target% %duration% %reason%",
                placeholders("", "15d", "Hacked Client #1")));
    }

    @Test
    void prependsTheSilentFlagToTheTarget() {
        assertEquals("tempban -s Steve 15d Hacked Client #1", PunishmentService.buildCommand(
                "tempban %flags%%target% %duration% %reason%",
                placeholders("-s ", "15d", "Hacked Client #1")));
    }

    @Test
    void dropsTokensThatResolveToNothing() {
        assertEquals("kick Steve Inappropriate name", PunishmentService.buildCommand(
                "kick %flags%%target% %duration% %reason%",
                placeholders("", "", "Inappropriate name")));
    }

    @Test
    void keepsDoubleSpacesInsideTheReason() {
        assertEquals("ban Steve Ban  evasion #1", PunishmentService.buildCommand(
                "ban %flags%%target% %reason%",
                placeholders("", "perm", "Ban  evasion #1")));
    }

    @Test
    void collapsesExtraSpacingInTheTemplateItself() {
        assertEquals("ban Steve reason", PunishmentService.buildCommand(
                "  ban    %flags%%target%   %reason%  ",
                placeholders("", "perm", "reason")));
    }

    @Test
    void unknownPlaceholdersAreLeftUntouched() {
        assertEquals("ban Steve %ip%", PunishmentService.buildCommand(
                "ban %target% %ip%", placeholders("", "perm", "reason")));
    }

    @Test
    void nullTemplateBecomesEmpty() {
        assertEquals("", PunishmentService.buildCommand(null, placeholders("", "15d", "reason")));
    }

    @Test
    void theSilentFlagIsAddedOnlyWhenLiteBansIsNotAlreadySilent() {
        assertEquals("-s ", PunishmentService.flagsFor(true, false, "-s", ""));
        assertEquals("", PunishmentService.flagsFor(true, true, "-s", ""));
        assertEquals("", PunishmentService.flagsFor(false, false, "-s", ""));
    }

    @Test
    void theBroadcastFlagForcesAnAnnouncementOnASilentByDefaultServer() {
        assertEquals("-b ", PunishmentService.flagsFor(false, true, "-s", "-b"));
        assertEquals("", PunishmentService.flagsFor(false, false, "-s", "-b"));
        assertEquals("", PunishmentService.flagsFor(true, true, "-s", "-b"));
    }

    @Test
    void aSilentByDefaultServerStaysSilentWithoutABroadcastFlag() {
        assertTrue(PunishmentService.effectiveSilent(false, true, ""));
        assertFalse(PunishmentService.effectiveSilent(false, true, "-b"));
        assertFalse(PunishmentService.effectiveSilent(false, false, ""));
        assertTrue(PunishmentService.effectiveSilent(true, false, ""));
    }

    @Test
    void whatLiteBansReportsWinsOverTheConfiguredDefault() {
        assertEquals(PunishmentType.IPBAN,
                PunishmentService.recordedType(PunishmentType.BAN, Boolean.TRUE, false));
        assertEquals(PunishmentType.BAN,
                PunishmentService.recordedType(PunishmentType.IPBAN, Boolean.FALSE, true));
    }

    @Test
    void withoutAReportTheConfiguredDefaultDecides() {
        assertEquals(PunishmentType.IPBAN,
                PunishmentService.recordedType(PunishmentType.BAN, null, true));
        assertEquals(PunishmentType.BAN,
                PunishmentService.recordedType(PunishmentType.BAN, null, false));
        assertEquals(PunishmentType.IPBAN,
                PunishmentService.recordedType(PunishmentType.IPBAN, null, false));
    }

    @Test
    void onlyBansCanTurnIntoIpBans() {
        assertEquals(PunishmentType.MUTE,
                PunishmentService.recordedType(PunishmentType.MUTE, Boolean.TRUE, true));
        assertEquals(PunishmentType.WARN,
                PunishmentService.recordedType(PunishmentType.WARN, Boolean.TRUE, true));
        assertEquals(PunishmentType.KICK,
                PunishmentService.recordedType(PunishmentType.KICK, Boolean.TRUE, true));
    }
}
