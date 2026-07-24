package org.ItsInspector.sunnyCoinflip.listeners;

import org.ItsInspector.sunnyCoinflip.SunnyCoinflip;
import org.ItsInspector.sunnyCoinflip.managers.BedfightManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.projectiles.ProjectileSource;

/** Listener della modalità BedWars. */
public final class BedfightListener implements Listener {
    private final SunnyCoinflip plugin;

    public BedfightListener(SunnyCoinflip plugin) {
        this.plugin = plugin;
    }

    private BedfightManager manager() {
        return plugin.getBedfightManager();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onMove(PlayerMoveEvent event) {
        Location to = event.getTo();
        if (to == null) return;
        Player player = event.getPlayer();

        // Il movimento bloccato durante il countdown non deve consumare la protezione.
        if (!manager().canMoveDuringCountdown(player, event.getFrom(), to)) {
            Location locked = event.getFrom().clone();
            locked.setYaw(to.getYaw());
            locked.setPitch(to.getPitch());
            event.setTo(locked);
            return;
        }

        // FIX: la protezione viene rimossa subito quando cambiano X/Y/Z.
        manager().handleSpawnProtectionMovement(player, event.getFrom(), to);
        manager().handleVoidLevel(player);

        if (!manager().canLeaveArena(player, to)) {
            Location locked = event.getFrom().clone();
            locked.setYaw(to.getYaw());
            locked.setPitch(to.getPitch());
            event.setTo(locked);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        Player attacker = resolveAttacker(event.getDamager());
        if (attacker == null) {
            if (manager().isActiveParticipant(victim.getUniqueId())) event.setCancelled(true);
            return;
        }

        boolean attackerParticipant = manager().isActiveParticipant(attacker.getUniqueId());
        boolean victimParticipant = manager().isActiveParticipant(victim.getUniqueId());
        if (!attackerParticipant && !victimParticipant) return;
        if (!attackerParticipant || !victimParticipant || !manager().canDamage(attacker, victim)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFinalDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!manager().isActiveParticipant(player.getUniqueId())) return;
        if (!manager().canTakeDamage(player)) {
            event.setCancelled(true);
            return;
        }
        if (manager().handlePotentialElimination(player, event.getFinalDamage())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!manager().isActiveParticipant(event.getPlayer().getUniqueId())) return;
        if (!isAllowedBuildingMaterial(event.getBlockPlaced().getType())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§cIn questa modalità puoi piazzare soltanto i blocchi del kit.");
            return;
        }
        manager().handleBlockPlace(event.getPlayer(), event.getBlockPlaced());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!manager().isActiveParticipant(event.getPlayer().getUniqueId())) return;
        BedfightManager.BreakResult result = manager().handleBlockBreak(event.getPlayer(), event.getBlock());
        switch (result) {
            case ALLOW -> { }
            case ENEMY_BED -> event.setCancelled(true);
            case OWN_BED -> {
                event.setCancelled(true);
                event.getPlayer().sendMessage("§cNon puoi distruggere il tuo letto.");
            }
            case DENY -> {
                event.setCancelled(true);
                event.getPlayer().sendMessage("§cNon puoi rompere questo blocco.");
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onExplosion(EntityExplodeEvent event) {
        if (manager().isRoundWorld(event.getLocation().getWorld())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (manager().isActiveParticipant(event.getPlayer().getUniqueId())
                && manager().isUndroppableKitItem(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFood(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player && manager().isActiveParticipant(player.getUniqueId())) {
            event.setCancelled(true);
            player.setFoodLevel(20);
            player.setSaturation(20.0f);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();

        // Legge il messaggio finale, dopo gli altri plugin, e impedisce il broadcast globale.
        if (manager().isArenaWorld(player.getWorld())) {
            String deathMessage = event.getDeathMessage();
            event.setDeathMessage(null);
            if (deathMessage != null && !deathMessage.isBlank()) {
                for (Player recipient : player.getWorld().getPlayers()) {
                    recipient.sendMessage(deathMessage);
                }
            }
        }

        manager().handleDeath(player);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Location location = manager().getRespawnLocation(event.getPlayer().getUniqueId());
        if (location != null) event.setRespawnLocation(location);
        manager().handleRespawn(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        manager().handleQuit(event.getPlayer());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        manager().handleJoin(event.getPlayer());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        manager().handleChangedWorld(event.getPlayer());
    }

    private Player resolveAttacker(Entity entity) {
        if (entity instanceof Player player) return player;
        if (entity instanceof org.bukkit.entity.Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof Player player) return player;
        }
        return null;
    }

    private boolean isAllowedBuildingMaterial(Material material) {
        String name = material.name();
        return material == Material.END_STONE || name.endsWith("_WOOL") || name.endsWith("_PLANKS")
                || name.endsWith("_LOG") || name.endsWith("_WOOD");
    }
}
