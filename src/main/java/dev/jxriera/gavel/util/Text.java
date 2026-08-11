package dev.jxriera.gavel.util;

import org.bukkit.ChatColor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Text {
    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static final boolean HEX_SUPPORTED = detectHexSupport();

    private Text() {
    }

    private static boolean detectHexSupport() {
        try {
            Class.forName("net.md_5.bungee.api.ChatColor").getMethod("of", String.class);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static String color(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        String out = input;
        if (HEX_SUPPORTED) {
            Matcher matcher = HEX_PATTERN.matcher(out);
            StringBuffer buffer = new StringBuffer();
            while (matcher.find()) {
                String replacement;
                try {
                    replacement = net.md_5.bungee.api.ChatColor.of("#" + matcher.group(1)).toString();
                } catch (Throwable ignored) {
                    replacement = "";
                }
                matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement));
            }
            matcher.appendTail(buffer);
            out = buffer.toString();
        } else {
            out = HEX_PATTERN.matcher(out).replaceAll("");
        }
        return ChatColor.translateAlternateColorCodes('&', out);
    }

    public static List<String> color(List<String> input) {
        List<String> out = new ArrayList<String>();
        if (input == null) {
            return out;
        }
        for (String line : input) {
            out.add(color(line));
        }
        return out;
    }

    public static String apply(String input, Map<String, String> placeholders) {
        if (input == null) {
            return "";
        }
        String out = input;
        if (placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                String value = entry.getValue() == null ? "" : entry.getValue();
                out = out.replace("%" + entry.getKey() + "%", value);
            }
        }
        return out;
    }

}
