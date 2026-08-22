package dev.jxriera.gavel.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YamlMergerRealFilesTest {

    private static String read(String name) throws IOException {
        File file = new File("src/main/resources/" + name);
        return new String(Files.readAllBytes(file.toPath()), Charset.forName("UTF-8"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parse(String text) {
        Object loaded = new Yaml().load(text);
        return loaded instanceof Map ? (Map<String, Object>) loaded : new LinkedHashMap<String, Object>();
    }

    @ParameterizedTest
    @ValueSource(strings = {"config.yml", "messages.yml", "categories.yml"})
    void mergingAFileWithItselfChangesNothing(String name) throws IOException {
        String text = read(name);

        YamlMerger.Result result = YamlMerger.merge(text, text);

        assertNull(result.getFailure());
        assertEquals(0, result.getAdded().size(), name);
    }

    @ParameterizedTest
    @ValueSource(strings = {"config.yml", "messages.yml"})
    void anOlderFileGainsEveryMissingKeyAndKeepsItsOwn(String name) throws IOException {
        String defaults = read(name);
        Map<String, Object> full = parse(defaults);

        Map<String, Object> older = new LinkedHashMap<String, Object>(full);
        int removed = 0;
        for (String key : full.keySet().toArray(new String[0])) {
            if (removed < 2 && older.size() > 2) {
                older.remove(key);
                removed++;
            }
        }
        String userText = new Yaml().dump(older);

        YamlMerger.Result result = YamlMerger.merge(defaults, userText);

        assertNull(result.getFailure(), name + ": " + result.getFailure());
        assertTrue(result.getAdded().size() > 0, name);

        Map<String, Object> merged = parse(result.getText());
        assertEquals(YamlMerger.flatten(full).keySet(), YamlMerger.flatten(merged).keySet(), name);
    }

    @Test
    void aCustomisedConfigKeepsEveryCustomValue() throws IOException {
        String defaults = read("config.yml");
        String user = defaults
                .replace("server-name: 'main'", "server-name: 'survival'")
                .replace("execute-as: PLAYER", "execute-as: CONSOLE")
                .replace("duplicate-window-seconds: 5", "duplicate-window-seconds: 30")
                .replace("    ip-bans: false", "    ip-bans: true")
                .replace("  external-removals: ALL", "  external-removals: PLAYERS");
        String stripped = user.replace("  confirm-with-api: true\n", "");

        YamlMerger.Result result = YamlMerger.merge(defaults, stripped);

        assertNull(result.getFailure());
        assertTrue(result.getAdded().contains("execution.confirm-with-api"));

        Map<String, Object> merged = YamlMerger.flatten(parse(result.getText()));
        assertEquals("survival", merged.get("server-name"));
        assertEquals("CONSOLE", merged.get("execution.execute-as"));
        assertEquals(30, merged.get("execution.duplicate-window-seconds"));
        assertEquals(Boolean.TRUE, merged.get("execution.litebans-defaults.ip-bans"));
        assertEquals("PLAYERS", merged.get("tracking.external-removals"));
        assertEquals(Boolean.TRUE, merged.get("execution.confirm-with-api"));
        assertTrue(result.getText().contains("# Categories and escalation tiers"));
    }
}
