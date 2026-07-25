package org.ItsInspector.sunnyCoinflip.listeners;

import java.util.Locale;
import java.util.Set;
import org.ItsInspector.sunnyCoinflip.SunnyCoinflip;
import org.ItsInspector.sunnyCoinflip.models.PillarMatch;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

public final class PillarItemSafetyListener implements Listener {
    private static final Set<String> FORBIDDEN_EXACT = Set.of("GOLDEN_HOE", "COMMAND_BLOCK", "CHAIN_COMMAND_BLOCK", "REPEATING_COMMAND_BLOCK", "COMMAND_BLOCK_MINECART", "TEST_BLOCK", "TEST_INSTANCE_BLOCK", "JIGSAW", "STRUCTURE_BLOCK", "STRUCTURE_VOID", "DEBUG_STICK", "BARRIER", "LIGHT");
    private final SunnyCoinflip plugin;

    public PillarItemSafetyListener(SunnyCoinflip plugin) {
        this.plugin = plugin;
    }

    @EventHandler(
            priority = EventPriority.HIGHEST,
            ignoreCancelled = true
    )
    public void onFireballUse(PlayerInteractEvent event) {
        if (event.getHand() == EquipmentSlot.HAND) {
            if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
                Player player = event.getPlayer();
                if (this.isPlayingPillarsParticipant(player)) {
                    ItemStack item = event.getItem();
                    if (item != null) {
                        if (this.isForbidden(item.getType())) {
                            event.setCancelled(true);
                            this.removeFromMainHand(player);
                            player.sendMessage("§cQuesto oggetto è disabilitato nei Pillars.");
                            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8F, 1.1F);
                        } else if (item.getType() == Material.FIRE_CHARGE) {
                            event.setCancelled(true);
                            this.consumeOne(player, item);
                            Fireball fireball = (Fireball)player.launchProjectile(Fireball.class);
                            Vector velocity = player.getEyeLocation().getDirection().normalize().multiply(1.35);
                            fireball.setVelocity(velocity);
                            fireball.setYield(1.6F);
                            fireball.setIsIncendiary(false);
                            fireball.setShooter(player);
                            player.setCooldown(Material.FIRE_CHARGE, 12);
                            player.playSound(player.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1.0F, 0.8F);
                        }
                    }
                }
            }
        }
    }

    @EventHandler(
            priority = EventPriority.HIGHEST,
            ignoreCancelled = true
    )
    public void onForbiddenPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (this.isPillarsParticipant(player)) {
            if (this.isForbidden(event.getItemInHand().getType())) {
                event.setCancelled(true);
                this.removeFromMainHand(player);
                player.sendMessage("§cQuesto blocco è disabilitato nei Pillars.");
            }
        }
    }

    @EventHandler(
            priority = EventPriority.HIGHEST,
            ignoreCancelled = true
    )
    public void onForbiddenSelect(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        if (this.isPillarsParticipant(player)) {
            ItemStack selected = player.getInventory().getItem(event.getNewSlot());
            if (selected != null && this.isForbidden(selected.getType())) {
                player.getInventory().setItem(event.getNewSlot(), (ItemStack)null);
                player.sendMessage("§cHai ricevuto un oggetto disabilitato: è stato rimosso.");
                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 0.8F, 0.9F);
            }
        }
    }

    @EventHandler(
            priority = EventPriority.HIGHEST,
            ignoreCancelled = true
    )
    public void onForbiddenInventoryClick(InventoryClickEvent event) {
        HumanEntity var3 = event.getWhoClicked();
        if (var3 instanceof Player player) {
            if (this.isPillarsParticipant(player)) {
                ItemStack current = event.getCurrentItem();
                ItemStack cursor = event.getCursor();
                if (current != null && this.isForbidden(current.getType()) || cursor != null && this.isForbidden(cursor.getType())) {
                    event.setCancelled(true);
                    if (current != null && this.isForbidden(current.getType())) {
                        event.setCurrentItem((ItemStack)null);
                    }

                    if (cursor != null && this.isForbidden(cursor.getType())) {
                        event.setCursor((ItemStack)null);
                    }

                    player.sendMessage("§cQuesto oggetto è disabilitato nei Pillars.");
                }

            }
        }
    }

    @EventHandler(
            priority = EventPriority.HIGHEST,
            ignoreCancelled = true
    )
    public void onForbiddenPickup(EntityPickupItemEvent event) {
        LivingEntity var3 = event.getEntity();
        if (var3 instanceof Player player) {
            if (this.isPillarsParticipant(player)) {
                if (this.isForbidden(event.getItem().getItemStack().getType())) {
                    event.setCancelled(true);
                    event.getItem().remove();
                }
            }
        }
    }

    private boolean isPlayingPillarsParticipant(Player player) {
        PillarMatch match = this.plugin.getGameManager().getActivePillarMatch();
        return match != null && match.isPlaying() && this.isParticipant(match, player);
    }

    private boolean isPillarsParticipant(Player player) {
        PillarMatch match = this.plugin.getGameManager().getActivePillarMatch();
        return match != null && this.isParticipant(match, player);
    }

    private boolean isParticipant(PillarMatch match, Player player) {
        return player.getUniqueId().equals(match.getCreator()) || match.getOpponent() != null && player.getUniqueId().equals(match.getOpponent());
    }

    private boolean isForbidden(Material material) {
        String name = material.name().toUpperCase(Locale.ROOT);
        return FORBIDDEN_EXACT.contains(name) || name.contains("COMMAND_BLOCK") || name.startsWith("TEST_");
    }

    private void consumeOne(Player player, ItemStack stack) {
        if (player.getGameMode() != GameMode.CREATIVE) {
            if (stack.getAmount() <= 1) {
                player.getInventory().setItemInMainHand((ItemStack)null);
            } else {
                stack.setAmount(stack.getAmount() - 1);
            }

        }
    }

    private void removeFromMainHand(Player player) {
        ItemStack held = player.getInventory().getItemInMainHand();
        if (!held.getType().isAir() && this.isForbidden(held.getType())) {
            player.getInventory().setItemInMainHand((ItemStack)null);
        }

    }
}
