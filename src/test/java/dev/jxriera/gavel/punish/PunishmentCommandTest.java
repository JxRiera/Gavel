package dev.jxriera.gavel.punish;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
