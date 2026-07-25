package org.ItsInspector.sunnyCoinflip.listeners;

import java.util.concurrent.ThreadLocalRandom;
import org.ItsInspector.sunnyCoinflip.SunnyCoinflip;
import org.ItsInspector.sunnyCoinflip.utils.SpecialEgg;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.PointedDripstone;
import org.bukkit.block.data.type.PointedDripstone.Thickness;
import org.bukkit.entity.Egg;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerEggThrowEvent;
import org.bukkit.persistence.PersistentDataType;

public final class SpecialEggListener implements Listener {
    private static final double SPAWN_HEIGHT = (double)10.0F;
    private final SunnyCoinflip plugin;

    public SpecialEggListener(SunnyCoinflip plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onEggLaunch(ProjectileLaunchEvent event) {
        Projectile var3 = event.getEntity();
        if (var3 instanceof Egg egg) {
            if (SpecialEgg.isSpecial(egg.getItem())) {
                egg.getPersistentDataContainer().set(SpecialEgg.key(), PersistentDataType.BYTE, (byte)1);
            }
        }
    }

    @EventHandler
    public void onEggThrow(PlayerEggThrowEvent event) {
        Egg egg = event.getEgg();
        if (this.isSpecialProjectile(egg)) {
            event.setHatching(false);
            event.setNumHatches((byte)0);
        }
    }

    @EventHandler
    public void onEggHit(ProjectileHitEvent event) {
        Projectile var3 = event.getEntity();
        if (var3 instanceof Egg egg) {
            if (this.isSpecialProjectile(egg)) {
                Location impact = egg.getLocation().clone();
                Location spawnLocation = impact.clone().add((double)0.0F, (double)10.0F, (double)0.0F);
                boolean spawnAnvil = ThreadLocalRandom.current().nextBoolean();
                BlockData data = spawnAnvil ? Material.ANVIL.createBlockData() : this.createPointedDripstone();
                FallingBlock fallingBlock = impact.getWorld().spawnFallingBlock(spawnLocation, data);
                fallingBlock.setDropItem(false);
                fallingBlock.setHurtEntities(true);
                if (spawnAnvil) {
                    fallingBlock.setMaxDamage(40);
                    fallingBlock.setDamagePerBlock(2.0F);
                }

                return;
            }
        }

    }

    private boolean isSpecialProjectile(Egg egg) {
        Byte value = (Byte)egg.getPersistentDataContainer().get(SpecialEgg.key(), PersistentDataType.BYTE);
        return value != null && value == 1;
    }

    private BlockData createPointedDripstone() {
        PointedDripstone dripstone = (PointedDripstone)Material.POINTED_DRIPSTONE.createBlockData();
        dripstone.setVerticalDirection(BlockFace.DOWN);
        dripstone.setThickness(Thickness.TIP);
        return dripstone;
    }
}
