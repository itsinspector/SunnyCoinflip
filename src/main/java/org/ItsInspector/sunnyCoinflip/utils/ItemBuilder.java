package org.ItsInspector.sunnyCoinflip.utils;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class ItemBuilder {
    public static ItemStack createItem(Material material, String name, String... lore) {
        return createItem(material, name, false, lore);
    }

    public static String translate(String text) {
        if (text == null) return null;
        text = ChatColor.translateAlternateColorCodes('&', text);
        if (Bukkit.getPluginManager().isPluginEnabled("ItemsAdder")) {
            try {
                Class<?> wrapperClass = Class.forName("dev.lone.itemsadder.api.FontImageWrapper");
                java.lang.reflect.Method method = wrapperClass.getMethod("replaceFontImages", String.class);
                return (String) method.invoke(null, text);
            } catch (Exception ignored) {}
        }
        return text;
    }

    public static ItemStack createItem(Material material, String name, boolean enchanted, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(translate(name));
            if (lore.length > 0) {
                List<String> list = new ArrayList<>();
                for (String s : lore) {
                    list.add(translate(s));
                }
                meta.setLore(list);
            }
            if (enchanted) {
                for (org.bukkit.enchantments.Enchantment ench : org.bukkit.enchantments.Enchantment.values()) {
                    if (ench != null) {
                        meta.addEnchant(ench, 1, true);
                        break;
                    }
                }
                meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    public static ItemStack createSkull(OfflinePlayer player, String name, String... lore) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        if (meta != null) {
            meta.setOwningPlayer(player);
            meta.setDisplayName(translate(name));
            if (lore.length > 0) {
                List<String> list = new ArrayList<>();
                for (String s : lore) {
                    list.add(translate(s));
                }
                meta.setLore(list);
            }
            item.setItemMeta(meta);
        }
        return item;
    }
}