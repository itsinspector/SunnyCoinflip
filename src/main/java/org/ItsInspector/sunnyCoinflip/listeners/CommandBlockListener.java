package org.ItsInspector.sunnyCoinflip.listeners;

import org.ItsInspector.sunnyCoinflip.SunnyCoinflip;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

/** Blocca i comandi esterni durante le partite, lasciando disponibili quelli del plugin. */
public final class CommandBlockListener implements Listener {
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        SunnyCoinflip plugin = SunnyCoinflip.getInstance();
        boolean bedwars = plugin.getBedfightManager().isActiveParticipant(event.getPlayer().getUniqueId());
        boolean pillars = plugin.getGameManager().getActivePillarMatch() != null
                && (plugin.getGameManager().getActivePillarMatch().getCreator().equals(event.getPlayer().getUniqueId())
                || event.getPlayer().getUniqueId().equals(plugin.getGameManager().getActivePillarMatch().getOpponent()));
        if (!bedwars && !pillars) return;
        String command = event.getMessage().toLowerCase();
        if (command.startsWith("/cf") || command.startsWith("/coinflip")) return;
        event.setCancelled(true);
    }
}
