package org.ItsInspector.sunnyCoinflip.listeners;

import org.ItsInspector.sunnyCoinflip.SunnyCoinflip;
import org.ItsInspector.sunnyCoinflip.models.Coinflip;
import org.ItsInspector.sunnyCoinflip.models.PillarMatch;
import org.ItsInspector.sunnyCoinflip.utils.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/** Menu essenziali per coinflip classico e Pillars. */
public final class InventoryListener implements Listener {
    private static final String NORMAL_TITLE = "§8Coinflip classici";
    private static final String PILLARS_TITLE = "§8Coinflip Pillars";
    private final SunnyCoinflip plugin;

    public InventoryListener(SunnyCoinflip plugin) {
        this.plugin = plugin;
    }

    public void openNormalCoinflipMenu(Player player, int page) {
        Inventory inventory = Bukkit.createInventory(null, 54, NORMAL_TITLE);
        List<Coinflip> matches = new ArrayList<>(plugin.getGameManager().getAllCoinflips());
        int start = Math.max(0, page) * 45;
        for (int slot = 0; slot < 45 && start + slot < matches.size(); slot++) {
            Coinflip match = matches.get(start + slot);
            ItemStack head = ItemBuilder.createSkull(Bukkit.getOfflinePlayer(match.getCreator()),
                    "&e" + match.getCreatorName(),
                    "&7Importo: &a" + String.format("%.0f", match.getAmount()),
                    "&fClicca per accettare");
            ItemMeta meta = head.getItemMeta();
            if (meta != null) {
                List<String> lore = meta.getLore() == null ? new ArrayList<>() : new ArrayList<>(meta.getLore());
                lore.add("§0coinflip:" + match.getCreator());
                meta.setLore(lore);
                head.setItemMeta(meta);
            }
            inventory.setItem(slot, head);
        }
        inventory.setItem(49, ItemBuilder.createItem(Material.EMERALD, "&aCrea coinflip"));
        inventory.setItem(53, ItemBuilder.createItem(Material.BLAZE_POWDER, "&6Pillars"));
        player.openInventory(inventory);
    }

    public void handlePillarsSelection(Player player) {
        PillarMatch match = plugin.getGameManager().getActivePillarMatch();
        if (match == null) {
            Inventory inventory = Bukkit.createInventory(null, 27, PILLARS_TITLE);
            inventory.setItem(13, ItemBuilder.createItem(Material.EMERALD, "&aCrea un match Pillars"));
            player.openInventory(inventory);
            return;
        }
        if (match.isPlaying()) {
            player.sendMessage("§eIl round è già iniziato: userai la modalità spettatore.");
            org.ItsInspector.sunnyCoinflip.managers.PillarSpectatorManager.get(plugin).startSpectating(player);
            return;
        }
        if (match.getCreator().equals(player.getUniqueId())) {
            player.sendMessage("§eSei il creator di questo match.");
            return;
        }
        plugin.getPillarListener().startPillarMatch(player);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String title = event.getView().getTitle();
        if (!title.equals(NORMAL_TITLE) && !title.equals(PILLARS_TITLE)) return;
        event.setCancelled(true);
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) return;

        if (title.equals(PILLARS_TITLE)) {
            if (clicked.getType() == Material.EMERALD) plugin.getChatListener().awaitPillarAmount(player);
            return;
        }

        if (event.getRawSlot() == 49) {
            plugin.getChatListener().awaitNormalAmount(player);
            return;
        }
        if (event.getRawSlot() == 53) {
            handlePillarsSelection(player);
            return;
        }

        ItemMeta meta = clicked.getItemMeta();
        if (meta == null || meta.getLore() == null) return;
        String marker = meta.getLore().stream().filter(line -> line.startsWith("§0coinflip:")).findFirst().orElse(null);
        if (marker == null) return;
        try {
            java.util.UUID creatorId = java.util.UUID.fromString(marker.substring("§0coinflip:".length()));
            acceptNormal(player, creatorId);
        } catch (IllegalArgumentException ignored) {
            player.sendMessage("§cQuesto coinflip non è più valido.");
        }
    }

    private void acceptNormal(Player opponent, java.util.UUID creatorId) {
        Coinflip match = plugin.getGameManager().getCoinflip(creatorId);
        Player creator = Bukkit.getPlayer(creatorId);
        if (match == null || creator == null || !creator.isOnline()) {
            plugin.getGameManager().removeCoinflip(creatorId);
            opponent.sendMessage("§cQuesto coinflip non è più disponibile.");
            return;
        }
        if (creatorId.equals(opponent.getUniqueId())) {
            opponent.sendMessage("§cNon puoi accettare il tuo coinflip.");
            return;
        }
        if (!SunnyCoinflip.getEconomy().has(creator, match.getAmount())
                || !SunnyCoinflip.getEconomy().has(opponent, match.getAmount())) {
            opponent.sendMessage("§cSaldo insufficiente di uno dei giocatori.");
            return;
        }
        SunnyCoinflip.getEconomy().withdrawPlayer(creator, match.getAmount());
        SunnyCoinflip.getEconomy().withdrawPlayer(opponent, match.getAmount());
        Player winner = Math.random() < 0.5 ? creator : opponent;
        SunnyCoinflip.getEconomy().depositPlayer(winner, match.getAmount() * 2.0);
        creator.sendMessage("§6Il coinflip è stato vinto da §f" + winner.getName() + "§6!");
        opponent.sendMessage("§6Il coinflip è stato vinto da §f" + winner.getName() + "§6!");
        plugin.getGameManager().removeCoinflip(creatorId);
        opponent.closeInventory();
    }
}
