package org.ItsInspector.sunnyCoinflip.integrations;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.ItsInspector.sunnyCoinflip.SunnyCoinflip;
import org.ItsInspector.sunnyCoinflip.models.PillarMatch;
import org.ItsInspector.sunnyCoinflip.utils.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PillarExpansion extends PlaceholderExpansion {
    private final SunnyCoinflip plugin;
    public PillarExpansion(SunnyCoinflip plugin) { this.plugin = plugin; }
    @Override public @NotNull String getIdentifier() { return "pillars"; }
    @Override public @NotNull String getAuthor() { return "ItsInspector"; }
    @Override public @NotNull String getVersion() { return plugin.getDescription().getVersion(); }
    @Override public boolean persist() { return true; }
    @Override public @Nullable String onPlaceholderRequest(Player player, @NotNull String params) {
        if (params.equalsIgnoreCase("round_status")) {
            PillarMatch match = plugin.getGameManager().getActivePillarMatch();
            if (match == null) return ItemBuilder.translate("&a&lᴅɪѕᴘᴏɴɪʙɪʟᴇ.");
            if (match.getOpponent() == null) return ItemBuilder.translate("&a&lᴜɴɪѕᴄɪᴛɪ ᴘᴇʀ &f&e" + String.format("%.0f", match.getAmount()));
            return ItemBuilder.translate("&c&lɴᴏɴ ᴅɪѕᴘᴏɴɪʙɪʟᴇ.");
        }
        if (params.equalsIgnoreCase("players")) {
            return String.valueOf(Bukkit.getOnlinePlayers().stream().filter(p -> plugin.getGameManager().isPillarWorld(p.getWorld())).count());
        }
        return null;
    }
}
