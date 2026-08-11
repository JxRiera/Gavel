package dev.jxriera.gavel.escalation;

import dev.jxriera.gavel.model.Category;
import dev.jxriera.gavel.model.OffenseRecord;
import dev.jxriera.gavel.model.PunishmentType;
import dev.jxriera.gavel.model.Tier;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EscalationEngineTest {

    private static final long NOW = 1_700_000_000_000L;
    private static final long DAY = 86_400_000L;

    private static Category hackedClient(long expireAfterMillis) {
        List<Tier> tiers = Arrays.asList(
                new Tier(1, PunishmentType.BAN, "15d", "Hacked Client #1"),
                new Tier(2, PunishmentType.BAN, "25d", "Hacked Client #2"),
                new Tier(3, PunishmentType.BAN, "perm", "Hacked Client #3"));
        return new Category("hacked_client", 10, null, expireAfterMillis, null, tiers);
    }

    private static OffenseRecord offense(String category, long created) {
        return new OffenseRecord(0L, "uuid", "Steve", category, 1, "BAN", "15d", "reason",
                "staff-uuid", "Staff", "main", false, true, created);
    }

    private static List<OffenseRecord> repeat(String category, int times, long created) {
        List<OffenseRecord> out = new ArrayList<OffenseRecord>();
        for (int index = 0; index < times; index++) {
            out.add(offense(category, created));
        }
        return out;
    }

    @Test
    void firstOffenseUsesTheFirstTier() {
        EscalationEngine.Result result = EscalationEngine.resolve(
                hackedClient(-1L), Collections.<OffenseRecord>emptyList(), Overflow.LAST, NOW);

        assertEquals(0, result.getPreviousOffenses());
        assertEquals(1, result.getOffenseNumber());
        assertEquals("15d", result.getTier().getDuration());
        assertEquals("Hacked Client #1", result.getTier().getReason());
    }

    @Test
    void climbsOneTierPerPreviousOffense() {
        Category category = hackedClient(-1L);

        assertEquals("25d", EscalationEngine
                .resolve(category, repeat("hacked_client", 1, NOW), Overflow.LAST, NOW)
                .getTier().getDuration());
        assertEquals("perm", EscalationEngine
                .resolve(category, repeat("hacked_client", 2, NOW), Overflow.LAST, NOW)
                .getTier().getDuration());
    }

    @Test
    void nullHistoryCountsAsNoOffenses() {
        assertEquals(0, EscalationEngine.count(hackedClient(-1L), null, NOW));
    }

    @Test
    void overflowLastRepeatsTheFinalTier() {
        EscalationEngine.Result result = EscalationEngine.resolve(
                hackedClient(-1L), repeat("hacked_client", 9, NOW), Overflow.LAST, NOW);

        assertEquals(10, result.getOffenseNumber());
        assertEquals("perm", result.getTier().getDuration());
    }

    @Test
    void overflowCycleWrapsBackToTheFirstTier() {
        Category category = hackedClient(-1L);

        assertEquals("15d", EscalationEngine
                .resolve(category, repeat("hacked_client", 3, NOW), Overflow.CYCLE, NOW)
                .getTier().getDuration());
        assertEquals("25d", EscalationEngine
                .resolve(category, repeat("hacked_client", 4, NOW), Overflow.CYCLE, NOW)
                .getTier().getDuration());
    }

    @Test
    void offensesFromOtherCategoriesAreIgnored() {
        List<OffenseRecord> history = Arrays.asList(
                offense("chat_spam", NOW),
                offense("griefing", NOW),
                offense("hacked_client", NOW));

        assertEquals(1, EscalationEngine.count(hackedClient(-1L), history, NOW));
    }

    @Test
    void categoryMatchingIsCaseInsensitive() {
        assertEquals(1, EscalationEngine.count(
                hackedClient(-1L), Collections.singletonList(offense("HACKED_CLIENT", NOW)), NOW));
    }

    @Test
    void offensesOlderThanTheWindowStopCounting() {
        Category category = hackedClient(90L * DAY);
        List<OffenseRecord> history = Arrays.asList(
                offense("hacked_client", NOW - 91L * DAY),
                offense("hacked_client", NOW - 10L * DAY));

        assertEquals(1, EscalationEngine.count(category, history, NOW));
        assertEquals("25d", EscalationEngine.resolve(category, history, Overflow.LAST, NOW)
                .getTier().getDuration());
    }

    @Test
    void offenseExactlyOnTheWindowEdgeStillCounts() {
        Category category = hackedClient(90L * DAY);
        List<OffenseRecord> history = Collections.singletonList(offense("hacked_client", NOW - 90L * DAY));

        assertEquals(1, EscalationEngine.count(category, history, NOW));
    }

    @Test
    void aPermanentWindowNeverExpires() {
        Category category = hackedClient(-1L);
        List<OffenseRecord> history = Collections.singletonList(offense("hacked_client", NOW - 3650L * DAY));

        assertEquals(1, EscalationEngine.count(category, history, NOW));
    }

    @Test
    void resolveFromCountMatchesResolve() {
        Category category = hackedClient(-1L);

        assertEquals(
                EscalationEngine.resolve(category, repeat("hacked_client", 2, NOW), Overflow.LAST, NOW)
                        .getTier().getReason(),
                EscalationEngine.resolveFromCount(category, 2, Overflow.LAST).getTier().getReason());
    }

    @Test
    void overflowParsingFallsBackOnUnknownValues() {
        assertEquals(Overflow.CYCLE, Overflow.parse("cycle", Overflow.LAST));
        assertEquals(Overflow.LAST, Overflow.parse("nonsense", Overflow.LAST));
        assertEquals(Overflow.LAST, Overflow.parse(null, Overflow.LAST));
    }
}
