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
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.ShulkerBox;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarFlag;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Egg;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.Wither;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.scheduler.BukkitRunnable;

public class PillarListener implements Listener {
    private static final List<Material> SHULKER_VARIANTS = List.of(
            Material.SHULKER_BOX,
            Material.WHITE_SHULKER_BOX,
            Material.ORANGE_SHULKER_BOX,
            Material.MAGENTA_SHULKER_BOX,
            Material.LIGHT_BLUE_SHULKER_BOX,
            Material.YELLOW_SHULKER_BOX,
            Material.LIME_SHULKER_BOX,
            Material.PINK_SHULKER_BOX,
            Material.GRAY_SHULKER_BOX,
            Material.LIGHT_GRAY_SHULKER_BOX,
            Material.CYAN_SHULKER_BOX,
            Material.PURPLE_SHULKER_BOX,
            Material.BLUE_SHULKER_BOX,
            Material.BROWN_SHULKER_BOX,
            Material.GREEN_SHULKER_BOX,
            Material.RED_SHULKER_BOX,
            Material.BLACK_SHULKER_BOX);
    private final SunnyCoinflip plugin;
    private final Random random = new Random();
    private final NamespacedKey pillarMobEggKey;
    private final NamespacedKey pillarMobOwnerKey;
    private final NamespacedKey pillarMobOpponentKey;
    private BossBar pillarBossBar;

