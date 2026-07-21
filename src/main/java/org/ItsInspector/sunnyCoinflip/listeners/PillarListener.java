package org.ItsInspector.sunnyCoinflip.listeners;

import org.ItsInspector.sunnyCoinflip.SunnyCoinflip;
import org.ItsInspector.sunnyCoinflip.models.PillarMatch;
import org.ItsInspector.sunnyCoinflip.utils.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Egg;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Random;

public class PillarListener implements Listener {

    private final SunnyCoinflip plugin;
    private final Random random = new Random();
    private BossBar pillarBossBar;

    public PillarListener(SunnyCoinflip plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        PillarMatch match = plugin.getGameManager().getActivePillarMatch();
        if (match == null) return;
        Player player = event.getPlayer();
        if (!player.getUniqueId().equals(match.getCreator()) && !player.getUniqueId().equals(match.getOpponent())) return;

        // Blocco movimento se la partita non è ancora "playing" (countdown in corso)
        if (!match.isPlaying()) {
            Location from = event.getFrom();
            Location to = event.getTo();
            if (from.getX() != to.getX() || from.getZ() != to.getZ() || from.getY() != to.getY()) {
                event.setTo(from.setDirection(to.getDirection()));
                return;
            }
        }

        // Se il match non è iniziato (attesa sfidante), non facciamo i controlli di altezza
        if (!match.isStarted()) return;

        // Tolleranza 0.5s per il cambio mondo iniziale prima dei controlli altezza
        long joinTime = player.getUniqueId().equals(match.getCreator()) ? match.getCreatorJoinTime() : match.getOpponentJoinTime();
        if (System.currentTimeMillis() - joinTime < 500) return;

        // Morte sotto -62
        if (event.getTo().getY() <= -62) {
            Player winner = player.getUniqueId().equals(match.getCreator()) ? 
                    org.bukkit.Bukkit.getPlayer(match.getOpponent()) : 
                    org.bukkit.Bukkit.getPlayer(match.getCreator());
            
            finishPillarMatch(winner, player, match);
            return;
        }

    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        PillarMatch match = plugin.getGameManager().getActivePillarMatch();
        if (match == null || !match.isStarted()) return;
        Player player = event.getPlayer();
        if (!player.getUniqueId().equals(match.getCreator()) && !player.getUniqueId().equals(match.getOpponent())) return;

        int maxHeight = plugin.getGameManager().getMaxHeight();
        if (event.getBlock().getY() > maxHeight) {
            event.setCancelled(true);
            player.sendMessage("§cNon puoi piazzare blocchi sopra l'altezza " + maxHeight + "!");
        }
    }

