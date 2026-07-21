package org.ItsInspector.sunnyCoinflip.integrations;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.ItsInspector.sunnyCoinflip.SunnyCoinflip;
import org.ItsInspector.sunnyCoinflip.models.PillarMatch;
import org.ItsInspector.sunnyCoinflip.utils.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class PillarExpansion extends PlaceholderExpansion {

    private final SunnyCoinflip plugin;

    public PillarExpansion(SunnyCoinflip plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "pillars";
    }

    @Override
    public String getAuthor() {
        return "ItsInspector";
    }

    @Override
    public String getVersion() {
        return "1.0";
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onPlaceholderRequest(Player player, String params) {
        if (params.equalsIgnoreCase("round_status")) {
            PillarMatch match = plugin.getGameManager().getActivePillarMatch();
            if (match == null) {
                return ItemBuilder.translate("&a&lᴅɪѕᴘᴏɴɪʙɪʟᴇ.");
            }
            if (match.getOpponent() == null) {
                return ItemBuilder.translate("&f &a&lᴜɴɪѕᴄɪᴛɪ ᴘᴇʀ &f&e" + String.format("%.0f", match.getAmount()));
            }
            return ItemBuilder.translate("&f&c&lɴᴏɴ ᴅɪѕᴘᴏɴɪʙɪʟᴇ.");
        }

        if (params.equalsIgnoreCase("players")) {
            int count = 0;
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (plugin.getGameManager().isPillarWorld(p.getWorld())) {
                    count++;
                }
            }
            return String.valueOf(count);
        }

        return null;
    }
}
