package org.ItsInspector.sunnyCoinflip.listeners;

import java.util.ArrayList;
import java.util.List;
import org.ItsInspector.sunnyCoinflip.SunnyCoinflip;
import org.ItsInspector.sunnyCoinflip.models.Coinflip;
import org.ItsInspector.sunnyCoinflip.models.PillarMatch;
import org.ItsInspector.sunnyCoinflip.utils.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

public class InventoryListener implements Listener {
    private final SunnyCoinflip plugin;

    public InventoryListener(SunnyCoinflip plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getView().getTitle().equals(ItemBuilder.translate("ᴄᴏɪɴꜰʟɪᴘ - ᴍᴇɴᴜ"))) {
            event.setCancelled(true);
            Player player = (Player)event.getWhoClicked();
            ItemStack item = event.getCurrentItem();
            if (item == null || item.getType() == Material.AIR) {
                return;
            }

            if (item.getType() == Material.GOLD_INGOT) {
                this.openNormalCoinflipMenu(player, 0);
            } else if (item.getType() == Material.BEDROCK) {
                this.handlePillarsSelection(player);
            }
        } else if (event.getView().getTitle().equals(ItemBuilder.translate("ᴄᴏɪɴғʟɪᴘ ʀᴏʟʟɪɴɢ..."))) {
            event.setCancelled(true);
        } else if (event.getView().getTitle().startsWith(ItemBuilder.translate("ᴄᴏɪɴꜰʟɪᴘ ᴄʟᴀѕѕɪᴄɪ - ᴘᴀɢɪɴᴀ"))) {
            event.setCancelled(true);
            Player player = (Player)event.getWhoClicked();
            int page = Integer.parseInt(event.getView().getTitle().split(" ")[4]) - 1;
            ItemStack item = event.getCurrentItem();
            if (item == null || item.getType() == Material.AIR) {
                return;
            }

            if (item.getType() == Material.NETHER_STAR) {
                player.closeInventory();
                player.sendMessage("§eDigita l'importo del coinflip (solo numeri, max 10.000.000):");
                this.plugin.getChatListener().addPending(player.getUniqueId());
            } else if (item.getType() == Material.BARRIER) {
                Coinflip cf = this.plugin.getGameManager().getCoinflip(player.getUniqueId());
                if (cf != null && !cf.isActive()) {
                    this.plugin.getGameManager().removeCoinflip(player.getUniqueId());
                    player.sendMessage("§cIl tuo coinflip è stato rimosso.");
                } else if (cf != null && cf.isActive()) {
                    player.sendMessage("§cNon puoi rimuovere un coinflip già in corso!");
                }

                this.openNormalCoinflipMenu(player, page);
            } else if (item.getType() == Material.ARROW) {
                if (item.getItemMeta().getDisplayName().contains("Avanti")) {
                    List<Coinflip> allCfs = new ArrayList(this.plugin.getGameManager().getAllCoinflips());
                    if ((page + 1) * 45 < allCfs.size()) {
                        this.openNormalCoinflipMenu(player, page + 1);
                    }
                } else if (page > 0) {
                    this.openNormalCoinflipMenu(player, page - 1);
                }
            } else if (item.getType() == Material.PLAYER_HEAD) {
                if (item.getItemMeta() == null || !item.getItemMeta().hasDisplayName()) {
                    return;
                }

                String creatorName = ChatColor.stripColor(item.getItemMeta().getDisplayName());

                for(Coinflip cf : this.plugin.getGameManager().getAllCoinflips()) {
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

                        this.acceptCoinflip(player, cf);
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
        } else {
            cf.setActive(true);
            this.startRoll(creator, opponent, cf);
        }
    }

    private void startRoll(final Player p1, final Player p2, final Coinflip cf) {
        final Inventory inv = Bukkit.createInventory((InventoryHolder)null, 27, ItemBuilder.translate("ᴄᴏɪɴғʟɪᴘ ʀᴏʟʟɪɴɢ..."));
        p1.openInventory(inv);
        p2.openInventory(inv);
        final ItemStack p1Head = ItemBuilder.createSkull(p1, "§e" + p1.getName());
        final ItemStack p2Head = ItemBuilder.createSkull(p2, "§e" + p2.getName());
        ItemStack glass = ItemBuilder.createItem(Material.GRAY_STAINED_GLASS_PANE, " ");

        for(int i = 0; i < 27; ++i) {
            if (i != 13) {
                inv.setItem(i, glass);
            }
        }

        (new BukkitRunnable() {
            int ticks = 0;
            boolean toggle = false;

            public void run() {
                if (this.ticks >= 60) {
                    this.cancel();
                    InventoryListener.this.finishRoll(p1, p2, cf);
                } else {
                    if (this.toggle) {
                        inv.setItem(13, p1Head);
                    } else {
                        inv.setItem(13, p2Head);
                    }

                    p1.playSound(p1, Sound.BLOCK_NOTE_BLOCK_PLING, 1.0F, 1.0F);
                    p2.playSound(p2, Sound.BLOCK_NOTE_BLOCK_PLING, 1.0F, 1.0F);
                    this.toggle = !this.toggle;
                    this.ticks += 5;
                }
            }
        }).runTaskTimer(this.plugin, 0L, 5L);
    }

    private void finishRoll(Player p1, Player p2, Coinflip cf) {
        Player winner = Math.random() < (double)0.5F ? p1 : p2;
        Player loser = winner.equals(p1) ? p2 : p1;
        double totalPrize = cf.getAmount() * this.plugin.getGameManager().getWinMultiplier();
        SunnyCoinflip.getEconomy().withdrawPlayer(winner, cf.getAmount());
        SunnyCoinflip.getEconomy().withdrawPlayer(loser, cf.getAmount());
        SunnyCoinflip.getEconomy().depositPlayer(winner, totalPrize);
        winner.closeInventory();
        loser.closeInventory();
        winner.playSound(winner, Sound.ENTITY_PLAYER_LEVELUP, 1.0F, 1.0F);
        String var10001 = loser.getName();
        winner.sendMessage("§aʜᴀɪ ᴠɪɴᴛᴏ ɪʟ ᴄᴏɪɴғʟɪᴘ ᴄᴏɴᴛʀᴏ " + var10001 + "! ʜᴀɪ ɢᴜᴀᴅᴀɢɴᴀᴛᴏ §r§f\ue0d8 §e" + String.format("%.0f", totalPrize));
        loser.sendMessage("§cʜᴀɪ ᴘᴇʀsᴏ ɪʟ ᴄᴏɪɴғʟɪᴘ ᴄᴏɴᴛʀᴏ " + winner.getName() + ".");
        this.plugin.getGameManager().removeCoinflip(cf.getCreator());
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        String title = event.getView().getTitle();
        final Player player = (Player)event.getPlayer();
        if (title.equals(ItemBuilder.translate("ᴄᴏɪɴғʟɪᴘ ʀᴏʟʟɪɴɢ..."))) {
            (new BukkitRunnable() {
                public void run() {
                    if (!player.getOpenInventory().getTitle().equals(ItemBuilder.translate("ᴄᴏɪɴғʟɪᴘ ʀᴏʟʟɪɴɢ..."))) {
                        ;
                    }
                }
            }).runTaskLater(this.plugin, 1L);
        } else if (title.equalsIgnoreCase(ItemBuilder.translate("Scegli un Kit"))) {
            PillarMatch match = this.plugin.getGameManager().getActivePillarMatch();
            if (match != null && (player.getUniqueId().equals(match.getCreator()) || player.getUniqueId().equals(match.getOpponent())) && match.isStarted() && !match.isPlaying()) {
                (new BukkitRunnable() {
                    public void run() {
                    }
                }).runTaskLater(this.plugin, 1L);
            }
        }

    }

    @EventHandler
    public void onKitInventoryClick(InventoryClickEvent event) {
        if (event.getView().getTitle().equalsIgnoreCase(ItemBuilder.translate("Scegli un Kit"))) {
            Player player = (Player)event.getWhoClicked();
            PillarMatch match = this.plugin.getGameManager().getActivePillarMatch();
            if (match != null && (player.getUniqueId().equals(match.getCreator()) || player.getUniqueId().equals(match.getOpponent()))) {
                ItemStack item = event.getCurrentItem();
                if (item != null && item.getType() != Material.AIR) {
                    player.getInventory().clear();
                }
            }

        }
    }

    public void handlePillarsSelection(Player player) {
        if (this.plugin.getGameManager().getPillarFirst() != null && this.plugin.getGameManager().getPillarOpponent() != null) {
            PillarMatch activeMatch = this.plugin.getGameManager().getActivePillarMatch();
            if (activeMatch == null) {
                if (this.plugin.getGameManager().getCoinflip(player.getUniqueId()) != null) {
                    player.sendMessage("§cHai già un coinflip attivo, non puoi partecipare ai Pillars!");
                } else {
                    player.closeInventory();
                    player.sendMessage("§eDigita l'importo per la sfida Pillars (solo numeri, max 10.000.000)::");
                    this.plugin.getChatListener().addPendingPillar(player.getUniqueId());
                }
            } else if (this.plugin.getGameManager().getCoinflip(player.getUniqueId()) != null) {
                player.sendMessage("§cHai già un coinflip attivo, non puoi partecipare ai Pillars!");
            } else if (activeMatch.isStarted()) {
                player.sendMessage("§cC'è già una partita di Pillars in corso!");
            } else if (activeMatch.getCreator().equals(player.getUniqueId())) {
                player.sendMessage("§cSei già in attesa di uno sfidante!");
            } else if (SunnyCoinflip.getEconomy().getBalance(player) < activeMatch.getAmount()) {
                player.sendMessage("§cNon hai abbastanza soldi per accettare questa sfida di Pillars!");
            } else {
                activeMatch.setOpponent(player.getUniqueId());
                activeMatch.setOpponentJoinTime(System.currentTimeMillis());
                Player creator = Bukkit.getPlayer(activeMatch.getCreator());
                if (creator != null) {
                    String var10000 = player.getName();
                    String msg1 = "§e§lPILLARS! §f" + var10000 + " §7ha accettato la sfida di §f" + creator.getName() + " §7per §r§f\ue0d8§e" + activeMatch.getAmount() + "!";
                    String msg2 = "§7Scommetti su chi vincerà con §e/pillars scommetti (player) (somma)§7!";

                    for(Player p : Bukkit.getOnlinePlayers()) {
                        if (!p.getUniqueId().equals(activeMatch.getCreator()) && !p.getUniqueId().equals(activeMatch.getOpponent()) && !this.plugin.getGameManager().isPillarWorld(p.getWorld())) {
                            p.sendMessage(msg1);
                            p.sendMessage(msg2);
                        }
                    }
                }

                this.plugin.getGameManager().setPlayerReturn(player.getUniqueId(), player.getLocation());
                player.teleport(this.plugin.getGameManager().getPillarOpponent());
                player.setHealth(player.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue());
                player.setFoodLevel(20);
                player.closeInventory();
                Bukkit.getScheduler().runTaskLater(this.plugin, () -> this.plugin.getPillarListener().startPillarMatch(activeMatch), 5L);
            }
        } else {
            player.sendMessage("§cLe posizioni dei pillars non sono state impostate!");
        }
    }

    public void openNormalCoinflipMenu(Player player, int page) {
        String title = ItemBuilder.translate("ᴄᴏɪɴꜰʟɪᴘ ᴄʟᴀѕѕɪᴄɪ - ᴘᴀɢɪɴᴀ ") + (page + 1);
        Inventory inv = Bukkit.createInventory((InventoryHolder)null, 54, title);
        List<Coinflip> allCfs = new ArrayList(this.plugin.getGameManager().getAllCoinflips());
        int start = page * 45;
        int end = Math.min(start + 45, allCfs.size());

        for(int i = start; i < end; ++i) {
            Coinflip cf = (Coinflip)allCfs.get(i);
            String status = cf.isActive() ? "§cɪɴ ᴄᴏʀѕᴏ" : "§aɪɴ ᴀᴛᴛᴇѕᴀ";
            ItemStack[] var10001 = new ItemStack[1];
            OfflinePlayer var10004 = Bukkit.getOfflinePlayer(cf.getCreator());
            String var10005 = cf.getCreatorName();
            var10005 = "§6" + var10005;
            String[] var10006 = new String[5];
            double var10009 = cf.getAmount();
            var10006[0] = "§f\ue114 ɪᴍᴘᴏʀᴛᴏ: &f\ue0d8 §e" + var10009;
            String var14 = String.format("%.0f", cf.getAmount() * this.plugin.getGameManager().getWinMultiplier());
            var10006[1] = "§f\ue0b2 ᴠɪɴᴄɪᴛᴀ: &f\ue0d8 §a" + var14;
            var10006[2] = "";
            var10006[3] = "&f\ue03c &fѕᴛᴀᴛᴏ:" + status;
            var10006[4] = cf.isActive() ? "§cɢɪᴀ ɪɴ ᴄᴏʀѕᴏ..." : "§eᴄʟɪᴄᴋ-ѕɪɴɪѕᴛʀᴏ ᴘᴇʀ ᴜɴɪʀᴛɪ!";
            var10001[0] = ItemBuilder.createSkull(var10004, var10005, var10006);
            inv.addItem(var10001);
        }

        Material var10000 = Material.PAPER;
        String[] infoLore = new String[2];
        Object[] var13 = new Object[]{SunnyCoinflip.getEconomy().getBalance(player)};
        infoLore[0] = "§7ʙɪʟᴀɴᴄɪᴏ: &f\ue0d8 §e" + String.format("%.2f", var13);
        infoLore[1] = "§7ᴄᴏɪɴꜰʟɪᴘ ᴀᴛᴛɪᴠɪ: §e" + allCfs.size();
        ItemStack info = ItemBuilder.createItem(var10000, "§bʟᴇ ᴛᴜᴇ ɪɴꜰᴏ:", infoLore);
        inv.setItem(45, info);
        inv.setItem(48, ItemBuilder.createItem(Material.ARROW, "§7ɪɴᴅɪᴇᴛʀᴏ"));
        inv.setItem(49, ItemBuilder.createItem(Material.NETHER_STAR, "§6&lᴄʀᴇᴀ ᴄᴏɪɴꜰʟɪᴘ"));
        inv.setItem(50, ItemBuilder.createItem(Material.ARROW, "§7ᴀᴠᴀɴᴛɪ"));
        if (this.plugin.getGameManager().getCoinflip(player.getUniqueId()) != null) {
            inv.setItem(53, ItemBuilder.createItem(Material.BARRIER, "§cᴇʟɪᴍɪɴᴀ ɪʟ ᴛᴜᴏ ᴄᴏɪɴꜰʟɪᴘ"));
        }

        player.openInventory(inv);
    }
}