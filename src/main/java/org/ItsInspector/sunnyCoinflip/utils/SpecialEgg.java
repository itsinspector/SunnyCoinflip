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
    private SpecialEgg() {}

    public static NamespacedKey key() {
        return new NamespacedKey(SunnyCoinflip.getInstance(), "special_falling_egg");
    }

    public static ItemStack create() {
        ItemStack egg = new ItemStack(Material.EGG);
        ItemMeta meta = egg.getItemMeta();
        if (meta == null) return egg;
        meta.setDisplayName("§d§lUovo Incantato");
        meta.setLore(List.of("§7Lancialo per evocare dall'alto", "§7un'incudine o una stalattite."));
        meta.getPersistentDataContainer().set(key(), PersistentDataType.BYTE, (byte) 1);
        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        egg.setItemMeta(meta);
        return egg;
    }

    public static boolean isSpecial(ItemStack item) {
        if (item == null || item.getType() != Material.EGG || !item.hasItemMeta()) return false;
        Byte value = item.getItemMeta().getPersistentDataContainer().get(key(), PersistentDataType.BYTE);
        return value != null && value == (byte) 1;
    }
}
