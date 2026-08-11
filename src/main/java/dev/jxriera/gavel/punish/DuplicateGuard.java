package dev.jxriera.gavel.punish;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class DuplicateGuard {

    private final Map<String, Long> lastApplied = new HashMap<String, Long>();
    private final Set<String> inFlight = ConcurrentHashMap.newKeySet();

    public synchronized boolean tryBegin(String key, long windowMillis, long now) {
        if (inFlight.contains(key)) {
            return false;
        }
        prune(windowMillis, now);
        Long last = lastApplied.get(key);
        if (windowMillis > 0L && last != null && now - last < windowMillis) {
            return false;
        }
        inFlight.add(key);
        return true;
    }

    public boolean tryBegin(String key, long windowMillis) {
        return tryBegin(key, windowMillis, System.currentTimeMillis());
    }

    public synchronized void finish(String key, boolean applied, long now) {
        inFlight.remove(key);
        if (applied) {
            lastApplied.put(key, now);
        }
    }

    public void finish(String key, boolean applied) {
        finish(key, applied, System.currentTimeMillis());
    }

    private void prune(long windowMillis, long now) {
        if (windowMillis <= 0L) {
            lastApplied.clear();
            return;
        }
        Iterator<Map.Entry<String, Long>> iterator = lastApplied.entrySet().iterator();
        while (iterator.hasNext()) {
            if (now - iterator.next().getValue() >= windowMillis) {
                iterator.remove();
            }
        }
    }
}
