package org.ItsInspector.sunnyCoinflip.integrations;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.ItsInspector.sunnyCoinflip.SunnyCoinflip;
import org.ItsInspector.sunnyCoinflip.managers.BedfightManager;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;

import java.util.Locale;

/**
 * PlaceholderAPI expansion for the internal SunnyCoinflip BedWars arena.
 *
 * Main placeholder: %bedwars_available%
 */
public final class BedwarsExpansion extends PlaceholderExpansion {

    private final SunnyCoinflip plugin;

    public BedwarsExpansion(SunnyCoinflip plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "bedwars";
    }

    @Override
    public String getAuthor() {
        return "ItsInspector";
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
        if (params == null) {
            return null;
        }

        BedfightManager manager = plugin.getBedfightManager();
        String key = params.toLowerCase(Locale.ROOT);

        return switch (key) {
            case "available", "disponibile" -> colored(manager.isAvailable()
                    ? configText("bedwars.placeholders.available", "&aDisponibile")
                    : configText("bedwars.placeholders.unavailable", "&cNon disponibile"));
            case "status", "state" -> detailedStatus(manager);
            case "available_boolean", "is_available" -> Boolean.toString(manager.isAvailable());
            case "enabled" -> Boolean.toString(manager.isEnabled());
            case "configured" -> Boolean.toString(manager.isArenaConfigured());
            case "playing", "in_game" -> Boolean.toString(manager.isPlaying());
            default -> null;
        };
    }

    private String detailedStatus(BedfightManager manager) {
        if (!manager.isEnabled()) {
            return colored(configText("bedwars.placeholders.disabled", "&cDisabilitato"));
        }
        if (!manager.isArenaConfigured()) {
            return colored(configText("bedwars.placeholders.not-configured", "&eNon configurato"));
        }
        if (!manager.isAvailable()) {
            return colored(configText("bedwars.placeholders.busy", "&cOccupato"));
        }
        return colored(configText("bedwars.placeholders.available", "&aDisponibile"));
    }

    private String configText(String path, String fallback) {
        return plugin.getConfig().getString(path, fallback);
    }

    private String colored(String text) {
        return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text);
    }
}