    public PillarListener(SunnyCoinflip plugin) {
        this.plugin = plugin;
        this.pillarMobEggKey = new NamespacedKey(plugin, "pillar_mob_egg");
        this.pillarMobOwnerKey = new NamespacedKey(plugin, "pillar_mob_owner");
        this.pillarMobOpponentKey = new NamespacedKey(plugin, "pillar_mob_opponent");
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

    @EventHandler(
            priority = EventPriority.HIGHEST,
            ignoreCancelled = true
    )
    public void onPillarMobEggUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
                || event.getAction() != Action.RIGHT_CLICK_AIR
                && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player owner = event.getPlayer();
        PillarMatch match = this.plugin.getGameManager().getActivePillarMatch();
        if (match == null || !match.isPlaying() || !this.isParticipant(match, owner)) return;

        ItemStack item = event.getItem();
        PillarMobType mobType = this.getPillarMobType(item);
        if (mobType == null) return;

        Player opponent = this.getOpponent(match, owner.getUniqueId());
        if (opponent == null || !opponent.isOnline()) return;

        event.setCancelled(true);
        Location spawnLocation;
        if (event.getClickedBlock() != null) {
            spawnLocation = event.getClickedBlock()
                    .getRelative(event.getBlockFace())
                    .getLocation()
                    .add(0.5, 0.25, 0.5);
        } else {
            spawnLocation = owner.getEyeLocation()
                    .add(owner.getEyeLocation().getDirection().normalize().multiply(3.0));
        }

        if (this.spawnOwnedPillarMob(mobType, owner, opponent, match, spawnLocation)) {
            this.consumeOne(owner);
            owner.playSound(owner.getLocation(), mobType.spawnSound, 1.0f, 0.9f);
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
                            PillarListener.this.restorePillarPlayer(p1);
                            PillarListener.this.restorePillarPlayer(p2);
                            (new BukkitRunnable() {
                                @Override
                                public void run() {
                                    match.setPlaying(true);
                                    PillarListener.this.restorePillarPlayer(p1);
                                    PillarListener.this.restorePillarPlayer(p2);
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
        int shulkerChance = Math.max(0, Math.min(100,
                this.plugin.getConfig().getInt("pillars.shulker-loot-chance-percent", 20)));
        if (this.random.nextInt(100) < shulkerChance) {
            return this.createLootShulker();
        }
        return this.getRandomLootItem(true);
    }

    private ItemStack createLootShulker() {
        Material variant = SHULKER_VARIANTS.get(this.random.nextInt(SHULKER_VARIANTS.size()));
        ItemStack item = new ItemStack(variant);
        ItemMeta rawMeta = item.getItemMeta();
        if (!(rawMeta instanceof BlockStateMeta meta)
                || !(meta.getBlockState() instanceof ShulkerBox shulkerBox)) {
            return this.getRandomLootItem(true);
        }

        int inventorySize = shulkerBox.getInventory().getSize();
        int minimum = Math.max(1, Math.min(inventorySize,
                this.plugin.getConfig().getInt("pillars.shulker-min-loot", 4)));
        int maximum = Math.max(minimum, Math.min(inventorySize,
                this.plugin.getConfig().getInt("pillars.shulker-max-loot", 8)));
        int lootCount = minimum + this.random.nextInt(maximum - minimum + 1);

        List<Integer> availableSlots = new ArrayList<>();
        for (int slot = 0; slot < inventorySize; slot++) {
            availableSlots.add(slot);
        }
        for (int index = 0; index < lootCount; index++) {
            int selected = this.random.nextInt(availableSlots.size());
            int slot = availableSlots.remove(selected);
            shulkerBox.getInventory().setItem(slot, this.getRandomLootItem(true));
        }

        meta.setBlockState(shulkerBox);
        meta.setDisplayName("§d§lShulker del Caos");
        meta.setLore(List.of(
                "§7Contiene loot distribuito casualmente.",
                "§7Potrebbe nascondere un uovo evocatore."));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack getRandomLootItem(boolean allowMobEgg) {
        int eggChance = Math.max(0, Math.min(100,
                this.plugin.getConfig().getInt("pillars.mob-egg-chance-percent", 12)));
        if (allowMobEgg && this.random.nextInt(100) < eggChance) {
            PillarMobType[] types = PillarMobType.values();
            return this.createPillarMobEgg(types[this.random.nextInt(types.length)]);
        }

        Material[] mats = Material.values();
        Material mat = mats[this.random.nextInt(mats.length)];
        int attempts = 0;

        while(attempts < 1000) {
            if (mat.isItem() && !mat.isAir()) {
                String name = mat.name();
                if (this.isSafeRandomLoot(mat, name)) {
                    break;
                }

                mat = mats[this.random.nextInt(mats.length)];
                ++attempts;
            } else {
                mat = mats[this.random.nextInt(mats.length)];
                ++attempts;
            }
        }

        if (!mat.isItem() || !this.isSafeRandomLoot(mat, mat.name())) {
            mat = Material.DIRT;
        }

        return new ItemStack(mat, 1);
    }

    private boolean isSafeRandomLoot(Material material, String name) {
        return material.isItem()
                && !material.isAir()
                && material != Material.BEDROCK
                && !name.contains("SPAWN_EGG")
                && !name.contains("COMMAND_BLOCK")
                && !name.contains("DEBUG")
                && !name.startsWith("TEST_")
                && !name.endsWith("_SHULKER_BOX")
                && material != Material.SHULKER_BOX
                && material != Material.BARRIER
                && material != Material.JIGSAW
                && material != Material.STRUCTURE_BLOCK
                && material != Material.STRUCTURE_VOID;
    }

    private ItemStack createPillarMobEgg(PillarMobType type) {
        ItemStack item = new ItemStack(type.eggMaterial);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        long lifetimeSeconds = Math.max(
                1L,
                this.plugin.getConfig().getLong("pillars.mob-lifetime-seconds", 10L));
        meta.setDisplayName(type.color + "§l" + type.displayName);
        meta.setLore(List.of(
                "§7Evoca un alleato che attacca",
                "§7il tuo avversario per §f" + lifetimeSeconds + " secondi§7."));
        meta.getPersistentDataContainer().set(
                this.pillarMobEggKey,
                PersistentDataType.STRING,
                type.name());
        meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        item.setItemMeta(meta);
        return item;
    }

    private PillarMobType getPillarMobType(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        String stored = item.getItemMeta().getPersistentDataContainer().get(
                this.pillarMobEggKey,
                PersistentDataType.STRING);
        if (stored == null) return null;
        try {
            return PillarMobType.valueOf(stored);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private boolean spawnOwnedPillarMob(
            PillarMobType type,
            Player owner,
            Player opponent,
            PillarMatch match,
            Location spawnLocation) {
        World world = spawnLocation.getWorld();
        if (world == null || !world.equals(owner.getWorld()) || !world.equals(opponent.getWorld())) {
            return false;
        }

        Entity spawned = world.spawnEntity(spawnLocation, type.entityType);
        if (!(spawned instanceof Mob mob)) {
            spawned.remove();
            return false;
        }

        mob.getPersistentDataContainer().set(
                this.pillarMobOwnerKey,
                PersistentDataType.STRING,
                owner.getUniqueId().toString());
        mob.getPersistentDataContainer().set(
                this.pillarMobOpponentKey,
                PersistentDataType.STRING,
                opponent.getUniqueId().toString());
        mob.setCustomName("§f§l" + owner.getName());
        mob.setCustomNameVisible(true);
        mob.setPersistent(false);
        mob.setRemoveWhenFarAway(false);
        mob.setAware(true);
        mob.setCanPickupItems(false);
        mob.setLootTable(null);
        if (mob instanceof Wither wither) {
            wither.setInvulnerableTicks(0);
            wither.setCanTravelThroughPortals(false);
        }
        this.forceOwnedMobTarget(mob, opponent);

        long lifetimeTicks = Math.max(
                20L,
                this.plugin.getConfig().getLong("pillars.mob-lifetime-seconds", 10L) * 20L);
        (new BukkitRunnable() {
            private long livedTicks;

            @Override
            public void run() {
                PillarMatch current = PillarListener.this.plugin.getGameManager().getActivePillarMatch();
                if (!mob.isValid() || mob.isDead() || current != match || !match.isPlaying()
                        || this.livedTicks >= lifetimeTicks) {
                    if (mob.isValid()) mob.remove();
                    this.cancel();
                    return;
                }

                Player currentOpponent = Bukkit.getPlayer(opponent.getUniqueId());
                if (currentOpponent == null || !currentOpponent.isOnline()
                        || !currentOpponent.getWorld().equals(mob.getWorld())) {
                    mob.remove();
                    this.cancel();
                    return;
                }
                PillarListener.this.forceOwnedMobTarget(mob, currentOpponent);
                this.livedTicks += 10L;
            }
        }).runTaskTimer(this.plugin, 0L, 10L);
        return true;
    }

    private void forceOwnedMobTarget(Mob mob, Player opponent) {
        mob.setTarget(opponent);
        if (mob instanceof Wither wither) {
            for (Wither.Head head : Wither.Head.values()) {
                wither.setTarget(head, opponent);
            }
        }
    }

    private Player getOpponent(PillarMatch match, UUID ownerId) {
        UUID opponentId = ownerId.equals(match.getCreator())
                ? match.getOpponent()
                : match.getCreator();
        return opponentId == null ? null : Bukkit.getPlayer(opponentId);
    }

    private boolean isParticipant(PillarMatch match, Player player) {
        UUID playerId = player.getUniqueId();
        return playerId.equals(match.getCreator())
                || match.getOpponent() != null && playerId.equals(match.getOpponent());
    }

    private void consumeOne(Player player) {
        ItemStack stack = player.getInventory().getItemInMainHand();
        if (this.getPillarMobType(stack) == null) return;
        if (stack.getAmount() <= 1) {
            player.getInventory().setItemInMainHand(null);
        } else {
            stack.setAmount(stack.getAmount() - 1);
            player.getInventory().setItemInMainHand(stack);
        }
        player.updateInventory();
    }

    private UUID getOwnedMobOwner(Entity damager) {
        Entity source = this.resolveOwnedMobSource(damager);
        return source == null ? null : this.readUuid(source, this.pillarMobOwnerKey);
    }

    private UUID getOwnedMobOpponent(Entity damager) {
        Entity source = this.resolveOwnedMobSource(damager);
        return source == null ? null : this.readUuid(source, this.pillarMobOpponentKey);
    }

    private Entity resolveOwnedMobSource(Entity entity) {
        if (this.readUuid(entity, this.pillarMobOwnerKey) != null) return entity;
        if (entity instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof Entity shooterEntity
                    && this.readUuid(shooterEntity, this.pillarMobOwnerKey) != null) {
                return shooterEntity;
            }
        }
        return null;
    }

    private UUID readUuid(Entity entity, NamespacedKey key) {
        String stored = entity.getPersistentDataContainer().get(key, PersistentDataType.STRING);
        if (stored == null) return null;
        try {
            return UUID.fromString(stored);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public void clearBossBar() {
        if (this.pillarBossBar != null) {
            this.pillarBossBar.removeAll();
        }

    }

    private void restorePillarPlayer(Player player) {
        new ArrayList<>(player.getActivePotionEffects())
                .forEach(effect -> player.removePotionEffect(effect.getType()));
        player.setHealth(player.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue());
        player.setFoodLevel(20);
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
                this.restorePillarPlayer(p1);
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
                this.restorePillarPlayer(p2);
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
            this.restorePillarPlayer(winner);
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
            this.restorePillarPlayer(loser);
            loser.playSound(loser, Sound.ENTITY_ENDER_DRAGON_DEATH, 0.5F, 1.0F);
            loser.sendTitle(ItemBuilder.translate("§c§lSCONFITTA!"), "", 10, 80, 10);
            SunnyCoinflip.getEconomy().withdrawPlayer(loser, match.getAmount());
        }

        (new BukkitRunnable() {
            public void run() {
                if (winner != null && winner.isOnline()) {
                    winner.setGameMode(GameMode.SURVIVAL);
                    PillarListener.this.restorePillarPlayer(winner);
                    Location respawn = PillarListener.this.plugin.getGameManager().getPlayerReturn(winner.getUniqueId());
                    if (respawn != null) {
                        winner.teleport(respawn);
                        PillarListener.this.plugin.getGameManager().removePlayerReturn(winner.getUniqueId());
                    }
                }

                if (loser != null && loser.isOnline()) {
                    loser.setGameMode(GameMode.SURVIVAL);
                    PillarListener.this.restorePillarPlayer(loser);
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
                        this.restorePillarPlayer(player);
                        Location respawn = this.plugin.getGameManager().getPlayerReturn(playerUUID);
                        if (respawn != null) {
                            player.teleport(respawn);
                            this.plugin.getGameManager().removePlayerReturn(playerUUID);
                        }
                    }

                    if (otherUUID != null) {
                        Player other = Bukkit.getPlayer(otherUUID);
                        if (other != null && other.isOnline()) {
                            this.restorePillarPlayer(other);
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
            this.restorePillarPlayer(player);
            player.teleport(respawn);
            this.plugin.getGameManager().removePlayerReturn(player.getUniqueId());
        }

    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (event.isCancelled()) return;
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

    @EventHandler(
            priority = EventPriority.LOWEST
    )
    public void onOwnedMobDamage(EntityDamageByEntityEvent event) {
        UUID ownerId = this.getOwnedMobOwner(event.getDamager());
        if (ownerId == null || !(event.getEntity() instanceof Player victim)) return;

        UUID opponentId = this.getOwnedMobOpponent(event.getDamager());
        if (victim.getUniqueId().equals(ownerId)
                || opponentId == null
                || !victim.getUniqueId().equals(opponentId)) {
            event.setCancelled(true);
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

    @EventHandler(
            priority = EventPriority.HIGHEST,
            ignoreCancelled = true
    )
    public void onOwnedMobTarget(EntityTargetLivingEntityEvent event) {
        if (!(event.getEntity() instanceof Mob mob)) return;
        UUID opponentId = this.readUuid(mob, this.pillarMobOpponentKey);
        if (opponentId == null) return;

        Player opponent = Bukkit.getPlayer(opponentId);
        PillarMatch match = this.plugin.getGameManager().getActivePillarMatch();
        if (opponent == null || !opponent.isOnline()
                || match == null || !match.isPlaying()
                || !this.isParticipant(match, opponent)) {
            event.setCancelled(true);
            return;
        }
        if (!opponent.equals(event.getTarget())) {
            event.setTarget(opponent);
        }
    }

    @EventHandler(
            priority = EventPriority.HIGHEST,
            ignoreCancelled = true
    )
    public void onOwnedMobExplode(EntityExplodeEvent event) {
        if (this.getOwnedMobOwner(event.getEntity()) != null) {
            event.blockList().clear();
            event.setYield(0.0f);
        }
    }

    @EventHandler(
            priority = EventPriority.HIGHEST,
            ignoreCancelled = true
    )
    public void onOwnedMobChangeBlock(EntityChangeBlockEvent event) {
        if (this.getOwnedMobOwner(event.getEntity()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onOwnedMobDeath(EntityDeathEvent event) {
        if (this.getOwnedMobOwner(event.getEntity()) != null) {
            event.getDrops().clear();
            event.setDroppedExp(0);
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

    private enum PillarMobType {
        WITHER(
                Material.WITHER_SPAWN_EGG,
                EntityType.WITHER,
                "Sans",
                "§5",
                Sound.ENTITY_WITHER_SPAWN),
        GHAST(
                Material.GHAST_SPAWN_EGG,
                EntityType.GHAST,
                "Ex Furiosa",
                "§f",
                Sound.ENTITY_GHAST_SCREAM),
        BLAZE(
                Material.BLAZE_SPAWN_EGG,
                EntityType.BLAZE,
                "Terrorista aereo",
                "§6",
                Sound.ENTITY_BLAZE_AMBIENT),
        ZOMBIE(
                Material.ZOMBIE_SPAWN_EGG,
                EntityType.ZOMBIE,
                "Non Morto",
                "§2",
                Sound.ENTITY_ZOMBIE_AMBIENT),
        SKELETON(
                Material.SKELETON_SPAWN_EGG,
                EntityType.SKELETON,
                "Mario Ruggero",
                "§7",
                Sound.ENTITY_SKELETON_AMBIENT),
        CREEPER(
                Material.CREEPER_SPAWN_EGG,
                EntityType.CREEPER,
                "Terrorista",
                "§a",
                Sound.ENTITY_CREEPER_PRIMED),
        SPIDER(
                Material.SPIDER_SPAWN_EGG,
                EntityType.SPIDER,
                "Abominio",
                "§8",
                Sound.ENTITY_SPIDER_AMBIENT),
        ENDERMAN(
                Material.ENDERMAN_SPAWN_EGG,
                EntityType.ENDERMAN,
                "Ombra",
                "§5",
                Sound.ENTITY_ENDERMAN_SCREAM),
        WITCH(
                Material.WITCH_SPAWN_EGG,
                EntityType.WITCH,
                "Dynamike",
                "§d",
                Sound.ENTITY_WITCH_AMBIENT),
        VINDICATOR(
                Material.VINDICATOR_SPAWN_EGG,
                EntityType.VINDICATOR,
                "Maranza",
                "§c",
                Sound.ENTITY_VINDICATOR_AMBIENT);

        private final Material eggMaterial;
        private final EntityType entityType;
        private final String displayName;
        private final String color;
        private final Sound spawnSound;

        PillarMobType(
                Material eggMaterial,
                EntityType entityType,
                String displayName,
                String color,
                Sound spawnSound) {
            this.eggMaterial = eggMaterial;
            this.entityType = entityType;
            this.displayName = displayName;
            this.color = color;
            this.spawnSound = spawnSound;
        }
    }
}
