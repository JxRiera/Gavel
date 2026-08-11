package dev.jxriera.gavel.config;

import dev.jxriera.gavel.util.Text;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashMap;
import java.util.Map;

public final class Messages {
    private FileConfiguration config;

    public void bind(FileConfiguration config) {
        this.config = config;
    }

    public String prefix() {
        return config == null ? "" : config.getString("prefix", "");
    }

    public ConfigurationSection section(String path) {
        return config == null ? null : config.getConfigurationSection(path);
    }

    public String raw(String path) {
        return config == null ? "" : config.getString(path, "");
    }

    public String word(String key) {
        String value = raw("words." + key);
        return value == null || value.isEmpty() ? key : value;
    }

    public String bool(boolean value) {
        return word(value ? "boolean-true" : "boolean-false");
    }

    public String dateFormat() {
        String value = raw("date-format");
        return value == null || value.isEmpty() ? "dd/MM/yyyy HH:mm" : value;
    }

    public String get(String path, Map<String, String> placeholders) {
        String value = raw(path);
        if (value == null) {
            return "";
        }
        Map<String, String> all = new HashMap<String, String>();
        if (placeholders != null) {
            all.putAll(placeholders);
        }
        all.put("prefix", prefix());
        return Text.color(Text.apply(value, all));
    }

    public String get(String path) {
        return get(path, null);
    }

    public void send(CommandSender target, String path, Map<String, String> placeholders) {
        if (target == null) {
            return;
        }
        String value = raw(path);
        if (value == null || value.trim().isEmpty()) {
            return;
        }
        for (String line : get(path, placeholders).split("\n")) {
            target.sendMessage(line);
        }
    }

    public void send(CommandSender target, String path) {
        send(target, path, null);
    }

    public static Map<String, String> map(String... pairs) {
        Map<String, String> out = new HashMap<String, String>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            out.put(pairs[i], pairs[i + 1]);
        }
        return out;
    }
}
