package dev.jxriera.gavel.webhook;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebhookTest {

    @Test
    void escapesTheCharactersThatWouldBreakTheJsonBody() {
        assertEquals("say \\\"hi\\\"", Webhook.escape("say \"hi\""));
        assertEquals("a\\\\b", Webhook.escape("a\\b"));
        assertEquals("one\\ntwo", Webhook.escape("one\ntwo"));
        assertEquals("", Webhook.escape(null));
    }

    @Test
    void escapesControlCharacters() {
        assertEquals("\\u0007", Webhook.escape(""));
    }

    @Test
    void aReasonWithQuotesStillProducesValidJson() {
        Map<String, String> values = new HashMap<String, String>();
        values.put("reason", "Hacked \"client\" #1");
        values.put("target", "Steve");

        String body = Webhook.fill("{\"a\":\"%target%\",\"b\":\"%reason%\"}", values);

        assertEquals("{\"a\":\"Steve\",\"b\":\"Hacked \\\"client\\\" #1\"}", body);
    }

    @Test
    void unknownPlaceholdersAreLeftAlone() {
        assertEquals("{\"a\":\"%missing%\"}",
                Webhook.fill("{\"a\":\"%missing%\"}", new HashMap<String, String>()));
    }

    @Test
    void theWebhookUrlIsNeverExposedInFull() {
        String url = "https://discord.com/api/webhooks/123456789/SUPER-SECRET-TOKEN";

        String redacted = Webhook.redact(url);

        assertEquals("https://discord.com/...", redacted);
        assertFalse(redacted.contains("SUPER-SECRET-TOKEN"));
        assertFalse(redacted.contains("123456789"));
    }

    @Test
    void aMalformedUrlIsNotEchoedBack() {
        String redacted = Webhook.redact("not a url at all");

        assertFalse(redacted.contains("not a url"));
        assertTrue(redacted.length() > 0);
        assertEquals("?", Webhook.redact(null));
    }

    @Test
    void theTimestampFallsBackWhenThePatternIsBroken() {
        assertTrue(Webhook.now("dd/MM/yyyy").length() >= 8);
        assertTrue(Webhook.now("not a [ pattern").length() > 0);
    }
}
