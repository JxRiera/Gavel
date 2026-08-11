package dev.jxriera.gavel.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TargetsTest {

    @ParameterizedTest
    @ValueSource(strings = {"Steve", "a", "Notch_1", "ABCDEFGHIJKLMNOP", "____"})
    void acceptsRealMinecraftNames(String name) {
        assertTrue(Targets.isValidName(name), name);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "ABCDEFGHIJKLMNOPQ",
            "Steve Jobs",
            "Steve;ban",
            "@a",
            "Ste-ve",
            "sté",
            "",
            "name:steve"
    })
    void rejectsAnythingThatIsNotAName(String name) {
        assertFalse(Targets.isValidName(name), name);
    }

    @Test
    void rejectsNull() {
        assertFalse(Targets.isValidName(null));
    }

    @Test
    void aRejectedNameCanNeverOverflowTheNameColumn() {
        String longest = "ABCDEFGHIJKLMNOP";

        assertTrue(Targets.isValidName(longest));
        assertTrue(longest.length() <= 32);
        assertTrue(Targets.storageKey(null, longest).length() <= 36);
    }

    @Test
    void storageKeyPrefersTheUuid() {
        UUID uuid = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");

        assertEquals(uuid.toString(), Targets.storageKey(uuid, "Notch"));
        assertEquals("name:notch", Targets.storageKey(null, "Notch"));
    }
}
