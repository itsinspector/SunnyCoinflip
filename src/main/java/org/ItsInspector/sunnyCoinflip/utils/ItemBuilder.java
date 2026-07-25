package org.ItsInspector.sunnyCoinflip.utils;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

public class ItemBuilder {
    public static ItemStack createItem(Material material, String name, String... lore) {
        return createItem(material, name, false, lore);
    }

    public static String translate(String text) {
        if (text == null) {
            return null;
        } else {
            text = ChatColor.translateAlternateColorCodes('&', text);
            if (Bukkit.getPluginManager().isPluginEnabled("ItemsAdder")) {
                try {
                    Class<?> wrapperClass = Class.forName("dev.lone.itemsadder.api.FontImageWrapper");
                    Method method = wrapperClass.getMethod("replaceFontImages", String.class);
                    return (String)method.invoke((Object)null, text);
                } catch (Exception var3) {
                }
            }

            return text;
        }
    }

    public static ItemStack createItem(Material material, String name, boolean enchanted, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(translate(name));
            if (lore.length > 0) {
                List<String> list = new ArrayList();

                for(String s : lore) {
                    list.add(translate(s));
                }

                meta.setLore(list);
            }

            if (enchanted) {
                for(Enchantment ench : Enchantment.values()) {
                    if (ench != null) {
                        meta.addEnchant(ench, 1, true);
                        break;
                    }
                }

                meta.addItemFlags(new ItemFlag[]{ItemFlag.HIDE_ENCHANTS});
            }

            item.setItemMeta(meta);
        }

        return item;
    }

    public static ItemStack createSkull(OfflinePlayer player, String name, String... lore) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta)item.getItemMeta();
        if (meta != null) {
            meta.setOwningPlayer(player);
            meta.setDisplayName(translate(name));
            if (lore.length > 0) {
                List<String> list = new ArrayList();

                for(String s : lore) {
                    list.add(translate(s));
                }

                meta.setLore(list);
            }

            item.setItemMeta(meta);
        }

        return item;
    }
}
