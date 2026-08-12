package dev.jxriera.gavel.litebans;

import dev.jxriera.gavel.model.PunishmentType;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

public final class Confirmations {

    public interface Callback {
        void done(boolean confirmed);
    }

    public static final class Pending {
        private final UUID target;
        private final String key;
        private final PunishmentType type;
        private final long since;
        private final long deadline;
        private final Callback callback;

        private Pending(UUID target, PunishmentType type, long since, long deadline, Callback callback) {
            this.target = target;
            this.key = normalize(target.toString());
            this.type = type;
            this.since = since;
            this.deadline = deadline;
            this.callback = callback;
        }

        public UUID getTarget() {
            return target;
        }

        public PunishmentType getType() {
            return type;
        }

        public long getSince() {
            return since;
        }

        public Callback getCallback() {
            return callback;
        }
    }

    private final List<Pending> pending = new ArrayList<Pending>();

    public static String normalize(String uuid) {
        return uuid == null ? null : uuid.replace("-", "").toLowerCase();
    }

    public static String family(PunishmentType type) {
        if (type == null) {
            return null;
        }
        switch (type) {
            case BAN:
            case IPBAN:
                return "ban";
            case MUTE:
                return "mute";
            case WARN:
                return "warn";
            case KICK:
                return "kick";
            default:
                return null;
        }
    }

    public static String familyOf(String liteBansType) {
        if (liteBansType == null) {
            return null;
        }
        String value = liteBansType.trim().toLowerCase();
        if (value.equals("warning")) {
            return "warn";
        }
        if (value.equals("ban") || value.equals("mute") || value.equals("warn") || value.equals("kick")) {
            return value;
        }
        return null;
    }

    public synchronized void await(UUID target, PunishmentType type, long since, long deadline,
                                   Callback callback) {
        pending.add(new Pending(target, type, since, deadline, callback));
    }

    public Pending confirm(String uuid, String family) {
        String key = normalize(uuid);
        if (key == null || family == null) {
            return null;
        }
        synchronized (this) {
            for (Iterator<Pending> iterator = pending.iterator(); iterator.hasNext(); ) {
                Pending candidate = iterator.next();
                if (key.equals(candidate.key) && family.equals(family(candidate.type))) {
                    iterator.remove();
                    return candidate;
                }
            }
        }
        return null;
    }

    public Pending cancel(UUID target, PunishmentType type) {
        return confirm(target == null ? null : target.toString(), family(type));
    }

    public List<Pending> expire(long now) {
        List<Pending> expired = new ArrayList<Pending>();
        synchronized (this) {
            for (Iterator<Pending> iterator = pending.iterator(); iterator.hasNext(); ) {
                Pending candidate = iterator.next();
                if (now >= candidate.deadline) {
                    iterator.remove();
                    expired.add(candidate);
                }
            }
        }
        return expired;
    }

    public synchronized int size() {
        return pending.size();
    }
}
