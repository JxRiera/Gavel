package dev.jxriera.gavel.placeholder;

import dev.jxriera.gavel.Gavel;
import dev.jxriera.gavel.escalation.EscalationEngine;
import dev.jxriera.gavel.model.Category;
import dev.jxriera.gavel.util.Durations;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;

import java.util.Locale;

public final class GavelExpansion extends PlaceholderExpansion {

    private final Gavel plugin;

    public GavelExpansion(Gavel plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "gavel";
    }

    @Override
    public String getAuthor() {
        return "jxriera";
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        if (player == null || params == null) {
            return null;
        }
        String query = params.toLowerCase(Locale.ROOT);

        if (query.equals("offenses") || query.equals("offences")) {
            return String.valueOf(plugin.cache().total(player.getUniqueId()));
        }
        if (query.equals("cached")) {
            return String.valueOf(plugin.cache().isCached(player.getUniqueId()));
        }

        Category category = categoryFor(query, "offenses_", "offences_");
        if (category != null) {
            return String.valueOf(plugin.cache().count(player.getUniqueId(), category.getId()));
        }

        category = categoryFor(query, "next_number_");
        if (category != null) {
            return String.valueOf(next(player, category).getTier().getNumber());
        }

        category = categoryFor(query, "next_type_");
        if (category != null) {
            return plugin.config().messages().word(next(player, category).getTier().getType().wordKey());
        }

        category = categoryFor(query, "next_duration_");
        if (category != null) {
            return duration(next(player, category));
        }

        category = categoryFor(query, "next_reason_");
        if (category != null) {
            return next(player, category).getTier().getReason();
        }
        return null;
    }

    private EscalationEngine.Result next(OfflinePlayer player, Category category) {
        return plugin.cache().next(player.getUniqueId(), category);
    }

    private String duration(EscalationEngine.Result result) {
        if (!result.getTier().getType().supportsDuration()) {
            return plugin.config().messages().word("none");
        }
        return Durations.display(result.getTier().getDuration(),
                plugin.config().messages().word("permanent"));
    }

    private Category categoryFor(String query, String... prefixes) {
        for (String prefix : prefixes) {
            if (query.startsWith(prefix)) {
                return plugin.config().getCategory(query.substring(prefix.length()));
            }
        }
        return null;
    }
}
