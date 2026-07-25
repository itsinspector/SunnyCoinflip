package org.ItsInspector.sunnyCoinflip.listeners;

import java.util.Locale;
import org.ItsInspector.sunnyCoinflip.SunnyCoinflip;
import org.ItsInspector.sunnyCoinflip.managers.BedfightManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.HumanEntity;
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
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.projectiles.ProjectileSource;

public final class BedfightListener implements Listener {
    private final SunnyCoinflip plugin;

    public BedfightListener(SunnyCoinflip plugin) {
        this.plugin = plugin;
    }

    private BedfightManager manager() {
        return this.plugin.getBedfightManager();
    }

    @EventHandler(
            priority = EventPriority.HIGHEST
    )
    public void onChatAmount(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (this.manager().isAwaitingCreateAmount(player.getUniqueId())) {
            event.setCancelled(true);
            String message = event.getMessage().trim();
            Bukkit.getScheduler().runTask(this.plugin, () -> this.manager().handleCreateAmountChat(player, message));
        }
    }

    @EventHandler(
            priority = EventPriority.HIGHEST,
            ignoreCancelled = true
    )
    public void onBlockPlace(BlockPlaceEvent event) {
        boolean allowed = this.manager().handleBlockPlace(
                event.getPlayer(),
                event.getBlockPlaced(),
                event.getBlockReplacedState().getBlockData());
        if (!allowed) {
            event.setCancelled(true);
        }
    }

    @EventHandler(
            priority = EventPriority.HIGHEST,
            ignoreCancelled = true
    )
    public void onBlockBreak(BlockBreakEvent event) {
        BedfightManager.BreakResult result = this.manager().handleBlockBreak(event.getPlayer(), event.getBlock());
        if (result == BedfightManager.BreakResult.DENY
                || result == BedfightManager.BreakResult.OWN_BED) {
            event.setCancelled(true);
        } else if (result == BedfightManager.BreakResult.ENEMY_BED) {
            event.setDropItems(false);
            event.setExpToDrop(0);
        } else if (result == BedfightManager.BreakResult.ALLOW
                && this.manager().isBlockProtectionActive(event.getBlock().getWorld())) {
            Material brokenType = event.getBlock().getType();
            event.setDropItems(false);
            event.setExpToDrop(0);
            event.getBlock().getWorld().dropItemNaturally(
                    event.getBlock().getLocation().add(0.5, 0.25, 0.5),
                    new ItemStack(brokenType, 1));
        }
    }

    @EventHandler(
            priority = EventPriority.HIGHEST,
            ignoreCancelled = true
    )
    public void onEntityExplode(EntityExplodeEvent event) {
        if (this.manager().isBlockProtectionActive(event.getLocation().getWorld())) {
            event.blockList().clear();
        }

    }

    @EventHandler(
            priority = EventPriority.HIGHEST,
            ignoreCancelled = true
    )
    public void onBlockExplode(BlockExplodeEvent event) {
        if (this.manager().isBlockProtectionActive(event.getBlock().getWorld())) {
            event.blockList().clear();
        }

    }

    @EventHandler(
            priority = EventPriority.HIGHEST,
            ignoreCancelled = true
    )
    public void onMove(PlayerMoveEvent event) {
        this.manager().handleVoidLevel(event.getPlayer(), event.getTo());
        if (!this.manager().canMoveDuringCountdown(event.getPlayer(), event.getFrom(), event.getTo())) {
            Location from = event.getFrom();
            Location to = event.getTo();
            if (to != null) {
                from.setYaw(to.getYaw());
                from.setPitch(to.getPitch());
            }

            event.setTo(from);
        }
    }

    @EventHandler
    public void onChangedWorld(PlayerChangedWorldEvent event) {
        this.manager().handleChangedWorld(event.getPlayer());
    }

