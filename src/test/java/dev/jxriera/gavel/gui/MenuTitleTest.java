package dev.jxriera.gavel.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MenuTitleTest {

    @Test
    void shortTitlesAreLeftAlone() {
        assertEquals("Punish Steve", Menu.truncate("Punish Steve", 32));
    }

    @Test
    void nullBecomesEmpty() {
        assertEquals("", Menu.truncate(null, 32));
    }

    @Test
    void doesNotLeaveADanglingColourCharacter() {
        String title = "0123456789012345678901234567890§a";

        assertEquals("0123456789012345678901234567890", Menu.truncate(title, 32));
    }

    @Test
    void keepsACompleteColourCodeInsideTheLimit() {
        String title = "§aPunish Steve and a very long tail";

        assertEquals("§aPunish Steve and a very long t", Menu.truncate(title, 32));
    }

    @Test
    void stripsSeveralTrailingColourCharacters() {
        assertEquals("abc", Menu.truncate("abc§§def", 5));
    }
}
