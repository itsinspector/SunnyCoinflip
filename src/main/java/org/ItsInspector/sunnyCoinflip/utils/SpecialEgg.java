package org.ItsInspector.sunnyCoinflip.utils;

import org.ItsInspector.sunnyCoinflip.SunnyCoinflip;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

public final class SpecialEgg {
    private static final String KEY_NAME = "special_falling_egg";

    private SpecialEgg() {
    }

    public static NamespacedKey key() {
        return new NamespacedKey(SunnyCoinflip.getInstance(), KEY_NAME);
    }

    public static ItemStack create() {
        ItemStack egg = new ItemStack(Material.EGG);
        ItemMeta meta = egg.getItemMeta();

        meta.setDisplayName("§d§lUovo Incantato");
        meta.setLore(List.of(
                "§7Lancialo per evocare dall'alto",
                "§7un'incudine o una stalattite."
        ));
        meta.getPersistentDataContainer().set(key(), PersistentDataType.BYTE, (byte) 1);

        // Effetto luccicante senza mostrare un incantesimo inutile nella descrizione.
        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);

        egg.setItemMeta(meta);
        return egg;
    }

    public static boolean isSpecial(ItemStack item) {
        if (item == null || item.getType() != Material.EGG || !item.hasItemMeta()) {
            return false;
        }

        Byte value = item.getItemMeta()
                .getPersistentDataContainer()
                .get(key(), PersistentDataType.BYTE);
        return value != null && value == (byte) 1;
    }
}
