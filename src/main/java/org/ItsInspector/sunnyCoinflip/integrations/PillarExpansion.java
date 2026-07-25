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

    public String getIdentifier() {
        return "pillars";
    }

    public String getAuthor() {
        return "ItsInspector";
    }

    public String getVersion() {
        return "1.0";
    }

    public boolean persist() {
        return true;
    }

    public String onPlaceholderRequest(Player player, String params) {
        if (params.equalsIgnoreCase("round_status")) {
            PillarMatch match = this.plugin.getGameManager().getActivePillarMatch();
            if (match == null) {
                return ItemBuilder.translate("&a&lᴅɪѕᴘᴏɴɪʙɪʟᴇ.");
            } else if (match.getOpponent() == null) {
                Object[] var10001 = new Object[]{match.getAmount()};
                return ItemBuilder.translate("&f\ue03c &a&lᴜɴɪѕᴄɪᴛɪ ᴘᴇʀ &f\ue0d8&e" + String.format("%.0f", var10001));
            } else {
                return ItemBuilder.translate("&f\ue060&c&lɴᴏɴ ᴅɪѕᴘᴏɴɪʙɪʟᴇ.");
            }
        } else if (params.equalsIgnoreCase("players")) {
            int count = 0;

            for(Player p : Bukkit.getOnlinePlayers()) {
                if (this.plugin.getGameManager().isPillarWorld(p.getWorld())) {
                    ++count;
                }
            }

            return String.valueOf(count);
        } else {
            return null;
        }
    }
}
