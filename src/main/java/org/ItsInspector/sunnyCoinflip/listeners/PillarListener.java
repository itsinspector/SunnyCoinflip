package org.ItsInspector.sunnyCoinflip.listeners;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.ItsInspector.sunnyCoinflip.SunnyCoinflip;
import org.ItsInspector.sunnyCoinflip.models.Coinflip;
import org.ItsInspector.sunnyCoinflip.models.PillarMatch;
import org.ItsInspector.sunnyCoinflip.utils.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarFlag;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Egg;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

public class PillarListener implements Listener {
    private final SunnyCoinflip plugin;
    private final Random random = new Random();
    private BossBar pillarBossBar;

    public PillarListener(SunnyCoinflip plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        PillarMatch match = this.plugin.getGameManager().getActivePillarMatch();
        if (match != null) {
            Player player = event.getPlayer();
            if (player.getUniqueId().equals(match.getCreator()) || player.getUniqueId().equals(match.getOpponent())) {
                if (!match.isPlaying()) {
                    Location from = event.getFrom();
                    Location to = event.getTo();
                    if (from.getX() != to.getX() || from.getZ() != to.getZ() || from.getY() != to.getY()) {
                        event.setTo(from.setDirection(to.getDirection()));
                        return;
                    }
                }

                if (match.isStarted()) {
                    long joinTime = player.getUniqueId().equals(match.getCreator()) ? match.getCreatorJoinTime() : match.getOpponentJoinTime();
                    if (System.currentTimeMillis() - joinTime >= 500L) {
                        if (event.getTo().getY() <= (double)-62.0F) {
                            Player winner = player.getUniqueId().equals(match.getCreator()) ? Bukkit.getPlayer(match.getOpponent()) : Bukkit.getPlayer(match.getCreator());
                            this.finishPillarMatch(winner, player, match);
                        }
                    }
                }
            }
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        PillarMatch match = this.plugin.getGameManager().getActivePillarMatch();
        if (match != null && match.isStarted()) {
            Player player = event.getPlayer();
            if (player.getUniqueId().equals(match.getCreator()) || player.getUniqueId().equals(match.getOpponent())) {
                int maxHeight = this.plugin.getGameManager().getMaxHeight();
                if (event.getBlock().getY() > maxHeight) {
                    event.setCancelled(true);
                    player.sendMessage("§cNon puoi piazzare blocchi sopra l'altezza " + maxHeight + "!");
                }

            }
        }
    }

    public void startPillarMatch(final PillarMatch match) {
        match.setStarted(true);
        final Player p1 = Bukkit.getPlayer(match.getCreator());
        final Player p2 = Bukkit.getPlayer(match.getOpponent());
        if (p1 != null && p2 != null) {
            match.setPlaying(false);
            (new BukkitRunnable() {
                int countdown;

                {
                    this.countdown = PillarListener.this.plugin.getGameManager().getPillarCountdown();
                }

                public void run() {
                    if (PillarListener.this.plugin.getGameManager().getActivePillarMatch() != match) {
                        this.cancel();
                    } else {
                        if (this.countdown > 0) {
                            if (this.countdown == 10 || this.countdown <= 10) {
                                p1.sendMessage("§eʟᴀ ᴘᴀʀᴛɪᴛᴀ ɪɴɪᴢɪᴀ ɪɴ " + this.countdown + "...");
                                p2.sendMessage("§eʟᴀ ᴘᴀʀᴛɪᴛᴀ ɪɴɪᴢɪᴀ ɪɴ " + this.countdown + "...");
                                p1.playSound(p1, Sound.BLOCK_NOTE_BLOCK_PLING, 1.0F, 1.0F);
                                p2.playSound(p2, Sound.BLOCK_NOTE_BLOCK_PLING, 1.0F, 1.0F);
                            }

                            --this.countdown;
                        } else {
                            this.cancel();
                            p1.sendMessage("§aɪɴɪᴢɪᴏ!");
                            p2.sendMessage("§aɪɴɪᴢɪᴏ!");
                            p1.playSound(p1, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0F, 1.0F);
                            p2.playSound(p2, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0F, 1.0F);
                            p1.setHealth(p1.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue());
                            p1.setFoodLevel(20);
                            p2.setHealth(p2.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue());
                            p2.setFoodLevel(20);
                            (new BukkitRunnable() {
                                @Override
                                public void run() {
                                    match.setPlaying(true);
                                    p1.setHealth(p1.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue());
                                    p1.setFoodLevel(20);
                                    p2.setHealth(p2.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue());
                                    p2.setFoodLevel(20);
                                    PillarListener.this.startDropping(p1, p2, match);
                                    PillarListener.this.startDeathmatchTimer(match);
                                }
                            }).runTaskLater(PillarListener.this.plugin, 40L);
                        }

                    }
                }
            }).runTaskTimer(this.plugin, 0L, 20L);
        } else {
            this.plugin.getGameManager().setActivePillarMatch((PillarMatch)null);
        }
    }

    private void startDropping(final Player p1, final Player p2, final PillarMatch match) {
        if (this.pillarBossBar != null) {
            this.pillarBossBar.removeAll();
        }

        this.pillarBossBar = Bukkit.createBossBar("§eᴘʀᴏѕѕɪᴍᴏ ᴏɢɢᴇᴛᴛᴏ: §f" + this.plugin.getGameManager().getItemDropInterval() / 20 + "s", BarColor.YELLOW, BarStyle.SOLID, new BarFlag[0]);
        this.pillarBossBar.addPlayer(p1);
        this.pillarBossBar.addPlayer(p2);
        this.pillarBossBar.setVisible(true);
        (new BukkitRunnable() {
            int interval;
            int current;

            {
                this.interval = PillarListener.this.plugin.getGameManager().getItemDropInterval();
                this.current = this.interval;
            }

            public void run() {
                if (PillarListener.this.plugin.getGameManager().getActivePillarMatch() != match) {
                    PillarListener.this.pillarBossBar.removeAll();
                    this.cancel();
                } else {
                    if (this.current <= 0) {
                        if (!match.isDeathmatch()) {
                            p1.getInventory().addItem(new ItemStack[]{PillarListener.this.getRandomPillarItem()});
                            p2.getInventory().addItem(new ItemStack[]{PillarListener.this.getRandomPillarItem()});
                        }

                        this.current = this.interval;
                    }

                    double progress = (double)this.current / (double)this.interval;
                    PillarListener.this.pillarBossBar.setProgress(Math.max((double)0.0F, Math.min((double)1.0F, progress)));
                    int var10001 = this.current + 19;
                    PillarListener.this.pillarBossBar.setTitle("§eᴘʀᴏѕѕɪᴍᴏ ᴏɢɢᴇᴛᴛᴏ: §f" + var10001 / 20 + "s");
                    this.current -= 2;
                }
            }
        }).runTaskTimer(this.plugin, 0L, 2L);
    }

    private ItemStack getRandomPillarItem() {
        Material[] mats = Material.values();
        Material mat = mats[this.random.nextInt(mats.length)];
        int attempts = 0;

        while(attempts < 1000) {
            if (mat.isItem() && !mat.isAir()) {
                String name = mat.name();
                if (!name.contains("SPAWN_EGG") && !name.contains("DEBUG") && mat != Material.BEDROCK) {
                    break;
                }

                mat = mats[this.random.nextInt(mats.length)];
                ++attempts;
            } else {
                mat = mats[this.random.nextInt(mats.length)];
                ++attempts;
            }
        }

        if (!mat.isItem()) {
            mat = Material.DIRT;
        }

        return new ItemStack(mat);
    }

    public void clearBossBar() {
        if (this.pillarBossBar != null) {
            this.pillarBossBar.removeAll();
        }

    }

    public void handleServerShutdown() {
        PillarMatch match = this.plugin.getGameManager().getActivePillarMatch();
        if (match != null) {
            this.refundBets(match);
            Player p1 = Bukkit.getPlayer(match.getCreator());
            Player p2 = match.getOpponent() != null ? Bukkit.getPlayer(match.getOpponent()) : null;
            if (p1 != null && p1.isOnline()) {
                p1.getInventory().clear();
                p1.setGameMode(GameMode.SURVIVAL);
                p1.setHealth(p1.getMaxHealth());
                p1.setFoodLevel(20);
                Location respawn = this.plugin.getGameManager().getPlayerReturn(p1.getUniqueId());
                if (respawn != null) {
                    p1.teleport(respawn);
                    this.plugin.getGameManager().removePlayerReturn(p1.getUniqueId());
                }

                p1.sendMessage("§cIl server si sta riavviando, la sfida Pillars è stata annullata e le scommesse rimborsate.");
            }

            if (p2 != null && p2.isOnline()) {
                p2.getInventory().clear();
                p2.setGameMode(GameMode.SURVIVAL);
                p2.setHealth(p2.getMaxHealth());
                p2.setFoodLevel(20);
                Location respawn = this.plugin.getGameManager().getPlayerReturn(p2.getUniqueId());
                if (respawn != null) {
                    p2.teleport(respawn);
                    this.plugin.getGameManager().removePlayerReturn(p2.getUniqueId());
                }

                p2.sendMessage("§cIl server si sta riavviando, la sfida Pillars è stata annullata e le scommesse rimborsate.");
            }

            Location center = this.plugin.getGameManager().getPillarFirst();
            this.cleanupArena(center);
        }

        this.clearBossBar();
    }

    private void finishPillarMatch(final Player winner, final Player loser, PillarMatch match) {
        this.plugin.getGameManager().setActivePillarMatch((PillarMatch)null);
        if (this.pillarBossBar != null) {
            this.pillarBossBar.removeAll();
        }

        double prize = match.getAmount() * this.plugin.getGameManager().getWinMultiplier();
        final Location center = this.plugin.getGameManager().getPillarFirst();
        if (winner != null && winner.isOnline()) {
            winner.setGameMode(GameMode.SPECTATOR);
            winner.getInventory().clear();
            winner.setHealth(winner.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue());
            winner.setFoodLevel(20);
            winner.playSound(winner, Sound.ENTITY_ENDER_DRAGON_DEATH, 0.5F, 1.0F);
            winner.sendTitle(ItemBuilder.translate("§a§lVITTORIA!"), "", 10, 80, 10);
            this.handleBets(winner, match);
            SunnyCoinflip.getEconomy().withdrawPlayer(winner, match.getAmount());
            SunnyCoinflip.getEconomy().depositPlayer(winner, prize);
            Object[] var10002 = new Object[]{prize};
            winner.sendMessage(ItemBuilder.translate("§aʜᴀɪ ɢᴜᴀᴅᴀɢɴᴀᴛᴏ §r§f\ue0d8 §e" + String.format("%.0f", var10002)));
        }

        if (loser != null && loser.isOnline()) {
            loser.setGameMode(GameMode.SPECTATOR);
            loser.getInventory().clear();
            loser.setHealth(loser.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue());
            loser.playSound(loser, Sound.ENTITY_ENDER_DRAGON_DEATH, 0.5F, 1.0F);
            loser.setFoodLevel(20);
            loser.sendTitle(ItemBuilder.translate("§c§lSCONFITTA!"), "", 10, 80, 10);
            SunnyCoinflip.getEconomy().withdrawPlayer(loser, match.getAmount());
        }

        (new BukkitRunnable() {
            public void run() {
                if (winner != null && winner.isOnline()) {
                    winner.setGameMode(GameMode.SURVIVAL);
                    Location respawn = PillarListener.this.plugin.getGameManager().getPlayerReturn(winner.getUniqueId());
                    if (respawn != null) {
                        winner.teleport(respawn);
                        PillarListener.this.plugin.getGameManager().removePlayerReturn(winner.getUniqueId());
                    }
                }

                if (loser != null && loser.isOnline()) {
                    loser.setGameMode(GameMode.SURVIVAL);
                    Location respawn = PillarListener.this.plugin.getGameManager().getPlayerReturn(loser.getUniqueId());
                    if (respawn != null) {
                        loser.teleport(respawn);
                        PillarListener.this.plugin.getGameManager().removePlayerReturn(loser.getUniqueId());
                    }
                }

                PillarListener.this.cleanupArena(center);
            }
        }).runTaskLater(this.plugin, 60L);
    }

    private void cleanupArena(Location center) {
        if (center != null) {
            World world = center.getWorld();
            if (world != null) {
                List<Location> cleanupLocs = new ArrayList();
                cleanupLocs.add(center);
                if (this.plugin.getGameManager().getPillarOpponent() != null) {
                    cleanupLocs.add(this.plugin.getGameManager().getPillarOpponent());
                }

                int radius = 20;
                int minY = -63;
                int maxY = this.plugin.getGameManager().getMaxHeight() + 5;

                for(Location loc : cleanupLocs) {
                    if (loc.getWorld() != null && loc.getWorld().getName().equals(world.getName())) {
                        int minX = loc.getBlockX() - radius;
                        int maxX = loc.getBlockX() + radius;
                        int minZ = loc.getBlockZ() - radius;
                        int maxZ = loc.getBlockZ() + radius;

                        for(int x = minX; x <= maxX; ++x) {
                            for(int z = minZ; z <= maxZ; ++z) {
                                for(int y = minY; y <= maxY; ++y) {
                                    Block block = world.getBlockAt(x, y, z);
                                    if (block.getType() != Material.AIR && block.getType() != Material.BEDROCK) {
                                        block.setType(Material.AIR);
                                    }
                                }
                            }
                        }
                    }
                }

                world.getNearbyEntities(center, (double)50.0F, (double)100.0F, (double)50.0F).forEach((entity) -> {
                    if (!(entity instanceof Player)) {
                        entity.remove();
                    }

                });
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        this.handlePillarExit(event.getPlayer(), false);
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        this.handlePillarExit(event.getPlayer(), true);
    }

    private void handlePillarExit(Player player, boolean isWorldChange) {
        PillarMatch match = this.plugin.getGameManager().getActivePillarMatch();
        if (match != null) {
            UUID playerUUID = player.getUniqueId();
            if (playerUUID.equals(match.getCreator()) || match.getOpponent() != null && playerUUID.equals(match.getOpponent())) {
                if (isWorldChange) {
                    long joinTime = playerUUID.equals(match.getCreator()) ? match.getCreatorJoinTime() : match.getOpponentJoinTime();
                    if (System.currentTimeMillis() - joinTime < 500L) {
                        return;
                    }
                }

                if (match.isPlaying()) {
                    Player winner = playerUUID.equals(match.getCreator()) ? Bukkit.getPlayer(match.getOpponent()) : Bukkit.getPlayer(match.getCreator());
                    this.finishPillarMatch(winner, player, match);
                } else {
                    this.refundBets(match);
                    this.plugin.getGameManager().setActivePillarMatch((PillarMatch)null);
                    UUID otherUUID = playerUUID.equals(match.getCreator()) ? match.getOpponent() : match.getCreator();
                    if (player.isOnline()) {
                        player.setHealth(player.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue());
                        player.setFoodLevel(20);
                        Location respawn = this.plugin.getGameManager().getPlayerReturn(playerUUID);
                        if (respawn != null) {
                            player.teleport(respawn);
                            this.plugin.getGameManager().removePlayerReturn(playerUUID);
                        }
                    }

                    if (otherUUID != null) {
                        Player other = Bukkit.getPlayer(otherUUID);
                        if (other != null && other.isOnline()) {
                            other.setHealth(other.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue());
                            other.setFoodLevel(20);
                            other.sendMessage("§cLo sfidante è uscito o la partita è stata annullata.");
                            Location respawn = this.plugin.getGameManager().getPlayerReturn(otherUUID);
                            if (respawn != null) {
                                other.teleport(respawn);
                                this.plugin.getGameManager().removePlayerReturn(otherUUID);
                            }
                        }
                    }

                    if (this.plugin.getGameManager().getPillarFirst() != null) {
                        this.cleanupArena(this.plugin.getGameManager().getPillarFirst());
                    }
                }
            }

        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Location respawn = this.plugin.getGameManager().getPlayerReturn(player.getUniqueId());
        if (respawn != null) {
            player.setHealth(player.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue());
            player.setFoodLevel(20);
            player.teleport(respawn);
            this.plugin.getGameManager().removePlayerReturn(player.getUniqueId());
        }

    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player)event.getEntity();
            PillarMatch match = this.plugin.getGameManager().getActivePillarMatch();
            if (match != null) {
                if (player.getUniqueId().equals(match.getCreator()) || player.getUniqueId().equals(match.getOpponent())) {
                    if (!match.isPlaying()) {
                        event.setCancelled(true);
                    } else {
                        if (player.getHealth() - event.getFinalDamage() < (double)1.0F) {
                            event.setCancelled(true);
                            player.setHealth((double)1.0F);
                            Player winner = player.getUniqueId().equals(match.getCreator()) ? Bukkit.getPlayer(match.getOpponent()) : Bukkit.getPlayer(match.getCreator());
                            this.finishPillarMatch(winner, player, match);
                        }

                    }
                }
            }
        }
    }

    @EventHandler
    public void onProjectileHit(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Snowball || event.getDamager() instanceof Egg) {
            if (event.getEntity() instanceof Player) {
                Player victim = (Player)event.getEntity();
                PillarMatch match = this.plugin.getGameManager().getActivePillarMatch();
                if (match != null && match.isStarted()) {
                    if (victim.getUniqueId().equals(match.getCreator()) || victim.getUniqueId().equals(match.getOpponent())) {
                        event.setDamage(0.01);
                    }
                }
            }
        }
    }

    @EventHandler
    public void onMobSpawn(EntitySpawnEvent event) {
        if (event.getEntity() instanceof Mob) {
            Mob mob = (Mob)event.getEntity();
            PillarMatch match = this.plugin.getGameManager().getActivePillarMatch();
            if (match != null && match.isStarted()) {
                if (this.plugin.getGameManager().getPillarFirst() != null && event.getLocation().getWorld().equals(this.plugin.getGameManager().getPillarFirst().getWorld())) {
                    double dist1 = event.getLocation().distanceSquared(this.plugin.getGameManager().getPillarFirst());
                    double dist2 = event.getLocation().distanceSquared(this.plugin.getGameManager().getPillarOpponent());
                    if (dist1 < (double)100.0F || dist2 < (double)100.0F) {
                        Player p1 = Bukkit.getPlayer(match.getCreator());
                        Player p2 = Bukkit.getPlayer(match.getOpponent());
                        if (p1 != null && p2 != null) {
                            if (dist1 < dist2) {
                                mob.setTarget(p2);
                            } else {
                                mob.setTarget(p1);
                            }
                        }
                    }
                }

            }
        }
    }

    private void startDeathmatchTimer(final PillarMatch match) {
        (new BukkitRunnable() {
            int time = 180;

            public void run() {
                if (PillarListener.this.plugin.getGameManager().getActivePillarMatch() != match) {
                    this.cancel();
                } else {
                    if (this.time <= 0) {
                        match.setDeathmatch(true);
                        PillarListener.this.clearBossBar();
                        Player p1 = Bukkit.getPlayer(match.getCreator());
                        Player p2 = Bukkit.getPlayer(match.getOpponent());
                        if (p1 != null) {
                            p1.sendMessage("§c§l§nDEATHMATCH!§7 Iniziano a piovere TNT!");
                        }

                        if (p2 != null) {
                            p2.sendMessage("§c§l§nDEATHMATCH!§7 Iniziano a piovere TNT!");
                        }

                        PillarListener.this.startTNTShower(match);
                        this.cancel();
                    }

                    --this.time;
                }
            }
        }).runTaskTimer(this.plugin, 0L, 20L);
    }

    private void startTNTShower(final PillarMatch match) {
        (new BukkitRunnable() {
            public void run() {
                if (PillarListener.this.plugin.getGameManager().getActivePillarMatch() == match && match.isDeathmatch()) {
                    Player p1 = Bukkit.getPlayer(match.getCreator());
                    Player p2 = Bukkit.getPlayer(match.getOpponent());
                    if (p1 != null && p1.isOnline()) {
                        PillarListener.this.spawnTNT(p1.getLocation());
                    }

                    if (p2 != null && p2.isOnline()) {
                        PillarListener.this.spawnTNT(p2.getLocation());
                    }

                } else {
                    this.cancel();
                }
            }
        }).runTaskTimer(this.plugin, 0L, 60L);
    }

    private void spawnTNT(Location loc) {
        Location tntLoc = loc.clone().add(this.random.nextDouble() * (double)6.0F - (double)3.0F, (double)12.0F, this.random.nextDouble() * (double)6.0F - (double)3.0F);
        loc.getWorld().spawn(tntLoc, TNTPrimed.class);
    }

    private void handleBets(Player winner, PillarMatch match) {
        if (winner != null) {
            Map<UUID, Double> winningBets = winner.getUniqueId().equals(match.getCreator()) ? match.getCreatorBets() : match.getOpponentBets();

            for(Map.Entry<UUID, Double> entry : winningBets.entrySet()) {
                OfflinePlayer bettor = Bukkit.getOfflinePlayer((UUID)entry.getKey());
                double prize = (Double)entry.getValue() * this.plugin.getGameManager().getWinMultiplier();
                SunnyCoinflip.getEconomy().depositPlayer(bettor, prize);
                if (bettor.isOnline()) {
                    Player var10000 = (Player)bettor;
                    Object[] var10002 = new Object[]{prize};
                    var10000.sendMessage("§aIl giocatore su cui hai scommesso ha vinto! Hai ricevuto §r§f\ue0d8 §e" + String.format("%.0f", var10002) + "§a!");
                }
            }

        }
    }

    private void refundBets(PillarMatch match) {
        for(Map.Entry<UUID, Double> entry : match.getCreatorBets().entrySet()) {
            SunnyCoinflip.getEconomy().depositPlayer(Bukkit.getOfflinePlayer((UUID)entry.getKey()), (Double)entry.getValue());
            Player bettor = Bukkit.getPlayer((UUID)entry.getKey());
            if (bettor != null) {
                bettor.sendMessage("§cLa sfida è stata annullata, la tua scommessa è stata rimborsata.");
            }
        }

        for(Map.Entry<UUID, Double> entry : match.getOpponentBets().entrySet()) {
            SunnyCoinflip.getEconomy().depositPlayer(Bukkit.getOfflinePlayer((UUID)entry.getKey()), (Double)entry.getValue());
            Player bettor = Bukkit.getPlayer((UUID)entry.getKey());
            if (bettor != null) {
                bettor.sendMessage("§cLa sfida è stata annullata, la tua scommessa è stata rimborsata.");
            }
        }

    }

    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        if (this.plugin.getGameManager().isRestrictedWorld(player.getWorld())) {
            Coinflip coinflip = this.plugin.getGameManager().getCoinflip(player.getUniqueId());
            if (coinflip != null) {
                this.plugin.getGameManager().removeCoinflip(player.getUniqueId());
                SunnyCoinflip.getEconomy().depositPlayer(player, coinflip.getAmount());
                Object[] var10002 = new Object[]{coinflip.getAmount()};
                player.sendMessage("§cIl tuo coinflip è stato annullato. §r§f\ue0d8 §e" + String.format("%.0f", var10002) + "§c è stato restituito.");
            }
        }

    }
}