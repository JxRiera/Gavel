package dev.jxriera.gavel.punish;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class DispatchGuard {
    private final Set<UUID> players = ConcurrentHashMap.newKeySet();
    private final AtomicInteger console = new AtomicInteger();

    public void enter(UUID playerId) {
        if (playerId == null) {
            console.incrementAndGet();
        } else {
            players.add(playerId);
        }
    }

    public void exit(UUID playerId) {
        if (playerId == null) {
            if (console.get() > 0) {
                console.decrementAndGet();
            }
        } else {
            players.remove(playerId);
        }
    }

    public boolean isDispatching(UUID playerId) {
        return playerId == null ? console.get() > 0 : players.contains(playerId);
    }
}
