package org.ItsInspector.sunnyCoinflip.listeners;

import org.ItsInspector.sunnyCoinflip.SunnyCoinflip;
import org.ItsInspector.sunnyCoinflip.models.PillarMatch;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Fireball;
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

import java.util.Locale;
import java.util.Set;

/**
 * Pillars-only restrictions for technical items and throwable fire charges.
 */
public final class PillarItemSafetyListener implements Listener {

    private static final Set<String> FORBIDDEN_EXACT = Set.of(
            "GOLDEN_HOE",
            "COMMAND_BLOCK",
            "CHAIN_COMMAND_BLOCK",
            "REPEATING_COMMAND_BLOCK",
            "COMMAND_BLOCK_MINECART",
            "TEST_BLOCK",
            "TEST_INSTANCE_BLOCK",
            "JIGSAW",
            "STRUCTURE_BLOCK",
            "STRUCTURE_VOID",
            "DEBUG_STICK",
            "BARRIER",
            "LIGHT"
    );

    private final SunnyCoinflip plugin;

    public PillarItemSafetyListener(SunnyCoinflip plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFireballUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        if (!isPlayingPillarsParticipant(player)) return;

        ItemStack item = event.getItem();
        if (item == null) return;

        if (isForbidden(item.getType())) {
            event.setCancelled(true);
            removeFromMainHand(player);
            player.sendMessage("§cQuesto oggetto è disabilitato nei Pillars.");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1.1f);
            return;
        }

        if (item.getType() != Material.FIRE_CHARGE) return;

        event.setCancelled(true);
        consumeOne(player, item);

        Fireball fireball = player.launchProjectile(Fireball.class);
        Vector velocity = player.getEyeLocation().getDirection().normalize().multiply(1.35);
        fireball.setVelocity(velocity);
        fireball.setYield(1.6f);
        fireball.setIsIncendiary(false);
        fireball.setShooter(player);

        player.setCooldown(Material.FIRE_CHARGE, 12);
        player.playSound(player.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1.0f, 0.8f);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onForbiddenPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (!isPillarsParticipant(player)) return;
        if (!isForbidden(event.getItemInHand().getType())) return;

        event.setCancelled(true);
        removeFromMainHand(player);
        player.sendMessage("§cQuesto blocco è disabilitato nei Pillars.");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onForbiddenSelect(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        if (!isPillarsParticipant(player)) return;

        ItemStack selected = player.getInventory().getItem(event.getNewSlot());
        if (selected == null || !isForbidden(selected.getType())) return;

        player.getInventory().setItem(event.getNewSlot(), null);
        player.sendMessage("§cHai ricevuto un oggetto disabilitato: è stato rimosso.");
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 0.8f, 0.9f);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onForbiddenInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!isPillarsParticipant(player)) return;

        ItemStack current = event.getCurrentItem();
        ItemStack cursor = event.getCursor();
        if ((current != null && isForbidden(current.getType()))
                || (cursor != null && isForbidden(cursor.getType()))) {
            event.setCancelled(true);
            if (current != null && isForbidden(current.getType())) {
                event.setCurrentItem(null);
            }
            if (cursor != null && isForbidden(cursor.getType())) {
                event.setCursor(null);
            }
            player.sendMessage("§cQuesto oggetto è disabilitato nei Pillars.");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onForbiddenPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!isPillarsParticipant(player)) return;
        if (!isForbidden(event.getItem().getItemStack().getType())) return;

        event.setCancelled(true);
        event.getItem().remove();
    }

    private boolean isPlayingPillarsParticipant(Player player) {
        PillarMatch match = plugin.getGameManager().getActivePillarMatch();
        return match != null && match.isPlaying() && isParticipant(match, player);
    }

    private boolean isPillarsParticipant(Player player) {
        PillarMatch match = plugin.getGameManager().getActivePillarMatch();
        return match != null && isParticipant(match, player);
    }

    private boolean isParticipant(PillarMatch match, Player player) {
        return player.getUniqueId().equals(match.getCreator())
                || (match.getOpponent() != null && player.getUniqueId().equals(match.getOpponent()));
    }

    private boolean isForbidden(Material material) {
        String name = material.name().toUpperCase(Locale.ROOT);
        return FORBIDDEN_EXACT.contains(name)
                || name.contains("COMMAND_BLOCK")
                || name.startsWith("TEST_");
    }

    private void consumeOne(Player player, ItemStack stack) {
        if (player.getGameMode() == GameMode.CREATIVE) return;
        if (stack.getAmount() <= 1) {
            player.getInventory().setItemInMainHand(null);
        } else {
            stack.setAmount(stack.getAmount() - 1);
        }
    }

    private void removeFromMainHand(Player player) {
        ItemStack held = player.getInventory().getItemInMainHand();
        if (!held.getType().isAir() && isForbidden(held.getType())) {
            player.getInventory().setItemInMainHand(null);
        }
    }
}
