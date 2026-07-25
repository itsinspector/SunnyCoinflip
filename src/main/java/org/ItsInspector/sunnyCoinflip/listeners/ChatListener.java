package org.ItsInspector.sunnyCoinflip.listeners;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.ItsInspector.sunnyCoinflip.SunnyCoinflip;
import org.ItsInspector.sunnyCoinflip.models.Coinflip;
import org.ItsInspector.sunnyCoinflip.models.PillarMatch;
import org.ItsInspector.sunnyCoinflip.utils.NumberParser;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

public class ChatListener implements Listener {
    private final SunnyCoinflip plugin;
    private final Set<UUID> pendingCreation = Collections.synchronizedSet(new HashSet());
    private final Set<UUID> pendingPillar = Collections.synchronizedSet(new HashSet());

    public ChatListener(SunnyCoinflip plugin) {
        this.plugin = plugin;
    }

    public void addPending(UUID uuid) {
        this.pendingCreation.add(uuid);
    }

    public void addPendingPillar(UUID uuid) {
        this.pendingPillar.add(uuid);
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (this.pendingCreation.contains(player.getUniqueId())) {
            event.setCancelled(true);
            this.pendingCreation.remove(player.getUniqueId());
            String message = event.getMessage();

            try {
                double amount = NumberParser.parseNumber(message);
                double maxAmount = this.plugin.getGameManager().getMaxAmount();
                if (amount <= (double)0.0F || amount > maxAmount) {
                    Object[] var14 = new Object[]{maxAmount};
                    player.sendMessage("§cL'importo deve essere tra 1 e " + String.format("%.0f", var14) + ".");
                    return;
                }

                Bukkit.getScheduler().runTask(this.plugin, () -> {
                    if (SunnyCoinflip.getEconomy().getBalance(player) < amount) {
                        player.sendMessage("§cNon hai abbastanza soldi!");
                    } else if (this.plugin.getGameManager().getCoinflip(player.getUniqueId()) != null) {
                        player.sendMessage("§cHai già un coinflip attivo!");
                    } else {
                        Coinflip cf = new Coinflip(player.getUniqueId(), player.getName(), amount);
                        this.plugin.getGameManager().addCoinflip(cf);
                        player.sendMessage("§aCoinflip creato con successo!");
                    }
                });
            } catch (IllegalArgumentException e) {
                player.sendMessage("§c" + e.getMessage());
            }
        } else if (this.pendingPillar.contains(player.getUniqueId())) {
            event.setCancelled(true);
            this.pendingPillar.remove(player.getUniqueId());
            String message = event.getMessage();

            try {
                double amount = NumberParser.parseNumber(message);
                double maxAmount = this.plugin.getGameManager().getMaxAmount();
                if (amount <= (double)0.0F || amount > maxAmount) {
                    Object[] var10002 = new Object[]{maxAmount};
                    player.sendMessage("§cL'importo deve essere tra 1 e " + String.format("%.0f", var10002) + ".");
                    return;
                }

                Location returnLoc = player.getLocation();
                Bukkit.getScheduler().runTask(this.plugin, () -> {
                    if (SunnyCoinflip.getEconomy().getBalance(player) < amount) {
                        player.sendMessage("§cNon hai abbastanza soldi!");
                    } else if (this.plugin.getGameManager().getActivePillarMatch() != null) {
                        player.sendMessage("§cC'è già una partita di Pillars attiva o in attesa!");
                    } else if (this.plugin.getGameManager().getCoinflip(player.getUniqueId()) != null) {
                        player.sendMessage("§cHai già un coinflip attivo, non puoi partecipare ai Pillars!");
                    } else {
                        this.plugin.getGameManager().setPlayerReturn(player.getUniqueId(), returnLoc);
                        player.teleport(this.plugin.getGameManager().getPillarFirst());
                        player.setHealth(player.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue());
                        player.setFoodLevel(20);
                        PillarMatch match = new PillarMatch(player.getUniqueId(), player.getLocation(), amount);
                        match.setCreatorJoinTime(System.currentTimeMillis());
                        this.plugin.getGameManager().setActivePillarMatch(match);
                        Object[] var10002 = new Object[]{amount};
                        player.sendMessage("§aPillar creato con successo! In attesa di uno sfidante per §r§f\ue0d8 §e" + String.format("%.0f", var10002));
                    }
                });
            } catch (IllegalArgumentException e) {
                player.sendMessage("§c" + e.getMessage());
            }
        }

    }

    public void createCoinflipDirect(Player player, String amountStr) {
        try {
            double amount = NumberParser.parseNumber(amountStr);
            double maxAmount = this.plugin.getGameManager().getMaxAmount();
            if (amount <= (double)0.0F || amount > maxAmount) {
                Object[] var9 = new Object[]{maxAmount};
                player.sendMessage("§cL'importo deve essere tra 1 e " + String.format("%.0f", var9) + ".");
                return;
            }

            if (SunnyCoinflip.getEconomy().getBalance(player) < amount) {
                player.sendMessage("§cNon hai abbastanza soldi!");
                return;
            }

            if (this.plugin.getGameManager().getCoinflip(player.getUniqueId()) != null) {
                player.sendMessage("§cHai già un coinflip attivo!");
                return;
            }

            Coinflip cf = new Coinflip(player.getUniqueId(), player.getName(), amount);
            this.plugin.getGameManager().addCoinflip(cf);
            Object[] var10002 = new Object[]{amount};
            player.sendMessage("§aCoinflip creato con successo per §r§f\ue0d8 §e" + String.format("%.0f", var10002) + "§a!");
        } catch (IllegalArgumentException e) {
            player.sendMessage("§c" + e.getMessage());
        }

    }

    public void createPillarDirect(Player player, String amountStr) {
        try {
            double amount = NumberParser.parseNumber(amountStr);
            double maxAmount = this.plugin.getGameManager().getMaxAmount();
            if (amount <= (double)0.0F || amount > maxAmount) {
                Object[] var10 = new Object[]{maxAmount};
                player.sendMessage("§cL'importo deve essere tra 1 e " + String.format("%.0f", var10) + ".");
                return;
            }

            if (SunnyCoinflip.getEconomy().getBalance(player) < amount) {
                player.sendMessage("§cNon hai abbastanza soldi!");
                return;
            }

            if (this.plugin.getGameManager().getActivePillarMatch() != null) {
                player.sendMessage("§cC'è già una partita di Pillars attiva o in attesa!");
                return;
            }

            if (this.plugin.getGameManager().getCoinflip(player.getUniqueId()) != null) {
                player.sendMessage("§cHai già un coinflip attivo, non puoi partecipare ai Pillars!");
                return;
            }

            Location returnLoc = player.getLocation();
            this.plugin.getGameManager().setPlayerReturn(player.getUniqueId(), returnLoc);
            player.teleport(this.plugin.getGameManager().getPillarFirst());
            player.setHealth(player.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue());
            player.setFoodLevel(20);
            PillarMatch match = new PillarMatch(player.getUniqueId(), player.getLocation(), amount);
            match.setCreatorJoinTime(System.currentTimeMillis());
            this.plugin.getGameManager().setActivePillarMatch(match);
            Object[] var10002 = new Object[]{amount};
            player.sendMessage("§aPillar creato con successo! In attesa di uno sfidante per §r§f\ue0d8 §e" + String.format("%.0f", var10002));
        } catch (IllegalArgumentException e) {
            player.sendMessage("§c" + e.getMessage());
        }

    }
}
