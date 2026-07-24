package org.ItsInspector.sunnyCoinflip.listeners;

import org.ItsInspector.sunnyCoinflip.SunnyCoinflip;
import org.ItsInspector.sunnyCoinflip.managers.BedfightManager;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.projectiles.ProjectileSource;

import java.util.Locale;

/** Event bridge for the internal BedWars arena. */
public final class BedfightListener implements Listener {

    private final SunnyCoinflip plugin;

    public BedfightListener(SunnyCoinflip plugin) {
        this.plugin = plugin;
    }

    private BedfightManager manager() {
        return plugin.getBedfightManager();
    }


    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChatAmount(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (!manager().isAwaitingCreateAmount(player.getUniqueId())) {
            return;
        }
        event.setCancelled(true);
        String message = event.getMessage().trim();
        org.bukkit.Bukkit.getScheduler().runTask(plugin,
                () -> manager().handleCreateAmountChat(player, message));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        boolean allowed = manager().handleBlockPlace(
                event.getPlayer(),
                event.getBlockPlaced(),
                event.getBlockReplacedState().getBlockData()
        );
        if (!allowed) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        BedfightManager.BreakResult result = manager().handleBlockBreak(event.getPlayer(), event.getBlock());
        if (result == BedfightManager.BreakResult.DENY) {
            event.setCancelled(true);
            return;
        }
        if (result == BedfightManager.BreakResult.BED) {
            event.setDropItems(false);
            event.setExpToDrop(0);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (manager().isRoundWorld(event.getLocation().getWorld())) {
            event.blockList().clear();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (manager().isRoundWorld(event.getBlock().getWorld())) {
            event.blockList().clear();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        manager().handleVoidLevel(event.getPlayer(), event.getTo());
        if (!manager().canMoveDuringCountdown(event.getPlayer(), event.getFrom(), event.getTo())) {
            Location from = event.getFrom();
            Location to = event.getTo();
            if (to != null) {
                from.setYaw(to.getYaw());
                from.setPitch(to.getPitch());
            }
            event.setTo(from);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (!manager().canLeaveArena(event.getPlayer(), event.getTo())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§cNon puoi lasciare l'arena durante il BedWars.");
        }
    }

    @EventHandler
    public void onChangedWorld(PlayerChangedWorldEvent event) {
        manager().handleChangedWorld(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (player.getWorld().getName().equalsIgnoreCase("bedfight")) {
            event.setDeathMessage(buildBedfightDeathMessage(player));
        }
        if (!manager().isActiveParticipant(player.getUniqueId())) {
            return;
        }
        event.getDrops().clear();
        event.setDroppedExp(0);
        event.setKeepInventory(false);
        event.setKeepLevel(false);
        manager().handleDeath(player);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent event) {
        Location location = manager().getRespawnLocation(event.getPlayer().getUniqueId());
        if (location != null) {
            event.setRespawnLocation(location);
        }
        manager().handleRespawn(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (manager().isActiveParticipant(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§cNon puoi buttare gli oggetti del kit.");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFood(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player
                && manager().isActiveParticipant(player.getUniqueId())) {
            event.setCancelled(true);
            player.setFoodLevel(20);
            player.setSaturation(20.0f);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (!manager().canTakeDamage(player)) {
            event.setCancelled(true);
            return;
        }
        if (manager().handlePotentialElimination(player, event.getFinalDamage(), event.getCause())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onRecordDamager(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player victim) {
            Player attacker = resolvePlayer(event.getDamager());
            manager().recordLastDamager(victim, attacker);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        Player victim = event.getEntity() instanceof Player player ? player : null;
        Player attacker = resolvePlayer(event.getDamager());
        if ((victim != null || attacker != null) && !manager().canDamage(attacker, victim)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockedCoinflipCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (!manager().isActiveParticipant(player.getUniqueId())) {
            return;
        }

        String message = event.getMessage().trim().toLowerCase(Locale.ROOT);
        boolean conflicts = message.startsWith("/pillars")
                || message.startsWith("/cf classici")
                || message.startsWith("/coinflip classici")
                || message.startsWith("/cf pillars")
                || message.startsWith("/coinflip pillars");
        if (conflicts) {
            event.setCancelled(true);
            player.sendMessage("§cNon puoi avviare altri coinflip durante un BedWars.");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCoinflipInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)
                || !manager().isActiveParticipant(player.getUniqueId())) {
            return;
        }
        String title = ChatColor.stripColor(event.getView().getTitle());
        if (title == null) {
            return;
        }
        String normalized = title.toLowerCase(Locale.ROOT);
        if (normalized.contains("coinflip") || normalized.equals("scegli un kit")) {
            event.setCancelled(true);
            player.closeInventory();
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        manager().handleQuit(event.getPlayer());
    }

    @EventHandler
    public void onKick(PlayerKickEvent event) {
        manager().handleQuit(event.getPlayer());
    }

    private String buildBedfightDeathMessage(Player player) {
        Player killer = player.getKiller();
        if (killer != null) {
            return "§c☠ §f" + player.getName() + " §7è stato eliminato da §f" + killer.getName() + "§7.";
        }
        EntityDamageEvent lastDamage = player.getLastDamageCause();
        if (lastDamage == null) {
            return "§c☠ §f" + player.getName() + " §7è stato eliminato.";
        }
        return switch (lastDamage.getCause()) {
            case VOID -> "§c☠ §f" + player.getName() + " §7è caduto nel vuoto.";
            case FALL -> "§c☠ §f" + player.getName() + " §7si è schiantato.";
            case FIRE, FIRE_TICK, LAVA, HOT_FLOOR -> "§c☠ §f" + player.getName() + " §7è finito arrosto.";
            case PROJECTILE -> "§c☠ §f" + player.getName() + " §7è stato colpito a distanza.";
            case ENTITY_EXPLOSION, BLOCK_EXPLOSION -> "§c☠ §f" + player.getName() + " §7è esploso.";
            case DROWNING -> "§c☠ §f" + player.getName() + " §7non sapeva nuotare.";
            case SUFFOCATION -> "§c☠ §f" + player.getName() + " §7è rimasto incastrato nei blocchi.";
            default -> "§c☠ §f" + player.getName() + " §7è stato eliminato.";
        };
    }

    private Player resolvePlayer(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof Player player) {
                return player;
            }
        }
        return null;
    }
}
