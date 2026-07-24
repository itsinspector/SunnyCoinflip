package org.ItsInspector.sunnyCoinflip.listeners;

import org.ItsInspector.sunnyCoinflip.SunnyCoinflip;
import org.ItsInspector.sunnyCoinflip.utils.SpecialEgg;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Egg;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

/** Comportamento dell'uovo speciale usato nella modalità Pillars. */
public final class SpecialEggListener implements Listener {
    private final SunnyCoinflip plugin;
    private final NamespacedKey projectileKey;

    public SpecialEggListener(SunnyCoinflip plugin) {
        this.plugin = plugin;
        this.projectileKey = new NamespacedKey(plugin, "special_egg_projectile");
    }

    @EventHandler
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack item = event.getItem();
        if (!SpecialEgg.isSpecial(item)) return;
        event.setCancelled(true);

        Player player = event.getPlayer();
        Egg egg = player.launchProjectile(Egg.class);
        egg.getPersistentDataContainer().set(projectileKey, PersistentDataType.BYTE, (byte) 1);
        if (player.getGameMode() != org.bukkit.GameMode.CREATIVE && item != null) {
            item.setAmount(item.getAmount() - 1);
        }
    }

    @EventHandler
    public void onHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Egg egg)) return;
        Byte marked = egg.getPersistentDataContainer().get(projectileKey, PersistentDataType.BYTE);
        if (marked == null || marked != (byte) 1) return;
        Location hit = egg.getLocation().clone().add(0, 14, 0);
        Material material = Math.random() < 0.5 ? Material.ANVIL : Material.POINTED_DRIPSTONE;
        FallingBlock falling = hit.getWorld().spawnFallingBlock(hit, material.createBlockData());
        falling.setDropItem(false);
        falling.setHurtEntities(true);
        falling.setVelocity(new Vector(0, -0.35, 0));
    }
}
