package dev.jxriera.gavel.config;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YamlMergerTest {

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parse(String text) {
        return (Map<String, Object>) new Yaml().load(text);
    }

    @Test
    void addsMissingKeysAndKeepsTheExistingValues() {
        String defaults = "# header\n"
                + "server-name: 'main'\n"
                + "\n"
                + "execution:\n"
                + "  # how it runs\n"
                + "  execute-as: PLAYER\n"
                + "  verify-permissions: true\n";
        String user = "server-name: 'lobby'\n"
                + "execution:\n"
                + "  execute-as: CONSOLE\n";

        YamlMerger.Result result = YamlMerger.merge(defaults, user);

        assertNull(result.getFailure());
        assertTrue(result.isChanged());
        assertEquals(1, result.getAdded().size());
        assertEquals("execution.verify-permissions", result.getAdded().get(0));

        Map<String, Object> merged = parse(result.getText());
        assertEquals("lobby", merged.get("server-name"));
        assertEquals("CONSOLE", ((Map<?, ?>) merged.get("execution")).get("execute-as"));
        assertEquals(Boolean.TRUE, ((Map<?, ?>) merged.get("execution")).get("verify-permissions"));
    }

    @Test
    void keepsTheComments() {
        String defaults = "# top comment\nserver-name: 'main'\n# another\ndebug: false\n";
        String user = "server-name: 'lobby'\n";

        String merged = YamlMerger.merge(defaults, user).getText();

        assertTrue(merged.contains("# top comment"));
        assertTrue(merged.contains("# another"));
    }

    @Test
    void keepsAUserBlockList() {
        String defaults = "intercept:\n  commands:\n    - ban\n    - mute\n  enabled: true\n";
        String user = "intercept:\n  commands:\n    - ban\n    - tempban\n    - kick\n";

        YamlMerger.Result result = YamlMerger.merge(defaults, user);
        Map<String, Object> merged = parse(result.getText());

        List<?> commands = (List<?>) ((Map<?, ?>) merged.get("intercept")).get("commands");
        assertEquals(3, commands.size());
        assertTrue(commands.contains("tempban"));
        assertFalse(commands.contains("mute"));
    }

    @Test
    void addsAMissingBlockListWithItsDefaultItems() {
        String defaults = "intercept:\n  enabled: true\n  commands:\n    - ban\n    - mute\n";
        String user = "intercept:\n  enabled: false\n";

        YamlMerger.Result result = YamlMerger.merge(defaults, user);
        Map<String, Object> merged = parse(result.getText());

        List<?> commands = (List<?>) ((Map<?, ?>) merged.get("intercept")).get("commands");
        assertEquals(2, commands.size());
        assertTrue(commands.contains("ban"));
        assertEquals(Boolean.FALSE, ((Map<?, ?>) merged.get("intercept")).get("enabled"));
    }

    @Test
    void addsAMissingInlineListWithItsDefaultValue() {
        String defaults = "revert:\n  commands:\n    unban: [BAN]\n";
        String user = "revert: {}\n";

        YamlMerger.Result result = YamlMerger.merge(defaults, user);
        Map<String, Object> merged = parse(result.getText());

        List<?> unban = (List<?>) ((Map<?, ?>) ((Map<?, ?>) merged.get("revert")).get("commands")).get("unban");
        assertEquals(1, unban.size());
        assertEquals("BAN", unban.get(0));
    }

    @Test
    void keepsAnEmptyUserList() {
        String defaults = "execution:\n  post-commands: []\n  silent-flag: '-s'\n";
        String user = "execution:\n  post-commands:\n    - 'say hi'\n";

        Map<String, Object> merged = parse(YamlMerger.merge(defaults, user).getText());
        List<?> post = (List<?>) ((Map<?, ?>) merged.get("execution")).get("post-commands");

        assertEquals(1, post.size());
        assertEquals("say hi", post.get(0));
    }

    @Test
    void preservesABlockScalar() {
        String defaults = "usage: |-\n  line one\n  line two\ndebug: false\n";
        String user = "usage: |-\n  line one\n  line two\n";

        YamlMerger.Result result = YamlMerger.merge(defaults, user);

        assertNull(result.getFailure());
        assertEquals("line one\nline two", parse(result.getText()).get("usage"));
        assertEquals(Boolean.FALSE, parse(result.getText()).get("debug"));
    }

    @Test
    void quotesValuesThatWouldBreakTheDocument() {
        String defaults = "prefix: '&8[Gavel] '\nextra: 1\n";
        String user = "prefix: \"it's: tricky #1\"\n";

        Map<String, Object> merged = parse(YamlMerger.merge(defaults, user).getText());

        assertEquals("it's: tricky #1", merged.get("prefix"));
    }

    @Test
    void doesNothingWhenThereIsNothingToAdd() {
        String defaults = "a: 1\nb: 2\n";
        String user = "a: 9\nb: 8\n";

        YamlMerger.Result result = YamlMerger.merge(defaults, user);

        assertFalse(result.isChanged());
        assertNull(result.getFailure());
        assertEquals(user, result.getText());
    }

    @Test
    void refusesToTouchAFileThatDoesNotParse() {
        YamlMerger.Result result = YamlMerger.merge("a: 1\n", "a: [unclosed\n");

        assertFalse(result.isChanged());
        assertTrue(result.getFailure().contains("not valid YAML"));
    }

    @Test
    void anEmptyUserFileTakesTheDefaultsWholesale() {
        String defaults = "a: 1\n";

        YamlMerger.Result result = YamlMerger.merge(defaults, "");

        assertNull(result.getFailure());
        assertEquals(defaults, result.getText());
    }

    @Test
    void numbersAndBooleansAreNotQuoted() {
        assertEquals("5", YamlMerger.scalar(Integer.valueOf(5)));
        assertEquals("true", YamlMerger.scalar(Boolean.TRUE));
        assertEquals("''", YamlMerger.scalar(null));
        assertEquals("'don''t'", YamlMerger.scalar("don't"));
        assertEquals("[]", YamlMerger.inline(null));
    }
}
