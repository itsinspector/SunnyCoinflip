package org.ItsInspector.sunnyCoinflip.listeners;

import org.ItsInspector.sunnyCoinflip.SunnyCoinflip;
import org.ItsInspector.sunnyCoinflip.models.Coinflip;
import org.ItsInspector.sunnyCoinflip.models.PillarMatch;
import org.ItsInspector.sunnyCoinflip.utils.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class InventoryListener implements Listener {

    private final SunnyCoinflip plugin;

    public InventoryListener(SunnyCoinflip plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getView().getTitle().equals(ItemBuilder.translate("ᴄᴏɪɴꜰʟɪᴘ - ᴍᴇɴᴜ"))) {
            event.setCancelled(true);
            Player player = (Player) event.getWhoClicked();
            ItemStack item = event.getCurrentItem();
            if (item == null || item.getType() == Material.AIR) return;

            if (item.getType() == Material.GOLD_INGOT) {
                openNormalCoinflipMenu(player, 0);
            } else if (item.getType() == Material.BEDROCK) {
                handlePillarsSelection(player);
            }
        } else if (event.getView().getTitle().equals(ItemBuilder.translate("ᴄᴏɪɴғʟɪᴘ ʀᴏʟʟɪɴɢ..."))) {
            event.setCancelled(true);
        } else if (event.getView().getTitle().startsWith(ItemBuilder.translate("ᴄᴏɪɴꜰʟɪᴘ ᴄʟᴀѕѕɪᴄɪ - ᴘᴀɢɪɴᴀ"))) {
            event.setCancelled(true);
            Player player = (Player) event.getWhoClicked();
            int page = Integer.parseInt(event.getView().getTitle().split(" ")[4]) - 1;
            ItemStack item = event.getCurrentItem();
            if (item == null || item.getType() == Material.AIR) return;

            if (item.getType() == Material.NETHER_STAR) {
                player.closeInventory();
                player.sendMessage("§eDigita l'importo del coinflip (solo numeri, max 10.000.000):");
                plugin.getChatListener().addPending(player.getUniqueId());
            } else if (item.getType() == Material.BARRIER) {
                Coinflip cf = plugin.getGameManager().getCoinflip(player.getUniqueId());
                if (cf != null && !cf.isActive()) {
                    plugin.getGameManager().removeCoinflip(player.getUniqueId());
                    player.sendMessage("§cIl tuo coinflip è stato rimosso.");
                } else if (cf != null && cf.isActive()) {
                    player.sendMessage("§cNon puoi rimuovere un coinflip già in corso!");
                }
                openNormalCoinflipMenu(player, page);
            } else if (item.getType() == Material.ARROW) {
                if (item.getItemMeta().getDisplayName().contains("Avanti")) {
                    List<Coinflip> allCfs = new ArrayList<>(plugin.getGameManager().getAllCoinflips());
                    if ((page + 1) * 45 < allCfs.size()) {
                        openNormalCoinflipMenu(player, page + 1);
                    }
                } else {
                    if (page > 0) {
                        openNormalCoinflipMenu(player, page - 1);
                    }
                }
            } else if (item.getType() == Material.PLAYER_HEAD) {
                if (item.getItemMeta() == null || !item.getItemMeta().hasDisplayName()) return;
                String creatorName = ChatColor.stripColor(item.getItemMeta().getDisplayName());
                for (Coinflip cf : plugin.getGameManager().getAllCoinflips()) {
                    if (cf.getCreatorName().equals(creatorName)) {
                        if (cf.getCreator().equals(player.getUniqueId())) {
                            player.sendMessage("§cNon puoi accettare il tuo stesso coinflip!");
                            return;
                        }
                        if (cf.isActive()) {
                            player.sendMessage("§cQuesto coinflip è già in corso!");
                            return;
                        }
                        if (SunnyCoinflip.getEconomy().getBalance(player) < cf.getAmount()) {
                            player.sendMessage("§cNon hai abbastanza soldi per accettare questo coinflip!");
                            return;
                        }
                        
                        acceptCoinflip(player, cf);
                        break;
                    }
                }
            }
        }
    }

    private void acceptCoinflip(Player opponent, Coinflip cf) {
        Player creator = Bukkit.getPlayer(cf.getCreator());
        if (creator == null) {
            opponent.sendMessage("§cIl creatore non è online!");
            return;
        }

        cf.setActive(true);
        startRoll(creator, opponent, cf);
    }

    private void startRoll(Player p1, Player p2, Coinflip cf) {
        Inventory inv = Bukkit.createInventory(null, 27, ItemBuilder.translate("ᴄᴏɪɴғʟɪᴘ ʀᴏʟʟɪɴɢ..."));
        p1.openInventory(inv);
        p2.openInventory(inv);

        ItemStack p1Head = ItemBuilder.createSkull(p1, "§e" + p1.getName());
        ItemStack p2Head = ItemBuilder.createSkull(p2, "§e" + p2.getName());
        ItemStack glass = ItemBuilder.createItem(Material.GRAY_STAINED_GLASS_PANE, " ");

        for (int i = 0; i < 27; i++) {
            if (i != 13) inv.setItem(i, glass);
        }

        new org.bukkit.scheduler.BukkitRunnable() {
            int ticks = 0;
            boolean toggle = false;

            @Override
            public void run() {
                if (ticks >= 60) {
                    this.cancel();
                    finishRoll(p1, p2, cf);
                    return;
                }

                if (toggle) {
                    inv.setItem(13, p1Head);
                } else {
                    inv.setItem(13, p2Head);
                }
                
                p1.playSound(p1, org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1f);
                p2.playSound(p2, org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1f);
                
                toggle = !toggle;
                ticks += 5;
            }
        }.runTaskTimer(plugin, 0, 5);
    }

    private void finishRoll(Player p1, Player p2, Coinflip cf) {
        Player winner = Math.random() < 0.5 ? p1 : p2;
        Player loser = winner.equals(p1) ? p2 : p1;

        double totalPrize = cf.getAmount() * plugin.getGameManager().getWinMultiplier(); 
        
        // Prelievo e vincita dopo il risultato
        SunnyCoinflip.getEconomy().withdrawPlayer(winner, cf.getAmount());
        SunnyCoinflip.getEconomy().withdrawPlayer(loser, cf.getAmount());
        SunnyCoinflip.getEconomy().depositPlayer(winner, totalPrize);

        winner.closeInventory();
        loser.closeInventory();

        winner.playSound(winner, org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);

        winner.sendMessage("§aʜᴀɪ ᴠɪɴᴛᴏ ɪʟ ᴄᴏɪɴғʟɪᴘ ᴄᴏɴᴛʀᴏ " + loser.getName() + "! ʜᴀɪ ɢᴜᴀᴅᴀɢɴᴀᴛᴏ §r§f\uE0D8 §e" + String.format("%.0f", totalPrize));
        loser.sendMessage("§cʜᴀɪ ᴘᴇʀsᴏ ɪʟ ᴄᴏɪɴғʟɪᴘ ᴄᴏɴᴛʀᴏ " + winner.getName() + ".");

        plugin.getGameManager().removeCoinflip(cf.getCreator());
    }

    @EventHandler
    public void onInventoryClose(org.bukkit.event.inventory.InventoryCloseEvent event) {
        String title = event.getView().getTitle();
        Player player = (Player) event.getPlayer();

        if (title.equals(ItemBuilder.translate("ᴄᴏɪɴғʟɪᴘ ʀᴏʟʟɪɴɢ..."))) {
            new org.bukkit.scheduler.BukkitRunnable() {
                @Override
                public void run() {
                    if (player.getOpenInventory().getTitle().equals(ItemBuilder.translate("ᴄᴏɪɴғʟɪᴘ ʀᴏʟʟɪɴɢ..."))) return;
                }
            }.runTaskLater(plugin, 1);
        } else if (title.equalsIgnoreCase(ItemBuilder.translate("Scegli un Kit"))) {
            // Controlla se il giocatore è in un match di Pillar
            PillarMatch match = plugin.getGameManager().getActivePillarMatch();
            if (match != null && (player.getUniqueId().equals(match.getCreator()) || player.getUniqueId().equals(match.getOpponent()))) {
                if (match.isStarted() && !match.isPlaying()) {
                    new org.bukkit.scheduler.BukkitRunnable() {
                        @Override
                        public void run() {
                        }
                    }.runTaskLater(plugin, 1);
                }
            }
        }
    }

    @EventHandler
    public void onKitInventoryClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equalsIgnoreCase(ItemBuilder.translate("Scegli un Kit"))) return;
        
        Player player = (Player) event.getWhoClicked();
        PillarMatch match = plugin.getGameManager().getActivePillarMatch();
        
        if (match != null && (player.getUniqueId().equals(match.getCreator()) || player.getUniqueId().equals(match.getOpponent()))) {
            ItemStack item = event.getCurrentItem();
            if (item != null && item.getType() != Material.AIR) {
                // Il giocatore ha cliccato un kit (assumiamo che ogni item non aria sia un kit)
                player.getInventory().clear();
            }
        }
    }

    public void handlePillarsSelection(Player player) {
        if (plugin.getGameManager().getPillarFirst() == null || plugin.getGameManager().getPillarOpponent() == null) {
            player.sendMessage("§cLe posizioni dei pillars non sono state impostate!");
            return;
        }

        PillarMatch activeMatch = plugin.getGameManager().getActivePillarMatch();
        if (activeMatch != null) {
            if (plugin.getGameManager().getCoinflip(player.getUniqueId()) != null) {
                player.sendMessage("§cHai già un coinflip attivo, non puoi partecipare ai Pillars!");
                return;
            }

            if (activeMatch.isStarted()) {
                player.sendMessage("§cC'è già una partita di Pillars in corso!");
                return;
            }
            if (activeMatch.getCreator().equals(player.getUniqueId())) {
                player.sendMessage("§cSei già in attesa di uno sfidante!");
                return;
            }

            if (SunnyCoinflip.getEconomy().getBalance(player) < activeMatch.getAmount()) {
                player.sendMessage("§cNon hai abbastanza soldi per accettare questa sfida di Pillars!");
                return;
            }

            activeMatch.setOpponent(player.getUniqueId());
            activeMatch.setOpponentJoinTime(System.currentTimeMillis());

            Player creator = Bukkit.getPlayer(activeMatch.getCreator());
            if (creator != null) {
                String msg1 = "§e§lPILLARS! §f" + player.getName() + " §7ha accettato la sfida di §f" + creator.getName() + " §7per §r§f\uE0D8§e" + activeMatch.getAmount() + "!";
                String msg2 = "§7Scommetti su chi vincerà con §e/pillars scommetti (player) (somma)§7!";

                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getUniqueId().equals(activeMatch.getCreator()) || p.getUniqueId().equals(activeMatch.getOpponent())) continue;
                    if (!plugin.getGameManager().isPillarWorld(p.getWorld())) {
                        p.sendMessage(msg1);
                        p.sendMessage(msg2);
                    }
                }
            }

            plugin.getGameManager().setPlayerReturn(player.getUniqueId(), player.getLocation());
            player.teleport(plugin.getGameManager().getPillarOpponent());
            player.setHealth(player.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).getValue());
            player.setFoodLevel(20);
            player.closeInventory();

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                plugin.getPillarListener().startPillarMatch(activeMatch);
            }, 5L);
            return;
        }

        if (plugin.getGameManager().getCoinflip(player.getUniqueId()) != null) {
            player.sendMessage("§cHai già un coinflip attivo, non puoi partecipare ai Pillars!");
            return;
        }

        player.closeInventory();
        player.sendMessage("§eDigita l'importo per la sfida Pillars (solo numeri, max 10.000.000)::");
        plugin.getChatListener().addPendingPillar(player.getUniqueId());
    }

    public void openNormalCoinflipMenu(Player player, int page) {
        Inventory inv = Bukkit.createInventory(null, 54, ItemBuilder.translate("ᴄᴏɪɴꜰʟɪᴘ ᴄʟᴀѕѕɪᴄɪ - ᴘᴀɢɪɴᴀ ") + (page + 1));
        List<Coinflip> allCfs = new ArrayList<>(plugin.getGameManager().getAllCoinflips());
        
        int start = page * 45;
        int end = Math.min(start + 45, allCfs.size());

        for (int i = start; i < end; i++) {
            Coinflip cf = allCfs.get(i);
            String status = cf.isActive() ? "§cɪɴ ᴄᴏʀѕᴏ" : "§aɪɴ ᴀᴛᴛᴇѕᴀ";
            inv.addItem(ItemBuilder.createSkull(Bukkit.getOfflinePlayer(cf.getCreator()), 
                    "§6" + cf.getCreatorName(),
                    "§f\uE114 ɪᴍᴘᴏʀᴛᴏ: &f\uE0D8 §e" + cf.getAmount(),
                    "§f\uE0B2 ᴠɪɴᴄɪᴛᴀ: &f\uE0D8 §a" + String.format("%.0f", cf.getAmount() * plugin.getGameManager().getWinMultiplier()),
                    "",
                    "&f\uE03C &fѕᴛᴀᴛᴏ:"+ status,
                    cf.isActive() ? "§cɢɪᴀ ɪɴ ᴄᴏʀѕᴏ..." : "§eᴄʟɪᴄᴋ-ѕɪɴɪѕᴛʀᴏ ᴘᴇʀ ᴜɴɪʀᴛɪ!"));
        }

        // Info bar
        ItemStack info = ItemBuilder.createItem(Material.PAPER, "§bʟᴇ ᴛᴜᴇ ɪɴꜰᴏ:",
                "§7ʙɪʟᴀɴᴄɪᴏ: &f\uE0D8 §e" + String.format("%.2f", SunnyCoinflip.getEconomy().getBalance(player)),
                "§7ᴄᴏɪɴꜰʟɪᴘ ᴀᴛᴛɪᴠɪ: §e" + allCfs.size());
        inv.setItem(45, info);

        inv.setItem(48, ItemBuilder.createItem(Material.ARROW, "§7ɪɴᴅɪᴇᴛʀᴏ"));
        inv.setItem(49, ItemBuilder.createItem(Material.NETHER_STAR, "§6&lᴄʀᴇᴀ ᴄᴏɪɴꜰʟɪᴘ"));
        inv.setItem(50, ItemBuilder.createItem(Material.ARROW, "§7ᴀᴠᴀɴᴛɪ"));
        
        if (plugin.getGameManager().getCoinflip(player.getUniqueId()) != null) {
            inv.setItem(53, ItemBuilder.createItem(Material.BARRIER, "§cᴇʟɪᴍɪɴᴀ ɪʟ ᴛᴜᴏ ᴄᴏɪɴꜰʟɪᴘ"));
        }

        player.openInventory(inv);
    }
}