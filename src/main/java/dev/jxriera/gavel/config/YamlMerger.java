package dev.jxriera.gavel.config;

import org.yaml.snakeyaml.Yaml;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class YamlMerger {

    public static final class Result {
        private final String text;
        private final List<String> added;
        private final String failure;

        private Result(String text, List<String> added, String failure) {
            this.text = text;
            this.added = added == null ? Collections.<String>emptyList() : added;
            this.failure = failure;
        }

        public String getText() {
            return text;
        }

        public List<String> getAdded() {
            return added;
        }

        public String getFailure() {
            return failure;
        }

        public boolean isChanged() {
            return failure == null && !added.isEmpty();
        }
    }

    private static final Pattern KEY = Pattern.compile("^(\\s*)([^\\s#:][^:]*):(.*)$");

    private final List<String> lines;
    private final Map<String, Object> user;
    private final List<String> added = new ArrayList<String>();
    private final StringBuilder out = new StringBuilder();
    private int cursor;

    private YamlMerger(List<String> lines, Map<String, Object> user) {
        this.lines = lines;
        this.user = user;
    }

    public static Result merge(String defaultsText, String userText) {
        Map<String, Object> user;
        Map<String, Object> defaults;
        try {
            user = asMap(new Yaml().load(userText));
            defaults = asMap(new Yaml().load(defaultsText));
        } catch (Exception ex) {
            return new Result(userText, null, "the existing file is not valid YAML: " + ex.getMessage());
        }
        if (user.isEmpty()) {
            return new Result(defaultsText, null, null);
        }

        YamlMerger merger = new YamlMerger(splitLines(defaultsText), user);
        String merged;
        try {
            merged = merger.walk("", 0);
        } catch (Exception ex) {
            return new Result(userText, null, "the merge could not be built: " + ex);
        }
        if (merger.added.isEmpty()) {
            return new Result(userText, null, null);
        }

        String problem = verify(merged, user, defaults);
        if (problem != null) {
            return new Result(userText, null, problem);
        }
        return new Result(merged, merger.added, null);
    }

    private String walk(String prefix, int depth) {
        while (cursor < lines.size()) {
            String line = lines.get(cursor);
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                out.append(line).append('\n');
                cursor++;
                continue;
            }
            Matcher matcher = KEY.matcher(line);
            if (!matcher.matches()) {
                out.append(line).append('\n');
                cursor++;
                continue;
            }
            int indent = matcher.group(1).length();
            if (indent < depth) {
                break;
            }
            String key = matcher.group(2).trim();
            String value = matcher.group(3).trim();
            String path = prefix.isEmpty() ? key : prefix + "." + key;
            cursor++;

            if (value.isEmpty() && startsBlockList(indent)) {
                List<String> defaultItems = takeBlockList(indent);
                emitBlockList(matcher.group(1), key, path, line, defaultItems);
                continue;
            }
            if (value.isEmpty()) {
                out.append(line).append('\n');
                walk(path, indent + 1);
                continue;
            }
            if (value.startsWith("|") || value.startsWith(">")) {
                out.append(line).append('\n');
                copyBlockScalar(indent);
                continue;
            }
            if (value.startsWith("[")) {
                emitInlineList(matcher.group(1), key, path, line);
                continue;
            }
            emitScalar(matcher.group(1), key, path, line);
        }
        return out.toString();
    }

    private void emitScalar(String indent, String key, String path, String defaultLine) {
        if (!contains(path)) {
            added.add(path);
            out.append(defaultLine).append('\n');
            return;
        }
        out.append(indent).append(key).append(": ").append(scalar(lookup(path))).append('\n');
    }

    private void emitInlineList(String indent, String key, String path, String defaultLine) {
        if (!contains(path)) {
            added.add(path);
            out.append(defaultLine).append('\n');
            return;
        }
        out.append(indent).append(key).append(": ").append(inline(asList(lookup(path)))).append('\n');
    }

    private void emitBlockList(String indent, String key, String path, String headerLine,
                               List<String> defaultItems) {
        if (!contains(path)) {
            added.add(path);
            out.append(headerLine).append('\n');
            for (String item : defaultItems) {
                out.append(item).append('\n');
            }
            return;
        }
        List<?> values = asList(lookup(path));
        if (values.isEmpty()) {
            out.append(indent).append(key).append(": []").append('\n');
            return;
        }
        out.append(headerLine).append('\n');
        for (Object value : values) {
            out.append(indent).append("  - ").append(scalar(value)).append('\n');
        }
    }

    private boolean startsBlockList(int indent) {
        for (int index = cursor; index < lines.size(); index++) {
            String trimmed = lines.get(index).trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            return indentOf(lines.get(index)) > indent && trimmed.startsWith("- ");
        }
        return false;
    }

    private List<String> takeBlockList(int indent) {
        List<String> items = new ArrayList<String>();
        while (cursor < lines.size()) {
            String line = lines.get(cursor);
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                items.add(line);
                cursor++;
                continue;
            }
            if (indentOf(line) > indent && trimmed.startsWith("- ")) {
                items.add(line);
                cursor++;
                continue;
            }
            break;
        }
        while (!items.isEmpty() && items.get(items.size() - 1).trim().isEmpty()) {
            items.remove(items.size() - 1);
            cursor--;
        }
        return items;
    }

    private void copyBlockScalar(int indent) {
        while (cursor < lines.size()) {
            String line = lines.get(cursor);
            if (line.trim().isEmpty() || indentOf(line) > indent) {
                out.append(line).append('\n');
                cursor++;
                continue;
            }
            return;
        }
    }

    private static int indentOf(String line) {
        int index = 0;
        while (index < line.length() && line.charAt(index) == ' ') {
            index++;
        }
        return index;
    }

    private Object lookup(String path) {
        Object current = user;
        for (String part : path.split("\\.")) {
            if (!(current instanceof Map)) {
                return null;
            }
            current = ((Map<?, ?>) current).get(part);
        }
        return current;
    }

    private boolean contains(String path) {
        Object current = user;
        for (String part : path.split("\\.")) {
            if (!(current instanceof Map)) {
                return false;
            }
            Map<?, ?> map = (Map<?, ?>) current;
            if (!map.containsKey(part)) {
                return false;
            }
            current = map.get(part);
        }
        return true;
    }

    private static List<?> asList(Object value) {
        return value instanceof List ? (List<?>) value : Collections.emptyList();
    }

    static String scalar(Object value) {
        if (value == null) {
            return "''";
        }
        if (value instanceof Boolean || value instanceof Number) {
            return String.valueOf(value);
        }
        return "'" + String.valueOf(value).replace("'", "''") + "'";
    }

    static String inline(List<?> values) {
        if (values == null || values.isEmpty()) {
            return "[]";
        }
        StringBuilder builder = new StringBuilder("[");
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) {
                builder.append(", ");
            }
            builder.append(scalar(values.get(index)));
        }
        return builder.append(']').toString();
    }

    static String verify(String merged, Map<String, Object> user, Map<String, Object> defaults) {
        Map<String, Object> result;
        try {
            result = asMap(new Yaml().load(merged));
        } catch (Exception ex) {
            return "the merged file would not parse: " + ex.getMessage();
        }
        Map<String, Object> flatMerged = flatten(result);
        for (Map.Entry<String, Object> entry : flatten(user).entrySet()) {
            if (!flatMerged.containsKey(entry.getKey())) {
                return "the merge would drop '" + entry.getKey() + "'";
            }
            if (!equal(entry.getValue(), flatMerged.get(entry.getKey()))) {
                return "the merge would change '" + entry.getKey() + "'";
            }
        }
        for (String key : flatten(defaults).keySet()) {
            if (!flatMerged.containsKey(key)) {
                return "the merge would still be missing '" + key + "'";
            }
        }
        return null;
    }

    private static boolean equal(Object left, Object right) {
        if (left == null || right == null) {
            return left == right;
        }
        if (left instanceof Number && right instanceof Number) {
            return ((Number) left).doubleValue() == ((Number) right).doubleValue();
        }
        return left.equals(right) || String.valueOf(left).equals(String.valueOf(right));
    }

    static Map<String, Object> flatten(Map<String, Object> source) {
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        flatten("", source, out);
        return out;
    }

    private static void flatten(String prefix, Map<?, ?> source, Map<String, Object> out) {
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            String path = prefix.isEmpty() ? String.valueOf(entry.getKey())
                    : prefix + "." + entry.getKey();
            Object value = entry.getValue();
            if (value instanceof Map) {
                flatten(path, (Map<?, ?>) value, out);
            } else {
                out.put(path, value);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object loaded) {
        if (loaded instanceof Map) {
            return (Map<String, Object>) loaded;
        }
        return new LinkedHashMap<String, Object>();
    }

    private static List<String> splitLines(String text) {
        List<String> out = new ArrayList<String>();
        for (String line : text.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1)) {
            out.add(line);
        }
        if (!out.isEmpty() && out.get(out.size() - 1).isEmpty()) {
            out.remove(out.size() - 1);
        }
        return out;
    }
}
