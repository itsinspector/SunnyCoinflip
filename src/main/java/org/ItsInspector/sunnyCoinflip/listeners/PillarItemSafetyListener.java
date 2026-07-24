package org.ItsInspector.sunnyCoinflip.listeners;

import org.ItsInspector.sunnyCoinflip.SunnyCoinflip;
import org.ItsInspector.sunnyCoinflip.models.PillarMatch;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;

/** Impedisce ai partecipanti di spostare o buttare il kit durante Pillars. */
public final class PillarItemSafetyListener implements Listener {
    private final SunnyCoinflip plugin;

    public PillarItemSafetyListener(SunnyCoinflip plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (isParticipant(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player && isParticipant(player)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player && isParticipant(player)) event.setCancelled(true);
    }

    private boolean isParticipant(Player player) {
        PillarMatch match = plugin.getGameManager().getActivePillarMatch();
        return match != null && (match.getCreator().equals(player.getUniqueId())
                || (match.getOpponent() != null && match.getOpponent().equals(player.getUniqueId())));
    }
}