    public void startPillarMatch(PillarMatch match) {
        match.setStarted(true);
        Player p1 = org.bukkit.Bukkit.getPlayer(match.getCreator());
        Player p2 = org.bukkit.Bukkit.getPlayer(match.getOpponent());
        
        if (p1 == null || p2 == null) {
            plugin.getGameManager().setActivePillarMatch(null);
            return;
        }

        match.setPlaying(false);

        new BukkitRunnable() {
            int countdown = plugin.getGameManager().getPillarCountdown();
            @Override
            public void run() {
                if (plugin.getGameManager().getActivePillarMatch() != match) {
                    this.cancel();
                    return;
                }
                if (countdown > 0) {
                    if (countdown == 10 || countdown <= 10) {
                        p1.sendMessage("§eʟᴀ ᴘᴀʀᴛɪᴛᴀ ɪɴɪᴢɪᴀ ɪɴ " + countdown + "...");
                        p2.sendMessage("§eʟᴀ ᴘᴀʀᴛɪᴛᴀ ɪɴɪᴢɪᴀ ɪɴ " + countdown + "...");
                        p1.playSound(p1, Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1f);
                        p2.playSound(p2, Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1f);
                    }
                    countdown--;
                } else {
                    this.cancel();
                    
                    p1.sendMessage("§aɪɴɪᴢɪᴏ!");
                    p2.sendMessage("§aɪɴɪᴢɪᴏ!");
                    p1.playSound(p1, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
                    p2.playSound(p2, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);

                    p1.setHealth(p1.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).getValue());
                    p1.setFoodLevel(20);
                    p2.setHealth(p2.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).getValue());
                    p2.setFoodLevel(20);
                    
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            match.setPlaying(true);
                            p1.setHealth(p1.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).getValue());
                            p1.setFoodLevel(20);
                            p2.setHealth(p2.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).getValue());
                            p2.setFoodLevel(20);
                            startDropping(p1, p2, match);
                            startDeathmatchTimer(match);
                        }
                    }.runTaskLater(plugin, 40); // 2 secondi per scegliere il kit
                }
            }
        }.runTaskTimer(plugin, 0, 20);
    }

    private void startDropping(Player p1, Player p2, PillarMatch match) {
        if (pillarBossBar != null) {
            pillarBossBar.removeAll();
        }
        pillarBossBar = Bukkit.createBossBar("§eᴘʀᴏѕѕɪᴍᴏ ᴏɢɢᴇᴛᴛᴏ: §f" + (plugin.getGameManager().getItemDropInterval() / 20) + "s", BarColor.YELLOW, BarStyle.SOLID);
        pillarBossBar.addPlayer(p1);
        pillarBossBar.addPlayer(p2);
        pillarBossBar.setVisible(true);

        new BukkitRunnable() {
            int interval = plugin.getGameManager().getItemDropInterval();
            int current = interval;

            @Override
            public void run() {
                if (plugin.getGameManager().getActivePillarMatch() != match) {
                    pillarBossBar.removeAll();
                    this.cancel();
                    return;
                }
                
                if (current <= 0) {
                    if (!match.isDeathmatch()) {
                        p1.getInventory().addItem(getRandomPillarItem());
                        p2.getInventory().addItem(getRandomPillarItem());
                    }
                    current = interval;
                }

                double progress = (double) current / interval;
                pillarBossBar.setProgress(Math.max(0, Math.min(1, progress)));
                pillarBossBar.setTitle("§eᴘʀᴏѕѕɪᴍᴏ ᴏɢɢᴇᴛᴛᴏ: §f" + ((current + 19) / 20) + "s");
                
                current -= 2; // Gira ogni 2 ticks per fluidità
            }
        }.runTaskTimer(plugin, 0, 2);
    }

    private ItemStack getRandomPillarItem() {
        Material[] mats = Material.values();
        Material mat = mats[random.nextInt(mats.length)];
        int attempts = 0;
        while (attempts < 1000) {
            if (mat.isItem() && !mat.isAir()) {
                String name = mat.name();
                // Escludi uova spawn, bedrock e oggetti di debug
                if (name.contains("SPAWN_EGG") || name.contains("DEBUG") || mat == Material.BEDROCK) {
                    mat = mats[random.nextInt(mats.length)];
                    attempts++;
                    continue;
                } else {
                    break;
                }
            }
            mat = mats[random.nextInt(mats.length)];
            attempts++;
        }
        if (!mat.isItem()) mat = Material.DIRT;
        return new ItemStack(mat);
    }

    public void clearBossBar() {
        if (pillarBossBar != null) {
            pillarBossBar.removeAll();
        }
    }

    public void handleServerShutdown() {
        PillarMatch match = plugin.getGameManager().getActivePillarMatch();
        if (match != null) {
            refundBets(match);
            
            Player p1 = Bukkit.getPlayer(match.getCreator());
            Player p2 = match.getOpponent() != null ? Bukkit.getPlayer(match.getOpponent()) : null;

            if (p1 != null && p1.isOnline()) {
                p1.getInventory().clear();
                p1.setGameMode(GameMode.SURVIVAL);
                p1.setHealth(p1.getMaxHealth());
                p1.setFoodLevel(20);
                Location respawn = plugin.getGameManager().getPlayerReturn(p1.getUniqueId());
                if (respawn != null) {
                    p1.teleport(respawn);
                    plugin.getGameManager().removePlayerReturn(p1.getUniqueId());
                }
                p1.sendMessage("§cIl server si sta riavviando, la sfida Pillars è stata annullata e le scommesse rimborsate.");
            }
            
            if (p2 != null && p2.isOnline()) {
                p2.getInventory().clear();
                p2.setGameMode(GameMode.SURVIVAL);
                p2.setHealth(p2.getMaxHealth());
                p2.setFoodLevel(20);
                Location respawn = plugin.getGameManager().getPlayerReturn(p2.getUniqueId());
                if (respawn != null) {
                    p2.teleport(respawn);
                    plugin.getGameManager().removePlayerReturn(p2.getUniqueId());
                }
                p2.sendMessage("§cIl server si sta riavviando, la sfida Pillars è stata annullata e le scommesse rimborsate.");
            }

            Location center = plugin.getGameManager().getPillarFirst();
            cleanupArena(center);
        }
        clearBossBar();
    }

    private void finishPillarMatch(Player winner, Player loser, PillarMatch match) {
        plugin.getGameManager().setActivePillarMatch(null);
        if (pillarBossBar != null) pillarBossBar.removeAll();
        
        double prize = match.getAmount() * plugin.getGameManager().getWinMultiplier();
        Location center = plugin.getGameManager().getPillarFirst();

        if (winner != null && winner.isOnline()) {
            winner.setGameMode(GameMode.SPECTATOR);
            winner.getInventory().clear();
            winner.setHealth(winner.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).getValue());
            winner.setFoodLevel(20);
            winner.playSound(winner, Sound.ENTITY_ENDER_DRAGON_DEATH, 0.5f, 1f);
            winner.sendTitle(ItemBuilder.translate("§a§lVITTORIA!"), "", 10, 80, 10);
            handleBets(winner, match);
            // Prelievo e vincita dopo il risultato
            SunnyCoinflip.getEconomy().withdrawPlayer(winner, match.getAmount());
            SunnyCoinflip.getEconomy().depositPlayer(winner, prize);

            winner.sendMessage(ItemBuilder.translate("§aʜᴀɪ ɢᴜᴀᴅᴀɢɴᴀᴛᴏ §r§f\uE0D8 §e" + String.format("%.0f", prize)));
        }
        if (loser != null && loser.isOnline()) {
            loser.setGameMode(GameMode.SPECTATOR);
            loser.getInventory().clear();
            loser.setHealth(loser.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).getValue());
            loser.playSound(loser, Sound.ENTITY_ENDER_DRAGON_DEATH, 0.5f, 1f);
            loser.setFoodLevel(20);
            loser.sendTitle(ItemBuilder.translate("§c§lSCONFITTA!"), "", 10, 80, 10);
            SunnyCoinflip.getEconomy().withdrawPlayer(loser, match.getAmount());
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                if (winner != null && winner.isOnline()) {
                    winner.setGameMode(GameMode.SURVIVAL);
                    org.bukkit.Location respawn = plugin.getGameManager().getPlayerReturn(winner.getUniqueId());
                    if (respawn != null) {
                        winner.teleport(respawn);
                        plugin.getGameManager().removePlayerReturn(winner.getUniqueId());
                    }
                }
                if (loser != null && loser.isOnline()) {
                    loser.setGameMode(GameMode.SURVIVAL);
                    org.bukkit.Location respawn = plugin.getGameManager().getPlayerReturn(loser.getUniqueId());
                    if (respawn != null) {
                        loser.teleport(respawn);
                        plugin.getGameManager().removePlayerReturn(loser.getUniqueId());
                    }
                    
                    // Prelievo perdente a fine match
                }

                // Pulizia arena dopo il teletrasporto
                cleanupArena(center);
            }
        }.runTaskLater(plugin, 60); // Dopo 3 secondi
    }

    private void cleanupArena(Location center) {
        if (center == null) return;
        World world = center.getWorld();
        if (world == null) return;

        java.util.List<Location> cleanupLocs = new java.util.ArrayList<>();
        cleanupLocs.add(center);
        if (plugin.getGameManager().getPillarOpponent() != null) {
            cleanupLocs.add(plugin.getGameManager().getPillarOpponent());
        }

        int radius = 20;
        int minY = -63;
        int maxY = plugin.getGameManager().getMaxHeight() + 5;

        for (Location loc : cleanupLocs) {
            if (loc.getWorld() == null || !loc.getWorld().getName().equals(world.getName())) continue;
            
            int minX = loc.getBlockX() - radius;
            int maxX = loc.getBlockX() + radius;
            int minZ = loc.getBlockZ() - radius;
            int maxZ = loc.getBlockZ() + radius;

            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    for (int y = minY; y <= maxY; y++) {
                        Block block = world.getBlockAt(x, y, z);
                        if (block.getType() != Material.AIR && block.getType() != Material.BEDROCK) {
                            block.setType(Material.AIR);
                        }
                    }
                }
            }
        }

        // Rimuovi entità (non giocatori) nel raggio di 50 dal centro
        world.getNearbyEntities(center, 50, 100, 50).forEach(entity -> {
            if (!(entity instanceof Player)) {
                entity.remove();
            }
        });
    }

    @EventHandler
    public void onQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        handlePillarExit(event.getPlayer(), false);
    }

    @EventHandler
    public void onWorldChange(org.bukkit.event.player.PlayerChangedWorldEvent event) {
        handlePillarExit(event.getPlayer(), true);
    }

    private void handlePillarExit(Player player, boolean isWorldChange) {
        PillarMatch match = plugin.getGameManager().getActivePillarMatch();
        if (match == null) return;

        java.util.UUID playerUUID = player.getUniqueId();
        if (playerUUID.equals(match.getCreator()) || (match.getOpponent() != null && playerUUID.equals(match.getOpponent()))) {
            // Tolleranza 0.5s per il cambio mondo iniziale
            if (isWorldChange) {
                long joinTime = playerUUID.equals(match.getCreator()) ? match.getCreatorJoinTime() : match.getOpponentJoinTime();
                if (System.currentTimeMillis() - joinTime < 500) {
                    return;
                }
            }

            if (match.isPlaying()) {
                Player winner = playerUUID.equals(match.getCreator()) ? 
                        org.bukkit.Bukkit.getPlayer(match.getOpponent()) : 
                        org.bukkit.Bukkit.getPlayer(match.getCreator());
                finishPillarMatch(winner, player, match);
            } else {
                refundBets(match);
                plugin.getGameManager().setActivePillarMatch(null);
                
                java.util.UUID otherUUID = playerUUID.equals(match.getCreator()) ? match.getOpponent() : match.getCreator();
                
                // Teletrasporta il giocatore che ha cambiato mondo (se è un cambio mondo)
                if (player.isOnline()) {
                    player.setHealth(player.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).getValue());
                    player.setFoodLevel(20);
                    org.bukkit.Location respawn = plugin.getGameManager().getPlayerReturn(playerUUID);
                    if (respawn != null) {
                        player.teleport(respawn);
                        plugin.getGameManager().removePlayerReturn(playerUUID);
                    }
                }
                
                // Teletrasporta l'altro giocatore
                if (otherUUID != null) {
                    Player other = org.bukkit.Bukkit.getPlayer(otherUUID);
                    if (other != null && other.isOnline()) {
                        other.setHealth(other.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).getValue());
                        other.setFoodLevel(20);
                        other.sendMessage("§cLo sfidante è uscito o la partita è stata annullata.");
                        org.bukkit.Location respawn = plugin.getGameManager().getPlayerReturn(otherUUID);
                        if (respawn != null) {
                            other.teleport(respawn);
                            plugin.getGameManager().removePlayerReturn(otherUUID);
                        }
                    }
                }
                
                if (plugin.getGameManager().getPillarFirst() != null) {
                    cleanupArena(plugin.getGameManager().getPillarFirst());
                }
            }
        }
    }

    @EventHandler
    public void onJoin(org.bukkit.event.player.PlayerJoinEvent event) {
        Player player = event.getPlayer();
        org.bukkit.Location respawn = plugin.getGameManager().getPlayerReturn(player.getUniqueId());
        if (respawn != null) {
            player.setHealth(player.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).getValue());
            player.setFoodLevel(20);
            player.teleport(respawn);
            plugin.getGameManager().removePlayerReturn(player.getUniqueId());
        }
    }

    @EventHandler
    public void onDamage(org.bukkit.event.entity.EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player player = (Player) event.getEntity();
        PillarMatch match = plugin.getGameManager().getActivePillarMatch();
        if (match == null) return;
        if (!player.getUniqueId().equals(match.getCreator()) && !player.getUniqueId().equals(match.getOpponent())) return;

        if (!match.isPlaying()) {
            event.setCancelled(true);
            return;
        }

        if (player.getHealth() - event.getFinalDamage() < 1.0) {
            event.setCancelled(true);
            player.setHealth(1.0);
            Player winner = player.getUniqueId().equals(match.getCreator()) ? 
                    org.bukkit.Bukkit.getPlayer(match.getOpponent()) : 
                    org.bukkit.Bukkit.getPlayer(match.getCreator());
            
            finishPillarMatch(winner, player, match);
        }
    }

    @EventHandler
    public void onProjectileHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Snowball || event.getDamager() instanceof Egg)) return;
        if (!(event.getEntity() instanceof Player)) return;
        
        Player victim = (Player) event.getEntity();
        PillarMatch match = plugin.getGameManager().getActivePillarMatch();
        if (match == null || !match.isStarted()) return;
        if (!victim.getUniqueId().equals(match.getCreator()) && !victim.getUniqueId().equals(match.getOpponent())) return;

        event.setDamage(0.01); // Trigger knockback
    }

    @EventHandler
    public void onMobSpawn(org.bukkit.event.entity.EntitySpawnEvent event) {
        if (!(event.getEntity() instanceof org.bukkit.entity.Mob)) return;
        org.bukkit.entity.Mob mob = (org.bukkit.entity.Mob) event.getEntity();
        
        PillarMatch match = plugin.getGameManager().getActivePillarMatch();
        if (match == null || !match.isStarted()) return;
        
        // Se lo spawn è vicino ai pillar
        if (plugin.getGameManager().getPillarFirst() != null && 
            event.getLocation().getWorld().equals(plugin.getGameManager().getPillarFirst().getWorld())) {
            double dist1 = event.getLocation().distanceSquared(plugin.getGameManager().getPillarFirst());
            double dist2 = event.getLocation().distanceSquared(plugin.getGameManager().getPillarOpponent());
            
            if (dist1 < 100 || dist2 < 100) {
                Player p1 = org.bukkit.Bukkit.getPlayer(match.getCreator());
                Player p2 = org.bukkit.Bukkit.getPlayer(match.getOpponent());
                
                if (p1 != null && p2 != null) {
                    // Targettizza l'altro giocatore
                    if (dist1 < dist2) {
                        mob.setTarget(p2);
                    } else {
                        mob.setTarget(p1);
                    }
                }
            }
        }
    }

    private void startDeathmatchTimer(PillarMatch match) {
        new BukkitRunnable() {
            int time = 180; // 3 minuti
            @Override
            public void run() {
                if (plugin.getGameManager().getActivePillarMatch() != match) {
                    this.cancel();
                    return;
                }
                if (time <= 0) {
                    match.setDeathmatch(true);
                    clearBossBar();
                    Player p1 = Bukkit.getPlayer(match.getCreator());
                    Player p2 = Bukkit.getPlayer(match.getOpponent());
                    if (p1 != null) p1.sendMessage("§c§l§nDEATHMATCH!§7 Iniziano a piovere TNT!");
                    if (p2 != null) p2.sendMessage("§c§l§nDEATHMATCH!§7 Iniziano a piovere TNT!");
                    startTNTShower(match);
                    this.cancel();
                }
                time--;
            }
        }.runTaskTimer(plugin, 0, 20);
    }

    private void startTNTShower(PillarMatch match) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (plugin.getGameManager().getActivePillarMatch() != match || !match.isDeathmatch()) {
                    this.cancel();
                    return;
                }
                Player p1 = Bukkit.getPlayer(match.getCreator());
                Player p2 = Bukkit.getPlayer(match.getOpponent());
                if (p1 != null && p1.isOnline()) spawnTNT(p1.getLocation());
                if (p2 != null && p2.isOnline()) spawnTNT(p2.getLocation());
            }
        }.runTaskTimer(plugin, 0, 60); // Ogni 3 secondi
    }

    private void spawnTNT(Location loc) {
        Location tntLoc = loc.clone().add(random.nextDouble() * 6 - 3, 12, random.nextDouble() * 6 - 3);
        loc.getWorld().spawn(tntLoc, org.bukkit.entity.TNTPrimed.class);
    }

    private void handleBets(Player winner, PillarMatch match) {
        if (winner == null) return;
        java.util.Map<java.util.UUID, Double> winningBets = winner.getUniqueId().equals(match.getCreator()) ? match.getCreatorBets() : match.getOpponentBets();
        
        for (java.util.Map.Entry<java.util.UUID, Double> entry : winningBets.entrySet()) {
            org.bukkit.OfflinePlayer bettor = Bukkit.getOfflinePlayer(entry.getKey());
            double prize = entry.getValue() * plugin.getGameManager().getWinMultiplier();
            SunnyCoinflip.getEconomy().depositPlayer(bettor, prize);
            if (bettor.isOnline()) {
                ((Player) bettor).sendMessage("§aIl giocatore su cui hai scommesso ha vinto! Hai ricevuto §r§f\uE0D8 §e" + String.format("%.0f", prize) + "§a!");
            }
        }
    }

    private void refundBets(PillarMatch match) {
        for (java.util.Map.Entry<java.util.UUID, Double> entry : match.getCreatorBets().entrySet()) {
            SunnyCoinflip.getEconomy().depositPlayer(Bukkit.getOfflinePlayer(entry.getKey()), entry.getValue());
            Player bettor = Bukkit.getPlayer(entry.getKey());
            if (bettor != null) bettor.sendMessage("§cLa sfida è stata annullata, la tua scommessa è stata rimborsata.");
        }
        for (java.util.Map.Entry<java.util.UUID, Double> entry : match.getOpponentBets().entrySet()) {
            SunnyCoinflip.getEconomy().depositPlayer(Bukkit.getOfflinePlayer(entry.getKey()), entry.getValue());
            Player bettor = Bukkit.getPlayer(entry.getKey());
            if (bettor != null) bettor.sendMessage("§cLa sfida è stata annullata, la tua scommessa è stata rimborsata.");
        }
    }

    @EventHandler
    public void onPlayerChangedWorld(org.bukkit.event.player.PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        if (plugin.getGameManager().isRestrictedWorld(player.getWorld())) {
            org.ItsInspector.sunnyCoinflip.models.Coinflip coinflip = plugin.getGameManager().getCoinflip(player.getUniqueId());
            if (coinflip != null) {
                plugin.getGameManager().removeCoinflip(player.getUniqueId());
                SunnyCoinflip.getEconomy().depositPlayer(player, coinflip.getAmount());
                player.sendMessage("§cIl tuo coinflip è stato annullato. §r§f\uE0D8 §e" + String.format("%.0f", coinflip.getAmount()) + "§c è stato restituito.");
            }
        }
    }
}