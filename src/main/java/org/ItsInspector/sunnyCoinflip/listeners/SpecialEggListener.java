package org.ItsInspector.sunnyCoinflip.listeners;

import org.ItsInspector.sunnyCoinflip.SunnyCoinflip;
import org.ItsInspector.sunnyCoinflip.utils.SpecialEgg;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.PointedDripstone;
import org.bukkit.entity.Egg;
import org.bukkit.entity.FallingBlock;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerEggThrowEvent;
import org.bukkit.persistence.PersistentDataType;

import java.util.concurrent.ThreadLocalRandom;

public final class SpecialEggListener implements Listener {
    private static final double SPAWN_HEIGHT = 10.0;
    private final SunnyCoinflip plugin;

    public SpecialEggListener(SunnyCoinflip plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onEggLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof Egg egg)) {
            return;
        }

        if (!SpecialEgg.isSpecial(egg.getItem())) {
            return;
        }

        // Copia il tag dall'ItemStack al proiettile, così resta riconoscibile all'impatto.
        egg.getPersistentDataContainer().set(
                SpecialEgg.key(),
                PersistentDataType.BYTE,
                (byte) 1
        );
    }

    @EventHandler
    public void onEggThrow(PlayerEggThrowEvent event) {
        Egg egg = event.getEgg();
        if (!isSpecialProjectile(egg)) {
            return;
        }

        // Questo uovo non può mai generare pulcini.
        event.setHatching(false);
        event.setNumHatches((byte) 0);
    }

    @EventHandler
    public void onEggHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Egg egg) || !isSpecialProjectile(egg)) {
            return;
        }

        Location impact = egg.getLocation().clone();
        Location spawnLocation = impact.clone().add(0.0, SPAWN_HEIGHT, 0.0);

        boolean spawnAnvil = ThreadLocalRandom.current().nextBoolean();
        BlockData data = spawnAnvil
                ? Material.ANVIL.createBlockData()
                : createPointedDripstone();

        FallingBlock fallingBlock = impact.getWorld().spawnFallingBlock(spawnLocation, data);
        fallingBlock.setDropItem(false);
        fallingBlock.setHurtEntities(true);

        // Evita che l'incudine si rompa troppo facilmente dopo la caduta.
        if (spawnAnvil) {
            fallingBlock.setMaxDamage(40);
            fallingBlock.setDamagePerBlock(2.0F);
        }
    }

    private boolean isSpecialProjectile(Egg egg) {
        Byte value = egg.getPersistentDataContainer()
                .get(SpecialEgg.key(), PersistentDataType.BYTE);
        return value != null && value == (byte) 1;
    }

    private BlockData createPointedDripstone() {
        PointedDripstone dripstone = (PointedDripstone) Material.POINTED_DRIPSTONE.createBlockData();
        dripstone.setVerticalDirection(org.bukkit.block.BlockFace.DOWN);
        dripstone.setThickness(PointedDripstone.Thickness.TIP);
        return dripstone;
    }
}
