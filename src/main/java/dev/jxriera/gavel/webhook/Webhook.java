package dev.jxriera.gavel.webhook;

import dev.jxriera.gavel.Gavel;
import org.bukkit.Bukkit;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.logging.Level;

public final class Webhook {

    private final Gavel plugin;

    public Webhook(Gavel plugin) {
        this.plugin = plugin;
    }

    public void send(String payloadKey, Map<String, String> placeholders) {
        if (!plugin.config().isWebhookEnabled()) {
            return;
        }
        final String url = plugin.config().getWebhookUrl();
        final String template = plugin.config().getWebhookPayload(payloadKey);
        if (url == null || url.trim().isEmpty() || template == null || template.trim().isEmpty()) {
            return;
        }
        final String body = fill(template, placeholders);
        try {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, new Runnable() {
                @Override
                public void run() {
                    post(url.trim(), body);
                }
            });
        } catch (Throwable ignored) {
            plugin.getLogger().warning("Could not schedule the webhook delivery.");
        }
    }

    private void post(String url, String body) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setRequestProperty("User-Agent", "Gavel/" + plugin.getDescription().getVersion());
            connection.setConnectTimeout(plugin.config().getWebhookTimeoutMillis());
            connection.setReadTimeout(plugin.config().getWebhookTimeoutMillis());
            connection.setDoOutput(true);

            byte[] payload = body.getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(payload.length);
            OutputStream stream = connection.getOutputStream();
            try {
                stream.write(payload);
            } finally {
                stream.close();
            }

            int status = connection.getResponseCode();
            if (status >= 400) {
                plugin.getLogger().warning("The webhook at " + redact(url) + " answered HTTP " + status + ".");
            } else if (plugin.config().isDebug()) {
                plugin.getLogger().info("Webhook delivered, HTTP " + status + ".");
            }
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING,
                    "Could not deliver the webhook to " + redact(url), ex);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    static String redact(String url) {
        if (url == null) {
            return "?";
        }
        try {
            URL parsed = new URL(url);
            return parsed.getProtocol() + "://" + parsed.getHost() + "/...";
        } catch (Exception ex) {
            return "the configured URL";
        }
    }

    static String fill(String template, Map<String, String> placeholders) {
        String out = template;
        if (placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                out = out.replace("%" + entry.getKey() + "%", escape(entry.getValue()));
            }
        }
        return out;
    }

    static String escape(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder(value.length() + 8);
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"':
                    builder.append("\\\"");
                    break;
                case '\\':
                    builder.append("\\\\");
                    break;
                case '\n':
                    builder.append("\\n");
                    break;
                case '\r':
                    builder.append("\\r");
                    break;
                case '\t':
                    builder.append("\\t");
                    break;
                default:
                    if (character < 0x20) {
                        builder.append(String.format("\\u%04x", (int) character));
                    } else {
                        builder.append(character);
                    }
            }
        }
        return builder.toString();
    }

    public static String now(String pattern) {
        try {
            return new SimpleDateFormat(pattern).format(new Date());
        } catch (Exception ex) {
            return String.valueOf(System.currentTimeMillis());
        }
    }
}
