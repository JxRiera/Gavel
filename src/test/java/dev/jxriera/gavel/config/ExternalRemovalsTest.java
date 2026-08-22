package dev.jxriera.gavel.config;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExternalRemovalsTest {

    private static final String PLAYER = "069a79f4-44e9-4726-a5be-fca90e38aaf5";
    private static final String CONSOLE_UUID = "00000000-0000-0000-0000-000000000000";
    private static final Set<String> NONE = Collections.emptySet();

    @Test
    void allAcceptsEverySource() {
        assertTrue(ConfigManager.acceptsRemoval("ALL", PLAYER, "JxRiera", NONE));
        assertTrue(ConfigManager.acceptsRemoval("ALL", null, "Console", NONE));
        assertTrue(ConfigManager.acceptsRemoval("ALL", CONSOLE_UUID, "AntiCheat", NONE));
    }

    @Test
    void playersRejectsTheConsoleAndPlugins() {
        assertTrue(ConfigManager.acceptsRemoval("PLAYERS", PLAYER, "JxRiera", NONE));
        assertFalse(ConfigManager.acceptsRemoval("PLAYERS", null, "Console", NONE));
        assertFalse(ConfigManager.acceptsRemoval("PLAYERS", "", "Console", NONE));
        assertFalse(ConfigManager.acceptsRemoval("PLAYERS", "not-a-uuid", "Plugin", NONE));
    }

    @Test
    void theZeroUuidCountsAsTheConsole() {
        assertFalse(ConfigManager.acceptsRemoval("PLAYERS", CONSOLE_UUID, "Console", NONE));
    }

    @Test
    void gavelRejectsEverythingExternal() {
        assertFalse(ConfigManager.acceptsRemoval("GAVEL", PLAYER, "JxRiera", NONE));
        assertFalse(ConfigManager.acceptsRemoval("GAVEL", null, "Console", NONE));
    }

    @Test
    void anIgnoredExecutorIsRejectedInEveryMode() {
        Set<String> ignored = new HashSet<String>();
        ignored.add("anticheat");

        assertFalse(ConfigManager.acceptsRemoval("ALL", PLAYER, "AntiCheat", ignored));
        assertFalse(ConfigManager.acceptsRemoval("ALL", PLAYER, "  antiCHEAT  ", ignored));
        assertTrue(ConfigManager.acceptsRemoval("ALL", PLAYER, "JxRiera", ignored));
    }

    @Test
    void aMissingExecutorNameDoesNotCrashTheFilter() {
        assertTrue(ConfigManager.acceptsRemoval("ALL", PLAYER, null, NONE));
        assertFalse(ConfigManager.acceptsRemoval("PLAYERS", null, null, NONE));
    }
}
