package org.ItsInspector.sunnyCoinflip.integrations;

import java.util.Locale;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.ItsInspector.sunnyCoinflip.SunnyCoinflip;
import org.ItsInspector.sunnyCoinflip.managers.BedfightManager;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;

public final class BedwarsExpansion extends PlaceholderExpansion {
    private final SunnyCoinflip plugin;

    public BedwarsExpansion(SunnyCoinflip plugin) {
        this.plugin = plugin;
    }

    public String getIdentifier() {
        return "bedwars";
    }

    public String getAuthor() {
        return "ItsInspector";
    }

    public String getVersion() {
        return this.plugin.getDescription().getVersion();
    }

    public boolean persist() {
        return true;
    }

    public String onRequest(OfflinePlayer player, String params) {
        if (params == null) {
            return null;
        } else {
            BedfightManager manager = this.plugin.getBedfightManager();
            String var10000;
            switch (params.toLowerCase(Locale.ROOT)) {
                case "available":
                case "disponibile":
                    var10000 = this.colored(manager.isAvailable() ? this.configText("bedwars.placeholders.available", "&aDisponibile") : this.configText("bedwars.placeholders.unavailable", "&cNon disponibile"));
                    break;
                case "status":
                case "state":
                    var10000 = this.detailedStatus(manager);
                    break;
                case "available_boolean":
                case "is_available":
                    var10000 = Boolean.toString(manager.isAvailable());
                    break;
                case "enabled":
                    var10000 = Boolean.toString(manager.isEnabled());
                    break;
                case "configured":
                    var10000 = Boolean.toString(manager.isArenaConfigured());
                    break;
                case "playing":
                case "in_game":
                    var10000 = Boolean.toString(manager.isPlaying());
                    break;
                default:
                    var10000 = null;
            }

            return var10000;
        }
    }

    private String detailedStatus(BedfightManager manager) {
        if (!manager.isEnabled()) {
            return this.colored(this.configText("bedwars.placeholders.disabled", "&cDisabilitato"));
        } else if (!manager.isArenaConfigured()) {
            return this.colored(this.configText("bedwars.placeholders.not-configured", "&eNon configurato"));
        } else {
            return !manager.isAvailable() ? this.colored(this.configText("bedwars.placeholders.busy", "&cOccupato")) : this.colored(this.configText("bedwars.placeholders.available", "&aDisponibile"));
        }
    }

    private String configText(String path, String fallback) {
        return this.plugin.getConfig().getString(path, fallback);
    }

    private String colored(String text) {
        return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text);
    }
}
