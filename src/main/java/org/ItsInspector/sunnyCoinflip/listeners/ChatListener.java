package org.ItsInspector.sunnyCoinflip.listeners;

import org.ItsInspector.sunnyCoinflip.SunnyCoinflip;
import org.ItsInspector.sunnyCoinflip.models.Coinflip;
import org.ItsInspector.sunnyCoinflip.utils.NumberParser;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class ChatListener implements Listener {

    private final SunnyCoinflip plugin;
    private final Set<UUID> pendingCreation = java.util.Collections.synchronizedSet(new HashSet<>());
    private final Set<UUID> pendingPillar = java.util.Collections.synchronizedSet(new HashSet<>());

    public ChatListener(SunnyCoinflip plugin) {
        this.plugin = plugin;
    }

    public void addPending(UUID uuid) {
        pendingCreation.add(uuid);
    }

    public void addPendingPillar(UUID uuid) {
        pendingPillar.add(uuid);
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (pendingCreation.contains(player.getUniqueId())) {
            event.setCancelled(true);
            pendingCreation.remove(player.getUniqueId());

            String message = event.getMessage();
            try {
                double amount = NumberParser.parseNumber(message);
                double maxAmount = plugin.getGameManager().getMaxAmount();
                if (amount <= 0 || amount > maxAmount) {
                    player.sendMessage("§cL'importo deve essere tra 1 e " + String.format("%.0f", maxAmount) + ".");
                    return;
                }

                org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                    if (SunnyCoinflip.getEconomy().getBalance(player) < amount) {
                        player.sendMessage("§cNon hai abbastanza soldi!");
                        return;
                    }

                    if (plugin.getGameManager().getCoinflip(player.getUniqueId()) != null) {
                        player.sendMessage("§cHai già un coinflip attivo!");
                        return;
                    }

                    Coinflip cf = new Coinflip(player.getUniqueId(), player.getName(), amount);
                    plugin.getGameManager().addCoinflip(cf);
                    player.sendMessage("§aCoinflip creato con successo!");
                });

            } catch (IllegalArgumentException e) {
                player.sendMessage("§c" + e.getMessage());
            }
        } else if (pendingPillar.contains(player.getUniqueId())) {
            event.setCancelled(true);
            pendingPillar.remove(player.getUniqueId());

            String message = event.getMessage();
            try {
                double amount = NumberParser.parseNumber(message);
                double maxAmount = plugin.getGameManager().getMaxAmount();
                if (amount <= 0 || amount > maxAmount) {
                    player.sendMessage("§cL'importo deve essere tra 1 e " + String.format("%.0f", maxAmount) + ".");
                    return;
                }

                final org.bukkit.Location returnLoc = player.getLocation();

                org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                    if (SunnyCoinflip.getEconomy().getBalance(player) < amount) {
                        player.sendMessage("§cNon hai abbastanza soldi!");
                        return;
                    }

                    if (plugin.getGameManager().getActivePillarMatch() != null) {
                        player.sendMessage("§cC'è già una partita di Pillars attiva o in attesa!");
                        return;
                    }

                    if (plugin.getGameManager().getCoinflip(player.getUniqueId()) != null) {
                        player.sendMessage("§cHai già un coinflip attivo, non puoi partecipare ai Pillars!");
                        return;
                    }

                    plugin.getGameManager().setPlayerReturn(player.getUniqueId(), returnLoc);
                    player.teleport(plugin.getGameManager().getPillarFirst());
                    player.setHealth(player.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).getValue());
                    player.setFoodLevel(20);
                    
                    org.ItsInspector.sunnyCoinflip.models.PillarMatch match = new org.ItsInspector.sunnyCoinflip.models.PillarMatch(player.getUniqueId(), player.getLocation(), amount);
                    match.setCreatorJoinTime(System.currentTimeMillis());
                    plugin.getGameManager().setActivePillarMatch(match);
                    
                    player.sendMessage("§aPillar creato con successo! In attesa di uno sfidante per §r§f\uE0D8 §e" + String.format("%.0f", amount));
                });

            } catch (IllegalArgumentException e) {
                player.sendMessage("§c" + e.getMessage());
            }
        }
    }

    public void createCoinflipDirect(Player player, String amountStr) {
        try {
            double amount = NumberParser.parseNumber(amountStr);
            double maxAmount = plugin.getGameManager().getMaxAmount();
            
            if (amount <= 0 || amount > maxAmount) {
                player.sendMessage("§cL'importo deve essere tra 1 e " + String.format("%.0f", maxAmount) + ".");
                return;
            }

            if (SunnyCoinflip.getEconomy().getBalance(player) < amount) {
                player.sendMessage("§cNon hai abbastanza soldi!");
                return;
            }

            if (plugin.getGameManager().getCoinflip(player.getUniqueId()) != null) {
                player.sendMessage("§cHai già un coinflip attivo!");
                return;
            }

            Coinflip cf = new Coinflip(player.getUniqueId(), player.getName(), amount);
            plugin.getGameManager().addCoinflip(cf);
            player.sendMessage("§aCoinflip creato con successo per §r§f\uE0D8 §e" + String.format("%.0f", amount) + "§a!");
        } catch (IllegalArgumentException e) {
            player.sendMessage("§c" + e.getMessage());
        }
    }

    public void createPillarDirect(Player player, String amountStr) {
        try {
            double amount = NumberParser.parseNumber(amountStr);
            double maxAmount = plugin.getGameManager().getMaxAmount();
            
            if (amount <= 0 || amount > maxAmount) {
                player.sendMessage("§cL'importo deve essere tra 1 e " + String.format("%.0f", maxAmount) + ".");
                return;
            }

            if (SunnyCoinflip.getEconomy().getBalance(player) < amount) {
                player.sendMessage("§cNon hai abbastanza soldi!");
                return;
            }

            if (plugin.getGameManager().getActivePillarMatch() != null) {
                player.sendMessage("§cC'è già una partita di Pillars attiva o in attesa!");
                return;
            }

            if (plugin.getGameManager().getCoinflip(player.getUniqueId()) != null) {
                player.sendMessage("§cHai già un coinflip attivo, non puoi partecipare ai Pillars!");
                return;
            }

            org.bukkit.Location returnLoc = player.getLocation();
            plugin.getGameManager().setPlayerReturn(player.getUniqueId(), returnLoc);
            player.teleport(plugin.getGameManager().getPillarFirst());
            player.setHealth(player.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).getValue());
            player.setFoodLevel(20);
            
            org.ItsInspector.sunnyCoinflip.models.PillarMatch match = new org.ItsInspector.sunnyCoinflip.models.PillarMatch(player.getUniqueId(), player.getLocation(), amount);
            match.setCreatorJoinTime(System.currentTimeMillis());
            plugin.getGameManager().setActivePillarMatch(match);
            
            player.sendMessage("§aPillar creato con successo! In attesa di uno sfidante per §r§f\uE0D8 §e" + String.format("%.0f", amount));
        } catch (IllegalArgumentException e) {
            player.sendMessage("§c" + e.getMessage());
        }
    }
}