package org.ItsInspector.sunnyCoinflip.listeners;

import org.ItsInspector.sunnyCoinflip.SunnyCoinflip;
import org.ItsInspector.sunnyCoinflip.models.Coinflip;
import org.ItsInspector.sunnyCoinflip.models.PillarMatch;
import org.ItsInspector.sunnyCoinflip.utils.NumberParser;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Gestisce l'inserimento degli importi tramite chat. */
public final class ChatListener implements Listener {
    private final SunnyCoinflip plugin;
    private final Set<UUID> awaitingNormalAmount = new HashSet<>();
    private final Set<UUID> awaitingPillarAmount = new HashSet<>();

    public ChatListener(SunnyCoinflip plugin) {
        this.plugin = plugin;
    }

    public void awaitNormalAmount(Player player) {
        awaitingNormalAmount.add(player.getUniqueId());
        player.closeInventory();
        player.sendMessage("§eScrivi in chat l'importo del coinflip classico, oppure §fcancel§e.");
    }

    public void awaitPillarAmount(Player player) {
        awaitingPillarAmount.add(player.getUniqueId());
        player.closeInventory();
        player.sendMessage("§eScrivi in chat l'importo del coinflip Pillars, oppure §fcancel§e.");
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        boolean bedwars = plugin.getBedfightManager().isAwaitingCreateAmount(id);
        boolean normal = awaitingNormalAmount.remove(id);
        boolean pillars = awaitingPillarAmount.remove(id);
        if (!bedwars && !normal && !pillars) return;

        event.setCancelled(true);
        String message = event.getMessage();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (bedwars) {
                plugin.getBedfightManager().handleCreateAmountChat(player, message);
                return;
            }
            if (message.equalsIgnoreCase("cancel") || message.equalsIgnoreCase("annulla")) {
                player.sendMessage("§eCreazione annullata.");
                return;
            }
            if (normal) createCoinflipDirect(player, message);
            else createPillarDirect(player, message);
        });
    }

    public void createCoinflipDirect(Player player, String rawAmount) {
        double amount;
        try {
            amount = NumberParser.parseNumber(rawAmount);
        } catch (IllegalArgumentException exception) {
            player.sendMessage("§c" + exception.getMessage());
            return;
        }
        if (!validAmount(player, amount)) return;
        if (plugin.getGameManager().getCoinflip(player.getUniqueId()) != null) {
            player.sendMessage("§cHai già un coinflip classico attivo.");
            return;
        }
        plugin.getGameManager().addCoinflip(new Coinflip(player.getUniqueId(), player.getName(), amount));
        player.sendMessage("§aCoinflip classico creato per §f" + String.format("%.0f", amount) + "§a.");
    }

    public void createPillarDirect(Player player, String rawAmount) {
        double amount;
        try {
            amount = NumberParser.parseNumber(rawAmount);
        } catch (IllegalArgumentException exception) {
            player.sendMessage("§c" + exception.getMessage());
            return;
        }
        if (!validAmount(player, amount)) return;
        if (plugin.getGameManager().getActivePillarMatch() != null) {
            player.sendMessage("§cEsiste già un match Pillars disponibile o in corso.");
            return;
        }
        if (plugin.getGameManager().getPillarFirst() == null || plugin.getGameManager().getPillarOpponent() == null) {
            player.sendMessage("§cLe posizioni Pillars non sono configurate.");
            return;
        }
        PillarMatch match = new PillarMatch(player.getUniqueId(), player.getLocation().clone(), amount);
        match.setCreatorJoinTime(System.currentTimeMillis());
        plugin.getGameManager().setActivePillarMatch(match);
        player.sendMessage("§aMatch Pillars creato per §f" + String.format("%.0f", amount) + "§a.");
    }

    private boolean validAmount(Player player, double amount) {
        if (amount <= 0 || amount > plugin.getGameManager().getMaxAmount()) {
            player.sendMessage("§cImporto non valido.");
            return false;
        }
        if (!SunnyCoinflip.getEconomy().has(player, amount)) {
            player.sendMessage("§cNon hai abbastanza denaro.");
            return false;
        }
        return true;
    }
}
