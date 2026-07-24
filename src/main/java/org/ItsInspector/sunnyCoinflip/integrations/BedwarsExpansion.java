package org.ItsInspector.sunnyCoinflip.integrations;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.ItsInspector.sunnyCoinflip.SunnyCoinflip;
import org.ItsInspector.sunnyCoinflip.managers.BedfightManager;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.util.Locale;

public final class BedwarsExpansion extends PlaceholderExpansion {
    private final SunnyCoinflip plugin;
    public BedwarsExpansion(SunnyCoinflip plugin) { this.plugin = plugin; }
    @Override public @NotNull String getIdentifier() { return "bedwars"; }
    @Override public @NotNull String getAuthor() { return "ItsInspector"; }
    @Override public @NotNull String getVersion() { return plugin.getDescription().getVersion(); }
    @Override public boolean persist() { return true; }
    @Override public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        BedfightManager manager = plugin.getBedfightManager();
        return switch (params.toLowerCase(Locale.ROOT)) {
            case "available", "disponibile" -> color(manager.isAvailable() ? config("bedwars.placeholders.available", "&f\uE03C &a&lᴅɪѕᴘᴏɴɪʙɪʟᴇ.") : config("bedwars.placeholders.unavailable", "&f\uE03C &c&lɴᴏɴ ᴅɪѕᴘᴏɴɪʙɪʟᴇ."));
            case "available_boolean", "is_available" -> Boolean.toString(manager.isAvailable());
            case "enabled" -> Boolean.toString(manager.isEnabled());
            case "configured" -> Boolean.toString(manager.isArenaConfigured());
            case "playing", "in_game" -> Boolean.toString(manager.isPlaying());
            case "status", "state" -> color(!manager.isEnabled() ? config("bedwars.placeholders.disabled", "&f\uE03C &c&lɴᴏɴ ᴅɪѕᴘᴏɴɪʙɪʟᴇ.") : !manager.isArenaConfigured() ? config("bedwars.placeholders.not-configured", "&eNon configurato") : manager.isAvailable() ? config("bedwars.placeholders.available", "&f\uE03C &a&lᴅɪѕᴘᴏɴɪʙɪʟᴇ.") : config("bedwars.placeholders.busy", "&f\uE03C &c&lᴏᴄᴄᴜᴘᴀᴛᴏ."));
            default -> null;
        };
    }
    private String config(String path, String fallback) { return plugin.getConfig().getString(path, fallback); }
    private String color(String text) { return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text); }
}
