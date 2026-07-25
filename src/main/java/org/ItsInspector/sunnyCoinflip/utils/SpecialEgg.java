package org.ItsInspector.sunnyCoinflip.utils;

import java.util.List;
import org.ItsInspector.sunnyCoinflip.SunnyCoinflip;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public final class SpecialEgg {
    private static final String KEY_NAME = "special_falling_egg";

    private SpecialEgg() {
    }

    public static NamespacedKey key() {
        return new NamespacedKey(SunnyCoinflip.getInstance(), "special_falling_egg");
    }

    public static ItemStack create() {
        ItemStack egg = new ItemStack(Material.EGG);
        ItemMeta meta = egg.getItemMeta();
        meta.setDisplayName("§d§lUovo Incantato");
        meta.setLore(List.of("§7Lancialo per evocare dall'alto", "§7un'incudine o una stalattite."));
        meta.getPersistentDataContainer().set(key(), PersistentDataType.BYTE, (byte)1);
        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(new ItemFlag[]{ItemFlag.HIDE_ENCHANTS});
        egg.setItemMeta(meta);
        return egg;
    }

    public static boolean isSpecial(ItemStack item) {
        if (item != null && item.getType() == Material.EGG && item.hasItemMeta()) {
            Byte value = (Byte)item.getItemMeta().getPersistentDataContainer().get(key(), PersistentDataType.BYTE);
            return value != null && value == 1;
        } else {
            return false;
        }
    }
}
