package org.ItsInspector.sunnyCoinflip.integrations;

import java.util.Locale;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.ItsInspector.sunnyCoinflip.SunnyCoinflip;
import org.ItsInspector.sunnyCoinflip.managers.BedfightManager;
import org.ItsInspector.sunnyCoinflip.models.BedfightCoinflip;
import org.ItsInspector.sunnyCoinflip.utils.ItemBuilder;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;

public final class BedfightExpansion extends PlaceholderExpansion {
    private final SunnyCoinflip plugin;
    private final String identifier;

    public BedfightExpansion(SunnyCoinflip plugin) {
        this(plugin, "bedfight");
    }

    public BedfightExpansion(SunnyCoinflip plugin, String identifier) {
        this.plugin = plugin;
        this.identifier = identifier;
    }

    public String getIdentifier() {
        return this.identifier;
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
                    var10000 = this.colored(manager.isAvailable() ? this.configText("bedfight.placeholders.available", "&aDisponibile") : this.configText("bedfight.placeholders.unavailable", "&cNon disponibile"));
                    break;
                case "round_status":
                case "status":
                case "state":
                    var10000 = this.roundStatus(manager);
                    break;
                case "detailed_status":
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

    private String roundStatus(BedfightManager manager) {
        if (!manager.isEnabled() || !manager.isArenaConfigured()
                || manager.getActiveMatch() != null) {
            return ItemBuilder.translate("&f\ue060&c&lɴᴏɴ ᴅɪѕᴘᴏɴɪʙɪʟᴇ.");
        }

        BedfightCoinflip waiting = manager.getWaitingChallenges()
                .stream()
                .findFirst()
                .orElse(null);
        if (waiting != null) {
            return ItemBuilder.translate(
                    "&f\ue03c &a&lᴜɴɪѕᴄɪᴛɪ ᴘᴇʀ &f\ue0d8&e"
                            + String.format("%.0f", waiting.getAmount()));
        }

        return ItemBuilder.translate("&a&lᴅɪѕᴘᴏɴɪʙɪʟᴇ.");
    }

    private String detailedStatus(BedfightManager manager) {
        if (!manager.isEnabled()) {
            return this.colored(this.configText("bedfight.placeholders.disabled", "&cDisabilitato"));
        } else if (!manager.isArenaConfigured()) {
            return this.colored(this.configText("bedfight.placeholders.not-configured", "&eNon configurato"));
        } else {
            return !manager.isAvailable() ? this.colored(this.configText("bedfight.placeholders.busy", "&cOccupato")) : this.colored(this.configText("bedfight.placeholders.available", "&aDisponibile"));
        }
    }

    private String configText(String path, String fallback) {
        return this.plugin.getConfig().getString(path, fallback);
    }

    private String colored(String text) {
        return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text);
    }
}
