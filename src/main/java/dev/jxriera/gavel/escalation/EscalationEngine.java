package dev.jxriera.gavel.escalation;

import dev.jxriera.gavel.model.Category;
import dev.jxriera.gavel.model.OffenseRecord;
import dev.jxriera.gavel.model.Tier;

import java.util.List;

public final class EscalationEngine {

    public static final class Result {
        private final int previousOffenses;
        private final Tier tier;

        Result(int previousOffenses, Tier tier) {
            this.previousOffenses = previousOffenses;
            this.tier = tier;
        }

        public int getPreviousOffenses() {
            return previousOffenses;
        }

        public int getOffenseNumber() {
            return previousOffenses + 1;
        }

        public Tier getTier() {
            return tier;
        }
    }

    private EscalationEngine() {
    }

    public static int count(Category category, List<OffenseRecord> history, long now) {
        if (history == null || history.isEmpty()) {
            return 0;
        }
        long window = category.getExpireAfterMillis();
        int count = 0;
        for (OffenseRecord record : history) {
            if (record.getCategory() == null || !record.getCategory().equalsIgnoreCase(category.getId())) {
                continue;
            }
            if (window > 0L && now - record.getCreated() > window) {
                continue;
            }
            count++;
        }
        return count;
    }

    public static Result resolve(Category category, List<OffenseRecord> history, Overflow overflow, long now) {
        return resolveFromCount(category, count(category, history, now), overflow);
    }

    public static Result resolve(Category category, List<OffenseRecord> history, Overflow overflow) {
        return resolve(category, history, overflow, System.currentTimeMillis());
    }

    public static Result resolveFromCount(Category category, int previousOffenses, Overflow overflow) {
        List<Tier> tiers = category.getTiers();
        int size = tiers.size();
        int index;
        if (previousOffenses < size) {
            index = previousOffenses;
        } else if (overflow == Overflow.CYCLE) {
            index = previousOffenses % size;
        } else {
            index = size - 1;
        }
        return new Result(previousOffenses, tiers.get(index));
    }
}