    @EventHandler(
            priority = EventPriority.HIGHEST
    )
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (this.manager().isActiveParticipant(player.getUniqueId())) {
            event.setDeathMessage(null);
            event.getDrops().clear();
            event.setDroppedExp(0);
            event.setKeepInventory(false);
            event.setKeepLevel(false);
            this.manager().handleDeath(player);
        } else if (player.getWorld().getName().equalsIgnoreCase("bedfight")) {
            event.setDeathMessage(this.buildBedfightDeathMessage(player));
        }
    }

    @EventHandler(
            priority = EventPriority.HIGHEST
    )
    public void onRespawn(PlayerRespawnEvent event) {
        Location location = this.manager().getRespawnLocation(event.getPlayer().getUniqueId());
        if (location != null) {
            event.setRespawnLocation(location);
        }

        this.manager().handleRespawn(event.getPlayer());
    }

    @EventHandler(
            priority = EventPriority.HIGHEST,
            ignoreCancelled = true
    )
    public void onDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        if (this.manager().isActiveParticipant(player.getUniqueId())) {
            if (this.manager().isUndroppableKitItem(event.getItemDrop().getItemStack())) {
                event.setCancelled(true);
            }

        }
    }

    @EventHandler(
            priority = EventPriority.HIGHEST,
            ignoreCancelled = true
    )
    public void onKitDamage(PlayerItemDamageEvent event) {
        if (this.manager().isActiveParticipant(event.getPlayer().getUniqueId())
                && this.manager().isUndroppableKitItem(event.getItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(
            priority = EventPriority.MONITOR
    )
    public void onJoin(PlayerJoinEvent event) {
        this.manager().handleJoin(event.getPlayer());
    }

    @EventHandler(
            priority = EventPriority.HIGHEST,
            ignoreCancelled = true
    )
    public void onFood(FoodLevelChangeEvent event) {
        HumanEntity var3 = event.getEntity();
        if (var3 instanceof Player player) {
            if (this.manager().isActiveParticipant(player.getUniqueId())) {
                event.setCancelled(true);
                player.setFoodLevel(20);
                player.setSaturation(0.0F);
            }
        }

    }

    @EventHandler(
            priority = EventPriority.HIGHEST,
            ignoreCancelled = true
    )
    public void onDamage(EntityDamageEvent event) {
        Entity var3 = event.getEntity();
        if (var3 instanceof Player player) {
            if (!this.manager().canTakeDamage(player)) {
                event.setCancelled(true);
            } else {
                if (this.manager().handlePotentialElimination(
                        player,
                        event.getFinalDamage(),
                        event.getCause())) {
                    event.setCancelled(true);
                }

            }
        }
    }

    @EventHandler(
            priority = EventPriority.LOWEST,
            ignoreCancelled = true
    )
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        Entity var4 = event.getEntity();
        Player var10000;
        if (var4 instanceof Player player) {
            var10000 = player;
        } else {
            var10000 = null;
        }

        Player victim = var10000;
        Player attacker = this.resolvePlayer(event.getDamager());
        if ((victim != null || attacker != null) && !this.manager().canDamage(attacker, victim)) {
            event.setCancelled(true);
        }

    }

    @EventHandler(
            priority = EventPriority.HIGHEST,
            ignoreCancelled = true
    )
    public void onBlockedCoinflipCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (this.manager().isActiveParticipant(player.getUniqueId())) {
            String message = event.getMessage().trim().toLowerCase(Locale.ROOT);
            boolean conflicts = message.startsWith("/pillars") || message.startsWith("/cf classici") || message.startsWith("/coinflip classici") || message.startsWith("/cf pillars") || message.startsWith("/coinflip pillars");
            if (conflicts) {
                event.setCancelled(true);
                player.sendMessage("§cNon puoi avviare altre modalità durante una partita BedFight.");
            }

        }
    }

    @EventHandler(
            priority = EventPriority.HIGHEST,
            ignoreCancelled = true
    )
    public void onCoinflipInventoryClick(InventoryClickEvent event) {
        HumanEntity var3 = event.getWhoClicked();
        if (var3 instanceof Player player) {
            if (this.manager().isActiveParticipant(player.getUniqueId())) {
                String title = ChatColor.stripColor(event.getView().getTitle());
                if (title == null) {
                    return;
                }

                String normalized = title.toLowerCase(Locale.ROOT);
                if (normalized.contains("coinflip") || normalized.equals("scegli un kit")) {
                    event.setCancelled(true);
                    player.closeInventory();
                }

                return;
            }
        }

    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        this.manager().handleQuit(event.getPlayer());
    }

    @EventHandler
    public void onKick(PlayerKickEvent event) {
        this.manager().handleQuit(event.getPlayer());
    }

    private String buildBedfightDeathMessage(Player player) {
        Player killer = player.getKiller();
        if (killer != null) {
            String var4 = player.getName();
            return "§c☠ §f" + var4 + " §7è stato eliminato da §f" + killer.getName() + "§7.";
        } else {
            EntityDamageEvent lastDamage = player.getLastDamageCause();
            if (lastDamage == null) {
                return "§c☠ §f" + player.getName() + " §7è stato eliminato.";
            } else {
                String var10000;
                switch (lastDamage.getCause()) {
                    case VOID:
                        var10000 = "§c☠ §f" + player.getName() + " §7è caduto nel vuoto.";
                        break;
                    case FALL:
                        var10000 = "§c☠ §f" + player.getName() + " §7si è schiantato.";
                        break;
                    case FIRE:
                    case FIRE_TICK:
                    case LAVA:
                    case HOT_FLOOR:
                        var10000 = "§c☠ §f" + player.getName() + " §7è morto tra le fiamme.";
                        break;
                    case PROJECTILE:
                        var10000 = "§c☠ §f" + player.getName() + " §7è stato colpito a distanza.";
                        break;
                    case ENTITY_EXPLOSION:
                    case BLOCK_EXPLOSION:
                        var10000 = "§c☠ §f" + player.getName() + " §7è esploso.";
                        break;
                    case DROWNING:
                        var10000 = "§c☠ §f" + player.getName() + " §7è annegato.";
                        break;
                    case SUFFOCATION:
                        var10000 = "§c☠ §f" + player.getName() + " §7è soffocato.";
                        break;
                    default:
                        var10000 = "§c☠ §f" + player.getName() + " §7è stato eliminato.";
                }

                return var10000;
            }
        }
    }

    private Player resolvePlayer(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        } else {
            if (damager instanceof Projectile projectile) {
                ProjectileSource shooter = projectile.getShooter();
                if (shooter instanceof Player player) {
                    return player;
                }
            }

            return null;
        }
    }
}
